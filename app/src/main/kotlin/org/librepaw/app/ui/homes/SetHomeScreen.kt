package org.librepaw.app.ui.homes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.R
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.DARK_MAP_STYLE_URL
import org.librepaw.app.ui.common.LIGHT_MAP_STYLE_URL
import org.librepaw.app.ui.common.MapLibreHost
import org.librepaw.app.ui.common.asString
import org.librepaw.app.ui.home.BottomBarClearance
import org.librepaw.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val HOME_CIRCLE_SOURCE_ID = "home-circle"
private const val HOME_CIRCLE_FILL_LAYER_ID = "home-circle-fill"
private const val HOME_CIRCLE_LINE_LAYER_ID = "home-circle-line"
private const val MIN_RADIUS_METERS = 20.0
private const val MAX_RADIUS_METERS = 100.0
private const val RADIUS_STEP_METERS = 5.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetHomeScreen(
  petId: Int,
  homeId: Int?,
  petRepository: PetRepository,
  onSaved: () -> Unit,
  onDeleted: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  defaultHomeName: String = stringResource(R.string.set_home_default_name),
  viewModel: SetHomeViewModel =
    viewModel(key = "set-home-$petId-${homeId ?: "new"}") { SetHomeViewModel(petId, homeId, petRepository, defaultHomeName) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showDeleteConfirmation by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(if (viewModel.isNew) R.string.set_home_add else R.string.set_home_edit)) },
        navigationIcon = {
          IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) }
        },
        actions = {
          if (!viewModel.isNew && viewModel.permissions?.canManageHomes == true) {
            IconButton(onClick = { showDeleteConfirmation = true }) {
              Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
      when (val state = uiState) {
        SetHomeUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is SetHomeUiState.Failed ->
          Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.message.asString(), style = MaterialTheme.typography.bodyMedium)
          }
        SetHomeUiState.Ready -> SetHomeContent(viewModel = viewModel, onSaved = onSaved)
      }
    }
  }

  if (showDeleteConfirmation) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirmation = false },
      title = { Text(stringResource(R.string.set_home_delete_confirmation)) },
      text = { Text(stringResource(R.string.set_home_delete_explanation)) },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirmation = false
            viewModel.delete(onSuccess = onDeleted)
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun SetHomeContent(viewModel: SetHomeViewModel, onSaved: () -> Unit) {
  val canManage = viewModel.permissions?.canManageHomes == true
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val styleUrl = if (LocalDarkTheme.current) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL
  val homeCircleColor = MaterialTheme.colorScheme.primary.toArgb()
  // How much of the map, from the bottom, the form card and the tab bar cover. The pin and the
  // camera's center both sit in the middle of what's left: on a shorter screen (or with
  // three-button navigation) the card used to cover the screen's center, hiding exactly the pin
  // that shows where the home goes.
  val bottomBarClearancePx = with(LocalDensity.current) { BottomBarClearance.roundToPx() }
  var cardHeightPx by remember { mutableIntStateOf(0) }
  val coveredBottomPx = cardHeightPx + bottomBarClearancePx

  LaunchedEffect(map, coveredBottomPx) {
    // Padding keeps the camera's target coordinate, only moving where on screen it's drawn.
    map?.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, coveredBottomPx.toDouble()))
  }

  Box(modifier = Modifier.fillMaxSize()) {
    MapLibreHost(
      modifier = Modifier.fillMaxSize(),
      onMapReady = { loadedMap ->
        map = loadedMap
        loadedMap.moveCamera(
          CameraUpdateFactory.newLatLngZoom(LatLng(viewModel.center.latitude, viewModel.center.longitude), 17.0)
        )
        loadedMap.setStyle(styleUrl) { style -> addHomeCircleLayer(style, homeCircleColor) }
        if (canManage) {
          loadedMap.addOnCameraIdleListener {
            loadedMap.cameraPosition.target?.let { target ->
              viewModel.updateCenter(Coordinate(target.latitude, target.longitude))
            }
          }
        }
      },
    )

    LaunchedEffect(map, viewModel.center, viewModel.radiusMeters) {
      map?.style?.let { style -> updateHomeCircle(style, viewModel.center, viewModel.radiusMeters) }
    }

    // A fixed pin at the center of the visible map; the map pans underneath it (drop-a-pin UX).
    Icon(
      Icons.Filled.Place,
      contentDescription = null,
      modifier = Modifier.align(Alignment.Center).offset { IntOffset(0, -coveredBottomPx / 2) }.padding(bottom = 32.dp),
      tint = MaterialTheme.colorScheme.error,
    )

    Surface(
      modifier =
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = BottomBarClearance).onSizeChanged {
          cardHeightPx = it.height
        },
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        if (canManage) {
          OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::updateName,
            label = { Text(stringResource(R.string.set_home_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            stringResource(R.string.set_home_pan_hint),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            stringResource(R.string.set_home_radius, viewModel.radiusMeters.toInt()),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.labelLarge,
          )
          Slider(
            value = viewModel.radiusMeters.toFloat(),
            onValueChange = { viewModel.updateRadius(roundToStep(it.toDouble())) },
            valueRange = MIN_RADIUS_METERS.toFloat()..MAX_RADIUS_METERS.toFloat(),
            steps = ((MAX_RADIUS_METERS - MIN_RADIUS_METERS) / RADIUS_STEP_METERS).toInt() - 1,
          )

          viewModel.errorMessage?.asString()?.let { message ->
            Text(
              message,
              modifier = Modifier.padding(top = 8.dp),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }

          Button(
            onClick = { viewModel.save(onSuccess = onSaved) },
            enabled = viewModel.name.isNotBlank() && !viewModel.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          ) {
            if (viewModel.isSaving) {
              CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
              Text(stringResource(if (viewModel.isNew) R.string.set_home_add else R.string.action_save))
            }
          }
        } else {
          Text(viewModel.name, style = MaterialTheme.typography.titleMedium)
          Text(
            stringResource(R.string.set_home_radius, viewModel.radiusMeters.toInt()),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            stringResource(R.string.set_home_no_permission),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun roundToStep(value: Double): Double = Math.round(value / RADIUS_STEP_METERS) * RADIUS_STEP_METERS

private fun addHomeCircleLayer(style: Style, homeCircleColor: Int) {
  if (style.getSource(HOME_CIRCLE_SOURCE_ID) != null) return
  style.addSource(GeoJsonSource(HOME_CIRCLE_SOURCE_ID))
  style.addLayer(
    FillLayer(HOME_CIRCLE_FILL_LAYER_ID, HOME_CIRCLE_SOURCE_ID)
      .withProperties(PropertyFactory.fillColor(homeCircleColor), PropertyFactory.fillOpacity(0.25f))
  )
  style.addLayer(
    LineLayer(HOME_CIRCLE_LINE_LAYER_ID, HOME_CIRCLE_SOURCE_ID)
      .withProperties(PropertyFactory.lineColor(homeCircleColor), PropertyFactory.lineWidth(2f))
  )
}

private fun updateHomeCircle(style: Style, center: Coordinate, radiusMeters: Double) {
  val source = style.getSource(HOME_CIRCLE_SOURCE_ID) as? GeoJsonSource ?: return
  source.setGeoJson(circlePolygon(center, radiusMeters))
}

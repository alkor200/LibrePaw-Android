package org.librepaw.app.ui.homes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.DARK_MAP_STYLE_URL
import org.librepaw.app.ui.common.LIGHT_MAP_STYLE_URL
import org.librepaw.app.ui.common.MapLibreHost
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
private val HOME_CIRCLE_COLOR = Color(0xFF2E7D32)

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
  viewModel: SetHomeViewModel =
    viewModel(key = "set-home-$petId-${homeId ?: "new"}") { SetHomeViewModel(petId, homeId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showDeleteConfirmation by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(if (viewModel.isNew) "Add Home" else "Edit Home") },
        navigationIcon = {
          IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") }
        },
        actions = {
          if (!viewModel.isNew && viewModel.permissions?.canManageHomes == true) {
            IconButton(onClick = { showDeleteConfirmation = true }) {
              Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
          }
        SetHomeUiState.Ready -> SetHomeContent(viewModel = viewModel, onSaved = onSaved)
      }
    }
  }

  if (showDeleteConfirmation) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirmation = false },
      title = { Text("Delete this home?") },
      text = { Text("Excursions already recorded against it aren't affected.") },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirmation = false
            viewModel.delete(onSuccess = onDeleted)
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun SetHomeContent(viewModel: SetHomeViewModel, onSaved: () -> Unit) {
  val canManage = viewModel.permissions?.canManageHomes == true
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val styleUrl = if (LocalDarkTheme.current) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL

  Box(modifier = Modifier.fillMaxSize()) {
    MapLibreHost(
      modifier = Modifier.fillMaxSize(),
      onMapReady = { loadedMap ->
        map = loadedMap
        loadedMap.moveCamera(
          CameraUpdateFactory.newLatLngZoom(LatLng(viewModel.center.latitude, viewModel.center.longitude), 17.0)
        )
        loadedMap.setStyle(styleUrl) { style -> addHomeCircleLayer(style) }
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

    // A fixed pin at the exact screen center; the map pans underneath it (drop-a-pin UX).
    Icon(
      Icons.Filled.Place,
      contentDescription = null,
      modifier = Modifier.align(Alignment.Center).padding(bottom = 32.dp),
      tint = MaterialTheme.colorScheme.error,
    )

    Surface(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        if (canManage) {
          OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::updateName,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            "Pan the map so the pin sits where your pet's home should be.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            "${viewModel.radiusMeters.toInt()} meters radius",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.labelLarge,
          )
          Slider(
            value = viewModel.radiusMeters.toFloat(),
            onValueChange = { viewModel.updateRadius(roundToStep(it.toDouble())) },
            valueRange = MIN_RADIUS_METERS.toFloat()..MAX_RADIUS_METERS.toFloat(),
            steps = ((MAX_RADIUS_METERS - MIN_RADIUS_METERS) / RADIUS_STEP_METERS).toInt() - 1,
          )

          viewModel.errorMessage?.let { message ->
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
              Text(if (viewModel.isNew) "Add Home" else "Save")
            }
          }
        } else {
          Text(viewModel.name, style = MaterialTheme.typography.titleMedium)
          Text(
            "${viewModel.radiusMeters.toInt()} meters radius",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            "You don't have permission to edit this home.",
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

private fun addHomeCircleLayer(style: Style) {
  if (style.getSource(HOME_CIRCLE_SOURCE_ID) != null) return
  style.addSource(GeoJsonSource(HOME_CIRCLE_SOURCE_ID))
  style.addLayer(
    FillLayer(HOME_CIRCLE_FILL_LAYER_ID, HOME_CIRCLE_SOURCE_ID)
      .withProperties(PropertyFactory.fillColor(HOME_CIRCLE_COLOR.toArgb()), PropertyFactory.fillOpacity(0.25f))
  )
  style.addLayer(
    LineLayer(HOME_CIRCLE_LINE_LAYER_ID, HOME_CIRCLE_SOURCE_ID)
      .withProperties(PropertyFactory.lineColor(HOME_CIRCLE_COLOR.toArgb()), PropertyFactory.lineWidth(2f))
  )
}

private fun updateHomeCircle(style: Style, center: Coordinate, radiusMeters: Double) {
  val source = style.getSource(HOME_CIRCLE_SOURCE_ID) as? GeoJsonSource ?: return
  source.setGeoJson(circlePolygon(center, radiusMeters))
}

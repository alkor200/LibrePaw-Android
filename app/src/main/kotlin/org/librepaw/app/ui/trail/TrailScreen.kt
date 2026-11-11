package org.librepaw.app.ui.trail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.DARK_MAP_STYLE_URL
import org.librepaw.app.ui.common.LIGHT_MAP_STYLE_URL
import org.librepaw.app.ui.common.MapLibreHost
import org.librepaw.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Stable per-excursion colors: cycling a fixed, visually distinct palette by each excursion's
 * position in the (most-recent-first, same order every reload) excursions list, so a route's
 * color doesn't shuffle between renders. */
private val ROUTE_PALETTE =
  listOf(
    Color(0xFF007AFF), // blue
    Color(0xFFFF9500), // orange
    Color(0xFFAF52DE), // purple
    Color(0xFFFF2D55), // pink
    Color(0xFF30B0C7), // teal
    Color(0xFF5856D6), // indigo
    Color(0xFFA2845E), // brown
    Color(0xFF00C7BE), // mint
    Color(0xFF32ADE6), // cyan
    Color(0xFFFFCC00), // yellow
  )

private fun lineSourceId(excursionId: Int) = "trail-line-$excursionId"

private fun lineLayerId(excursionId: Int) = "trail-line-$excursionId"

private fun midpointSourceId(excursionId: Int) = "trail-midpoint-$excursionId"

private fun midpointLayerId(excursionId: Int) = "trail-midpoint-$excursionId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailScreen(
  petId: Int,
  petName: String,
  petRepository: PetRepository,
  onBack: () -> Unit,
  onExcursionClick: (Excursion) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: TrailViewModel = viewModel(key = "trail-$petId") { TrailViewModel(petId, petRepository) },
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Trail") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
      TrailMap(viewModel = viewModel, onExcursionClick = onExcursionClick, modifier = Modifier.fillMaxSize())

      WindowPicker(
        selected = viewModel.window,
        onSelect = viewModel::selectWindow,
        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
      )

      if (!viewModel.isLoading && viewModel.errorMessage == null && viewModel.excursions.isEmpty()) {
        Surface(
          modifier = Modifier.align(Alignment.Center).padding(32.dp),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
          Column(modifier = Modifier.padding(24.dp)) {
            Text("No Trail Yet", style = MaterialTheme.typography.titleMedium)
            Text(
              "$petName hasn't been on an excursion in the last ${viewModel.window.label}.",
              modifier = Modifier.padding(top = 4.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else if (viewModel.errorMessage != null) {
        Surface(
          modifier = Modifier.align(Alignment.Center).padding(32.dp),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
          Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't Load Trail", style = MaterialTheme.typography.titleMedium)
            Text(
              viewModel.errorMessage.orEmpty(),
              modifier = Modifier.padding(top = 4.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 16.dp)) { Text("Try Again") }
          }
        }
      }

      if (viewModel.isLoading) {
        Surface(
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
          CircularProgressIndicator(modifier = Modifier.padding(12.dp))
        }
      }
    }
  }
}

@Composable
private fun WindowPicker(selected: TrailWindow, onSelect: (TrailWindow) -> Unit, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
  ) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      TrailWindow.entries.forEachIndexed { index, window ->
        SegmentedButton(
          selected = selected == window,
          onClick = { onSelect(window) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = TrailWindow.entries.size),
        ) {
          Text(window.label)
        }
      }
    }
  }
}

@Composable
private fun TrailMap(viewModel: TrailViewModel, onExcursionClick: (Excursion) -> Unit, modifier: Modifier = Modifier) {
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val styleUrl = if (LocalDarkTheme.current) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL
  val latestExcursions by rememberUpdatedState(viewModel.excursions)
  val latestOnExcursionClick by rememberUpdatedState(onExcursionClick)

  LaunchedEffect(map) {
    val currentMap = map ?: return@LaunchedEffect
    currentMap.addOnMapClickListener { latLng ->
      val screenPoint = currentMap.projection.toScreenLocation(latLng)
      val tapped =
        latestExcursions.firstOrNull { excursion ->
          currentMap.queryRenderedFeatures(screenPoint, midpointLayerId(excursion.id)).isNotEmpty()
        }
      if (tapped != null) {
        latestOnExcursionClick(tapped)
        true
      } else {
        false
      }
    }
  }

  LaunchedEffect(map, styleUrl, viewModel.excursions, viewModel.trailsByExcursionId) {
    val currentMap = map ?: return@LaunchedEffect
    currentMap.setStyle(styleUrl) { style -> drawRoutes(style, viewModel.excursions, viewModel.trailsByExcursionId) }
    fitCameraToAllTrails(currentMap, viewModel.trailsByExcursionId)
  }

  MapLibreHost(modifier = modifier, onMapReady = { map = it })
}

private fun drawRoutes(style: Style, excursions: List<Excursion>, trailsByExcursionId: Map<Int, List<PetPosition>>) {
  excursions.forEachIndexed { index, excursion ->
    val positions = trailsByExcursionId[excursion.id] ?: return@forEachIndexed
    if (positions.size <= 1) return@forEachIndexed
    val color = ROUTE_PALETTE[index % ROUTE_PALETTE.size].toArgb()
    val sortedPositions = positions.sortedBy { it.recordedAt }

    style.addSource(
      GeoJsonSource(lineSourceId(excursion.id), LineString.fromLngLats(sortedPositions.map { Point.fromLngLat(it.longitude, it.latitude) }))
    )
    style.addLayer(
      LineLayer(lineLayerId(excursion.id), lineSourceId(excursion.id))
        .withProperties(
          PropertyFactory.lineColor(color),
          PropertyFactory.lineWidth(4f),
          PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
          PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )

    val midpoint = sortedPositions[sortedPositions.size / 2]
    style.addSource(GeoJsonSource(midpointSourceId(excursion.id), Point.fromLngLat(midpoint.longitude, midpoint.latitude)))
    style.addLayer(
      CircleLayer(midpointLayerId(excursion.id), midpointSourceId(excursion.id))
        .withProperties(
          PropertyFactory.circleRadius(8f),
          PropertyFactory.circleColor(color),
          PropertyFactory.circleStrokeWidth(2f),
          PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        )
    )
  }
}

private fun fitCameraToAllTrails(map: MapLibreMap, trailsByExcursionId: Map<Int, List<PetPosition>>) {
  val positions = trailsByExcursionId.values.flatten()
  if (positions.isEmpty()) return
  if (positions.size == 1) {
    val position = positions.first()
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), 16.0))
    return
  }
  val bounds = LatLngBounds.Builder().apply { positions.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
  map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100, 40, 60, 40))
}

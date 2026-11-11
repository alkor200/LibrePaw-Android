package org.librepaw.app.ui.excursions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val TRAIL_SOURCE_ID = "excursion-trail"
private const val TRAIL_LAYER_ID = "excursion-trail-line"
private val TRAIL_COLOR = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcursionDetailScreen(
  petId: Int,
  excursion: Excursion,
  petRepository: PetRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ExcursionDetailViewModel =
    viewModel(key = "excursion-detail-$petId-${excursion.id}") { ExcursionDetailViewModel(excursion, petId, petRepository) },
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(dateOnlyText(excursion.startTime)) },
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
    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (excursion.isLikelyVehicleTrip) {
        InfoBanner(Icons.Filled.DirectionsCar, "This looks like a car or train ride, it isn't counted in your totals.")
      }
      viewModel.routeText?.let { InfoBanner(Icons.Filled.SwapHoriz, it) }
      StatGrid(excursion)
      MapCard(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
    }
  }
}

@Composable
private fun InfoBanner(icon: ImageVector, text: String) {
  Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
      Text(
        text,
        modifier = Modifier.padding(start = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StatGrid(excursion: Excursion) {
  val tiles = buildList {
    add(Triple(Icons.Filled.Flag, "Started", timeText(excursion.startTime)))
    if (excursion.isOngoing) {
      add(Triple(Icons.Filled.Pets, "Status", "Away now"))
    } else {
      excursion.endTime?.let { add(Triple(Icons.Filled.FlagCircle, "Ended", timeText(it))) }
    }
    excursion.distanceMeters?.let { add(Triple(Icons.Filled.Map, "Distance", distanceText(it))) }
    excursion.durationMillis?.let { add(Triple(Icons.Filled.Schedule, "Duration", durationText(it))) }
    excursion.averageSpeedKmh?.let { add(Triple(Icons.Filled.Speed, "Avg. Speed", "%.1f km/h".format(it))) }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    tiles.chunked(3).forEach { row ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { (icon, label, value) ->
          DetailStatTile(icon = icon, label = label, value = value, modifier = Modifier.weight(1f))
        }
        repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun DetailStatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
      Text(value, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun MapCard(viewModel: ExcursionDetailViewModel, modifier: Modifier = Modifier) {
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val styleUrl = if (LocalDarkTheme.current) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL

  Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(modifier = Modifier.fillMaxSize()) {
      MapLibreHost(
        modifier = Modifier.fillMaxSize(),
        onMapReady = { loadedMap ->
          map = loadedMap
          loadedMap.setStyle(styleUrl) { style -> addTrailLayer(style) }
        },
      )

      LaunchedEffect(map, viewModel.trail, styleUrl) {
        val currentMap = map ?: return@LaunchedEffect
        currentMap.setStyle(styleUrl) { style ->
          addTrailLayer(style)
          updateTrail(style, viewModel.trail)
        }
        fitCameraToTrail(currentMap, viewModel.trail)
      }

      when {
        viewModel.isLoading ->
          Box(modifier = Modifier.align(Alignment.Center)) { CircularProgressIndicator() }
        viewModel.errorMessage != null ->
          Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Text(
              viewModel.errorMessage.orEmpty(),
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        viewModel.trail.size <= 1 ->
          Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          ) {
            Text(
              "No route recorded for this excursion.",
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              style = MaterialTheme.typography.bodySmall,
            )
          }
      }
    }
  }
}

private fun addTrailLayer(style: Style) {
  if (style.getSource(TRAIL_SOURCE_ID) != null) return
  style.addSource(GeoJsonSource(TRAIL_SOURCE_ID))
  style.addLayer(
    LineLayer(TRAIL_LAYER_ID, TRAIL_SOURCE_ID)
      .withProperties(
        PropertyFactory.lineColor(TRAIL_COLOR.toArgb()),
        PropertyFactory.lineWidth(4f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      )
  )
}

private fun updateTrail(style: Style, trail: List<PetPosition>) {
  val source = style.getSource(TRAIL_SOURCE_ID) as? GeoJsonSource ?: return
  if (trail.size > 1) {
    source.setGeoJson(LineString.fromLngLats(trail.map { Point.fromLngLat(it.longitude, it.latitude) }))
  } else {
    source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
  }
}

private fun fitCameraToTrail(map: MapLibreMap, trail: List<PetPosition>) {
  if (trail.isEmpty()) return
  if (trail.size == 1) {
    val position = trail.first()
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), 16.0))
    return
  }
  val bounds = LatLngBounds.Builder().apply { trail.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
  map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60, 60, 60, 60))
}

private fun dateOnlyText(epochMillis: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))

private fun timeText(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

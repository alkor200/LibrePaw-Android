package org.librepaw.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/fiord"
private const val PETS_SOURCE_ID = "pets"
private const val PETS_LAYER_ID = "pets-layer"

@Composable
fun PetMapScreen(
  petRepository: PetRepository,
  onPetClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped by the caller after a successful edit, so a fresh [PetMapViewModel] re-fetches instead of reusing stale cached state. */
  dataVersion: Int = 0,
  viewModel: PetMapViewModel = viewModel(key = "pet-map-$dataVersion") { PetMapViewModel(petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val styleUrl = if (LocalDarkTheme.current) DARK_STYLE_URL else LIGHT_STYLE_URL
  val pets = (uiState as? PetMapUiState.Loaded)?.pets.orEmpty()

  Box(modifier = modifier.fillMaxSize()) {
    MapLibreMapView(styleUrl = styleUrl, pets = pets, onPetClick = onPetClick, modifier = Modifier.fillMaxSize())

    when (val state = uiState) {
      PetMapUiState.Loading ->
        Surface(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text("Loading pets…", style = MaterialTheme.typography.bodyMedium)
          }
        }
      is PetMapUiState.Failed ->
        Surface(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp, start = 24.dp, end = 24.dp),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            state.message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
        }
      is PetMapUiState.Loaded ->
        if (state.pets.isEmpty()) {
          Surface(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          ) {
            Column(modifier = Modifier.padding(24.dp)) {
              Text("No Pet Locations Yet", style = MaterialTheme.typography.titleMedium)
              Text(
                "Pets will appear here once they've reported a position.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
    }
  }
}

@Composable
private fun MapLibreMapView(
  styleUrl: String,
  pets: List<Pet>,
  onPetClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mapView = remember { MapView(context) }
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val latestPets by rememberUpdatedState(pets)
  val latestOnPetClick by rememberUpdatedState(onPetClick)

  DisposableEffect(lifecycleOwner, mapView) {
    mapView.onCreate(null)
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      mapView.onDestroy()
    }
  }

  LaunchedEffect(mapView) { mapView.getMapAsync { loadedMap -> map = loadedMap } }

  LaunchedEffect(map) {
    val currentMap = map ?: return@LaunchedEffect
    currentMap.addOnMapClickListener { latLng ->
      val screenPoint = currentMap.projection.toScreenLocation(latLng)
      val clicked = currentMap.queryRenderedFeatures(screenPoint, PETS_LAYER_ID).firstOrNull()
      val petId = clicked?.getNumberProperty("petId")?.toInt()
      val pet = latestPets.firstOrNull { it.id == petId }
      if (pet != null) {
        latestOnPetClick(pet)
        true
      } else {
        false
      }
    }
  }

  LaunchedEffect(map, styleUrl, pets) {
    val currentMap = map ?: return@LaunchedEffect
    currentMap.setStyle(styleUrl) { style ->
      pets.forEach { pet -> style.addImage(markerImageId(pet), PetMarkerIconFactory.icon(pet)) }

      val features =
        pets.mapNotNull { pet ->
          val position = pet.lastPosition ?: return@mapNotNull null
          Feature.fromGeometry(
            Point.fromLngLat(position.longitude, position.latitude),
            JsonObject().apply {
              addProperty("petId", pet.id)
              addProperty("icon", markerImageId(pet))
            },
          )
        }
      style.addSource(GeoJsonSource(PETS_SOURCE_ID, FeatureCollection.fromFeatures(features)))
      style.addLayer(
        SymbolLayer(PETS_LAYER_ID, PETS_SOURCE_ID)
          .withProperties(PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true))
      )

      fitCamera(currentMap, pets)
    }
  }

  AndroidView(factory = { mapView }, modifier = modifier)
}

private fun markerImageId(pet: Pet) = "pet-${pet.id}"

private fun fitCamera(map: MapLibreMap, pets: List<Pet>) {
  val positions = pets.mapNotNull { it.lastPosition }
  when {
    positions.isEmpty() -> return
    positions.size == 1 -> {
      val position = positions.first()
      map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), 16.0))
    }
    else -> {
      val bounds =
        LatLngBounds.Builder().apply { positions.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
      map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40, 80, 40, 80))
    }
  }
}

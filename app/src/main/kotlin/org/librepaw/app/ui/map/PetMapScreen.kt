package org.librepaw.app.ui.map

import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.DARK_MAP_STYLE_URL
import org.librepaw.app.ui.common.ImageDecoding
import org.librepaw.app.ui.common.LIGHT_MAP_STYLE_URL
import org.librepaw.app.ui.common.MapLibreHost
import org.librepaw.app.ui.common.NavigationLauncher
import org.librepaw.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val PETS_SOURCE_ID = "pets"
private const val PETS_LAYER_ID = "pets-layer"

@Composable
fun PetMapScreen(
  petRepository: PetRepository,
  onPetClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped by the caller after a successful edit, so a fresh [PetMapViewModel] re-fetches instead of reusing stale cached state. */
  dataVersion: Int = 0,
  /** Set by Pet Detail's "View on Map", so this screen centers on that one pet once (matching
   * iOS's AppTabNavigation.mapFocusPet); [onFocusConsumed] clears it the same way iOS's own
   * `.onChange` handler does once it's finished animating the camera there. */
  focusPetId: Int? = null,
  onFocusConsumed: () -> Unit = {},
  viewModel: PetMapViewModel = viewModel(key = "pet-map-$dataVersion") { PetMapViewModel(petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val styleUrl = if (LocalDarkTheme.current) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL
  val pets = (uiState as? PetMapUiState.Loaded)?.pets.orEmpty()

  Box(modifier = modifier.fillMaxSize()) {
    PetMarkersMap(
      petRepository = petRepository,
      styleUrl = styleUrl,
      pets = pets,
      onPetClick = onPetClick,
      focusPetId = focusPetId,
      onFocusConsumed = onFocusConsumed,
      modifier = Modifier.fillMaxSize(),
    )

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
private fun PetMarkersMap(
  petRepository: PetRepository,
  styleUrl: String,
  pets: List<Pet>,
  onPetClick: (Pet) -> Unit,
  focusPetId: Int?,
  onFocusConsumed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  val latestPets by rememberUpdatedState(pets)
  val latestOnPetClick by rememberUpdatedState(onPetClick)
  val latestOnFocusConsumed by rememberUpdatedState(onFocusConsumed)
  val fallbackColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
  val pawColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
  val context = LocalContext.current

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
    // Matches iOS's PetMapView: long-pressing the map (not necessarily right on a marker, since
    // MapLibre's long-press API only gives a coordinate, no feature hit-testing) starts
    // navigation to whichever pet is nearest the press, within 50m.
    currentMap.addOnMapLongClickListener { latLng ->
      val pressed = Coordinate(latLng.latitude, latLng.longitude)
      val nearestPet =
        latestPets
          .mapNotNull { pet -> pet.lastPosition?.let { position -> pet to pressed.distanceMeters(Coordinate(position.latitude, position.longitude)) } }
          .filter { (_, distance) -> distance <= 50.0 }
          .minByOrNull { (_, distance) -> distance }
          ?.first
      if (nearestPet != null) {
        val position = nearestPet.lastPosition!!
        NavigationLauncher.navigateTo(context, position.latitude, position.longitude, nearestPet.name)
        true
      } else {
        false
      }
    }
  }

  LaunchedEffect(map, styleUrl, pets) {
    val currentMap = map ?: return@LaunchedEffect
    val photosByPetId = fetchPhotos(petRepository, pets)
    currentMap.setStyle(styleUrl) { style ->
      pets.forEach { pet ->
        style.addImage(markerImageId(pet), PetMarkerIconFactory.icon(pet, photosByPetId[pet.id], fallbackColor, pawColor))
      }

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

      // `setStyle` resets the camera to the style's own default view as it loads, which would
      // otherwise undo a pending "View on Map" focus: this effect's photo fetch makes it slower
      // than the dedicated focus effect below, so its setStyle call lands after that effect's
      // animateCamera and would silently snap back to a fit-all (or default) view a moment later.
      // Deciding the camera here, after the style has actually finished loading, is the only spot
      // guaranteed to run after that reset, so it must handle the focused case itself rather than
      // deferring to the other effect.
      val focusPosition = focusPetId?.let { id -> pets.firstOrNull { it.id == id }?.lastPosition }
      if (focusPosition != null) {
        currentMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focusPosition.latitude, focusPosition.longitude), 16.0))
        latestOnFocusConsumed()
      } else {
        fitCamera(currentMap, pets)
      }
    }
  }

  // Handles "View on Map" tapped again while this screen's composition (and its already-loaded
  // style) is still alive, e.g. reached from a pet marker on this same map: no new setStyle call
  // happens in that case, so there's no reset to race against here.
  LaunchedEffect(map, focusPetId, pets) {
    val currentMap = map ?: return@LaunchedEffect
    val petId = focusPetId ?: return@LaunchedEffect
    val position = pets.firstOrNull { it.id == petId }?.lastPosition ?: return@LaunchedEffect
    currentMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), 16.0))
    latestOnFocusConsumed()
  }

  MapLibreHost(modifier = modifier, onMapReady = { map = it })
}

private fun markerImageId(pet: Pet) = "pet-${pet.id}"

/** Markers are drawn at 128px (see PetMarkerIconFactory), so this is plenty. */
private const val MARKER_PHOTO_MAX_DIMENSION_PX = 256

/** Fetches every pet's photo concurrently, decoded and keyed by pet id; `null` for a pet with no
 * photo set or whose fetch failed, so [PetMarkerIconFactory] falls back to a paw icon for it. */
private suspend fun fetchPhotos(petRepository: PetRepository, pets: List<Pet>): Map<Int, Bitmap?> =
  coroutineScope {
    pets
      .map { pet ->
        async {
          val bytes = try { petRepository.fetchPhoto(pet) } catch (e: TraccarError) { null }
          pet.id to bytes?.let { ImageDecoding.decode(it, maxDimension = MARKER_PHOTO_MAX_DIMENSION_PX) }
        }
      }
      .awaitAll()
      .toMap()
  }

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

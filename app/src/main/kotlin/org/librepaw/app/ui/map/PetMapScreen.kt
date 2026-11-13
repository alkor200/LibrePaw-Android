package org.librepaw.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.librepaw.app.R
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.DARK_MAP_STYLE_URL
import org.librepaw.app.ui.common.ImageDecoding
import org.librepaw.app.ui.common.LIGHT_MAP_STYLE_URL
import org.librepaw.app.ui.common.LocationHeadingService
import org.librepaw.app.ui.common.MapLibreHost
import org.librepaw.app.ui.common.NavigationLauncher
import org.librepaw.app.ui.common.asString
import org.librepaw.app.ui.home.BottomBarClearance
import org.librepaw.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
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
            Text(stringResource(R.string.loading_pets), style = MaterialTheme.typography.bodyMedium)
          }
        }
      is PetMapUiState.Failed ->
        Surface(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp, start = 24.dp, end = 24.dp),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            state.message.asString(),
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
              Text(stringResource(R.string.map_no_locations_title), style = MaterialTheme.typography.titleMedium)
              Text(
                stringResource(R.string.map_no_locations_description),
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
  // The style the latest setStyle call finished loading, for what needs one to attach to.
  var loadedStyle by remember { mutableStateOf<Style?>(null) }
  val latestPets by rememberUpdatedState(pets)
  val latestOnPetClick by rememberUpdatedState(onPetClick)
  val latestOnFocusConsumed by rememberUpdatedState(onFocusConsumed)
  val fallbackColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
  val pawColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
  val context = LocalContext.current
  val density = LocalDensity.current
  val cameraPadding = markerCameraPadding()

  var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      hasLocationPermission = grants.values.any { it } || hasLocationPermission(context)
    }
  // Asked for once per launch when the map first shows, like iOS's PetMapView, not every time the
  // Map tab is reopened after a refusal: from then on only the locate button asks again.
  LaunchedEffect(Unit) {
    if (!hasLocationPermission && !askedForLocationThisLaunch) {
      askedForLocationThisLaunch = true
      permissionLauncher.launch(LOCATION_PERMISSIONS)
    }
  }

  // Only while the map is actually visible, not while the app sits in the background.
  val locationService = remember { LocationHeadingService(context.applicationContext, trackHeading = false) }
  LifecycleStartEffect(hasLocationPermission) {
    if (hasLocationPermission) locationService.startUpdating()
    onStopOrDispose { locationService.stopUpdating() }
  }

  LaunchedEffect(map, loadedStyle, hasLocationPermission) {
    val currentMap = map ?: return@LaunchedEffect
    val style = loadedStyle ?: return@LaunchedEffect
    if (!hasLocationPermission) return@LaunchedEffect
    showUserLocation(context, currentMap, style)
    locationService.location.collect { location ->
      if (location != null && currentMap.locationComponent.isLocationComponentActivated) {
        currentMap.locationComponent.forceLocationUpdate(location)
      }
    }
  }

  // MapLibre's logo and attribution sit at the map's bottom edge, which the floating tab bar
  // covers: moved up to just above it, the same as iOS's ornament row.
  val ornamentBottomPx = with(density) { (mapBottomBarClearance() + 8.dp).roundToPx() }
  LaunchedEffect(map, ornamentBottomPx) {
    val uiSettings = map?.uiSettings ?: return@LaunchedEffect
    val bottom = ornamentBottomPx
    uiSettings.setLogoMargins(uiSettings.logoMarginLeft, uiSettings.logoMarginTop, uiSettings.logoMarginRight, bottom)
    uiSettings.setAttributionMargins(
      uiSettings.attributionMarginLeft,
      uiSettings.attributionMarginTop,
      uiSettings.attributionMarginRight,
      bottom,
    )
  }

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
      loadedStyle = style

      val focusPosition = focusPetId?.let { id -> pets.firstOrNull { it.id == id }?.lastPosition }
      if (focusPosition != null) {
        currentMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focusPosition.latitude, focusPosition.longitude), 16.0))
        latestOnFocusConsumed()
      } else {
        fitCamera(currentMap, pets, cameraPadding)
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

  Box(modifier = modifier) {
    MapLibreHost(modifier = Modifier.fillMaxSize(), onMapReady = { map = it })
    LocateButton(
      onClick = {
        if (!hasLocationPermission) {
          permissionLauncher.launch(LOCATION_PERMISSIONS)
        } else {
          // Nothing yet without a first fix, same as iOS: the next tap after one arrives works.
          val currentMap = map
          val location = locationService.location.value
          if (currentMap != null && location != null) {
            currentMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16.0))
          }
        }
      },
      // Above the logo/attribution row, which itself sits just above the tab bar (see above).
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = mapBottomBarClearance() + 48.dp),
    )
  }
}

/**
 * The tab bar's clearance from the screen's bottom edge: the map draws edge to edge without a
 * Scaffold, so unlike other screens it has to add the system navigation bar itself (see
 * [BottomBarClearance]).
 */
@Composable
private fun mapBottomBarClearance(): Dp = BottomBarClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
private fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    modifier = modifier.size(48.dp),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.primary,
    shadowElevation = 4.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_show_my_location))
    }
  }
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** See the map's own permission request: once per process, not once per Map tab visit. */
private var askedForLocationThisLaunch = false

private fun hasLocationPermission(context: Context): Boolean =
  LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * The user's own position as MapLibre's standard location dot, fed from [LocationHeadingService]
 * rather than MapLibre's own location engine (see the service's doc comment). Activated once per
 * map (it then re-attaches itself to every style setStyle loads), only ever with a permission
 * already granted.
 */
@SuppressLint("MissingPermission")
private fun showUserLocation(context: Context, map: MapLibreMap, style: Style) {
  val locationComponent = map.locationComponent
  try {
    if (!locationComponent.isLocationComponentActivated) {
      locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false).build()
      )
    }
    locationComponent.isLocationComponentEnabled = true
    locationComponent.cameraMode = CameraMode.NONE
    locationComponent.renderMode = RenderMode.NORMAL
  } catch (e: SecurityException) {
    // Permission revoked in the meantime: simply no location dot.
  }
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

/** Pixels to keep clear on each side when fitting the camera to every pet, see [markerCameraPadding]. */
private data class CameraPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Room for a whole marker (not just its center point) on every side, plus what the map shares
 * with the status bar on top and the floating tab bar at the bottom. It used to be a flat 40/80
 * pixels, less than half a 128px marker, so with two pets fitted edge to edge both markers were
 * cut in half at the screen's edges.
 */
@Composable
private fun markerCameraPadding(): CameraPadding {
  val density = LocalDensity.current
  val margin = with(density) { 24.dp.roundToPx() }
  val markerHalf = PetMarkerIconFactory.SIZE_PX / 2
  val statusBar = WindowInsets.statusBars.getTop(density)
  val bottomBar = with(density) { mapBottomBarClearance().roundToPx() }
  return CameraPadding(
    left = markerHalf + margin,
    top = statusBar + markerHalf + margin,
    right = markerHalf + margin,
    bottom = bottomBar + markerHalf + margin,
  )
}

private fun fitCamera(map: MapLibreMap, pets: List<Pet>, padding: CameraPadding) {
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
      map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding.left, padding.top, padding.right, padding.bottom))
    }
  }
}

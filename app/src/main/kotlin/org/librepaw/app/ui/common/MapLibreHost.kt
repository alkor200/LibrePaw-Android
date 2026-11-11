package org.librepaw.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** OpenFreeMap tile styles (ADR-0028 on the iOS side): [LIGHT_MAP_STYLE_URL] in light mode, [DARK_MAP_STYLE_URL] in dark. */
const val LIGHT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
const val DARK_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/fiord"

/**
 * Hosts a lifecycle-managed MapLibre [MapView] inside Compose, handing back the ready
 * [MapLibreMap] via [onMapReady] once available. Shared by every screen that embeds a map, since
 * MapLibre has no official Compose integration of its own.
 */
@Composable
fun MapLibreHost(modifier: Modifier = Modifier, onMapReady: (MapLibreMap) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mapView = remember { MapView(context) }
  val latestOnMapReady by rememberUpdatedState(onMapReady)

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

  LaunchedEffect(mapView) { mapView.getMapAsync { map -> latestOnMapReady(map) } }

  AndroidView(factory = { mapView }, modifier = modifier)
}

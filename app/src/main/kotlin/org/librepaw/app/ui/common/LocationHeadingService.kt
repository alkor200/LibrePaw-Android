package org.librepaw.app.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librepaw.app.data.pets.Coordinate

/**
 * Wraps [LocationManager] and [SensorManager] to expose the user's own coordinate and compass
 * heading, needed for the compass-based "Find Pet" screen (porting iOS's own LocationService,
 * see ADR-0010). Android has no single true/magnetic heading API: the rotation-vector sensor's
 * azimuth is relative to magnetic north, so unlike iOS this never corrects to true north, a
 * simplification treated as acceptable since most on-device compass UIs behave the same way.
 *
 * Also the map's own location dot ([trackHeading] `false` there, no compass needed): MapLibre's
 * built-in location engine always asks the fused provider for balanced (Wi-Fi/cell, ~100m)
 * accuracy whatever priority it's given, too coarse to walk up to a pet with.
 */
class LocationHeadingService(private val context: Context, private val trackHeading: Boolean = true) {
  private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

  private val _location = MutableStateFlow<Location?>(null)

  /** The latest fix, with its accuracy, for MapLibre's location dot. */
  val location: StateFlow<Location?> = _location.asStateFlow()

  private val _coordinate = MutableStateFlow<Coordinate?>(null)
  val coordinate: StateFlow<Coordinate?> = _coordinate.asStateFlow()

  private val _headingDegrees = MutableStateFlow<Double?>(null)
  val headingDegrees: StateFlow<Double?> = _headingDegrees.asStateFlow()

  /** `false` on devices with no rotation-vector sensor; callers should fall back to showing
   * direction relative to true north (an arrow with 0 rotation applied). */
  val isHeadingAvailable: Boolean = rotationSensor != null

  fun hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  /**
   * A full object, not a `LocationListener { }` lambda: up to Android 10 (API 29)
   * onStatusChanged/onProviderEnabled/onProviderDisabled are abstract, their default bodies only
   * arrived in Android 11, so the lambda compiled against a recent SDK crashed with
   * AbstractMethodError the moment the system called one of them (confirmed on Android 7.1.1).
   */
  private val locationListener =
    object : LocationListener {
      override fun onLocationChanged(location: Location) = publish(location)

      @Deprecated("Deprecated in Java")
      override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

      override fun onProviderEnabled(provider: String) = Unit

      override fun onProviderDisabled(provider: String) = Unit
    }

  private fun publish(location: Location) {
    _location.value = location
    _coordinate.value = Coordinate(location.latitude, location.longitude)
  }

  private val rotationMatrix = FloatArray(9)
  private val orientationAngles = FloatArray(3)
  private val sensorListener =
    object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val degrees = Math.toDegrees(orientationAngles[0].toDouble())
        _headingDegrees.value = (degrees + 360) % 360
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

  /**
   * Picks the best provider that exists, is enabled and is allowed with the permission actually
   * granted. It used to fall back to the network provider unconditionally when GPS was off, and
   * request GPS even with only approximate location granted: a device without a network provider
   * (some emulators, de-Googled ROMs) threw IllegalArgumentException, and GPS with only coarse
   * permission below Android 12 a SecurityException, both crashing the Find screen.
   */
  @SuppressLint("MissingPermission")
  fun startUpdating() {
    if (!hasLocationPermission()) return
    val hasFineLocation =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    // From Android 12 on, any provider works with approximate location, its fixes are coarsened.
    val canUseGps = hasFineLocation || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val candidates = if (canUseGps) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
    val provider =
      candidates.firstOrNull { it in locationManager.allProviders && locationManager.isProviderEnabled(it) }
    if (provider != null) {
      try {
        // Something to show right away, instead of nothing until the provider's first fresh fix.
        if (_location.value == null) locationManager.getLastKnownLocation(provider)?.let(::publish)
        locationManager.requestLocationUpdates(provider, 1_000L, 1f, locationListener)
      } catch (e: SecurityException) {
        // Permission revoked between the check above and here.
      } catch (e: IllegalArgumentException) {
        // Provider vanished between the check above and here.
      }
    }
    if (trackHeading) rotationSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
  }

  fun stopUpdating() {
    locationManager.removeUpdates(locationListener)
    sensorManager.unregisterListener(sensorListener)
  }
}

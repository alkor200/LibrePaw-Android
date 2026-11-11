package org.librepaw.app.data.traccar

/**
 * Traccar's circle geofences are stored as a WKT-like string: `CIRCLE (lat lon, radiusMeters)`,
 * **latitude first** rather than standard WKT's (lon, lat) order — confirmed against Traccar's own
 * server source. Don't reintroduce the lon-first assumption (see the iOS app's ADR-0014).
 */
object TraccarGeofenceArea {
  private val CIRCLE_PATTERN = Regex("""CIRCLE\s*\(\s*([-\d.]+)\s+([-\d.]+)\s*,\s*([-\d.]+)\s*\)""")

  data class Circle(val latitude: Double, val longitude: Double, val radiusMeters: Double)

  fun circleWkt(centerLatitude: Double, centerLongitude: Double, radiusMeters: Double): String =
    "CIRCLE ($centerLatitude $centerLongitude, $radiusMeters)"

  fun parseCircle(wkt: String): Circle? {
    val match = CIRCLE_PATTERN.find(wkt) ?: return null
    val (lat, lon, radius) = match.destructured
    return try {
      Circle(lat.toDouble(), lon.toDouble(), radius.toDouble())
    } catch (e: NumberFormatException) {
      null
    }
  }
}

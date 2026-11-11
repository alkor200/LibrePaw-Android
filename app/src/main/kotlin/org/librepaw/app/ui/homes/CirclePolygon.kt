package org.librepaw.app.ui.homes

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.librepaw.app.data.pets.Coordinate
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Approximates a circle on Earth's surface as a closed polygon ring, for drawing a home's radius on a map. */
fun circlePolygon(center: Coordinate, radiusMeters: Double, segments: Int = 64): Polygon {
  val latRad = Math.toRadians(center.latitude)
  val lonRad = Math.toRadians(center.longitude)
  val angularDistance = radiusMeters / EARTH_RADIUS_METERS

  val points =
    (0..segments).map { index ->
      val bearing = 2 * Math.PI * index / segments
      val lat2 = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearing))
      val lon2 =
        lonRad + atan2(sin(bearing) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(lat2))
      Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
    }
  return Polygon.fromLngLats(listOf(points))
}

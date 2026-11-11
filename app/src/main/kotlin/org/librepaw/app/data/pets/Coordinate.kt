package org.librepaw.app.data.pets

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class Coordinate(val latitude: Double, val longitude: Double) {
  /** Great-circle distance to [other], in meters (haversine). */
  fun distanceMeters(to: Coordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val latRad1 = Math.toRadians(latitude)
    val latRad2 = Math.toRadians(to.latitude)
    val deltaLat = Math.toRadians(to.latitude - latitude)
    val deltaLon = Math.toRadians(to.longitude - longitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) + cos(latRad1) * cos(latRad2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
  }
}

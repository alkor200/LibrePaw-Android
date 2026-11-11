package org.librepaw.app.data.pets

import kotlinx.serialization.Serializable

/** A trip away from home and back, within a time range. `endTime == null` means still ongoing. */
@Serializable
data class Excursion(
  val id: Int,
  val startTime: Long,
  val endTime: Long?,
  val startCoordinate: Coordinate?,
  val endCoordinate: Coordinate?,
  val distanceMeters: Double?,
  val startHomeId: Int?,
  val endHomeId: Int?,
) {
  val isOngoing: Boolean
    get() = endTime == null

  val durationMillis: Long?
    get() = endTime?.minus(startTime)

  /** A rough heuristic (sustained average speed), not an exact replica of any particular formula. */
  val isLikelyVehicleTrip: Boolean
    get() {
      val duration = durationMillis ?: return false
      val distance = distanceMeters ?: return false
      if (duration <= 0) return false
      val averageSpeedKmh = (distance / 1000.0) / (duration / 3_600_000.0)
      return averageSpeedKmh > 15.0
    }
}

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

  /** `null` until both a duration and a distance are known (i.e. the excursion has closed). */
  val averageSpeedKmh: Double?
    get() {
      val duration = durationMillis ?: return null
      val distance = distanceMeters ?: return null
      if (duration <= 0) return null
      return (distance / 1000.0) / (duration / 3_600_000.0)
    }

  /** A rough heuristic (sustained average speed), not an exact replica of any particular formula. */
  val isLikelyVehicleTrip: Boolean
    get() = (averageSpeedKmh ?: 0.0) > 15.0
}

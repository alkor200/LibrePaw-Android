package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable

/** An event as returned by Traccar's `/api/reports/events` endpoint. */
@Serializable
data class TraccarEvent(
  val id: Int,
  val type: String,
  val eventTime: String,
  val deviceId: Int,
  val geofenceId: Int? = null,
) {
  object EventType {
    const val GEOFENCE_ENTER = "geofenceEnter"
    const val GEOFENCE_EXIT = "geofenceExit"
  }
}

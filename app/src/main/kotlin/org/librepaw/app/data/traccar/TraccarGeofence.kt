package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A geofence as returned by Traccar's `/api/geofences` endpoint. `area` is a WKT string. */
@Serializable
data class TraccarGeofence(
  val id: Int,
  val name: String,
  val area: String,
  val attributes: Map<String, JsonElement> = emptyMap(),
)

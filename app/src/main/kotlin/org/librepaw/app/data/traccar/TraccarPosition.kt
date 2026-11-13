package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A position fix as returned by Traccar's `/api/positions` endpoint. */
@Serializable
data class TraccarPosition(
  val id: Long,
  val deviceId: Int,
  val fixTime: String,
  val latitude: Double,
  val longitude: Double,
  /** Knots, not km/h; Traccar reports speed in knots regardless of locale. */
  val speed: Double,
  val address: String? = null,
  val attributes: Map<String, JsonElement> = emptyMap(),
)

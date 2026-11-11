package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A device as returned by Traccar's `/api/devices` endpoint. */
@Serializable
data class TraccarDevice(
  val id: Int,
  val name: String,
  val uniqueId: String,
  val status: String,
  val disabled: Boolean,
  val lastUpdate: String? = null,
  val category: String? = null,
  val attributes: Map<String, JsonElement> = emptyMap(),
)

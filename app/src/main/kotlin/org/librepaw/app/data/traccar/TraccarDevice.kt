package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable

/**
 * A device as returned by Traccar's `/api/devices` endpoint.
 *
 * Only the fields needed so far (validating a login) are modeled here; this
 * grows into the full pet-mapping shape when the Pet List feature is ported.
 */
@Serializable
data class TraccarDevice(
  val id: Int,
  val name: String,
  val uniqueId: String,
  val status: String,
  val disabled: Boolean,
)

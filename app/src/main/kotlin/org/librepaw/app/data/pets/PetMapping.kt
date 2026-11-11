package org.librepaw.app.data.pets

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import org.librepaw.app.data.traccar.TraccarDates
import org.librepaw.app.data.traccar.TraccarDevice
import org.librepaw.app.data.traccar.TraccarPosition

/** Maps Traccar DTOs onto LibrePaw's own [Pet] domain model. */
object PetMapping {
  fun pet(device: TraccarDevice, latestPosition: TraccarPosition?, serverUrl: HttpUrl): Pet =
    Pet(
      id = device.id,
      name = device.name,
      species = device.category,
      status = status(device.status),
      lastSeenAt = TraccarDates.parse(device.lastUpdate),
      lastPosition = latestPosition?.let(::position),
      photoUrl = photoUrl(device, serverUrl),
    )

  fun position(position: TraccarPosition): PetPosition =
    PetPosition(
      id = position.id,
      petId = position.deviceId,
      latitude = position.latitude,
      longitude = position.longitude,
      recordedAt = TraccarDates.parse(position.fixTime) ?: 0L,
      // Traccar reports speed in knots regardless of locale.
      speedKilometersPerHour = position.speed * 1.852,
      address = position.address,
      batteryLevelPercent = (position.attributes["batteryLevel"] as? JsonPrimitive)?.doubleOrNull,
    )

  private fun status(raw: String): Pet.Status =
    when (raw) {
      "online" -> Pet.Status.ONLINE
      "offline" -> Pet.Status.OFFLINE
      else -> Pet.Status.UNKNOWN
    }

  /** Traccar stores an uploaded photo's filename in the device's `deviceImage` attribute. */
  private fun photoUrl(device: TraccarDevice, serverUrl: HttpUrl): HttpUrl? {
    val filename = (device.attributes["deviceImage"] as? JsonPrimitive)?.contentOrNull
    if (filename.isNullOrBlank()) return null
    return serverUrl.newBuilder().addPathSegments("api/media/${device.uniqueId}/$filename").build()
  }
}

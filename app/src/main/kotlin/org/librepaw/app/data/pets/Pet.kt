package org.librepaw.app.data.pets

import okhttp3.HttpUrl

/**
 * A tracked pet, as understood by LibrePaw.
 *
 * This is LibrePaw's own domain model, mapped from a Traccar "device" at
 * the repository boundary; nothing above [PetRepository] needs to know
 * that Traccar, or the word "device", exists.
 */
data class Pet(
  val id: Int,
  val name: String,
  /** Free-form species/category (e.g. "dog", "cat"), if set on the Traccar device. */
  val species: String?,
  val status: Status,
  /** Epoch milliseconds, parsed from the device's `lastUpdate`. */
  val lastSeenAt: Long?,
  val lastPosition: PetPosition?,
  /** Where to fetch the pet's photo from, Basic Auth required, same as any other request. */
  val photoUrl: HttpUrl?,
) {
  enum class Status {
    ONLINE,
    OFFLINE,
    UNKNOWN,
  }
}

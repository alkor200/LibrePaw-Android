package org.librepaw.app.data.pets

/** Reads pets and their positions for the currently authenticated session. */
interface PetRepository {
  /** All pets visible to the current user, each with its latest known position attached. */
  suspend fun fetchPets(): List<Pet>

  /** The pet's photo bytes, `null` when it has none set. */
  suspend fun fetchPhoto(pet: Pet): ByteArray?

  /** What the signed-in Traccar account can actually do. */
  suspend fun currentUserPermissions(): AccountPermissions

  /** Renames the pet, i.e. its underlying Traccar device. */
  suspend fun renamePet(petId: Int, name: String)

  /** Sets (or replaces) the pet's photo. `jpegData` must already be JPEG-encoded. */
  suspend fun setPhoto(petId: Int, jpegData: ByteArray)

  /** The position history for one pet within a time range, oldest first. */
  suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition>

  /** The pet's configured home areas, empty when none has been set yet. */
  suspend fun homeGeofences(petId: Int): List<HomeGeofence>

  /** Adds a new home area for the pet. */
  suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double)

  /** Moves and/or renames an existing home area in place (same geofence id). */
  suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double)

  /** Removes a home area entirely. */
  suspend fun deleteHomeGeofence(homeId: Int)

  /** Excursions within a time range, most recent first. Requires at least one home geofence. */
  suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion>

  /** Discards any cached excursions for this pet, forcing the next fetch to recompute from scratch. */
  suspend fun resetExcursionsCache(petId: Int)

  /** Total distance and time away across every excursion the pet has ever taken. */
  suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats

  /** Discards any cached lifetime stats for this pet, forcing a full recomputation next time. */
  suspend fun resetLifetimeStats(petId: Int)

  /** Shares the pet with another Traccar user, found by email. Throws if no such user exists. */
  suspend fun sharePet(petId: Int, email: String)

  /** Best-effort, never throws: ends any server-side session this repository opened, on logout. */
  suspend fun endSession() {}
}

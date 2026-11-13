package org.librepaw.app.data.pets

import org.librepaw.app.data.traccar.TraccarError

/**
 * Wraps the session's real [PetRepository] and reports any [TraccarError.InvalidCredentials] it
 * throws to [onInvalidCredentials], so the app can end a session the server no longer accepts
 * (account disabled or deleted, token revoked or expired, a stored password changed) instead of
 * every screen separately showing "The email or password is incorrect." with no way out but
 * logging out by hand. Ported from the iOS app's own SessionExpiryDetectingPetRepository.
 *
 * Errors are passed through unchanged either way, this only observes them. [onInvalidCredentials]
 * can be called several times (several requests failing at once) and from whatever thread the
 * failing call ran on.
 */
class SessionExpiryDetectingPetRepository(
  private val base: PetRepository,
  private val onInvalidCredentials: () -> Unit,
) : PetRepository {
  private suspend fun <T> checked(operation: suspend () -> T): T =
    try {
      operation()
    } catch (e: TraccarError.InvalidCredentials) {
      onInvalidCredentials()
      throw e
    }

  override suspend fun fetchPets(): List<Pet> = checked { base.fetchPets() }

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = checked { base.fetchPhoto(pet) }

  override suspend fun currentUserPermissions(): AccountPermissions = checked { base.currentUserPermissions() }

  override suspend fun renamePet(petId: Int, name: String) = checked { base.renamePet(petId, name) }

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = checked { base.setPhoto(petId, jpegData) }

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> =
    checked { base.fetchTrail(petId, fromEpochMillis, toEpochMillis) }

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = checked { base.homeGeofences(petId) }

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    checked { base.addHomeGeofence(petId, name, center, radiusMeters) }

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    checked { base.updateHomeGeofence(homeId, petId, name, center, radiusMeters) }

  override suspend fun deleteHomeGeofence(homeId: Int) = checked { base.deleteHomeGeofence(homeId) }

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> =
    checked { base.fetchExcursions(petId, fromEpochMillis, toEpochMillis) }

  override suspend fun resetExcursionsCache(petId: Int) = checked { base.resetExcursionsCache(petId) }

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats = checked { base.lifetimeExcursionStats(petId) }

  override suspend fun resetLifetimeStats(petId: Int) = checked { base.resetLifetimeStats(petId) }

  override suspend fun sharePet(petId: Int, email: String) = checked { base.sharePet(petId, email) }

  // Not checked: only ever called on the way out anyway.
  override suspend fun endSession() = base.endSession()
}

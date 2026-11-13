package org.librepaw.app.data.pets

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarError

class SessionExpiryDetectingPetRepositoryTest {
  @Test
  fun invalidCredentials_isReported_andStillThrown() = runTest {
    var reports = 0
    val repository = SessionExpiryDetectingPetRepository(ThrowingPetRepository(TraccarError.InvalidCredentials())) { reports++ }

    val thrown = runCatching { repository.fetchPets() }.exceptionOrNull()

    assertTrue(thrown is TraccarError.InvalidCredentials)
    assertEquals(1, reports)
  }

  @Test
  fun otherFailures_areNotReported() = runTest {
    var reports = 0
    for (error in listOf(TraccarError.Forbidden(), TraccarError.Server(500), TraccarError.Transport("offline"))) {
      val repository = SessionExpiryDetectingPetRepository(ThrowingPetRepository(error)) { reports++ }
      runCatching { repository.fetchExcursions(1, 0L, 1L) }
    }
    assertEquals(0, reports)
  }
}

/** Every call fails with [error]. */
private class ThrowingPetRepository(private val error: Throwable) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = throw error

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = throw error

  override suspend fun currentUserPermissions(): AccountPermissions = throw error

  override suspend fun renamePet(petId: Int, name: String) = throw error

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw error

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> = throw error

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = throw error

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) = throw error

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) = throw error

  override suspend fun deleteHomeGeofence(homeId: Int) = throw error

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> = throw error

  override suspend fun resetExcursionsCache(petId: Int) = throw error

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats = throw error

  override suspend fun resetLifetimeStats(petId: Int) = throw error

  override suspend fun sharePet(petId: Int, email: String) = throw error
}

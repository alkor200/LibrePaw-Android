package org.librepaw.app.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

@OptIn(ExperimentalCoroutinesApi::class)
class PetMapViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_filtersOutPetsWithoutAPosition() {
    val withPosition = pet(id = 1, hasPosition = true)
    val withoutPosition = pet(id = 2, hasPosition = false)
    val viewModel = PetMapViewModel(FakePetRepository(Result.success(listOf(withPosition, withoutPosition))))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetMapUiState.Loaded)
    assertEquals(listOf(1), (state as PetMapUiState.Loaded).pets.map { it.id })
  }

  @Test
  fun uiState_onFailure_isFailed() {
    val viewModel = PetMapViewModel(FakePetRepository(Result.failure(TraccarError.Server(500))))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is PetMapUiState.Failed)
  }

  private fun pet(id: Int, hasPosition: Boolean) =
    Pet(
      id = id,
      name = "Pet$id",
      species = null,
      status = Pet.Status.ONLINE,
      lastSeenAt = null,
      lastPosition =
        if (hasPosition) {
          PetPosition(id = id, petId = id, latitude = 0.0, longitude = 0.0, recordedAt = 0L, speedKilometersPerHour = 0.0, address = null, batteryLevelPercent = null)
        } else {
          null
        },
      photoUrl = null,
    )
}

private class FakePetRepository(private val result: Result<List<Pet>>) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = result.getOrThrow()

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = null

  override suspend fun currentUserPermissions() = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun homeGeofences(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun addHomeGeofence(petId: Int, name: String, center: org.librepaw.app.data.pets.Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun updateHomeGeofence(
    homeId: Int,
    petId: Int,
    name: String,
    center: org.librepaw.app.data.pets.Coordinate,
    radiusMeters: Double,
  ) = throw UnsupportedOperationException("not used by this test")

  override suspend fun deleteHomeGeofence(homeId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun resetExcursionsCache(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun lifetimeExcursionStats(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun resetLifetimeStats(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun sharePet(petId: Int, email: String) = throw UnsupportedOperationException("not used by this test")
}

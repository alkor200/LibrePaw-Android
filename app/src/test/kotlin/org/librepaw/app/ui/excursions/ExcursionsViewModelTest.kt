package org.librepaw.app.ui.excursions

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
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.HomeGeofence
import org.librepaw.app.data.pets.LifetimeStats
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

@OptIn(ExperimentalCoroutinesApi::class)
class ExcursionsViewModelTest {
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
  fun uiState_loadsExcursionsSortedMostRecentFirst() {
    val excursions = listOf(excursion(id = 1, startTime = 1_000L), excursion(id = 2, startTime = 3_000L), excursion(id = 3, startTime = 2_000L))
    val homes = listOf(HomeGeofence(id = 10, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0))
    val viewModel = ExcursionsViewModel(petId = 1, petRepository = FakeExcursionsRepository(Result.success(excursions), homes))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ExcursionsUiState.Loaded)
    assertEquals(listOf(2L, 3L, 1L), (state as ExcursionsUiState.Loaded).excursions.map { it.id })
    assertEquals(mapOf(10 to "Home"), state.homeNamesById)
  }

  @Test
  fun uiState_onFailure_isFailed() {
    val viewModel = ExcursionsViewModel(petId = 1, petRepository = FakeExcursionsRepository(Result.failure(TraccarError.Server(500)), emptyList()))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is ExcursionsUiState.Failed)
  }

  @Test
  fun refresh_reloadsFromRepository() {
    val repository = FakeExcursionsRepository(Result.success(listOf(excursion(id = 1, startTime = 0L))), emptyList())
    val viewModel = ExcursionsViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    repository.excursions = Result.success(listOf(excursion(id = 1, startTime = 0L), excursion(id = 2, startTime = 1L)))
    viewModel.refresh()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ExcursionsUiState.Loaded)
    assertEquals(2, (state as ExcursionsUiState.Loaded).excursions.size)
  }

  private fun excursion(id: Int, startTime: Long) =
    Excursion(
      id = id.toLong(),
      startTime = startTime,
      endTime = startTime + 1000L,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = null,
      startHomeId = null,
      endHomeId = null,
    )
}

private class FakeExcursionsRepository(var excursions: Result<List<Excursion>>, private val homes: List<HomeGeofence>) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = throw UnsupportedOperationException("not used by this test")

  override suspend fun currentUserPermissions(): AccountPermissions = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = homes

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun deleteHomeGeofence(homeId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> = excursions.getOrThrow()

  override suspend fun resetExcursionsCache(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats = throw UnsupportedOperationException("not used by this test")

  override suspend fun resetLifetimeStats(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun sharePet(petId: Int, email: String) = throw UnsupportedOperationException("not used by this test")
}

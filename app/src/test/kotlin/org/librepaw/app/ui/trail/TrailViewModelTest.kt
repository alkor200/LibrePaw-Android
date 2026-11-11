package org.librepaw.app.ui.trail

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
class TrailViewModelTest {
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
  fun load_fetchesTrailPerExcursion() {
    val excursions = listOf(excursion(id = 1, startTime = 0L, endTime = 1000L), excursion(id = 2, startTime = 2000L, endTime = 3000L))
    val trailsByExcursionId =
      mapOf(1 to listOf(position(0.0, 0.0), position(0.0, 0.01)), 2 to listOf(position(1.0, 1.0), position(1.0, 1.01)))
    val repository = FakePetRepository(excursions = excursions, trailsByExcursionId = trailsByExcursionId)
    val viewModel = TrailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(excursions, viewModel.excursions)
    assertEquals(trailsByExcursionId, viewModel.trailsByExcursionId)
    assertTrue(!viewModel.isLoading)
  }

  @Test
  fun selectWindow_reloadsWithNewRange() {
    val repository = FakePetRepository(excursions = emptyList(), trailsByExcursionId = emptyMap())
    val viewModel = TrailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.selectWindow(TrailWindow.SEVEN_DAYS)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(TrailWindow.SEVEN_DAYS, viewModel.window)
    assertEquals(2, repository.fetchExcursionsCallCount)
  }

  @Test
  fun selectWindow_sameWindow_doesNotReload() {
    val repository = FakePetRepository(excursions = emptyList(), trailsByExcursionId = emptyMap())
    val viewModel = TrailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.selectWindow(TrailWindow.ONE_DAY)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.fetchExcursionsCallCount)
  }

  @Test
  fun load_onFailure_setsErrorMessage() {
    val repository = FakePetRepository(excursions = emptyList(), trailsByExcursionId = emptyMap(), failWith = TraccarError.Server(500))
    val viewModel = TrailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.errorMessage != null)
    assertTrue(!viewModel.isLoading)
  }

  private fun excursion(id: Int, startTime: Long, endTime: Long?) =
    Excursion(
      id = id,
      startTime = startTime,
      endTime = endTime,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = null,
      startHomeId = null,
      endHomeId = null,
    )

  private fun position(latitude: Double, longitude: Double) =
    PetPosition(
      id = 1,
      petId = 1,
      latitude = latitude,
      longitude = longitude,
      recordedAt = 0L,
      speedKilometersPerHour = 0.0,
      address = null,
      batteryLevelPercent = null,
    )
}

private class FakePetRepository(
  private val excursions: List<Excursion>,
  private val trailsByExcursionId: Map<Int, List<PetPosition>>,
  private val failWith: TraccarError? = null,
) : PetRepository {
  var fetchExcursionsCallCount = 0
    private set

  override suspend fun fetchPets(): List<Pet> = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = throw UnsupportedOperationException("not used by this test")

  override suspend fun currentUserPermissions(): AccountPermissions = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> {
    failWith?.let { throw it }
    val excursionId = excursions.first { it.startTime == fromEpochMillis }.id
    return trailsByExcursionId[excursionId].orEmpty()
  }

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = throw UnsupportedOperationException("not used by this test")

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun deleteHomeGeofence(homeId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> {
    fetchExcursionsCallCount++
    failWith?.let { throw it }
    return excursions
  }

  override suspend fun resetExcursionsCache(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats = throw UnsupportedOperationException("not used by this test")

  override suspend fun resetLifetimeStats(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun sharePet(petId: Int, email: String) = throw UnsupportedOperationException("not used by this test")
}

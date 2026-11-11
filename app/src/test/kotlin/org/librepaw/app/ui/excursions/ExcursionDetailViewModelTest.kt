package org.librepaw.app.ui.excursions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class ExcursionDetailViewModelTest {
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
  fun loadTrail_populatesTrailFromRepository() {
    val trail = listOf(position(0.0, 0.0), position(0.0, 0.01))
    val repository = FakeExcursionDetailRepository(trail = Result.success(trail), homes = emptyList())
    val viewModel = ExcursionDetailViewModel(excursion = excursion(startHomeId = null, endHomeId = null), petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(trail, viewModel.trail)
    assertFalse(viewModel.isLoading)
    assertNull(viewModel.errorMessage)
  }

  @Test
  fun loadTrail_onFailure_setsErrorMessage() {
    val repository = FakeExcursionDetailRepository(trail = Result.failure(TraccarError.Server(500)), homes = emptyList())
    val viewModel = ExcursionDetailViewModel(excursion = excursion(startHomeId = null, endHomeId = null), petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.errorMessage != null)
  }

  @Test
  fun routeText_whenStartAndEndHomesDiffer_showsBothNames() {
    val homes = listOf(
      HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0),
      HomeGeofence(id = 2, name = "Cabin", center = Coordinate(1.0, 1.0), radiusMeters = 50.0),
    )
    val repository = FakeExcursionDetailRepository(trail = Result.success(emptyList()), homes = homes)
    val viewModel = ExcursionDetailViewModel(excursion = excursion(startHomeId = 1, endHomeId = 2), petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("Home → Cabin", viewModel.routeText)
  }

  @Test
  fun routeText_whenStartAndEndHomesAreTheSame_isNull() {
    val homes = listOf(HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0))
    val repository = FakeExcursionDetailRepository(trail = Result.success(emptyList()), homes = homes)
    val viewModel = ExcursionDetailViewModel(excursion = excursion(startHomeId = 1, endHomeId = 1), petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.routeText)
  }

  private fun excursion(startHomeId: Int?, endHomeId: Int?) =
    Excursion(
      id = 1,
      startTime = 0L,
      endTime = 60_000L,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = null,
      startHomeId = startHomeId,
      endHomeId = endHomeId,
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

private class FakeExcursionDetailRepository(private val trail: Result<List<PetPosition>>, private val homes: List<HomeGeofence>) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = throw UnsupportedOperationException("not used by this test")

  override suspend fun currentUserPermissions(): AccountPermissions = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> = trail.getOrThrow()

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = homes

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun deleteHomeGeofence(homeId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun resetExcursionsCache(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats = throw UnsupportedOperationException("not used by this test")

  override suspend fun resetLifetimeStats(petId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun sharePet(petId: Int, email: String) = throw UnsupportedOperationException("not used by this test")
}

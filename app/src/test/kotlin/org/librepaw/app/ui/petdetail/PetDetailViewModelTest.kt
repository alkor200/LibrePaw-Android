package org.librepaw.app.ui.petdetail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class PetDetailViewModelTest {
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
  fun uiState_findsMatchingPetById() {
    val pets = listOf(pet(id = 1), pet(id = 2))
    val viewModel = PetDetailViewModel(petId = 2, petRepository = FakePetRepository(Result.success(pets)))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetDetailUiState.Loaded)
    assertEquals(2, (state as PetDetailUiState.Loaded).pet.id)
  }

  @Test
  fun uiState_whenPetIsMissing_isFailed() {
    val viewModel = PetDetailViewModel(petId = 99, petRepository = FakePetRepository(Result.success(listOf(pet(id = 1)))))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is PetDetailUiState.Failed)
  }

  @Test
  fun uiState_onRepositoryFailure_isFailed() {
    val viewModel = PetDetailViewModel(petId = 1, petRepository = FakePetRepository(Result.failure(TraccarError.Server(500))))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is PetDetailUiState.Failed)
  }

  @Test
  fun uiState_whenPermissionsFail_loadsPetWithNullPermissions() {
    val viewModel =
      PetDetailViewModel(
        petId = 1,
        petRepository = FakePetRepository(Result.success(listOf(pet(id = 1))), permissionsFail = true),
      )
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetDetailUiState.Loaded)
    assertNull((state as PetDetailUiState.Loaded).permissions)
  }

  @Test
  fun uiState_whenPermissionsSucceed_loadsPetWithThem() {
    val viewModel = PetDetailViewModel(petId = 1, petRepository = FakePetRepository(Result.success(listOf(pet(id = 1)))))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetDetailUiState.Loaded)
    assertTrue((state as PetDetailUiState.Loaded).permissions?.canManageDevice == true)
  }

  @Test
  fun totalTrailDistanceMeters_sumsConsecutiveFixes() {
    val trail =
      listOf(
        position(latitude = 0.0, longitude = 0.0),
        position(latitude = 0.0, longitude = 0.01),
        position(latitude = 0.0, longitude = 0.02),
      )
    val repository = FakePetRepository(Result.success(listOf(pet(id = 1))), trail = trail)
    val viewModel = PetDetailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    val expected =
      Coordinate(0.0, 0.0).distanceMeters(Coordinate(0.0, 0.01)) + Coordinate(0.0, 0.01).distanceMeters(Coordinate(0.0, 0.02))
    assertEquals(expected, viewModel.totalTrailDistanceMeters!!, 0.01)
  }

  @Test
  fun totalTrailDistanceMeters_withFewerThanTwoPositions_isNull() {
    val repository = FakePetRepository(Result.success(listOf(pet(id = 1))), trail = listOf(position(0.0, 0.0)))
    val viewModel = PetDetailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.totalTrailDistanceMeters)
  }

  @Test
  fun totalTimeAwayTodayMillis_excludesLikelyVehicleTrips() {
    val walked = excursion(id = 1, startTime = 0L, endTime = 10 * 60_000L, distanceMeters = 500.0)
    val driven = excursion(id = 2, startTime = 0L, endTime = 10 * 60_000L, distanceMeters = 50_000.0)
    val repository = FakePetRepository(Result.success(listOf(pet(id = 1))), todaysExcursions = listOf(walked, driven))
    val viewModel = PetDetailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(10 * 60_000L, viewModel.totalTimeAwayTodayMillis)
  }

  @Test
  fun lifetimeStats_loadsFromRepository() {
    val stats = LifetimeStats(totalDistanceMeters = 1234.0, totalDurationMillis = 5_678_000L)
    val repository = FakePetRepository(Result.success(listOf(pet(id = 1))), lifetimeStats = stats)
    val viewModel = PetDetailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(stats, viewModel.lifetimeStats)
  }

  @Test
  fun recalculate_resetsCachesThenReloads() {
    val repository = FakePetRepository(Result.success(listOf(pet(id = 1))))
    val viewModel = PetDetailViewModel(petId = 1, petRepository = repository)
    dispatcher.scheduler.advanceUntilIdle()

    repository.lifetimeStats = LifetimeStats(totalDistanceMeters = 999.0, totalDurationMillis = 111L)
    viewModel.recalculate()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(repository.didResetExcursionsCache)
    assertTrue(repository.didResetLifetimeStats)
    assertEquals(999.0, viewModel.lifetimeStats?.totalDistanceMeters)
  }

  private fun pet(id: Int) =
    Pet(id = id, name = "Pet$id", species = null, status = Pet.Status.ONLINE, lastSeenAt = null, lastPosition = null, photoUrl = null)

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

  private fun excursion(id: Int, startTime: Long, endTime: Long?, distanceMeters: Double?) =
    Excursion(
      id = id.toLong(),
      startTime = startTime,
      endTime = endTime,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = distanceMeters,
      startHomeId = null,
      endHomeId = null,
    )
}

private class FakePetRepository(
  private val result: Result<List<Pet>>,
  private val permissionsFail: Boolean = false,
  private val trail: List<PetPosition> = emptyList(),
  private val todaysExcursions: List<Excursion> = emptyList(),
  var lifetimeStats: LifetimeStats = LifetimeStats(totalDistanceMeters = 0.0, totalDurationMillis = 0L),
) : PetRepository {
  var didResetExcursionsCache = false
    private set

  var didResetLifetimeStats = false
    private set

  override suspend fun fetchPets(): List<Pet> = result.getOrThrow()

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = null

  override suspend fun currentUserPermissions(): AccountPermissions {
    if (permissionsFail) throw TraccarError.Server(500)
    return AccountPermissions(isAdministrator = true, canManageHomes = true, canManageDevice = true, canViewExcursions = true)
  }

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long) = trail

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = emptyList()

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun deleteHomeGeofence(homeId: Int) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long) = todaysExcursions

  override suspend fun resetExcursionsCache(petId: Int) {
    didResetExcursionsCache = true
  }

  override suspend fun lifetimeExcursionStats(petId: Int) = lifetimeStats

  override suspend fun resetLifetimeStats(petId: Int) {
    didResetLifetimeStats = true
  }

  override suspend fun sharePet(petId: Int, email: String) = throw UnsupportedOperationException("not used by this test")
}

package org.librepaw.app.ui.sharepet

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
import org.librepaw.app.R
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.HomeGeofence
import org.librepaw.app.data.pets.LifetimeStats
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class SharePetViewModelTest {
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
  fun share_withBlankEmail_setsErrorWithoutCallingRepository() {
    val repository = FakePetRepository()
    val viewModel = SharePetViewModel(petId = 1, petName = "Buddy", petRepository = repository)
    var succeeded = false

    viewModel.share(onSuccess = { succeeded = true })
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.errorMessage != null)
    assertFalse(succeeded)
    assertFalse(repository.didShare)
  }

  @Test
  fun share_withValidEmail_sharesAndCallsOnSuccess() {
    val repository = FakePetRepository()
    val viewModel = SharePetViewModel(petId = 1, petName = "Buddy", petRepository = repository)
    viewModel.updateEmail("friend@example.com")
    var succeeded = false

    viewModel.share(onSuccess = { succeeded = true })
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(repository.didShare)
    assertEquals("friend@example.com", repository.sharedEmail)
    assertTrue(succeeded)
    assertNull(viewModel.errorMessage)
    assertFalse(viewModel.isSharing)
  }

  @Test
  fun share_trimsEmailWhitespace() {
    val repository = FakePetRepository()
    val viewModel = SharePetViewModel(petId = 1, petName = "Buddy", petRepository = repository)
    viewModel.updateEmail("  friend@example.com  ")

    viewModel.share(onSuccess = {})
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("friend@example.com", repository.sharedEmail)
  }

  @Test
  fun share_onFailure_setsErrorMessage() {
    val repository = FakePetRepository(failWith = TraccarError.NotFound("No user with that email was found on this server."))
    val viewModel = SharePetViewModel(petId = 1, petName = "Buddy", petRepository = repository)
    viewModel.updateEmail("nobody@example.com")
    var succeeded = false

    viewModel.share(onSuccess = { succeeded = true })
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(UiText(R.string.error_user_not_found), viewModel.errorMessage)
    assertFalse(succeeded)
    assertFalse(viewModel.isSharing)
  }
}

private class FakePetRepository(private val failWith: TraccarError? = null) : PetRepository {
  var didShare = false
    private set

  var sharedEmail: String? = null
    private set

  override suspend fun fetchPets(): List<Pet> = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = throw UnsupportedOperationException("not used by this test")

  override suspend fun currentUserPermissions(): AccountPermissions = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) = throw UnsupportedOperationException("not used by this test")

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) = throw UnsupportedOperationException("not used by this test")

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> =
    throw UnsupportedOperationException("not used by this test")

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = throw UnsupportedOperationException("not used by this test")

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

  override suspend fun sharePet(petId: Int, email: String) {
    failWith?.let { throw it }
    didShare = true
    sharedEmail = email
  }
}

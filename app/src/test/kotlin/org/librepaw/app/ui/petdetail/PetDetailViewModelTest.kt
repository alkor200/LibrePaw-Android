package org.librepaw.app.ui.petdetail

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

  private fun pet(id: Int) =
    Pet(id = id, name = "Pet$id", species = null, status = Pet.Status.ONLINE, lastSeenAt = null, lastPosition = null, photoUrl = null)
}

private class FakePetRepository(private val result: Result<List<Pet>>) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = result.getOrThrow()

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = null
}

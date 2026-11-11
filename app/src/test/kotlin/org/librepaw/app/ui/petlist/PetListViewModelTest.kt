package org.librepaw.app.ui.petlist

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
class PetListViewModelTest {
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
  fun uiState_onSuccess_isLoadedSortedByName() {
    val pets = listOf(pet(id = 1, name = "Zoe"), pet(id = 2, name = "Amy"))
    val viewModel = PetListViewModel(FakePetRepository(result = Result.success(pets)))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetListUiState.Loaded)
    assertEquals(listOf("Amy", "Zoe"), (state as PetListUiState.Loaded).pets.map { it.name })
  }

  @Test
  fun uiState_onFailure_isFailed() {
    val viewModel = PetListViewModel(FakePetRepository(result = Result.failure(TraccarError.Server(500))))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is PetListUiState.Failed)
  }

  @Test
  fun refresh_resetsToLoadingFirst() {
    val viewModel = PetListViewModel(FakePetRepository(result = Result.success(emptyList())))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.refresh()
    assertEquals(PetListUiState.Loading, viewModel.uiState.value)
  }

  private fun pet(id: Int, name: String) =
    Pet(id = id, name = name, species = null, status = Pet.Status.ONLINE, lastSeenAt = null, lastPosition = null, photoUrl = null)
}

private class FakePetRepository(private val result: Result<List<Pet>>) : PetRepository {
  override suspend fun fetchPets(): List<Pet> = result.getOrThrow()

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = null
}

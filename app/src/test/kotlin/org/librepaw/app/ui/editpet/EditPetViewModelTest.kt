package org.librepaw.app.ui.editpet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EditPetViewModelTest {
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
  fun hasChanges_falseUntilNameOrPhotoChanges() {
    val repository = FakePetRepository(listOf(pet(1, "Buddy")))
    val viewModel = EditPetViewModel(1, repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.hasChanges())
    viewModel.updateName("Buddy")
    assertFalse(viewModel.hasChanges())
    viewModel.updateName("Rex")
    assertTrue(viewModel.hasChanges())
  }

  @Test
  fun save_onlyRenamesWhenNameChanged() {
    val repository = FakePetRepository(listOf(pet(1, "Buddy")))
    val viewModel = EditPetViewModel(1, repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.updateName("Rex")
    var saved = false
    viewModel.save(croppedJpeg = null) { saved = true }
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(saved)
    assertEquals(listOf("Rex"), repository.renamedTo)
    assertEquals(0, repository.photosSet)
  }

  @Test
  fun save_uploadsPhotoWhenProvided() {
    val repository = FakePetRepository(listOf(pet(1, "Buddy")))
    val viewModel = EditPetViewModel(1, repository)
    dispatcher.scheduler.advanceUntilIdle()

    var saved = false
    viewModel.save(croppedJpeg = ByteArray(10)) { saved = true }
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(saved)
    assertEquals(emptyList<String>(), repository.renamedTo)
    assertEquals(1, repository.photosSet)
  }

  private fun pet(id: Int, name: String) =
    Pet(id = id, name = name, species = null, status = Pet.Status.ONLINE, lastSeenAt = null, lastPosition = null, photoUrl = null)
}

private class FakePetRepository(private val pets: List<Pet>) : PetRepository {
  val renamedTo = mutableListOf<String>()
  var photosSet = 0

  override suspend fun fetchPets(): List<Pet> = pets

  override suspend fun fetchPhoto(pet: Pet): ByteArray? = null

  override suspend fun currentUserPermissions() = throw UnsupportedOperationException("not used by this test")

  override suspend fun renamePet(petId: Int, name: String) {
    renamedTo += name
  }

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) {
    photosSet++
  }
}

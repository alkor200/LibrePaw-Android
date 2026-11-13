package org.librepaw.app.ui.editpet

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.ImageDecoding

sealed interface EditPetUiState {
  object Loading : EditPetUiState

  data class Loaded(val pet: Pet, val currentPhoto: Bitmap?) : EditPetUiState

  data class Failed(val message: String) : EditPetUiState
}

class EditPetViewModel(private val petId: Int, private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<EditPetUiState>(EditPetUiState.Loading)
  val uiState: StateFlow<EditPetUiState> = _uiState.asStateFlow()

  var name by mutableStateOf("")
    private set

  var selectedImage by mutableStateOf<Bitmap?>(null)
    private set

  var isSaving by mutableStateOf(false)
    private set

  var errorMessage by mutableStateOf<String?>(null)
    private set

  private var originalName = ""

  init {
    load()
  }

  private fun load() {
    viewModelScope.launch {
      _uiState.value =
        try {
          val pet = petRepository.fetchPets().firstOrNull { it.id == petId }
          if (pet == null) {
            EditPetUiState.Failed("This pet could not be found.")
          } else {
            name = pet.name
            originalName = pet.name
            val photoBytes = try { petRepository.fetchPhoto(pet) } catch (e: TraccarError) { null }
            val bitmap = photoBytes?.let { ImageDecoding.decode(it, maxDimension = PHOTO_MAX_DIMENSION_PX) }
            EditPetUiState.Loaded(pet, bitmap)
          }
        } catch (e: TraccarError) {
          EditPetUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }

  fun updateName(value: String) {
    name = value
  }

  fun selectImage(bitmap: Bitmap) {
    selectedImage = bitmap
  }

  fun hasChanges(): Boolean = name.trim() != originalName || selectedImage != null

  /**
   * [renderCroppedJpeg] produces the photo to upload, `null` for none. A suspend function rather
   * than the bytes themselves so the screen can render the crop off the main thread while
   * [isSaving] already blocks a second tap.
   */
  fun save(renderCroppedJpeg: suspend () -> ByteArray?, onSuccess: () -> Unit) {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return

    isSaving = true
    errorMessage = null
    viewModelScope.launch {
      try {
        if (trimmedName != originalName) {
          petRepository.renamePet(petId, trimmedName)
          originalName = trimmedName
        }
        val croppedJpeg = renderCroppedJpeg()
        if (croppedJpeg != null) {
          petRepository.setPhoto(petId, croppedJpeg)
        }
        isSaving = false
        onSuccess()
      } catch (e: TraccarError) {
        isSaving = false
        errorMessage = e.message ?: "Something went wrong."
      }
    }
  }
}

/** The server's own photos are 512px crops (see renderCircularCropJpeg), with room for older, larger ones. */
private const val PHOTO_MAX_DIMENSION_PX = 1024

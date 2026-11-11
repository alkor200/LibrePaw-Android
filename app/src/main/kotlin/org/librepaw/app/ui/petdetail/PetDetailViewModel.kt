package org.librepaw.app.ui.petdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

sealed interface PetDetailUiState {
  object Loading : PetDetailUiState

  data class Loaded(val pet: Pet, val permissions: AccountPermissions?) : PetDetailUiState

  data class Failed(val message: String) : PetDetailUiState
}

class PetDetailViewModel(private val petId: Int, private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<PetDetailUiState>(PetDetailUiState.Loading)
  val uiState: StateFlow<PetDetailUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _uiState.value = PetDetailUiState.Loading
    viewModelScope.launch {
      _uiState.value =
        try {
          val pet = petRepository.fetchPets().firstOrNull { it.id == petId }
          if (pet == null) {
            PetDetailUiState.Failed("This pet could not be found.")
          } else {
            // Best-effort and fail-closed: if permissions can't be determined, the edit
            // affordance that depends on them simply stays hidden, same as iOS.
            val permissions = try { petRepository.currentUserPermissions() } catch (e: TraccarError) { null }
            PetDetailUiState.Loaded(pet, permissions)
          }
        } catch (e: TraccarError) {
          PetDetailUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }
}

package org.librepaw.app.ui.petlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

sealed interface PetListUiState {
  object Loading : PetListUiState

  data class Loaded(val pets: List<Pet>) : PetListUiState

  data class Failed(val message: String) : PetListUiState
}

class PetListViewModel(private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<PetListUiState>(PetListUiState.Loading)
  val uiState: StateFlow<PetListUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _uiState.value = PetListUiState.Loading
    viewModelScope.launch {
      _uiState.value =
        try {
          PetListUiState.Loaded(
            petRepository.fetchPets().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
          )
        } catch (e: TraccarError) {
          PetListUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }
}

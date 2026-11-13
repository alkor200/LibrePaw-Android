package org.librepaw.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText

sealed interface PetMapUiState {
  object Loading : PetMapUiState

  data class Loaded(val pets: List<Pet>) : PetMapUiState

  data class Failed(val message: UiText) : PetMapUiState
}

class PetMapViewModel(private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<PetMapUiState>(PetMapUiState.Loading)
  val uiState: StateFlow<PetMapUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _uiState.value = PetMapUiState.Loading
    viewModelScope.launch {
      _uiState.value =
        try {
          PetMapUiState.Loaded(petRepository.fetchPets().filter { it.lastPosition != null })
        } catch (e: TraccarError) {
          PetMapUiState.Failed(e.toUiText())
        }
    }
  }
}

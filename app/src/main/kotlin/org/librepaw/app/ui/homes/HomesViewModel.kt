package org.librepaw.app.ui.homes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.HomeGeofence
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

sealed interface HomesUiState {
  object Loading : HomesUiState

  data class Loaded(val homes: List<HomeGeofence>, val permissions: AccountPermissions?) : HomesUiState

  data class Failed(val message: String) : HomesUiState
}

class HomesViewModel(private val petId: Int, private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<HomesUiState>(HomesUiState.Loading)
  val uiState: StateFlow<HomesUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _uiState.value = HomesUiState.Loading
    viewModelScope.launch {
      _uiState.value =
        try {
          coroutineScope {
            val homesDeferred = async { petRepository.homeGeofences(petId) }
            val permissions = try { petRepository.currentUserPermissions() } catch (e: TraccarError) { null }
            HomesUiState.Loaded(homesDeferred.await(), permissions)
          }
        } catch (e: TraccarError) {
          HomesUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }

  fun deleteHome(homeId: Int) {
    viewModelScope.launch {
      try {
        petRepository.deleteHomeGeofence(homeId)
      } catch (e: TraccarError) {
        // Fall through to refresh() either way so the list reflects the server's actual state.
      }
      refresh()
    }
  }
}

package org.librepaw.app.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.librepaw.app.R
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText
import org.librepaw.app.ui.petdetail.bestEffort

sealed interface FindPetUiState {
  data object Loading : FindPetUiState

  data class Loaded(val pet: Pet) : FindPetUiState

  data class Failed(val message: UiText) : FindPetUiState
}

private const val POLL_INTERVAL_MILLIS = 5_000L

/**
 * Keeps the pet's position fresh while the Find screen is open, so the compass arrow and
 * distance readout track a moving pet. Polls [PetRepository.fetchPets] on an interval rather
 * than subscribing to a live feed: unlike iOS's own `livePetUpdates()` (ADR-0013), this
 * repository only exposes a one-shot fetch. The screen drives [pollPositions] only while it's
 * actually visible, see FindPetScreen.
 */
class FindPetViewModel(private val petId: Int, private val petRepository: PetRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<FindPetUiState>(FindPetUiState.Loading)
  val uiState: StateFlow<FindPetUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _uiState.value =
        try {
          val pet = petRepository.fetchPets().firstOrNull { it.id == petId }
          if (pet == null) FindPetUiState.Failed(UiText(R.string.pet_not_found)) else FindPetUiState.Loaded(pet)
        } catch (e: TraccarError) {
          FindPetUiState.Failed(e.toUiText())
        }
    }
  }

  /**
   * Refreshes the pet every [POLL_INTERVAL_MILLIS] until cancelled. Suspends instead of running in
   * [viewModelScope] so the caller decides how long it runs: it used to start in `init` and only
   * stop in [onCleared], polling the server every 5 seconds for as long as the ViewModel lived,
   * app in the background included.
   */
  suspend fun pollPositions() {
    while (true) {
      delay(POLL_INTERVAL_MILLIS)
      val pets = bestEffort<List<Pet>?>(null) { petRepository.fetchPets() } ?: continue
      val updated = pets.firstOrNull { it.id == petId } ?: continue
      _uiState.update { state -> if (state is FindPetUiState.Loaded) state.copy(pet = updated) else FindPetUiState.Loaded(updated) }
    }
  }
}

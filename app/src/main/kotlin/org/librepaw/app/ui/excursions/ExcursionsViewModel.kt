package org.librepaw.app.ui.excursions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

sealed interface ExcursionsUiState {
  object Loading : ExcursionsUiState

  data class Loaded(val excursions: List<Excursion>, val homeNamesById: Map<Int, String>) : ExcursionsUiState

  data class Failed(val message: String) : ExcursionsUiState
}

/**
 * Lists a pet's excursions over the trailing [lookbackDays] (default a week, same as iOS), most
 * recent first, alongside its home geofences so each row can resolve [Excursion.startHomeId]/
 * [Excursion.endHomeId] to a name (e.g. "Home → Cabin") instead of raw ids.
 */
class ExcursionsViewModel(
  private val petId: Int,
  private val petRepository: PetRepository,
  private val lookbackDays: Int = 7,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ExcursionsUiState>(ExcursionsUiState.Loading)
  val uiState: StateFlow<ExcursionsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _uiState.value = ExcursionsUiState.Loading
    viewModelScope.launch {
      _uiState.value =
        try {
          coroutineScope {
            val from = daysAgoEpochMillis(lookbackDays)
            val excursionsDeferred = async { petRepository.fetchExcursions(petId, from, System.currentTimeMillis()) }
            val homesDeferred = async { petRepository.homeGeofences(petId) }
            val excursions = excursionsDeferred.await().sortedByDescending { it.startTime }
            val homeNamesById = homesDeferred.await().associate { it.id to it.name }
            ExcursionsUiState.Loaded(excursions, homeNamesById)
          }
        } catch (e: TraccarError) {
          ExcursionsUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }

  private fun daysAgoEpochMillis(days: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -days)
    return calendar.timeInMillis
  }
}

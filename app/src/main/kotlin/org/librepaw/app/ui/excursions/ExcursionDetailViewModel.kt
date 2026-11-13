package org.librepaw.app.ui.excursions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText

class ExcursionDetailViewModel(val excursion: Excursion, private val petId: Int, private val petRepository: PetRepository) :
  ViewModel() {
  var trail by mutableStateOf<List<PetPosition>>(emptyList())
    private set

  var isLoading by mutableStateOf(true)
    private set

  var errorMessage by mutableStateOf<UiText?>(null)
    private set

  private var homeNamesById: Map<Int, String> by mutableStateOf(emptyMap())

  init {
    loadTrail()
  }

  private fun loadTrail() {
    viewModelScope.launch {
      isLoading = true
      errorMessage = null
      try {
        coroutineScope {
          val trailDeferred = async {
            petRepository.fetchTrail(petId, excursion.startTime, excursion.endTime ?: System.currentTimeMillis())
          }
          val homesDeferred = async { petRepository.homeGeofences(petId) }
          trail = trailDeferred.await()
          homeNamesById = homesDeferred.await().associate { it.id to it.name }
        }
      } catch (e: TraccarError) {
        errorMessage = e.toUiText()
      } finally {
        isLoading = false
      }
    }
  }

  /**
   * `"Home → Cabin"` when the excursion started and ended at different known homes, `null` for an
   * ordinary round trip.
   */
  val routeText: String?
    get() {
      val startId = excursion.startHomeId
      val endId = excursion.endHomeId
      if (startId == null || endId == null || startId == endId) return null
      val start = homeNamesById[startId] ?: return null
      val end = homeNamesById[endId] ?: return null
      return "$start → $end"
    }
}

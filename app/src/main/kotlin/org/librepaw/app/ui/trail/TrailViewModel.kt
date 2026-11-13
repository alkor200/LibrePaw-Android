package org.librepaw.app.ui.trail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.PetPosition
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText

/**
 * Aggregates every excursion in an adjustable window (default 24 hours), each with its own
 * trail, for [TrailScreen] to draw as a separate colored route. Replaces the old single-day
 * "Today's Trail" view (see ADR-0020 on the iOS side).
 */
class TrailViewModel(private val petId: Int, private val petRepository: PetRepository) : ViewModel() {
  var window by mutableStateOf(TrailWindow.ONE_DAY)
    private set

  var excursions by mutableStateOf<List<Excursion>>(emptyList())
    private set

  var trailsByExcursionId by mutableStateOf<Map<Long, List<PetPosition>>>(emptyMap())
    private set

  var isLoading by mutableStateOf(true)
    private set

  var errorMessage by mutableStateOf<UiText?>(null)
    private set

  /** The most recently started load, so switching [window] cancels a stale one still in flight
   * instead of letting both race to set the published state. */
  private var loadJob: Job? = null

  init {
    load()
  }

  fun selectWindow(newWindow: TrailWindow) {
    if (window == newWindow) return
    window = newWindow
    load()
  }

  fun retry() = load()

  private fun load() {
    loadJob?.cancel()
    loadJob =
      viewModelScope.launch {
        isLoading = true
        errorMessage = null
        try {
          val to = System.currentTimeMillis()
          val from = to - window.millis
          val fetchedExcursions = petRepository.fetchExcursions(petId, from, to)
          val fetchedTrails = fetchTrails(fetchedExcursions, to)
          excursions = fetchedExcursions
          trailsByExcursionId = fetchedTrails
          isLoading = false
        } catch (e: CancellationException) {
          throw e
        } catch (e: TraccarError) {
          errorMessage = e.toUiText()
          isLoading = false
        }
      }
  }

  /** One position-history fetch per excursion, all concurrently, each bounded to that
   * excursion's own start/end (or now, if still ongoing) so the drawn route matches exactly
   * what [org.librepaw.app.ui.excursions.ExcursionDetailScreen] would show for the same excursion. */
  private suspend fun fetchTrails(excursions: List<Excursion>, nowEpochMillis: Long): Map<Long, List<PetPosition>> =
    coroutineScope {
      excursions
        .map { excursion ->
          async { excursion.id to petRepository.fetchTrail(petId, excursion.startTime, excursion.endTime ?: nowEpochMillis) }
        }
        .awaitAll()
        .toMap()
    }
}

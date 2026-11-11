package org.librepaw.app.ui.petdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.LifetimeStats
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetPosition
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

  /** Today's position history, for [totalTrailDistanceMeters]. Best-effort, same reasoning as [lifetimeStats]. */
  var trail by mutableStateOf<List<PetPosition>>(emptyList())
    private set

  /**
   * Lifetime distance/time-away totals. Best-effort: stays `null` on failure rather than
   * surfacing an error, since this is a secondary stat alongside today's trail.
   */
  var lifetimeStats by mutableStateOf<LifetimeStats?>(null)
    private set

  /** Today's excursions, for [totalTimeAwayTodayMillis]. Best-effort, same reasoning as [lifetimeStats]. */
  var todaysExcursions by mutableStateOf<List<Excursion>>(emptyList())
    private set

  var isRecalculating by mutableStateOf(false)
    private set

  init {
    refresh()
    loadTodaysTrail()
    loadLifetimeStats()
    loadTodaysExcursions()
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

  private fun loadTodaysTrail() {
    viewModelScope.launch {
      trail = bestEffort(emptyList()) { petRepository.fetchTrail(petId, startOfTodayEpochMillis(), nowEpochMillis()) }
    }
  }

  private fun loadLifetimeStats() {
    viewModelScope.launch { lifetimeStats = fetchLifetimeStats() }
  }

  private fun loadTodaysExcursions() {
    viewModelScope.launch { todaysExcursions = fetchTodaysExcursions() }
  }

  /**
   * Discards both the cached excursions and the cached lifetime totals built from them, then
   * recomputes everything from the pet's entire excursion history, for recovering from bad
   * cached data (e.g. an excursion a since-fixed bug had closed wrong). Clears the excursions
   * cache before the lifetime stats one: the latter is itself built from fetchExcursions,
   * clearing it first would just re-read the still-bad cached excursions right back in.
   */
  fun recalculate() {
    viewModelScope.launch {
      isRecalculating = true
      try {
        bestEffort(Unit) { petRepository.resetExcursionsCache(petId) }
        bestEffort(Unit) { petRepository.resetLifetimeStats(petId) }
        lifetimeStats = fetchLifetimeStats()
        todaysExcursions = fetchTodaysExcursions()
      } finally {
        isRecalculating = false
      }
    }
  }

  private suspend fun fetchLifetimeStats(): LifetimeStats? = bestEffort(null) { petRepository.lifetimeExcursionStats(petId) }

  private suspend fun fetchTodaysExcursions(): List<Excursion> =
    bestEffort(emptyList()) { petRepository.fetchExcursions(petId, startOfTodayEpochMillis(), nowEpochMillis()) }

  /**
   * Total distance covered along today's trail, summing consecutive fixes. `null` until there
   * are at least two points to measure between.
   */
  val totalTrailDistanceMeters: Double?
    get() {
      if (trail.size <= 1) return null
      return trail.zipWithNext().sumOf { (a, b) ->
        Coordinate(a.latitude, a.longitude).distanceMeters(Coordinate(b.latitude, b.longitude))
      }
    }

  /**
   * Total time spent away from home today, summing every excursion that started today (an
   * ongoing one contributes its elapsed-so-far duration). Likely-vehicle trips are excluded, same
   * reasoning as the lifetime total. `null` until there's at least one excursion to show.
   */
  val totalTimeAwayTodayMillis: Long?
    get() {
      val walked = todaysExcursions.filterNot { it.isLikelyVehicleTrip }
      if (walked.isEmpty()) return null
      return walked.sumOf { it.durationMillis ?: (nowEpochMillis() - it.startTime) }
    }

  private fun nowEpochMillis(): Long = System.currentTimeMillis()

  private fun startOfTodayEpochMillis(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }
}

/**
 * Runs [block], falling back to [fallback] on any failure, cancellation excepted so cooperative
 * coroutine cancellation still propagates. For secondary stats where surfacing an error isn't
 * worth it, same reasoning as iOS's `try?`.
 */
internal suspend fun <T> bestEffort(fallback: T, block: suspend () -> T): T =
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    fallback
  }

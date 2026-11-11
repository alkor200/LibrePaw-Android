package org.librepaw.app.ui.homes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

private const val DEFAULT_RADIUS_METERS = 50.0

sealed interface SetHomeUiState {
  object Loading : SetHomeUiState

  object Ready : SetHomeUiState

  data class Failed(val message: String) : SetHomeUiState
}

class SetHomeViewModel(private val petId: Int, private val homeId: Int?, private val petRepository: PetRepository) :
  ViewModel() {
  private val _uiState = MutableStateFlow<SetHomeUiState>(SetHomeUiState.Loading)
  val uiState: StateFlow<SetHomeUiState> = _uiState.asStateFlow()

  val isNew: Boolean get() = homeId == null

  var name by mutableStateOf("")
    private set

  var center by mutableStateOf(Coordinate(0.0, 0.0))
    private set

  var radiusMeters by mutableStateOf(DEFAULT_RADIUS_METERS)
    private set

  var isSaving by mutableStateOf(false)
    private set

  var errorMessage by mutableStateOf<String?>(null)
    private set

  var permissions: AccountPermissions? by mutableStateOf(null)
    private set

  init {
    load()
  }

  private fun load() {
    viewModelScope.launch {
      _uiState.value =
        try {
          coroutineScope {
            val permissionsDeferred = async { try { petRepository.currentUserPermissions() } catch (e: TraccarError) { null } }
            if (homeId != null) {
              val home = petRepository.homeGeofences(petId).firstOrNull { it.id == homeId }
              permissions = permissionsDeferred.await()
              if (home == null) {
                SetHomeUiState.Failed("This home could not be found.")
              } else {
                name = home.name
                center = home.center
                radiusMeters = home.radiusMeters
                SetHomeUiState.Ready
              }
            } else {
              val pet = petRepository.fetchPets().firstOrNull { it.id == petId }
              permissions = permissionsDeferred.await()
              val position = pet?.lastPosition
              if (position != null) center = Coordinate(position.latitude, position.longitude)
              name = "Home"
              SetHomeUiState.Ready
            }
          }
        } catch (e: TraccarError) {
          SetHomeUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
  }

  fun updateName(value: String) {
    name = value
  }

  fun updateCenter(value: Coordinate) {
    center = value
  }

  fun updateRadius(value: Double) {
    radiusMeters = value
  }

  fun save(onSuccess: () -> Unit) {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return
    isSaving = true
    errorMessage = null
    viewModelScope.launch {
      try {
        if (homeId != null) {
          petRepository.updateHomeGeofence(homeId, petId, trimmedName, center, radiusMeters)
        } else {
          petRepository.addHomeGeofence(petId, trimmedName, center, radiusMeters)
        }
        isSaving = false
        onSuccess()
      } catch (e: TraccarError) {
        isSaving = false
        errorMessage = e.message ?: "Something went wrong."
      }
    }
  }

  fun delete(onSuccess: () -> Unit) {
    val id = homeId ?: return
    isSaving = true
    errorMessage = null
    viewModelScope.launch {
      try {
        petRepository.deleteHomeGeofence(id)
        isSaving = false
        onSuccess()
      } catch (e: TraccarError) {
        isSaving = false
        errorMessage = e.message ?: "Something went wrong."
      }
    }
  }
}

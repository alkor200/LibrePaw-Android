package org.librepaw.app.ui.sharepet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.librepaw.app.R
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText

/** Grants another Traccar account access to this pet, by email, admin-only server-side (see
 * ADR-0018 on the iOS side). */
class SharePetViewModel(private val petId: Int, private val petName: String, private val petRepository: PetRepository) :
  ViewModel() {
  var email by mutableStateOf("")
    private set

  var isSharing by mutableStateOf(false)
    private set

  var errorMessage by mutableStateOf<UiText?>(null)
    private set

  fun updateEmail(value: String) {
    email = value
  }

  fun share(onSuccess: () -> Unit) {
    val trimmedEmail = email.trim()
    if (trimmedEmail.isEmpty()) {
      errorMessage = UiText(R.string.share_pet_email_missing, petName)
      return
    }

    isSharing = true
    errorMessage = null
    viewModelScope.launch {
      try {
        petRepository.sharePet(petId, trimmedEmail)
        isSharing = false
        onSuccess()
      } catch (e: TraccarError) {
        isSharing = false
        errorMessage = e.toUiText()
      }
    }
  }
}

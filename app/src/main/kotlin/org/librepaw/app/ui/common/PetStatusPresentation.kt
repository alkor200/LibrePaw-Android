package org.librepaw.app.ui.common

import androidx.annotation.StringRes
import org.librepaw.app.R
import org.librepaw.app.data.pets.Pet

/** Shared status -> color/label mapping, used by both the Pet List grid and the Map's markers. */
object PetStatusPresentation {
  fun colorArgb(status: Pet.Status): Int =
    when (status) {
      Pet.Status.ONLINE -> 0xFF2E7D32.toInt()
      Pet.Status.OFFLINE -> 0xFF9E9E9E.toInt()
      Pet.Status.UNKNOWN -> 0xFFFF8F00.toInt()
    }

  @StringRes
  fun label(status: Pet.Status): Int =
    when (status) {
      Pet.Status.ONLINE -> R.string.pet_status_online
      Pet.Status.OFFLINE -> R.string.pet_status_offline
      Pet.Status.UNKNOWN -> R.string.pet_status_unknown
    }
}

package org.librepaw.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Login : NavKey

@Serializable data class PetDetail(val petId: Int) : NavKey

@Serializable data class EditPet(val petId: Int) : NavKey

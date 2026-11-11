package org.librepaw.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.librepaw.app.data.pets.Excursion

@Serializable data object Main : NavKey

@Serializable data object Login : NavKey

@Serializable data class PetDetail(val petId: Int) : NavKey

@Serializable data class EditPet(val petId: Int) : NavKey

@Serializable data class Homes(val petId: Int, val petName: String) : NavKey

@Serializable data class SetHome(val petId: Int, val homeId: Int?) : NavKey

@Serializable data class Excursions(val petId: Int, val petName: String) : NavKey

@Serializable data class ExcursionDetail(val petId: Int, val excursion: Excursion) : NavKey

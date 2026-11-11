package org.librepaw.app.data.pets

/** Reads pets and their positions for the currently authenticated session. */
interface PetRepository {
  /** All pets visible to the current user, each with its latest known position attached. */
  suspend fun fetchPets(): List<Pet>

  /** The pet's photo bytes, `null` when it has none set. */
  suspend fun fetchPhoto(pet: Pet): ByteArray?
}

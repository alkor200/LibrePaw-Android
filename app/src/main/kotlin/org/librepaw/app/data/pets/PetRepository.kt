package org.librepaw.app.data.pets

/** Reads pets and their positions for the currently authenticated session. */
interface PetRepository {
  /** All pets visible to the current user, each with its latest known position attached. */
  suspend fun fetchPets(): List<Pet>

  /** The pet's photo bytes, `null` when it has none set. */
  suspend fun fetchPhoto(pet: Pet): ByteArray?

  /** What the signed-in Traccar account can actually do. */
  suspend fun currentUserPermissions(): AccountPermissions

  /** Renames the pet, i.e. its underlying Traccar device. */
  suspend fun renamePet(petId: Int, name: String)

  /** Sets (or replaces) the pet's photo. `jpegData` must already be JPEG-encoded. */
  suspend fun setPhoto(petId: Int, jpegData: ByteArray)
}

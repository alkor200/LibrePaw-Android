package org.librepaw.app.data.pets

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.librepaw.app.data.traccar.TraccarClient

/** [PetRepository] backed by a live Traccar server. */
class TraccarPetRepository(private val client: TraccarClient) : PetRepository {
  override suspend fun fetchPets(): List<Pet> =
    coroutineScope {
      val devicesDeferred = async { client.fetchDevices() }
      val positionsDeferred = async { client.fetchLatestPositions() }
      val positionsByDeviceId = positionsDeferred.await().associateBy { it.deviceId }
      devicesDeferred.await().map { device ->
        PetMapping.pet(device, positionsByDeviceId[device.id], client.serverUrl)
      }
    }

  override suspend fun fetchPhoto(pet: Pet): ByteArray? {
    val photoUrl = pet.photoUrl ?: return null
    return client.fetchData(photoUrl)
  }

  override suspend fun currentUserPermissions(): AccountPermissions {
    val user = client.fetchCurrentUser()
    return AccountPermissions(
      isAdministrator = user.administrator,
      canManageHomes = user.administrator || !user.readonly,
      canManageDevice = user.administrator || (!user.readonly && !user.deviceReadonly),
      canViewExcursions = user.administrator || !user.disableReports,
    )
  }

  override suspend fun renamePet(petId: Int, name: String) {
    client.updateDeviceName(petId, name)
  }

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) {
    client.updateDeviceImage(petId, jpegData, "image/jpeg")
  }
}

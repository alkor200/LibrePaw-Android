package org.librepaw.app.data.pets

import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarDevice
import org.librepaw.app.data.traccar.TraccarPosition

class PetMappingTest {
  private val serverUrl = "https://traccar.example.com/".toHttpUrl()

  @Test
  fun pet_mapsStatusAndSpecies() {
    val device = device(status = "online", category = "dog")
    val pet = PetMapping.pet(device, latestPosition = null, serverUrl = serverUrl)

    assertEquals(Pet.Status.ONLINE, pet.status)
    assertEquals("dog", pet.species)
  }

  @Test
  fun pet_unknownTraccarStatus_mapsToUnknown() {
    val pet = PetMapping.pet(device(status = "whatever"), latestPosition = null, serverUrl = serverUrl)
    assertEquals(Pet.Status.UNKNOWN, pet.status)
  }

  @Test
  fun pet_withoutDeviceImageAttribute_hasNoPhotoUrl() {
    val pet = PetMapping.pet(device(), latestPosition = null, serverUrl = serverUrl)
    assertNull(pet.photoUrl)
  }

  @Test
  fun pet_withDeviceImageAttribute_buildsMediaUrl() {
    val device = device(attributes = mapOf("deviceImage" to JsonPrimitive("buddy.jpg")))
    val pet = PetMapping.pet(device, latestPosition = null, serverUrl = serverUrl)
    assertEquals("https://traccar.example.com/api/media/860000000000099/buddy.jpg", pet.photoUrl.toString())
  }

  @Test
  fun pet_parsesLastUpdate() {
    val device = device(lastUpdate = "2024-01-02T03:04:05.000+00:00")
    val pet = PetMapping.pet(device, latestPosition = null, serverUrl = serverUrl)
    assertTrue(pet.lastSeenAt != null)
  }

  @Test
  fun position_convertsKnotsToKilometersPerHour() {
    val position = PetMapping.position(traccarPosition(speed = 10.0))
    assertEquals(18.52, position.speedKilometersPerHour, 0.001)
  }

  @Test
  fun position_readsBatteryLevelFromAttributes() {
    val position =
      PetMapping.position(traccarPosition(attributes = mapOf("batteryLevel" to JsonPrimitive(72.0))))
    assertEquals(72.0, position.batteryLevelPercent)
  }

  private fun device(
    status: String = "online",
    category: String? = null,
    lastUpdate: String? = null,
    attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
  ) =
    TraccarDevice(
      id = 1,
      name = "Buddy",
      uniqueId = "860000000000099",
      status = status,
      disabled = false,
      lastUpdate = lastUpdate,
      category = category,
      attributes = attributes,
    )

  private fun traccarPosition(
    speed: Double = 0.0,
    attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
  ) =
    TraccarPosition(
      id = 1,
      deviceId = 1,
      fixTime = "2024-01-02T03:04:05.000+00:00",
      latitude = 51.0,
      longitude = 12.0,
      speed = speed,
      attributes = attributes,
    )
}

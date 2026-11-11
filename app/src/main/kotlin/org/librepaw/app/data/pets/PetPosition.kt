package org.librepaw.app.data.pets

data class PetPosition(
  val id: Int,
  val petId: Int,
  val latitude: Double,
  val longitude: Double,
  /** Epoch milliseconds, parsed from the position's `fixTime`. */
  val recordedAt: Long,
  val speedKilometersPerHour: Double,
  val address: String?,
  val batteryLevelPercent: Double?,
)

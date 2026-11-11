package org.librepaw.app.data.traccar

import kotlinx.serialization.Serializable

/** The signed-in account, as returned by `GET /api/session` (cookie-authenticated). */
@Serializable
data class TraccarUser(
  val id: Int,
  val email: String,
  val administrator: Boolean = false,
  val readonly: Boolean = false,
  val deviceReadonly: Boolean = false,
  val disableReports: Boolean = false,
)

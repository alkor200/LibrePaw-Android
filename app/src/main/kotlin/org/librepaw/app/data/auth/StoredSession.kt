package org.librepaw.app.data.auth

import kotlinx.serialization.Serializable

/** A logged-in Traccar session, persisted across app launches. */
@Serializable
data class StoredSession(
  val serverUrl: String,
  val email: String,
  val password: String,
) {
  override fun toString(): String = "StoredSession(serverUrl=$serverUrl, email=$email, password=<redacted>)"
}

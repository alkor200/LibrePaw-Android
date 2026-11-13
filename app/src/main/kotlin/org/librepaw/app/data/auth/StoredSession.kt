package org.librepaw.app.data.auth

import kotlinx.serialization.Serializable
import org.librepaw.app.data.traccar.TraccarCredentials

/**
 * A logged-in Traccar session, persisted across app launches. Normally holds an API [token], not
 * the account's password: see LiveAuthenticationService.logIn for when [password] is still set.
 */
@Serializable
data class StoredSession(
  val serverUrl: String,
  val email: String,
  /** Only for a server that can't issue tokens, or a session stored before tokens were used. */
  val password: String? = null,
  val token: String? = null,
  /** Epoch millis. */
  val tokenExpiresAt: Long? = null,
) {
  val secret: TraccarCredentials.Secret
    get() = token?.let(TraccarCredentials.Secret::Token) ?: TraccarCredentials.Secret.Password(password.orEmpty())

  override fun toString(): String =
    "StoredSession(serverUrl=$serverUrl, email=$email, password=<redacted>, token=<redacted>, tokenExpiresAt=$tokenExpiresAt)"
}

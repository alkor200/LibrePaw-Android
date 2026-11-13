package org.librepaw.app.data.traccar

import kotlin.io.encoding.Base64
import okhttp3.HttpUrl

/** Credentials for a Traccar server: an API token (Bearer) or, failing that, a password (Basic Auth). */
data class TraccarCredentials(
  val serverUrl: HttpUrl,
  val email: String,
  val secret: Secret,
) {
  sealed interface Secret {
    /** Only used to log in, and for servers that can't issue tokens, see LiveAuthenticationService. */
    data class Password(val value: String) : Secret {
      override fun toString(): String = "Password(<redacted>)"
    }

    /** An API token from `POST /api/session/token`, revocable and expiring, unlike the password. */
    data class Token(val value: String) : Secret {
      override fun toString(): String = "Token(<redacted>)"
    }
  }

  val authorizationHeaderValue: String
    get() =
      when (secret) {
        is Secret.Password -> "Basic " + Base64.Default.encode("$email:${secret.value}".toByteArray(Charsets.UTF_8))
        is Secret.Token -> "Bearer ${secret.value}"
      }

  override fun toString(): String = "TraccarCredentials(serverUrl=$serverUrl, email=$email, secret=<redacted>)"
}

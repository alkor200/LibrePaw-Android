package org.librepaw.app.data.traccar

import kotlin.io.encoding.Base64
import okhttp3.HttpUrl

/** Credentials for a Traccar server, authenticated via HTTP Basic Auth. */
data class TraccarCredentials(
  val serverUrl: HttpUrl,
  val email: String,
  val password: String,
) {
  val basicAuthHeaderValue: String
    get() {
      val encoded = Base64.Default.encode("$email:$password".toByteArray(Charsets.UTF_8))
      return "Basic $encoded"
    }

  override fun toString(): String = "TraccarCredentials(serverUrl=$serverUrl, email=$email, password=<redacted>)"
}

package org.librepaw.app.data.traccar

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarCredentials.Secret

class TraccarCredentialsTest {
  private val serverUrl = "https://traccar.example.com/".toHttpUrl()

  @Test
  fun authorizationHeaderValue_password_isBasicAuthOfEmailAndPassword() {
    val credentials = TraccarCredentials(serverUrl, "user@example.com", Secret.Password("hunter2"))
    assertEquals("Basic dXNlckBleGFtcGxlLmNvbTpodW50ZXIy", credentials.authorizationHeaderValue)
  }

  @Test
  fun authorizationHeaderValue_token_isBearer() {
    val credentials = TraccarCredentials(serverUrl, "user@example.com", Secret.Token("abc123"))
    assertEquals("Bearer abc123", credentials.authorizationHeaderValue)
  }

  @Test
  fun toString_neverContainsTheSecret() {
    assertFalse(TraccarCredentials(serverUrl, "user@example.com", Secret.Password("hunter2")).toString().contains("hunter2"))
    assertFalse(TraccarCredentials(serverUrl, "user@example.com", Secret.Token("abc123")).toString().contains("abc123"))
  }
}

package org.librepaw.app.data.traccar

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TraccarCredentialsTest {
  private val credentials =
    TraccarCredentials(
      serverUrl = "https://traccar.example.com/".toHttpUrl(),
      email = "user@example.com",
      password = "hunter2",
    )

  @Test
  fun basicAuthHeaderValue_encodesEmailAndPassword() {
    assertEquals("Basic dXNlckBleGFtcGxlLmNvbTpodW50ZXIy", credentials.basicAuthHeaderValue)
  }

  @Test
  fun toString_neverContainsThePassword() {
    assertFalse(credentials.toString().contains("hunter2"))
  }
}

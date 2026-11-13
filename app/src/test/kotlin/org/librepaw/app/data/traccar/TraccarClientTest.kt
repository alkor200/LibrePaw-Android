package org.librepaw.app.data.traccar

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarCredentials.Secret

/** The HTTP layer itself, against a real (local) HTTP server. */
class TraccarClientTest {
  private val server = MockWebServer()
  private lateinit var client: TraccarClient

  @Before
  fun setUp() {
    server.start()
    client = TraccarClient(TraccarCredentials(server.url("/"), "user@example.org", Secret.Token("token-abc")))
  }

  @After
  fun tearDown() {
    server.close()
  }

  @Test
  fun requests_carryTheTokenAsBearer() = runTest {
    server.enqueue(MockResponse(200, body = "[]"))

    client.fetchDevices()

    assertEquals("Bearer token-abc", server.takeRequest().headers["Authorization"])
  }

  @Test
  fun status401_isInvalidCredentials() = runTest {
    server.enqueue(MockResponse(401, body = ""))
    assertTrue(failureOf { client.fetchDevices() } is TraccarError.InvalidCredentials)
  }

  @Test
  fun status403_isForbidden_notInvalidCredentials() = runTest {
    server.enqueue(MockResponse(403, body = ""))
    assertTrue(failureOf { client.fetchDevices() } is TraccarError.Forbidden)
  }

  private suspend fun failureOf(block: suspend () -> Unit): Throwable {
    try {
      block()
    } catch (e: Throwable) {
      return e
    }
    fail("Expected a failure")
    throw AssertionError()
  }
}

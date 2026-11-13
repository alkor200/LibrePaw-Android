package org.librepaw.app.data.traccar

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
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

  @Test
  fun concurrentPhotoFetches_openOnlyOneCookieSession() = runTest {
    val sessionLogins = AtomicInteger()
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          when (request.url.encodedPath) {
            "/api/session" -> {
              sessionLogins.incrementAndGet()
              Thread.sleep(50) // wide enough a window for every caller to see no session yet
              MockResponse(200, headersOf("Set-Cookie", "JSESSIONID=abc; Path=/"), "{}")
            }
            else ->
              if (request.headers["Cookie"] == "JSESSIONID=abc") MockResponse(200, body = "photo") else MockResponse(401, body = "")
          }
      }

    val photos =
      withContext(Dispatchers.Default) {
        (1..8).map { async { client.fetchData(server.url("/api/media/1/device.jpg")) } }.awaitAll()
      }

    assertEquals(1, sessionLogins.get())
    assertTrue(photos.all { String(it) == "photo" })
  }

  @Test
  fun cookieJar_keepsTheSessionCookieWhenALaterResponseSetsAnother() = runTest {
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          when (request.url.encodedPath) {
            "/api/session" -> MockResponse(200, headersOf("Set-Cookie", "JSESSIONID=abc; Path=/"), "{}")
            // E.g. a load balancer's own stickiness cookie on some later response.
            "/api/devices" -> MockResponse(200, headersOf("Set-Cookie", "lb=1; Path=/"), "[]")
            else ->
              if (request.headers["Cookie"]?.contains("JSESSIONID=abc") == true) MockResponse(200, body = "photo") else MockResponse(401, body = "")
          }
      }

    client.fetchData(server.url("/api/media/1/device.jpg"))
    client.fetchDevices()
    val photo = client.fetchData(server.url("/api/media/1/device.jpg"))

    assertEquals("photo", String(photo))
    // The session was reused, not silently re-established after the second cookie replaced it.
    assertEquals(1, server.requestCount - 3)
  }

  @Test
  fun renamingADevice_whoseResponseIsNotAJsonObject_failsAsDecoding_notACrash() = runTest {
    server.enqueue(MockResponse(200, body = "[]"))
    assertTrue(failureOf { client.updateDeviceName(1, "Rex") } is TraccarError.Decoding)
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

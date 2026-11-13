package org.librepaw.app.data.auth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.librepaw.app.data.pets.ExcursionCacheStore
import org.librepaw.app.data.pets.LifetimeStatsStore
import org.librepaw.app.testing.InMemorySharedPreferences

/** Login, renewal and expiry against a real (local) HTTP server. */
class LiveAuthenticationServiceTokenTest {
  private val server = MockWebServer()
  private val credentialStore = InMemoryCredentialStore()
  private val now = 1_800_000_000_000L
  private val day = 24 * 60 * 60 * 1000L

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.close()
  }

  private fun service() =
    LiveAuthenticationService(
      credentialStore,
      ExcursionCacheStore(InMemorySharedPreferences()),
      LifetimeStatsStore(InMemorySharedPreferences()),
      now = { now },
    )

  @Test
  fun logIn_tradesThePasswordForAToken_andNeverStoresThePassword() = runTest {
    server.enqueue(MockResponse(200, body = "[]"))
    server.enqueue(MockResponse(200, body = "token-abc\n"))

    val session = service().logIn(server.url("/"), "user@example.org", "hunter2")

    assertEquals("token-abc", session.token)
    assertNull(session.password)
    assertEquals(now + LiveAuthenticationService.TOKEN_LIFETIME_MILLIS, session.tokenExpiresAt)
    assertEquals(session, credentialStore.loadSession())
    // Checked with the password once, then the token is requested with it too.
    assertEquals("/api/devices", server.takeRequest().url.encodedPath)
    val tokenRequest = server.takeRequest()
    assertEquals("/api/session/token", tokenRequest.url.encodedPath)
    assertEquals(true, tokenRequest.headers["Authorization"]?.startsWith("Basic "))
  }

  @Test
  fun logIn_serverWithoutTokenSupport_fallsBackToThePassword() = runTest {
    server.enqueue(MockResponse(200, body = "[]"))
    server.enqueue(MockResponse(404, body = ""))

    val session = service().logIn(server.url("/"), "user@example.org", "hunter2")

    assertNull(session.token)
    assertEquals("hunter2", session.password)
  }

  @Test
  fun logIn_serverAnsweringTheTokenRequestWithAPage_fallsBackToThePassword() = runTest {
    server.enqueue(MockResponse(200, body = "[]"))
    server.enqueue(MockResponse(200, body = "<!DOCTYPE html><html>...</html>"))

    val session = service().logIn(server.url("/"), "user@example.org", "hunter2")

    assertNull(session.token)
    assertEquals("hunter2", session.password)
  }

  @Test
  fun renewStoredSession_legacyPasswordSession_isUpgradedToAToken() = runTest {
    val legacy = StoredSession(server.url("/").toString(), "user@example.org", password = "hunter2")
    credentialStore.save(legacy)
    server.enqueue(MockResponse(200, body = "token-new"))

    service().renewStoredSession(legacy)

    assertEquals("token-new", credentialStore.loadSession()?.token)
    assertNull(credentialStore.loadSession()?.password)
  }

  @Test
  fun renewStoredSession_tokenExpiringSoon_isRenewedWithItself() = runTest {
    val expiring = StoredSession(server.url("/").toString(), "user@example.org", token = "token-old", tokenExpiresAt = now + 10 * day)
    credentialStore.save(expiring)
    server.enqueue(MockResponse(200, body = "token-new"))

    service().renewStoredSession(expiring)

    assertEquals("token-new", credentialStore.loadSession()?.token)
    assertEquals("Bearer token-old", server.takeRequest().headers["Authorization"])
  }

  @Test
  fun renewStoredSession_tokenFarFromExpiring_isLeftAlone() = runTest {
    val fresh = StoredSession(server.url("/").toString(), "user@example.org", token = "token-old", tokenExpiresAt = now + 200 * day)
    credentialStore.save(fresh)

    service().renewStoredSession(fresh)

    assertEquals(fresh, credentialStore.loadSession())
    assertEquals(0, server.requestCount)
  }

  @Test
  fun renewStoredSession_afterALogoutMeanwhile_doesNotResurrectTheSession() = runTest {
    val expiring = StoredSession(server.url("/").toString(), "user@example.org", token = "token-old", tokenExpiresAt = now + 10 * day)
    // Already logged out by the time the renewal's answer arrives.
    server.enqueue(MockResponse(200, body = "token-new"))

    service().renewStoredSession(expiring)

    assertNull(credentialStore.loadSession())
  }

  @Test
  fun restoreSession_expiredToken_readsAsLoggedOut() {
    credentialStore.save(StoredSession("https://a.example/", "user@example.org", token = "token-old", tokenExpiresAt = now - 1))

    assertNull(service().restoreSession())
    assertNull(credentialStore.loadSession())
  }

  @Test
  fun revokeSession_revokesTheToken() = runTest {
    server.enqueue(MockResponse(204, body = ""))

    service().revokeSession(StoredSession(server.url("/").toString(), "user@example.org", token = "token-old", tokenExpiresAt = now + day))

    val request = server.takeRequest()
    assertEquals("/api/session/token/revoke", request.url.encodedPath)
    assertEquals("token=token-old", request.body?.utf8())
  }
}

private class InMemoryCredentialStore : CredentialStore {
  private var session: StoredSession? = null

  override fun save(session: StoredSession) {
    this.session = session
  }

  override fun loadSession(): StoredSession? = session

  override fun clear() {
    session = null
  }
}

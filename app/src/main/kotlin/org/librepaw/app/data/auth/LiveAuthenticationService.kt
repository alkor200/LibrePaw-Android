package org.librepaw.app.data.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.librepaw.app.data.pets.ExcursionCacheStore
import org.librepaw.app.data.pets.LifetimeStatsStore
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.pets.TraccarPetRepository
import org.librepaw.app.data.traccar.TraccarClient
import org.librepaw.app.data.traccar.TraccarCredentials
import org.librepaw.app.data.traccar.TraccarError

class LiveAuthenticationService(
  private val credentialStore: CredentialStore,
  private val excursionCacheStore: ExcursionCacheStore,
  private val lifetimeStatsStore: LifetimeStatsStore,
  private val now: () -> Long = System::currentTimeMillis,
  private val makeClient: (TraccarCredentials) -> TraccarClient = ::TraccarClient,
) : AuthenticationService {
  /** An expired token can't do anything any more, so that reads as logged out, not as a session. */
  override fun restoreSession(): StoredSession? {
    val session = credentialStore.loadSession() ?: return null
    val expiresAt = session.tokenExpiresAt
    if (session.token != null && expiresAt != null && expiresAt <= now()) {
      logOut()
      return null
    }
    return session
  }

  /**
   * Checks the password, then trades it for an API token so the password itself is never stored:
   * a token can be revoked (on logout, or by the owner from Traccar's own UI) and expires on its
   * own, the password is good for every app and the web UI until someone changes it. Falls back to
   * storing the password only when the server can't issue a token (older than Traccar 5, or an
   * account restricted from it).
   */
  override suspend fun logIn(serverUrl: HttpUrl, email: String, password: String): StoredSession {
    val client = makeClient(TraccarCredentials(serverUrl, email, TraccarCredentials.Secret.Password(password)))
    // Traccar has no dedicated login endpoint, so a credential check is just
    // any authenticated request. GET /api/devices correctly 401s on bad
    // credentials, unlike /api/session, which ignores Basic Auth entirely.
    client.fetchDevices()

    val session =
      newTokenSession(client, serverUrl.toString(), email) ?: StoredSession(serverUrl.toString(), email, password = password)
    // Also cleans up after builds from before logOut cleared these, whatever they left behind
    // belongs to some earlier account.
    clearLocalCaches()
    credentialStore.save(session)
    return session
  }

  override fun logOut() {
    // Before the credentials, so a failing clear of those can't skip this.
    clearLocalCaches()
    credentialStore.clear()
  }

  override suspend fun revokeSession(session: StoredSession) {
    val token = session.token ?: return
    try {
      makeClient(credentials(session)).revokeToken(token)
    } catch (e: TraccarError) {
      // Best-effort: it still expires on its own.
    }
  }

  override suspend fun renewStoredSession(session: StoredSession) {
    val expiresAt = session.tokenExpiresAt
    val isLegacyPasswordSession = session.token == null
    val expiresSoon = expiresAt != null && expiresAt - now() < TOKEN_RENEWAL_WINDOW_MILLIS
    if (!isLegacyPasswordSession && !expiresSoon) return

    val renewed = newTokenSession(makeClient(credentials(session)), session.serverUrl, session.email) ?: return
    // Only if nothing changed meanwhile, e.g. a logout while the request was in flight, which this
    // would otherwise quietly undo. The token this one replaces isn't revoked: the running app
    // keeps using it until the next launch, and it expires on its own.
    if (credentialStore.loadSession() != session) return
    try {
      credentialStore.save(renewed)
    } catch (e: CredentialStorageException) {
      // Best-effort: tried again on the next launch.
    }
  }

  /** `null` when the server can't issue a token, or the request fails: the caller decides what then. */
  private suspend fun newTokenSession(client: TraccarClient, serverUrl: String, email: String): StoredSession? {
    val expiresAt = now() + TOKEN_LIFETIME_MILLIS
    val token =
      try {
        client.createToken(expiresAt)
      } catch (e: TraccarError) {
        return null
      }
    // An old server without the endpoint may answer with something other than a token (an HTML
    // page) rather than an error.
    if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH || token.any { it.isWhitespace() || it == '<' }) return null
    return StoredSession(serverUrl, email, token = token, tokenExpiresAt = expiresAt)
  }

  /** Everything derived from one account's server data, see [ExcursionCacheStore.clearAll]. */
  private fun clearLocalCaches() {
    excursionCacheStore.clearAll()
    lifetimeStatsStore.clearAll()
  }

  private fun credentials(session: StoredSession) = TraccarCredentials(session.serverUrl.toHttpUrl(), session.email, session.secret)

  override fun makePetRepository(session: StoredSession): PetRepository =
    TraccarPetRepository(makeClient(credentials(session)), excursionCacheStore, lifetimeStatsStore)

  companion object {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** A year: long enough to never get in the way, renewed well before it runs out. */
    const val TOKEN_LIFETIME_MILLIS = 365 * DAY_MILLIS

    /** Renewed on any launch within this long of expiring. */
    const val TOKEN_RENEWAL_WINDOW_MILLIS = 60 * DAY_MILLIS

    private const val MAX_TOKEN_LENGTH = 1024
  }
}

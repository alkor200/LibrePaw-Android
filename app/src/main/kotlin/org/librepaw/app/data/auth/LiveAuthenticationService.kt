package org.librepaw.app.data.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.librepaw.app.data.pets.ExcursionCacheStore
import org.librepaw.app.data.pets.LifetimeStatsStore
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.pets.TraccarPetRepository
import org.librepaw.app.data.traccar.TraccarClient
import org.librepaw.app.data.traccar.TraccarCredentials

class LiveAuthenticationService(
  private val credentialStore: CredentialStore,
  private val excursionCacheStore: ExcursionCacheStore,
  private val lifetimeStatsStore: LifetimeStatsStore,
) : AuthenticationService {
  override fun restoreSession(): StoredSession? = credentialStore.loadSession()

  override suspend fun logIn(serverUrl: HttpUrl, email: String, password: String): StoredSession {
    val credentials = TraccarCredentials(serverUrl, email, password)
    // Traccar has no dedicated login endpoint, so a credential check is just
    // any authenticated request. GET /api/devices correctly 401s on bad
    // credentials, unlike /api/session, which ignores Basic Auth entirely.
    TraccarClient(credentials).fetchDevices()

    val session = StoredSession(serverUrl.toString(), email, password)
    credentialStore.save(session)
    return session
  }

  override fun logOut() {
    credentialStore.clear()
  }

  override fun makePetRepository(session: StoredSession): PetRepository {
    val credentials = TraccarCredentials(session.serverUrl.toHttpUrl(), session.email, session.password)
    return TraccarPetRepository(TraccarClient(credentials), excursionCacheStore, lifetimeStatsStore)
  }
}

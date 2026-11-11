package org.librepaw.app.data.auth

import okhttp3.HttpUrl
import org.librepaw.app.data.traccar.TraccarClient
import org.librepaw.app.data.traccar.TraccarCredentials

class LiveAuthenticationService(private val credentialStore: CredentialStore) : AuthenticationService {
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
}

package org.librepaw.app.data.auth

import okhttp3.HttpUrl

/** Logs in to a Traccar server and persists/restores the resulting session. */
interface AuthenticationService {
  fun restoreSession(): StoredSession?

  suspend fun logIn(serverUrl: HttpUrl, email: String, password: String): StoredSession

  fun logOut()
}

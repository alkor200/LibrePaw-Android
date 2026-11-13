package org.librepaw.app.data.auth

import okhttp3.HttpUrl
import org.librepaw.app.data.pets.PetRepository

/** Logs in to a Traccar server and persists/restores the resulting session. */
interface AuthenticationService {
  fun restoreSession(): StoredSession?

  suspend fun logIn(serverUrl: HttpUrl, email: String, password: String): StoredSession

  /** Forgets the session and everything cached for it on this device. */
  fun logOut()

  /** Best-effort: invalidates a logged-out [session]'s token on the server too, never throws. */
  suspend fun revokeSession(session: StoredSession)

  /**
   * Best-effort and never throws: replaces the stored [session] with a fresh token when its own
   * expires soon, or was never a token at all. Only takes effect from the next launch on, this
   * one keeps using [session] as is.
   */
  suspend fun renewStoredSession(session: StoredSession)

  fun makePetRepository(session: StoredSession): PetRepository
}

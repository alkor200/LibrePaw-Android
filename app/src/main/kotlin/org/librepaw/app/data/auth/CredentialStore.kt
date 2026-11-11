package org.librepaw.app.data.auth

/** Persists the current [StoredSession], if any. */
interface CredentialStore {
  fun save(session: StoredSession)

  fun loadSession(): StoredSession?

  fun clear()
}

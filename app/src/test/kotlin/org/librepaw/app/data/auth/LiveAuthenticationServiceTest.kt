package org.librepaw.app.data.auth

import org.junit.Assert.assertNull
import org.junit.Test
import org.librepaw.app.data.pets.CachedExcursions
import org.librepaw.app.data.pets.CachedLifetimeStats
import org.librepaw.app.data.pets.ExcursionCacheStore
import org.librepaw.app.data.pets.LifetimeStatsStore
import org.librepaw.app.testing.InMemorySharedPreferences

class LiveAuthenticationServiceTest {
  @Test
  fun logOut_clearsCachedExcursionsAndLifetimeStats() {
    val excursionCacheStore = ExcursionCacheStore(InMemorySharedPreferences())
    val lifetimeStatsStore = LifetimeStatsStore(InMemorySharedPreferences())
    excursionCacheStore.save(CachedExcursions(emptyList(), coveredFrom = 0L, cachedThroughEndTime = 1L), petId = 1, serverHost = "a.example")
    excursionCacheStore.save(CachedExcursions(emptyList(), coveredFrom = 0L, cachedThroughEndTime = 1L), petId = 2, serverHost = "b.example")
    lifetimeStatsStore.save(CachedLifetimeStats(10.0, 10L, 1L), petId = 1, serverHost = "a.example")
    val credentialStore = FakeCredentialStore(StoredSession("https://a.example", "a@example.org", token = "secret"))

    LiveAuthenticationService(credentialStore, excursionCacheStore, lifetimeStatsStore).logOut()

    assertNull(excursionCacheStore.load(1, "a.example"))
    assertNull(excursionCacheStore.load(2, "b.example"))
    assertNull(lifetimeStatsStore.load(1, "a.example"))
    assertNull(credentialStore.loadSession())
  }
}

private class FakeCredentialStore(private var session: StoredSession?) : CredentialStore {
  override fun save(session: StoredSession) {
    this.session = session
  }

  override fun loadSession(): StoredSession? = session

  override fun clear() {
    session = null
  }
}

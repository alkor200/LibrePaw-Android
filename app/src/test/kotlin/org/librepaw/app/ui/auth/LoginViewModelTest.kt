package org.librepaw.app.ui.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.librepaw.app.R
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun expiredSession_prefillsServerAndEmail_andSaysWhy() {
    val expired = StoredSession("https://traccar.example.com/", "user@example.com", token = "revoked")

    val viewModel = LoginViewModel(FakeAuthenticationService(), expiredSession = expired)

    assertEquals("traccar.example.com", viewModel.serverUrlString)
    assertEquals("user@example.com", viewModel.email)
    assertEquals("", viewModel.password)
    assertEquals(UiText(R.string.login_session_expired), viewModel.errorMessage)
  }

  @Test
  fun usesInsecureConnection_detectsHttp() {
    val viewModel = LoginViewModel(FakeAuthenticationService())
    viewModel.updateServerUrl("http://traccar.example.com")
    assertTrue(viewModel.usesInsecureConnection)
  }

  @Test
  fun canSubmit_falseUntilAllFieldsFilled() {
    val viewModel = LoginViewModel(FakeAuthenticationService())
    assertFalse(viewModel.canSubmit)

    viewModel.updateServerUrl("traccar.example.com")
    viewModel.updateEmail("user@example.com")
    assertFalse(viewModel.canSubmit)

    viewModel.updatePassword("hunter2")
    assertTrue(viewModel.canSubmit)
  }

  @Test
  fun canSubmit_falseForInsecureConnection() {
    val viewModel = LoginViewModel(FakeAuthenticationService())
    viewModel.updateServerUrl("http://traccar.example.com")
    viewModel.updateEmail("user@example.com")
    viewModel.updatePassword("hunter2")
    assertFalse(viewModel.canSubmit)
  }

  @Test
  fun logIn_onSuccess_setsLoggedInSession() = runTest {
    val session = StoredSession("https://traccar.example.com/", "user@example.com", token = "abc123")
    val viewModel = LoginViewModel(FakeAuthenticationService(result = Result.success(session)))
    viewModel.updateServerUrl("traccar.example.com")
    viewModel.updateEmail("user@example.com")
    viewModel.updatePassword("hunter2")

    viewModel.logIn()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(session, viewModel.loggedInSession)
    assertNull(viewModel.errorMessage)
    assertFalse(viewModel.isLoggingIn)
  }

  @Test
  fun logIn_onFailure_setsErrorMessage() = runTest {
    val viewModel =
      LoginViewModel(FakeAuthenticationService(result = Result.failure(TraccarError.InvalidCredentials())))
    viewModel.updateServerUrl("traccar.example.com")
    viewModel.updateEmail("user@example.com")
    viewModel.updatePassword("wrong")

    viewModel.logIn()
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.loggedInSession)
    assertEquals(UiText(R.string.error_invalid_credentials), viewModel.errorMessage)
    assertFalse(viewModel.isLoggingIn)
  }
}

private class FakeAuthenticationService(private val result: Result<StoredSession> = Result.success(StoredSession("", ""))) :
  AuthenticationService {
  override fun restoreSession(): StoredSession? = null

  override suspend fun logIn(serverUrl: HttpUrl, email: String, password: String): StoredSession =
    result.getOrThrow()

  override fun logOut() {}

  override suspend fun revokeSession(session: StoredSession) {}

  override suspend fun renewStoredSession(session: StoredSession) {}

  override fun makePetRepository(session: StoredSession): org.librepaw.app.data.pets.PetRepository =
    throw UnsupportedOperationException("not used by this test")
}

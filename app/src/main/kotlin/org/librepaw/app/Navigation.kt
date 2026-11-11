package org.librepaw.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.ui.auth.LoginScreen
import org.librepaw.app.ui.main.MainScreen

@Composable
fun MainNavigation(authenticationService: AuthenticationService) {
  var currentSession by remember { mutableStateOf(authenticationService.restoreSession()) }
  val backStack = rememberNavBackStack(if (currentSession != null) Main else Login)

  fun goTo(key: NavKey) {
    backStack.clear()
    backStack.add(key)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            authenticationService = authenticationService,
            onLoggedIn = { session: StoredSession ->
              currentSession = session
              goTo(Main)
            },
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            loggedInEmail = currentSession?.email,
            onLogOut = {
              authenticationService.logOut()
              currentSession = null
              goTo(Login)
            },
          )
        }
      },
  )
}

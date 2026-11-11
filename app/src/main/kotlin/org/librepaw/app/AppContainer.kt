package org.librepaw.app

import android.content.Context
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.CredentialStore
import org.librepaw.app.data.auth.EncryptedCredentialStore
import org.librepaw.app.data.auth.LiveAuthenticationService
import org.librepaw.app.data.settings.AppearancePreferences

/**
 * Manually wires the app's dependencies, passed down explicitly from
 * [MainActivity] rather than through a DI framework, mirroring the iOS
 * app's own deliberate choice (ADR-0009).
 */
class AppContainer(context: Context) {
  val credentialStore: CredentialStore = EncryptedCredentialStore(context)
  val authenticationService: AuthenticationService = LiveAuthenticationService(credentialStore)
  val appearancePreferences: AppearancePreferences = AppearancePreferences(context)
}

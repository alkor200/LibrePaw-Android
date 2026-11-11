package org.librepaw.app.data.pets

/** What the signed-in Traccar account can actually do, read off Traccar's own permission flags. */
data class AccountPermissions(
  val isAdministrator: Boolean,
  val canManageHomes: Boolean,
  val canManageDevice: Boolean,
  val canViewExcursions: Boolean,
)

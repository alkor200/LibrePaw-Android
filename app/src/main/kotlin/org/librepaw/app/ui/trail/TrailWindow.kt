package org.librepaw.app.ui.trail

/** How far back [TrailScreen] looks for excursions to show, user-adjustable, default 24 hours. */
enum class TrailWindow(val label: String, val millis: Long) {
  ONE_DAY("24 Hours", 86_400_000L),
  THREE_DAYS("3 Days", 3 * 86_400_000L),
  SEVEN_DAYS("7 Days", 7 * 86_400_000L),
  THIRTY_DAYS("30 Days", 30 * 86_400_000L),
}

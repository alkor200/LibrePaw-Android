package org.librepaw.app.ui.trail

import androidx.annotation.StringRes
import org.librepaw.app.R

/** How far back [TrailScreen] looks for excursions to show, user-adjustable, default 24 hours. */
enum class TrailWindow(@StringRes val label: Int, val millis: Long) {
  ONE_DAY(R.string.trail_window_one_day, 86_400_000L),
  THREE_DAYS(R.string.trail_window_three_days, 3 * 86_400_000L),
  SEVEN_DAYS(R.string.trail_window_seven_days, 7 * 86_400_000L),
  THIRTY_DAYS(R.string.trail_window_thirty_days, 30 * 86_400_000L),
}

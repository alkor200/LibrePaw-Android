package org.librepaw.app.ui.common

import android.content.res.Resources
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.librepaw.app.R

/**
 * The one place distances, durations, speeds and times are turned into text, so every screen shows
 * the same value the same way, in the app's current language (see ADR-0022 and the iOS app's own
 * Formatting.swift, which this mirrors).
 *
 * These used to be helpers repeated per screen that had drifted apart (durations read "1h 5m" in
 * the excursion list but "1d 2h" on Pet Detail, distances "%.0f m" in one place and "%d m" in
 * another), with hard-coded English date patterns ("MMM d, h:mm a") and DateUtils' relative times,
 * which follow the device's system language rather than the app's.
 */
class Formatting(private val resources: Resources, val locale: Locale) {
  fun distance(meters: Double): String =
    if (meters < 1000) number(meters, fractionDigits = 0) + " m" else number(meters / 1000, fractionDigits = 1) + " km"

  /** "42 min" under an hour, "3h 5m" under a day, "2d 4h" beyond that (unit abbreviations localized). */
  fun duration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    val d = resources.getString(R.string.duration_days_abbreviation)
    val h = resources.getString(R.string.duration_hours_abbreviation)
    val m = resources.getString(R.string.duration_minutes_abbreviation)
    return when {
      days > 0 -> "$days$d $hours$h"
      hours > 0 -> "$hours$h $minutes$m"
      else -> "$minutes ${resources.getString(R.string.duration_minutes_unit)}"
    }
  }

  fun speed(kilometersPerHour: Double): String = number(kilometersPerHour, fractionDigits = 1) + " km/h"

  /** [percent] is 0 to 100, e.g. a battery level. */
  fun percent(percent: Double): String = NumberFormat.getPercentInstance(locale).format(percent / 100)

  /**
   * Always with a decimal point, whatever the language: with German's decimal comma,
   * "51,33696, 12,34077" can't be told apart from four numbers.
   */
  fun coordinate(latitude: Double, longitude: Double): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

  fun time(epochMillis: Long): String = pattern("jmm", epochMillis)

  fun date(epochMillis: Long): String = pattern("yMMMd", epochMillis)

  fun dateTime(epochMillis: Long): String = pattern("MMMdjmm", epochMillis)

  /**
   * "now", "5 minutes ago", "yesterday", "3 days ago", or the date beyond a week. Days count
   * calendar days, like DateUtils did: something 30 hours ago at 23:00 the day before yesterday is
   * "2 days ago", not "yesterday".
   */
  fun relative(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    val minutes = maxOf(0L, nowMillis - epochMillis) / 60_000
    val hours = minutes / 60
    val days = calendarDaysBetween(epochMillis, nowMillis)
    return when {
      minutes < 1 -> formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
      minutes < 60 -> formatter.format(minutes.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
      hours < 24 -> formatter.format(hours.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS)
      days == 1L -> formatter.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
      days < 7 -> formatter.format(days.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS)
      else -> date(epochMillis)
    }
  }

  private fun calendarDaysBetween(fromMillis: Long, toMillis: Long): Long {
    fun startOfDay(millis: Long): Long =
      Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis
    // Rounded, not truncated: a day with a DST change is 23 or 25 hours long.
    return Math.round((startOfDay(toMillis) - startOfDay(fromMillis)) / 86_400_000.0)
  }

  private fun number(value: Double, fractionDigits: Int): String =
    NumberFormat.getNumberInstance(locale)
      .apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
      }
      .format(value)

  /** [skeleton] in the locale's own order and hour cycle, e.g. "MMMdjmm": "Nov 13, 3:12 PM" / "13. Nov., 15:12". */
  private fun pattern(skeleton: String, epochMillis: Long): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(epochMillis))
}

/** A [Formatting] in the current app language, rebuilt whenever the configuration (and so the language) changes. */
@Composable
fun rememberFormatting(): Formatting {
  val configuration = LocalConfiguration.current
  val resources = LocalResources.current
  return remember(configuration, resources) { Formatting(resources, configuration.locales[0]) }
}

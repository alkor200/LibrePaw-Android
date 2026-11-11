package org.librepaw.app.data.traccar

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parsing/formatting for Traccar's various ISO-8601-ish date representations. */
object TraccarDates {
  private val responseFormat =
    ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US) }
  private val queryParamFormat =
    ThreadLocal.withInitial {
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

  /** Parses a Traccar JSON response timestamp (e.g. `fixTime`, `lastUpdate`, `eventTime`) to epoch millis. */
  fun parse(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
      responseFormat.get()!!.parse(raw)?.time
    } catch (e: ParseException) {
      null
    }
  }

  /** Formats epoch millis for use as a `from`/`to` query parameter. */
  fun queryParam(epochMillis: Long): String = queryParamFormat.get()!!.format(Date(epochMillis))
}

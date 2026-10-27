package dev.sequel.app.domain.importer.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ParsedDateResult(
    val epochMillis: Long?,
    val dateOnlyString: String?,
    val isDateOnly: Boolean,
    val rawString: String?
)

/**
 * Utility for cross-service date and timestamp normalization.
 *
 * Guarantees:
 * 1. Date-only values (e.g. Letterboxd YYYY-MM-DD) remain date-only values and are anchored
 *    to noon UTC (12:00:00 UTC) when converted to epoch millis. This ensures that when converted
 *    to any local timezone worldwide (-11 to +14), the calendar date never shifts unexpectedly.
 * 2. ISO-8601 timestamps (Trakt) and SQL-like timestamps (TV Time) preserve exact instants in UTC.
 * 3. Null, empty, or unparseable dates return null without fabricating a fake timestamp.
 */
object ImportDateParser {

    private val utcZone = TimeZone.getTimeZone("UTC")

    private val isoWithMillisFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = utcZone
        isLenient = false
    }

    private val isoNoMillisFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = utcZone
        isLenient = false
    }

    private val isoWithOffsetFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        isLenient = false
    }

    private val isoWithOffsetNoMillisFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        isLenient = false
    }

    private val sqlDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = utcZone
        isLenient = false
    }

    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = utcZone
        isLenient = false
    }

    private val dateSlashFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US).apply {
        timeZone = utcZone
        isLenient = false
    }

    fun parse(raw: String?): ParsedDateResult {
        if (raw.isNullOrBlank()) {
            return ParsedDateResult(
                epochMillis = null,
                dateOnlyString = null,
                isDateOnly = false,
                rawString = raw
            )
        }

        val trimmed = raw.trim().trim('"', '\'')

        // 1. Check Date-Only (YYYY-MM-DD or YYYY/MM/DD)
        if (trimmed.length == 10 && (trimmed[4] == '-' || trimmed[4] == '/')) {
            val normalizedDateStr = trimmed.replace('/', '-')
            return try {
                val parsed = synchronized(dateOnlyFormat) { dateOnlyFormat.parse(normalizedDateStr) }
                if (parsed != null) {
                    // Anchor to 12:00:00 UTC to prevent timezone shifts across international date lines
                    val cal = Calendar.getInstance(utcZone).apply {
                        time = parsed
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    ParsedDateResult(
                        epochMillis = cal.timeInMillis,
                        dateOnlyString = normalizedDateStr,
                        isDateOnly = true,
                        rawString = raw
                    )
                } else {
                    ParsedDateResult(null, null, false, raw)
                }
            } catch (e: Exception) {
                ParsedDateResult(null, null, false, raw)
            }
        }

        // 2. Check ISO-8601 with milliseconds (e.g. 2024-01-15T20:30:00.000Z)
        try {
            val parsed = synchronized(isoWithMillisFormat) { isoWithMillisFormat.parse(trimmed) }
            if (parsed != null) {
                val dateOnly = synchronized(dateOnlyFormat) { dateOnlyFormat.format(parsed) }
                return ParsedDateResult(
                    epochMillis = parsed.time,
                    dateOnlyString = dateOnly,
                    isDateOnly = false,
                    rawString = raw
                )
            }
        } catch (_: Exception) {}

        // 3. Check ISO-8601 without milliseconds (e.g. 2024-01-15T20:30:00Z)
        try {
            val parsed = synchronized(isoNoMillisFormat) { isoNoMillisFormat.parse(trimmed) }
            if (parsed != null) {
                val dateOnly = synchronized(dateOnlyFormat) { dateOnlyFormat.format(parsed) }
                return ParsedDateResult(
                    epochMillis = parsed.time,
                    dateOnlyString = dateOnly,
                    isDateOnly = false,
                    rawString = raw
                )
            }
        } catch (_: Exception) {}

        // 4. Check ISO-8601 with timezone offset (+00:00, -05:00) with or without millis
        try {
            val parsed = synchronized(isoWithOffsetFormat) {
                try {
                    isoWithOffsetFormat.parse(trimmed)
                } catch (_: Exception) {
                    synchronized(isoWithOffsetNoMillisFormat) {
                        isoWithOffsetNoMillisFormat.parse(trimmed)
                    }
                }
            }
            if (parsed != null) {
                val dateOnly = if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
                    trimmed.substring(0, 10)
                } else {
                    synchronized(dateOnlyFormat) { dateOnlyFormat.format(parsed) }
                }
                return ParsedDateResult(
                    epochMillis = parsed.time,
                    dateOnlyString = dateOnly,
                    isDateOnly = false,
                    rawString = raw
                )
            }
        } catch (_: Exception) {}

        // 5. Check SQL-like timestamp (e.g. 2021-04-12 20:30:00)
        try {
            val parsed = synchronized(sqlDateTimeFormat) { sqlDateTimeFormat.parse(trimmed) }
            if (parsed != null) {
                val dateOnly = synchronized(dateOnlyFormat) { dateOnlyFormat.format(parsed) }
                return ParsedDateResult(
                    epochMillis = parsed.time,
                    dateOnlyString = dateOnly,
                    isDateOnly = false,
                    rawString = raw
                )
            }
        } catch (_: Exception) {}

        return ParsedDateResult(
            epochMillis = null,
            dateOnlyString = null,
            isDateOnly = false,
            rawString = raw
        )
    }

    fun formatDateOnly(epochMillis: Long): String {
        return synchronized(dateOnlyFormat) { dateOnlyFormat.format(epochMillis) }
    }

    fun formatIsoTimestamp(epochMillis: Long): String {
        return synchronized(isoWithMillisFormat) { isoWithMillisFormat.format(epochMillis) }
    }
}

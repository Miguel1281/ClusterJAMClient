package com.clusterjam.mobile.core.network

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun parseRetryAfterMillis(
    value: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    header.toLongOrNull()?.let { seconds ->
        if (seconds < 0) return null
        return if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
    }
    val format =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
    val position = ParsePosition(0)
    val date = format.parse(header, position) ?: return null
    if (position.index != header.length) return null
    return (date.time - nowMillis).coerceAtLeast(0)
}

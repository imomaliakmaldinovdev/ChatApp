package com.imomali.chatapp.domain

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object MessageTime {
    fun format(millis: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault(),
               zone: TimeZone = TimeZone.getDefault(), full: Boolean = false): String? {
        if (millis <= 0) return null
        val first = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val second = Calendar.getInstance(zone).apply { timeInMillis = now }
        val sameDay = first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
            first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
        val formatter = when {
            full -> DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM, locale)
            sameDay -> DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        }
        formatter.timeZone = zone
        return formatter.format(Date(millis))
    }
}

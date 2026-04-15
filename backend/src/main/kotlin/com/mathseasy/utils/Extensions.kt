package com.mathseasy.utils

import com.google.cloud.firestore.DocumentSnapshot
import kotlinx.datetime.*
import java.time.ZoneId

/**
 * Extension functions for common operations
 */

// DateTime Extensions
fun Long.toLocalDateTime(): LocalDateTime {
    return Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC)
}

fun LocalDateTime.toEpochMillis(): Long {
    return this.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

fun getCurrentTimestamp(): Long {
    return Clock.System.now().toEpochMilliseconds()
}

fun getCurrentDate(): LocalDate {
    return Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}

fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val date1 = timestamp1.toLocalDateTime().date
    val date2 = timestamp2.toLocalDateTime().date
    return date1 == date2
}

fun isYesterday(timestamp: Long): Boolean {
    val today = getCurrentDate()
    val checkDate = timestamp.toLocalDateTime().date
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    return checkDate == yesterday
}

fun daysAgo(days: Int): LocalDate {
    return getCurrentDate().minus(days, DateTimeUnit.DAY)
}

fun getYesterdayTimestamp(): Long {
    return Clock.System.now().minus(1, DateTimeUnit.DAY, TimeZone.UTC).toEpochMilliseconds()
}

// String Extensions
fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return this.matches(emailRegex)
}

fun String.isValidTime(): Boolean {
    val timeRegex = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex()
    return this.matches(timeRegex)
}

// Firestore Extensions
inline fun <reified T> DocumentSnapshot.toObject(): T? {
    return this.toObject(T::class.java)
}

fun DocumentSnapshot.getStringOrNull(field: String): String? {
    return this.getString(field)
}

fun DocumentSnapshot.getLongOrDefault(field: String, default: Long = 0): Long {
    return this.getLong(field) ?: default
}

fun DocumentSnapshot.getIntOrDefault(field: String, default: Int = 0): Int {
    return this.getLong(field)?.toInt() ?: default
}

fun DocumentSnapshot.getBooleanOrDefault(field: String, default: Boolean = false): Boolean {
    return this.getBoolean(field) ?: default
}

// Collection Extensions
fun <T> List<T>.paginate(offset: Int, limit: Int): List<T> {
    if (offset >= this.size) return emptyList()
    val endIndex = minOf(offset + limit, this.size)
    return this.subList(offset, endIndex)
}

// Number Extensions
fun Double.roundToDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

fun Int.toPercentage(): String {
    return "$this%"
}

// Validation Extensions
fun String?.isNullOrBlankOrEmpty(): Boolean {
    return this == null || this.isBlank() || this.isEmpty()
}

fun <T> List<T>?.isNullOrEmpty(): Boolean {
    return this == null || this.isEmpty()
}



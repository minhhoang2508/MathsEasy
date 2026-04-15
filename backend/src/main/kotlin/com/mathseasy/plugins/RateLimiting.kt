package com.mathseasy.plugins

import com.mathseasy.models.errorResponse
import com.mathseasy.utils.Constants
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class RateLimitEntry(val count: Int, val windowStart: Long)

object RateLimiter {
    private val aiLimits = ConcurrentHashMap<String, RateLimitEntry>()
    private val generalLimits = ConcurrentHashMap<String, RateLimitEntry>()
    private val authLimits = ConcurrentHashMap<String, RateLimitEntry>()

    private val AI_MAX = Constants.RATE_LIMIT_AI_REQUESTS
    private val AI_WINDOW_MS = Constants.RATE_LIMIT_AI_WINDOW_MINUTES * 60 * 1000L
    private val GENERAL_MAX = Constants.RATE_LIMIT_GENERAL_REQUESTS
    private val GENERAL_WINDOW_MS = Constants.RATE_LIMIT_GENERAL_WINDOW_MINUTES * 60 * 1000L
    private const val AUTH_MAX = 20
    private const val AUTH_WINDOW_MS = 15 * 60 * 1000L

    fun checkAiLimit(userId: String): Boolean = checkLimit(userId, aiLimits, AI_MAX, AI_WINDOW_MS)
    fun checkGeneralLimit(userId: String): Boolean = checkLimit(userId, generalLimits, GENERAL_MAX, GENERAL_WINDOW_MS)
    fun checkAuthLimit(ip: String): Boolean = checkLimit(ip, authLimits, AUTH_MAX, AUTH_WINDOW_MS)

    private fun checkLimit(
        key: String,
        store: ConcurrentHashMap<String, RateLimitEntry>,
        maxRequests: Int,
        windowMs: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        val entry = store[key]

        if (entry == null || now - entry.windowStart > windowMs) {
            store[key] = RateLimitEntry(1, now)
            return true
        }

        if (entry.count >= maxRequests) {
            return false
        }

        store[key] = entry.copy(count = entry.count + 1)
        return true
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        aiLimits.entries.removeIf { now - it.value.windowStart > AI_WINDOW_MS }
        generalLimits.entries.removeIf { now - it.value.windowStart > GENERAL_WINDOW_MS }
        authLimits.entries.removeIf { now - it.value.windowStart > AUTH_WINDOW_MS }
    }
}

fun ApplicationCall.clientIp(): String {
    return request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.origin.remoteHost
}

suspend fun ApplicationCall.checkAiRateLimit(userId: String): Boolean {
    if (!RateLimiter.checkAiLimit(userId)) {
        respond(
            HttpStatusCode.TooManyRequests,
            errorResponse<Unit>(Constants.ERROR_RATE_LIMIT_EXCEEDED, "AI rate limit exceeded. Please try again later.")
        )
        return false
    }
    return true
}

suspend fun ApplicationCall.checkGeneralRateLimit(userId: String): Boolean {
    if (!RateLimiter.checkGeneralLimit(userId)) {
        respond(
            HttpStatusCode.TooManyRequests,
            errorResponse<Unit>(Constants.ERROR_RATE_LIMIT_EXCEEDED, "Rate limit exceeded. Please try again later.")
        )
        return false
    }
    return true
}

suspend fun ApplicationCall.checkAuthRateLimit(): Boolean {
    val ip = clientIp()
    if (!RateLimiter.checkAuthLimit(ip)) {
        respond(
            HttpStatusCode.TooManyRequests,
            errorResponse<Unit>(Constants.ERROR_RATE_LIMIT_EXCEEDED, "Too many requests. Please try again later.")
        )
        return false
    }
    return true
}

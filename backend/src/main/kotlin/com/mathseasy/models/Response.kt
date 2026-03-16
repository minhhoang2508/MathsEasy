package com.mathseasy.models

import kotlinx.serialization.Serializable

/**
 * Standard API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: ErrorDetails? = null
)

@Serializable
data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)

/**
 * Helper functions to create responses
 */
fun <T> successResponse(data: T, message: String? = null): ApiResponse<T> {
    return ApiResponse(
        success = true,
        message = message,
        data = data,
        error = null
    )
}

fun <T> errorResponse(code: String, message: String, details: Map<String, String>? = null): ApiResponse<T> {
    return ApiResponse(
        success = false,
        message = null,
        data = null,
        error = ErrorDetails(code, message, details)
    )
}

/**
 * Pagination response wrapper
 */
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean
)



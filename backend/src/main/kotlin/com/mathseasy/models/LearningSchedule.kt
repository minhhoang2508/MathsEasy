package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class LearningSchedule(
    val id: String? = null,
    val userId: String = "",
    val dayOfWeek: Int = 0, // 1 = Monday, 7 = Sunday
    val startTime: String = "", // Format: "HH:mm"
    val endTime: String = "",   // Format: "HH:mm"
    val active: Boolean = true, // Changed from isActive to match Firestore field name
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Request/Response DTOs
 */
@Serializable
data class CreateSchedulesRequest(
    val schedules: List<ScheduleInput>
)

@Serializable
data class ScheduleInput(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class UpdateScheduleRequest(
    val dayOfWeek: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val active: Boolean? = null
)

@Serializable
data class SchedulesResponse(
    val schedules: List<LearningSchedule>
)



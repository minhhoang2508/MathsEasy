package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class LearningHistory(
    val id: String? = null,
    val userId: String = "",
    val exerciseId: String = "",
    val userAnswer: String = "",
    val isCorrect: Boolean = false,
    val timeSpent: Int = 0, // in seconds
    val pointsEarned: Int = 0,
    val completedAt: Long = 0L,
    val feedback: String? = null
)

/**
 * Request/Response DTOs
 */
@Serializable
data class HistoryQuery(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val topic: String? = null,
    val isCorrect: Boolean? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

@Serializable
data class HistoryResponse(
    val history: List<LearningHistoryItem>,
    val total: Int
)

@Serializable
data class LearningHistoryItem(
    val history: LearningHistory,
    val exercise: Exercise?
)

@Serializable
data class DailyProgress(
    val date: String, // "YYYY-MM-DD"
    val exercisesCompleted: Int,
    val pointsEarned: Int,
    val timeSpent: Int,
    val accuracy: Double
)

@Serializable
data class DailyProgressResponse(
    val dailyProgress: List<DailyProgress>
)



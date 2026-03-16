package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String? = null,
    val title: String = "",
    val description: String? = null,
    val content: String = "", // The question text in English
    val options: List<String> = emptyList(), // List of 4 options
    val correctAnswer: String = "", // "A", "B", "C", or "D"
    val explanation: String = "", // Detailed explanation
    val difficulty: String = "", // "easy", "medium", "hard"
    val topic: String = "",
    val subtopic: String? = null,
    val points: Int = 0,
    val createdAt: Long = 0L,
    val createdBy: String = "", // "ai" or "admin"
    val metadata: Map<String, String>? = null
)

/**
 * Request/Response DTOs
 */
@Serializable
data class SubmitExerciseRequest(
    val answer: String, // "A", "B", "C", or "D"
    val timeSpent: Int  // in seconds
)

@Serializable
data class SubmitExerciseResponse(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val explanation: String,
    val pointsEarned: Int,
    val newTotalPoints: Int,
    val newBadges: List<UserBadge> = emptyList(),
    val streakUpdated: Boolean = false,
    val currentStreak: Int = 0
)

@Serializable
data class ExerciseQuery(
    val difficulty: String? = null,
    val topic: String? = null,
    val subtopic: String? = null,
    val limit: Int = 20,
    val offset: Int = 0
)

@Serializable
data class ExercisesResponse(
    val exercises: List<Exercise>,
    val total: Int,
    val hasMore: Boolean
)



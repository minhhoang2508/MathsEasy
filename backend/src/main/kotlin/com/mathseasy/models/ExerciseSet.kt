package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseSet(
    val id: String? = null,
    val title: String = "",
    val description: String? = null,
    val topic: String = "",
    val subtopic: String? = null,
    val difficulty: String = "",
    val exerciseIds: List<String> = emptyList(),
    val totalPoints: Int = 0,
    val questionCount: Int = 0,
    val createdAt: Long = 0L,
    val createdBy: String = ""
)

@Serializable
data class ExerciseSetDetail(
    val set: ExerciseSet,
    val exercises: List<Exercise>
)

@Serializable
data class ExerciseSetsResponse(
    val sets: List<ExerciseSet>,
    val total: Int,
    val hasMore: Boolean
)

@Serializable
data class SubmitSetRequest(
    val answers: List<SetAnswer>,
    val totalTimeSpent: Int
)

@Serializable
data class SetAnswer(
    val exerciseId: String,
    val answer: String
)

@Serializable
data class SubmitSetResponse(
    val correctCount: Int,
    val totalCount: Int,
    val score: Double,
    val pointsEarned: Int,
    val newTotalPoints: Int,
    val results: List<QuestionResult>,
    val streakUpdated: Boolean = false,
    val currentStreak: Int = 0,
    val newBadges: List<UserBadge> = emptyList()
)

@Serializable
data class QuestionResult(
    val exerciseId: String,
    val userAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val explanation: String,
    val pointsEarned: Int
)

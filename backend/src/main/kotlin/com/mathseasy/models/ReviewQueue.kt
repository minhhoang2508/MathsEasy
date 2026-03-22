package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class ReviewQueue(
    val id: String? = null,
    val userId: String = "",
    val setId: String = "",
    val topic: String = "",
    val dueDate: Long = 0L,
    val lastReviewed: Long = 0L,
    val intervalDays: Int = 1,
    val score: Double = 0.0
)

@Serializable
data class StudyPlanResponse(
    val todayPlan: List<StudyPlanItem>,
    val nextDays: List<NextDayPlan>,
    val reviewPlan: List<ReviewPlanItem>,
    val summary: String
)

@Serializable
data class StudyPlanItem(
    val type: String,
    val skill: String,
    val skillLabel: String,
    val setId: String? = null,
    val setTitle: String? = null,
    val numQuestions: Int = 10,
    val difficulty: String = "medium",
    val reason: String,
    val isPremium: Boolean = false
)

@Serializable
data class NextDayPlan(
    val day: Int,
    val focus: String
)

@Serializable
data class ReviewPlanItem(
    val skill: String,
    val skillLabel: String,
    val setId: String,
    val setTitle: String? = null,
    val nextReview: String
)

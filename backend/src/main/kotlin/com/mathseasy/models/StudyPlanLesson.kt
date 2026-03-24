package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class LessonAction(
    val action: String,
    val setId: String? = null,
    val lessonTitle: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val isPremium: Boolean = false,
    val message: String
)

@Serializable
data class LessonStudyPlan(
    val topic: String,
    val topicLabel: String,
    val lessonTitle: String,
    val difficulty: String,
    val score: Double,
    val reason: String,
    val actions: List<LessonAction>
)

@Serializable
data class LessonStudyPlanResponse(
    val recommendation: LessonStudyPlan
)

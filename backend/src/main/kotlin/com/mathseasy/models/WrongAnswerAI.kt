package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class WrongAnswerAnalysisRequest(
    val exerciseId: String,
    val userAnswer: String
)

@Serializable
data class SimilarExerciseDraft(
    val title: String,
    val content: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val topic: String,
    val difficulty: String
)

@Serializable
data class WrongAnswerAnalysisResponse(
    val analysis: String,
    val theory: String,
    val similarExercise: SimilarExerciseDraft
)

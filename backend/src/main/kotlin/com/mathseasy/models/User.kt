package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val totalPoints: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStreakDate: Long? = null,
    val badgeIds: List<String> = emptyList(),
    val fcmToken: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val premiumTopics: List<String> = emptyList()
)

@Serializable
data class UserPreferences(
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val notificationMinutesBefore: Int = 10
)

@Serializable
data class UserStats(
    val totalExercises: Int,
    val correctAnswers: Int,
    val accuracy: Double,
    val totalTimeSpent: Int,
    val averageTimePerExercise: Int,
    val exercisesByDifficulty: Map<String, Int>,
    val progressByTopic: Map<String, TopicProgress>
)

@Serializable
data class TopicProgress(
    val topic: String,
    val totalExercises: Int,
    val correctAnswers: Int,
    val accuracy: Double
)

/**
 * Request/Response DTOs
 */
@Serializable
data class RegisterRequest(
    val idToken: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val idToken: String
)

@Serializable
data class AuthResponse(
    val user: User,
    val isNewUser: Boolean = false,
    val redirectToScheduleSetup: Boolean = false
)

@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val photoUrl: String? = null,
    val preferences: UserPreferences? = null
)

@Serializable
data class UpdateFcmTokenRequest(
    val fcmToken: String
)



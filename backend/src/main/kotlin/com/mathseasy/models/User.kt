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
    val premiumTopics: List<String> = emptyList(),
    val avatarConfig: AvatarConfig? = null,
    val inventory: Map<String, Int> = emptyMap(),
    val previousStreak: Int = 0,
    val streakBroken: Boolean = false
)

@Serializable
data class UserPreferences(
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val notificationMinutesBefore: Int = 10,
    val timezone: String? = null
)

@Serializable
data class AvatarConfig(
    val seed: String = "default_user",
    val top: String = "shortHairShortFlat",
    val hairColor: String = "black",
    val hatColor: String = "blue01",
    val skinColor: String = "light",
    val eyes: String = "default",
    val eyebrows: String = "default",
    val mouth: String = "default",
    val facialHair: String = "blank",
    val facialHairColor: String = "brown",
    val accessories: String = "blank",
    val accessoriesColor: String = "262e33",
    val clothing: String = "shirtCrewNeck",
    val clothesColor: String = "262e33",
    val clothingGraphic: String = "pizza"
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

@Serializable
data class RegisterRequest(
    val idToken: String,
    val displayName: String? = null,
    val timezone: String? = null
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
    val preferences: UserPreferences? = null,
    val avatarConfig: AvatarConfig? = null
)

@Serializable
data class UpdateFcmTokenRequest(
    val fcmToken: String
)


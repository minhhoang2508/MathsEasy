package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class Badge(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val iconUrl: String? = null,
    val condition: BadgeCondition = BadgeCondition(),
    val rarity: String = "common", // "common", "rare", "epic", "legendary"
    val isActive: Boolean = true
)

@Serializable
data class BadgeCondition(
    val type: String = "exercises", // "points", "streak", "exercises", "accuracy"
    val threshold: Int = 0,
    val topic: String? = null
)

@Serializable
data class UserBadge(
    val id: String? = null,
    val userId: String = "",
    val badgeId: String = "",
    val earnedAt: Long = 0L,
    val isNew: Boolean = true
)

@Serializable
data class BadgeWithProgress(
    val badge: Badge,
    val isEarned: Boolean,
    val earnedAt: Long? = null,
    val progress: Int,
    val total: Int
)

@Serializable
data class BadgesResponse(
    val badges: List<BadgeWithProgress>
)



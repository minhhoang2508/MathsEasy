package com.mathseasy.models

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String? = null,
    val userId: String,
    val title: String,
    val body: String,
    val type: String, // "reminder", "badge", "streak", "achievement"
    val scheduledTime: Long? = null,
    val sentAt: Long? = null,
    val isRead: Boolean = false,
    val data: Map<String, String>? = null
)

@Serializable
data class NotificationPreferencesRequest(
    val notificationsEnabled: Boolean,
    val notificationMinutesBefore: Int
)



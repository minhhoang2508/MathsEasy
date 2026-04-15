package com.mathseasy.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification as FcmNotification
import com.mathseasy.models.Notification
import com.mathseasy.repositories.NotificationRepository
import com.mathseasy.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class NotificationService {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    private val notificationRepository = NotificationRepository()
    private val userRepository = UserRepository()
    private val emailService = EmailService()

    /**
     * Send notification to a user via FCM
     */
    suspend fun sendNotificationToUser(
        userId: String,
        title: String,
        body: String,
        type: String,
        data: Map<String, String>? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get user's FCM token
                val user = userRepository.getUserById(userId)
                if (user == null) {
                    logger.error("User $userId not found")
                    return@withContext false
                }

                // Check if notifications are enabled
                if (!user.preferences.notificationsEnabled) {
                    logger.info("Notifications disabled for user $userId")
                    return@withContext false
                }

                val fcmToken = user.fcmToken
                if (fcmToken.isNullOrEmpty()) {
                    logger.warn("No FCM token found for user $userId. Creating notification record only.")
                    // Still create notification record even without FCM token
                    // This allows the notification to appear in history
                    val notification = Notification(
                        userId = userId,
                        title = title,
                        body = body,
                        type = type,
                        scheduledTime = System.currentTimeMillis(),
                        sentAt = System.currentTimeMillis(), // Mark as sent immediately
                        isRead = false,
                        data = data
                    )
                    notificationRepository.createNotification(notification)
                    return@withContext true // Return success
                }

                // Create notification record in Firestore
                val notification = Notification(
                    userId = userId,
                    title = title,
                    body = body,
                    type = type,
                    scheduledTime = System.currentTimeMillis(),
                    sentAt = null,
                    isRead = false,
                    data = data
                )
                val savedNotification = notificationRepository.createNotification(notification)

                // Skip FCM push for test tokens (for development/testing)
                if (fcmToken.startsWith("test-")) {
                    logger.info("Skipping FCM push for test token (dev mode). Notification saved: ${savedNotification.id}")
                    // Mark as sent even though we didn't actually send via FCM
                    notificationRepository.markAsSent(
                        savedNotification.id!!,
                        System.currentTimeMillis()
                    )
                    return@withContext true
                }

                // Send FCM message
                val message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(
                        FcmNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
                    )
                    .putData("notificationId", savedNotification.id ?: "")
                    .putData("type", type)
                    .apply {
                        data?.forEach { (key, value) ->
                            putData(key, value)
                        }
                    }
                    .build()

                val response = FirebaseMessaging.getInstance().send(message)
                logger.info("Successfully sent notification to user $userId: $response")

                // Mark as sent
                notificationRepository.markAsSent(
                    savedNotification.id!!,
                    System.currentTimeMillis()
                )

                true
            } catch (e: Exception) {
                logger.error("Failed to send notification to user $userId: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Send learning reminder notification (both push and email if enabled)
     */
    suspend fun sendLearningReminder(userId: String, minutesUntilSchedule: Int, scheduledTime: String): Boolean {
        // Get user info for email
        val user = userRepository.getUserById(userId)
        
        // Send push notification
        val pushSent = sendNotificationToUser(
            userId = userId,
            title = "📚 Time to Study!",
            body = "Your learning session starts in $minutesUntilSchedule minutes. Ready to learn?",
            type = "reminder",
            data = mapOf(
                "minutesUntilSchedule" to minutesUntilSchedule.toString()
            )
        )
        
        // Send email notification if enabled
        var emailSent = false
        if (user != null && user.preferences.emailNotificationsEnabled && user.email.isNotBlank()) {
            try {
                emailSent = emailService.sendLearningReminder(
                    toEmail = user.email,
                    userName = user.displayName ?: "Student",
                    scheduledTime = scheduledTime,
                    minutesBefore = minutesUntilSchedule
                )
                if (emailSent) {
                    logger.info("Email reminder sent to ${user.email}")
                }
            } catch (e: Exception) {
                logger.error("Failed to send email reminder to ${user.email}: ${e.message}", e)
            }
        }
        
        return pushSent || emailSent
    }

    /**
     * Send badge earned notification (both push and email if enabled)
     */
    suspend fun sendBadgeEarnedNotification(
        userId: String,
        badgeName: String,
        badgeDescription: String,
        badgeId: String
    ): Boolean {
        // Get user info for email
        val user = userRepository.getUserById(userId)
        
        // Send push notification
        val pushSent = sendNotificationToUser(
            userId = userId,
            title = "🏆 New Badge Earned!",
            body = "Congratulations! You earned the \"$badgeName\" badge!",
            type = "badge",
            data = mapOf(
                "badgeId" to badgeId,
                "badgeName" to badgeName,
                "badgeDescription" to badgeDescription
            )
        )
        
        // Send email notification if enabled
        var emailSent = false
        if (user != null && user.preferences.emailNotificationsEnabled && user.email.isNotBlank()) {
            try {
                emailSent = emailService.sendAchievementEmail(
                    toEmail = user.email,
                    userName = user.displayName ?: "Student",
                    achievementTitle = badgeName,
                    achievementDescription = badgeDescription
                )
                if (emailSent) {
                    logger.info("Achievement email sent to ${user.email}")
                }
            } catch (e: Exception) {
                logger.error("Failed to send achievement email to ${user.email}: ${e.message}", e)
            }
        }
        
        return pushSent || emailSent
    }

    /**
     * Send streak reminder notification
     */
    suspend fun sendStreakReminder(userId: String, currentStreak: Int): Boolean {
        val title = if (currentStreak > 0) {
            "🔥 Don't Break Your Streak!"
        } else {
            "📚 Start Your Learning Streak!"
        }

        val body = if (currentStreak > 0) {
            "You're on a $currentStreak-day streak! Complete an exercise today to keep it going!"
        } else {
            "Complete an exercise today to start building your learning streak!"
        }

        val pushSent = sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = "streak",
            data = mapOf(
                "currentStreak" to currentStreak.toString()
            )
        )
        
        val user = userRepository.getUserById(userId)
        var emailSent = false
        if (user != null && user.preferences.emailNotificationsEnabled && user.email.isNotBlank()) {
            try {
                emailSent = emailService.sendStreakReminderEmail(
                    toEmail = user.email,
                    userName = user.displayName ?: "Student",
                    currentStreak = currentStreak
                )
                if (emailSent) {
                    logger.info("Streak reminder email sent to ${user.email}")
                }
            } catch (e: Exception) {
                logger.error("Failed to send streak reminder email to ${user.email}: ${e.message}", e)
            }
        }
        
        return pushSent || emailSent
    }

    /**
     * Send achievement milestone notification
     */
    suspend fun sendAchievementNotification(
        userId: String,
        milestone: String,
        description: String
    ): Boolean {
        return sendNotificationToUser(
            userId = userId,
            title = "🎉 Achievement Unlocked!",
            body = milestone,
            type = "achievement",
            data = mapOf(
                "milestone" to milestone,
                "description" to description
            )
        )
    }

    /**
     * Schedule a notification for later
     */
    suspend fun scheduleNotification(
        userId: String,
        title: String,
        body: String,
        type: String,
        scheduledTime: Long,
        data: Map<String, String>? = null
    ): Notification {
        val notification = Notification(
            userId = userId,
            title = title,
            body = body,
            type = type,
            scheduledTime = scheduledTime,
            sentAt = null,
            isRead = false,
            data = data
        )
        return notificationRepository.createNotification(notification)
    }

    /**
     * Process and send pending notifications
     * This should be called by a scheduler periodically
     */
    suspend fun processPendingNotifications() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val pendingNotifications = notificationRepository.getPendingNotifications(currentTime)

                logger.info("Processing ${pendingNotifications.size} pending notifications")

                pendingNotifications.forEach { notification ->
                    try {
                        val sent = sendNotificationToUser(
                            userId = notification.userId,
                            title = notification.title,
                            body = notification.body,
                            type = notification.type,
                            data = notification.data
                        )

                        if (sent) {
                            notification.id?.let {
                                notificationRepository.markAsSent(it, System.currentTimeMillis())
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to send pending notification ${notification.id}: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to process pending notifications: ${e.message}", e)
            }
        }
    }

    /**
     * Get notification history for user
     */
    suspend fun getNotificationHistory(
        userId: String,
        limit: Int = 50,
        includeRead: Boolean = true
    ): List<Notification> {
        return notificationRepository.getNotificationsByUserId(userId, limit, includeRead)
    }

    /**
     * Mark notification as read (with ownership check)
     */
    suspend fun markNotificationAsRead(notificationId: String, userId: String): Boolean {
        val notification = notificationRepository.getNotificationById(notificationId)
            ?: return false
        if (notification.userId != userId) return false
        return notificationRepository.markAsRead(notificationId)
    }

    /**
     * Get unread notification count
     */
    suspend fun getUnreadCount(userId: String): Int {
        return notificationRepository.getUnreadCount(userId)
    }

    /**
     * Delete notification (with ownership check)
     */
    suspend fun deleteNotification(notificationId: String, userId: String): Boolean {
        val notification = notificationRepository.getNotificationById(notificationId)
            ?: return false
        if (notification.userId != userId) return false
        return notificationRepository.deleteNotification(notificationId)
    }

    /**
     * Update user's FCM token
     */
    suspend fun updateFcmToken(userId: String, fcmToken: String): Boolean {
        return try {
            userRepository.updateFcmToken(userId, fcmToken)
            logger.info("Updated FCM token for user $userId")
            true
        } catch (e: Exception) {
            logger.error("Failed to update FCM token for user $userId: ${e.message}", e)
            false
        }
    }
}

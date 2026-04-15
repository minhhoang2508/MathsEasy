package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.mathseasy.models.Notification
import com.mathseasy.services.FirebaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class NotificationRepository {
    private val logger = LoggerFactory.getLogger(NotificationRepository::class.java)
    private val db: Firestore = FirebaseService.getFirestore()
    private val notificationsCollection = db.collection("notifications")

    /**
     * Create a new notification
     */
    suspend fun createNotification(notification: Notification): Notification {
        return withContext(Dispatchers.IO) {
            try {
                val docRef = notificationsCollection.document()
                val notificationWithId = notification.copy(id = docRef.id)
                
                docRef.set(notificationWithId).get()
                logger.info("Created notification ${docRef.id} for user ${notification.userId}")
                notificationWithId
            } catch (e: Exception) {
                logger.error("Failed to create notification: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Get notification by ID
     */
    suspend fun getNotificationById(notificationId: String): Notification? {
        return withContext(Dispatchers.IO) {
            try {
                val doc = notificationsCollection.document(notificationId).get().get()
                if (doc.exists()) {
                    parseNotification(doc)
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error("Failed to get notification $notificationId: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Get all notifications for a user
     */
    suspend fun getNotificationsByUserId(
        userId: String,
        limit: Int = 50,
        includeRead: Boolean = true
    ): List<Notification> {
        return withContext(Dispatchers.IO) {
            try {
                var query: Query = notificationsCollection
                    .whereEqualTo("userId", userId)
                    .limit(limit)
                    // Temporarily removed orderBy to avoid index requirement
                    // .orderBy("scheduledTime", Query.Direction.DESCENDING)

                if (!includeRead) {
                    query = query.whereEqualTo("isRead", false)
                }

                val snapshot = query.get().get(10, TimeUnit.SECONDS)
                logger.info("Found ${snapshot.documents.size} notification documents for user $userId")
                
                snapshot.documents.mapNotNull { doc ->
                    try {
                        parseNotification(doc)
                    } catch (e: Exception) {
                        logger.error("Failed to parse notification ${doc.id}: ${e.message}", e)
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to get notifications for user $userId: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Manual parse notification from Firestore document
     * (Similar to Badge and LearningHistory manual parsing)
     */
    private fun parseNotification(doc: com.google.cloud.firestore.DocumentSnapshot): Notification? {
        return try {
            val data = doc.data ?: return null
            
            logger.info("Parsing notification ${doc.id}: $data")
            
            // Parse data map if exists
            val dataMap = (data["data"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k is String && v is String) k to v else null
            }?.toMap()
            
            val notification = Notification(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                body = data["body"] as? String ?: "",
                type = data["type"] as? String ?: "",
                scheduledTime = data["scheduledTime"] as? Long,
                sentAt = data["sentAt"] as? Long,
                isRead = (data["isRead"] as? Boolean) ?: (data["read"] as? Boolean) ?: false,  // Handle both field names
                data = dataMap
            )
            
            logger.info("Parsed notification: $notification")
            notification
        } catch (e: Exception) {
            logger.error("Failed to parse notification ${doc.id}: ${e.message}\nStack: ${e.stackTraceToString()}")
            null
        }
    }

    /**
     * Get pending notifications (scheduled but not sent yet)
     */
    suspend fun getPendingNotifications(currentTime: Long): List<Notification> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = notificationsCollection
                    .whereEqualTo("sentAt", null)
                    .whereLessThanOrEqualTo("scheduledTime", currentTime)
                    .limit(100)
                    .get()
                    .get(10, TimeUnit.SECONDS)

                snapshot.documents.mapNotNull { doc ->
                    try {
                        parseNotification(doc)
                    } catch (e: Exception) {
                        logger.error("Failed to parse pending notification ${doc.id}: ${e.message}", e)
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to get pending notifications: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Mark notification as sent
     */
    suspend fun markAsSent(notificationId: String, sentAt: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                notificationsCollection
                    .document(notificationId)
                    .update("sentAt", sentAt)
                    .get()
                logger.info("Marked notification $notificationId as sent")
                true
            } catch (e: Exception) {
                logger.error("Failed to mark notification $notificationId as sent: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Mark notification as read
     */
    suspend fun markAsRead(notificationId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                notificationsCollection
                    .document(notificationId)
                    .update("isRead", true)
                    .get()
                logger.info("Marked notification $notificationId as read")
                true
            } catch (e: Exception) {
                logger.error("Failed to mark notification $notificationId as read: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Delete notification
     */
    suspend fun deleteNotification(notificationId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                notificationsCollection.document(notificationId).delete().get()
                logger.info("Deleted notification $notificationId")
                true
            } catch (e: Exception) {
                logger.error("Failed to delete notification $notificationId: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Delete all notifications for a user
     */
    suspend fun deleteAllForUser(userId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                var totalDeleted = 0
                while (true) {
                    val snapshot = notificationsCollection
                        .whereEqualTo("userId", userId)
                        .limit(500)
                        .get()
                        .get()

                    if (snapshot.isEmpty) break

                    val batch = db.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().get()
                    totalDeleted += snapshot.documents.size
                }

                logger.info("Deleted $totalDeleted notifications for user $userId")
                totalDeleted
            } catch (e: Exception) {
                logger.error("Failed to delete notifications for user $userId: ${e.message}", e)
                -1
            }
        }
    }

    /**
     * Get unread notification count
     */
    suspend fun getUnreadCount(userId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = notificationsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isRead", false)
                    .get()
                    .get()
                snapshot.size()
            } catch (e: Exception) {
                logger.error("Failed to get unread count for user $userId: ${e.message}", e)
                0
            }
        }
    }
}

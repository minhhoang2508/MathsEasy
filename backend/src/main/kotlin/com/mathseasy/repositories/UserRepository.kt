package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.mathseasy.models.User
import com.mathseasy.models.UserPreferences
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import com.mathseasy.utils.getCurrentTimestamp
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class UserRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_USERS)
    
    suspend fun createUser(user: User): User {
        return try {
            collection.document(user.uid).set(user).get()
            logger.info { "User created: ${user.uid}" }
            user
        } catch (e: Exception) {
            logger.error(e) { "Failed to create user: ${e.message}" }
            throw e
        }
    }
    
    suspend fun getUserById(uid: String): User? {
        return try {
            val document = collection.document(uid).get().get()
            if (document.exists()) {
                document.toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get user: ${e.message}" }
            throw e
        }
    }
    
    suspend fun updateUser(uid: String, updates: Map<String, Any>): User? {
        return try {
            val updatesWithTimestamp = updates.toMutableMap().apply {
                put("lastLoginAt", getCurrentTimestamp())
            }
            collection.document(uid).update(updatesWithTimestamp).get()
            getUserById(uid)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update user: ${e.message}" }
            throw e
        }
    }
    
    suspend fun deleteUser(uid: String): Boolean {
        return try {
            collection.document(uid).delete().get()
            logger.info { "User deleted: $uid" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete user: ${e.message}" }
            false
        }
    }
    
    suspend fun updateLastLogin(uid: String): User? {
        return updateUser(uid, mapOf("lastLoginAt" to getCurrentTimestamp()))
    }
    
    suspend fun updatePoints(uid: String, points: Int): User? {
        return try {
            val docRef = collection.document(uid)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef).get()
                val currentPoints = snapshot.getLong("totalPoints")?.toInt() ?: 0
                transaction.update(docRef, mapOf(
                    "totalPoints" to (currentPoints + points),
                    "lastLoginAt" to getCurrentTimestamp()
                ))
            }.get()
            getUserById(uid)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update points atomically: ${e.message}" }
            throw e
        }
    }
    
    suspend fun updateStreak(uid: String, currentStreak: Int, longestStreak: Int): User? {
        return updateUser(
            uid, 
            mapOf(
                "currentStreak" to currentStreak,
                "longestStreak" to longestStreak,
                "lastStreakDate" to getCurrentTimestamp()
            )
        )
    }
    
    suspend fun addBadge(uid: String, badgeId: String): User? {
        val user = getUserById(uid) ?: return null
        val badges = user.badgeIds.toMutableList()
        if (!badges.contains(badgeId)) {
            badges.add(badgeId)
            return updateUser(uid, mapOf("badgeIds" to badges))
        }
        return user
    }
    
    suspend fun updateFcmToken(uid: String, fcmToken: String): User? {
        return updateUser(uid, mapOf("fcmToken" to fcmToken))
    }
    
    suspend fun updatePreferences(uid: String, preferences: UserPreferences): User? {
        return updateUser(uid, mapOf("preferences" to preferences))
    }
    
    suspend fun userExists(uid: String): Boolean {
        return try {
            val document = collection.document(uid).get().get()
            document.exists()
        } catch (e: Exception) {
            logger.error(e) { "Failed to check user existence: ${e.message}" }
            false
        }
    }
    suspend fun getAllUsersEnabledNotifications(): List<User> {
        return try {
            val snapshot = collection
                .whereEqualTo("preferences.notificationsEnabled", true)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(User::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get users with notifications enabled: ${e.message}" }
            emptyList()
        }
    }
}



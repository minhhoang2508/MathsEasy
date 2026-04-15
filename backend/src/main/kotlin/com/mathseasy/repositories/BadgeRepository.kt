package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.mathseasy.models.Badge
import com.mathseasy.models.BadgeCondition
import com.mathseasy.models.UserBadge
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import mu.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

class BadgeRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val badgesCollection = db.collection(Constants.COLLECTION_BADGES)
    private val userBadgesCollection = db.collection(Constants.COLLECTION_USER_BADGES)
    
    /**
     * Get all active badges
     */
    suspend fun getAllBadges(): List<Badge> {
        return try {
            // Get ALL badges first (remove isActive filter for debugging)
            val snapshot = badgesCollection.get().get()
            
            logger.info { "Found ${snapshot.documents.size} badge documents in Firestore" }
            
            val badges = snapshot.documents.mapNotNull { doc ->
                try {
                    logger.info { "Parsing badge ${doc.id}: ${doc.data}" }
                    val data = doc.data ?: return@mapNotNull null
                    
                    // Manually parse to handle "active" -> "isActive" field mismatch
                    val conditionMap = data["condition"] as? Map<*, *>
                    val condition = BadgeCondition(
                        type = conditionMap?.get("type") as? String ?: "exercises",
                        threshold = (conditionMap?.get("threshold") as? Long)?.toInt() ?: 0,
                        topic = conditionMap?.get("topic") as? String
                    )
                    
                    val badge = Badge(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        iconUrl = data["iconUrl"] as? String,
                        condition = condition,
                        rarity = data["rarity"] as? String ?: "common",
                        isActive = data["active"] as? Boolean ?: true // Map "active" to "isActive"
                    )
                    
                    logger.info { "Parsed badge: $badge" }
                    badge
                } catch (e: Exception) {
                    logger.error { "Failed to parse badge ${doc.id}: ${e.message}\nStack: ${e.stackTraceToString()}" }
                    null
                }
            }
            
            // Filter active badges after parsing
            val activeBadges = badges.filter { it.isActive }
            logger.info { "Returning ${activeBadges.size} active badges out of ${badges.size} total" }
            
            activeBadges
        } catch (e: Exception) {
            logger.error(e) { "Failed to get badges: ${e.message}" }
            emptyList()
        }
    }
    
    /**
     * Get badge by ID
     */
    suspend fun getBadgeById(badgeId: String): Badge? {
        return try {
            val doc = badgesCollection.document(badgeId).get().get()
            if (doc.exists()) {
                doc.toObject(Badge::class.java)?.copy(id = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get badge $badgeId: ${e.message}" }
            null
        }
    }
    
    /**
     * Create a new badge (admin only)
     */
    suspend fun createBadge(badge: Badge): Badge? {
        return try {
            val docRef = badgesCollection.document()
            val badgeWithId = badge.copy(id = docRef.id)
            docRef.set(badgeWithId).get()
            logger.info { "Badge created: ${docRef.id}" }
            badgeWithId
        } catch (e: Exception) {
            logger.error(e) { "Failed to create badge: ${e.message}" }
            null
        }
    }
    
    /**
     * Get all badges earned by a user
     */
    suspend fun getUserBadges(userId: String): List<UserBadge> {
        return try {
            val snapshot = userBadgesCollection
                .whereEqualTo("userId", userId)
                .orderBy("earnedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { 
                try {
                    it.toObject(UserBadge::class.java)?.copy(id = it.id)
                } catch (e: Exception) {
                    logger.error { "Failed to parse user badge ${it.id}: ${e.message}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get user badges for $userId: ${e.message}" }
            emptyList()
        }
    }
    
    /**
     * Check if user has already earned a specific badge
     */
    suspend fun hasUserEarnedBadge(userId: String, badgeId: String): Boolean {
        return try {
            val snapshot = userBadgesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("badgeId", badgeId)
                .limit(1)
                .get()
                .get()
            
            !snapshot.isEmpty
        } catch (e: Exception) {
            logger.error(e) { "Failed to check user badge: ${e.message}" }
            false
        }
    }
    
    /**
     * Award a badge to a user
     */
    suspend fun awardBadge(userId: String, badgeId: String): UserBadge? {
        return try {
            // Check if already earned
            if (hasUserEarnedBadge(userId, badgeId)) {
                logger.info { "User $userId already has badge $badgeId" }
                return null
            }
            
            val docRef = userBadgesCollection.document()
            val userBadge = UserBadge(
                id = docRef.id,
                userId = userId,
                badgeId = badgeId,
                earnedAt = System.currentTimeMillis(),
                isNew = true
            )
            
            docRef.set(userBadge).get()
            logger.info { "Badge $badgeId awarded to user $userId" }
            userBadge
        } catch (e: Exception) {
            logger.error(e) { "Failed to award badge: ${e.message}" }
            null
        }
    }
    
    /**
     * Mark badge as viewed (not new anymore)
     * Takes userId and badgeId, finds the userBadge document and marks it as viewed
     */
    suspend fun markBadgeAsViewed(userId: String, badgeId: String): Boolean {
        return try {
            // Query userBadges collection to find the document for this user+badge
            val snapshot = userBadgesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("badgeId", badgeId)
                .limit(1)
                .get()
                .get()
            
            if (snapshot.isEmpty) {
                logger.warn { "No userBadge found for userId=$userId, badgeId=$badgeId" }
                return false
            }
            
            val userBadgeDoc = snapshot.documents[0]
            userBadgeDoc.reference
                .update("isNew", false)
                .get()
            
            logger.info { "Badge $badgeId marked as viewed for user $userId" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to mark badge as viewed: ${e.message}" }
            false
        }
    }
    
    /**
     * Get count of badges earned by user
     */
    suspend fun getUserBadgeCount(userId: String): Int {
        return try {
            val snapshot = userBadgesCollection
                .whereEqualTo("userId", userId)
                .get()
                .get()
            
            snapshot.documents.size
        } catch (e: Exception) {
            logger.error(e) { "Failed to count user badges: ${e.message}" }
            0
        }
    }
    
    /**
     * Get new (unviewed) badges count
     */
    suspend fun getNewBadgesCount(userId: String): Int {
        return try {
            val snapshot = userBadgesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isNew", true)
                .get()
                .get()
            
            snapshot.documents.size
        } catch (e: Exception) {
            logger.error(e) { "Failed to count new badges: ${e.message}" }
            0
        }
    }

    /**
     * Delete all user badges for a user
     */
    suspend fun deleteAllForUser(userId: String): Int {
        return try {
            var deletedCount = 0
            while (true) {
                val snapshot = userBadgesCollection
                    .whereEqualTo("userId", userId)
                    .limit(500)
                    .get()
                    .get()

                if (snapshot.isEmpty) break

                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().get()
                deletedCount += snapshot.documents.size
            }

            logger.info { "Deleted $deletedCount user badges for user $userId" }
            deletedCount
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete user badges for $userId: ${e.message}" }
            -1
        }
    }
}

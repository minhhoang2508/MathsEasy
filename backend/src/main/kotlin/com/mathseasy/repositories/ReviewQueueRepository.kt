package com.mathseasy.repositories

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.mathseasy.models.ReviewQueue
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ReviewQueueRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_REVIEW_QUEUE)

    private fun parseReviewItem(doc: DocumentSnapshot): ReviewQueue? {
        return try {
            val data = doc.data ?: return null
            ReviewQueue(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                setId = data["setId"] as? String ?: "",
                topic = data["topic"] as? String ?: "",
                dueDate = data["dueDate"] as? Long ?: 0L,
                lastReviewed = data["lastReviewed"] as? Long ?: 0L,
                intervalDays = (data["intervalDays"] as? Long)?.toInt() ?: 1,
                score = (data["score"] as? Number)?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            logger.error { "Failed to parse review item ${doc.id}: ${e.message}" }
            null
        }
    }

    private fun toFirestoreMap(item: ReviewQueue): Map<String, Any?> = mapOf(
        "userId" to item.userId,
        "setId" to item.setId,
        "topic" to item.topic,
        "dueDate" to item.dueDate,
        "lastReviewed" to item.lastReviewed,
        "intervalDays" to item.intervalDays,
        "score" to item.score
    )

    suspend fun upsertReviewItem(
        userId: String,
        setId: String,
        topic: String,
        score: Double,
        completedAt: Long
    ): ReviewQueue {
        return try {
            val intervalDays = when {
                score < 50.0 -> 1
                score < 80.0 -> 3
                else -> 7
            }
            val dueDate = completedAt + intervalDays * 24 * 60 * 60 * 1000L

            val existing = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("setId", setId)
                .get().get()

            val item = ReviewQueue(
                userId = userId,
                setId = setId,
                topic = topic,
                dueDate = dueDate,
                lastReviewed = completedAt,
                intervalDays = intervalDays,
                score = score
            )

            if (existing.documents.isNotEmpty()) {
                val docRef = existing.documents.first().reference
                docRef.set(toFirestoreMap(item)).get()
                logger.info { "Review item updated for user=$userId set=$setId interval=${intervalDays}d" }
                item.copy(id = docRef.id)
            } else {
                val docRef = collection.document()
                val data = toFirestoreMap(item) + ("id" to docRef.id)
                docRef.set(data).get()
                logger.info { "Review item created for user=$userId set=$setId interval=${intervalDays}d" }
                item.copy(id = docRef.id)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to upsert review item: ${e.message}" }
            throw e
        }
    }

    suspend fun getDueReviews(userId: String, now: Long): List<ReviewQueue> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereLessThanOrEqualTo("dueDate", now)
                .get().get()
            snapshot.documents.mapNotNull { parseReviewItem(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get due reviews: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getAllReviewsByUser(userId: String): List<ReviewQueue> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .get().get()
            snapshot.documents.mapNotNull { parseReviewItem(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all reviews: ${e.message}" }
            emptyList()
        }
    }

    suspend fun deleteReviewItem(userId: String, setId: String): Boolean {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("setId", setId)
                .get().get()
            snapshot.documents.forEach { it.reference.delete().get() }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete review item: ${e.message}" }
            false
        }
    }
}

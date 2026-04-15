package com.mathseasy.repositories

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.mathseasy.models.LearningHistory
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class HistoryRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_LEARNING_HISTORY)
    
    /**
     * Manually parse DocumentSnapshot to handle field name mismatches
     * (e.g., Firestore has "correct" but model expects "isCorrect")
     */
    private fun parseHistory(doc: DocumentSnapshot): LearningHistory? {
        return try {
            val data = doc.data ?: return null
            LearningHistory(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                exerciseId = data["exerciseId"] as? String ?: "",
                setId = data["setId"] as? String ?: "",
                userAnswer = data["userAnswer"] as? String ?: "",
                isCorrect = (data["correct"] as? Boolean) ?: (data["isCorrect"] as? Boolean) ?: false,
                timeSpent = (data["timeSpent"] as? Long)?.toInt() ?: 0,
                pointsEarned = (data["pointsEarned"] as? Long)?.toInt() ?: 0,
                completedAt = data["completedAt"] as? Long ?: 0L,
                feedback = data["feedback"] as? String
            )
        } catch (e: Exception) {
            logger.error { "Failed to parse history ${doc.id}: ${e.message}" }
            null
        }
    }
    
    suspend fun createHistory(history: LearningHistory): LearningHistory {
        return try {
            val docRef = collection.document()
            val historyWithId = history.copy(id = docRef.id)
            
            // Save with "correct" field name for consistency with existing Firestore data
            val data = mapOf(
                "id" to historyWithId.id,
                "userId" to historyWithId.userId,
                "exerciseId" to historyWithId.exerciseId,
                "setId" to historyWithId.setId,
                "userAnswer" to historyWithId.userAnswer,
                "correct" to historyWithId.isCorrect,
                "timeSpent" to historyWithId.timeSpent,
                "pointsEarned" to historyWithId.pointsEarned,
                "completedAt" to historyWithId.completedAt,
                "feedback" to historyWithId.feedback
            )
            
            docRef.set(data).get()
            logger.info { "History created: ${historyWithId.id}" }
            historyWithId
        } catch (e: Exception) {
            logger.error(e) { "Failed to create history: ${e.message}" }
            throw e
        }
    }

    suspend fun createHistoriesBatch(histories: List<LearningHistory>): List<LearningHistory> {
        if (histories.isEmpty()) return emptyList()
        return try {
            val batch = db.batch()
            val createdHistories = mutableListOf<LearningHistory>()
            
            for (history in histories) {
                val docRef = collection.document()
                val historyWithId = history.copy(id = docRef.id)
                createdHistories.add(historyWithId)
                
                val data = mapOf(
                    "id" to historyWithId.id,
                    "userId" to historyWithId.userId,
                    "exerciseId" to historyWithId.exerciseId,
                    "setId" to historyWithId.setId,
                    "userAnswer" to historyWithId.userAnswer,
                    "correct" to historyWithId.isCorrect,
                    "timeSpent" to historyWithId.timeSpent,
                    "pointsEarned" to historyWithId.pointsEarned,
                    "completedAt" to historyWithId.completedAt,
                    "feedback" to historyWithId.feedback
                )
                batch.set(docRef, data)
            }
            
            batch.commit().get()
            logger.info { "Batch created ${createdHistories.size} histories" }
            createdHistories
        } catch (e: Exception) {
            logger.error(e) { "Failed to batch create histories: ${e.message}" }
            throw e
        }
    }
    
    suspend fun getHistoryByUserId(userId: String, limit: Int = 100): List<LearningHistory> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { parseHistory(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get history: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getHistoryByUserIdAndDateRange(
        userId: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 100
    ): List<LearningHistory> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("completedAt", startDate)
                .whereLessThanOrEqualTo("completedAt", endDate)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { parseHistory(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get history by date range: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getHistoryById(historyId: String): LearningHistory? {
        return try {
            val document = collection.document(historyId).get().get()
            if (document.exists()) {
                parseHistory(document)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get history by ID: ${e.message}" }
            null
        }
    }
    
    suspend fun getRecentHistoryByUserId(userId: String, days: Int = 30): List<LearningHistory> {
        val startDate = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return getHistoryByUserIdAndDateRange(userId, startDate, System.currentTimeMillis())
    }
    
    suspend fun countHistoryByUserId(userId: String): Int {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .get()
                .get()
            snapshot.documents.size
        } catch (e: Exception) {
            logger.error(e) { "Failed to count history: ${e.message}" }
            0
        }
    }

    suspend fun getCompletedSetIdsByUserId(userId: String, limit: Int = 5000): List<String> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .get()

            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val setId = data["setId"] as? String ?: ""
                setId.ifBlank { null }
            }.distinct()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get completed set ids: ${e.message}" }
            emptyList()
        }
    }

    suspend fun deleteAllForUser(userId: String): Int {
        return try {
            var deletedCount = 0
            while (true) {
                val snapshot = collection
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
            logger.info { "Deleted $deletedCount learning history records for user $userId" }
            deletedCount
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete learning history for user $userId: ${e.message}" }
            -1
        }
    }
}


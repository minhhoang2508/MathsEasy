package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.mathseasy.models.ExerciseSet
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExerciseSetRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_EXERCISE_SETS)

    suspend fun createSet(set: ExerciseSet): ExerciseSet {
        return try {
            val docRef = collection.document()
            val setWithId = set.copy(id = docRef.id)
            docRef.set(setWithId).get()
            logger.info { "ExerciseSet created: ${setWithId.id}" }
            setWithId
        } catch (e: Exception) {
            logger.error(e) { "Failed to create exercise set: ${e.message}" }
            throw e
        }
    }

    suspend fun getSetById(setId: String): ExerciseSet? {
        return try {
            val document = collection.document(setId).get().get()
            if (document.exists()) {
                document.toObject(ExerciseSet::class.java)
            } else null
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercise set: ${e.message}" }
            null
        }
    }

    suspend fun getSets(
        difficulty: String? = null,
        topic: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<ExerciseSet> {
        return try {
            var query: Query = collection

            if (difficulty != null) {
                query = query.whereEqualTo("difficulty", difficulty)
            }
            if (topic != null) {
                query = query.whereEqualTo("topic", topic)
            }

            val snapshot = query.get().get()
            val allSets = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ExerciseSet::class.java)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse exercise set ${doc.id}" }
                    null
                }
            }

            allSets.sortedByDescending { it.createdAt }.drop(offset).take(limit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercise sets: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getTotalCount(): Int {
        return try {
            collection.get().get().documents.size
        } catch (e: Exception) {
            logger.error(e) { "Failed to count exercise sets: ${e.message}" }
            0
        }
    }
}

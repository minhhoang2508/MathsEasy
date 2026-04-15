package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.mathseasy.models.Exercise
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExerciseRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_EXERCISES)
    
    suspend fun createExercise(exercise: Exercise): Exercise {
        return try {
            val docRef = collection.document()
            val exerciseWithId = exercise.copy(id = docRef.id)
            docRef.set(exerciseWithId).get()
            logger.info { "Exercise created: ${exerciseWithId.id}" }
            exerciseWithId
        } catch (e: Exception) {
            logger.error(e) { "Failed to create exercise: ${e.message}" }
            throw e
        }
    }
    
    suspend fun getExerciseById(exerciseId: String): Exercise? {
        return try {
            val document = collection.document(exerciseId).get().get()
            if (document.exists()) {
                document.toObject(Exercise::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercise: ${e.message}" }
            null
        }
    }
    
    suspend fun getExercisesByIds(ids: List<String>): List<Exercise> = coroutineScope {
        if (ids.isEmpty()) return@coroutineScope emptyList()
        
        try {
            // Firestore 'in' queries natively support up to 30 items, but chunking by 10 is safest.
            val chunks = ids.chunked(10)
            chunks.map { chunk ->
                async {
                    val snapshot = collection.whereIn("id", chunk).get().get()
                    snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
                }
            }.awaitAll().flatten()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercises by ids: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getExercises(
        difficulty: String? = null,
        topic: String? = null,
        subtopic: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<Exercise> {
        return try {
            logger.info { "Getting exercises - difficulty: $difficulty, topic: $topic, limit: $limit, offset: $offset" }
            
            var query: Query = collection
            
            // Apply filters (no orderBy to avoid index issues)
            if (difficulty != null) {
                query = query.whereEqualTo("difficulty", difficulty)
            }
            if (topic != null) {
                query = query.whereEqualTo("topic", topic)
            }
            if (subtopic != null) {
                query = query.whereEqualTo("subtopic", subtopic)
            }
            
            // Fetch all matching documents
            val snapshot = query.get().get()
            logger.info { "Firestore returned ${snapshot.documents.size} documents" }
            
            val allExercises = mutableListOf<Exercise>()
            snapshot.documents.forEach { doc ->
                try {
                    val exercise = doc.toObject(Exercise::class.java)
                    if (exercise != null) {
                        allExercises.add(exercise)
                        logger.debug { "Parsed exercise ${doc.id}: ${exercise.title}" }
                    } else {
                        logger.warn { "Exercise ${doc.id} parsed to null" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse exercise ${doc.id}: ${e.message}\nStack: ${e.stackTraceToString()}" }
                }
            }
            
            logger.info { "Successfully parsed ${allExercises.size} exercises" }
            
            // Sort by createdAt in memory (descending) - handle null createdAt
            val sorted = allExercises.sortedByDescending { it.createdAt ?: 0L }
            logger.info { "Sorted exercises, first createdAt: ${sorted.firstOrNull()?.createdAt}" }
            
            // Apply offset and limit
            val result = if (offset >= sorted.size) {
                logger.info { "Offset $offset >= size ${sorted.size}, returning empty" }
                emptyList()
            } else {
                val paginated = sorted.drop(offset).take(limit)
                logger.info { "Returning ${paginated.size} exercises after pagination" }
                paginated
            }
            
            result
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercises: ${e.message}\nStack: ${e.stackTraceToString()}" }
            emptyList()
        }
    }
    
    suspend fun getExercisesByDifficulty(difficulty: String, limit: Int = 10): List<Exercise> {
        return try {
            val snapshot = collection
                .whereEqualTo("difficulty", difficulty)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercises by difficulty: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getExercisesByTopic(topic: String, limit: Int = 10): List<Exercise> {
        return try {
            val snapshot = collection
                .whereEqualTo("topic", topic)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get exercises by topic: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getRandomExercises(limit: Int = 5): List<Exercise> {
        return try {
            val snapshot = collection
                .limit(limit * 2) // Get more than needed
                .get()
                .get()
            
            val exercises = snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
            exercises.shuffled().take(limit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get random exercises: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getTotalExerciseCount(): Int {
        return try {
            val snapshot = collection.get().get()
            snapshot.documents.size
        } catch (e: Exception) {
            logger.error(e) { "Failed to count exercises: ${e.message}" }
            0
        }
    }
    
    suspend fun updateExercise(exerciseId: String, updates: Map<String, Any>): Exercise? {
        return try {
            collection.document(exerciseId).update(updates).get()
            getExerciseById(exerciseId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update exercise: ${e.message}" }
            throw e
        }
    }
    
    suspend fun deleteExercise(exerciseId: String): Boolean {
        return try {
            collection.document(exerciseId).delete().get()
            logger.info { "Exercise deleted: $exerciseId" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete exercise: ${e.message}" }
            false
        }
    }
    
    suspend fun deleteAllExercises(): Int {
        return try {
            val snapshot = collection.get().get()
            var count = 0
            snapshot.documents.forEach { doc ->
                doc.reference.delete().get()
                count++
            }
            logger.info { "Deleted all $count exercises" }
            count
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete all exercises: ${e.message}" }
            throw e
        }
    }
}

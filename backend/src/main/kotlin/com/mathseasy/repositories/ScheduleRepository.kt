package com.mathseasy.repositories

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.mathseasy.models.LearningSchedule
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.Constants
import com.mathseasy.utils.getCurrentTimestamp
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ScheduleRepository {
    private val db: Firestore = FirebaseService.getFirestore()
    private val collection = db.collection(Constants.COLLECTION_LEARNING_SCHEDULES)
    companion object {
        // Shared cache for active schedules to prevent quota exhaustion
        private var activeSchedulesCache: List<LearningSchedule> = emptyList()
        private var lastCacheUpdate: Long = 0
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes

        private fun invalidateCache() {
            activeSchedulesCache = emptyList()
            lastCacheUpdate = 0
        }
    }
    
    suspend fun createSchedule(schedule: LearningSchedule): LearningSchedule {
        return try {
            val docRef = collection.document()
            val timestamp = getCurrentTimestamp()
            val scheduleWithId = schedule.copy(
                id = docRef.id,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            docRef.set(scheduleWithId).get()
            logger.info { "Schedule created: ${scheduleWithId.id}" }
            invalidateCache()
            scheduleWithId
        } catch (e: Exception) {
            logger.error(e) { "Failed to create schedule: ${e.message}" }
            throw e
        }
    }
    
    suspend fun getSchedulesByUserId(userId: String): List<LearningSchedule> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .orderBy("dayOfWeek", Query.Direction.ASCENDING)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(LearningSchedule::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get schedules: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getActiveSchedulesByUserId(userId: String): List<LearningSchedule> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("active", true)
                .orderBy("dayOfWeek", Query.Direction.ASCENDING)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(LearningSchedule::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get active schedules: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getScheduleById(scheduleId: String): LearningSchedule? {
        return try {
            val document = collection.document(scheduleId).get().get()
            if (document.exists()) {
                document.toObject(LearningSchedule::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get schedule by ID: ${e.message}" }
            null
        }
    }
    
    suspend fun updateSchedule(scheduleId: String, updates: Map<String, Any>): LearningSchedule? {
        return try {
            val updatesWithTimestamp = updates.toMutableMap().apply {
                put("updatedAt", getCurrentTimestamp())
            }
            collection.document(scheduleId).update(updatesWithTimestamp).get()
            invalidateCache()
            getScheduleById(scheduleId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update schedule: ${e.message}" }
            throw e
        }
    }
    
    suspend fun deleteSchedule(scheduleId: String): Boolean {
        return try {
            collection.document(scheduleId).delete().get()
            logger.info { "Schedule deleted: $scheduleId" }
            invalidateCache()
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete schedule: ${e.message}" }
            false
        }
    }
    
    suspend fun deleteAllSchedulesByUserId(userId: String): Boolean {
        return try {
            val schedules = getSchedulesByUserId(userId)
            schedules.forEach { schedule ->
                schedule.id?.let { deleteSchedule(it) }
            }
            invalidateCache()
            logger.info { "All schedules deleted for user: $userId" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete all schedules: ${e.message}" }
            false
        }
    }
    
    suspend fun getScheduleByDayAndUserId(userId: String, dayOfWeek: Int): List<LearningSchedule> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("dayOfWeek", dayOfWeek)
                .whereEqualTo("active", true)
                .get()
                .get()
            
            snapshot.documents.mapNotNull { it.toObject(LearningSchedule::class.java) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get schedule by day: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun getAllActiveSchedules(): List<LearningSchedule> {
        val now = getCurrentTimestamp()
        if (activeSchedulesCache.isNotEmpty() && (now - lastCacheUpdate) < CACHE_TTL_MS) {
            return activeSchedulesCache
        }
        
        return try {
            logger.info { "Refreshing active schedules cache from Firestore..." }
            val snapshot = collection
                .whereEqualTo("active", true)
                .get()
                .get()
            
            val schedules = snapshot.documents.mapNotNull { it.toObject(LearningSchedule::class.java) }
            activeSchedulesCache = schedules
            lastCacheUpdate = getCurrentTimestamp()
            schedules
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all active schedules: ${e.message}" }
            // Return stale cache if available, otherwise empty list
            if (activeSchedulesCache.isNotEmpty()) activeSchedulesCache else emptyList()
        }
    }
}

package com.mathseasy.services

import com.mathseasy.models.User
import com.mathseasy.models.UserPreferences
import com.mathseasy.models.UserStats
import com.mathseasy.models.TopicProgress
import com.mathseasy.repositories.BadgeRepository
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.NotificationRepository
import com.mathseasy.repositories.ScheduleRepository
import com.mathseasy.services.FirebaseService
import com.mathseasy.utils.roundToDecimals
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class UserService(
    private val userRepository: UserRepository = UserRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val scheduleRepository: ScheduleRepository = ScheduleRepository(),
    private val notificationRepository: NotificationRepository = NotificationRepository(),
    private val badgeRepository: BadgeRepository = BadgeRepository(),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository()
) {
    
    suspend fun getUserById(uid: String): User? {
        return userRepository.getUserById(uid)
    }
    
    suspend fun updateUser(
        uid: String,
        displayName: String? = null,
        photoUrl: String? = null,
        preferences: UserPreferences? = null
    ): User? {
        val updates = mutableMapOf<String, Any>()
        
        displayName?.let { updates["displayName"] = it }
        photoUrl?.let { updates["photoUrl"] = it }
        preferences?.let { updates["preferences"] = it }
        
        if (updates.isEmpty()) {
            return getUserById(uid)
        }
        
        return userRepository.updateUser(uid, updates)
    }
    
    suspend fun deleteUser(uid: String): Boolean {
        return try {
            val schedulesDeleted = scheduleRepository.deleteAllSchedulesByUserId(uid)
            val notificationsDeleted = notificationRepository.deleteAllForUser(uid)
            val historiesDeleted = historyRepository.deleteAllForUser(uid)
            val userBadgesDeleted = badgeRepository.deleteAllForUser(uid)

            if (!schedulesDeleted || notificationsDeleted < 0 || historiesDeleted < 0 || userBadgesDeleted < 0) {
                logger.error {
                    "Failed to delete all user data for $uid. " +
                        "schedules=$schedulesDeleted notifications=$notificationsDeleted " +
                        "histories=$historiesDeleted userBadges=$userBadgesDeleted"
                }
                return false
            }

            val dbDeleted = userRepository.deleteUser(uid)
            if (!dbDeleted) {
                logger.error { "Failed to delete user document for $uid" }
                return false
            }

            try {
                FirebaseService.getAuth().deleteUser(uid)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete Firebase Auth user $uid: ${e.message}" }
                return false
            }

            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete user $uid: ${e.message}" }
            false
        }
    }
    
    suspend fun updateFcmToken(uid: String, fcmToken: String): User? {
        return userRepository.updateFcmToken(uid, fcmToken)
    }
    
    suspend fun updatePreferences(uid: String, preferences: UserPreferences): User? {
        return userRepository.updatePreferences(uid, preferences)
    }
    
    suspend fun getUserStats(uid: String): UserStats? {
        val user = getUserById(uid) ?: return null
        val history = historyRepository.getHistoryByUserId(uid)
        
        if (history.isEmpty()) {
            return UserStats(
                totalExercises = 0,
                correctAnswers = 0,
                accuracy = 0.0,
                totalTimeSpent = 0,
                averageTimePerExercise = 0,
                exercisesByDifficulty = emptyMap(),
                progressByTopic = emptyMap()
            )
        }
        
        val totalExercises = history.size
        val correctAnswers = history.count { it.isCorrect }
        val accuracy = (correctAnswers.toDouble() / totalExercises * 100).roundToDecimals(1)
        val totalTimeSpent = history.sumOf { it.timeSpent }
        val averageTimePerExercise = totalTimeSpent / totalExercises
        
        val exerciseIds = history.map { it.exerciseId }.filter { it.isNotBlank() }.distinct()
        val exercises = exerciseRepository.getExercisesByIds(exerciseIds)
        val exercisesById = exercises.filter { it.id != null }.associateBy { it.id!! }

        // Group by difficulty (join history with exercises)
        val exercisesByDifficulty = mutableMapOf(
            "easy" to 0,
            "medium" to 0,
            "hard" to 0
        )
        
        // Group by topic (join history with exercises)
        val topicTotals = mutableMapOf<String, Pair<Int, Int>>() // topic -> (total, correct)
        
        history.forEach { item ->
            val exercise = exercisesById[item.exerciseId] ?: return@forEach
            
            val difficulty = exercise.difficulty.lowercase()
            if (exercisesByDifficulty.containsKey(difficulty)) {
                exercisesByDifficulty[difficulty] = exercisesByDifficulty.getValue(difficulty) + 1
            }
            
            val topic = exercise.topic
            if (topic.isNotBlank()) {
                val (total, correct) = topicTotals[topic] ?: (0 to 0)
                topicTotals[topic] = (total + 1) to (correct + if (item.isCorrect) 1 else 0)
            }
        }
        
        val progressByTopic = topicTotals.mapValues { (topic, counts) ->
            val total = counts.first
            val correct = counts.second
            val accuracyByTopic = if (total == 0) 0.0 else (correct.toDouble() / total * 100).roundToDecimals(1)
            TopicProgress(
                topic = topic,
                totalExercises = total,
                correctAnswers = correct,
                accuracy = accuracyByTopic
            )
        }
        
        return UserStats(
            totalExercises = totalExercises,
            correctAnswers = correctAnswers,
            accuracy = accuracy,
            totalTimeSpent = totalTimeSpent,
            averageTimePerExercise = averageTimePerExercise,
            exercisesByDifficulty = exercisesByDifficulty,
            progressByTopic = progressByTopic
        )
    }
}



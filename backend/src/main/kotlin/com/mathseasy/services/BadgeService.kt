package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.BadgeRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.utils.roundToDecimals
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BadgeService(
    private val badgeRepository: BadgeRepository = BadgeRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(),
    private val notificationService: NotificationService = NotificationService()
) {
    
    /**
     * Get all available badges
     */
    suspend fun getAllBadges(): List<Badge> {
        return badgeRepository.getAllBadges()
    }
    
    /**
     * Get badge by ID
     */
    suspend fun getBadgeById(badgeId: String): Badge? {
        return badgeRepository.getBadgeById(badgeId)
    }
    
    /**
     * Get all badges for a user with progress
     */
    suspend fun getUserBadgesWithProgress(userId: String): List<BadgeWithProgress> {
        val allBadges = badgeRepository.getAllBadges()
        val userBadges = badgeRepository.getUserBadges(userId)
        val user = userRepository.getUserById(userId)
        val history = historyRepository.getHistoryByUserId(userId, 10000)
        
        val uniqueExerciseIds = history.map { it.exerciseId }.distinct()
        val exerciseMap = if (uniqueExerciseIds.isNotEmpty()) {
            exerciseRepository.getExercisesByIds(uniqueExerciseIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        
        return allBadges.map { badge ->
            val userBadge = userBadges.find { it.badgeId == badge.id }
            val isEarned = userBadge != null
            
            // Calculate progress
            val (progress, total) = calculateBadgeProgress(badge, user, history, exerciseMap)
            
            BadgeWithProgress(
                badge = badge,
                isEarned = isEarned,
                earnedAt = userBadge?.earnedAt,
                progress = progress,
                total = total
            )
        }
    }
    
    /**
     * Calculate progress towards a badge
     */
    private suspend fun calculateBadgeProgress(
        badge: Badge,
        user: User?,
        history: List<LearningHistory>,
        exerciseMap: Map<String?, Exercise> = emptyMap()
    ): Pair<Int, Int> {
        if (user == null) return Pair(0, badge.condition.threshold)
        
        return when (badge.condition.type) {
            "exercises" -> {
            val historyWithExercises = history.mapNotNull { h ->
                exerciseMap[h.exerciseId]?.let { ex -> h to ex }
            }
            val completedLessons = historyWithExercises.groupBy { (h, ex) ->
                val timeKey = h.completedAt / (1000 * 60)
                "${ex.topic}-${ex.difficulty}-$timeKey"
            }.size
            
            Pair(completedLessons, badge.condition.threshold)
        }
            "streak" -> {
                val current = user.currentStreak
                Pair(current, badge.condition.threshold)
            }
            "points" -> {
                val current = user.totalPoints
                Pair(current, badge.condition.threshold)
            }
            "accuracy" -> {
                val topic = badge.condition.topic
                if (topic != null) {
                    val topicHistory = getTopicHistory(history, topic, exerciseMap)
                    if (topicHistory.size >= 10) {
                        val correct = topicHistory.count { it.isCorrect }
                        val accuracy = Math.round(correct.toDouble() / topicHistory.size * 100).toInt()
                        Pair(accuracy, badge.condition.threshold)
                    } else {
                        Pair(topicHistory.size, 10) // Need at least 10 exercises
                    }
                } else {
                    Pair(0, badge.condition.threshold)
                }
            }
            else -> Pair(0, badge.condition.threshold)
        }
    }
    
    /**
     * Check and award badges to a user after completing an exercise
     */
    suspend fun checkAndAwardBadges(userId: String): List<UserBadge> {
        val user = userRepository.getUserById(userId) ?: return emptyList()
        val history = historyRepository.getHistoryByUserId(userId, 10000)
        val allBadges = badgeRepository.getAllBadges()
        
        val userBadges = badgeRepository.getUserBadges(userId)
        val earnedBadgeIds = userBadges.map { it.badgeId }.toSet()
        
        val uniqueExerciseIds = history.map { it.exerciseId }.distinct()
        val exerciseMap = if (uniqueExerciseIds.isNotEmpty()) {
            exerciseRepository.getExercisesByIds(uniqueExerciseIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        
        val newBadges = mutableListOf<UserBadge>()
        
        for (badge in allBadges) {
            val badgeId = badge.id ?: continue
            // Skip if already earned
            if (earnedBadgeIds.contains(badgeId)) {
                continue
            }
            
            // Check if user meets the condition
            if (checkBadgeCondition(badge, user, history, exerciseMap)) {
                logger.info { "User $userId earned badge ${badge.id}: ${badge.name}" }
                val userBadge = badgeRepository.awardBadge(userId, badgeId)
                if (userBadge != null) {
                    newBadges.add(userBadge)
                    
                    // Send notification for new badge
                    try {
                        notificationService.sendBadgeEarnedNotification(
                            userId = userId,
                            badgeName = badge.name,
                            badgeDescription = badge.description,
                            badgeId = badge.id
                        )
                        logger.info { "Sent badge notification to user $userId for badge ${badge.name}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to send badge notification: ${e.message}" }
                    }
                }
            }
        }
        
        return newBadges
    }
    
    /**
     * Check if user meets badge condition
     */
    private suspend fun checkBadgeCondition(
        badge: Badge,
        user: User,
        history: List<LearningHistory>,
        exerciseMap: Map<String?, Exercise> = emptyMap()
    ): Boolean {
        return when (badge.condition.type) {
            "exercises" -> {
            // Total lessons completed 
            val historyWithExercises = history.mapNotNull { h ->
                exerciseMap[h.exerciseId]?.let { ex -> h to ex }
            }
            val completedLessons = historyWithExercises.groupBy { (h, ex) ->
                val timeKey = h.completedAt / (1000 * 60)
                "${ex.topic}-${ex.difficulty}-$timeKey"
            }.size
            
            completedLessons >= badge.condition.threshold
        }
            "streak" -> {
                // Current streak
                user.currentStreak >= badge.condition.threshold
            }
            "points" -> {
                // Total points earned
                user.totalPoints >= badge.condition.threshold
            }
            "accuracy" -> {
                // Topic-specific accuracy 
                val topic = badge.condition.topic ?: return false
                val topicHistory = getTopicHistory(history, topic, exerciseMap)
                
                if (topicHistory.size < 10) return false
                
                val correct = topicHistory.count { it.isCorrect }
                val accuracy = (correct.toDouble() / topicHistory.size * 100).roundToDecimals(1)
                
                accuracy >= badge.condition.threshold
            }
            else -> false
        }
    }
    

    
    /**
     * Get history for a specific topic
     */
    private suspend fun getTopicHistory(
        history: List<LearningHistory>,
        topic: String,
        exerciseMap: Map<String?, Exercise> = emptyMap()
    ): List<LearningHistory> {
        if (history.isEmpty()) return emptyList()
        
        val mapToUse = if (exerciseMap.isNotEmpty()) {
            exerciseMap
        } else {
            // Extract unique exercise IDs and fetch in batch if not provided
            val uniqueExerciseIds = history.map { it.exerciseId }.distinct()
            val exercises = exerciseRepository.getExercisesByIds(uniqueExerciseIds)
            exercises.associateBy { it.id }
        }
        
        return history.filter { h ->
            val exercise = mapToUse[h.exerciseId]
            exercise?.topic == topic
        }
    }
    
    /**
     * Mark badge as viewed
     */
    suspend fun markBadgeAsViewed(userId: String, badgeId: String): Boolean {
        return badgeRepository.markBadgeAsViewed(userId, badgeId)
    }
    
    /**
     * Get new badges count
     */
    suspend fun getNewBadgesCount(userId: String): Int {
        return badgeRepository.getNewBadgesCount(userId)
    }
    
    /**
     * Create a new badge 
     */
    suspend fun createBadge(badge: Badge): Badge? {
        return badgeRepository.createBadge(badge)
    }
}






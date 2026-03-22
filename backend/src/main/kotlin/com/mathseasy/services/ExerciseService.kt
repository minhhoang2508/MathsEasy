package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExerciseService(
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val userRepository: UserRepository = UserRepository()
) {
    
    suspend fun getExercises(
        difficulty: String? = null,
        topic: String? = null,
        subtopic: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): ExercisesResponse {
        // Validate difficulty 
        if (difficulty != null) {
            val validation = Validator.validateDifficulty(difficulty)
            if (validation is ValidationResult.Error) {
                throw IllegalArgumentException(validation.message)
            }
        }
        
        // Validate pagination
        val paginationValidation = Validator.validatePagination(limit, offset)
        if (paginationValidation is ValidationResult.Error) {
            throw IllegalArgumentException(paginationValidation.message)
        }
        
        val exercises = exerciseRepository.getExercises(difficulty, topic, subtopic, limit, offset)
        val total = exerciseRepository.getTotalExerciseCount()
        val hasMore = (offset + exercises.size) < total
        
        return ExercisesResponse(
            exercises = exercises,
            total = total,
            hasMore = hasMore
        )
    }
    
    suspend fun getExerciseById(exerciseId: String): Exercise? {
        return exerciseRepository.getExerciseById(exerciseId)
    }
    
    suspend fun submitExercise(
        userId: String,
        exerciseId: String,
        answer: String,
        timeSpent: Int
    ): SubmitExerciseResponse {
        // Validate inputs
        val answerValidation = Validator.validateExerciseAnswer(answer)
        if (answerValidation is ValidationResult.Error) {
            throw IllegalArgumentException(answerValidation.message)
        }
        
        val timeValidation = Validator.validateTimeSpent(timeSpent)
        if (timeValidation is ValidationResult.Error) {
            throw IllegalArgumentException(timeValidation.message)
        }
        
        // Get exercise
        val exercise = exerciseRepository.getExerciseById(exerciseId)
            ?: throw NoSuchElementException("Exercise not found")
        
        // Check if answer is correct
        val isCorrect = answer.uppercase() == exercise.correctAnswer.uppercase()
        
        // Calculate points earned
        val basePoints = when (exercise.difficulty) {
            Constants.DIFFICULTY_EASY -> Constants.POINTS_EASY
            Constants.DIFFICULTY_MEDIUM -> Constants.POINTS_MEDIUM
            Constants.DIFFICULTY_HARD -> Constants.POINTS_HARD
            else -> Constants.POINTS_MEDIUM
        }
        
        // Bonus points for speed (if correct and fast)
        val bonusPoints = if (isCorrect && timeSpent < 60) {
            Constants.POINTS_BONUS_SPEED
        } else {
            0
        }
        
        val pointsEarned = if (isCorrect) basePoints + bonusPoints else 0
        
        // Save to learning history
        val history = LearningHistory(
            userId = userId,
            exerciseId = exerciseId,
            userAnswer = answer,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            pointsEarned = pointsEarned,
            completedAt = getCurrentTimestamp()
        )
        historyRepository.createHistory(history)
        
        // Update user points
        val updatedUser = userRepository.updatePoints(userId, pointsEarned)
        val newTotalPoints = updatedUser?.totalPoints ?: 0
        
        // Update streak
        val (streakUpdated, currentStreak) = updateUserStreak(userId)
        
        // Check for new badges
        val badgeService = BadgeService()
        val newBadges = badgeService.checkAndAwardBadges(userId)
        
        logger.info { "Exercise submitted - User: $userId, Exercise: $exerciseId, Correct: $isCorrect, Points: $pointsEarned" }
        
        return SubmitExerciseResponse(
            isCorrect = isCorrect,
            correctAnswer = exercise.correctAnswer,
            explanation = exercise.explanation,
            pointsEarned = pointsEarned,
            newTotalPoints = newTotalPoints,
            newBadges = newBadges,
            streakUpdated = streakUpdated,
            currentStreak = currentStreak
        )
    }
    
    private suspend fun updateUserStreak(userId: String): Pair<Boolean, Int> {
        val user = userRepository.getUserById(userId) ?: return Pair(false, 0)
        
        val today = getCurrentDate()
        val lastStreakDate = user.lastStreakDate?.toLocalDateTime()?.date
        
        return when {
            lastStreakDate == null -> {
                // First time completing exercise
                userRepository.updateStreak(userId, 1, 1)
                Pair(true, 1)
            }
            lastStreakDate == today -> {
                // Already completed today, no change
                Pair(false, user.currentStreak)
            }
            isYesterday(user.lastStreakDate!!) -> {
                // Continuing streak
                val newStreak = user.currentStreak + 1
                val longestStreak = maxOf(newStreak, user.longestStreak)
                userRepository.updateStreak(userId, newStreak, longestStreak)
                Pair(true, newStreak)
            }
            else -> {
                // Streak broken, start new
                userRepository.updateStreak(userId, 1, user.longestStreak)
                Pair(true, 1)
            }
        }
    }
    
    suspend fun getRecommendedExercises(userId: String, limit: Int = 10): List<Exercise> {
        // Get user's learning history
        val history = historyRepository.getRecentHistoryByUserId(userId, days = 30)
        
        if (history.isEmpty()) {
            // No history, return random exercises
            return exerciseRepository.getRandomExercises(limit)
        }
        
        // Calculate user's accuracy to determine difficulty
        val correctCount = history.count { it.isCorrect }
        val accuracy = correctCount.toDouble() / history.size
        
        val recommendedDifficulty = when {
            accuracy >= 0.8 -> Constants.DIFFICULTY_HARD
            accuracy >= 0.5 -> Constants.DIFFICULTY_MEDIUM
            else -> Constants.DIFFICULTY_EASY
        }
        
        // Get topics user hasn't practiced much
        val practicedTopics = history.map { it.exerciseId }.toSet()
        
        // Get exercises with recommended difficulty
        val exercises = exerciseRepository.getExercisesByDifficulty(recommendedDifficulty, limit * 2)
        
        // Filter out recently completed exercises and shuffle
        return exercises
            .filter { it.id !in practicedTopics }
            .shuffled()
            .take(limit)
    }
    
    suspend fun deleteAllExercises(): Int {
        return exerciseRepository.deleteAllExercises()
    }
    
    suspend fun createExercise(exercise: Exercise): Exercise {
        // Validate difficulty
        val validation = Validator.validateDifficulty(exercise.difficulty)
        if (validation is ValidationResult.Error) {
            throw IllegalArgumentException(validation.message)
        }
        
        // Validate answer
        val answerValidation = Validator.validateExerciseAnswer(exercise.correctAnswer)
        if (answerValidation is ValidationResult.Error) {
            throw IllegalArgumentException(answerValidation.message)
        }
        
        if (exercise.options.size != 4) {
            throw IllegalArgumentException("Exercise must have exactly 4 options")
        }
        
        return exerciseRepository.createExercise(exercise)
    }
}

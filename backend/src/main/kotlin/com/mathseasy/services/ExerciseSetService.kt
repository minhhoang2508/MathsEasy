package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.repositories.ExerciseSetRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.ReviewQueueRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExerciseSetService(
    private val setRepository: ExerciseSetRepository = ExerciseSetRepository(),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val reviewQueueRepository: ReviewQueueRepository = ReviewQueueRepository()
) {

    suspend fun getSets(
        difficulty: String? = null,
        topic: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): ExerciseSetsResponse {
        val sets = setRepository.getSets(difficulty, topic, limit, offset)
        val total = setRepository.getTotalCount()
        return ExerciseSetsResponse(
            sets = sets,
            total = total,
            hasMore = (offset + sets.size) < total
        )
    }

    suspend fun getSetDetail(setId: String): ExerciseSetDetail? {
        val set = setRepository.getSetById(setId) ?: return null
        val exercises = set.exerciseIds.mapNotNull { id ->
            exerciseRepository.getExerciseById(id)
        }
        return ExerciseSetDetail(set = set, exercises = exercises)
    }

    suspend fun submitSet(
        userId: String,
        setId: String,
        request: SubmitSetRequest
    ): SubmitSetResponse {
        val set = setRepository.getSetById(setId)
            ?: throw NoSuchElementException("Exercise set not found")

        val exercises = exerciseRepository.getExercisesByIds(set.exerciseIds)

        val answerMap = request.answers.associate { it.exerciseId to it.answer.uppercase() }
        var totalPointsEarned = 0
        val results = mutableListOf<QuestionResult>()
        val historiesToSave = mutableListOf<LearningHistory>()

        for ((idx, exercise) in exercises.withIndex()) {
            val userAnswer = answerMap[exercise.id] ?: ""
            val isCorrect = userAnswer == exercise.correctAnswer.uppercase()

            val basePoints = when (exercise.difficulty) {
                Constants.DIFFICULTY_EASY -> Constants.POINTS_EASY
                Constants.DIFFICULTY_MEDIUM -> Constants.POINTS_MEDIUM
                Constants.DIFFICULTY_HARD -> Constants.POINTS_HARD
                else -> Constants.POINTS_MEDIUM
            }
            val pointsEarned = if (isCorrect) basePoints else 0
            totalPointsEarned += pointsEarned

            // Add idx to timestamp to ensure stable sorting order for badges
            val history = LearningHistory(
                userId = userId,
                exerciseId = exercise.id ?: "",
                setId = setId,
                userAnswer = userAnswer,
                isCorrect = isCorrect,
                timeSpent = request.totalTimeSpent / exercises.size,
                pointsEarned = pointsEarned,
                completedAt = getCurrentTimestamp() + idx
            )
            historiesToSave.add(history)

            results.add(
                QuestionResult(
                    exerciseId = exercise.id ?: "",
                    userAnswer = userAnswer,
                    correctAnswer = exercise.correctAnswer,
                    isCorrect = isCorrect,
                    explanation = exercise.explanation,
                    pointsEarned = pointsEarned
                )
            )
        }

        if (historiesToSave.isNotEmpty()) {
            historyRepository.createHistoriesBatch(historiesToSave)
        }

        val updatedUser = userRepository.updatePoints(userId, totalPointsEarned)
        val newTotalPoints = updatedUser?.totalPoints ?: 0

        val (streakUpdated, currentStreak) = updateUserStreak(userId)

        val badgeService = BadgeService()
        val newBadges = badgeService.checkAndAwardBadges(userId)

        val correctCount = results.count { it.isCorrect }
        val scorePercent = if (exercises.isNotEmpty()) correctCount.toDouble() / exercises.size * 100 else 0.0

        try {
            reviewQueueRepository.upsertReviewItem(
                userId = userId,
                setId = setId,
                topic = set.topic,
                score = scorePercent,
                completedAt = getCurrentTimestamp()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update review queue for set $setId: ${e.message}" }
        }

        logger.info { "Set submitted - User: $userId, Set: $setId, Score: $correctCount/${exercises.size}" }

        return SubmitSetResponse(
            correctCount = correctCount,
            totalCount = exercises.size,
            score = if (exercises.isNotEmpty()) correctCount.toDouble() / exercises.size * 100 else 0.0,
            pointsEarned = totalPointsEarned,
            newTotalPoints = newTotalPoints,
            results = results,
            streakUpdated = streakUpdated,
            currentStreak = currentStreak,
            newBadges = newBadges
        )
    }

    private suspend fun updateUserStreak(userId: String): Pair<Boolean, Int> {
        val user = userRepository.getUserById(userId) ?: return Pair(false, 0)
        val today = getCurrentDate()
        val lastStreakDate = user.lastStreakDate?.toLocalDateTime()?.date

        return when {
            lastStreakDate == null -> {
                userRepository.updateStreak(userId, 1, 1)
                Pair(true, 1)
            }
            lastStreakDate == today -> Pair(false, user.currentStreak)
            isYesterday(user.lastStreakDate!!) -> {
                val newStreak = user.currentStreak + 1
                val longestStreak = maxOf(newStreak, user.longestStreak)
                userRepository.updateStreak(userId, newStreak, longestStreak)
                Pair(true, newStreak)
            }
            else -> {
                val lastDate = user.lastStreakDate!!.toLocalDateTime().date
                val twoDaysAgoDate = daysAgo(2)
                val missedOneDay = lastDate == twoDaysAgoDate
                val updates = mutableMapOf<String, Any>(
                    "currentStreak" to 1,
                    "longestStreak" to user.longestStreak,
                    "lastStreakDate" to getCurrentTimestamp(),
                    "previousStreak" to if (missedOneDay) user.currentStreak else 0,
                    "streakBroken" to missedOneDay
                )
                userRepository.updateUser(userId, updates)
                Pair(true, 1)
            }
        }
    }

    suspend fun createSet(set: ExerciseSet): ExerciseSet {
        return setRepository.createSet(set)
    }
}
package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.repositories.ExerciseSetRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.ReviewQueueRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ExerciseSetService(
    private val setRepository: ExerciseSetRepository = ExerciseSetRepository(),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val reviewQueueRepository: ReviewQueueRepository = ReviewQueueRepository()
) {

    suspend fun getSets(
        difficulty: String? = null,
        topic: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): ExerciseSetsResponse {
        val sets = setRepository.getSets(difficulty, topic, limit, offset)
        val total = setRepository.getTotalCount()
        return ExerciseSetsResponse(
            sets = sets,
            total = total,
            hasMore = (offset + sets.size) < total
        )
    }

    suspend fun getSetDetail(setId: String): ExerciseSetDetail? {
        val set = setRepository.getSetById(setId) ?: return null
        val exercises = set.exerciseIds.mapNotNull { id ->
            exerciseRepository.getExerciseById(id)
        }
        return ExerciseSetDetail(set = set, exercises = exercises)
    }

    suspend fun submitSet(
        userId: String,
        setId: String,
        request: SubmitSetRequest
    ): SubmitSetResponse {
        val set = setRepository.getSetById(setId)
            ?: throw NoSuchElementException("Exercise set not found")

        val exercises = exerciseRepository.getExercisesByIds(set.exerciseIds)

        val answerMap = request.answers.associate { it.exerciseId to it.answer.uppercase() }
        var totalPointsEarned = 0
        val results = mutableListOf<QuestionResult>()
        val historiesToSave = mutableListOf<LearningHistory>()

        for ((idx, exercise) in exercises.withIndex()) {
            val userAnswer = answerMap[exercise.id] ?: ""
            val isCorrect = userAnswer == exercise.correctAnswer.uppercase()

            val basePoints = when (exercise.difficulty) {
                Constants.DIFFICULTY_EASY -> Constants.POINTS_EASY
                Constants.DIFFICULTY_MEDIUM -> Constants.POINTS_MEDIUM
                Constants.DIFFICULTY_HARD -> Constants.POINTS_HARD
                else -> Constants.POINTS_MEDIUM
            }
            val pointsEarned = if (isCorrect) basePoints else 0
            totalPointsEarned += pointsEarned

            // Add idx to timestamp to ensure stable sorting order for badges
            val history = LearningHistory(
                userId = userId,
                exerciseId = exercise.id ?: "",
                setId = setId,
                userAnswer = userAnswer,
                isCorrect = isCorrect,
                timeSpent = request.totalTimeSpent / exercises.size,
                pointsEarned = pointsEarned,
                completedAt = getCurrentTimestamp() + idx
            )
            historiesToSave.add(history)

            results.add(
                QuestionResult(
                    exerciseId = exercise.id ?: "",
                    userAnswer = userAnswer,
                    correctAnswer = exercise.correctAnswer,
                    isCorrect = isCorrect,
                    explanation = exercise.explanation,
                    pointsEarned = pointsEarned
                )
            )
        }

        if (historiesToSave.isNotEmpty()) {
            historyRepository.createHistoriesBatch(historiesToSave)
        }

        val updatedUser = userRepository.updatePoints(userId, totalPointsEarned)
        val newTotalPoints = updatedUser?.totalPoints ?: 0

        val (streakUpdated, currentStreak) = updateUserStreak(userId)

        val badgeService = BadgeService()
        val newBadges = badgeService.checkAndAwardBadges(userId)

        val correctCount = results.count { it.isCorrect }
        val scorePercent = if (exercises.isNotEmpty()) correctCount.toDouble() / exercises.size * 100 else 0.0

        try {
            reviewQueueRepository.upsertReviewItem(
                userId = userId,
                setId = setId,
                topic = set.topic,
                score = scorePercent,
                completedAt = getCurrentTimestamp()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update review queue for set $setId: ${e.message}" }
        }

        logger.info { "Set submitted - User: $userId, Set: $setId, Score: $correctCount/${exercises.size}" }

        return SubmitSetResponse(
            correctCount = correctCount,
            totalCount = exercises.size,
            score = if (exercises.isNotEmpty()) correctCount.toDouble() / exercises.size * 100 else 0.0,
            pointsEarned = totalPointsEarned,
            newTotalPoints = newTotalPoints,
            results = results,
            streakUpdated = streakUpdated,
            currentStreak = currentStreak,
            newBadges = newBadges
        )
    }

    private suspend fun updateUserStreak(userId: String): Pair<Boolean, Int> {
        val user = userRepository.getUserById(userId) ?: return Pair(false, 0)
        val today = getCurrentDate()
        val lastStreakDate = user.lastStreakDate?.toLocalDateTime()?.date

        return when {
            lastStreakDate == null -> {
                userRepository.updateStreak(userId, 1, 1)
                Pair(true, 1)
            }
            lastStreakDate == today -> Pair(false, user.currentStreak)
            isYesterday(user.lastStreakDate!!) -> {
                val newStreak = user.currentStreak + 1
                val longestStreak = maxOf(newStreak, user.longestStreak)
                userRepository.updateStreak(userId, newStreak, longestStreak)
                Pair(true, newStreak)
            }
            else -> {
                val lastDate = user.lastStreakDate!!.toLocalDateTime().date
                val twoDaysAgoDate = daysAgo(2)
                val missedOneDay = lastDate == twoDaysAgoDate
                val updates = mutableMapOf<String, Any>(
                    "currentStreak" to 1,
                    "longestStreak" to user.longestStreak,
                    "lastStreakDate" to getCurrentTimestamp(),
                    "previousStreak" to if (missedOneDay) user.currentStreak else 0,
                    "streakBroken" to missedOneDay
                )
                userRepository.updateUser(userId, updates)
                Pair(true, 1)
            }
        }
    }

    suspend fun createSet(set: ExerciseSet): ExerciseSet {
        return setRepository.createSet(set)
    }
}

package com.mathseasy.services

import com.mathseasy.models.*
import com.mathseasy.repositories.ExerciseSetRepository
import com.mathseasy.repositories.HistoryRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class StudyPlanService(
    private val exerciseSetRepository: ExerciseSetRepository = ExerciseSetRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(),
    private val userRepository: UserRepository = UserRepository()
) {
    companion object {
        val TOPIC_ORDER = listOf(
            Constants.TOPIC_FOUNDATIONS,
            Constants.TOPIC_LINEAR_ALGEBRA,
            Constants.TOPIC_QUADRATIC,
            Constants.TOPIC_FUNCTIONS,
            Constants.TOPIC_EXPONENTS_LOGARITHMS,
            Constants.TOPIC_SEQUENCES
        )

        val TOPIC_LABELS = mapOf(
            Constants.TOPIC_FOUNDATIONS to "Foundations",
            Constants.TOPIC_LINEAR_ALGEBRA to "Linear Algebra",
            Constants.TOPIC_QUADRATIC to "Quadratic & Polynomial",
            Constants.TOPIC_FUNCTIONS to "Functions",
            Constants.TOPIC_EXPONENTS_LOGARITHMS to "Exponents & Logarithms",
            Constants.TOPIC_SEQUENCES to "Sequences"
        )

        val PREMIUM_TOPICS = setOf(
            Constants.TOPIC_EXPONENTS_LOGARITHMS,
            Constants.TOPIC_SEQUENCES
        )
    }

    /**
     * Generate a lesson-based study plan for the user.
     * Uses latest attempt score per setId to recommend next steps.
     */
    suspend fun generateLessonPlan(userId: String): LessonStudyPlanResponse {
        val user = userRepository.getUserById(userId)
        val userPremiumTopics = user?.premiumTopics ?: emptyList()

        val history = historyRepository.getHistoryByUserId(userId, 10000)
            .filter { it.setId.isNotBlank() }

        if (history.isEmpty()) {
            return buildFallbackPlan(userPremiumTopics)
        }

        val latestAttempts = computeLatestAttempts(history)

        val mostRecentAttempt = latestAttempts.maxByOrNull { it.value.completedAt }
            ?: return buildFallbackPlan(userPremiumTopics)

        val latestSetId = mostRecentAttempt.key
        val latestScore = mostRecentAttempt.value.score

        val exerciseSet = exerciseSetRepository.getSetById(latestSetId)
            ?: return buildFallbackPlan(userPremiumTopics)

        val topic = exerciseSet.topic.lowercase()
        val difficulty = exerciseSet.difficulty.lowercase()
        val lessonTitle = cleanLessonTitle(exerciseSet.title)

        val lessonOrder = buildLessonOrder(topic)

        val actions = buildActions(
            topic = topic,
            difficulty = difficulty,
            score = latestScore,
            lessonTitle = lessonTitle,
            lessonOrder = lessonOrder,
            userPremiumTopics = userPremiumTopics
        )

        val reason = buildReason(difficulty, latestScore)

        val isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics

        val recommendation = LessonStudyPlan(
            topic = topic,
            topicLabel = TOPIC_LABELS[topic] ?: topic,
            lessonTitle = lessonTitle,
            difficulty = difficulty,
            score = latestScore,
            reason = reason,
            actions = actions
        )

        logger.info { "Lesson plan generated for user=$userId: topic=$topic, lesson=$lessonTitle, difficulty=$difficulty, score=$latestScore" }

        return LessonStudyPlanResponse(recommendation = recommendation)
    }

    private data class AttemptInfo(
        val score: Double,
        val completedAt: Long,
        val correct: Int,
        val total: Int
    )

    private fun computeLatestAttempts(history: List<LearningHistory>): Map<String, AttemptInfo> {
        // Group by (setId, minuteBucket)
        val attemptGroups = history.groupBy { h ->
            val minuteBucket = h.completedAt / (1000 * 60)
            "${h.setId}__$minuteBucket"
        }

        // Compute score per attempt group
        val attempts = attemptGroups.map { (key, items) ->
            val setId = key.substringBefore("__")
            val correct = items.count { it.isCorrect }
            val total = items.size
            val score = if (total > 0) correct.toDouble() / total else 0.0
            val completedAt = items.maxOf { it.completedAt }
            Triple(setId, AttemptInfo(score, completedAt, correct, total), completedAt)
        }

        // Keep latest attempt per setId
        return attempts
            .groupBy { it.first }
            .mapValues { (_, group) ->
                group.maxByOrNull { it.third }!!.second
            }
    }

    private suspend fun buildLessonOrder(topic: String): List<LessonInfo> {
        val sets = exerciseSetRepository.getSets(topic = topic, limit = 100)
        if (sets.isEmpty()) return emptyList()

        val grouped = sets.groupBy { cleanLessonTitle(it.title) }

        val orderedTitles = grouped.keys.toList().reversed()

        return orderedTitles.map { title ->
            val lessonSets = grouped[title] ?: emptyList()
            LessonInfo(
                title = title,
                easySets = lessonSets.filter { it.difficulty.lowercase() == "easy" },
                mediumSets = lessonSets.filter { it.difficulty.lowercase() == "medium" },
                hardSets = lessonSets.filter { it.difficulty.lowercase() == "hard" }
            )
        }
    }

    private data class LessonInfo(
        val title: String,
        val easySets: List<ExerciseSet>,
        val mediumSets: List<ExerciseSet>,
        val hardSets: List<ExerciseSet>
    ) {
        fun getSetByDifficulty(difficulty: String): ExerciseSet? {
            return when (difficulty.lowercase()) {
                "easy" -> easySets.firstOrNull()
                "medium" -> mediumSets.firstOrNull()
                "hard" -> hardSets.firstOrNull()
                else -> null
            }
        }
    }

    private fun cleanLessonTitle(title: String): String {
        return title
            .replace(Regex("\\s*-\\s*(easy|medium|hard)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun buildActions(
        topic: String,
        difficulty: String,
        score: Double,
        lessonTitle: String,
        lessonOrder: List<LessonInfo>,
        userPremiumTopics: List<String>
    ): List<LessonAction> {
        val actions = mutableListOf<LessonAction>()
        val topicLabel = TOPIC_LABELS[topic] ?: topic

        when (difficulty) {
            "easy" -> {
                when {
                    score >= 0.8 -> {
                        // Recommend moving to Medium
                        val mediumSet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("medium")
                        actions.add(LessonAction(
                            action = "upgrade",
                            setId = mediumSet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "medium",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Great job! Move on to Medium difficulty."
                        ))
                    }
                    score >= 0.6 -> {
                        // Recommend retrying Easy
                        val easySet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("easy")
                        actions.add(LessonAction(
                            action = "retry",
                            setId = easySet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "easy",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Almost there! Retry Easy to strengthen your understanding."
                        ))
                    }
                    else -> {
                        // Recommend reviewing Theory
                        actions.add(LessonAction(
                            action = "theory",
                            topic = topic,
                            message = "Review the theory for $topicLabel before trying again."
                        ))
                    }
                }
            }
            "medium" -> {
                when {
                    score >= 0.8 -> {
                        // Recommend moving to Hard
                        val hardSet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("hard")
                        actions.add(LessonAction(
                            action = "upgrade",
                            setId = hardSet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "hard",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Excellent! Challenge yourself with Hard difficulty."
                        ))
                    }
                    score >= 0.6 -> {
                        // Recommend retrying Medium
                        val mediumSet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("medium")
                        actions.add(LessonAction(
                            action = "retry",
                            setId = mediumSet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "medium",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Good effort! Retry Medium to improve your score."
                        ))
                    }
                    else -> {
                        // Recommend Theory + redo Easy
                        actions.add(LessonAction(
                            action = "theory",
                            topic = topic,
                            message = "Review the theory for $topicLabel."
                        ))
                        val easySet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("easy")
                        actions.add(LessonAction(
                            action = "retry",
                            setId = easySet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "easy",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Go back to Easy and build a solid foundation."
                        ))
                    }
                }
            }
            "hard" -> {
                when {
                    score >= 0.8 -> {
                        // Congratulate + recommend next lesson or new topic
                        val currentIndex = lessonOrder.indexOfFirst { it.title == lessonTitle }
                        val nextLesson = if (currentIndex >= 0 && currentIndex < lessonOrder.size - 1) {
                            lessonOrder[currentIndex + 1]
                        } else null

                        if (nextLesson != null) {
                            // Next lesson in same topic
                            val nextEasySet = nextLesson.getSetByDifficulty("easy")
                            actions.add(LessonAction(
                                action = "next_lesson",
                                setId = nextEasySet?.id,
                                lessonTitle = nextLesson.title,
                                topic = topic,
                                difficulty = "easy",
                                isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                                message = "🎉 Amazing! Move on to the next lesson: ${nextLesson.title}."
                            ))
                        } else {
                            // Last lesson in topic → recommend a new topic
                            val currentTopicIndex = TOPIC_ORDER.indexOf(topic)
                            val nextTopic = if (currentTopicIndex >= 0 && currentTopicIndex < TOPIC_ORDER.size - 1) {
                                TOPIC_ORDER[currentTopicIndex + 1]
                            } else null

                            if (nextTopic != null) {
                                val nextTopicLabel = TOPIC_LABELS[nextTopic] ?: nextTopic
                                val isPremiumNext = nextTopic in PREMIUM_TOPICS && nextTopic !in userPremiumTopics
                                actions.add(LessonAction(
                                    action = "new_topic",
                                    topic = nextTopic,
                                    isPremium = isPremiumNext,
                                    message = "🏆 You've mastered $topicLabel! Start $nextTopicLabel next."
                                ))
                            } else {
                                actions.add(LessonAction(
                                    action = "completed",
                                    message = "🏆 Congratulations! You've completed all topics! Keep practicing to stay sharp."
                                ))
                            }
                        }
                    }
                    score >= 0.6 -> {
                        // Retry Hard
                        val hardSet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("hard")
                        actions.add(LessonAction(
                            action = "retry",
                            setId = hardSet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "hard",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "So close! Retry Hard to master this lesson."
                        ))
                    }
                    else -> {
                        // Recommend Theory + redo Medium
                        actions.add(LessonAction(
                            action = "theory",
                            topic = topic,
                            message = "Review the theory for $topicLabel."
                        ))
                        val mediumSet = lessonOrder.find { it.title == lessonTitle }?.getSetByDifficulty("medium")
                        actions.add(LessonAction(
                            action = "retry",
                            setId = mediumSet?.id,
                            lessonTitle = lessonTitle,
                            topic = topic,
                            difficulty = "medium",
                            isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                            message = "Go back to Medium and solidify your skills."
                        ))
                    }
                }
            }
        }

        return actions
    }

    private fun buildReason(difficulty: String, score: Double): String {
        val scorePercent = (score * 100).toInt()
        val diffLabel = difficulty.replaceFirstChar { it.uppercase() }
        return when {
            score >= 0.8 -> "You scored $scorePercent% on $diffLabel — well done!"
            score >= 0.6 -> "You scored $scorePercent% on $diffLabel — keep practicing."
            else -> "You scored $scorePercent% on $diffLabel — review the basics."
        }
    }

    /**
     * Fallback plan for users with no history.
     * Recommends the first lesson on Easy in the first topic.
     */
    private suspend fun buildFallbackPlan(userPremiumTopics: List<String>): LessonStudyPlanResponse {
        for (topic in TOPIC_ORDER) {
            val lessonOrder = buildLessonOrder(topic)
            if (lessonOrder.isEmpty()) continue

            val firstLesson = lessonOrder.first()
            val easySet = firstLesson.getSetByDifficulty("easy")

            val actions = mutableListOf<LessonAction>()
            if (easySet != null) {
                actions.add(LessonAction(
                    action = "start",
                    setId = easySet.id,
                    lessonTitle = firstLesson.title,
                    topic = topic,
                    difficulty = "easy",
                    isPremium = topic in PREMIUM_TOPICS && topic !in userPremiumTopics,
                    message = "Start your math journey here!"
                ))
            }

            return LessonStudyPlanResponse(
                recommendation = LessonStudyPlan(
                    topic = topic,
                    topicLabel = TOPIC_LABELS[topic] ?: topic,
                    lessonTitle = firstLesson.title,
                    difficulty = "easy",
                    score = 0.0,
                    reason = "Welcome! Let's start with the basics.",
                    actions = actions
                )
            )
        }

        return LessonStudyPlanResponse(
            recommendation = LessonStudyPlan(
                topic = TOPIC_ORDER.first(),
                topicLabel = TOPIC_LABELS[TOPIC_ORDER.first()] ?: TOPIC_ORDER.first(),
                lessonTitle = "Getting Started",
                difficulty = "easy",
                score = 0.0,
                reason = "Welcome to MathsEasy! Start practicing to get personalized recommendations.",
                actions = emptyList()
            )
        )
    }
}

package com.mathseasy.services

import com.google.cloud.firestore.Firestore
import com.mathseasy.models.*
import com.mathseasy.repositories.ExerciseRepository
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ShopService(
    private val userRepository: UserRepository = UserRepository(),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(),
    private val db: Firestore = FirebaseService.getFirestore()
) {

    private val shopItems = listOf(
        ShopItemInfo(
            itemType = Constants.SHOP_ITEM_BATTERY,
            name = "Battery",
            description = "Restore your streak if you missed one day. Get back on track and keep your momentum going!",
            price = Constants.SHOP_PRICE_BATTERY,
            image = "/illustrations/shop/battery.png"
        ),
        ShopItemInfo(
            itemType = Constants.SHOP_ITEM_REDEMPTION,
            name = "Redemption",
            description = "Get a second chance! Redo one of the questions you got wrong and earn back those points.",
            price = Constants.SHOP_PRICE_REDEMPTION,
            image = "/illustrations/shop/redemption.png"
        ),
        ShopItemInfo(
            itemType = Constants.SHOP_ITEM_RESURRECTION,
            name = "Resurrection",
            description = "Full revival! Redo all the questions you got wrong and maximize your score.",
            price = Constants.SHOP_PRICE_RESURRECTION,
            image = "/illustrations/shop/resurrection.png"
        ),
        ShopItemInfo(
            itemType = Constants.SHOP_ITEM_RISKY_RISKY,
            name = "Risky Risky",
            description = "Feeling lucky? Flip a card to multiply your points — but beware, you might lose some too!",
            price = Constants.SHOP_PRICE_RISKY_RISKY,
            image = "/illustrations/shop/risky_risky.png"
        )
    )

    fun getShopItems(): List<ShopItemInfo> = shopItems

    suspend fun getShopPage(userId: String): ShopPageResponse {
        val user = userRepository.getUserById(userId)
            ?: throw NoSuchElementException("User not found")

        val inventory = UserInventory(
            userId = userId,
            battery = user.inventory.getOrDefault(Constants.SHOP_ITEM_BATTERY, 0),
            redemption = user.inventory.getOrDefault(Constants.SHOP_ITEM_REDEMPTION, 0),
            resurrection = user.inventory.getOrDefault(Constants.SHOP_ITEM_RESURRECTION, 0),
            riskyRisky = user.inventory.getOrDefault(Constants.SHOP_ITEM_RISKY_RISKY, 0)
        )

        return ShopPageResponse(
            items = shopItems,
            inventory = inventory,
            userPoints = user.totalPoints
        )
    }

    suspend fun getInventory(userId: String): UserInventory {
        val user = userRepository.getUserById(userId)
            ?: throw NoSuchElementException("User not found")

        return UserInventory(
            userId = userId,
            battery = user.inventory.getOrDefault(Constants.SHOP_ITEM_BATTERY, 0),
            redemption = user.inventory.getOrDefault(Constants.SHOP_ITEM_REDEMPTION, 0),
            resurrection = user.inventory.getOrDefault(Constants.SHOP_ITEM_RESURRECTION, 0),
            riskyRisky = user.inventory.getOrDefault(Constants.SHOP_ITEM_RISKY_RISKY, 0)
        )
    }

    suspend fun buyItem(userId: String, itemType: String, quantity: Int = 1): BuyItemResponse {
        val price = when (itemType) {
            Constants.SHOP_ITEM_BATTERY -> Constants.SHOP_PRICE_BATTERY
            Constants.SHOP_ITEM_REDEMPTION -> Constants.SHOP_PRICE_REDEMPTION
            Constants.SHOP_ITEM_RESURRECTION -> Constants.SHOP_PRICE_RESURRECTION
            Constants.SHOP_ITEM_RISKY_RISKY -> Constants.SHOP_PRICE_RISKY_RISKY
            else -> throw IllegalArgumentException("Invalid item type: $itemType")
        }

        require(quantity > 0) { "Quantity must be greater than 0" }
        val totalPrice = price * quantity

        val docRef = db.collection(Constants.COLLECTION_USERS).document(userId)
        val result = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef).get()
            if (!snapshot.exists()) throw NoSuchElementException("User not found")

            val currentPoints = snapshot.getLong("totalPoints")?.toInt() ?: 0
            if (currentPoints < totalPrice) throw IllegalStateException("Not enough points")

            @Suppress("UNCHECKED_CAST")
            val inventory = (snapshot.get("inventory") as? Map<String, Long>)
                ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
            val currentQty = inventory.getOrDefault(itemType, 0)
            inventory[itemType] = currentQty + quantity
            val newTotalPoints = currentPoints - totalPrice

            transaction.update(docRef, mapOf(
                "totalPoints" to newTotalPoints,
                "inventory" to inventory
            ))
            Triple(newTotalPoints, currentQty + quantity, totalPrice)
        }.get()

        logger.info { "User $userId bought $quantity $itemType for ${result.third} points" }

        return BuyItemResponse(
            success = true,
            itemType = itemType,
            newQuantity = result.second,
            pointsSpent = result.third,
            remainingPoints = result.first
        )
    }

    suspend fun useBattery(userId: String): UseBatteryResponse {
        val docRef = db.collection(Constants.COLLECTION_USERS).document(userId)
        val result = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef).get()
            if (!snapshot.exists()) throw NoSuchElementException("User not found")

            @Suppress("UNCHECKED_CAST")
            val inventory = (snapshot.get("inventory") as? Map<String, Long>)
                ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
            val batteryCount = inventory.getOrDefault(Constants.SHOP_ITEM_BATTERY, 0)
            if (batteryCount <= 0) throw IllegalStateException("No batteries available")

            val streakBroken = snapshot.getBoolean("streakBroken") ?: false
            val previousStreak = snapshot.getLong("previousStreak")?.toInt() ?: 0
            if (!streakBroken || previousStreak <= 0) throw IllegalStateException("Cannot be used now")

            val lastStreakDateMs = snapshot.getLong("lastStreakDate")
            if (lastStreakDateMs != null) {
                val streakDate = lastStreakDateMs.toLocalDateTime().date
                if (streakDate < daysAgo(2)) throw IllegalStateException("Cannot be used now")
            }

            val longestStreak = snapshot.getLong("longestStreak")?.toInt() ?: 0
            inventory[Constants.SHOP_ITEM_BATTERY] = batteryCount - 1

            transaction.update(docRef, mapOf(
                "currentStreak" to previousStreak,
                "longestStreak" to maxOf(previousStreak, longestStreak),
                "lastStreakDate" to getYesterdayTimestamp(),
                "streakBroken" to false,
                "previousStreak" to 0,
                "inventory" to inventory
            ))
            Pair(previousStreak, batteryCount - 1)
        }.get()

        logger.info { "User $userId used battery, restored streak to ${result.first}" }

        return UseBatteryResponse(
            success = true,
            message = "Streak restored to ${result.first} days!",
            restoredStreak = result.first,
            newQuantity = result.second
        )
    }

    suspend fun useRedemption(
        userId: String,
        setId: String,
        exerciseId: String,
        answer: String
    ): UseRedemptionResponse {
        val exercise = exerciseRepository.getExerciseById(exerciseId)
            ?: throw NoSuchElementException("Exercise not found")

        val isCorrect = answer.uppercase() == exercise.correctAnswer.uppercase()
        val basePoints = when (exercise.difficulty) {
            Constants.DIFFICULTY_EASY -> Constants.POINTS_EASY
            Constants.DIFFICULTY_MEDIUM -> Constants.POINTS_MEDIUM
            Constants.DIFFICULTY_HARD -> Constants.POINTS_HARD
            else -> Constants.POINTS_MEDIUM
        }
        val pointsEarned = if (isCorrect) basePoints else 0

        val docRef = db.collection(Constants.COLLECTION_USERS).document(userId)
        val result = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef).get()
            if (!snapshot.exists()) throw NoSuchElementException("User not found")

            @Suppress("UNCHECKED_CAST")
            val inventory = (snapshot.get("inventory") as? Map<String, Long>)
                ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
            val redemptionCount = inventory.getOrDefault(Constants.SHOP_ITEM_REDEMPTION, 0)
            if (redemptionCount <= 0) throw IllegalStateException("No redemptions available")

            val currentPoints = snapshot.getLong("totalPoints")?.toInt() ?: 0
            inventory[Constants.SHOP_ITEM_REDEMPTION] = redemptionCount - 1
            val newTotalPoints = currentPoints + pointsEarned

            transaction.update(docRef, mapOf(
                "totalPoints" to newTotalPoints,
                "inventory" to inventory
            ))
            Pair(newTotalPoints, redemptionCount - 1)
        }.get()

        logger.info { "User $userId used redemption on exercise $exerciseId, correct: $isCorrect, points: $pointsEarned" }

        return UseRedemptionResponse(
            success = true,
            exerciseId = exerciseId,
            isCorrect = isCorrect,
            correctAnswer = exercise.correctAnswer,
            pointsEarned = pointsEarned,
            newTotalPoints = result.first,
            newQuantity = result.second
        )
    }

    suspend fun useResurrection(
        userId: String,
        setId: String,
        answers: List<SetAnswer>
    ): UseResurrectionResponse {
        var totalPointsEarned = 0
        val results = mutableListOf<QuestionResult>()

        for (ans in answers) {
            val exercise = exerciseRepository.getExerciseById(ans.exerciseId) ?: continue
            val isCorrect = ans.answer.uppercase() == exercise.correctAnswer.uppercase()
            val basePoints = when (exercise.difficulty) {
                Constants.DIFFICULTY_EASY -> Constants.POINTS_EASY
                Constants.DIFFICULTY_MEDIUM -> Constants.POINTS_MEDIUM
                Constants.DIFFICULTY_HARD -> Constants.POINTS_HARD
                else -> Constants.POINTS_MEDIUM
            }
            val pointsEarned = if (isCorrect) basePoints else 0
            totalPointsEarned += pointsEarned
            results.add(QuestionResult(
                exerciseId = ans.exerciseId,
                userAnswer = ans.answer.uppercase(),
                correctAnswer = exercise.correctAnswer,
                isCorrect = isCorrect,
                explanation = exercise.explanation,
                pointsEarned = pointsEarned
            ))
        }

        val docRef = db.collection(Constants.COLLECTION_USERS).document(userId)
        val result = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef).get()
            if (!snapshot.exists()) throw NoSuchElementException("User not found")

            @Suppress("UNCHECKED_CAST")
            val inventory = (snapshot.get("inventory") as? Map<String, Long>)
                ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
            val resurrectionCount = inventory.getOrDefault(Constants.SHOP_ITEM_RESURRECTION, 0)
            if (resurrectionCount <= 0) throw IllegalStateException("No resurrections available")

            val currentPoints = snapshot.getLong("totalPoints")?.toInt() ?: 0
            inventory[Constants.SHOP_ITEM_RESURRECTION] = resurrectionCount - 1
            val newTotalPoints = currentPoints + totalPointsEarned

            transaction.update(docRef, mapOf(
                "totalPoints" to newTotalPoints,
                "inventory" to inventory
            ))
            Pair(newTotalPoints, resurrectionCount - 1)
        }.get()

        logger.info { "User $userId used resurrection on set $setId, earned $totalPointsEarned points" }

        return UseResurrectionResponse(
            success = true,
            results = results,
            totalPointsEarned = totalPointsEarned,
            newTotalPoints = result.first,
            newQuantity = result.second
        )
    }

    suspend fun useRiskyRisky(
        userId: String,
        originalPoints: Int
    ): UseRiskyRiskyResponse {
        val maxAllowedPoints = Constants.POINTS_HARD * 10
        val safeOriginalPoints = originalPoints.coerceIn(0, maxAllowedPoints)

        val shuffledIndices = Constants.RISKY_MULTIPLIERS.indices.shuffled()
        val selectedIndex = shuffledIndices[0]
        val multiplier = Constants.RISKY_MULTIPLIERS[selectedIndex]
        val label = Constants.RISKY_LABELS[selectedIndex]
        val newPoints = (safeOriginalPoints * multiplier).toInt()
        val pointsDiff = newPoints - safeOriginalPoints

        val docRef = db.collection(Constants.COLLECTION_USERS).document(userId)
        val result = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef).get()
            if (!snapshot.exists()) throw NoSuchElementException("User not found")

            @Suppress("UNCHECKED_CAST")
            val inventory = (snapshot.get("inventory") as? Map<String, Long>)
                ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
            val riskyCount = inventory.getOrDefault(Constants.SHOP_ITEM_RISKY_RISKY, 0)
            if (riskyCount <= 0) throw IllegalStateException("No Risky Risky items available")

            val currentPoints = snapshot.getLong("totalPoints")?.toInt() ?: 0
            inventory[Constants.SHOP_ITEM_RISKY_RISKY] = riskyCount - 1
            val newTotalPoints = maxOf(0, currentPoints + pointsDiff)

            transaction.update(docRef, mapOf(
                "totalPoints" to newTotalPoints,
                "inventory" to inventory
            ))
            Pair(newTotalPoints, riskyCount - 1)
        }.get()

        val allCards = shuffledIndices.map { Constants.RISKY_LABELS[it] }

        logger.info { "User $userId used Risky Risky: $label on $safeOriginalPoints points -> $newPoints" }

        return UseRiskyRiskyResponse(
            success = true,
            multiplierLabel = label,
            multiplier = multiplier,
            originalPoints = safeOriginalPoints,
            newPoints = newPoints,
            pointsDifference = pointsDiff,
            newTotalPoints = result.first,
            newQuantity = result.second,
            cardIndex = 0,
            allCards = allCards
        )
    }
}

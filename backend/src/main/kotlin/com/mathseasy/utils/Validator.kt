package com.mathseasy.utils

import com.mathseasy.models.AvatarConfig

/**
 * Input validation utilities
 */
object Validator {

    private val ALLOWED_TOP = setOf(
        "noHair", "hat", "hijab", "turban", "winterHat1", "winterHat02", "winterHat03", "winterHat04",
        "bob", "bun", "curly", "curvy", "dreads", "frida", "fro", "froBand", "longButNotTooLong",
        "miaWallace", "shavedSides", "straight02", "straight01", "straightAndStrand", "dreads01",
        "dreads02", "frizzle", "shaggy", "shaggyMullet", "shortCurly", "shortFlat", "shortRound",
        "shortWaved", "sides", "theCaesar", "theCaesarAndSidePart", "bigHair"
    )
    private val ALLOWED_HAIR_COLOR = setOf(
        "a55728", "2c1b18", "b58143", "d6b370", "724133", "4a312c", "f59797", "ecdcbf", "c93305", "e8e1e1"
    )
    private val ALLOWED_HAT_COLOR = setOf(
        "262e33", "65c9ff", "5199e4", "25557c", "e6e6e6", "929598", "3c4f5c", "b1e2ff", "a7ffc4", "ffdeb5",
        "ffafb9", "ffffb1", "ff488e", "ff5c5c", "ffffff"
    )
    private val ALLOWED_SKIN_COLOR = setOf(
        "614335", "d08b5b", "ae5d29", "edb98a", "ffdbb4", "fd9841", "f8d25c"
    )
    private val ALLOWED_EYES = setOf(
        "closed", "cry", "default", "eyeRoll", "happy", "hearts", "side", "squint", "surprised",
        "winkWacky", "wink", "xDizzy"
    )
    private val ALLOWED_EYEBROWS = setOf(
        "angryNatural", "defaultNatural", "flatNatural", "frownNatural", "raisedExcitedNatural",
        "sadConcernedNatural", "unibrowNatural", "upDownNatural", "angry", "default", "raisedExcited",
        "sadConcerned", "upDown"
    )
    private val ALLOWED_MOUTH = setOf(
        "concerned", "default", "disbelief", "eating", "grimace", "sad", "screamOpen", "serious",
        "smile", "tongue", "twinkle", "vomit"
    )
    private val ALLOWED_FACIAL_HAIR = setOf(
        "blank", "beardLight", "beardMajestic", "beardMedium", "moustacheFancy", "moustacheMagnum"
    )
    private val ALLOWED_ACCESSORIES = setOf(
        "blank", "kurt", "prescription01", "prescription02", "round", "sunglasses", "wayfarers", "eyepatch"
    )
    private val ALLOWED_CLOTHING = setOf(
        "blazerAndShirt", "blazerAndSweater", "collarAndSweater", "graphicShirt", "hoodie", "overall",
        "shirtCrewNeck", "shirtScoopNeck", "shirtVNeck"
    )
    private val ALLOWED_CLOTHING_GRAPHIC = setOf(
        "bat", "bear", "cumbia", "deer", "diamond", "hola", "pizza", "resist", "skull", "skullOutline"
    )
    private val ALLOWED_GENERAL_COLOR = ALLOWED_HAT_COLOR

    fun validateAvatarConfig(config: AvatarConfig): ValidationResult {
        if (config.seed.length > 100 || config.seed.contains("<") || config.seed.contains(">")) {
            return ValidationResult.Error("Invalid avatar seed")
        }
        if (config.top !in ALLOWED_TOP) return ValidationResult.Error("Invalid avatar top: ${config.top}")
        if (config.hairColor !in ALLOWED_HAIR_COLOR) return ValidationResult.Error("Invalid hair color")
        if (config.hatColor !in ALLOWED_HAT_COLOR) return ValidationResult.Error("Invalid hat color")
        if (config.skinColor !in ALLOWED_SKIN_COLOR) return ValidationResult.Error("Invalid skin color")
        if (config.eyes !in ALLOWED_EYES) return ValidationResult.Error("Invalid eyes")
        if (config.eyebrows !in ALLOWED_EYEBROWS) return ValidationResult.Error("Invalid eyebrows")
        if (config.mouth !in ALLOWED_MOUTH) return ValidationResult.Error("Invalid mouth")
        if (config.facialHair !in ALLOWED_FACIAL_HAIR) return ValidationResult.Error("Invalid facial hair")
        if (config.facialHairColor !in ALLOWED_HAIR_COLOR) return ValidationResult.Error("Invalid facial hair color")
        if (config.accessories !in ALLOWED_ACCESSORIES) return ValidationResult.Error("Invalid accessories")
        if (config.accessoriesColor !in ALLOWED_GENERAL_COLOR) return ValidationResult.Error("Invalid accessories color")
        if (config.clothing !in ALLOWED_CLOTHING) return ValidationResult.Error("Invalid clothing")
        if (config.clothesColor !in ALLOWED_GENERAL_COLOR) return ValidationResult.Error("Invalid clothes color")
        if (config.clothingGraphic !in ALLOWED_CLOTHING_GRAPHIC) return ValidationResult.Error("Invalid clothing graphic")
        return ValidationResult.Success
    }
    
    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlankOrEmpty()) {
            return ValidationResult.Error("Email is required")
        }
        if (!email!!.isValidEmail()) {
            return ValidationResult.Error("Invalid email format")
        }
        return ValidationResult.Success
    }
    
    fun validateDisplayName(name: String?): ValidationResult {
        if (name.isNullOrBlankOrEmpty()) {
            return ValidationResult.Error("Display name is required")
        }
        if (name!!.length < 2) {
            return ValidationResult.Error("Display name must be at least 2 characters")
        }
        if (name.length > 50) {
            return ValidationResult.Error("Display name must not exceed 50 characters")
        }
        return ValidationResult.Success
    }
    
    fun validateScheduleTime(time: String?): ValidationResult {
        if (time.isNullOrBlankOrEmpty()) {
            return ValidationResult.Error("Time is required")
        }
        if (!time!!.isValidTime()) {
            return ValidationResult.Error("Invalid time format. Use HH:mm (e.g., 08:00)")
        }
        return ValidationResult.Success
    }
    
    fun validateDayOfWeek(day: Int?): ValidationResult {
        if (day == null) {
            return ValidationResult.Error("Day of week is required")
        }
        if (day !in 1..7) {
            return ValidationResult.Error("Day of week must be between 1 (Monday) and 7 (Sunday)")
        }
        return ValidationResult.Success
    }
    
    fun validateTimeRange(startTime: String?, endTime: String?): ValidationResult {
        val startValidation = validateScheduleTime(startTime)
        if (startValidation is ValidationResult.Error) return startValidation
        
        val endValidation = validateScheduleTime(endTime)
        if (endValidation is ValidationResult.Error) return endValidation
        
        val start = timeToMinutes(startTime!!)
        val end = timeToMinutes(endTime!!)
        
        if (start >= end) {
            return ValidationResult.Error("End time must be after start time")
        }
        
        return ValidationResult.Success
    }
    
    fun validateExerciseAnswer(answer: String?): ValidationResult {
        if (answer.isNullOrBlankOrEmpty()) {
            return ValidationResult.Error("Answer is required")
        }
        if (answer !in listOf("A", "B", "C", "D")) {
            return ValidationResult.Error("Answer must be one of: A, B, C, D")
        }
        return ValidationResult.Success
    }
    
    fun validateDifficulty(difficulty: String?): ValidationResult {
        if (difficulty.isNullOrBlankOrEmpty()) {
            return ValidationResult.Error("Difficulty is required")
        }
        if (difficulty !in listOf(Constants.DIFFICULTY_EASY, Constants.DIFFICULTY_MEDIUM, Constants.DIFFICULTY_HARD)) {
            return ValidationResult.Error("Difficulty must be one of: easy, medium, hard")
        }
        return ValidationResult.Success
    }
    
    fun validatePagination(limit: Int?, offset: Int?): ValidationResult {
        if (limit != null && limit <= 0) {
            return ValidationResult.Error("Limit must be greater than 0")
        }
        if (limit != null && limit > 100) {
            return ValidationResult.Error("Limit must not exceed 100")
        }
        if (offset != null && offset < 0) {
            return ValidationResult.Error("Offset must be greater than or equal to 0")
        }
        return ValidationResult.Success
    }
    
    fun validateTimeSpent(timeSpent: Int?): ValidationResult {
        if (timeSpent == null) {
            return ValidationResult.Error("Time spent is required")
        }
        if (timeSpent < 0) {
            return ValidationResult.Error("Time spent cannot be negative")
        }
        if (timeSpent > 3600) {
            return ValidationResult.Error("Time spent seems unrealistic (>1 hour)")
        }
        return ValidationResult.Success
    }
    
    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        return hours * 60 + minutes
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}


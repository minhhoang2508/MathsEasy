package com.mathseasy.utils

/**
 * Input validation utilities
 */
object Validator {
    
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


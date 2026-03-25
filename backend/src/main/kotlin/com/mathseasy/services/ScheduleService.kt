package com.mathseasy.services

import com.mathseasy.models.LearningSchedule
import com.mathseasy.models.ScheduleInput
import com.mathseasy.repositories.ScheduleRepository
import com.mathseasy.utils.Validator
import com.mathseasy.utils.ValidationResult
import com.mathseasy.utils.getCurrentTimestamp
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ScheduleService(
    private val scheduleRepository: ScheduleRepository = ScheduleRepository()
) {
    
    suspend fun createSchedules(userId: String, scheduleInputs: List<ScheduleInput>): List<LearningSchedule> {
        val createdSchedules = mutableListOf<LearningSchedule>()
        
        for (input in scheduleInputs) {
            // Validate input
            val dayValidation = Validator.validateDayOfWeek(input.dayOfWeek)
            if (dayValidation is ValidationResult.Error) {
                throw IllegalArgumentException(dayValidation.message)
            }
            
            val timeValidation = Validator.validateTimeRange(input.startTime, input.endTime)
            if (timeValidation is ValidationResult.Error) {
                throw IllegalArgumentException(timeValidation.message)
            }
            
            // Create schedule
            val schedule = LearningSchedule(
                userId = userId,
                dayOfWeek = input.dayOfWeek,
                startTime = input.startTime,
                endTime = input.endTime,
                active = true,
                createdAt = getCurrentTimestamp(),
                updatedAt = getCurrentTimestamp()
            )
            
            val created = scheduleRepository.createSchedule(schedule)
            createdSchedules.add(created)
        }
        
        logger.info { "Created ${createdSchedules.size} schedules for user: $userId" }
        return createdSchedules
    }
    
    suspend fun getSchedulesByUserId(userId: String): List<LearningSchedule> {
        return scheduleRepository.getSchedulesByUserId(userId)
    }
    
    suspend fun getActiveSchedulesByUserId(userId: String): List<LearningSchedule> {
        return scheduleRepository.getActiveSchedulesByUserId(userId)
    }
    
    suspend fun getScheduleById(scheduleId: String, userId: String): LearningSchedule? {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        
        // Verify ownership
        if (schedule != null && schedule.userId != userId) {
            throw IllegalAccessException("You don't have permission to access this schedule")
        }
        
        return schedule
    }
    
    suspend fun updateSchedule(
        scheduleId: String,
        userId: String,
        dayOfWeek: Int? = null,
        startTime: String? = null,
        endTime: String? = null,
        active: Boolean? = null
    ): LearningSchedule? {
        // Verify ownership
        val existingSchedule = getScheduleById(scheduleId, userId)
            ?: throw NoSuchElementException("Schedule not found")
        
        val updates = mutableMapOf<String, Any>()
        
        // Validate and add updates
        if (dayOfWeek != null) {
            val validation = Validator.validateDayOfWeek(dayOfWeek)
            if (validation is ValidationResult.Error) {
                throw IllegalArgumentException(validation.message)
            }
            updates["dayOfWeek"] = dayOfWeek
        }
        
        if (startTime != null || endTime != null) {
            val finalStartTime = startTime ?: existingSchedule.startTime
            val finalEndTime = endTime ?: existingSchedule.endTime
            
            val validation = Validator.validateTimeRange(finalStartTime, finalEndTime)
            if (validation is ValidationResult.Error) {
                throw IllegalArgumentException(validation.message)
            }
            
            if (startTime != null) updates["startTime"] = startTime
            if (endTime != null) updates["endTime"] = endTime
        }
        
        if (active != null) {
            updates["active"] = active
        }
        
        if (updates.isEmpty()) {
            return existingSchedule
        }
        
        return scheduleRepository.updateSchedule(scheduleId, updates)
    }
    
    suspend fun deleteSchedule(scheduleId: String, userId: String): Boolean {
        // Verify ownership
        getScheduleById(scheduleId, userId)
            ?: throw NoSuchElementException("Schedule not found")
        
        return scheduleRepository.deleteSchedule(scheduleId)
    }
    
    suspend fun deleteAllSchedules(userId: String): Boolean {
        return scheduleRepository.deleteAllSchedulesByUserId(userId)
    }
    
    suspend fun getTodaySchedules(userId: String): List<LearningSchedule> {
        val today = java.time.LocalDate.now().dayOfWeek.value // 1-7 (Monday-Sunday)
        return scheduleRepository.getScheduleByDayAndUserId(userId, today)
    }
}

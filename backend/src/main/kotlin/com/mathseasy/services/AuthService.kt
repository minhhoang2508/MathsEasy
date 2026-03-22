package com.mathseasy.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.mathseasy.models.User
import com.mathseasy.models.UserPreferences
import com.mathseasy.repositories.UserRepository
import com.mathseasy.utils.getCurrentTimestamp
import com.mathseasy.utils.getCurrentDate
import com.mathseasy.utils.isYesterday
import com.mathseasy.utils.toLocalDateTime
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class AuthService(
    private val userRepository: UserRepository = UserRepository()
) {
    private val auth: FirebaseAuth = FirebaseService.getAuth()
    
    /**
     * Verify Firebase ID token and return decoded token
     */
    fun verifyIdToken(idToken: String): FirebaseToken? {
        return try {
            auth.verifyIdToken(idToken)
        } catch (e: Exception) {
            logger.error(e) { "Token verification failed: ${e.message}" }
            null
        }
    }
    
    /**
     * Register new user or login existing user
     */
    suspend fun registerOrLogin(idToken: String, displayName: String? = null): Pair<User, Boolean> {
        val decodedToken = verifyIdToken(idToken) 
            ?: throw IllegalArgumentException("Invalid ID token")
        
        val uid = decodedToken.uid
        val email = decodedToken.email ?: throw IllegalArgumentException("Email not found in token")
        
        // Check if user already exists
        val existingUser = userRepository.getUserById(uid)
        
        return if (existingUser != null) {
            // Existing user - update last login
            var updatedUser = userRepository.updateLastLogin(uid) ?: existingUser

            // Validate streak: reset to 0 if lastStreakDate is not today or yesterday
            // Only update currentStreak, preserve lastStreakDate so submitSet can detect "else" branch correctly
            val lastStreakDate = updatedUser.lastStreakDate
            if (lastStreakDate != null && updatedUser.currentStreak > 0) {
                val today = getCurrentDate()
                val streakDate = lastStreakDate.toLocalDateTime().date
                if (streakDate != today && !isYesterday(lastStreakDate)) {
                    userRepository.updateUser(uid, mapOf("currentStreak" to 0))
                    updatedUser = updatedUser.copy(currentStreak = 0)
                }
            }

            Pair(updatedUser, false)
        } else {
            // New user - create profile
            val newUser = User(
                uid = uid,
                email = email,
                displayName = displayName ?: decodedToken.name,
                photoUrl = decodedToken.picture,
                createdAt = getCurrentTimestamp(),
                lastLoginAt = getCurrentTimestamp(),
                totalPoints = 0,
                currentStreak = 0,
                longestStreak = 0,
                lastStreakDate = null,
                badgeIds = emptyList(),
                fcmToken = null,
                preferences = UserPreferences()
            )
            
            val createdUser = userRepository.createUser(newUser)
            logger.info { "New user registered: $uid" }
            Pair(createdUser, true)
        }
    }
    
    /**
     * Get user by ID token
     */
    suspend fun getUserByToken(idToken: String): User? {
        val decodedToken = verifyIdToken(idToken) ?: return null
        return userRepository.getUserById(decodedToken.uid)
    }
    
    /**
     * Verify if user is authenticated and return user ID
     */
    fun authenticateToken(idToken: String): String? {
        val decodedToken = verifyIdToken(idToken)
        return decodedToken?.uid
    }
}



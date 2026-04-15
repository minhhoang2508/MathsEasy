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
import com.mathseasy.utils.daysAgo
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
    suspend fun registerOrLogin(idToken: String, displayName: String? = null, timezone: String? = null): Pair<User, Boolean> {
        val decodedToken = verifyIdToken(idToken) 
            ?: throw IllegalArgumentException("Invalid ID token")
        
        val uid = decodedToken.uid
        val email = decodedToken.email ?: throw IllegalArgumentException("Email not found in token")
        val requestedName = displayName?.trim().takeUnless { it.isNullOrBlank() }
        val tokenName = decodedToken.name?.trim().takeUnless { it.isNullOrBlank() }
        val resolvedName = requestedName ?: tokenName
        val resolvedTimezone = timezone?.trim().takeUnless { it.isNullOrBlank() }
        
        val existingUser = userRepository.getUserById(uid)
        
        return if (existingUser != null) {
            var updatedUser = userRepository.updateLastLogin(uid) ?: existingUser

            val lastStreakDate = updatedUser.lastStreakDate
            if (lastStreakDate != null && updatedUser.currentStreak > 0) {
                val today = getCurrentDate()
                val streakDate = lastStreakDate.toLocalDateTime().date
                if (streakDate != today && !isYesterday(lastStreakDate)) {
                    val twoDaysAgo = daysAgo(2)
                    val missedOneDay = streakDate == twoDaysAgo
                    userRepository.updateUser(uid, mapOf(
                        "currentStreak" to 0,
                        "previousStreak" to updatedUser.currentStreak,
                        "streakBroken" to missedOneDay
                    ))
                    updatedUser = updatedUser.copy(
                        currentStreak = 0,
                        previousStreak = updatedUser.currentStreak,
                        streakBroken = missedOneDay
                    )
                }
            }

            if (!resolvedName.isNullOrBlank() && resolvedName != updatedUser.displayName) {
                userRepository.updateUser(uid, mapOf("displayName" to resolvedName))
                updatedUser = updatedUser.copy(displayName = resolvedName)
            }

            if (resolvedTimezone != null && resolvedTimezone != updatedUser.preferences.timezone) {
                val updatedPrefs = updatedUser.preferences.copy(timezone = resolvedTimezone)
                userRepository.updatePreferences(uid, updatedPrefs)
                updatedUser = updatedUser.copy(preferences = updatedPrefs)
            }

            Pair(updatedUser, false)
        } else {
            val newPreferences = UserPreferences(
                timezone = resolvedTimezone
            )
            val newUser = User(
                uid = uid,
                email = email,
                displayName = resolvedName,
                photoUrl = decodedToken.picture,
                createdAt = getCurrentTimestamp(),
                lastLoginAt = getCurrentTimestamp(),
                totalPoints = 0,
                currentStreak = 0,
                longestStreak = 0,
                lastStreakDate = null,
                badgeIds = emptyList(),
                fcmToken = null,
                preferences = newPreferences
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


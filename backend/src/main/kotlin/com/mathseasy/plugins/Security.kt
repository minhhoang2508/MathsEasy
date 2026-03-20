package com.mathseasy.plugins

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.mathseasy.services.FirebaseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Firebase Authentication configuration
 */
fun Application.configureSecurity() {
    install(Authentication) {
        bearer("firebase-auth") {
            authenticate { credential ->
                try {
                    val token = credential.token
                    val decodedToken = verifyFirebaseToken(token)
                    
                    if (decodedToken != null) {
                        UserIdPrincipal(decodedToken.uid)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Authentication failed: ${e.message}" }
                    null
                }
            }
        }
    }
}

/**
 * Verify Firebase ID token
 */
fun verifyFirebaseToken(idToken: String): FirebaseToken? {
    return try {
        val auth = FirebaseService.getAuth()
        auth.verifyIdToken(idToken)
    } catch (e: Exception) {
        logger.error(e) { "Token verification failed: ${e.message}" }
        null
    }
}

/**
 * Extension to get current user ID from authenticated request
 */
fun ApplicationCall.getUserId(): String? {
    return principal<UserIdPrincipal>()?.name
}

/**
 * Extension to require authenticated user
 */
suspend fun ApplicationCall.requireUserId(): String {
    val userId = getUserId()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        throw UnauthorizedException()
    }
    return userId
}

class UnauthorizedException : Exception("Unauthorized")



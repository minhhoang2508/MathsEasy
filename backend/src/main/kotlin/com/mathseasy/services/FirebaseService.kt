package com.mathseasy.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.github.cdimascio.dotenv.dotenv
import mu.KotlinLogging
import java.io.FileInputStream

private val logger = KotlinLogging.logger {}

/**
 * Firebase Service to initialize Firebase Admin SDK
 */
object FirebaseService {
    private var firebaseApp: FirebaseApp? = null
    private var firestore: Firestore? = null
    private var auth: FirebaseAuth? = null
    
    fun initialize() {
        if (firebaseApp != null) {
            logger.info { "Firebase already initialized" }
            return
        }
        
        try {
            val dotenv = dotenv {
                ignoreIfMissing = true
            }
            
            val serviceAccountPath = dotenv["FIREBASE_SERVICE_ACCOUNT_PATH"] 
                ?: "src/main/resources/serviceAccountKey.json"
            
            val serviceAccount = FileInputStream(serviceAccountPath)
            val credentials = GoogleCredentials.fromStream(serviceAccount)
            
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()
            
            firebaseApp = FirebaseApp.initializeApp(options)
            firestore = FirestoreClient.getFirestore()
            auth = FirebaseAuth.getInstance()
            
            logger.info { "Firebase initialized successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Firebase: ${e.message}" }
            throw e
        }
    }
    
    fun getFirestore(): Firestore {
        if (firestore == null) {
            initialize()
        }
        return firestore ?: throw IllegalStateException("Firestore not initialized")
    }
    
    fun getAuth(): FirebaseAuth {
        if (auth == null) {
            initialize()
        }
        return auth ?: throw IllegalStateException("FirebaseAuth not initialized")
    }
    
    fun shutdown() {
        firebaseApp?.delete()
        firebaseApp = null
        firestore = null
        auth = null
        logger.info { "Firebase shutdown complete" }
    }
}



package com.mathseasy

import com.mathseasy.plugins.*
import com.mathseasy.services.FirebaseService
import com.mathseasy.services.NotificationScheduler
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val notificationScheduler = NotificationScheduler()

fun main() {
    // Load environment variables
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    
    val port = dotenv["PORT"]?.toIntOrNull() ?: 8080
    val environment = dotenv["ENVIRONMENT"] ?: "development"
    
    logger.info { "Starting MathsEasy Backend Server..." }
    logger.info { "Environment: $environment" }
    logger.info { "Port: $port" }
    
    // Initialize Firebase
    try {
        FirebaseService.initialize()
        logger.info { "Firebase initialized successfully" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to initialize Firebase: ${e.message}" }
        logger.warn { "Server will start but Firebase-dependent features may not work" }
    }
    
    // Start Ktor server
    embeddedServer(
        Netty, 
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // Configure plugins
    configureSerialization()
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
    
    logger.info { "All plugins configured successfully" }
    
    // Start notification scheduler
    try {
        notificationScheduler.start()
        logger.info { "Notification scheduler started successfully" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to start notification scheduler: ${e.message}" }
    }
    
    logger.info { "MathsEasy Backend is ready to accept requests!" }
    
    // Shutdown hook
    environment.monitor.subscribe(ApplicationStopped) {
        logger.info { "Shutting down MathsEasy Backend..." }
        notificationScheduler.stop()
        FirebaseService.shutdown()
        logger.info { "Shutdown complete" }
    }
}



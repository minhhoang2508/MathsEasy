package com.mathseasy.plugins

import com.mathseasy.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Health check endpoint
        get("/") {
            call.respondText("MathsEasy Backend API is running!")
        }
        
        get("/health") {
            call.respond(
                mapOf(
                    "status" to "healthy",
                    "service" to "mathseasy-backend",
                    "version" to "1.0.0"
                )
            )
        }
        
        // API routes
        route("/api") {
            authRoutes()
            userRoutes()
            scheduleRoutes()
            exerciseRoutes()
            exerciseSetRoutes()
            historyRoutes()
            badgeRoutes()
            aiRoutes()
            notificationRoutes()
            paymentRoutes()
        }
    }
}


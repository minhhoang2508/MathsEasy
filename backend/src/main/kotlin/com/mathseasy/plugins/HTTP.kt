package com.mathseasy.plugins

import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    val dotenv = dotenv { ignoreIfMissing = true }
    val allowedOrigins = dotenv["ALLOWED_ORIGINS"]?.split(",") 
        ?: listOf("http://localhost:3000", "http://localhost:8081")
    
    install(CORS) {
        allowedOrigins.forEach { origin ->
            allowHost(origin.removePrefix("http://").removePrefix("https://"), schemes = listOf("http", "https"))
        }
        
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        
        allowCredentials = true
        maxAgeInSeconds = 86400 // 1 day
    }
}


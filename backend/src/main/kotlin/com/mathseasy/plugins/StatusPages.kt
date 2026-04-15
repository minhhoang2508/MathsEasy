package com.mathseasy.plugins

import com.mathseasy.models.errorResponse
import com.mathseasy.utils.Constants
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception: ${cause.message}" }
            
            when (cause) {
                is UnauthorizedException -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        errorResponse<Map<String,String>>(Constants.ERROR_UNAUTHORIZED, "Unauthorized access")
                    )
                }
                is ForbiddenException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        errorResponse<Map<String,String>>(Constants.ERROR_UNAUTHORIZED, "Admin access required")
                    )
                }
                is IllegalArgumentException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        errorResponse<Map<String,String>>(Constants.ERROR_INVALID_INPUT, cause.message ?: "Invalid input")
                    )
                }
                is NoSuchElementException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        errorResponse<Map<String,String>>(Constants.ERROR_NOT_FOUND, cause.message ?: "Resource not found")
                    )
                }
                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        errorResponse<Map<String,String>>(
                            Constants.ERROR_INTERNAL_SERVER,
                            "An internal server error occurred"
                        )
                    )
                }
            }
        }
        
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                errorResponse<Map<String,String>>(Constants.ERROR_NOT_FOUND, "Endpoint not found")
            )
        }
    }
}



package com.printnest.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

private val logger = LoggerFactory.getLogger("ErrorHandling")

/**
 * Configure error handling with StatusPages plugin.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        // Handle validation errors
        exception<BadRequestException> { call, cause ->
            val error = ApiError.badRequest(
                message = cause.message ?: "Invalid request",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            logger.warn("Bad request: ${cause.message}", cause)
            call.respond(HttpStatusCode.BadRequest, error)
        }

        // Handle not found
        exception<NotFoundException> { call, cause ->
            val error = ApiError.notFound(
                message = cause.message ?: "Resource not found",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            logger.debug("Not found: ${cause.message}")
            call.respond(HttpStatusCode.NotFound, error)
        }

        // Handle serialization errors
        exception<SerializationException> { call, cause ->
            val error = ApiError.badRequest(
                message = "Invalid JSON format: ${cause.message}",
                code = "INVALID_JSON",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            logger.warn("Serialization error: ${cause.message}", cause)
            call.respond(HttpStatusCode.BadRequest, error)
        }

        // Handle content transformation errors
        exception<io.ktor.server.plugins.ContentTransformationException> { call, cause ->
            val error = ApiError.badRequest(
                message = "Unable to parse request body: ${cause.message}",
                code = "PARSE_ERROR",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            logger.warn("Content transformation error: ${cause.message}", cause)
            call.respond(HttpStatusCode.BadRequest, error)
        }

        // Handle all other exceptions
        exception<Exception> { call, cause ->
            val requestId = call.getRequestId()

            logger.error(
                "Unhandled exception [requestId=$requestId, path=${call.request.path()}]",
                cause
            )

            val error = ApiError.internalError(
                message = cause.message ?: "An unexpected error occurred",
                path = call.request.path(),
                requestId = requestId
            )

            call.respond(HttpStatusCode.InternalServerError, error)
        }

        // Handle status codes
        status(HttpStatusCode.NotFound) { call, status ->
            val error = ApiError.notFound(
                message = "The requested resource was not found",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            call.respond(status, error)
        }

        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            val error = ApiError(
                status = 405,
                error = "Method Not Allowed",
                message = "HTTP method ${call.request.httpMethod.value} is not allowed for this resource",
                code = "METHOD_NOT_ALLOWED",
                path = call.request.path(),
                requestId = call.getRequestId()
            )
            call.respond(status, error)
        }
    }
}

/**
 * Standard API error response.
 */
@Serializable
data class ApiError(
    val status: Int,
    val error: String,
    val message: String,
    val code: String? = null,
    val path: String? = null,
    val timestamp: String = Instant.now().toString(),
    val requestId: String? = null
) {
    companion object {
        fun badRequest(
            message: String,
            code: String? = "BAD_REQUEST",
            path: String? = null,
            requestId: String? = null
        ) = ApiError(
            status = 400,
            error = "Bad Request",
            message = message,
            code = code,
            path = path,
            requestId = requestId
        )

        fun notFound(
            message: String,
            code: String? = "NOT_FOUND",
            path: String? = null,
            requestId: String? = null
        ) = ApiError(
            status = 404,
            error = "Not Found",
            message = message,
            code = code,
            path = path,
            requestId = requestId
        )

        fun internalError(
            message: String,
            code: String? = "INTERNAL_ERROR",
            path: String? = null,
            requestId: String? = null
        ) = ApiError(
            status = 500,
            error = "Internal Server Error",
            message = message,
            code = code,
            path = path,
            requestId = requestId
        )
    }
}

// Request ID helper
private fun ApplicationCall.getRequestId(): String {
    return request.header("X-Request-ID")
        ?: response.headers["X-Request-ID"]
        ?: UUID.randomUUID().toString()
}

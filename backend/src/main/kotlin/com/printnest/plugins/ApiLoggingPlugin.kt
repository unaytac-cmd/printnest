package com.printnest.plugins

import com.printnest.domain.repository.ApiLogRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Ktor plugin for logging all API requests and responses.
 * Stores logs in the database for monitoring and debugging.
 */
class ApiLoggingPlugin(private val config: ApiLoggingConfig) {

    private val logger = LoggerFactory.getLogger(ApiLoggingPlugin::class.java)

    class ApiLoggingConfig {
        var enabled: Boolean = true
        var logRequestBody: Boolean = false
        var logResponseBody: Boolean = false
        var maxBodySize: Int = 10000 // Max bytes to log for request/response body
        var excludePaths: List<String> = listOf(
            "/health",
            "/",
            "/api/v1/status",
            "/api/v1/monitor/health",
            "/api/v1/monitor/logs" // Prevent recursive logging
        )
        var excludeContentTypes: List<String> = listOf(
            "image/",
            "video/",
            "audio/",
            "application/octet-stream"
        )
        var slowRequestThresholdMs: Long = 5000
    }

    companion object Plugin : BaseApplicationPlugin<Application, ApiLoggingConfig, ApiLoggingPlugin> {
        override val key = AttributeKey<ApiLoggingPlugin>("ApiLoggingPlugin")

        private val REQUEST_START_TIME = AttributeKey<Long>("RequestStartTime")
        private val REQUEST_BODY = AttributeKey<String>("RequestBody")

        override fun install(pipeline: Application, configure: ApiLoggingConfig.() -> Unit): ApiLoggingPlugin {
            val config = ApiLoggingConfig().apply(configure)
            val plugin = ApiLoggingPlugin(config)

            if (!config.enabled) {
                return plugin
            }

            // Intercept at the start of the call to capture timing
            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                call.attributes.put(REQUEST_START_TIME, System.currentTimeMillis())

                // Capture request body if enabled
                if (config.logRequestBody && !isExcludedPath(call.request.path(), config.excludePaths)) {
                    try {
                        val contentType = call.request.contentType().toString()
                        if (!config.excludeContentTypes.any { contentType.startsWith(it) }) {
                            // Note: Reading request body here may consume it
                            // In production, you might want to use DoubleReceive plugin
                        }
                    } catch (e: Exception) {
                        // Ignore errors reading body
                    }
                }

                proceed()
            }

            // Intercept after response to log the request
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) {
                logRequest(call, config)
            }

            return plugin
        }

        private suspend fun logRequest(
            call: ApplicationCall,
            config: ApiLoggingConfig
        ) {
            val path = call.request.path()

            // Skip excluded paths
            if (isExcludedPath(path, config.excludePaths)) {
                return
            }

            try {
                val apiLogRepository: ApiLogRepository by call.application.inject()

                val startTime = call.attributes.getOrNull(REQUEST_START_TIME) ?: System.currentTimeMillis()
                val duration = System.currentTimeMillis() - startTime

                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                val userId = call.request.headers["X-User-Id"]?.toLongOrNull()

                val statusCode = call.response.status()?.value ?: 0
                val method = call.request.httpMethod.value

                val userAgent = call.request.userAgent()
                val ipAddress = call.request.origin.remoteHost

                // Determine if this is an error
                val errorMessage = if (statusCode >= 400) {
                    "HTTP $statusCode - ${call.response.status()?.description ?: "Error"}"
                } else null

                // Log slow requests
                if (duration >= config.slowRequestThresholdMs) {
                    val logger = LoggerFactory.getLogger("SlowRequest")
                    logger.warn("Slow request: $method $path took ${duration}ms (tenant: $tenantId, user: $userId)")
                }

                // Create log entry asynchronously to not block the response
                try {
                    apiLogRepository.create(
                        tenantId = tenantId,
                        userId = userId,
                        endpoint = path,
                        method = method,
                        statusCode = statusCode,
                        durationMs = duration,
                        requestSize = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                        responseSize = call.response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                        userAgent = userAgent?.take(500),
                        ipAddress = ipAddress?.take(50),
                        errorMessage = errorMessage,
                        errorStackTrace = null,
                        requestBody = if (config.logRequestBody) call.attributes.getOrNull(REQUEST_BODY) else null,
                        responseBody = null // Response body logging requires more complex setup
                    )
                } catch (e: Exception) {
                    val logger = LoggerFactory.getLogger("ApiLogging")
                    logger.error("Failed to log API request: $method $path", e)
                }
            } catch (e: Exception) {
                val logger = LoggerFactory.getLogger("ApiLogging")
                logger.error("Error in API logging plugin", e)
            }
        }

        private fun isExcludedPath(path: String, excludePaths: List<String>): Boolean {
            return excludePaths.any { excluded ->
                path == excluded || path.startsWith("$excluded/") || path.startsWith(excluded)
            }
        }
    }
}

/**
 * Configure API logging plugin on the application
 */
fun Application.configureApiLogging() {
    install(ApiLoggingPlugin) {
        enabled = System.getenv("API_LOGGING_ENABLED")?.toBoolean() ?: true
        logRequestBody = System.getenv("API_LOGGING_REQUEST_BODY")?.toBoolean() ?: false
        logResponseBody = System.getenv("API_LOGGING_RESPONSE_BODY")?.toBoolean() ?: false
        maxBodySize = System.getenv("API_LOGGING_MAX_BODY_SIZE")?.toIntOrNull() ?: 10000
        slowRequestThresholdMs = System.getenv("API_LOGGING_SLOW_THRESHOLD_MS")?.toLongOrNull() ?: 5000

        excludePaths = listOf(
            "/health",
            "/",
            "/api/v1/status",
            "/api/v1/monitor/health",
            "/api/v1/monitor/logs",
            "/api/v1/monitor/dashboard",
            "/favicon.ico",
            "/robots.txt"
        )

        excludeContentTypes = listOf(
            "image/",
            "video/",
            "audio/",
            "application/octet-stream",
            "multipart/form-data"
        )
    }
}

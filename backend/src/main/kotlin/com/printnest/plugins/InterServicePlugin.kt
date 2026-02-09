package com.printnest.plugins

import com.printnest.domain.models.ServicePrincipal
import com.printnest.integrations.interservice.InterServiceAuth
import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("InterServicePlugin")

/**
 * Configure inter-service authentication for the application.
 * This is called after the main Authentication plugin is installed,
 * and provides helper extensions for inter-service communication.
 *
 * Note: The actual authentication providers ("inter-service" and "internal-api-key")
 * are registered in the main configureAuthentication() function.
 */
fun Application.configureInterServiceAuth() {
    logger.info("Inter-service authentication extensions configured")
    // Authentication providers are registered in configureAuthentication()
    // This function can be used for additional inter-service setup if needed
}

/**
 * Error response for inter-service authentication failures.
 */
@Serializable
data class InterServiceAuthError(
    val error: String,
    val message: String
)

/**
 * Extension function to verify inter-service request signature.
 * Can be used in routes for additional signature verification.
 */
fun ApplicationCall.verifyInterServiceSignature(
    interServiceAuth: InterServiceAuth,
    payload: String
): Boolean {
    val signature = request.headers["X-PrintNest-Signature"]
    val timestampStr = request.headers["X-PrintNest-Timestamp"]

    if (signature.isNullOrBlank() || timestampStr.isNullOrBlank()) {
        return false
    }

    val timestamp = timestampStr.toLongOrNull() ?: return false

    return interServiceAuth.verifyWebhookSignature(payload, signature, timestamp)
}

/**
 * Extension to get the service principal from an authenticated inter-service request.
 */
val ApplicationCall.servicePrincipal: ServicePrincipal?
    get() = principal<ServicePrincipal>()

/**
 * Extension to get the internal service principal from an authenticated internal request.
 */
val ApplicationCall.internalServicePrincipal: InternalServicePrincipal?
    get() = principal<InternalServicePrincipal>()

/**
 * Extension to check if the request is from an authenticated service.
 */
val ApplicationCall.isAuthenticatedService: Boolean
    get() = servicePrincipal != null || internalServicePrincipal != null

/**
 * Extension to get the calling service name.
 */
val ApplicationCall.callingServiceName: String?
    get() = servicePrincipal?.serviceName ?: internalServicePrincipal?.serviceName

/**
 * Get inter-service signature header name.
 */
object InterServiceHeaders {
    const val SIGNATURE = "X-PrintNest-Signature"
    const val TIMESTAMP = "X-PrintNest-Timestamp"
    const val SERVICE_NAME = "X-PrintNest-Service"
}

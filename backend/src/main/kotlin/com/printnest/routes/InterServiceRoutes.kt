package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.integrations.interservice.InterServiceAuth
import com.printnest.integrations.interservice.InterServiceClient
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("InterServiceRoutes")

fun Route.interServiceRoutes() {
    val interServiceAuth: InterServiceAuth = GlobalContext.get().get()
    val interServiceClient: InterServiceClient = GlobalContext.get().get()
    val json: Json = GlobalContext.get().get()

    route("/interservice") {

        /**
         * POST /api/v1/interservice/authenticate
         * Get a service token for inter-service authentication.
         */
        post("/authenticate") {
            try {
                val request = call.receive<ServiceAuthRequest>()

                // Validate the service authentication
                val isValid = interServiceAuth.validateServiceAuth(
                    serviceName = request.serviceName,
                    serviceSecret = request.serviceSecret,
                    timestamp = request.timestamp
                )

                if (!isValid) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ServiceAuthResponse(
                            success = false,
                            message = "Invalid service credentials or timestamp"
                        )
                    )
                    return@post
                }

                // Generate service token
                val serviceToken = interServiceAuth.generateServiceToken(request.serviceName)

                call.respond(
                    HttpStatusCode.OK,
                    ServiceAuthResponse(
                        success = true,
                        token = serviceToken.token,
                        expiresIn = serviceToken.expiry - System.currentTimeMillis(),
                        message = "Authentication successful"
                    )
                )
            } catch (e: Exception) {
                logger.error("Error in service authentication: {}", e.message)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ServiceAuthResponse(
                        success = false,
                        message = "Invalid request: ${e.message}"
                    )
                )
            }
        }

        // Protected inter-service routes
        authenticate("inter-service") {

            /**
             * POST /api/v1/interservice/tracking-update
             * Update tracking information for an order.
             */
            post("/tracking-update") {
                try {
                    val servicePrincipal = call.principal<ServicePrincipal>()
                    if (servicePrincipal == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            InterServiceResponse(success = false, message = "Invalid service token")
                        )
                        return@post
                    }

                    val payload = call.receive<TrackingUpdatePayload>()

                    logger.info(
                        "Tracking update from service {} for order {}: {} - {}",
                        servicePrincipal.serviceName,
                        payload.orderId,
                        payload.carrier,
                        payload.status
                    )

                    // TODO: Update order tracking in database
                    // orderService.updateTracking(payload)

                    call.respond(
                        HttpStatusCode.OK,
                        InterServiceResponse(
                            success = true,
                            message = "Tracking updated successfully",
                            data = json.encodeToJsonElement(
                                mapOf(
                                    "orderId" to payload.orderId,
                                    "status" to payload.status.name
                                )
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error updating tracking: {}", e.message)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        InterServiceResponse(
                            success = false,
                            message = "Error updating tracking: ${e.message}"
                        )
                    )
                }
            }

            /**
             * POST /api/v1/interservice/order-complete
             * Notify that an order has been completed.
             */
            post("/order-complete") {
                try {
                    val servicePrincipal = call.principal<ServicePrincipal>()
                    if (servicePrincipal == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            InterServiceResponse(success = false, message = "Invalid service token")
                        )
                        return@post
                    }

                    val payload = call.receive<OrderCompletePayload>()

                    logger.info(
                        "Order complete notification from service {} for order {} (tenant: {})",
                        servicePrincipal.serviceName,
                        payload.orderId,
                        payload.tenantId
                    )

                    // TODO: Handle order completion
                    // - Update order status
                    // - Send tracking to marketplace if notifyMarketplace is true
                    // - Send notification emails

                    call.respond(
                        HttpStatusCode.OK,
                        InterServiceResponse(
                            success = true,
                            message = "Order completion processed",
                            data = json.encodeToJsonElement(
                                mapOf(
                                    "orderId" to payload.orderId,
                                    "processed" to true
                                )
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error processing order completion: {}", e.message)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        InterServiceResponse(
                            success = false,
                            message = "Error processing order completion: ${e.message}"
                        )
                    )
                }
            }

            /**
             * POST /api/v1/interservice/webhook
             * Generic webhook handler for inter-service events.
             */
            post("/webhook") {
                try {
                    val servicePrincipal = call.principal<ServicePrincipal>()
                    if (servicePrincipal == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            InterServiceResponse(success = false, message = "Invalid service token")
                        )
                        return@post
                    }

                    val request = call.receive<InterServiceRequest>()

                    // Verify signature
                    val payloadJson = json.encodeToString(JsonElement.serializer(), request.payload)
                    val expectedSignature = interServiceAuth.generateSignature(
                        "${request.action}.$payloadJson.${request.timestamp}"
                    )

                    if (!interServiceAuth.verifySignature(
                            "${request.action}.$payloadJson.${request.timestamp}",
                            request.signature
                        )
                    ) {
                        logger.warn("Invalid signature from service: {}", servicePrincipal.serviceName)
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            InterServiceResponse(success = false, message = "Invalid signature")
                        )
                        return@post
                    }

                    logger.info(
                        "Webhook received from {} - action: {}",
                        servicePrincipal.serviceName,
                        request.action
                    )

                    // Route the webhook based on action
                    val result = when (request.action) {
                        "tracking.update" -> handleTrackingUpdate(request.payload, json)
                        "order.complete" -> handleOrderComplete(request.payload, json)
                        "gangsheet.generated" -> handleGangsheetGenerated(request.payload, json)
                        "shipment.created" -> handleShipmentCreated(request.payload, json)
                        else -> {
                            logger.warn("Unknown webhook action: {}", request.action)
                            InterServiceResponse(
                                success = false,
                                message = "Unknown action: ${request.action}"
                            )
                        }
                    }

                    if (result.success) {
                        call.respond(HttpStatusCode.OK, result)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, result)
                    }
                } catch (e: Exception) {
                    logger.error("Error processing webhook: {}", e.message)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        InterServiceResponse(
                            success = false,
                            message = "Error processing webhook: ${e.message}"
                        )
                    )
                }
            }

            /**
             * GET /api/v1/interservice/health
             * Health check for inter-service communication.
             */
            get("/health") {
                val servicePrincipal = call.principal<ServicePrincipal>()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "healthy",
                        "service" to "printnest-backend",
                        "callingService" to (servicePrincipal?.serviceName ?: "unknown"),
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            }
        }

        /**
         * POST /api/v1/interservice/external-webhook
         * Handle webhooks from external services (with signature verification).
         */
        post("/external-webhook") {
            try {
                // Get signature and timestamp from headers
                val signature = call.request.header("X-PrintNest-Signature")
                val timestampStr = call.request.header("X-PrintNest-Timestamp")

                if (signature.isNullOrBlank() || timestampStr.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        InterServiceResponse(
                            success = false,
                            message = "Missing signature or timestamp headers"
                        )
                    )
                    return@post
                }

                val timestamp = timestampStr.toLongOrNull()
                if (timestamp == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        InterServiceResponse(success = false, message = "Invalid timestamp format")
                    )
                    return@post
                }

                val payload = call.receive<WebhookPayload>()
                val payloadJson = json.encodeToString(JsonElement.serializer(), payload.data)

                // Verify webhook signature
                if (!interServiceAuth.verifyWebhookSignature(payloadJson, payload.signature, payload.timestamp)) {
                    logger.warn("Invalid webhook signature")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        InterServiceResponse(success = false, message = "Invalid webhook signature")
                    )
                    return@post
                }

                logger.info("External webhook received - type: {}", payload.type)

                // Process the webhook based on type
                val result = when (payload.type) {
                    WebhookEventTypes.ORDER_CREATED -> handleExternalOrderCreated(payload.data, json)
                    WebhookEventTypes.ORDER_UPDATED -> handleExternalOrderUpdated(payload.data, json)
                    WebhookEventTypes.TRACKING_UPDATED -> handleExternalTrackingUpdated(payload.data, json)
                    else -> {
                        logger.info("Unhandled webhook type: {}", payload.type)
                        InterServiceResponse(
                            success = true,
                            message = "Webhook received but not processed: ${payload.type}"
                        )
                    }
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                logger.error("Error processing external webhook: {}", e.message)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    InterServiceResponse(
                        success = false,
                        message = "Error processing webhook: ${e.message}"
                    )
                )
            }
        }
    }
}

// Internal webhook handlers

private fun handleTrackingUpdate(payload: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement tracking update logic
    return InterServiceResponse(
        success = true,
        message = "Tracking update processed",
        data = payload
    )
}

private fun handleOrderComplete(payload: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement order completion logic
    return InterServiceResponse(
        success = true,
        message = "Order completion processed",
        data = payload
    )
}

private fun handleGangsheetGenerated(payload: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement gangsheet generated logic
    return InterServiceResponse(
        success = true,
        message = "Gangsheet generation notification processed",
        data = payload
    )
}

private fun handleShipmentCreated(payload: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement shipment created logic
    return InterServiceResponse(
        success = true,
        message = "Shipment creation notification processed",
        data = payload
    )
}

// External webhook handlers

private fun handleExternalOrderCreated(data: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement external order created logic
    return InterServiceResponse(
        success = true,
        message = "External order created notification processed",
        data = data
    )
}

private fun handleExternalOrderUpdated(data: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement external order updated logic
    return InterServiceResponse(
        success = true,
        message = "External order updated notification processed",
        data = data
    )
}

private fun handleExternalTrackingUpdated(data: JsonElement, json: Json): InterServiceResponse {
    // TODO: Implement external tracking updated logic
    return InterServiceResponse(
        success = true,
        message = "External tracking updated notification processed",
        data = data
    )
}

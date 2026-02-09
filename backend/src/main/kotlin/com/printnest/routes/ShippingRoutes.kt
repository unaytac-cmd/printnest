package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.ShippingService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import kotlinx.serialization.Serializable

/**
 * Shipping Routes
 *
 * Provides endpoints for:
 * - Address validation
 * - Rate calculation
 * - Label creation and management
 * - Shipment tracking
 * - Label voiding/refunds
 */
fun Route.shippingRoutes() {
    val shippingService: ShippingService = GlobalContext.get().get()

    route("/shipping") {

        // =====================================================
        // ADDRESS VALIDATION
        // =====================================================

        /**
         * POST /api/v1/shipping/validate-address
         *
         * Validates a shipping address and returns suggestions if available
         */
        post("/validate-address") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val request = call.receive<ValidateAddressRequest>()

            shippingService.validateAddress(tenantId, request.address)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Address validation failed"))
                    )
                }
        }

        // =====================================================
        // RATE CALCULATION
        // =====================================================

        /**
         * POST /api/v1/shipping/rates
         *
         * Get available shipping rates for an order
         */
        post("/rates") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val request = call.receive<RateRequest>()

            shippingService.calculateShippingCost(
                orderId = request.orderId,
                tenantId = tenantId,
                destination = request.fromAddress,
                weight = request.parcel?.weight
            )
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Rate calculation failed"))
                    )
                }
        }

        /**
         * GET /api/v1/shipping/rates/{orderId}
         *
         * Get cached/available rates for an existing order
         */
        get("/rates/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            shippingService.getAvailableRates(orderId, tenantId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to get rates"))
                    )
                }
        }

        // =====================================================
        // LABEL MANAGEMENT
        // =====================================================

        /**
         * POST /api/v1/shipping/labels
         *
         * Create a shipping label for an order
         */
        post("/labels") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val request = call.receive<ShippingLabelRequest>()

            shippingService.createShippingLabel(
                orderId = request.orderId,
                tenantId = tenantId,
                rateId = request.rateId,
                shipmentId = request.shipmentId
            )
                .onSuccess { response ->
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Label creation failed"))
                    )
                }
        }

        /**
         * GET /api/v1/shipping/labels/{id}
         *
         * Get a shipping label by ID
         */
        get("/labels/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val labelId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid label ID")
                )

            val label = shippingService.getShippingLabel(labelId, tenantId)
            if (label != null) {
                call.respond(label)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Label not found")
                )
            }
        }

        /**
         * GET /api/v1/shipping/labels/order/{orderId}
         *
         * Get the shipping label for an order
         */
        get("/labels/order/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            val label = shippingService.getLabelByOrderId(orderId, tenantId)
            if (label != null) {
                call.respond(label)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No label found for this order")
                )
            }
        }

        /**
         * GET /api/v1/shipping/labels/order/{orderId}/history
         *
         * Get the label history for an order
         */
        get("/labels/order/{orderId}/history") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            val history = shippingService.getLabelHistory(orderId, tenantId)
            call.respond(mapOf("history" to history))
        }

        /**
         * POST /api/v1/shipping/labels/{id}/void
         *
         * Void a shipping label and request refund
         */
        post("/labels/{id}/void") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val labelId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid label ID")
                )

            shippingService.voidLabel(labelId, tenantId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to void label"))
                    )
                }
        }

        /**
         * POST /api/v1/shipping/labels/order/{orderId}/void
         *
         * Void the label for a specific order
         */
        post("/labels/order/{orderId}/void") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            shippingService.voidLabelByOrderId(orderId, tenantId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to void label"))
                    )
                }
        }

        // =====================================================
        // TRACKING
        // =====================================================

        /**
         * GET /api/v1/shipping/tracking/{code}
         *
         * Get tracking information for a shipment
         */
        get("/tracking/{code}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val trackingCode = call.parameters["code"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tracking code required")
                )

            val carrier = call.request.queryParameters["carrier"]

            shippingService.getTrackingInfo(tenantId, trackingCode, carrier)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to get tracking info"))
                    )
                }
        }

        /**
         * POST /api/v1/shipping/tracking/order/{orderId}/update
         *
         * Update tracking status for an order
         */
        post("/tracking/order/{orderId}/update") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            shippingService.updateTrackingStatus(orderId, tenantId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to update tracking"))
                    )
                }
        }

        // =====================================================
        // QUICK BUY LABEL (Combined rate + buy)
        // =====================================================

        /**
         * POST /api/v1/shipping/buy-label/{orderId}
         *
         * Quick buy label for an order using selected or cheapest rate
         */
        post("/buy-label/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid order ID")
                )

            val request = try {
                call.receive<BuyLabelRequest>()
            } catch (e: Exception) {
                BuyLabelRequest()
            }

            // If rate and shipment ID provided, use them
            if (request.rateId != null && request.shipmentId != null) {
                shippingService.createShippingLabel(
                    orderId = orderId,
                    tenantId = tenantId,
                    rateId = request.rateId,
                    shipmentId = request.shipmentId
                )
                    .onSuccess { response ->
                        call.respond(HttpStatusCode.Created, response)
                    }
                    .onFailure { error ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (error.message ?: "Label creation failed"))
                        )
                    }
                return@post
            }

            // Otherwise, get rates first and use the cheapest or preferred
            shippingService.getAvailableRates(orderId, tenantId)
                .onSuccess { rateResponse ->
                    val rates = rateResponse.rates
                    if (rates.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "No rates available for this shipment")
                        )
                        return@onSuccess
                    }

                    // Find preferred rate or use cheapest
                    val selectedRate = if (request.preferredCarrier != null) {
                        rates.find { it.carrier.equals(request.preferredCarrier, ignoreCase = true) }
                            ?: rates.first()
                    } else if (request.preferredService != null) {
                        rates.find { it.service.contains(request.preferredService, ignoreCase = true) }
                            ?: rates.first()
                    } else {
                        rates.first() // Already sorted by price
                    }

                    shippingService.createShippingLabel(
                        orderId = orderId,
                        tenantId = tenantId,
                        rateId = selectedRate.id,
                        shipmentId = rateResponse.shipmentId
                    )
                        .onSuccess { response ->
                            call.respond(HttpStatusCode.Created, response)
                        }
                        .onFailure { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Label creation failed"))
                            )
                        }
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to get rates"))
                    )
                }
        }
    }
}

// =====================================================
// REQUEST DTOs
// =====================================================

@Serializable
private data class BuyLabelRequest(
    val rateId: String? = null,
    val shipmentId: String? = null,
    val preferredCarrier: String? = null,
    val preferredService: String? = null
)

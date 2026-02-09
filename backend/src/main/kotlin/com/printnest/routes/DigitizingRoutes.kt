package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.DigitizingService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.digitizingRoutes() {
    val digitizingService: DigitizingService = GlobalContext.get().get()

    route("/digitizing") {

        // =====================================================
        // DIGITIZING ORDERS
        // =====================================================

        route("/orders") {

            // GET /api/v1/digitizing/orders - List all digitizing orders
            get {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val filters = DigitizingOrderFilters(
                    page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                    limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                    status = call.request.queryParameters["status"]?.toIntOrNull(),
                    digitizerId = call.request.queryParameters["digitizerId"]?.toLongOrNull(),
                    userId = call.request.queryParameters["userId"]?.toLongOrNull(),
                    search = call.request.queryParameters["search"],
                    startDate = call.request.queryParameters["startDate"],
                    endDate = call.request.queryParameters["endDate"],
                    sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                    sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
                )

                val response = digitizingService.getDigitizingOrders(tenantId, filters)
                call.respond(response)
            }

            // POST /api/v1/digitizing/orders - Create a new digitizing order
            post {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

                val request = call.receive<CreateDigitizingOrderRequest>()

                digitizingService.createDigitizingOrderDirect(tenantId, userId, request)
                    .onSuccess { order ->
                        call.respond(HttpStatusCode.Created, order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // GET /api/v1/digitizing/orders/pending - Get pending orders (for digitizers)
            get("/pending") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val orders = digitizingService.getPendingOrders(tenantId)
                call.respond(mapOf("orders" to orders))
            }

            // GET /api/v1/digitizing/orders/my - Get current user's digitizing orders
            get("/my") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

                val orders = digitizingService.getOrdersByUser(userId, tenantId)
                call.respond(mapOf("orders" to orders))
            }

            // GET /api/v1/digitizing/orders/assigned - Get orders assigned to current digitizer
            get("/assigned") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val digitizerId = call.request.headers["X-User-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

                val orders = digitizingService.getOrdersByDigitizer(digitizerId, tenantId)
                call.respond(mapOf("orders" to orders))
            }

            // GET /api/v1/digitizing/orders/stats - Get order statistics
            get("/stats") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val stats = digitizingService.getOrderStats(tenantId)
                call.respond(mapOf("stats" to stats))
            }

            // GET /api/v1/digitizing/orders/{id} - Get single digitizing order
            get("/{id}") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                digitizingService.getDigitizingOrder(id, tenantId)
                    .onSuccess { order ->
                        call.respond(order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                    }
            }

            // PUT /api/v1/digitizing/orders/{id}/status - Update order status
            put("/{id}/status") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                val request = call.receive<UpdateDigitizingStatusRequest>()

                digitizingService.updateDigitizingStatus(id, tenantId, request.status, request.requestedPrice)
                    .onSuccess { order ->
                        call.respond(order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // POST /api/v1/digitizing/orders/{id}/accept - Accept task (for digitizers)
            post("/{id}/accept") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val digitizerId = call.request.headers["X-User-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                val body = call.receive<Map<String, Double>>()
                val requestedPrice = body["requestedPrice"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Requested price is required"))

                digitizingService.acceptTask(id, tenantId, digitizerId, requestedPrice)
                    .onSuccess { order ->
                        call.respond(order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // POST /api/v1/digitizing/orders/{id}/assign - Assign digitizer to order
            post("/{id}/assign") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                val request = call.receive<AssignDigitizerRequest>()

                digitizingService.assignDigitizer(id, tenantId, request.digitizerId)
                    .onSuccess { order ->
                        call.respond(order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // POST /api/v1/digitizing/orders/{id}/upload - Upload digitized result files
            post("/{id}/upload") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                val request = call.receive<UploadDigitizedFileRequest>()

                digitizingService.uploadDigitizedFile(id, tenantId, request)
                    .onSuccess { order ->
                        call.respond(order)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // POST /api/v1/digitizing/orders/{id}/cancel - Cancel order
            post("/{id}/cancel") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                digitizingService.cancelOrder(id, tenantId)
                    .onSuccess { cancelled ->
                        if (cancelled) {
                            call.respond(mapOf("success" to true, "message" to "Order cancelled successfully"))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "message" to "Failed to cancel order"))
                        }
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // POST /api/v1/digitizing/orders/{id}/pay - Complete payment
            post("/{id}/pay") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

                val request = call.receive<CompletePaymentRequest>()

                // TODO: Get actual paid amount from payment processing
                val paidAmount = 25.00 // Default price, would come from Stripe or balance check

                digitizingService.completePayment(id, tenantId, userId, request.paymentMethod, paidAmount)
                    .onSuccess { order ->
                        call.respond(CompletePaymentResponse(
                            success = true,
                            message = "Payment completed successfully",
                            orderId = order.id,
                            newStatus = order.status
                        ))
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, CompletePaymentResponse(
                            success = false,
                            message = error.message ?: "Payment failed"
                        ))
                    }
            }
        }

        // =====================================================
        // QUOTE
        // =====================================================

        // POST /api/v1/digitizing/quote - Get price quote
        post("/quote") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<DigitizingQuoteRequest>()

            digitizingService.getDigitizingQuote(tenantId, userId, request)
                .onSuccess { quote ->
                    call.respond(quote)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // THREAD COLORS
        // =====================================================

        route("/colors") {

            // GET /api/v1/digitizing/colors - Get all thread colors
            get {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val inStockOnly = call.request.queryParameters["inStockOnly"]?.toBoolean() ?: false

                val response = digitizingService.getEmbroideryColors(tenantId, inStockOnly)
                call.respond(response)
            }

            // POST /api/v1/digitizing/colors - Create thread color
            post {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val request = call.receive<CreateThreadColorRequest>()

                digitizingService.createThreadColor(tenantId, request)
                    .onSuccess { color ->
                        call.respond(HttpStatusCode.Created, color)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }

            // PUT /api/v1/digitizing/colors/{id} - Update thread color
            put("/{id}") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid color ID"))

                val request = call.receive<UpdateThreadColorRequest>()

                digitizingService.updateThreadColor(id, tenantId, request)
                    .onSuccess { color ->
                        call.respond(color)
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                    }
            }

            // DELETE /api/v1/digitizing/colors/{id} - Delete thread color
            delete("/{id}") {
                val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid color ID"))

                digitizingService.deleteThreadColor(id, tenantId)
                    .onSuccess { deleted ->
                        if (deleted) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Color not found"))
                        }
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
            }
        }

        // =====================================================
        // DIGITIZERS
        // =====================================================

        // GET /api/v1/digitizing/digitizers - Get available digitizers
        get("/digitizers") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val response = digitizingService.getDigitizers(tenantId)
            call.respond(response)
        }

        // =====================================================
        // FILE PARSING
        // =====================================================

        // POST /api/v1/digitizing/parse - Parse embroidery file info
        post("/parse") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, String>>()
            val fileUrl = body["fileUrl"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "File URL is required"))

            digitizingService.parseEmbroideryFile(fileUrl)
                .onSuccess { info ->
                    call.respond(info)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // HELPER ENDPOINTS
        // =====================================================

        // GET /api/v1/digitizing/statuses - Get available status values
        get("/statuses") {
            val statuses = DigitizingStatus.entries.map { status ->
                mapOf(
                    "code" to status.code,
                    "label" to status.label,
                    "name" to status.name
                )
            }
            call.respond(mapOf("statuses" to statuses))
        }
    }
}

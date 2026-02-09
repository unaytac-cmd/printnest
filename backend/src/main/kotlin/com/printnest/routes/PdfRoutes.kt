package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.PdfService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.pdfRoutes() {
    val pdfService: PdfService = GlobalContext.get().get()

    route("/pdf") {

        // =====================================================
        // PACKING SLIP ENDPOINTS
        // =====================================================

        /**
         * POST /api/v1/pdf/packing-slip/{orderId}
         * Generate a packing slip PDF for a single order
         */
        post("/packing-slip/{orderId}") {
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

            // Parse optional options from request body
            val options = try {
                call.receiveNullable<PdfGenerationOptions>() ?: PdfGenerationOptions()
            } catch (e: Exception) {
                PdfGenerationOptions()
            }

            pdfService.generatePackingSlip(orderId, tenantId, options)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to generate packing slip"))
                    )
                }
        }

        /**
         * POST /api/v1/pdf/packing-slips
         * Generate packing slips for multiple orders (bulk)
         * Request body: BulkPackingSlipRequest
         */
        post("/packing-slips") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val request = try {
                call.receive<BulkPackingSlipRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body: ${e.message}")
                )
            }

            if (request.orderIds.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "At least one order ID is required")
                )
            }

            if (request.orderIds.size > 100) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Maximum 100 orders allowed per request")
                )
            }

            val options = PdfGenerationOptions(
                includeLabel = request.includeLabels,
                groupByModification = request.groupByModification
            )

            pdfService.generateBulkPackingSlips(
                orderIds = request.orderIds,
                tenantId = tenantId,
                options = options,
                generateZip = request.generateZip
            )
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to generate packing slips"))
                    )
                }
        }

        // =====================================================
        // INVOICE ENDPOINTS
        // =====================================================

        /**
         * POST /api/v1/pdf/invoice/{orderId}
         * Generate an invoice PDF for a single order
         */
        post("/invoice/{orderId}") {
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

            val options = try {
                call.receiveNullable<PdfGenerationOptions>() ?: PdfGenerationOptions()
            } catch (e: Exception) {
                PdfGenerationOptions()
            }

            pdfService.generateInvoice(orderId, tenantId, options)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to generate invoice"))
                    )
                }
        }

        // =====================================================
        // SHIPPING LABEL ENDPOINTS
        // =====================================================

        /**
         * POST /api/v1/pdf/label/{orderId}
         * Generate a shipping label PDF for an order
         */
        post("/label/{orderId}") {
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

            val options = try {
                call.receiveNullable<PdfGenerationOptions>() ?: PdfGenerationOptions()
            } catch (e: Exception) {
                PdfGenerationOptions()
            }

            pdfService.generateShippingLabel(orderId, tenantId, options)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: "Failed to generate shipping label"))
                    )
                }
        }

        // =====================================================
        // DOWNLOAD ENDPOINTS
        // =====================================================

        /**
         * GET /api/v1/pdf/download/{fileId}
         * Get download URL for a generated PDF
         */
        get("/download/{fileId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val fileId = call.parameters["fileId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "File ID required")
                )

            val downloadInfo = pdfService.getDownloadInfo(fileId, tenantId)

            if (downloadInfo != null) {
                call.respond(HttpStatusCode.OK, downloadInfo)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "File not found or expired")
                )
            }
        }

        // =====================================================
        // GENERIC GENERATION ENDPOINT
        // =====================================================

        /**
         * POST /api/v1/pdf/generate
         * Generic PDF generation endpoint
         * Request body: PdfGenerationRequest
         */
        post("/generate") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Tenant ID required")
                )

            val request = try {
                call.receive<PdfGenerationRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body: ${e.message}")
                )
            }

            when (request.type) {
                PdfType.PACKING_SLIP -> {
                    val orderId = request.orderId
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Order ID required for packing slip")
                        )

                    pdfService.generatePackingSlip(orderId, tenantId, request.options)
                        .onSuccess { response ->
                            call.respond(HttpStatusCode.OK, response)
                        }
                        .onFailure { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to generate packing slip"))
                            )
                        }
                }

                PdfType.BULK_PACKING_SLIPS -> {
                    val orderIds = request.orderIds
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Order IDs required for bulk packing slips")
                        )

                    pdfService.generateBulkPackingSlips(orderIds, tenantId, request.options)
                        .onSuccess { response ->
                            call.respond(HttpStatusCode.OK, response)
                        }
                        .onFailure { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to generate packing slips"))
                            )
                        }
                }

                PdfType.INVOICE -> {
                    val orderId = request.orderId
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Order ID required for invoice")
                        )

                    pdfService.generateInvoice(orderId, tenantId, request.options)
                        .onSuccess { response ->
                            call.respond(HttpStatusCode.OK, response)
                        }
                        .onFailure { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to generate invoice"))
                            )
                        }
                }

                PdfType.SHIPPING_LABEL -> {
                    val orderId = request.orderId
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Order ID required for shipping label")
                        )

                    pdfService.generateShippingLabel(orderId, tenantId, request.options)
                        .onSuccess { response ->
                            call.respond(HttpStatusCode.OK, response)
                        }
                        .onFailure { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to generate shipping label"))
                            )
                        }
                }

                PdfType.BULK_INVOICE -> {
                    // TODO: Implement bulk invoice generation for period summaries
                    call.respond(
                        HttpStatusCode.NotImplemented,
                        mapOf("error" to "Bulk invoice generation not yet implemented")
                    )
                }
            }
        }
    }
}

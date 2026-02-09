package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.ExcelService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.exportRoutes() {
    val excelService: ExcelService = GlobalContext.get().get()

    route("/export") {
        // =====================================================
        // ORDERS EXPORT
        // =====================================================

        // POST /api/v1/export/orders - Export orders to Excel
        post("/orders") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportRequest>()

            // Get order IDs from request
            val orderIds = request.orderIds
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order IDs required"))

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order IDs list cannot be empty"))
            }

            excelService.createOrdersExport(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExportResponse(
                            success = false,
                            message = error.message ?: "Failed to create export"
                        )
                    )
                }
        }

        // =====================================================
        // PRODUCTS EXPORT
        // =====================================================

        // POST /api/v1/export/products - Export products to Excel
        post("/products") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportRequest>()

            excelService.createProductsExport(tenantId, userId, request.filters)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExportResponse(
                            success = false,
                            message = error.message ?: "Failed to create export"
                        )
                    )
                }
        }

        // =====================================================
        // TRANSACTIONS EXPORT
        // =====================================================

        // POST /api/v1/export/transactions - Export transactions to Excel
        post("/transactions") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportRequest>()

            // Target user ID for transactions (optional, defaults to requesting user)
            val targetUserId = request.filters?.userId ?: userId

            excelService.createTransactionsExport(tenantId, userId, targetUserId, request.filters)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExportResponse(
                            success = false,
                            message = error.message ?: "Failed to create export"
                        )
                    )
                }
        }

        // =====================================================
        // DESIGNS EXPORT
        // =====================================================

        // POST /api/v1/export/designs - Export designs to Excel
        post("/designs") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportRequest>()

            excelService.createDesignsExport(tenantId, userId, request.filters)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExportResponse(
                            success = false,
                            message = error.message ?: "Failed to create export"
                        )
                    )
                }
        }

        // =====================================================
        // OUT OF STOCK EXPORT
        // =====================================================

        // POST /api/v1/export/out-of-stock - Export out of stock products
        post("/out-of-stock") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ExportRequest>()

            val orderIds = request.orderIds
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order IDs required"))

            if (orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order IDs list cannot be empty"))
            }

            excelService.createOutOfStockExport(tenantId, userId, orderIds)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExportResponse(
                            success = false,
                            message = error.message ?: "Failed to create export"
                        )
                    )
                }
        }

        // =====================================================
        // DOWNLOAD EXPORT
        // =====================================================

        // GET /api/v1/export/download/{exportId} - Get download URL for export
        get("/download/{exportId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val exportId = call.parameters["exportId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid export ID"))

            val response = excelService.getDownloadUrl(tenantId, exportId)

            if (response.success) {
                call.respond(response)
            } else {
                call.respond(HttpStatusCode.NotFound, response)
            }
        }

        // =====================================================
        // EXPORT HISTORY
        // =====================================================

        // GET /api/v1/export/history - Get export history
        get("/history") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()

            // Optional: filter by user (admin can see all, regular user sees only their exports)
            val filterUserId = call.request.queryParameters["userId"]?.toLongOrNull() ?: userId

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

            val response = excelService.getExportHistory(tenantId, filterUserId, page, limit)
            call.respond(response)
        }

        // GET /api/v1/export/{exportId} - Get export details
        get("/{exportId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val exportId = call.parameters["exportId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid export ID"))

            val export = excelService.getExportById(tenantId, exportId)

            if (export != null) {
                call.respond(export)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Export not found"))
            }
        }
    }
}

package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.BatchService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.batchRoutes() {
    val batchService: BatchService = GlobalContext.get().get()

    route("/batches") {
        // =====================================================
        // BATCH LISTING & STATS
        // =====================================================

        // GET /api/v1/batches - List all batches
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val filters = BatchFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                search = call.request.queryParameters["search"],
                hasGangsheet = call.request.queryParameters["hasGangsheet"]?.toBooleanStrictOrNull(),
                sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = batchService.getBatches(tenantId, filters)
            call.respond(response)
        }

        // GET /api/v1/batches/stats - Get batch statistics
        get("/stats") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val stats = batchService.getBatchStats(tenantId)
            call.respond(stats)
        }

        // GET /api/v1/batches/status/{status} - Get batches by status
        get("/status/{status}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val statusCode = call.parameters["status"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid status"))

            val status = BatchStatus.fromCode(statusCode)
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

            val response = batchService.getBatchesByStatus(tenantId, status, page, limit)
            call.respond(response)
        }

        // =====================================================
        // BATCH CRUD
        // =====================================================

        // POST /api/v1/batches - Create new batch
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateBatchRequest>()

            if (request.name.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Batch name is required"))
            }

            batchService.createBatch(tenantId, request.name, request.orderIds)
                .onSuccess { batch ->
                    call.respond(HttpStatusCode.Created, BatchResponse(batch = batch, message = "Batch created successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/batches/{id} - Get batch by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            val withOrders = call.request.queryParameters["withOrders"]?.toBoolean() ?: true
            val batch = batchService.getBatch(id, tenantId, withOrders)

            if (batch != null) {
                call.respond(batch)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Batch not found"))
            }
        }

        // PUT /api/v1/batches/{id} - Update batch
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            val request = call.receive<UpdateBatchRequest>()

            batchService.updateBatch(id, tenantId, userId, request)
                .onSuccess { batch ->
                    call.respond(BatchResponse(batch = batch, message = "Batch updated successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/batches/{id} - Delete batch
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            batchService.deleteBatch(id, tenantId, userId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Batch not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // BATCH HISTORY
        // =====================================================

        // GET /api/v1/batches/{id}/history - Get batch history
        get("/{id}/history") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            // Verify batch belongs to tenant
            val batch = batchService.getBatch(id, tenantId, false)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Batch not found"))

            val history = batchService.getBatchHistory(id)
            call.respond(mapOf("history" to history))
        }

        // =====================================================
        // ORDER MANAGEMENT IN BATCH
        // =====================================================

        // POST /api/v1/batches/{id}/orders - Add orders to batch
        post("/{id}/orders") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            val request = call.receive<AddOrdersToBatchRequest>()

            if (request.orderIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order IDs required"))
            }

            batchService.addOrdersToBatch(id, tenantId, userId, request.orderIds)
                .onSuccess { batch ->
                    call.respond(BatchResponse(batch = batch, message = "Orders added to batch"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/batches/{id}/orders/{orderId} - Remove order from batch
        delete("/{id}/orders/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            batchService.removeOrderFromBatch(id, tenantId, userId, orderId)
                .onSuccess { batch ->
                    if (batch != null) {
                        call.respond(BatchResponse(batch = batch, message = "Order removed from batch"))
                    } else {
                        // Batch was deleted because no orders left
                        call.respond(mapOf("message" to "Batch deleted as it had no remaining orders"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // BATCH PROCESSING
        // =====================================================

        // POST /api/v1/batches/{id}/process - Start gangsheet generation for batch
        post("/{id}/process") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            val request = try {
                call.receive<ProcessBatchRequest>()
            } catch (e: Exception) {
                ProcessBatchRequest()
            }

            batchService.processBatch(id, tenantId, userId, request.gangsheetSettings)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.Accepted, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/batches/{id}/ready - Mark batch as ready for processing
        post("/{id}/ready") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batch ID"))

            batchService.updateBatch(id, tenantId, userId, UpdateBatchRequest(status = BatchStatus.READY.code))
                .onSuccess { batch ->
                    call.respond(BatchResponse(batch = batch, message = "Batch marked as ready"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // COMBINE ORDERS
        // =====================================================

        // POST /api/v1/batches/combine - Combine multiple orders into one
        post("/combine") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CombineBatchOrdersRequest>()

            if (request.orderIds.size < 2) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "At least 2 orders required to combine"))
            }

            batchService.combineOrders(tenantId, userId, request.orderIds, request.primaryOrderId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // FIND BATCHES FOR ORDER
        // =====================================================

        // GET /api/v1/batches/for-order/{orderId} - Find batches containing an order
        get("/for-order/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val batches = batchService.findBatchesForOrder(orderId, tenantId)
            call.respond(mapOf("batches" to batches))
        }
    }
}

package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.DesignService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.designRoutes() {
    val designService: DesignService = GlobalContext.get().get()

    route("/designs") {

        // =====================================================
        // LIST & SEARCH
        // =====================================================

        // GET /api/v1/designs - List all designs
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val filters = DesignFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                designType = call.request.queryParameters["designType"]?.toIntOrNull(),
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                search = call.request.queryParameters["search"],
                userId = call.request.queryParameters["userId"]?.toLongOrNull(),
                sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = designService.getDesigns(tenantId, filters)
            call.respond(response)
        }

        // GET /api/v1/designs/types - Get available design types
        get("/types") {
            val types = designService.getDesignTypes()
            call.respond(mapOf("types" to types))
        }

        // =====================================================
        // UPLOAD FLOW
        // =====================================================

        // POST /api/v1/designs/upload-url - Get pre-signed upload URL
        post("/upload-url") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<UploadUrlRequest>()

            designService.generateUploadUrl(tenantId, userId, request)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/designs/complete-upload - Complete upload and create design
        post("/complete-upload") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CompleteUploadRequest>()

            designService.completeUpload(tenantId, userId, request)
                .onSuccess { design ->
                    call.respond(HttpStatusCode.Created, design)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // CRUD
        // =====================================================

        // POST /api/v1/designs - Create design directly (for external URLs)
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CreateDesignRequest>()

            designService.createDesign(tenantId, userId, request)
                .onSuccess { design ->
                    call.respond(HttpStatusCode.Created, design)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/designs/{id} - Get single design
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid design ID"))

            designService.getDesign(id, tenantId)
                .onSuccess { design ->
                    call.respond(design)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/designs/{id} - Update design
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid design ID"))

            val request = call.receive<UpdateDesignRequest>()

            designService.updateDesign(id, tenantId, request)
                .onSuccess { design ->
                    call.respond(design)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/designs/{id} - Delete design
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid design ID"))

            designService.deleteDesign(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Design not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // BULK OPERATIONS
        // =====================================================

        // POST /api/v1/designs/bulk-delete - Delete multiple designs
        post("/bulk-delete") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<BulkDeleteRequest>()

            if (request.designIds.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No design IDs provided"))
            }

            val response = designService.bulkDelete(tenantId, request)
            call.respond(response)
        }

        // =====================================================
        // UTILITIES
        // =====================================================

        // POST /api/v1/designs/check-duplicate - Check if design already exists
        post("/check-duplicate") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, String>>()
            val fileHash = body["fileHash"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "File hash required"))

            val response = designService.checkDuplicate(tenantId, fileHash)
            call.respond(response)
        }

        // GET /api/v1/designs/by-ids - Get multiple designs by IDs
        get("/by-ids") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val ids = call.request.queryParameters["ids"]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "IDs required"))

            val designs = designService.getDesignsByIds(ids, tenantId)
            call.respond(mapOf("designs" to designs))
        }
    }
}

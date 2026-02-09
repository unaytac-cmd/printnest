package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.GangsheetService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.gangsheetRoutes() {
    val gangsheetService: GangsheetService = GlobalContext.get().get()

    route("/gangsheets") {

        // =====================================================
        // GET /api/v1/gangsheets - List all gangsheets
        // =====================================================
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val filters = GangsheetFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                search = call.request.queryParameters["search"],
                startDate = call.request.queryParameters["startDate"],
                endDate = call.request.queryParameters["endDate"],
                sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = gangsheetService.getGangsheets(tenantId, filters)
            call.respond(response)
        }

        // =====================================================
        // POST /api/v1/gangsheets - Create new gangsheet
        // =====================================================
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateGangsheetRequest>()

            gangsheetService.createGangsheet(tenantId, request)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // GET /api/v1/gangsheets/settings - Get default settings
        // =====================================================
        get("/settings") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val settings = gangsheetService.getDefaultSettings(tenantId)
            call.respond(mapOf("settings" to settings))
        }

        // =====================================================
        // PUT /api/v1/gangsheets/settings - Update default settings
        // =====================================================
        put("/settings") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val settings = call.receive<GangsheetSettingsFull>()

            gangsheetService.updateDefaultSettings(tenantId, settings)
                .onSuccess { updatedSettings ->
                    call.respond(mapOf("settings" to updatedSettings, "message" to "Settings updated"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // GET /api/v1/gangsheets/{id} - Get gangsheet by ID
        // =====================================================
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid gangsheet ID"))

            val withRolls = call.request.queryParameters["withRolls"]?.toBoolean() ?: true
            val gangsheet = gangsheetService.getGangsheet(id, tenantId, withRolls)

            if (gangsheet != null) {
                call.respond(gangsheet)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Gangsheet not found"))
            }
        }

        // =====================================================
        // GET /api/v1/gangsheets/{id}/status - Check gangsheet status
        // =====================================================
        get("/{id}/status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid gangsheet ID"))

            gangsheetService.getGangsheetStatus(id, tenantId)
                .onSuccess { status ->
                    call.respond(status)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // GET /api/v1/gangsheets/{id}/download - Get download URL
        // =====================================================
        get("/{id}/download") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid gangsheet ID"))

            gangsheetService.downloadGangsheet(id, tenantId)
                .onSuccess { downloadResponse ->
                    call.respond(downloadResponse)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalArgumentException -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
                    }
                }
        }

        // =====================================================
        // DELETE /api/v1/gangsheets/{id} - Delete gangsheet
        // =====================================================
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid gangsheet ID"))

            gangsheetService.deleteGangsheet(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Gangsheet not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // POST /api/v1/gangsheets/preview - Preview placement
        // (Optional endpoint for UI preview without generating)
        // =====================================================
        post("/preview") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateGangsheetRequest>()

            // Get designs without creating gangsheet
            val settings = request.settings ?: gangsheetService.getDefaultSettings(tenantId)
            val designs = gangsheetService.getOrdersForGangsheet(tenantId, request.orderIds, request.products)

            if (designs.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No designs found for the selected orders"))
                return@post
            }

            // Calculate placements
            val placements = gangsheetService.calculateDesignPlacements(
                designs,
                settings.rollWidth,
                settings.rollLength,
                settings.dpi
            )

            call.respond(mapOf(
                "preview" to true,
                "settings" to settings,
                "totalDesigns" to placements.totalDesigns,
                "totalRolls" to placements.totalRolls,
                "rolls" to placements.rolls.map { roll ->
                    mapOf(
                        "rollNumber" to roll.rollNumber,
                        "designCount" to roll.placements.size,
                        "maxHeight" to roll.maxHeight,
                        "orderIds" to roll.orderIds,
                        "placements" to roll.placements
                    )
                }
            ))
        }
    }
}

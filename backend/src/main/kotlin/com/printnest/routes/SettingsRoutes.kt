package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.SettingsService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.settingsRoutes() {
    val settingsService: SettingsService = GlobalContext.get().get()

    // =====================================================
    // TENANT SETTINGS
    // =====================================================

    route("/settings") {
        // GET /api/v1/settings - Get tenant settings
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val settings = settingsService.getTenantSettings(tenantId)

            if (settings != null) {
                call.respond(settings)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Settings not found"))
            }
        }

        // PUT /api/v1/settings - Update all tenant settings (integrations, AWS, etc.)
        put {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<UpdateTenantSettingsRequest>()

            settingsService.updateTenantSettings(tenantId, request)
                .onSuccess { settings ->
                    call.respond(settings)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/settings/gangsheet - Update gangsheet settings
        put("/gangsheet") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<UpdateGangsheetSettingsRequest>()

            settingsService.updateGangsheetSettings(tenantId, request)
                .onSuccess { settings ->
                    call.respond(settings)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // ANNOUNCEMENTS
    // =====================================================

    route("/announcements") {
        // GET /api/v1/announcements - List all announcements
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
            val announcements = settingsService.getAnnouncements(tenantId, includeInactive)

            call.respond(mapOf("announcements" to announcements))
        }

        // GET /api/v1/announcements/active - Get active announcements (for dashboard)
        get("/active") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val announcements = settingsService.getActiveAnnouncements(tenantId)
            val popup = settingsService.getPopupAnnouncement(tenantId)

            call.respond(mapOf(
                "announcements" to announcements,
                "popup" to popup
            ))
        }

        // POST /api/v1/announcements - Create announcement
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateAnnouncementRequest>()

            settingsService.createAnnouncement(tenantId, request)
                .onSuccess { announcement ->
                    call.respond(HttpStatusCode.Created, announcement)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/announcements/{id} - Update announcement
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid announcement ID"))

            val request = call.receive<UpdateAnnouncementRequest>()

            settingsService.updateAnnouncement(id, tenantId, request)
                .onSuccess { announcement ->
                    call.respond(announcement)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/announcements/{id} - Delete announcement
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid announcement ID"))

            settingsService.deleteAnnouncement(id, tenantId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // =====================================================
    // REFERRAL CAMPAIGNS
    // =====================================================

    route("/referral-campaigns") {
        // GET /api/v1/referral-campaigns - List all campaigns
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
            val campaigns = settingsService.getReferralCampaigns(tenantId, includeInactive)

            call.respond(mapOf("campaigns" to campaigns))
        }

        // POST /api/v1/referral-campaigns - Create campaign
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateReferralCampaignRequest>()

            settingsService.createReferralCampaign(tenantId, request)
                .onSuccess { campaign ->
                    call.respond(HttpStatusCode.Created, campaign)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/referral-campaigns/{id} - Update campaign
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid campaign ID"))

            val request = call.receive<UpdateReferralCampaignRequest>()

            settingsService.updateReferralCampaign(id, tenantId, request)
                .onSuccess { campaign ->
                    call.respond(campaign)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // REFERRAL CREDITS
    // =====================================================

    route("/referral-credits") {
        // GET /api/v1/referral-credits/pending - Get pending credits
        get("/pending") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val credits = settingsService.getPendingReferralCredits(tenantId)
            call.respond(mapOf("credits" to credits))
        }

        // GET /api/v1/referral-credits/user/{userId} - Get credits for user
        get("/user/{userId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.parameters["userId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))

            val credits = settingsService.getReferralCreditsByUser(userId, tenantId)
            call.respond(mapOf("credits" to credits))
        }

        // POST /api/v1/referral-credits/process - Process pending credits
        post("/process") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<ProcessReferralCreditsRequest>()

            settingsService.processReferralCredits(tenantId, request)
                .onSuccess { count ->
                    call.respond(mapOf("processed" to count))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // EMBROIDERY COLORS
    // =====================================================

    route("/embroidery-colors") {
        // GET /api/v1/embroidery-colors - List all colors
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val colors = settingsService.getEmbroideryColors(tenantId)
            call.respond(mapOf("colors" to colors))
        }

        // POST /api/v1/embroidery-colors - Create color
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateEmbroideryColorRequest>()

            settingsService.createEmbroideryColor(tenantId, request)
                .onSuccess { color ->
                    call.respond(HttpStatusCode.Created, color)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/embroidery-colors/{id} - Update color
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid color ID"))

            val request = call.receive<UpdateEmbroideryColorRequest>()

            settingsService.updateEmbroideryColor(id, tenantId, request)
                .onSuccess { color ->
                    call.respond(color)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/embroidery-colors/{id} - Delete color
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid color ID"))

            settingsService.deleteEmbroideryColor(id, tenantId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // =====================================================
    // LABEL ADDRESSES
    // =====================================================

    route("/label-addresses") {
        // GET /api/v1/label-addresses - List all addresses
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val addresses = settingsService.getLabelAddresses(tenantId)
            call.respond(mapOf("addresses" to addresses))
        }

        // GET /api/v1/label-addresses/default - Get default address
        get("/default") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val address = settingsService.getDefaultLabelAddress(tenantId)

            if (address != null) {
                call.respond(address)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No default address found"))
            }
        }

        // POST /api/v1/label-addresses - Create address
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateLabelAddressRequest>()

            settingsService.createLabelAddress(tenantId, request)
                .onSuccess { address ->
                    call.respond(HttpStatusCode.Created, address)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/label-addresses/{id} - Update address
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid address ID"))

            val request = call.receive<UpdateLabelAddressRequest>()

            settingsService.updateLabelAddress(id, tenantId, request)
                .onSuccess { address ->
                    call.respond(address)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/label-addresses/{id} - Delete address
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid address ID"))

            settingsService.deleteLabelAddress(id, tenantId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // =====================================================
    // NOTIFICATIONS
    // =====================================================

    route("/notifications") {
        // GET /api/v1/notifications - Get user notifications
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val unreadOnly = call.request.queryParameters["unreadOnly"]?.toBoolean() ?: false

            val notifications = if (unreadOnly) {
                settingsService.getUnreadNotifications(userId, tenantId)
            } else {
                settingsService.getAllNotifications(userId, tenantId, page, limit)
            }

            call.respond(mapOf("notifications" to notifications))
        }

        // POST /api/v1/notifications/{id}/read - Mark notification as read
        post("/{id}/read") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid notification ID"))

            val marked = settingsService.markNotificationRead(id, userId, tenantId)

            if (marked) {
                call.respond(mapOf("success" to true))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Notification not found"))
            }
        }

        // POST /api/v1/notifications/read-all - Mark all as read
        post("/read-all") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val count = settingsService.markAllNotificationsRead(userId, tenantId)
            call.respond(mapOf("marked" to count))
        }
    }

    // =====================================================
    // USER PERMISSIONS
    // =====================================================

    route("/permissions") {
        // GET /api/v1/permissions - Get all available permissions
        get {
            val permissions = settingsService.getAllAvailablePermissions()
            call.respond(mapOf("permissions" to permissions))
        }

        // GET /api/v1/permissions/user/{userId} - Get user permissions
        get("/user/{userId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.parameters["userId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))

            val permissions = settingsService.getUserPermissions(userId, tenantId)
            call.respond(mapOf("permissionIds" to permissions))
        }

        // PUT /api/v1/permissions/user/{userId} - Update user permissions
        put("/user/{userId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val grantedBy = call.request.headers["X-User-Id"]?.toLongOrNull()

            val userId = call.parameters["userId"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))

            val request = call.receive<UpdateUserPermissionsRequest>()

            val permissions = settingsService.updateUserPermissions(userId, tenantId, request.permissionIds, grantedBy)
            call.respond(mapOf("permissionIds" to permissions))
        }
    }
}

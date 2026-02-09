package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.SettingsRepository
import com.printnest.domain.tables.Tenants
import com.printnest.domain.tables.Users
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.math.BigDecimal
import java.time.Instant

class SettingsService(
    private val settingsRepository: SettingsRepository
) : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // TENANT SETTINGS
    // =====================================================

    /**
     * Get a specific setting value from tenant settings JSON
     */
    fun getTenantSetting(tenantId: Long, key: String): String? = transaction {
        val settingsJson = Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.get(Tenants.settings) ?: return@transaction null

        try {
            val settings = json.decodeFromString<Map<String, String>>(settingsJson)
            settings[key]
        } catch (e: Exception) {
            null
        }
    }

    fun getTenantSettings(tenantId: Long): TenantSettingsResponse? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.let { row ->
                val settingsJson = row.getOrNull(Tenants.settings) ?: "{}"
                val storedSettings = try {
                    if (settingsJson.isNotEmpty() && settingsJson != "{}") {
                        json.decodeFromString<GangsheetSettings>(settingsJson)
                    } else null
                } catch (e: Exception) {
                    null
                }

                // Convert storage model to service model
                val gangsheetSettings = storedSettings?.let { gs ->
                    GangsheetSettingsFull(
                        rollWidth = gs.width,
                        rollLength = gs.height,
                        dpi = gs.dpi,
                        gap = gs.spacing,
                        border = true,  // Default
                        borderSize = 0.1,  // Default
                        borderColor = "red"  // Default
                    )
                } ?: GangsheetSettingsFull()

                TenantSettingsResponse(
                    tenantId = row[Tenants.id].value,
                    subdomain = row[Tenants.subdomain],
                    name = row[Tenants.name],
                    customDomain = row[Tenants.customDomain],
                    gangsheetSettings = gangsheetSettings,
                    status = row[Tenants.status],
                    createdAt = row[Tenants.createdAt].toString(),
                    updatedAt = row[Tenants.updatedAt].toString()
                )
            }
    }

    /**
     * Update all tenant settings including AWS, Stripe, ShipStation, etc.
     */
    fun updateTenantSettings(tenantId: Long, request: UpdateTenantSettingsRequest): Result<TenantSettingsResponse> {
        val updated = settingsRepository.updateTenantSettings(tenantId, request)

        return if (updated) {
            val settings = settingsRepository.getTenantSettings(tenantId)
            if (settings != null) {
                Result.success(settings)
            } else {
                Result.failure(IllegalStateException("Failed to retrieve updated settings"))
            }
        } else {
            Result.failure(IllegalStateException("Failed to update tenant settings"))
        }
    }

    fun updateGangsheetSettings(tenantId: Long, request: UpdateGangsheetSettingsRequest): Result<GangsheetSettingsFull> = transaction {
        val current = getTenantSettings(tenantId)?.gangsheetSettings ?: GangsheetSettingsFull()

        val updated = current.copy(
            rollWidth = request.rollWidth?.toDouble() ?: current.rollWidth,
            rollLength = request.rollLength?.toDouble() ?: current.rollLength,
            dpi = request.dpi ?: current.dpi,
            gap = request.gap ?: current.gap,
            border = request.border ?: current.border,
            borderSize = request.borderSize ?: current.borderSize,
            borderColor = request.borderColor ?: current.borderColor
        )

        // Validate DPI range
        if (updated.dpi < 100 || updated.dpi > 600) {
            return@transaction Result.failure(IllegalArgumentException("DPI must be between 100 and 600"))
        }

        // Convert to storage model
        val storageSettings = GangsheetSettings(
            width = updated.rollWidth,
            height = updated.rollLength,
            dpi = updated.dpi,
            spacing = updated.gap,
            backgroundColor = "#FFFFFF",
            autoArrange = true
        )

        val settingsJson = json.encodeToString(GangsheetSettings.serializer(), storageSettings)

        Tenants.update(where = { Tenants.id eq tenantId }) {
            it[settings] = settingsJson
            it[updatedAt] = Instant.now()
        }

        Result.success(updated)
    }

    // =====================================================
    // ANNOUNCEMENTS
    // =====================================================

    fun getAnnouncements(tenantId: Long, includeInactive: Boolean = false): List<Announcement> {
        return settingsRepository.findAllAnnouncements(tenantId, includeInactive)
    }

    fun getActiveAnnouncements(tenantId: Long): List<Announcement> {
        return settingsRepository.findActiveAnnouncements(tenantId)
    }

    fun getPopupAnnouncement(tenantId: Long): Announcement? {
        return getActiveAnnouncements(tenantId).find { it.showAsPopup }
    }

    fun createAnnouncement(tenantId: Long, request: CreateAnnouncementRequest): Result<Announcement> {
        if (request.title.isBlank()) {
            return Result.failure(IllegalArgumentException("Title is required"))
        }
        if (request.content.isBlank()) {
            return Result.failure(IllegalArgumentException("Content is required"))
        }

        return Result.success(settingsRepository.createAnnouncement(tenantId, request))
    }

    fun updateAnnouncement(id: Long, tenantId: Long, request: UpdateAnnouncementRequest): Result<Announcement> {
        val announcement = settingsRepository.updateAnnouncement(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Announcement not found"))

        return Result.success(announcement)
    }

    fun deleteAnnouncement(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(settingsRepository.deleteAnnouncement(id, tenantId))
    }

    // =====================================================
    // REFERRAL SYSTEM
    // =====================================================

    fun getReferralCampaigns(tenantId: Long, includeInactive: Boolean = false): List<ReferralCampaign> {
        return settingsRepository.findAllReferralCampaigns(tenantId, includeInactive)
    }

    fun createReferralCampaign(tenantId: Long, request: CreateReferralCampaignRequest): Result<ReferralCampaign> {
        // Check if code already exists
        val existing = settingsRepository.findReferralCampaignByCode(request.referralCode, tenantId)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Referral code already exists"))
        }

        return Result.success(settingsRepository.createReferralCampaign(tenantId, request))
    }

    fun updateReferralCampaign(id: Long, tenantId: Long, request: UpdateReferralCampaignRequest): Result<ReferralCampaign> {
        // Check for code conflict if updating code
        if (request.referralCode != null) {
            val existing = settingsRepository.findReferralCampaignByCode(request.referralCode, tenantId)
            if (existing != null && existing.id != id) {
                return Result.failure(IllegalArgumentException("Referral code already exists"))
            }
        }

        val campaign = settingsRepository.updateReferralCampaign(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Campaign not found"))

        return Result.success(campaign)
    }

    fun getPendingReferralCredits(tenantId: Long): List<ReferralCredit> {
        return settingsRepository.findPendingReferralCredits(tenantId)
    }

    fun getReferralCreditsByUser(userId: Long, tenantId: Long): List<ReferralCredit> {
        return settingsRepository.findReferralCreditsByReferrer(userId, tenantId)
    }

    fun processReferralCredits(tenantId: Long, request: ProcessReferralCreditsRequest): Result<Int> {
        // TODO: Also add the credits to user balances
        val processed = settingsRepository.processReferralCredits(request.creditIds, tenantId)
        return Result.success(processed)
    }

    // =====================================================
    // EMBROIDERY COLORS
    // =====================================================

    fun getEmbroideryColors(tenantId: Long): List<EmbroideryColor> {
        return settingsRepository.findAllEmbroideryColors(tenantId)
    }

    fun createEmbroideryColor(tenantId: Long, request: CreateEmbroideryColorRequest): Result<EmbroideryColor> {
        if (request.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (!request.hexColor.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            return Result.failure(IllegalArgumentException("Invalid hex color format"))
        }

        return Result.success(settingsRepository.createEmbroideryColor(tenantId, request))
    }

    fun updateEmbroideryColor(id: Long, tenantId: Long, request: UpdateEmbroideryColorRequest): Result<EmbroideryColor> {
        if (request.hexColor != null && !request.hexColor.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            return Result.failure(IllegalArgumentException("Invalid hex color format"))
        }

        val color = settingsRepository.updateEmbroideryColor(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Color not found"))

        return Result.success(color)
    }

    fun deleteEmbroideryColor(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(settingsRepository.deleteEmbroideryColor(id, tenantId))
    }

    // =====================================================
    // LABEL ADDRESSES
    // =====================================================

    fun getLabelAddresses(tenantId: Long): List<LabelAddress> {
        return settingsRepository.findAllLabelAddresses(tenantId)
    }

    fun getDefaultLabelAddress(tenantId: Long): LabelAddress? {
        return settingsRepository.findDefaultLabelAddress(tenantId)
    }

    fun createLabelAddress(tenantId: Long, request: CreateLabelAddressRequest): Result<LabelAddress> {
        if (request.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (request.street1.isBlank()) {
            return Result.failure(IllegalArgumentException("Street address is required"))
        }
        if (request.city.isBlank()) {
            return Result.failure(IllegalArgumentException("City is required"))
        }
        if (request.postalCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Postal code is required"))
        }

        return Result.success(settingsRepository.createLabelAddress(tenantId, request))
    }

    fun updateLabelAddress(id: Long, tenantId: Long, request: UpdateLabelAddressRequest): Result<LabelAddress> {
        val address = settingsRepository.updateLabelAddress(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Address not found"))

        return Result.success(address)
    }

    fun deleteLabelAddress(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(settingsRepository.deleteLabelAddress(id, tenantId))
    }

    // =====================================================
    // NOTIFICATIONS
    // =====================================================

    fun getUnreadNotifications(userId: Long, tenantId: Long): List<Notification> {
        return settingsRepository.findUnreadNotifications(userId, tenantId)
    }

    fun getAllNotifications(userId: Long, tenantId: Long, page: Int = 1, limit: Int = 50): List<Notification> {
        return settingsRepository.findAllNotifications(userId, tenantId, page, limit)
    }

    fun createNotification(tenantId: Long, request: CreateNotificationRequest): Notification {
        return settingsRepository.createNotification(tenantId, request)
    }

    fun markNotificationRead(id: Long, userId: Long, tenantId: Long): Boolean {
        return settingsRepository.markNotificationRead(id, userId, tenantId)
    }

    fun markAllNotificationsRead(userId: Long, tenantId: Long): Int {
        return settingsRepository.markAllNotificationsRead(userId, tenantId)
    }

    // Helper to create system notifications
    fun notifyUser(
        tenantId: Long,
        userId: Long,
        title: String,
        message: String,
        type: NotificationType = NotificationType.SYSTEM,
        priority: NotificationPriority = NotificationPriority.MEDIUM,
        url: String? = null
    ): Notification {
        return createNotification(tenantId, CreateNotificationRequest(
            userId = userId,
            notificationType = type.value,
            title = title,
            message = message,
            url = url,
            priority = priority.value
        ))
    }

    // =====================================================
    // USER PERMISSIONS
    // =====================================================

    fun getUserPermissions(userId: Long, tenantId: Long): List<Int> {
        return settingsRepository.findUserPermissions(userId, tenantId)
    }

    fun hasPermission(userId: Long, tenantId: Long, permissionId: Int): Boolean {
        return settingsRepository.hasPermission(userId, tenantId, permissionId)
    }

    fun updateUserPermissions(userId: Long, tenantId: Long, permissionIds: List<Int>, grantedBy: Long? = null): List<Int> {
        return settingsRepository.updateUserPermissions(userId, tenantId, permissionIds, grantedBy)
    }

    fun getAllAvailablePermissions(): List<Permission> {
        return settingsRepository.getAllPermissions()
    }

    // =====================================================
    // CUSTOMER MANAGEMENT
    // =====================================================

    fun assignCustomerProfiles(
        userId: Long,
        tenantId: Long,
        priceProfileId: Long? = null,
        shippingProfileId: Long? = null,
        labelAddressId: Long? = null
    ): Result<Boolean> = transaction {
        Users.update(
            where = { (Users.id eq userId) and (Users.tenantId eq tenantId) }
        ) {
            priceProfileId?.let { id -> it[Users.priceProfileId] = id }
            shippingProfileId?.let { id -> it[Users.shippingProfileId] = id }
            // labelAddressId would need column to be added to Users table
            it[Users.updatedAt] = Instant.now()
        }
        Result.success(true)
    }

    fun addCustomerBalance(
        userId: Long,
        tenantId: Long,
        amount: BigDecimal,
        description: String? = null
    ): Result<BigDecimal> = transaction {
        val currentBalance = Users.selectAll()
            .where { (Users.id eq userId) and (Users.tenantId eq tenantId) }
            .singleOrNull()
            ?.get(Users.totalCredit) ?: return@transaction Result.failure(IllegalArgumentException("User not found"))

        val newBalance = currentBalance + amount

        Users.update(
            where = { (Users.id eq userId) and (Users.tenantId eq tenantId) }
        ) {
            it[totalCredit] = newBalance
            it[updatedAt] = Instant.now()
        }

        // TODO: Create transaction record

        Result.success(newBalance)
    }
}

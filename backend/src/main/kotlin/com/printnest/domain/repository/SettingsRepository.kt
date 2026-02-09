package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class SettingsRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // TENANT SETTINGS
    // =====================================================

    /**
     * Get shipping settings for a tenant (EasyPost API key, etc.)
     */
    fun getShippingSettings(tenantId: Long): com.printnest.domain.models.ShippingSettings? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.let { row ->
                val settingsJson = row[Tenants.settings]
                try {
                    if (settingsJson.isNotEmpty() && settingsJson != "{}") {
                        val tenantSettings = json.decodeFromString<com.printnest.domain.models.TenantSettings>(settingsJson)
                        tenantSettings.shipping
                    } else null
                } catch (e: Exception) { null }
            }
    }

    /**
     * Get tenant by ID with settings for PDF generation
     */
    fun getTenantById(tenantId: Long): TenantForPdf? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.let { row ->
                val settingsJson = row[Tenants.settings]
                val settings = try {
                    if (settingsJson.isNotEmpty() && settingsJson != "{}") {
                        json.decodeFromString<com.printnest.domain.models.TenantSettings>(settingsJson)
                    } else null
                } catch (e: Exception) { null }

                // Get default label address for company address
                val labelAddress = findDefaultLabelAddress(tenantId)

                TenantForPdf(
                    id = row[Tenants.id].value,
                    name = row[Tenants.name],
                    settings = TenantPdfSettings(
                        logoUrl = settings?.logoUrl,
                        address = labelAddress?.let { addr ->
                            com.printnest.domain.models.Address(
                                name = addr.name,
                                street1 = addr.street1,
                                street2 = addr.street2,
                                city = addr.city,
                                state = addr.state,
                                postalCode = addr.postalCode,
                                country = addr.country
                            )
                        },
                        contactEmail = null,
                        contactPhone = labelAddress?.phone,
                        website = row[Tenants.customDomain],
                        taxId = settings?.detailedSettings?.taxId
                    )
                )
            }
    }

    /**
     * Get a specific setting value from tenant settings
     * Supported keys: stripe_secret_key, stripe_public_key, stripe_webhook_secret,
     * shipstation_api_key, shipstation_api_secret
     */
    fun getTenantSetting(tenantId: Long, key: String): String? = transaction {
        val settingsJson = Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.get(Tenants.settings) ?: return@transaction null

        try {
            if (settingsJson.isEmpty() || settingsJson == "{}") return@transaction null

            val settings = json.decodeFromString<com.printnest.domain.models.TenantSettings>(settingsJson)

            when (key) {
                "stripe_secret_key" -> settings.stripe?.secretKey
                "stripe_public_key" -> settings.stripe?.publicKey
                "stripe_webhook_secret" -> settings.stripe?.webhookSecret
                "shipstation_api_key" -> settings.shipstation?.apiKey
                "shipstation_api_secret" -> settings.shipstation?.apiSecret
                "aws_access_key_id" -> settings.aws?.accessKeyId
                "aws_secret_access_key" -> settings.aws?.secretAccessKey
                "aws_region" -> settings.aws?.region
                "aws_s3_bucket" -> settings.aws?.s3Bucket
                "nestshipper_api_key" -> settings.shipping?.nestshipperApiKey
                "easypost_api_key" -> settings.shipping?.easypostApiKey
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getTenantSettings(tenantId: Long): TenantSettingsResponse? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.let { row ->
                val settingsJson = row[Tenants.settings]
                val settings = try {
                    if (settingsJson.isNotEmpty() && settingsJson != "{}") {
                        json.decodeFromString<com.printnest.domain.models.TenantSettings>(settingsJson)
                    } else null
                } catch (e: Exception) { null }

                TenantSettingsResponse(
                    tenantId = row[Tenants.id].value,
                    subdomain = row[Tenants.subdomain],
                    name = row[Tenants.name],
                    customDomain = row[Tenants.customDomain],
                    logoUrl = settings?.logoUrl,
                    emailApiKey = null,
                    gangsheetSettings = settings?.gangsheet?.let { gs ->
                        GangsheetSettingsFull(
                            rollWidth = gs.width,
                            rollLength = gs.height,
                            dpi = gs.dpi,
                            gap = gs.spacing,
                            border = false,
                            borderSize = 0.1,
                            borderColor = "red"
                        )
                    } ?: GangsheetSettingsFull(),
                    detailedSettings = DetailedSettings(
                        defaultPriceProfileId = settings?.detailedSettings?.defaultPriceProfileId,
                        defaultShippingProfileId = settings?.detailedSettings?.defaultShippingProfileId,
                        defaultLabelAddressId = settings?.detailedSettings?.defaultLabelAddressId
                    ),
                    awsSettings = settings?.aws,
                    shipstationSettings = settings?.shipstation,
                    stripeSettings = settings?.stripe,
                    shippingSettings = settings?.shipping,
                    status = row[Tenants.status],
                    createdAt = row[Tenants.createdAt].toString(),
                    updatedAt = row[Tenants.updatedAt].toString()
                )
            }
    }

    /**
     * Update tenant settings with all integration settings
     */
    fun updateTenantSettings(tenantId: Long, request: UpdateTenantSettingsRequest): Boolean = transaction {
        // Get current settings
        val currentSettingsJson = Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.get(Tenants.settings) ?: "{}"

        val currentSettings = try {
            if (currentSettingsJson.isNotEmpty() && currentSettingsJson != "{}") {
                json.decodeFromString<com.printnest.domain.models.TenantSettings>(currentSettingsJson)
            } else {
                com.printnest.domain.models.TenantSettings()
            }
        } catch (e: Exception) {
            com.printnest.domain.models.TenantSettings()
        }

        // Build updated settings
        val updatedSettings = currentSettings.copy(
            gangsheet = request.gangsheetSettings ?: currentSettings.gangsheet,
            aws = request.awsSettings ?: currentSettings.aws,
            shipstation = request.shipstationSettings ?: currentSettings.shipstation,
            stripe = request.stripeSettings ?: currentSettings.stripe,
            shipping = request.shippingSettings ?: currentSettings.shipping,
            logoUrl = request.logoUrl ?: currentSettings.logoUrl
        )

        val updatedJson = json.encodeToString(com.printnest.domain.models.TenantSettings.serializer(), updatedSettings)

        Tenants.update(
            where = { Tenants.id eq tenantId }
        ) {
            request.name?.let { n -> it[name] = n }
            request.customDomain?.let { d -> it[customDomain] = d }
            it[settings] = updatedJson
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateGangsheetSettings(tenantId: Long, gangsheetSettings: com.printnest.domain.models.GangsheetSettings): Boolean = transaction {
        // Get current settings
        val currentSettingsJson = Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .singleOrNull()
            ?.get(Tenants.settings) ?: "{}"

        val currentSettings = try {
            if (currentSettingsJson.isNotEmpty() && currentSettingsJson != "{}") {
                json.decodeFromString<com.printnest.domain.models.TenantSettings>(currentSettingsJson)
            } else {
                com.printnest.domain.models.TenantSettings()
            }
        } catch (e: Exception) {
            com.printnest.domain.models.TenantSettings()
        }

        // Update gangsheet settings
        val updatedSettings = currentSettings.copy(gangsheet = gangsheetSettings)
        val updatedJson = json.encodeToString(com.printnest.domain.models.TenantSettings.serializer(), updatedSettings)

        Tenants.update(
            where = { Tenants.id eq tenantId }
        ) {
            it[settings] = updatedJson
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // ANNOUNCEMENTS
    // =====================================================

    fun findAllAnnouncements(tenantId: Long, includeInactive: Boolean = false): List<Announcement> = transaction {
        var query = Announcements.selectAll()
            .where { Announcements.tenantId eq tenantId }

        if (!includeInactive) {
            query = query.andWhere { Announcements.status eq 1 }
        }

        query.orderBy(Announcements.createdAt, SortOrder.DESC)
            .map { it.toAnnouncement() }
    }

    fun findActiveAnnouncements(tenantId: Long): List<Announcement> = transaction {
        val now = Instant.now()
        Announcements.selectAll()
            .where {
                (Announcements.tenantId eq tenantId) and
                (Announcements.status eq 1) and
                (Announcements.displayFrom lessEq now) and
                ((Announcements.displayTo.isNull()) or (Announcements.displayTo greaterEq now))
            }
            .orderBy(Announcements.createdAt, SortOrder.DESC)
            .map { it.toAnnouncement() }
    }

    fun findAnnouncementById(id: Long, tenantId: Long): Announcement? = transaction {
        Announcements.selectAll()
            .where { (Announcements.id eq id) and (Announcements.tenantId eq tenantId) }
            .singleOrNull()
            ?.toAnnouncement()
    }

    fun createAnnouncement(tenantId: Long, request: CreateAnnouncementRequest): Announcement = transaction {
        val displayFrom = request.displayFrom?.let { Instant.parse(it) } ?: Instant.now()
        val displayTo = request.displayTo?.let { Instant.parse(it) }

        // If showAsPopup is true, disable other popup announcements
        if (request.showAsPopup) {
            Announcements.update(
                where = { (Announcements.tenantId eq tenantId) and (Announcements.showAsPopup eq true) }
            ) {
                it[showAsPopup] = false
            }
        }

        val id = Announcements.insertAndGetId {
            it[this.tenantId] = tenantId
            it[title] = request.title
            it[content] = request.content
            it[this.displayFrom] = displayFrom
            it[this.displayTo] = displayTo
            it[showAsPopup] = request.showAsPopup
        }

        findAnnouncementById(id.value, tenantId)!!
    }

    fun updateAnnouncement(id: Long, tenantId: Long, request: UpdateAnnouncementRequest): Announcement? = transaction {
        // If showAsPopup is being set to true, disable other popup announcements
        if (request.showAsPopup == true) {
            Announcements.update(
                where = { (Announcements.tenantId eq tenantId) and (Announcements.id neq id) and (Announcements.showAsPopup eq true) }
            ) {
                it[showAsPopup] = false
            }
        }

        val updated = Announcements.update(
            where = { (Announcements.id eq id) and (Announcements.tenantId eq tenantId) }
        ) {
            request.title?.let { t -> it[title] = t }
            request.content?.let { c -> it[content] = c }
            request.displayFrom?.let { df -> it[displayFrom] = Instant.parse(df) }
            request.displayTo?.let { dt -> it[displayTo] = Instant.parse(dt) }
            request.showAsPopup?.let { sp -> it[showAsPopup] = sp }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findAnnouncementById(id, tenantId) else null
    }

    fun deleteAnnouncement(id: Long, tenantId: Long): Boolean = transaction {
        Announcements.update(
            where = { (Announcements.id eq id) and (Announcements.tenantId eq tenantId) }
        ) {
            it[status] = 0
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // REFERRAL CAMPAIGNS
    // =====================================================

    fun findAllReferralCampaigns(tenantId: Long, includeInactive: Boolean = false): List<ReferralCampaign> = transaction {
        var query = ReferralCampaigns.selectAll()
            .where { ReferralCampaigns.tenantId eq tenantId }

        if (!includeInactive) {
            query = query.andWhere { ReferralCampaigns.status eq 1 }
        }

        query.orderBy(ReferralCampaigns.createdAt, SortOrder.DESC)
            .map { it.toReferralCampaign() }
    }

    fun findReferralCampaignById(id: Long, tenantId: Long): ReferralCampaign? = transaction {
        ReferralCampaigns.selectAll()
            .where { (ReferralCampaigns.id eq id) and (ReferralCampaigns.tenantId eq tenantId) }
            .singleOrNull()
            ?.toReferralCampaign()
    }

    fun findReferralCampaignByCode(code: String, tenantId: Long): ReferralCampaign? = transaction {
        ReferralCampaigns.selectAll()
            .where { (ReferralCampaigns.referralCode eq code) and (ReferralCampaigns.tenantId eq tenantId) }
            .singleOrNull()
            ?.toReferralCampaign()
    }

    fun createReferralCampaign(tenantId: Long, request: CreateReferralCampaignRequest): ReferralCampaign = transaction {
        val id = ReferralCampaigns.insertAndGetId {
            it[this.tenantId] = tenantId
            it[referrerId] = request.referrerId
            it[referralCode] = request.referralCode
            it[minPayment] = request.minPayment
            it[amountToAdd] = request.amountToAdd
            it[startDate] = request.startDate?.let { d -> Instant.parse(d) } ?: Instant.now()
            it[endDate] = request.endDate?.let { d -> Instant.parse(d) }
        }

        findReferralCampaignById(id.value, tenantId)!!
    }

    fun updateReferralCampaign(id: Long, tenantId: Long, request: UpdateReferralCampaignRequest): ReferralCampaign? = transaction {
        val updated = ReferralCampaigns.update(
            where = { (ReferralCampaigns.id eq id) and (ReferralCampaigns.tenantId eq tenantId) }
        ) {
            request.referralCode?.let { c -> it[referralCode] = c }
            request.minPayment?.let { m -> it[minPayment] = m }
            request.amountToAdd?.let { a -> it[amountToAdd] = a }
            request.startDate?.let { s -> it[startDate] = Instant.parse(s) }
            request.endDate?.let { e -> it[endDate] = Instant.parse(e) }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findReferralCampaignById(id, tenantId) else null
    }

    // =====================================================
    // REFERRAL CREDITS
    // =====================================================

    fun findPendingReferralCredits(tenantId: Long): List<ReferralCredit> = transaction {
        ReferralCredits.selectAll()
            .where { (ReferralCredits.tenantId eq tenantId) and (ReferralCredits.status eq 0) }
            .orderBy(ReferralCredits.createdAt, SortOrder.DESC)
            .map { it.toReferralCredit() }
    }

    fun findReferralCreditsByReferrer(referrerId: Long, tenantId: Long): List<ReferralCredit> = transaction {
        ReferralCredits.selectAll()
            .where { (ReferralCredits.tenantId eq tenantId) and (ReferralCredits.referrerId eq referrerId) }
            .orderBy(ReferralCredits.createdAt, SortOrder.DESC)
            .map { it.toReferralCredit() }
    }

    fun processReferralCredits(creditIds: List<Long>, tenantId: Long): Int = transaction {
        ReferralCredits.update(
            where = { (ReferralCredits.id inList creditIds) and (ReferralCredits.tenantId eq tenantId) and (ReferralCredits.status eq 0) }
        ) {
            it[status] = 1
            it[processedAt] = Instant.now()
        }
    }

    // =====================================================
    // EMBROIDERY COLORS
    // =====================================================

    fun findAllEmbroideryColors(tenantId: Long, includeDeleted: Boolean = false): List<EmbroideryColor> = transaction {
        var query = EmbroideryColors.selectAll()
            .where { EmbroideryColors.tenantId eq tenantId }

        if (!includeDeleted) {
            query = query.andWhere { EmbroideryColors.status eq 1 }
        }

        query.orderBy(EmbroideryColors.sortOrder, SortOrder.ASC)
            .map { it.toEmbroideryColor() }
    }

    fun findEmbroideryColorById(id: Long, tenantId: Long): EmbroideryColor? = transaction {
        EmbroideryColors.selectAll()
            .where { (EmbroideryColors.id eq id) and (EmbroideryColors.tenantId eq tenantId) }
            .singleOrNull()
            ?.toEmbroideryColor()
    }

    fun createEmbroideryColor(tenantId: Long, request: CreateEmbroideryColorRequest): EmbroideryColor = transaction {
        val id = EmbroideryColors.insertAndGetId {
            it[this.tenantId] = tenantId
            it[name] = request.name
            it[hexColor] = request.hexColor
            it[inStock] = request.inStock
            it[sortOrder] = request.sortOrder
        }

        findEmbroideryColorById(id.value, tenantId)!!
    }

    fun updateEmbroideryColor(id: Long, tenantId: Long, request: UpdateEmbroideryColorRequest): EmbroideryColor? = transaction {
        val updated = EmbroideryColors.update(
            where = { (EmbroideryColors.id eq id) and (EmbroideryColors.tenantId eq tenantId) }
        ) {
            request.name?.let { n -> it[name] = n }
            request.hexColor?.let { h -> it[hexColor] = h }
            request.inStock?.let { s -> it[inStock] = s }
            request.sortOrder?.let { o -> it[sortOrder] = o }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findEmbroideryColorById(id, tenantId) else null
    }

    fun deleteEmbroideryColor(id: Long, tenantId: Long): Boolean = transaction {
        EmbroideryColors.update(
            where = { (EmbroideryColors.id eq id) and (EmbroideryColors.tenantId eq tenantId) }
        ) {
            it[status] = 0
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // LABEL ADDRESSES
    // =====================================================

    fun findAllLabelAddresses(tenantId: Long, includeDeleted: Boolean = false): List<LabelAddress> = transaction {
        var query = LabelAddresses.selectAll()
            .where { LabelAddresses.tenantId eq tenantId }

        if (!includeDeleted) {
            query = query.andWhere { LabelAddresses.status eq 1 }
        }

        query.orderBy(LabelAddresses.createdAt, SortOrder.DESC)
            .map { it.toLabelAddress() }
    }

    fun findLabelAddressById(id: Long, tenantId: Long): LabelAddress? = transaction {
        LabelAddresses.selectAll()
            .where { (LabelAddresses.id eq id) and (LabelAddresses.tenantId eq tenantId) }
            .singleOrNull()
            ?.toLabelAddress()
    }

    fun findDefaultLabelAddress(tenantId: Long): LabelAddress? = transaction {
        LabelAddresses.selectAll()
            .where { (LabelAddresses.tenantId eq tenantId) and (LabelAddresses.isDefault eq true) and (LabelAddresses.status eq 1) }
            .singleOrNull()
            ?.toLabelAddress()
    }

    fun createLabelAddress(tenantId: Long, request: CreateLabelAddressRequest): LabelAddress = transaction {
        // If this is set as default, unset other defaults
        if (request.isDefault) {
            LabelAddresses.update(
                where = { (LabelAddresses.tenantId eq tenantId) and (LabelAddresses.isDefault eq true) }
            ) {
                it[isDefault] = false
            }
        }

        val id = LabelAddresses.insertAndGetId {
            it[this.tenantId] = tenantId
            it[name] = request.name
            it[phone] = request.phone
            it[street1] = request.street1
            it[street2] = request.street2
            it[city] = request.city
            it[state] = request.state
            it[stateIso] = request.stateIso
            it[country] = request.country
            it[countryIso] = request.countryIso
            it[postalCode] = request.postalCode
            it[isDefault] = request.isDefault
        }

        findLabelAddressById(id.value, tenantId)!!
    }

    fun updateLabelAddress(id: Long, tenantId: Long, request: UpdateLabelAddressRequest): LabelAddress? = transaction {
        // If this is being set as default, unset other defaults
        if (request.isDefault == true) {
            LabelAddresses.update(
                where = { (LabelAddresses.tenantId eq tenantId) and (LabelAddresses.id neq id) and (LabelAddresses.isDefault eq true) }
            ) {
                it[isDefault] = false
            }
        }

        val updated = LabelAddresses.update(
            where = { (LabelAddresses.id eq id) and (LabelAddresses.tenantId eq tenantId) }
        ) {
            request.name?.let { n -> it[name] = n }
            request.phone?.let { p -> it[phone] = p }
            request.street1?.let { s -> it[street1] = s }
            request.street2?.let { s -> it[street2] = s }
            request.city?.let { c -> it[city] = c }
            request.state?.let { s -> it[state] = s }
            request.stateIso?.let { s -> it[stateIso] = s }
            request.country?.let { c -> it[country] = c }
            request.countryIso?.let { c -> it[countryIso] = c }
            request.postalCode?.let { p -> it[postalCode] = p }
            request.isDefault?.let { d -> it[isDefault] = d }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findLabelAddressById(id, tenantId) else null
    }

    fun deleteLabelAddress(id: Long, tenantId: Long): Boolean = transaction {
        LabelAddresses.update(
            where = { (LabelAddresses.id eq id) and (LabelAddresses.tenantId eq tenantId) }
        ) {
            it[status] = 0
            it[isDefault] = false
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // NOTIFICATIONS
    // =====================================================

    fun findUnreadNotifications(userId: Long, tenantId: Long, limit: Int = 50): List<Notification> = transaction {
        Notifications.selectAll()
            .where {
                (Notifications.tenantId eq tenantId) and
                (Notifications.userId eq userId) and
                (Notifications.isRead eq false)
            }
            .orderBy(Notifications.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toNotification() }
    }

    fun findAllNotifications(userId: Long, tenantId: Long, page: Int = 1, limit: Int = 50): List<Notification> = transaction {
        val offset = ((page - 1) * limit).toLong()
        Notifications.selectAll()
            .where { (Notifications.tenantId eq tenantId) and (Notifications.userId eq userId) }
            .orderBy(Notifications.createdAt, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toNotification() }
    }

    fun createNotification(tenantId: Long, request: CreateNotificationRequest): Notification = transaction {
        val id = Notifications.insertAndGetId {
            it[this.tenantId] = tenantId
            it[userId] = request.userId
            it[notificationType] = request.notificationType
            it[title] = request.title
            it[message] = request.message
            it[url] = request.url
            it[priority] = request.priority
        }

        Notifications.selectAll()
            .where { Notifications.id eq id }
            .single()
            .toNotification()
    }

    fun markNotificationRead(id: Long, userId: Long, tenantId: Long): Boolean = transaction {
        Notifications.update(
            where = { (Notifications.id eq id) and (Notifications.userId eq userId) and (Notifications.tenantId eq tenantId) }
        ) {
            it[isRead] = true
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun markAllNotificationsRead(userId: Long, tenantId: Long): Int = transaction {
        Notifications.update(
            where = { (Notifications.userId eq userId) and (Notifications.tenantId eq tenantId) and (Notifications.isRead eq false) }
        ) {
            it[isRead] = true
            it[updatedAt] = Instant.now()
        }
    }

    // =====================================================
    // USER PERMISSIONS
    // =====================================================

    fun findUserPermissions(userId: Long, tenantId: Long): List<Int> = transaction {
        UserPermissions.selectAll()
            .where { (UserPermissions.userId eq userId) and (UserPermissions.tenantId eq tenantId) }
            .map { it[UserPermissions.permissionId] }
    }

    fun hasPermission(userId: Long, tenantId: Long, permissionId: Int): Boolean = transaction {
        UserPermissions.selectAll()
            .where {
                (UserPermissions.userId eq userId) and
                (UserPermissions.tenantId eq tenantId) and
                (UserPermissions.permissionId eq permissionId)
            }
            .count() > 0
    }

    fun updateUserPermissions(userId: Long, tenantId: Long, permissionIds: List<Int>, grantedBy: Long? = null): List<Int> = transaction {
        // Remove existing permissions
        UserPermissions.deleteWhere {
            (UserPermissions.userId eq userId) and (UserPermissions.tenantId eq tenantId)
        }

        // Add new permissions
        permissionIds.forEach { permId ->
            UserPermissions.insert {
                it[this.tenantId] = tenantId
                it[this.userId] = userId
                it[permissionId] = permId
                it[this.grantedBy] = grantedBy
            }
        }

        permissionIds
    }

    fun getAllPermissions(): List<Permission> = transaction {
        PermissionsRef.selectAll()
            .map {
                Permission(
                    id = it[PermissionsRef.id].value.toInt(),
                    name = it[PermissionsRef.name],
                    description = it[PermissionsRef.description],
                    category = it[PermissionsRef.category]
                )
            }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toAnnouncement(): Announcement = Announcement(
        id = this[Announcements.id].value,
        tenantId = this[Announcements.tenantId].value,
        title = this[Announcements.title],
        content = this[Announcements.content],
        displayFrom = this[Announcements.displayFrom].toString(),
        displayTo = this[Announcements.displayTo]?.toString(),
        showAsPopup = this[Announcements.showAsPopup],
        status = this[Announcements.status],
        createdAt = this[Announcements.createdAt].toString(),
        updatedAt = this[Announcements.updatedAt].toString()
    )

    private fun ResultRow.toReferralCampaign(): ReferralCampaign = ReferralCampaign(
        id = this[ReferralCampaigns.id].value,
        tenantId = this[ReferralCampaigns.tenantId].value,
        referrerId = this[ReferralCampaigns.referrerId]?.value,
        referralCode = this[ReferralCampaigns.referralCode],
        minPayment = this[ReferralCampaigns.minPayment],
        amountToAdd = this[ReferralCampaigns.amountToAdd],
        startDate = this[ReferralCampaigns.startDate].toString(),
        endDate = this[ReferralCampaigns.endDate]?.toString(),
        status = this[ReferralCampaigns.status],
        createdAt = this[ReferralCampaigns.createdAt].toString(),
        updatedAt = this[ReferralCampaigns.updatedAt].toString()
    )

    private fun ResultRow.toReferralCredit(): ReferralCredit = ReferralCredit(
        id = this[ReferralCredits.id].value,
        tenantId = this[ReferralCredits.tenantId].value,
        campaignId = this[ReferralCredits.campaignId]?.value,
        referrerId = this[ReferralCredits.referrerId].value,
        referredUserId = this[ReferralCredits.referredUserId]?.value,
        orderId = this[ReferralCredits.orderId]?.value,
        creditAmount = this[ReferralCredits.creditAmount],
        status = this[ReferralCredits.status],
        processedAt = this[ReferralCredits.processedAt]?.toString(),
        createdAt = this[ReferralCredits.createdAt].toString()
    )

    private fun ResultRow.toEmbroideryColor(): EmbroideryColor = EmbroideryColor(
        id = this[EmbroideryColors.id].value,
        tenantId = this[EmbroideryColors.tenantId].value,
        name = this[EmbroideryColors.name],
        hexColor = this[EmbroideryColors.hexColor],
        inStock = this[EmbroideryColors.inStock],
        sortOrder = this[EmbroideryColors.sortOrder],
        status = this[EmbroideryColors.status],
        createdAt = this[EmbroideryColors.createdAt].toString(),
        updatedAt = this[EmbroideryColors.updatedAt].toString()
    )

    private fun ResultRow.toLabelAddress(): LabelAddress = LabelAddress(
        id = this[LabelAddresses.id].value,
        tenantId = this[LabelAddresses.tenantId].value,
        name = this[LabelAddresses.name],
        phone = this[LabelAddresses.phone],
        street1 = this[LabelAddresses.street1],
        street2 = this[LabelAddresses.street2],
        city = this[LabelAddresses.city],
        state = this[LabelAddresses.state],
        stateIso = this[LabelAddresses.stateIso],
        country = this[LabelAddresses.country],
        countryIso = this[LabelAddresses.countryIso],
        postalCode = this[LabelAddresses.postalCode],
        isDefault = this[LabelAddresses.isDefault],
        status = this[LabelAddresses.status],
        createdAt = this[LabelAddresses.createdAt].toString(),
        updatedAt = this[LabelAddresses.updatedAt].toString()
    )

    private fun ResultRow.toNotification(): Notification = Notification(
        id = this[Notifications.id].value,
        tenantId = this[Notifications.tenantId].value,
        userId = this[Notifications.userId].value,
        notificationType = this[Notifications.notificationType],
        title = this[Notifications.title],
        message = this[Notifications.message],
        url = this[Notifications.url],
        priority = this[Notifications.priority],
        isRead = this[Notifications.isRead],
        createdAt = this[Notifications.createdAt].toString(),
        updatedAt = this[Notifications.updatedAt].toString()
    )
}

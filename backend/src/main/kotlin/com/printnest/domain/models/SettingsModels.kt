package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// GangsheetSettings is defined in GangsheetModels.kt

@Serializable
data class DetailedSettings(
    val defaultPriceProfileId: Long? = null,
    val defaultShippingProfileId: Long? = null,
    val defaultLabelAddressId: Long? = null
)

@Serializable
data class UpdateGangsheetSettingsRequest(
    val rollWidth: Int? = null,
    val rollLength: Int? = null,
    val dpi: Int? = null,
    val gap: Double? = null,
    val border: Boolean? = null,
    val borderSize: Double? = null,
    val borderColor: String? = null
)

// =====================================================
// TENANT SETTINGS RESPONSE (for service layer)
// =====================================================

@Serializable
data class TenantSettingsResponse(
    val tenantId: Long,
    val subdomain: String,
    val name: String,
    val customDomain: String? = null,
    val logoUrl: String? = null,
    val emailApiKey: String? = null,
    val gangsheetSettings: GangsheetSettingsFull = GangsheetSettingsFull(),
    val detailedSettings: DetailedSettings = DetailedSettings(),
    val awsSettings: AwsSettings? = null,
    val shipstationSettings: ShipStationSettings? = null,
    val stripeSettings: StripeSettings? = null,
    val shippingSettings: ShippingSettings? = null,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

// =====================================================
// ANNOUNCEMENTS
// =====================================================

@Serializable
data class Announcement(
    val id: Long,
    val tenantId: Long,
    val title: String,
    val content: String,
    val displayFrom: String,
    val displayTo: String? = null,
    val showAsPopup: Boolean = false,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateAnnouncementRequest(
    val title: String,
    val content: String,
    val displayFrom: String? = null,
    val displayTo: String? = null,
    val showAsPopup: Boolean = false
)

@Serializable
data class UpdateAnnouncementRequest(
    val title: String? = null,
    val content: String? = null,
    val displayFrom: String? = null,
    val displayTo: String? = null,
    val showAsPopup: Boolean? = null,
    val status: Int? = null
)

// =====================================================
// REFERRAL SYSTEM
// =====================================================

@Serializable
data class ReferralCampaign(
    val id: Long,
    val tenantId: Long,
    val referrerId: Long? = null,
    val referrerName: String? = null,
    val referralCode: String,
    @Serializable(with = BigDecimalSerializer::class)
    val minPayment: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val amountToAdd: BigDecimal = BigDecimal.ZERO,
    val startDate: String,
    val endDate: String? = null,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateReferralCampaignRequest(
    val referrerId: Long? = null,
    val referralCode: String,
    @Serializable(with = BigDecimalSerializer::class)
    val minPayment: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val amountToAdd: BigDecimal = BigDecimal.ZERO,
    val startDate: String? = null,
    val endDate: String? = null
)

@Serializable
data class UpdateReferralCampaignRequest(
    val referralCode: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val minPayment: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amountToAdd: BigDecimal? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: Int? = null
)

@Serializable
data class ReferralCredit(
    val id: Long,
    val tenantId: Long,
    val campaignId: Long? = null,
    val referrerId: Long,
    val referrerName: String? = null,
    val referredUserId: Long? = null,
    val referredUserName: String? = null,
    val orderId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val creditAmount: BigDecimal = BigDecimal.ZERO,
    val status: Int = 0, // 0=pending, 1=processed, 2=rejected
    val processedAt: String? = null,
    val createdAt: String
)

@Serializable
data class ProcessReferralCreditsRequest(
    val creditIds: List<Long>
)

// =====================================================
// EMBROIDERY COLORS
// =====================================================

@Serializable
data class EmbroideryColor(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val hexColor: String,
    val inStock: Boolean = true,
    val sortOrder: Int = 0,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateEmbroideryColorRequest(
    val name: String,
    val hexColor: String,
    val inStock: Boolean = true,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateEmbroideryColorRequest(
    val name: String? = null,
    val hexColor: String? = null,
    val inStock: Boolean? = null,
    val sortOrder: Int? = null,
    val status: Int? = null
)

// =====================================================
// LABEL ADDRESSES
// =====================================================

@Serializable
data class LabelAddress(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val phone: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String? = null,
    val stateIso: String? = null,
    val country: String = "United States",
    val countryIso: String = "US",
    val postalCode: String,
    val isDefault: Boolean = false,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateLabelAddressRequest(
    val name: String,
    val phone: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String? = null,
    val stateIso: String? = null,
    val country: String = "United States",
    val countryIso: String = "US",
    val postalCode: String,
    val isDefault: Boolean = false
)

@Serializable
data class UpdateLabelAddressRequest(
    val name: String? = null,
    val phone: String? = null,
    val street1: String? = null,
    val street2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val stateIso: String? = null,
    val country: String? = null,
    val countryIso: String? = null,
    val postalCode: String? = null,
    val isDefault: Boolean? = null,
    val status: Int? = null
)

// =====================================================
// NOTIFICATIONS
// =====================================================

enum class NotificationType(val value: String) {
    SUPPORT("support"),
    ORDER("order"),
    DIGITIZING("digitizing"),
    SYSTEM("system"),
    PAYMENT("payment"),
    WALLET("wallet")
}

enum class NotificationPriority(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical")
}

@Serializable
data class Notification(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val notificationType: String = "system",
    val title: String,
    val message: String,
    val url: String? = null,
    val priority: String = "medium",
    val isRead: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateNotificationRequest(
    val userId: Long,
    val notificationType: String = "system",
    val title: String,
    val message: String,
    val url: String? = null,
    val priority: String = "medium"
)

@Serializable
data class MarkNotificationsReadRequest(
    val notificationIds: List<Long>? = null, // null = mark all as read
    val markAll: Boolean = false
)

// =====================================================
// USER PERMISSIONS
// =====================================================

object PermissionIds {
    const val VIEW_ORDERS = 1
    const val EDIT_ORDERS = 2
    const val EDIT_USER_PROFILES = 3
    const val VIEW_PRODUCTS = 4
    const val EDIT_PRODUCTS = 5
    const val VIEW_DESIGNS = 6
    const val EDIT_DESIGNS = 7
    const val VIEW_GANGSHEETS = 8
    const val CREATE_GANGSHEETS = 9
    const val VIEW_REPORTS = 10
    const val VIEW_USER_PROFILES = 11
    const val MANAGE_CUSTOMERS = 12
    const val MANAGE_SETTINGS = 13
    const val MANAGE_INTEGRATIONS = 14
    const val USE_MAPPING = 15
}

@Serializable
data class Permission(
    val id: Int,
    val name: String,
    val description: String? = null,
    val category: String? = null
)

@Serializable
data class UserPermission(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val permissionId: Int,
    val grantedBy: Long? = null,
    val createdAt: String
)

@Serializable
data class UpdateUserPermissionsRequest(
    val permissionIds: List<Int>
)

// Gangsheet models are defined in GangsheetModels.kt

// =====================================================
// SUPPORT TICKETS
// =====================================================

@Serializable
data class SupportTicket(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val userName: String? = null,
    val orderId: Long? = null,
    val subject: String,
    val status: String = "open",
    val priority: String = "medium",
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val messages: List<SupportTicketMessage> = emptyList()
)

@Serializable
data class SupportTicketMessage(
    val id: Long,
    val ticketId: Long,
    val userId: Long,
    val userName: String? = null,
    val message: String,
    val isInternal: Boolean = false,
    val attachments: List<String> = emptyList(),
    val createdAt: String
)

@Serializable
data class CreateSupportTicketRequest(
    val orderId: Long? = null,
    val subject: String,
    val message: String,
    val priority: String = "medium"
)

@Serializable
data class CreateTicketMessageRequest(
    val message: String,
    val isInternal: Boolean = false,
    val attachments: List<String> = emptyList()
)

@Serializable
data class UpdateTicketStatusRequest(
    val status: String,
    val priority: String? = null
)

// Design models are defined in DesignModels.kt
// TenantSettings is defined in Models.kt

@Serializable
data class UpdateTenantSettingsRequest(
    val name: String? = null,
    val customDomain: String? = null,
    val logoUrl: String? = null,
    val emailApiKey: String? = null,
    val gangsheetSettings: GangsheetSettings? = null,
    val detailedSettings: DetailedSettings? = null,
    val awsSettings: AwsSettings? = null,
    val shipstationSettings: ShipStationSettings? = null,
    val stripeSettings: StripeSettings? = null,
    val shippingSettings: ShippingSettings? = null
)

// =====================================================
// TENANT FOR PDF GENERATION
// =====================================================

@Serializable
data class TenantForPdf(
    val id: Long,
    val name: String,
    val settings: TenantPdfSettings? = null
)

@Serializable
data class TenantPdfSettings(
    val logoUrl: String? = null,
    val address: Address? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val website: String? = null,
    val taxId: String? = null
)

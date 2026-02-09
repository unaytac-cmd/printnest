package com.printnest.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.Instant

// =====================================================
// USER & AUTHENTICATION
// =====================================================

@Serializable
data class User(
    val id: Long,
    val tenantId: Long,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: UserRole,
    val status: Int = 1,
    val permissions: List<String> = emptyList(),
    @Serializable(with = BigDecimalSerializer::class)
    val totalCredit: BigDecimal = BigDecimal.ZERO,
    val emailVerified: Boolean = false,
    val parentUserId: Long? = null,
    val priceProfileId: Long? = null,
    val shippingProfileId: Long? = null,
    val lastLoginAt: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    val fullName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifEmpty { email }

    val isProducer: Boolean
        get() = role == UserRole.PRODUCER || role == UserRole.OWNER

    val isSubdealer: Boolean
        get() = role == UserRole.SUBDEALER
}

@Serializable
enum class UserRole {
    OWNER,      // Tenant owner (original)
    ADMIN,      // Tenant admin
    EMPLOYEE,   // Limited access employee
    CUSTOMER,   // Customer (legacy)
    PRODUCER,   // Producer (alias for owner in new system)
    SUBDEALER   // Sub-dealer (new)
}

@Serializable
data class UserCredentials(
    val email: String,
    val password: String
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

// =====================================================
// TENANT
// =====================================================

@Serializable
data class Tenant(
    val id: Long,
    val subdomain: String,
    val name: String,
    val status: Int,
    val customDomain: String? = null,
    val stripeCustomerId: String? = null,
    val settings: TenantSettings? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TenantSettings(
    val shipstation: ShipStationSettings? = null,
    val stripe: StripeSettings? = null,
    val gangsheet: GangsheetSettings? = null,
    val shipping: ShippingSettings? = null,
    val aws: AwsSettings? = null,
    val logoUrl: String? = null,
    val detailedSettings: DetailedTenantSettings? = null
)

@Serializable
data class DetailedTenantSettings(
    val defaultPriceProfileId: Long? = null,
    val defaultShippingProfileId: Long? = null,
    val defaultLabelAddressId: Long? = null,
    val taxId: String? = null
)

@Serializable
data class ShipStationSettings(
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val isConnected: Boolean = false,
    val lastSyncAt: String? = null
)

@Serializable
data class StripeSettings(
    val publicKey: String? = null,
    val secretKey: String? = null,
    val webhookSecret: String? = null,
    val isConnected: Boolean = false
)

@Serializable
data class GangsheetSettings(
    val width: Double = 22.0,
    val height: Double = 60.0,
    val dpi: Int = 300,
    val spacing: Double = 0.25,
    val backgroundColor: String = "#FFFFFF",
    val autoArrange: Boolean = true
)

@Serializable
data class ShippingSettings(
    val nestshipperApiKey: String? = null,
    val easypostApiKey: String? = null
)

@Serializable
data class AwsSettings(
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val region: String = "us-east-1",
    val s3Bucket: String? = null,
    val cdnDomain: String? = null
)

// =====================================================
// SHIPSTATION STORES
// =====================================================

@Serializable
data class ShipStationStore(
    val id: Long,
    val tenantId: Long,
    val shipstationStoreId: Long,
    val storeName: String,
    val marketplaceName: String? = null,
    val marketplaceId: Long? = null,
    val accountName: String? = null,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ShipStationStoreWithAssignments(
    val store: ShipStationStore,
    val assignedSubdealers: List<SubdealerSummary> = emptyList()
)

// =====================================================
// SUBDEALER
// =====================================================

@Serializable
data class Subdealer(
    val id: Long,
    val tenantId: Long,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val status: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val totalCredit: BigDecimal = BigDecimal.ZERO,
    val parentUserId: Long,
    val priceProfileId: Long? = null,
    val shippingProfileId: Long? = null,
    val assignedStores: List<ShipStationStore> = emptyList(),
    val createdAt: String,
    val updatedAt: String
) {
    val fullName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifEmpty { email }
}

@Serializable
data class SubdealerSummary(
    val id: Long,
    val email: String,
    val fullName: String,
    val status: Int
)

@Serializable
data class SubdealerStoreAssignment(
    val id: Long,
    val tenantId: Long,
    val subdealerId: Long,
    val shipstationStoreId: Long,
    val createdAt: String
)

@Serializable
data class CreateSubdealerRequest(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val storeIds: List<Long>? = null,
    val priceProfileId: Long? = null,
    val shippingProfileId: Long? = null
)

@Serializable
data class UpdateSubdealerRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val status: Int? = null,
    val priceProfileId: Long? = null,
    val shippingProfileId: Long? = null
)

@Serializable
data class AssignStoresRequest(
    val storeIds: List<Long>
)

// =====================================================
// ORDERS
// =====================================================

@Serializable
data class Order(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val storeId: Long? = null,
    val externalOrderId: String? = null,
    val orderStatus: Int = 0,
    val orderInfo: OrderInfo? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    val customerEmail: String? = null,
    val customerName: String? = null,
    val shippingAddress: Address? = null,
    val trackingNumber: String? = null,
    val trackingUrl: String? = null,
    val shipstationStoreId: Long? = null,
    val shipstationOrderId: Long? = null,
    val shippedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderInfo(
    val notes: String? = null,
    val giftMessage: String? = null,
    val isGift: Boolean = false,
    val customsInfo: OrderCustomsInfo? = null
)

@Serializable
data class OrderCustomsInfo(
    val contents: String? = null,
    val contentsType: String? = null,
    val nonDeliveryOption: String? = null,
    val customsCertify: Boolean = false
)

@Serializable
data class Address(
    val name: String? = null,
    val company: String? = null,
    val street1: String? = null,
    val street2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val phone: String? = null
)

@Serializable
data class OrderFilters(
    val status: Int? = null,
    val storeId: Long? = null,
    val shipstationStoreId: Long? = null,
    val search: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 1,
    val limit: Int = 50
)

// =====================================================
// PRICE PROFILES
// =====================================================

@Serializable
data class PriceProfile(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val profileType: Int = 0,
    val pricing: PricingRules? = null,
    val isDefault: Boolean = false,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class PricingRules(
    val baseMarkup: Double = 0.0,
    val tiers: List<PricingTier> = emptyList()
)

@Serializable
data class PricingTier(
    val minQuantity: Int,
    val maxQuantity: Int? = null,
    val markup: Double
)

// =====================================================
// API RESPONSES
// =====================================================

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null
)

// =====================================================
// SERIALIZERS
// =====================================================

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// SHIPPING PROFILE TYPES
// =====================================================

enum class ShippingProfileType(val code: Int) {
    QUANTITY_BASED(0),  // Fixed pricing based on quantity
    API_BASED(1);       // Dynamic pricing from EasyPost API

    companion object {
        fun fromCode(code: Int): ShippingProfileType = entries.find { it.code == code } ?: QUANTITY_BASED
    }
}

// =====================================================
// SHIPPING PROFILE
// =====================================================

@Serializable
data class ShippingProfile(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val profileType: Int = 0, // 0=quantity-based, 1=api-based
    val profilePricing: ShippingProfilePricing? = null,
    val isDefault: Boolean = false,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val methods: List<ShippingMethod> = emptyList()
)

@Serializable
data class ShippingProfilePricing(
    // For quantity-based (type 0)
    @Serializable(with = BigDecimalSerializer::class)
    val lightFirst: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val lightSecond: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val lightThird: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val heavyFirst: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val heavySecond: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val heavyThird: BigDecimal? = null,

    // For api-based (type 1)
    val differenceType: Int? = null, // 0=percentage, 1=dollar
    @Serializable(with = BigDecimalSerializer::class)
    val differenceAmount: BigDecimal? = null
)

@Serializable
data class CreateShippingProfileRequest(
    val name: String,
    val profileType: Int = 0,
    val profilePricing: ShippingProfilePricing? = null,
    val isDefault: Boolean = false
)

@Serializable
data class UpdateShippingProfileRequest(
    val name: String? = null,
    val profileType: Int? = null,
    val profilePricing: ShippingProfilePricing? = null,
    val isDefault: Boolean? = null,
    val status: Int? = null
)

// =====================================================
// SHIPPING METHOD
// =====================================================

@Serializable
data class ShippingMethod(
    val id: Long,
    val tenantId: Long,
    val shippingProfileId: Long? = null,
    val name: String,
    val description: String? = null,
    val apiMethod: String? = null, // EasyPost service name
    val isInternational: Boolean = false,
    @Serializable(with = BigDecimalSerializer::class)
    val extraFee: BigDecimal = BigDecimal.ZERO,
    val processingInfo: ProcessingInfo? = null,
    val sortOrder: Int = 0,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ProcessingInfo(
    val minDeliveryDays: Int = 0,
    val maxDeliveryDays: Int = 0,
    val minProcessingDays: Int = 0,
    val maxProcessingDays: Int = 0
)

@Serializable
data class CreateShippingMethodRequest(
    val shippingProfileId: Long? = null,
    val name: String,
    val description: String? = null,
    val apiMethod: String? = null,
    val isInternational: Boolean = false,
    @Serializable(with = BigDecimalSerializer::class)
    val extraFee: BigDecimal = BigDecimal.ZERO,
    val processingInfo: ProcessingInfo? = null,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateShippingMethodRequest(
    val shippingProfileId: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val apiMethod: String? = null,
    val isInternational: Boolean? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val extraFee: BigDecimal? = null,
    val processingInfo: ProcessingInfo? = null,
    val sortOrder: Int? = null,
    val status: Int? = null
)

// =====================================================
// DISCOUNT TYPE
// =====================================================

enum class DiscountType(val code: Int) {
    PERCENTAGE(0),
    DOLLAR(1);

    companion object {
        fun fromCode(code: Int): DiscountType = entries.find { it.code == code } ?: PERCENTAGE
    }
}

// =====================================================
// PRICE PROFILE
// =====================================================

@Serializable
data class PriceProfileFull(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val profileType: Int = 0,
    val discountType: Int = 0, // 0=percentage, 1=dollar
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val giftNotePrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val digitizingPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val gangsheetPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val uvGangsheetPrice: BigDecimal = BigDecimal.ZERO,
    val isDefault: Boolean = false,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val productOverrides: List<PriceProfileProduct> = emptyList()
)

@Serializable
data class CreatePriceProfileRequest(
    val name: String,
    val profileType: Int = 0,
    val discountType: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val giftNotePrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val digitizingPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val gangsheetPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val uvGangsheetPrice: BigDecimal = BigDecimal.ZERO,
    val isDefault: Boolean = false
)

@Serializable
data class UpdatePriceProfileRequest(
    val name: String? = null,
    val profileType: Int? = null,
    val discountType: Int? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val giftNotePrice: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val digitizingPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val gangsheetPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val uvGangsheetPrice: BigDecimal? = null,
    val isDefault: Boolean? = null,
    val status: Int? = null
)

// =====================================================
// PRICE PROFILE PRODUCT (Variant Override)
// =====================================================

@Serializable
data class PriceProfileProduct(
    val id: Long,
    val tenantId: Long,
    val priceProfileId: Long,
    val variantId: Long,
    val discountType: Int = 0, // 0=percentage, 1=dollar
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val variant: Variant? = null
)

@Serializable
data class CreatePriceProfileProductRequest(
    val variantId: Long,
    val discountType: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class UpdatePriceProfileProductRequest(
    val discountType: Int? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal? = null
)

@Serializable
data class BulkPriceProfileProductRequest(
    val products: List<CreatePriceProfileProductRequest>
)

// =====================================================
// SHIPPING CALCULATION
// =====================================================

@Serializable
data class ShippingCalculationRequest(
    val items: List<ShippingItem>,
    val destinationAddress: Address,
    val shippingProfileId: Long? = null,
    val shippingMethodId: Long? = null
)

@Serializable
data class ShippingItem(
    val variantId: Long,
    val quantity: Int,
    val categoryId: Long? = null,
    val isHeavy: Boolean = false,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal? = null
)

@Serializable
data class ShippingCalculationResult(
    @Serializable(with = BigDecimalSerializer::class)
    val totalShipping: BigDecimal,
    val breakdown: ShippingBreakdown? = null,
    val availableMethods: List<ShippingMethodRate> = emptyList()
)

@Serializable
data class ShippingBreakdown(
    val profileType: String, // "quantity-based" or "api-based"
    val itemCount: Int,
    val heavyItemCount: Int,
    val lightItemCount: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val baseRate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val markup: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class ShippingMethodRate(
    val methodId: Long,
    val methodName: String,
    val carrier: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rate: BigDecimal,
    val estimatedDays: String? = null // "3-5 business days"
)

// =====================================================
// PRICE CALCULATION
// =====================================================

@Serializable
data class PriceCalculationRequest(
    val userId: Long? = null,
    val priceProfileId: Long? = null,
    val items: List<PriceItem>
)

@Serializable
data class PriceItem(
    val variantId: Long,
    val quantity: Int,
    val modificationIds: List<Long> = emptyList(),
    val stitchCount: Int? = null, // For embroidery
    val hasGiftNote: Boolean = false
)

@Serializable
data class PriceCalculationResult(
    @Serializable(with = BigDecimalSerializer::class)
    val subtotal: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalDiscount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalModifications: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalStitchCharges: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalGiftNotes: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val total: BigDecimal,
    val itemDetails: List<PriceItemDetail>
)

@Serializable
data class PriceItemDetail(
    val variantId: Long,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val modificationPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val giftNotePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val discount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val lineTotal: BigDecimal
)

// =====================================================
// DEFAULT CARRIERS & METHODS
// =====================================================

object DefaultShippingMethods {
    val USPS_FIRST_CLASS = ShippingMethodTemplate(
        name = "USPS First Class",
        apiMethod = "First",
        isInternational = false,
        processingInfo = ProcessingInfo(3, 5, 1, 2)
    )

    val USPS_PRIORITY = ShippingMethodTemplate(
        name = "USPS Priority Mail",
        apiMethod = "Priority",
        isInternational = false,
        processingInfo = ProcessingInfo(1, 3, 1, 2)
    )

    val USPS_EXPRESS = ShippingMethodTemplate(
        name = "USPS Priority Mail Express",
        apiMethod = "Express",
        isInternational = false,
        processingInfo = ProcessingInfo(1, 2, 1, 1)
    )

    val UPS_GROUND = ShippingMethodTemplate(
        name = "UPS Ground",
        apiMethod = "Ground",
        isInternational = false,
        processingInfo = ProcessingInfo(3, 7, 1, 2)
    )

    val UPS_2DAY = ShippingMethodTemplate(
        name = "UPS 2nd Day Air",
        apiMethod = "2ndDayAir",
        isInternational = false,
        processingInfo = ProcessingInfo(2, 2, 1, 1)
    )

    val FEDEX_GROUND = ShippingMethodTemplate(
        name = "FedEx Ground",
        apiMethod = "FEDEX_GROUND",
        isInternational = false,
        processingInfo = ProcessingInfo(3, 7, 1, 2)
    )

    val allDomestic = listOf(USPS_FIRST_CLASS, USPS_PRIORITY, USPS_EXPRESS, UPS_GROUND, UPS_2DAY, FEDEX_GROUND)
}

@Serializable
data class ShippingMethodTemplate(
    val name: String,
    val apiMethod: String,
    val isInternational: Boolean,
    val processingInfo: ProcessingInfo
)

package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// CARRIER AND SERVICE ENUMS
// =====================================================

@Serializable
enum class Carrier(val displayName: String) {
    USPS("USPS"),
    UPS("UPS"),
    FEDEX("FedEx"),
    DHL("DHL Express");

    companion object {
        fun fromString(value: String): Carrier? = entries.find {
            it.name.equals(value, ignoreCase = true) ||
            it.displayName.equals(value, ignoreCase = true)
        }
    }
}

@Serializable
enum class ServiceType(val displayName: String, val carrier: Carrier) {
    // USPS Services
    USPS_FIRST_CLASS("First Class Mail", Carrier.USPS),
    USPS_PRIORITY("Priority Mail", Carrier.USPS),
    USPS_EXPRESS("Priority Mail Express", Carrier.USPS),
    USPS_GROUND_ADVANTAGE("Ground Advantage", Carrier.USPS),
    USPS_PARCEL_SELECT("Parcel Select", Carrier.USPS),
    USPS_MEDIA_MAIL("Media Mail", Carrier.USPS),
    USPS_FIRST_CLASS_INTERNATIONAL("First Class International", Carrier.USPS),
    USPS_PRIORITY_INTERNATIONAL("Priority Mail International", Carrier.USPS),
    USPS_EXPRESS_INTERNATIONAL("Priority Mail Express International", Carrier.USPS),

    // UPS Services
    UPS_GROUND("Ground", Carrier.UPS),
    UPS_3_DAY_SELECT("3 Day Select", Carrier.UPS),
    UPS_2ND_DAY_AIR("2nd Day Air", Carrier.UPS),
    UPS_2ND_DAY_AIR_AM("2nd Day Air AM", Carrier.UPS),
    UPS_NEXT_DAY_AIR("Next Day Air", Carrier.UPS),
    UPS_NEXT_DAY_AIR_SAVER("Next Day Air Saver", Carrier.UPS),
    UPS_NEXT_DAY_AIR_EARLY("Next Day Air Early", Carrier.UPS),
    UPS_WORLDWIDE_ECONOMY("Worldwide Economy", Carrier.UPS),
    UPS_WORLDWIDE_EXPEDITED("Worldwide Expedited", Carrier.UPS),
    UPS_WORLDWIDE_EXPRESS("Worldwide Express", Carrier.UPS),

    // FedEx Services
    FEDEX_GROUND("Ground", Carrier.FEDEX),
    FEDEX_HOME_DELIVERY("Home Delivery", Carrier.FEDEX),
    FEDEX_EXPRESS_SAVER("Express Saver", Carrier.FEDEX),
    FEDEX_2DAY("2Day", Carrier.FEDEX),
    FEDEX_2DAY_AM("2Day AM", Carrier.FEDEX),
    FEDEX_STANDARD_OVERNIGHT("Standard Overnight", Carrier.FEDEX),
    FEDEX_PRIORITY_OVERNIGHT("Priority Overnight", Carrier.FEDEX),
    FEDEX_FIRST_OVERNIGHT("First Overnight", Carrier.FEDEX),
    FEDEX_INTERNATIONAL_ECONOMY("International Economy", Carrier.FEDEX),
    FEDEX_INTERNATIONAL_PRIORITY("International Priority", Carrier.FEDEX),

    // DHL Services
    DHL_EXPRESS_WORLDWIDE("Express Worldwide", Carrier.DHL),
    DHL_EXPRESS_12("Express 12:00", Carrier.DHL),
    DHL_EXPRESS_9("Express 9:00", Carrier.DHL);

    companion object {
        fun fromString(value: String): ServiceType? = entries.find {
            it.name.equals(value, ignoreCase = true)
        }

        fun getByCarrier(carrier: Carrier): List<ServiceType> = entries.filter { it.carrier == carrier }
    }
}

// =====================================================
// SHIPPING ADDRESS
// =====================================================

@Serializable
data class ShippingAddress(
    val id: String? = null,
    val name: String,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String,
    val zip: String,
    val country: String = "US",
    val phone: String? = null,
    val email: String? = null,
    val residential: Boolean? = null,
    val verify: Boolean = false,
    val verificationStatus: AddressVerificationStatus? = null,
    val verificationMessages: List<String> = emptyList()
) {
    fun toEasyPostFormat(): Map<String, Any?> = mapOf(
        "name" to name,
        "company" to company,
        "street1" to street1,
        "street2" to street2,
        "city" to city,
        "state" to state,
        "zip" to zip,
        "country" to country,
        "phone" to phone,
        "email" to email,
        "residential" to residential,
        "verify" to verify
    ).filterValues { it != null }
}

@Serializable
enum class AddressVerificationStatus {
    VERIFIED,
    UNVERIFIED,
    INVALID,
    NEEDS_REVIEW
}

// =====================================================
// PARCEL
// =====================================================

@Serializable
data class Parcel(
    val id: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val length: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal, // in ounces
    val predefinedPackage: String? = null
) {
    fun toEasyPostFormat(): Map<String, Any?> = buildMap {
        put("weight", weight.toDouble())
        length?.let { put("length", it.toDouble()) }
        width?.let { put("width", it.toDouble()) }
        height?.let { put("height", it.toDouble()) }
        predefinedPackage?.let { put("predefined_package", it) }
    }
}

// =====================================================
// CUSTOMS INFO
// =====================================================

@Serializable
data class CustomsInfo(
    val id: String? = null,
    val eelPfc: String = "NOEEI 30.37(a)",
    val customsCertify: Boolean = true,
    val customsSigner: String? = null,
    val contentsType: String = "merchandise",
    val contentsExplanation: String? = null,
    val restrictionType: String = "none",
    val nonDeliveryOption: String = "return",
    val customsItems: List<CustomsItem> = emptyList()
)

@Serializable
data class CustomsItem(
    val description: String,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val value: BigDecimal,
    val hsTariffNumber: String? = null,
    val originCountry: String = "US"
)

// =====================================================
// SHIPMENT REQUESTS AND RESPONSES
// =====================================================

@Serializable
data class ShipmentRequest(
    val fromAddress: ShippingAddress,
    val toAddress: ShippingAddress,
    val parcel: Parcel,
    val customsInfo: CustomsInfo? = null,
    val options: ShipmentOptions? = null,
    val reference: String? = null
)

@Serializable
data class ShipmentOptions(
    val labelFormat: String = "PDF",
    val labelSize: String = "4x6",
    val printCustom1: String? = null,
    val printCustom2: String? = null,
    val printCustom3: String? = null,
    val invoiceNumber: String? = null,
    val currency: String = "USD"
)

@Serializable
data class ShipmentResponse(
    val id: String,
    val object_: String = "Shipment",
    val mode: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val fromAddress: EasyPostAddress? = null,
    val toAddress: EasyPostAddress? = null,
    val parcel: EasyPostParcel? = null,
    val customsInfo: EasyPostCustomsInfo? = null,
    val rates: List<ShippingRate> = emptyList(),
    val selectedRate: ShippingRate? = null,
    val postageLabel: PostageLabel? = null,
    val trackingCode: String? = null,
    val tracker: EasyPostTracker? = null,
    val refundStatus: String? = null,
    val batchId: String? = null,
    val batchStatus: String? = null,
    val messages: List<EasyPostMessage> = emptyList()
)

@Serializable
data class EasyPostAddress(
    val id: String,
    val mode: String? = null,
    val name: String? = null,
    val company: String? = null,
    val street1: String? = null,
    val street2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val residential: Boolean? = null,
    val verifications: AddressVerifications? = null
)

@Serializable
data class AddressVerifications(
    val zip4: VerificationResult? = null,
    val delivery: VerificationResult? = null
)

@Serializable
data class VerificationResult(
    val success: Boolean,
    val errors: List<VerificationError> = emptyList()
)

@Serializable
data class VerificationError(
    val field: String? = null,
    val message: String? = null,
    val code: String? = null
)

@Serializable
data class EasyPostParcel(
    val id: String,
    val length: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val predefinedPackage: String? = null
)

@Serializable
data class EasyPostCustomsInfo(
    val id: String,
    val eelPfc: String? = null,
    val customsCertify: Boolean? = null,
    val customsSigner: String? = null,
    val contentsType: String? = null,
    val contentsExplanation: String? = null,
    val restrictionType: String? = null,
    val nonDeliveryOption: String? = null,
    val customsItems: List<EasyPostCustomsItem> = emptyList()
)

@Serializable
data class EasyPostCustomsItem(
    val id: String,
    val description: String? = null,
    val quantity: Int? = null,
    val weight: Double? = null,
    val value: Double? = null,
    val hsTariffNumber: String? = null,
    val originCountry: String? = null
)

@Serializable
data class EasyPostTracker(
    val id: String,
    val mode: String? = null,
    val publicUrl: String? = null,
    val status: String? = null,
    val statusDetail: String? = null,
    val signedBy: String? = null,
    val estDeliveryDate: String? = null,
    val shipmentId: String? = null,
    val carrier: String? = null,
    val trackingCode: String? = null,
    val trackingDetails: List<TrackingDetail> = emptyList()
)

@Serializable
data class TrackingDetail(
    val datetime: String? = null,
    val message: String? = null,
    val status: String? = null,
    val source: String? = null,
    val trackingLocation: TrackingLocation? = null
)

@Serializable
data class TrackingLocation(
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val zip: String? = null
)

@Serializable
data class EasyPostMessage(
    val carrier: String? = null,
    val type: String? = null,
    val message: String? = null
)

// =====================================================
// SHIPPING RATES
// =====================================================

@Serializable
data class RateRequest(
    val orderId: Long,
    val fromAddress: ShippingAddress? = null, // Optional, uses tenant default if not provided
    val parcel: Parcel? = null // Optional, calculated from order if not provided
)

@Serializable
data class RateResponse(
    val shipmentId: String,
    val rates: List<ShippingRate>,
    val messages: List<String> = emptyList()
)

@Serializable
data class ShippingRate(
    val id: String,
    val shipmentId: String? = null,
    val carrier: String,
    val service: String,
    val carrierAccountId: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val listRate: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val retailRate: BigDecimal? = null,
    val currency: String = "USD",
    val deliveryDays: Int? = null,
    val deliveryDate: String? = null,
    val deliveryDateGuaranteed: Boolean = false,
    val estDeliveryDays: Int? = null,
    val mode: String? = null,
    val billingType: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = "$carrier $service"

    val isInternational: Boolean
        get() = service.contains("International", ignoreCase = true) ||
                service.contains("Worldwide", ignoreCase = true)
}

// =====================================================
// SHIPPING LABELS
// =====================================================

@Serializable
data class ShippingLabelRequest(
    val orderId: Long,
    val rateId: String,
    val shipmentId: String,
    val labelFormat: String = "PDF",
    val labelSize: String = "4x6"
)

@Serializable
data class ShippingLabelResponse(
    val id: Long,
    val orderId: Long,
    val carrier: String,
    val service: String,
    val trackingNumber: String,
    val trackingUrl: String,
    val labelUrl: String,
    val labelFormat: String,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal,
    val rateId: String,
    val shipmentId: String,
    val createdAt: String
)

@Serializable
data class PostageLabel(
    val id: String? = null,
    val labelUrl: String? = null,
    val labelZplUrl: String? = null,
    val labelPdfUrl: String? = null,
    val labelEpl2Url: String? = null,
    val labelDate: String? = null,
    val labelSize: String? = null,
    val labelResolution: Int? = null,
    val labelFileType: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// =====================================================
// TRACKING
// =====================================================

@Serializable
data class TrackingRequest(
    val trackingCode: String,
    val carrier: String? = null
)

@Serializable
data class TrackingResponse(
    val id: String,
    val trackingCode: String,
    val carrier: String,
    val status: TrackingStatus,
    val statusDetail: String? = null,
    val estimatedDeliveryDate: String? = null,
    val signedBy: String? = null,
    val publicUrl: String? = null,
    val trackingDetails: List<TrackingDetail> = emptyList()
)

@Serializable
enum class TrackingStatus {
    UNKNOWN,
    PRE_TRANSIT,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    AVAILABLE_FOR_PICKUP,
    RETURN_TO_SENDER,
    FAILURE,
    CANCELLED,
    ERROR
}

// =====================================================
// REFUND
// =====================================================

@Serializable
data class ShippingRefundRequest(
    val labelId: Long? = null,
    val shipmentId: String? = null
)

@Serializable
data class ShippingRefundResponse(
    val id: String,
    val shipmentId: String,
    val trackingCode: String,
    val status: RefundStatus,
    val message: String? = null
)

@Serializable
enum class RefundStatus {
    SUBMITTED,
    REFUNDED,
    REJECTED,
    PENDING
}

// =====================================================
// ADDRESS VALIDATION
// =====================================================

@Serializable
data class ValidateAddressRequest(
    val address: ShippingAddress
)

@Serializable
data class ValidateAddressResponse(
    val isValid: Boolean,
    val address: ShippingAddress,
    val suggestedAddress: ShippingAddress? = null,
    val messages: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

// =====================================================
// API ERROR
// =====================================================

@Serializable
data class EasyPostError(
    val error: EasyPostErrorDetail? = null
)

@Serializable
data class EasyPostErrorDetail(
    val code: String? = null,
    val message: String? = null,
    val errors: List<EasyPostErrorItem> = emptyList()
)

@Serializable
data class EasyPostErrorItem(
    val field: String? = null,
    val message: String? = null
)

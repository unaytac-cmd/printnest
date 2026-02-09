package com.printnest.integrations.nestshipper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// ============================================
// REQUEST MODELS
// ============================================

@Serializable
data class NestShipperAddressRequest(
    val name: String? = null,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String,
    val zip: String,
    val country: String = "US",
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class NestShipperParcelRequest(
    val length: Double,      // inches
    val width: Double,       // inches
    val height: Double,      // inches
    val weight: Double,      // ounces
    @SerialName("weight_unit")
    val weightUnit: String = "oz",
    @SerialName("dimension_unit")
    val dimensionUnit: String = "in"
)

@Serializable
data class NestShipperShipmentRequest(
    @SerialName("to_address")
    val toAddress: NestShipperAddressRequest,
    @SerialName("from_address")
    val fromAddress: NestShipperAddressRequest,
    val parcel: NestShipperParcelRequest,
    @SerialName("carrier_accounts")
    val carrierAccounts: List<String>? = null,
    val service: String? = null,          // For one-call buy
    val reference: String? = null,        // Order reference
    @SerialName("is_return")
    val isReturn: Boolean = false
)

@Serializable
data class NestShipperBuyRequest(
    @SerialName("rate_id")
    val rateId: String,
    val insurance: Double? = null
)

@Serializable
data class NestShipperCreateTrackerRequest(
    @SerialName("tracking_code")
    val trackingCode: String,
    val carrier: String? = null           // Optional, auto-detect if not provided
)

// ============================================
// RESPONSE MODELS
// ============================================

@Serializable
data class NestShipperAddressResponse(
    val id: String,
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
    val verified: Boolean? = null
)

@Serializable
data class NestShipperParcelResponse(
    val id: String,
    val length: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val weight: Double? = null
)

@Serializable
data class NestShipperRateResponse(
    val id: String,
    val carrier: String,
    val service: String,
    @SerialName("carrier_account_id")
    val carrierAccountId: String? = null,
    val rate: String,                     // Price as string (e.g., "7.58")
    val currency: String = "USD",
    @SerialName("retail_rate")
    val retailRate: String? = null,
    @SerialName("list_rate")
    val listRate: String? = null,
    @SerialName("delivery_days")
    val deliveryDays: Int? = null,
    @SerialName("delivery_date")
    val deliveryDate: String? = null,
    @SerialName("delivery_date_guaranteed")
    val deliveryDateGuaranteed: Boolean? = null,
    @SerialName("est_delivery_days")
    val estDeliveryDays: Int? = null
)

@Serializable
data class NestShipperLabelResponse(
    val id: String,
    @SerialName("label_url")
    val labelUrl: String,
    @SerialName("label_pdf_url")
    val labelPdfUrl: String? = null,
    @SerialName("label_zpl_url")
    val labelZplUrl: String? = null,
    @SerialName("label_epl2_url")
    val labelEpl2Url: String? = null,
    @SerialName("label_file_type")
    val labelFileType: String = "PNG",
    @SerialName("label_size")
    val labelSize: String = "4x6",
    @SerialName("label_resolution")
    val labelResolution: Int = 300
)

@Serializable
data class NestShipperFee(
    val type: String,
    val amount: String,
    val charged: Boolean = true,
    val refunded: Boolean = false
)

@Serializable
data class NestShipperShipmentResponse(
    val id: String,
    val mode: String = "production",      // "test" or "production"
    val reference: String? = null,
    @SerialName("to_address")
    val toAddress: NestShipperAddressResponse,
    @SerialName("from_address")
    val fromAddress: NestShipperAddressResponse,
    val parcel: NestShipperParcelResponse,
    val rates: List<NestShipperRateResponse> = emptyList(),
    @SerialName("selected_rate")
    val selectedRate: NestShipperRateResponse? = null,
    @SerialName("postage_label")
    val postageLabel: NestShipperLabelResponse? = null,
    @SerialName("tracking_code")
    val trackingCode: String? = null,
    val status: String? = null,           // "unknown", "pre_transit", "in_transit", "delivered", etc.
    val refund: String? = null,           // Refund status
    val fees: List<NestShipperFee> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class NestShipperTrackingDetail(
    val status: String,
    @SerialName("status_detail")
    val statusDetail: String? = null,
    val message: String? = null,
    val datetime: String? = null,
    val source: String? = null,
    @SerialName("tracking_location")
    val trackingLocation: NestShipperTrackingLocation? = null
)

@Serializable
data class NestShipperTrackingLocation(
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val zip: String? = null
)

@Serializable
data class NestShipperTrackerResponse(
    val id: String,
    val mode: String = "production",
    @SerialName("tracking_code")
    val trackingCode: String,
    val carrier: String,
    val status: String,                   // "unknown", "pre_transit", "in_transit", "out_for_delivery", "delivered", etc.
    @SerialName("status_detail")
    val statusDetail: String? = null,
    @SerialName("signed_by")
    val signedBy: String? = null,
    val weight: Double? = null,
    @SerialName("est_delivery_date")
    val estDeliveryDate: String? = null,
    @SerialName("shipment_id")
    val shipmentId: String? = null,
    @SerialName("tracking_details")
    val trackingDetails: List<NestShipperTrackingDetail> = emptyList(),
    @SerialName("public_url")
    val publicUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class NestShipperCarrierAccount(
    val id: String,
    val type: String,                     // "UspsAccount", "UpsAccount", "FedexAccount", etc.
    val description: String? = null,
    val readable: String? = null,         // Human-readable name
    val credentials: Map<String, String> = emptyMap(),
    @SerialName("test_credentials")
    val testCredentials: Map<String, String> = emptyMap(),
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class NestShipperError(
    val code: String? = null,
    val message: String,
    val field: String? = null
)

@Serializable
data class NestShipperErrorResponse(
    val error: NestShipperError
)

// ============================================
// PAGINATION
// ============================================

@Serializable
data class NestShipperListResponse<T>(
    val objects: List<T> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
    @SerialName("before_id")
    val beforeId: String? = null,
    @SerialName("after_id")
    val afterId: String? = null
)

// Type aliases for common list responses
typealias NestShipperShipmentsResponse = NestShipperListResponse<NestShipperShipmentResponse>
typealias NestShipperTrackersResponse = NestShipperListResponse<NestShipperTrackerResponse>

// ============================================
// REFUND
// ============================================

@Serializable
data class NestShipperRefundResponse(
    val id: String,
    @SerialName("tracking_code")
    val trackingCode: String? = null,
    val status: String,                   // "submitted", "refunded", "rejected"
    @SerialName("confirmation_number")
    val confirmationNumber: String? = null,
    @SerialName("shipment_id")
    val shipmentId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

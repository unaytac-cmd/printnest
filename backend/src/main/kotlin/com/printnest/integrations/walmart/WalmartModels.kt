package com.printnest.integrations.walmart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// WALMART STORE & AUTH MODELS
// =====================================================

/**
 * Walmart Store credentials and configuration
 */
@Serializable
data class WalmartStore(
    val storeId: Long,
    val tenantId: Long,
    val clientId: String,
    val clientSecret: String,
    val accessToken: String? = null,
    val tokenExpiresAt: Long? = null,
    val storeName: String? = null,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null
)

/**
 * OAuth token response from Walmart
 */
@Serializable
data class WalmartTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long // seconds
)

/**
 * Token validation response
 */
@Serializable
data class WalmartTokenDetailResponse(
    @SerialName("is_valid") val isValid: Boolean,
    @SerialName("is_channel_match") val isChannelMatch: Boolean? = null,
    @SerialName("expire_at") val expireAt: String? = null
)

// =====================================================
// WALMART ORDER MODELS
// =====================================================

/**
 * Walmart Orders List Response
 */
@Serializable
data class WalmartOrdersResponse(
    val list: WalmartOrderList? = null
)

@Serializable
data class WalmartOrderList(
    val elements: WalmartOrderElements? = null,
    val meta: WalmartMeta? = null
)

@Serializable
data class WalmartOrderElements(
    val order: List<WalmartOrder> = emptyList()
)

@Serializable
data class WalmartMeta(
    val totalCount: Int? = null,
    val limit: Int? = null,
    val nextCursor: String? = null
)

/**
 * Single Walmart Order
 */
@Serializable
data class WalmartOrder(
    val purchaseOrderId: String,
    val customerOrderId: String,
    val orderDate: String,
    val shippingInfo: WalmartShippingInfo,
    val orderLines: WalmartOrderLines,
    val estimatedShipDate: String? = null,
    val estimatedDeliveryDate: String? = null,
    val orderType: String? = null
)

@Serializable
data class WalmartShippingInfo(
    val phone: String? = null,
    val estimatedDeliveryDate: String? = null,
    val estimatedShipDate: String? = null,
    val methodCode: String? = null,
    val postalAddress: WalmartAddress
)

@Serializable
data class WalmartAddress(
    val name: String,
    val address1: String,
    val address2: String? = null,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val addressType: String? = null
)

@Serializable
data class WalmartOrderLines(
    val orderLine: List<WalmartOrderLine>
)

@Serializable
data class WalmartOrderLine(
    val lineNumber: String,
    val item: WalmartOrderItem,
    val charges: WalmartCharges? = null,
    val orderLineQuantity: WalmartQuantity,
    val statusDate: String? = null,
    val orderLineStatuses: WalmartOrderLineStatuses? = null,
    val fulfillment: WalmartFulfillment? = null
)

@Serializable
data class WalmartOrderItem(
    val productName: String,
    val sku: String,
    val imageUrl: String? = null,
    val weight: WalmartWeight? = null
)

@Serializable
data class WalmartWeight(
    val value: String? = null,
    val unit: String? = null
)

@Serializable
data class WalmartCharges(
    val charge: List<WalmartCharge> = emptyList()
)

@Serializable
data class WalmartCharge(
    val chargeType: String,
    val chargeName: String? = null,
    val chargeAmount: WalmartMoney? = null,
    val tax: WalmartTax? = null
)

@Serializable
data class WalmartMoney(
    val currency: String = "USD",
    val amount: Double = 0.0
)

@Serializable
data class WalmartTax(
    val taxName: String? = null,
    val taxAmount: WalmartMoney? = null
)

@Serializable
data class WalmartQuantity(
    val unitOfMeasurement: String = "EACH",
    val amount: String
)

@Serializable
data class WalmartOrderLineStatuses(
    val orderLineStatus: List<WalmartOrderLineStatus> = emptyList()
)

@Serializable
data class WalmartOrderLineStatus(
    val status: String,
    val statusQuantity: WalmartQuantity,
    val cancellationReason: String? = null,
    val trackingInfo: WalmartTrackingInfo? = null
)

@Serializable
data class WalmartFulfillment(
    val fulfillmentOption: String? = null,
    val shipMethod: String? = null,
    val pickUpDateTime: String? = null
)

// =====================================================
// WALMART TRACKING & SHIPMENT MODELS
// =====================================================

@Serializable
data class WalmartTrackingInfo(
    val shipDateTime: Long? = null,
    val carrierName: WalmartCarrier? = null,
    val methodCode: String? = null,
    val trackingNumber: String? = null,
    val trackingURL: String? = null
)

@Serializable
data class WalmartCarrier(
    val carrier: String
)

/**
 * Shipment update request to Walmart
 */
@Serializable
data class WalmartShipmentRequest(
    val orderShipment: WalmartOrderShipment
)

@Serializable
data class WalmartOrderShipment(
    val orderLines: WalmartShipmentOrderLines
)

@Serializable
data class WalmartShipmentOrderLines(
    val orderLine: List<WalmartShipmentOrderLine>
)

@Serializable
data class WalmartShipmentOrderLine(
    val lineNumber: String,
    val sellerOrderId: String? = null,
    val orderLineStatuses: WalmartShipmentStatuses
)

@Serializable
data class WalmartShipmentStatuses(
    val orderLineStatus: List<WalmartShipmentStatus>
)

@Serializable
data class WalmartShipmentStatus(
    val status: String = "Shipped",
    val statusQuantity: WalmartQuantity,
    val trackingInfo: WalmartShipmentTrackingInfo
)

@Serializable
data class WalmartShipmentTrackingInfo(
    val shipDateTime: Long,
    val carrierName: WalmartCarrier,
    val methodCode: String = "Standard",
    val trackingNumber: String,
    val trackingURL: String? = null
)

// =====================================================
// WALMART PRODUCT/ITEM MODELS
// =====================================================

/**
 * Walmart Item Details Response
 */
@Serializable
data class WalmartItemResponse(
    @SerialName("ItemResponse") val itemResponse: List<WalmartProduct> = emptyList()
)

@Serializable
data class WalmartProduct(
    val mart: String? = null,
    val sku: String,
    val wpid: String? = null,
    val upc: String? = null,
    val gtin: String? = null,
    val productName: String? = null,
    val shelf: String? = null,
    val productType: String? = null,
    val price: WalmartPrice? = null,
    val publishedStatus: String? = null,
    val lifecycleStatus: String? = null,
    val variantGroupInfo: WalmartVariantGroupInfo? = null
)

@Serializable
data class WalmartPrice(
    val currency: String = "USD",
    val amount: Double = 0.0
)

@Serializable
data class WalmartVariantGroupInfo(
    val isPrimaryVariant: Boolean? = null,
    val groupingAttributes: List<WalmartVariantAttribute> = emptyList()
)

@Serializable
data class WalmartVariantAttribute(
    val name: String,
    val value: String
)

// =====================================================
// WALMART ACKNOWLEDGE RESPONSE
// =====================================================

@Serializable
data class WalmartAcknowledgeResponse(
    val order: WalmartOrder? = null
)

// =====================================================
// WALMART ERROR RESPONSE
// =====================================================

@Serializable
data class WalmartErrorResponse(
    val errors: WalmartErrors? = null
)

@Serializable
data class WalmartErrors(
    val error: List<WalmartError> = emptyList()
)

@Serializable
data class WalmartError(
    val code: String,
    val field: String? = null,
    val description: String,
    val info: String? = null,
    val severity: String? = null,
    val category: String? = null,
    val causes: List<WalmartErrorCause>? = null,
    val errorIdentifiers: Map<String, String>? = null
)

@Serializable
data class WalmartErrorCause(
    val code: String? = null,
    val field: String? = null,
    val description: String? = null
)

// =====================================================
// ORDER STATUS ENUM
// =====================================================

enum class WalmartOrderStatus(val value: String) {
    CREATED("Created"),
    ACKNOWLEDGED("Acknowledged"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    REFUND("Refund");

    companion object {
        fun fromValue(value: String): WalmartOrderStatus? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}

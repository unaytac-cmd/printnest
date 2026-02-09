package com.printnest.integrations.shipstation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// SHIPSTATION API RESPONSE MODELS
// =====================================================

@Serializable
data class ShipStationStoreResponse(
    val storeId: Long,
    val storeName: String,
    val marketplaceId: Long,
    val marketplaceName: String,
    val accountName: String? = null,
    val email: String? = null,
    val integrationUrl: String? = null,
    val active: Boolean = true,
    val companyName: String? = null,
    val phone: String? = null,
    val publicEmail: String? = null,
    val website: String? = null,
    val refreshDate: String? = null,
    val lastRefreshAttempt: String? = null,
    val createDate: String? = null,
    val modifyDate: String? = null,
    val autoRefresh: Boolean = true
)

@Serializable
data class ShipStationOrdersResponse(
    val orders: List<ShipStationOrderResponse>,
    val total: Int,
    val page: Int,
    val pages: Int
)

@Serializable
data class ShipStationOrderResponse(
    val orderId: Long,
    val orderNumber: String,
    val orderKey: String? = null,
    val orderDate: String,
    val createDate: String,
    val modifyDate: String,
    val paymentDate: String? = null,
    val shipByDate: String? = null,
    val orderStatus: String,
    val customerId: Long? = null,
    val customerUsername: String? = null,
    val customerEmail: String? = null,
    val billTo: ShipStationAddressResponse? = null,
    val shipTo: ShipStationAddressResponse? = null,
    val items: List<ShipStationOrderItemResponse> = emptyList(),
    val orderTotal: Double = 0.0,
    val amountPaid: Double = 0.0,
    val taxAmount: Double = 0.0,
    val shippingAmount: Double = 0.0,
    val customerNotes: String? = null,
    val internalNotes: String? = null,
    val gift: Boolean = false,
    val giftMessage: String? = null,
    val paymentMethod: String? = null,
    val requestedShippingService: String? = null,
    val carrierCode: String? = null,
    val serviceCode: String? = null,
    val packageCode: String? = null,
    val confirmation: String? = null,
    val shipDate: String? = null,
    val holdUntilDate: String? = null,
    val weight: ShipStationWeightResponse? = null,
    val dimensions: ShipStationDimensionsResponse? = null,
    val insuranceOptions: ShipStationInsuranceOptions? = null,
    val internationalOptions: ShipStationInternationalOptions? = null,
    val advancedOptions: ShipStationAdvancedOptions? = null,
    val tagIds: List<Long>? = null,
    val userId: String? = null,
    val externallyFulfilled: Boolean = false,
    val externallyFulfilledBy: String? = null,
    val externallyFulfilledById: Long? = null,
    val externallyFulfilledByName: String? = null,
    val labelMessages: String? = null
)

@Serializable
data class ShipStationAddressResponse(
    val name: String? = null,
    val company: String? = null,
    val street1: String? = null,
    val street2: String? = null,
    val street3: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val residential: Boolean? = null,
    val addressVerified: String? = null
)

@Serializable
data class ShipStationOrderItemResponse(
    val orderItemId: Long,
    val lineItemKey: String? = null,
    val sku: String? = null,
    val name: String? = null,
    val imageUrl: String? = null,
    val weight: ShipStationWeightResponse? = null,
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val taxAmount: Double? = null,
    val shippingAmount: Double? = null,
    val warehouseLocation: String? = null,
    val options: List<ShipStationItemOption>? = null,
    val productId: Long? = null,
    val fulfillmentSku: String? = null,
    val adjustment: Boolean = false,
    val upc: String? = null,
    val createDate: String? = null,
    val modifyDate: String? = null
)

@Serializable
data class ShipStationItemOption(
    val name: String,
    val value: String
)

@Serializable
data class ShipStationWeightResponse(
    val value: Double = 0.0,
    val units: String = "ounces",
    @SerialName("WeightUnits") val weightUnits: Int? = null
)

@Serializable
data class ShipStationDimensionsResponse(
    val length: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val units: String = "inches"
)

@Serializable
data class ShipStationInsuranceOptions(
    val provider: String? = null,
    val insureShipment: Boolean = false,
    val insuredValue: Double = 0.0
)

@Serializable
data class ShipStationInternationalOptions(
    val contents: String? = null,
    val customsItems: List<ShipStationCustomsItem>? = null,
    val nonDelivery: String? = null
)

@Serializable
data class ShipStationCustomsItem(
    val customsItemId: Long? = null,
    val description: String? = null,
    val quantity: Int = 1,
    val value: Double = 0.0,
    val harmonizedTariffCode: String? = null,
    val countryOfOrigin: String? = null
)

@Serializable
data class ShipStationAdvancedOptions(
    val warehouseId: Long? = null,
    val nonMachinable: Boolean = false,
    val saturdayDelivery: Boolean = false,
    val containsAlcohol: Boolean = false,
    val storeId: Long? = null,
    val customField1: String? = null,
    val customField2: String? = null,
    val customField3: String? = null,
    val source: String? = null,
    val mergedOrSplit: Boolean = false,
    val mergedIds: List<Long>? = null,
    val parentId: Long? = null,
    val billToParty: String? = null,
    val billToAccount: String? = null,
    val billToPostalCode: String? = null,
    val billToCountryCode: String? = null,
    val billToMyOtherAccount: String? = null
)

// =====================================================
// SHIPSTATION API REQUEST MODELS
// =====================================================

@Serializable
data class ShipStationMarkShippedRequest(
    val orderId: Long,
    val carrierCode: String,
    val shipDate: String? = null,
    val trackingNumber: String? = null,
    val notifyCustomer: Boolean = true,
    val notifySalesChannel: Boolean = true
)

@Serializable
data class ShipStationCreateLabelRequest(
    val orderId: Long,
    val carrierCode: String,
    val serviceCode: String,
    val packageCode: String? = null,
    val confirmation: String? = null,
    val shipDate: String,
    val weight: ShipStationWeightResponse,
    val dimensions: ShipStationDimensionsResponse? = null,
    val shipFrom: ShipStationAddressResponse,
    val shipTo: ShipStationAddressResponse,
    val insuranceOptions: ShipStationInsuranceOptions? = null,
    val internationalOptions: ShipStationInternationalOptions? = null,
    val advancedOptions: ShipStationAdvancedOptions? = null,
    val testLabel: Boolean = false
)

@Serializable
data class ShipStationLabelResponse(
    val shipmentId: Long,
    val shipmentCost: Double,
    val insuranceCost: Double,
    val trackingNumber: String,
    val labelData: String, // Base64 encoded
    val formData: String? = null
)

// =====================================================
// ERROR RESPONSES
// =====================================================

@Serializable
data class ShipStationErrorResponse(
    @SerialName("ExceptionMessage") val exceptionMessage: String? = null,
    @SerialName("ExceptionType") val exceptionType: String? = null,
    @SerialName("Message") val message: String? = null
)

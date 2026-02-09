package com.printnest.integrations.amazon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// AMAZON STORE CONFIGURATION
// =====================================================

@Serializable
data class AmazonStore(
    val id: Long = 0,
    val tenantId: Long,
    val storeId: Long,  // Internal store reference
    val sellerId: String,
    val marketplaceId: String,
    val refreshToken: String,
    val accessToken: String? = null,
    val tokenExpiresAt: Long? = null,
    val storeName: String? = null,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class AmazonStoreCredentials(
    val sellerId: String,
    val marketplaceId: String,
    val refreshToken: String
)

// =====================================================
// LWA (LOGIN WITH AMAZON) TOKEN MODELS
// =====================================================

@Serializable
data class LwaTokenRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val code: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null
)

@Serializable
data class LwaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Int = 3600
)

@Serializable
data class LwaErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

// =====================================================
// AMAZON SP-API ORDER MODELS
// =====================================================

@Serializable
data class AmazonOrdersResponse(
    val payload: AmazonOrdersPayload? = null,
    val errors: List<AmazonApiError>? = null
)

@Serializable
data class AmazonOrdersPayload(
    @SerialName("Orders") val orders: List<AmazonOrder> = emptyList(),
    @SerialName("NextToken") val nextToken: String? = null,
    @SerialName("LastUpdatedBefore") val lastUpdatedBefore: String? = null,
    @SerialName("CreatedBefore") val createdBefore: String? = null
)

@Serializable
data class AmazonOrder(
    @SerialName("AmazonOrderId") val amazonOrderId: String,
    @SerialName("SellerOrderId") val sellerOrderId: String? = null,
    @SerialName("PurchaseDate") val purchaseDate: String,
    @SerialName("LastUpdateDate") val lastUpdateDate: String? = null,
    @SerialName("OrderStatus") val orderStatus: String,
    @SerialName("FulfillmentChannel") val fulfillmentChannel: String? = null,
    @SerialName("SalesChannel") val salesChannel: String? = null,
    @SerialName("OrderChannel") val orderChannel: String? = null,
    @SerialName("ShipServiceLevel") val shipServiceLevel: String? = null,
    @SerialName("OrderTotal") val orderTotal: AmazonMoney? = null,
    @SerialName("NumberOfItemsShipped") val numberOfItemsShipped: Int = 0,
    @SerialName("NumberOfItemsUnshipped") val numberOfItemsUnshipped: Int = 0,
    @SerialName("PaymentExecutionDetail") val paymentExecutionDetail: List<AmazonPaymentExecutionDetail>? = null,
    @SerialName("PaymentMethod") val paymentMethod: String? = null,
    @SerialName("PaymentMethodDetails") val paymentMethodDetails: List<String>? = null,
    @SerialName("MarketplaceId") val marketplaceId: String? = null,
    @SerialName("ShipmentServiceLevelCategory") val shipmentServiceLevelCategory: String? = null,
    @SerialName("EasyShipShipmentStatus") val easyShipShipmentStatus: String? = null,
    @SerialName("CbaDisplayableShippingLabel") val cbaDisplayableShippingLabel: String? = null,
    @SerialName("OrderType") val orderType: String? = null,
    @SerialName("EarliestShipDate") val earliestShipDate: String? = null,
    @SerialName("LatestShipDate") val latestShipDate: String? = null,
    @SerialName("EarliestDeliveryDate") val earliestDeliveryDate: String? = null,
    @SerialName("LatestDeliveryDate") val latestDeliveryDate: String? = null,
    @SerialName("IsBusinessOrder") val isBusinessOrder: Boolean = false,
    @SerialName("IsPrime") val isPrime: Boolean = false,
    @SerialName("IsPremiumOrder") val isPremiumOrder: Boolean = false,
    @SerialName("IsGlobalExpressEnabled") val isGlobalExpressEnabled: Boolean = false,
    @SerialName("ReplacedOrderId") val replacedOrderId: String? = null,
    @SerialName("IsReplacementOrder") val isReplacementOrder: Boolean = false,
    @SerialName("PromiseResponseDueDate") val promiseResponseDueDate: String? = null,
    @SerialName("IsEstimatedShipDateSet") val isEstimatedShipDateSet: Boolean = false,
    @SerialName("IsSoldByAB") val isSoldByAB: Boolean = false,
    @SerialName("IsIBA") val isIBA: Boolean = false,
    @SerialName("DefaultShipFromLocationAddress") val defaultShipFromLocationAddress: AmazonAddress? = null,
    @SerialName("BuyerInvoicePreference") val buyerInvoicePreference: String? = null,
    @SerialName("BuyerTaxInformation") val buyerTaxInformation: AmazonBuyerTaxInformation? = null,
    @SerialName("FulfillmentInstruction") val fulfillmentInstruction: AmazonFulfillmentInstruction? = null,
    @SerialName("IsISPU") val isISPU: Boolean = false,
    @SerialName("IsAccessPointOrder") val isAccessPointOrder: Boolean = false,
    @SerialName("MarketplaceTaxInfo") val marketplaceTaxInfo: AmazonMarketplaceTaxInfo? = null,
    @SerialName("SellerDisplayName") val sellerDisplayName: String? = null,
    @SerialName("ShippingAddress") val shippingAddress: AmazonAddress? = null,
    @SerialName("BuyerInfo") val buyerInfo: AmazonBuyerInfo? = null
)

@Serializable
data class AmazonMoney(
    @SerialName("CurrencyCode") val currencyCode: String? = null,
    @SerialName("Amount") val amount: String? = null
)

@Serializable
data class AmazonPaymentExecutionDetail(
    @SerialName("Payment") val payment: AmazonMoney? = null,
    @SerialName("PaymentMethod") val paymentMethod: String? = null
)

@Serializable
data class AmazonBuyerTaxInformation(
    @SerialName("BuyerLegalCompanyName") val buyerLegalCompanyName: String? = null,
    @SerialName("BuyerBusinessAddress") val buyerBusinessAddress: String? = null,
    @SerialName("BuyerTaxRegistrationId") val buyerTaxRegistrationId: String? = null,
    @SerialName("BuyerTaxOffice") val buyerTaxOffice: String? = null
)

@Serializable
data class AmazonFulfillmentInstruction(
    @SerialName("FulfillmentSupplySourceId") val fulfillmentSupplySourceId: String? = null
)

@Serializable
data class AmazonMarketplaceTaxInfo(
    @SerialName("TaxClassifications") val taxClassifications: List<AmazonTaxClassification>? = null
)

@Serializable
data class AmazonTaxClassification(
    @SerialName("Name") val name: String? = null,
    @SerialName("Value") val value: String? = null
)

@Serializable
data class AmazonBuyerInfo(
    @SerialName("BuyerEmail") val buyerEmail: String? = null,
    @SerialName("BuyerName") val buyerName: String? = null,
    @SerialName("BuyerCounty") val buyerCounty: String? = null,
    @SerialName("BuyerTaxInfo") val buyerTaxInfo: AmazonBuyerTaxInfo? = null,
    @SerialName("PurchaseOrderNumber") val purchaseOrderNumber: String? = null
)

@Serializable
data class AmazonBuyerTaxInfo(
    @SerialName("CompanyLegalName") val companyLegalName: String? = null,
    @SerialName("TaxingRegion") val taxingRegion: String? = null,
    @SerialName("TaxClassifications") val taxClassifications: List<AmazonTaxClassification>? = null
)

// =====================================================
// AMAZON ADDRESS MODEL
// =====================================================

@Serializable
data class AmazonAddress(
    @SerialName("Name") val name: String? = null,
    @SerialName("AddressLine1") val addressLine1: String? = null,
    @SerialName("AddressLine2") val addressLine2: String? = null,
    @SerialName("AddressLine3") val addressLine3: String? = null,
    @SerialName("City") val city: String? = null,
    @SerialName("County") val county: String? = null,
    @SerialName("District") val district: String? = null,
    @SerialName("StateOrRegion") val stateOrRegion: String? = null,
    @SerialName("Municipality") val municipality: String? = null,
    @SerialName("PostalCode") val postalCode: String? = null,
    @SerialName("CountryCode") val countryCode: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("AddressType") val addressType: String? = null
)

@Serializable
data class AmazonAddressResponse(
    val payload: AmazonAddressPayload? = null,
    val errors: List<AmazonApiError>? = null
)

@Serializable
data class AmazonAddressPayload(
    @SerialName("AmazonOrderId") val amazonOrderId: String? = null,
    @SerialName("ShippingAddress") val shippingAddress: AmazonAddress? = null
)

// =====================================================
// AMAZON ORDER ITEMS MODEL
// =====================================================

@Serializable
data class AmazonOrderItemsResponse(
    val payload: AmazonOrderItemsPayload? = null,
    val errors: List<AmazonApiError>? = null
)

@Serializable
data class AmazonOrderItemsPayload(
    @SerialName("AmazonOrderId") val amazonOrderId: String? = null,
    @SerialName("OrderItems") val orderItems: List<AmazonOrderItem> = emptyList(),
    @SerialName("NextToken") val nextToken: String? = null
)

@Serializable
data class AmazonOrderItem(
    @SerialName("ASIN") val asin: String? = null,
    @SerialName("SellerSKU") val sellerSku: String? = null,
    @SerialName("OrderItemId") val orderItemId: String,
    @SerialName("Title") val title: String? = null,
    @SerialName("QuantityOrdered") val quantityOrdered: Int = 0,
    @SerialName("QuantityShipped") val quantityShipped: Int = 0,
    @SerialName("ProductInfo") val productInfo: AmazonProductInfo? = null,
    @SerialName("PointsGranted") val pointsGranted: AmazonPointsGranted? = null,
    @SerialName("ItemPrice") val itemPrice: AmazonMoney? = null,
    @SerialName("ShippingPrice") val shippingPrice: AmazonMoney? = null,
    @SerialName("ItemTax") val itemTax: AmazonMoney? = null,
    @SerialName("ShippingTax") val shippingTax: AmazonMoney? = null,
    @SerialName("ShippingDiscount") val shippingDiscount: AmazonMoney? = null,
    @SerialName("ShippingDiscountTax") val shippingDiscountTax: AmazonMoney? = null,
    @SerialName("PromotionDiscount") val promotionDiscount: AmazonMoney? = null,
    @SerialName("PromotionDiscountTax") val promotionDiscountTax: AmazonMoney? = null,
    @SerialName("PromotionIds") val promotionIds: List<String>? = null,
    @SerialName("CODFee") val codFee: AmazonMoney? = null,
    @SerialName("CODFeeDiscount") val codFeeDiscount: AmazonMoney? = null,
    @SerialName("IsGift") val isGift: Boolean = false,
    @SerialName("ConditionNote") val conditionNote: String? = null,
    @SerialName("ConditionId") val conditionId: String? = null,
    @SerialName("ConditionSubtypeId") val conditionSubtypeId: String? = null,
    @SerialName("ScheduledDeliveryStartDate") val scheduledDeliveryStartDate: String? = null,
    @SerialName("ScheduledDeliveryEndDate") val scheduledDeliveryEndDate: String? = null,
    @SerialName("PriceDesignation") val priceDesignation: String? = null,
    @SerialName("TaxCollection") val taxCollection: AmazonTaxCollection? = null,
    @SerialName("SerialNumberRequired") val serialNumberRequired: Boolean = false,
    @SerialName("IsTransparency") val isTransparency: Boolean = false,
    @SerialName("IossNumber") val iossNumber: String? = null,
    @SerialName("StoreChainStoreId") val storeChainStoreId: String? = null,
    @SerialName("DeemedResellerCategory") val deemedResellerCategory: String? = null,
    @SerialName("BuyerInfo") val buyerInfo: AmazonOrderItemBuyerInfo? = null,
    @SerialName("BuyerRequestedCancel") val buyerRequestedCancel: AmazonBuyerRequestedCancel? = null
)

@Serializable
data class AmazonProductInfo(
    @SerialName("NumberOfItems") val numberOfItems: Int? = null
)

@Serializable
data class AmazonPointsGranted(
    @SerialName("PointsNumber") val pointsNumber: Int? = null,
    @SerialName("PointsMonetaryValue") val pointsMonetaryValue: AmazonMoney? = null
)

@Serializable
data class AmazonTaxCollection(
    @SerialName("Model") val model: String? = null,
    @SerialName("ResponsibleParty") val responsibleParty: String? = null
)

@Serializable
data class AmazonOrderItemBuyerInfo(
    @SerialName("BuyerCustomizedInfo") val buyerCustomizedInfo: AmazonBuyerCustomizedInfo? = null,
    @SerialName("GiftWrapPrice") val giftWrapPrice: AmazonMoney? = null,
    @SerialName("GiftWrapTax") val giftWrapTax: AmazonMoney? = null,
    @SerialName("GiftMessageText") val giftMessageText: String? = null,
    @SerialName("GiftWrapLevel") val giftWrapLevel: String? = null
)

@Serializable
data class AmazonBuyerCustomizedInfo(
    @SerialName("CustomizedURL") val customizedUrl: String? = null
)

@Serializable
data class AmazonBuyerRequestedCancel(
    @SerialName("IsBuyerRequestedCancel") val isBuyerRequestedCancel: Boolean = false,
    @SerialName("BuyerCancelReason") val buyerCancelReason: String? = null
)

// =====================================================
// AMAZON BUYER INFO RESPONSE
// =====================================================

@Serializable
data class AmazonOrderBuyerInfoResponse(
    val payload: AmazonOrderBuyerInfoPayload? = null,
    val errors: List<AmazonApiError>? = null
)

@Serializable
data class AmazonOrderBuyerInfoPayload(
    @SerialName("AmazonOrderId") val amazonOrderId: String? = null,
    @SerialName("BuyerEmail") val buyerEmail: String? = null,
    @SerialName("BuyerName") val buyerName: String? = null,
    @SerialName("BuyerCounty") val buyerCounty: String? = null,
    @SerialName("BuyerTaxInfo") val buyerTaxInfo: AmazonBuyerTaxInfo? = null,
    @SerialName("PurchaseOrderNumber") val purchaseOrderNumber: String? = null
)

@Serializable
data class AmazonOrderItemsBuyerInfoResponse(
    val payload: AmazonOrderItemsBuyerInfoPayload? = null,
    val errors: List<AmazonApiError>? = null
)

@Serializable
data class AmazonOrderItemsBuyerInfoPayload(
    @SerialName("AmazonOrderId") val amazonOrderId: String? = null,
    @SerialName("OrderItems") val orderItems: List<AmazonOrderItemBuyerInfoItem> = emptyList(),
    @SerialName("NextToken") val nextToken: String? = null
)

@Serializable
data class AmazonOrderItemBuyerInfoItem(
    @SerialName("OrderItemId") val orderItemId: String,
    @SerialName("BuyerCustomizedInfo") val buyerCustomizedInfo: AmazonBuyerCustomizedInfo? = null,
    @SerialName("GiftWrapPrice") val giftWrapPrice: AmazonMoney? = null,
    @SerialName("GiftWrapTax") val giftWrapTax: AmazonMoney? = null,
    @SerialName("GiftMessageText") val giftMessageText: String? = null,
    @SerialName("GiftWrapLevel") val giftWrapLevel: String? = null
)

// =====================================================
// AMAZON SHIPMENT CONFIRMATION MODELS
// =====================================================

@Serializable
data class AmazonShipmentConfirmationRequest(
    val marketplaceId: String,
    val packageDetail: AmazonPackageDetail,
    val cod: AmazonCod? = null
)

@Serializable
data class AmazonPackageDetail(
    val packageReferenceId: String,
    val carrierCode: String,
    val carrierName: String? = null,
    val shippingMethod: String? = null,
    val trackingNumber: String,
    val shipDate: String,
    val shipFromSupplySourceId: String? = null,
    val orderItems: List<AmazonConfirmShipmentOrderItem>
)

@Serializable
data class AmazonConfirmShipmentOrderItem(
    val orderItemId: String,
    val quantity: Int
)

@Serializable
data class AmazonCod(
    val codCollectionMethod: String? = null,
    val codCollectionAmount: AmazonMoney? = null
)

// =====================================================
// AMAZON TRACKING INFO
// =====================================================

@Serializable
data class AmazonTrackingInfo(
    val orderId: String,
    val trackingNumber: String,
    val carrierCode: String,
    val carrierName: String? = null,
    val shippingMethod: String? = null,
    val shipDate: String,
    val orderItems: List<AmazonConfirmShipmentOrderItem>
)

// =====================================================
// AMAZON API ERROR
// =====================================================

@Serializable
data class AmazonApiError(
    val code: String? = null,
    val message: String? = null,
    val details: String? = null
)

// =====================================================
// AMAZON CONSTANTS
// =====================================================

object AmazonConstants {
    const val AMAZON_AUTH_URL = "https://sellercentral.amazon.com/apps/authorize/consent"
    const val LWA_TOKEN_URL = "https://api.amazon.com/auth/o2/token"
    const val SP_API_BASE_URL_NA = "https://sellingpartnerapi-na.amazon.com"
    const val SP_API_BASE_URL_EU = "https://sellingpartnerapi-eu.amazon.com"
    const val SP_API_BASE_URL_FE = "https://sellingpartnerapi-fe.amazon.com"

    const val AUTH_GRANT_TYPE = "authorization_code"
    const val REFRESH_TOKEN_GRANT_TYPE = "refresh_token"

    // US Marketplace ID
    const val US_MARKETPLACE_ID = "ATVPDKIKX0DER"

    // Token expiry buffer (refresh 5 minutes before actual expiry)
    const val TOKEN_EXPIRY_BUFFER_SECONDS = 300

    // Order statuses
    const val ORDER_STATUS_UNSHIPPED = "Unshipped"
    const val ORDER_STATUS_SHIPPED = "Shipped"
    const val ORDER_STATUS_PENDING = "Pending"
    const val ORDER_STATUS_CANCELED = "Canceled"

    // Rate limits
    const val RATE_LIMIT_DELAY_MS = 500L
    const val MAX_ORDERS_PER_SYNC = 1000
}

// =====================================================
// AMAZON SYNC RESULT
// =====================================================

@Serializable
data class AmazonSyncResult(
    val success: Boolean,
    val ordersProcessed: Int = 0,
    val ordersInserted: Int = 0,
    val ordersSkipped: Int = 0,
    val errors: List<String> = emptyList(),
    val nextToken: String? = null
)

// =====================================================
// REQUEST/RESPONSE DTOs FOR ROUTES
// =====================================================

@Serializable
data class AmazonConnectRequest(
    val sellerId: String,
    val marketplaceId: String = AmazonConstants.US_MARKETPLACE_ID
)

@Serializable
data class AmazonAuthUrlResponse(
    val authUrl: String,
    val state: String
)

@Serializable
data class AmazonCallbackRequest(
    val code: String,
    val state: String,
    val sellingPartnerId: String? = null
)

@Serializable
data class AmazonStoreResponse(
    val id: Long,
    val storeId: Long,
    val sellerId: String,
    val marketplaceId: String,
    val storeName: String?,
    val isActive: Boolean,
    val isConnected: Boolean,
    val lastSyncAt: String?
)

@Serializable
data class AmazonDisconnectResponse(
    val success: Boolean,
    val message: String
)

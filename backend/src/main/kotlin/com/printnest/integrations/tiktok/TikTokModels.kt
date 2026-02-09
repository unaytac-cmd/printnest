package com.printnest.integrations.tiktok

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// TIKTOK SHOP STORE MODELS
// =====================================================

@Serializable
data class TikTokStore(
    val id: Long = 0,
    val tenantId: Long,
    val userId: Long,
    val storeId: String,
    val shopId: String? = null,
    val shopName: String? = null,
    val shopCipher: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenExpireAt: Long? = null,
    val refreshTokenExpireAt: Long? = null,
    val region: String? = null,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class TikTokTokenData(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("access_token_expire_in") val accessTokenExpireIn: Long,
    @SerialName("refresh_token_expire_in") val refreshTokenExpireIn: Long,
    @SerialName("open_id") val openId: String? = null,
    @SerialName("seller_name") val sellerName: String? = null,
    @SerialName("seller_base_region") val sellerBaseRegion: String? = null
)

@Serializable
data class TikTokTokenResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TikTokTokenData? = null,
    @SerialName("request_id") val requestId: String? = null
)

// =====================================================
// TIKTOK SHOP API RESPONSE MODELS
// =====================================================

@Serializable
data class TikTokApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    @SerialName("request_id") val requestId: String? = null
)

@Serializable
data class TikTokShopsData(
    val shops: List<TikTokShop> = emptyList()
)

@Serializable
data class TikTokShop(
    val id: String? = null,
    val name: String? = null,
    val region: String? = null,
    val cipher: String? = null,
    @SerialName("seller_type") val sellerType: String? = null
)

// =====================================================
// TIKTOK ORDER MODELS
// =====================================================

@Serializable
data class TikTokOrdersSearchData(
    val orders: List<TikTokOrder> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("next_page_token") val nextPageToken: String? = null
)

@Serializable
data class TikTokOrder(
    val id: String,
    val status: String? = null,
    @SerialName("buyer_message") val buyerMessage: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("create_time") val createTime: Long? = null,
    @SerialName("update_time") val updateTime: Long? = null,
    @SerialName("paid_time") val paidTime: Long? = null,
    @SerialName("delivery_option_id") val deliveryOptionId: String? = null,
    @SerialName("delivery_option_name") val deliveryOptionName: String? = null,
    @SerialName("line_items") val lineItems: List<TikTokOrderItem> = emptyList(),
    val payment: TikTokPayment? = null,
    @SerialName("recipient_address") val recipientAddress: TikTokAddress? = null,
    @SerialName("fulfillment_type") val fulfillmentType: String? = null,
    @SerialName("shipping_type") val shippingType: String? = null,
    @SerialName("is_sample_order") val isSampleOrder: Boolean = false
)

@Serializable
data class TikTokOrderItem(
    val id: String? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("sku_id") val skuId: String? = null,
    @SerialName("sku_name") val skuName: String? = null,
    @SerialName("sku_image") val skuImage: TikTokImage? = null,
    val quantity: Int = 1,
    @SerialName("original_price") val originalPrice: String? = null,
    @SerialName("sale_price") val salePrice: String? = null,
    @SerialName("platform_discount") val platformDiscount: String? = null,
    @SerialName("seller_discount") val sellerDiscount: String? = null,
    @SerialName("sku_type") val skuType: String? = null,
    @SerialName("display_status") val displayStatus: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("rts_time") val rtsTime: Long? = null,
    @SerialName("shipping_provider_id") val shippingProviderId: String? = null,
    @SerialName("shipping_provider_name") val shippingProviderName: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null
)

@Serializable
data class TikTokImage(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class TikTokPayment(
    @SerialName("total_amount") val totalAmount: String? = null,
    @SerialName("sub_total") val subTotal: String? = null,
    @SerialName("shipping_fee") val shippingFee: String? = null,
    @SerialName("shipping_fee_platform_discount") val shippingFeePlatformDiscount: String? = null,
    @SerialName("shipping_fee_seller_discount") val shippingFeeSellerDiscount: String? = null,
    @SerialName("tax") val tax: String? = null,
    @SerialName("small_order_fee") val smallOrderFee: String? = null,
    val currency: String? = null,
    @SerialName("product_total") val productTotal: String? = null,
    @SerialName("original_shipping_fee") val originalShippingFee: String? = null,
    @SerialName("buyer_service_fee") val buyerServiceFee: String? = null
)

@Serializable
data class TikTokAddress(
    val name: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("address_detail") val addressDetail: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    @SerialName("district_info") val districtInfo: List<TikTokDistrictInfo> = emptyList()
)

@Serializable
data class TikTokDistrictInfo(
    @SerialName("address_level") val addressLevel: String? = null,
    @SerialName("address_name") val addressName: String? = null,
    @SerialName("address_level_name") val addressLevelName: String? = null
)

// =====================================================
// TIKTOK SHIPPING MODELS
// =====================================================

@Serializable
data class TikTokShippingProvidersData(
    @SerialName("shipping_providers") val shippingProviders: List<TikTokShippingProvider> = emptyList()
)

@Serializable
data class TikTokShippingProvider(
    val id: String,
    val name: String,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("support_tracking_url") val supportTrackingUrl: Boolean = false
)

@Serializable
data class TikTokShipment(
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("shipping_provider_id") val shippingProviderId: String,
    @SerialName("shipping_provider_name") val shippingProviderName: String? = null
)

@Serializable
data class TikTokUpdateShippingInfoRequest(
    @SerialName("tracking_number") val trackingNumber: String,
    @SerialName("shipping_provider_id") val shippingProviderId: String
)

@Serializable
data class TikTokUpdateShippingInfoResponse(
    val code: Int = 0,
    val message: String = "",
    @SerialName("request_id") val requestId: String? = null
)

// =====================================================
// TIKTOK PRODUCT MODELS
// =====================================================

@Serializable
data class TikTokProductData(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("category_chains") val categoryChains: List<TikTokCategory> = emptyList(),
    @SerialName("main_images") val mainImages: List<TikTokImage> = emptyList(),
    val skus: List<TikTokProductSku> = emptyList(),
    @SerialName("create_time") val createTime: Long? = null,
    @SerialName("update_time") val updateTime: Long? = null
)

@Serializable
data class TikTokCategory(
    val id: String? = null,
    val name: String? = null,
    @SerialName("is_leaf") val isLeaf: Boolean = false,
    @SerialName("parent_id") val parentId: String? = null
)

@Serializable
data class TikTokProductSku(
    val id: String? = null,
    @SerialName("seller_sku") val sellerSku: String? = null,
    val price: TikTokPrice? = null,
    @SerialName("sales_attributes") val salesAttributes: List<TikTokSalesAttribute> = emptyList(),
    @SerialName("stock_infos") val stockInfos: List<TikTokStockInfo> = emptyList()
)

@Serializable
data class TikTokPrice(
    val currency: String? = null,
    @SerialName("original_price") val originalPrice: String? = null,
    @SerialName("sale_price") val salePrice: String? = null
)

@Serializable
data class TikTokSalesAttribute(
    val id: String? = null,
    val name: String? = null,
    @SerialName("value_id") val valueId: String? = null,
    @SerialName("value_name") val valueName: String? = null
)

@Serializable
data class TikTokStockInfo(
    @SerialName("warehouse_id") val warehouseId: String? = null,
    @SerialName("available_stock") val availableStock: Int = 0
)

// =====================================================
// ORDER STATUS ENUM
// =====================================================

enum class TikTokOrderStatus(val value: String) {
    UNPAID("UNPAID"),
    ON_HOLD("ON_HOLD"),
    AWAITING_SHIPMENT("AWAITING_SHIPMENT"),
    PARTIALLY_SHIPPING("PARTIALLY_SHIPPING"),
    AWAITING_COLLECTION("AWAITING_COLLECTION"),
    IN_TRANSIT("IN_TRANSIT"),
    DELIVERED("DELIVERED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED")
}

// =====================================================
// REQUEST/RESPONSE MODELS FOR ROUTES
// =====================================================

@Serializable
data class TikTokAuthResponse(
    val success: Boolean,
    val authUrl: String? = null,
    val message: String? = null
)

@Serializable
data class TikTokSyncResponse(
    val success: Boolean,
    val message: String,
    val ordersSynced: Int = 0,
    val ordersSkipped: Int = 0,
    val errors: List<String> = emptyList()
)

@Serializable
data class TikTokStoreResponse(
    val id: Long,
    val storeId: String,
    val shopId: String?,
    val shopName: String?,
    val region: String?,
    val isActive: Boolean,
    val isConnected: Boolean,
    val lastSyncAt: String?
)

@Serializable
data class TikTokConnectionCheckRequest(
    val storeIds: List<String>
)

@Serializable
data class TikTokDisconnectResponse(
    val success: Boolean,
    val message: String
)

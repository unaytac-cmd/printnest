package com.printnest.integrations.shopify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// SHOPIFY STORE CONFIGURATION
// =====================================================

@Serializable
data class ShopifyStore(
    val id: Long = 0,
    val tenantId: Long,
    val shopUrl: String,                    // e.g., "my-store.myshopify.com"
    val accessToken: String? = null,
    val scope: String? = null,
    val shopOwnerEmail: String? = null,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class ShopifyStoreCreate(
    val shopUrl: String,
    val accessToken: String? = null,
    val scope: String? = null
)

// =====================================================
// SHOPIFY OAUTH MODELS
// =====================================================

@Serializable
data class ShopifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("associated_user_scope") val associatedUserScope: String? = null,
    @SerialName("associated_user") val associatedUser: ShopifyAssociatedUser? = null
)

@Serializable
data class ShopifyAssociatedUser(
    val id: Long,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    @SerialName("account_owner") val accountOwner: Boolean = false
)

// =====================================================
// SHOPIFY ORDER MODELS (GraphQL Responses)
// =====================================================

@Serializable
data class ShopifyGraphQLResponse<T>(
    val data: T? = null,
    val errors: List<ShopifyGraphQLError>? = null
)

@Serializable
data class ShopifyGraphQLError(
    val message: String,
    val locations: List<ShopifyErrorLocation>? = null,
    val path: List<String>? = null
)

@Serializable
data class ShopifyErrorLocation(
    val line: Int,
    val column: Int
)

@Serializable
data class ShopifyOrdersData(
    val shop: ShopifyShopInfo? = null,
    val orders: ShopifyOrderConnection? = null
)

@Serializable
data class ShopifyShopInfo(
    val id: String,
    val email: String? = null
)

@Serializable
data class ShopifyOrderConnection(
    val edges: List<ShopifyOrderEdge> = emptyList(),
    val pageInfo: ShopifyPageInfo
)

@Serializable
data class ShopifyOrderEdge(
    val node: ShopifyOrder,
    val cursor: String
)

@Serializable
data class ShopifyPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean = false,
    val startCursor: String? = null,
    val endCursor: String? = null
)

@Serializable
data class ShopifyOrder(
    val id: String,                         // GraphQL Global ID (gid://shopify/Order/...)
    val name: String,                       // Order number (e.g., "#1001")
    val displayFinancialStatus: String? = null,
    val displayFulfillmentStatus: String? = null,
    val createdAt: String,
    val confirmationNumber: String? = null,
    val customer: ShopifyCustomer? = null,
    val shippingAddress: ShopifyAddress? = null,
    val billingAddress: ShopifyAddress? = null,
    val billingAddressMatchesShippingAddress: Boolean = false,
    val lineItems: ShopifyLineItemConnection? = null,
    val fulfillmentOrders: ShopifyFulfillmentOrderConnection? = null,
    val metafields: ShopifyMetafieldConnection? = null,
    val currentShippingPriceSet: ShopifyMoneyBag? = null
)

@Serializable
data class ShopifyCustomer(
    val id: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null
)

@Serializable
data class ShopifyAddress(
    val name: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val province: String? = null,
    val provinceCode: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val countryCodeV2: String? = null,
    val zip: String? = null,
    val phone: String? = null,
    val company: String? = null
)

@Serializable
data class ShopifyLineItemConnection(
    val edges: List<ShopifyLineItemEdge> = emptyList()
)

@Serializable
data class ShopifyLineItemEdge(
    val node: ShopifyLineItem
)

@Serializable
data class ShopifyLineItem(
    val id: String,                         // GraphQL Global ID
    val title: String,                      // Product name
    val quantity: Int,
    val sku: String? = null,
    val variantTitle: String? = null,
    val variant: ShopifyVariant? = null,
    val product: ShopifyProductInfo? = null,
    val image: ShopifyImage? = null
)

@Serializable
data class ShopifyVariant(
    val displayName: String? = null,
    val title: String? = null,
    val selectedOptions: List<ShopifySelectedOption> = emptyList()
)

@Serializable
data class ShopifySelectedOption(
    val name: String,
    val value: String
)

@Serializable
data class ShopifyProductInfo(
    val id: String,
    val title: String
)

@Serializable
data class ShopifyImage(
    val src: String? = null,
    val url: String? = null
)

// =====================================================
// SHOPIFY FULFILLMENT MODELS
// =====================================================

@Serializable
data class ShopifyFulfillmentOrderConnection(
    val edges: List<ShopifyFulfillmentOrderEdge> = emptyList()
)

@Serializable
data class ShopifyFulfillmentOrderEdge(
    val node: ShopifyFulfillmentOrder
)

@Serializable
data class ShopifyFulfillmentOrder(
    val id: String,
    val status: String
)

@Serializable
data class ShopifyFulfillmentResponse(
    val fulfillmentCreateV2: ShopifyFulfillmentCreateResult? = null,
    val fulfillmentTrackingInfoUpdate: ShopifyTrackingUpdateResult? = null
)

@Serializable
data class ShopifyFulfillmentCreateResult(
    val fulfillment: ShopifyFulfillment? = null,
    val userErrors: List<ShopifyUserError> = emptyList()
)

@Serializable
data class ShopifyTrackingUpdateResult(
    val fulfillment: ShopifyFulfillmentWithTracking? = null,
    val userErrors: List<ShopifyUserError> = emptyList()
)

@Serializable
data class ShopifyFulfillment(
    val id: String
)

@Serializable
data class ShopifyFulfillmentWithTracking(
    val id: String,
    val trackingInfo: List<ShopifyTrackingInfo> = emptyList()
)

@Serializable
data class ShopifyTrackingInfo(
    val number: String? = null,
    val company: String? = null,
    val url: String? = null
)

@Serializable
data class ShopifyUserError(
    val field: List<String>? = null,
    val message: String
)

// =====================================================
// SHOPIFY METAFIELD MODELS
// =====================================================

@Serializable
data class ShopifyMetafieldConnection(
    val nodes: List<ShopifyMetafield> = emptyList()
)

@Serializable
data class ShopifyMetafield(
    val id: String,
    val namespace: String,
    val key: String,
    val updatedAt: String? = null,
    val jsonValue: kotlinx.serialization.json.JsonElement? = null
)

// =====================================================
// SHOPIFY MONEY MODELS
// =====================================================

@Serializable
data class ShopifyMoneyBag(
    val presentmentMoney: ShopifyMoney? = null,
    val shopMoney: ShopifyMoney? = null
)

@Serializable
data class ShopifyMoney(
    val amount: String,
    val currencyCode: String
)

// =====================================================
// SHOPIFY PRODUCT MODELS (REST API)
// =====================================================

@Serializable
data class ShopifyProductsResponse(
    val products: List<ShopifyProductFull> = emptyList()
)

@Serializable
data class ShopifyProductResponse(
    val product: ShopifyProductFull
)

@Serializable
data class ShopifyProductFull(
    val id: Long,
    val title: String,
    val handle: String? = null,
    val vendor: String? = null,
    @SerialName("product_type") val productType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val status: String? = null,
    val tags: String? = null,
    @SerialName("body_html") val bodyHtml: String? = null,
    val variants: List<ShopifyVariantFull> = emptyList(),
    val images: List<ShopifyProductImage> = emptyList(),
    val options: List<ShopifyProductOption> = emptyList()
)

@Serializable
data class ShopifyVariantFull(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    val title: String,
    val price: String,
    val sku: String? = null,
    val position: Int = 1,
    @SerialName("inventory_policy") val inventoryPolicy: String? = null,
    @SerialName("compare_at_price") val compareAtPrice: String? = null,
    @SerialName("fulfillment_service") val fulfillmentService: String? = null,
    @SerialName("inventory_management") val inventoryManagement: String? = null,
    val option1: String? = null,
    val option2: String? = null,
    val option3: String? = null,
    val taxable: Boolean = true,
    val barcode: String? = null,
    val grams: Int = 0,
    @SerialName("image_id") val imageId: Long? = null,
    val weight: Double = 0.0,
    @SerialName("weight_unit") val weightUnit: String = "lb",
    @SerialName("inventory_item_id") val inventoryItemId: Long? = null,
    @SerialName("inventory_quantity") val inventoryQuantity: Int = 0,
    @SerialName("old_inventory_quantity") val oldInventoryQuantity: Int = 0,
    @SerialName("requires_shipping") val requiresShipping: Boolean = true
)

@Serializable
data class ShopifyProductImage(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    val position: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val alt: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val src: String,
    @SerialName("variant_ids") val variantIds: List<Long> = emptyList()
)

@Serializable
data class ShopifyProductOption(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    val name: String,
    val position: Int = 1,
    val values: List<String> = emptyList()
)

// =====================================================
// SHOPIFY WEBHOOK MODELS
// =====================================================

@Serializable
data class ShopifyWebhookRegistration(
    val webhook: ShopifyWebhookConfig
)

@Serializable
data class ShopifyWebhookConfig(
    val topic: String,
    val address: String,
    val format: String = "json"
)

@Serializable
data class ShopifyWebhookResponse(
    val webhook: ShopifyWebhookInfo? = null
)

@Serializable
data class ShopifyWebhooksResponse(
    val webhooks: List<ShopifyWebhookInfo> = emptyList()
)

@Serializable
data class ShopifyWebhookInfo(
    val id: Long,
    val address: String,
    val topic: String,
    val format: String = "json",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("api_version") val apiVersion: String? = null
)

// =====================================================
// GDPR WEBHOOK PAYLOADS
// =====================================================

@Serializable
data class ShopifyCustomerDataRequestPayload(
    @SerialName("shop_id") val shopId: Long,
    @SerialName("shop_domain") val shopDomain: String,
    val customer: ShopifyWebhookCustomer,
    @SerialName("orders_requested") val ordersRequested: List<Long> = emptyList()
)

@Serializable
data class ShopifyCustomerRedactPayload(
    @SerialName("shop_id") val shopId: Long,
    @SerialName("shop_domain") val shopDomain: String,
    val customer: ShopifyWebhookCustomer,
    @SerialName("orders_to_redact") val ordersToRedact: List<Long> = emptyList()
)

@Serializable
data class ShopifyShopRedactPayload(
    @SerialName("shop_id") val shopId: Long,
    @SerialName("shop_domain") val shopDomain: String
)

@Serializable
data class ShopifyWebhookCustomer(
    val id: Long,
    val email: String? = null,
    val phone: String? = null
)

// =====================================================
// INTERNAL CONVERSION HELPERS
// =====================================================

/**
 * Extract numeric ID from Shopify GraphQL Global ID
 * e.g., "gid://shopify/Order/123456789" -> "123456789"
 */
fun extractShopifyId(globalId: String): String {
    return globalId.substringAfterLast("/")
}

/**
 * Parse size and color from selected options
 */
fun parseSizeAndColor(selectedOptions: List<ShopifySelectedOption>): Pair<String, String> {
    var size = ""
    var color = ""

    for (option in selectedOptions) {
        when (option.name.lowercase()) {
            "size" -> size = option.value
            "color" -> color = option.value
        }
    }

    return Pair(size, color)
}

/**
 * Format address for display
 */
fun ShopifyAddress.formatForDisplay(): String {
    val lines = listOfNotNull(
        name,
        address1,
        address2?.takeIf { it.isNotBlank() },
        listOfNotNull(city, province, zip).joinToString(", ").takeIf { it.isNotBlank() },
        country
    )
    return lines.joinToString("\n")
}

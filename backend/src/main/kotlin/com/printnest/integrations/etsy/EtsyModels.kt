package com.printnest.integrations.etsy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// ETSY STORE & AUTHENTICATION MODELS
// =====================================================

/**
 * Represents an Etsy store connected to PrintNest
 */
@Serializable
data class EtsyStore(
    val id: Long = 0,
    val tenantId: Long,
    val shopId: Long,
    val shopName: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiry: Long, // Unix timestamp
    val userId: Long? = null, // Etsy user ID
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * OAuth 2.0 token response from Etsy
 */
@Serializable
data class EtsyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long // seconds until expiry
)

/**
 * OAuth state stored in Redis during auth flow
 */
@Serializable
data class EtsyAuthState(
    val storeId: Long,
    val tenantId: Long,
    val codeVerifier: String,
    val state: String,
    val redirectUri: String,
    val createdAt: Long = System.currentTimeMillis()
)

// =====================================================
// ETSY ORDER / RECEIPT MODELS
// =====================================================

/**
 * Etsy Receipt - represents an order/purchase on Etsy
 */
@Serializable
data class EtsyReceipt(
    @SerialName("receipt_id") val receiptId: Long,
    @SerialName("receipt_type") val receiptType: Int = 0,
    @SerialName("seller_user_id") val sellerUserId: Long? = null,
    @SerialName("seller_email") val sellerEmail: String? = null,
    @SerialName("buyer_user_id") val buyerUserId: Long? = null,
    @SerialName("buyer_email") val buyerEmail: String? = null,
    @SerialName("name") val buyerName: String? = null,
    @SerialName("first_line") val addressFirstLine: String? = null,
    @SerialName("second_line") val addressSecondLine: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("zip") val zip: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("formatted_address") val formattedAddress: String? = null,
    @SerialName("country_iso") val countryIso: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_email") val paymentEmail: String? = null,
    @SerialName("message_from_seller") val messageFromSeller: String? = null,
    @SerialName("message_from_buyer") val messageFromBuyer: String? = null,
    @SerialName("message_from_payment") val messageFromPayment: String? = null,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("is_shipped") val isShipped: Boolean = false,
    @SerialName("create_timestamp") val createTimestamp: Long? = null,
    @SerialName("created_timestamp") val createdTimestamp: Long? = null,
    @SerialName("update_timestamp") val updateTimestamp: Long? = null,
    @SerialName("updated_timestamp") val updatedTimestamp: Long? = null,
    @SerialName("is_gift") val isGift: Boolean = false,
    @SerialName("gift_message") val giftMessage: String? = null,
    @SerialName("grandtotal") val grandtotal: EtsyMoney? = null,
    @SerialName("subtotal") val subtotal: EtsyMoney? = null,
    @SerialName("total_price") val totalPrice: EtsyMoney? = null,
    @SerialName("total_shipping_cost") val totalShippingCost: EtsyMoney? = null,
    @SerialName("total_tax_cost") val totalTaxCost: EtsyMoney? = null,
    @SerialName("total_vat_cost") val totalVatCost: EtsyMoney? = null,
    @SerialName("discount_amt") val discountAmt: EtsyMoney? = null,
    @SerialName("gift_wrap_price") val giftWrapPrice: EtsyMoney? = null,
    @SerialName("shipments") val shipments: List<EtsyShipment> = emptyList(),
    @SerialName("transactions") val transactions: List<EtsyTransaction> = emptyList()
)

/**
 * Etsy money representation with currency
 */
@Serializable
data class EtsyMoney(
    val amount: Int = 0, // Amount in smallest currency unit (cents)
    val divisor: Int = 100,
    @SerialName("currency_code") val currencyCode: String = "USD"
) {
    fun toDouble(): Double = amount.toDouble() / divisor
}

/**
 * Etsy Transaction - represents a line item in an order
 */
@Serializable
data class EtsyTransaction(
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("seller_user_id") val sellerUserId: Long? = null,
    @SerialName("buyer_user_id") val buyerUserId: Long? = null,
    @SerialName("create_timestamp") val createTimestamp: Long? = null,
    @SerialName("created_timestamp") val createdTimestamp: Long? = null,
    @SerialName("paid_timestamp") val paidTimestamp: Long? = null,
    @SerialName("shipped_timestamp") val shippedTimestamp: Long? = null,
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("listing_image_id") val listingImageId: Long? = null,
    @SerialName("receipt_id") val receiptId: Long? = null,
    @SerialName("is_digital") val isDigital: Boolean = false,
    @SerialName("file_data") val fileData: String? = null,
    @SerialName("listing_id") val listingId: Long? = null,
    @SerialName("sku") val sku: String? = null,
    @SerialName("product_id") val productId: Long? = null,
    @SerialName("transaction_type") val transactionType: String? = null,
    @SerialName("price") val price: EtsyMoney? = null,
    @SerialName("shipping_cost") val shippingCost: EtsyMoney? = null,
    @SerialName("variations") val variations: List<EtsyVariation> = emptyList(),
    @SerialName("product_data") val productData: EtsyProductData? = null,
    @SerialName("shipping_profile_id") val shippingProfileId: Long? = null,
    @SerialName("min_processing_days") val minProcessingDays: Int? = null,
    @SerialName("max_processing_days") val maxProcessingDays: Int? = null,
    @SerialName("shipping_method") val shippingMethod: String? = null,
    @SerialName("shipping_upgrade") val shippingUpgrade: String? = null,
    @SerialName("expected_ship_date") val expectedShipDate: Long? = null,
    @SerialName("buyer_coupon") val buyerCoupon: Double = 0.0,
    @SerialName("shop_coupon") val shopCoupon: Double = 0.0
)

/**
 * Etsy Variation - product variation (size, color, etc.)
 */
@Serializable
data class EtsyVariation(
    @SerialName("property_id") val propertyId: Long? = null,
    @SerialName("value_id") val valueId: Long? = null,
    @SerialName("formatted_name") val formattedName: String? = null,
    @SerialName("formatted_value") val formattedValue: String? = null
)

/**
 * Etsy Product Data within a transaction
 */
@Serializable
data class EtsyProductData(
    @SerialName("product_id") val productId: Long? = null,
    @SerialName("sku") val sku: String? = null,
    @SerialName("property_values") val propertyValues: List<EtsyPropertyValue> = emptyList(),
    @SerialName("offerings") val offerings: List<EtsyOffering> = emptyList()
)

@Serializable
data class EtsyPropertyValue(
    @SerialName("property_id") val propertyId: Long? = null,
    @SerialName("property_name") val propertyName: String? = null,
    @SerialName("scale_id") val scaleId: Long? = null,
    @SerialName("scale_name") val scaleName: String? = null,
    @SerialName("value_ids") val valueIds: List<Long> = emptyList(),
    @SerialName("values") val values: List<String> = emptyList()
)

@Serializable
data class EtsyOffering(
    @SerialName("offering_id") val offeringId: Long? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("price") val price: EtsyMoney? = null
)

// =====================================================
// ETSY SHIPMENT MODELS
// =====================================================

/**
 * Etsy Shipment - tracking information for an order
 */
@Serializable
data class EtsyShipment(
    @SerialName("receipt_shipping_id") val receiptShippingId: Long? = null,
    @SerialName("shipment_notification_timestamp") val shipmentNotificationTimestamp: Long? = null,
    @SerialName("carrier_name") val carrierName: String? = null,
    @SerialName("tracking_code") val trackingCode: String? = null
)

/**
 * Request to create a shipment/tracking update on Etsy
 */
@Serializable
data class EtsyCreateShipmentRequest(
    @SerialName("tracking_code") val trackingCode: String,
    @SerialName("carrier_name") val carrierName: String,
    @SerialName("send_bcc") val sendBcc: Boolean = false
)

// =====================================================
// ETSY LISTING MODELS
// =====================================================

/**
 * Etsy Listing - a product listing
 */
@Serializable
data class EtsyListing(
    @SerialName("listing_id") val listingId: Long,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("shop_id") val shopId: Long? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("state") val state: String? = null, // active, removed, sold_out, etc.
    @SerialName("creation_timestamp") val creationTimestamp: Long? = null,
    @SerialName("created_timestamp") val createdTimestamp: Long? = null,
    @SerialName("ending_timestamp") val endingTimestamp: Long? = null,
    @SerialName("original_creation_timestamp") val originalCreationTimestamp: Long? = null,
    @SerialName("last_modified_timestamp") val lastModifiedTimestamp: Long? = null,
    @SerialName("updated_timestamp") val updatedTimestamp: Long? = null,
    @SerialName("state_timestamp") val stateTimestamp: Long? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("shop_section_id") val shopSectionId: Long? = null,
    @SerialName("featured_rank") val featuredRank: Int? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("num_favorers") val numFavorers: Int = 0,
    @SerialName("non_taxable") val nonTaxable: Boolean = false,
    @SerialName("is_taxable") val isTaxable: Boolean = true,
    @SerialName("is_customizable") val isCustomizable: Boolean = false,
    @SerialName("is_personalizable") val isPersonalizable: Boolean = false,
    @SerialName("personalization_is_required") val personalizationIsRequired: Boolean = false,
    @SerialName("personalization_char_count_max") val personalizationCharCountMax: Int? = null,
    @SerialName("personalization_instructions") val personalizationInstructions: String? = null,
    @SerialName("listing_type") val listingType: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("materials") val materials: List<String> = emptyList(),
    @SerialName("shipping_profile_id") val shippingProfileId: Long? = null,
    @SerialName("return_policy_id") val returnPolicyId: Long? = null,
    @SerialName("processing_min") val processingMin: Int? = null,
    @SerialName("processing_max") val processingMax: Int? = null,
    @SerialName("who_made") val whoMade: String? = null,
    @SerialName("when_made") val whenMade: String? = null,
    @SerialName("is_supply") val isSupply: Boolean = false,
    @SerialName("item_weight") val itemWeight: Double? = null,
    @SerialName("item_weight_unit") val itemWeightUnit: String? = null,
    @SerialName("item_length") val itemLength: Double? = null,
    @SerialName("item_width") val itemWidth: Double? = null,
    @SerialName("item_height") val itemHeight: Double? = null,
    @SerialName("item_dimensions_unit") val itemDimensionsUnit: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("style") val style: List<String> = emptyList(),
    @SerialName("file_data") val fileData: String? = null,
    @SerialName("has_variations") val hasVariations: Boolean = false,
    @SerialName("should_auto_renew") val shouldAutoRenew: Boolean = true,
    @SerialName("language") val language: String? = null,
    @SerialName("price") val price: EtsyMoney? = null,
    @SerialName("taxonomy_id") val taxonomyId: Long? = null,
    @SerialName("production_partners") val productionPartners: List<EtsyProductionPartner> = emptyList(),
    @SerialName("skus") val skus: List<String> = emptyList(),
    @SerialName("views") val views: Int = 0,
    @SerialName("images") val images: List<EtsyListingImage> = emptyList(),
    @SerialName("videos") val videos: List<EtsyListingVideo> = emptyList(),
    @SerialName("inventory") val inventory: EtsyListingInventory? = null
)

@Serializable
data class EtsyProductionPartner(
    @SerialName("production_partner_id") val productionPartnerId: Long? = null,
    @SerialName("partner_name") val partnerName: String? = null,
    @SerialName("location") val location: String? = null
)

/**
 * Etsy Listing Image
 */
@Serializable
data class EtsyListingImage(
    @SerialName("listing_id") val listingId: Long? = null,
    @SerialName("listing_image_id") val listingImageId: Long? = null,
    @SerialName("hex_code") val hexCode: String? = null,
    @SerialName("red") val red: Int? = null,
    @SerialName("green") val green: Int? = null,
    @SerialName("blue") val blue: Int? = null,
    @SerialName("hue") val hue: Int? = null,
    @SerialName("saturation") val saturation: Int? = null,
    @SerialName("brightness") val brightness: Int? = null,
    @SerialName("is_black_and_white") val isBlackAndWhite: Boolean = false,
    @SerialName("creation_tsz") val creationTsz: Long? = null,
    @SerialName("created_timestamp") val createdTimestamp: Long? = null,
    @SerialName("rank") val rank: Int = 0,
    @SerialName("url_75x75") val url75x75: String? = null,
    @SerialName("url_170x135") val url170x135: String? = null,
    @SerialName("url_570xN") val url570xN: String? = null,
    @SerialName("url_fullxfull") val urlFullxfull: String? = null,
    @SerialName("full_height") val fullHeight: Int? = null,
    @SerialName("full_width") val fullWidth: Int? = null,
    @SerialName("alt_text") val altText: String? = null
)

@Serializable
data class EtsyListingVideo(
    @SerialName("video_id") val videoId: Long? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("video_state") val videoState: String? = null
)

@Serializable
data class EtsyListingInventory(
    @SerialName("products") val products: List<EtsyProduct> = emptyList(),
    @SerialName("price_on_property") val priceOnProperty: List<Long> = emptyList(),
    @SerialName("quantity_on_property") val quantityOnProperty: List<Long> = emptyList(),
    @SerialName("sku_on_property") val skuOnProperty: List<Long> = emptyList()
)

/**
 * Etsy Product - a variant within a listing
 */
@Serializable
data class EtsyProduct(
    @SerialName("product_id") val productId: Long? = null,
    @SerialName("sku") val sku: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("offerings") val offerings: List<EtsyOffering> = emptyList(),
    @SerialName("property_values") val propertyValues: List<EtsyPropertyValue> = emptyList()
)

// =====================================================
// ETSY SHOP MODELS
// =====================================================

/**
 * Etsy Shop information
 */
@Serializable
data class EtsyShop(
    @SerialName("shop_id") val shopId: Long,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("shop_name") val shopName: String? = null,
    @SerialName("create_date") val createDate: Long? = null,
    @SerialName("created_timestamp") val createdTimestamp: Long? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("announcement") val announcement: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("is_vacation") val isVacation: Boolean = false,
    @SerialName("vacation_message") val vacationMessage: String? = null,
    @SerialName("sale_message") val saleMessage: String? = null,
    @SerialName("digital_sale_message") val digitalSaleMessage: String? = null,
    @SerialName("update_date") val updateDate: Long? = null,
    @SerialName("updated_timestamp") val updatedTimestamp: Long? = null,
    @SerialName("listing_active_count") val listingActiveCount: Int = 0,
    @SerialName("digital_listing_count") val digitalListingCount: Int = 0,
    @SerialName("login_name") val loginName: String? = null,
    @SerialName("accepts_custom_requests") val acceptsCustomRequests: Boolean = false,
    @SerialName("policy_welcome") val policyWelcome: String? = null,
    @SerialName("policy_payment") val policyPayment: String? = null,
    @SerialName("policy_shipping") val policyShipping: String? = null,
    @SerialName("policy_refunds") val policyRefunds: String? = null,
    @SerialName("policy_additional") val policyAdditional: String? = null,
    @SerialName("policy_seller_info") val policySellerInfo: String? = null,
    @SerialName("policy_update_date") val policyUpdateDate: Long? = null,
    @SerialName("policy_has_private_receipt_info") val policyHasPrivateReceiptInfo: Boolean = false,
    @SerialName("vacation_autoreply") val vacationAutoreply: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("image_url_760x100") val imageUrl760x100: String? = null,
    @SerialName("num_favorers") val numFavorers: Int = 0,
    @SerialName("languages") val languages: List<String> = emptyList(),
    @SerialName("icon_url_fullxfull") val iconUrlFullxfull: String? = null,
    @SerialName("is_using_structured_policies") val isUsingStructuredPolicies: Boolean = false,
    @SerialName("has_onboarded_structured_policies") val hasOnboardedStructuredPolicies: Boolean = false,
    @SerialName("include_dispute_form_link") val includeDisputeFormLink: Boolean = false,
    @SerialName("is_direct_checkout_onboarded") val isDirectCheckoutOnboarded: Boolean = false,
    @SerialName("is_etsy_payments_onboarded") val isEtsyPaymentsOnboarded: Boolean = false,
    @SerialName("is_calculated_eligible") val isCalculatedEligible: Boolean = false,
    @SerialName("is_opted_in_to_buyer_promise") val isOptedInToBuyerPromise: Boolean = false,
    @SerialName("is_shop_us_based") val isShopUsBased: Boolean = false,
    @SerialName("transaction_sold_count") val transactionSoldCount: Int = 0,
    @SerialName("shipping_from_country_iso") val shippingFromCountryIso: String? = null,
    @SerialName("shop_location_country_iso") val shopLocationCountryIso: String? = null,
    @SerialName("review_count") val reviewCount: Int? = null,
    @SerialName("review_average") val reviewAverage: Double? = null
)

// =====================================================
// ETSY API RESPONSE WRAPPERS
// =====================================================

/**
 * Paginated response wrapper for Etsy API
 */
@Serializable
data class EtsyPaginatedResponse<T>(
    val count: Int = 0,
    val results: List<T> = emptyList()
)

/**
 * Etsy API error response
 */
@Serializable
data class EtsyErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

// =====================================================
// ETSY CARRIER MAPPING
// =====================================================

/**
 * Mapping of common carrier names to Etsy carrier names
 */
object EtsyCarrierMapping {
    private val carrierMap = mapOf(
        "usps" to "usps",
        "ups" to "ups",
        "fedex" to "fedex",
        "dhl" to "dhl",
        "dhl-express" to "dhl",
        "dhl_express" to "dhl",
        "canadapost" to "canada-post",
        "canada-post" to "canada-post",
        "canada_post" to "canada-post",
        "royal-mail" to "royal-mail",
        "royal_mail" to "royal-mail",
        "royalmail" to "royal-mail",
        "auspost" to "australia-post",
        "australia-post" to "australia-post",
        "australia_post" to "australia-post",
        "dpd" to "dpd",
        "gls" to "gls",
        "hermes" to "hermes-uk",
        "hermes-uk" to "hermes-uk",
        "parcelforce" to "parcelforce",
        "tnt" to "tnt",
        "other" to "other"
    )

    fun getEtsyCarrier(carrier: String): String {
        val normalized = carrier.lowercase().trim()
        return carrierMap[normalized] ?: "other"
    }
}

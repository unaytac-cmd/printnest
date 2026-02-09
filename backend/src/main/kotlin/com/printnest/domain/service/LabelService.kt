package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.*
import com.printnest.domain.tables.Orders
import com.printnest.integrations.amazon.AmazonService
import com.printnest.integrations.easypost.EasyPostException
import com.printnest.integrations.easypost.EasyPostService
import com.printnest.integrations.etsy.EtsyService
import com.printnest.integrations.shopify.ShopifyService
import com.printnest.integrations.tiktok.TikTokService
import com.printnest.integrations.walmart.WalmartService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Label Service
 *
 * Handles label-related operations including:
 * - Requesting refunds for shipping labels (EasyPost and USPS)
 * - Sending tracking information to marketplaces
 */
class LabelService(
    private val orderRepository: OrderRepository,
    private val shippingRepository: ShippingRepository,
    private val settingsRepository: SettingsRepository,
    private val easyPostService: EasyPostService,
    private val etsyService: EtsyService,
    private val shopifyService: ShopifyService,
    private val amazonService: AmazonService,
    private val walmartService: WalmartService,
    private val tiktokService: TikTokService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(LabelService::class.java)

    // Marketplace IDs (matching TeeDropV2 reference)
    companion object {
        const val MARKETPLACE_ETSY = 1
        const val MARKETPLACE_AMAZON = 2
        const val MARKETPLACE_WALMART = 4
        const val MARKETPLACE_TIKTOK = 5
        const val MARKETPLACE_SHOPIFY = 6
    }

    // =====================================================
    // LABEL REFUND
    // =====================================================

    /**
     * Request a refund for an order's shipping label
     *
     * Checks the label provider (easypost or usps) and calls the appropriate refund API.
     *
     * @param tenantId Tenant ID
     * @param orderId Order ID
     * @return LabelRefundResponse with refund status
     */
    suspend fun requestLabelRefund(
        tenantId: Long,
        orderId: Long
    ): Result<LabelRefundResponse> {
        logger.info("Requesting label refund for order: $orderId, tenant: $tenantId")

        // Get order with shipping info
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        val orderInfo = order.orderInfo
            ?: return Result.failure(IllegalStateException("Order has no order info"))

        val shipping = orderInfo.shipping
            ?: return Result.failure(IllegalStateException("Order has no shipping info"))

        val trackingCode = shipping.trackingCode
            ?: return Result.failure(IllegalStateException("Order has no tracking code"))

        val labelUrl = shipping.labelUrl
        if (labelUrl.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Order has no label URL - label may not have been purchased"))
        }

        // Determine label provider from order info or metadata
        val labelProvider = getLabelProvider(order, shipping)
        val carrier = getCarrierFromService(shipping.methodName ?: "")

        logger.info("Label provider: $labelProvider, carrier: $carrier, tracking: $trackingCode")

        return when (labelProvider) {
            "usps" -> requestUspsRefund(trackingCode)
            "easypost" -> requestEasypostRefund(tenantId, trackingCode, carrier)
            else -> {
                // Default to EasyPost for unknown providers
                logger.warn("Unknown label provider: $labelProvider, defaulting to EasyPost")
                requestEasypostRefund(tenantId, trackingCode, carrier)
            }
        }
    }

    /**
     * Request refund through EasyPost API
     */
    private suspend fun requestEasypostRefund(
        tenantId: Long,
        trackingCode: String,
        carrier: String
    ): Result<LabelRefundResponse> {
        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured for tenant: $tenantId"))

        return easyPostService.requestRefund(apiKey, trackingCode, carrier)
            .map { refund ->
                LabelRefundResponse(
                    success = refund.status == RefundStatus.SUBMITTED || refund.status == RefundStatus.REFUNDED,
                    trackingCode = trackingCode,
                    provider = "easypost",
                    status = refund.status.name.lowercase(),
                    message = refund.message ?: "Refund request ${refund.status.name.lowercase()}"
                )
            }
    }

    /**
     * Request refund through USPS API (placeholder for future implementation)
     *
     * Note: USPS direct API integration is not yet implemented in PrintNest.
     * This is a placeholder that will return an error until USPS integration is added.
     */
    private suspend fun requestUspsRefund(trackingCode: String): Result<LabelRefundResponse> {
        logger.warn("USPS direct refund not yet implemented for tracking: $trackingCode")
        return Result.failure(
            IllegalStateException("USPS direct refund is not yet implemented. " +
                "Please contact support or request refund manually through USPS.")
        )
    }

    /**
     * Determine the label provider from order data
     */
    private fun getLabelProvider(order: OrderFull, shipping: ShippingSelection): String {
        // Check if we have label provider info in order metadata
        // The label_provider field might be stored in order_info or shipping metadata

        // Try to get from the label's shipment ID prefix
        val shipmentId = shipping.shipmentId
        if (shipmentId != null) {
            // EasyPost shipment IDs start with "shp_"
            if (shipmentId.startsWith("shp_")) {
                return "easypost"
            }
        }

        // Check if tracking code follows USPS patterns
        val trackingCode = shipping.trackingCode
        if (trackingCode != null) {
            // USPS tracking typically starts with 94 or 93 and is 22-34 digits
            if ((trackingCode.startsWith("94") || trackingCode.startsWith("93")) &&
                trackingCode.length in 22..34 &&
                trackingCode.all { it.isDigit() }
            ) {
                // Could be either EasyPost or direct USPS
                // Default to EasyPost since that's more common
                return "easypost"
            }
        }

        // Default to EasyPost
        return "easypost"
    }

    /**
     * Extract carrier name from service string
     */
    private fun getCarrierFromService(service: String): String {
        val serviceUpper = service.uppercase()
        return when {
            serviceUpper.startsWith("USPS") -> "USPS"
            serviceUpper.startsWith("UPS") || serviceUpper.startsWith("UPSDAP") -> "UPS"
            serviceUpper.startsWith("FEDEX") -> "FedEx"
            serviceUpper.startsWith("DHL") -> "DHL"
            else -> service.split(" ").firstOrNull() ?: "USPS"
        }
    }

    /**
     * Get EasyPost API key for a tenant
     */
    private fun getEasyPostApiKey(tenantId: Long): String? {
        // First check environment variable (for shared key)
        val envKey = System.getenv("EASYPOST_API_KEY")
        if (!envKey.isNullOrBlank()) {
            return envKey
        }

        // Then check tenant-specific settings
        return settingsRepository.getShippingSettings(tenantId)?.easypostApiKey
    }

    // =====================================================
    // SEND TRACKING TO MARKETPLACE
    // =====================================================

    /**
     * Send tracking information to the marketplace where the order originated
     *
     * @param tenantId Tenant ID
     * @param orderId Order ID
     * @return SendTrackingResponse with results for each order (including combined orders)
     */
    suspend fun sendTrackingToMarketplace(
        tenantId: Long,
        orderId: Long
    ): Result<SendTrackingResponse> {
        logger.info("Sending tracking to marketplace for order: $orderId, tenant: $tenantId")

        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        val orderInfo = order.orderInfo
        val shipping = orderInfo?.shipping

        // Check if we have tracking info
        if (shipping?.trackingCode.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Order has no tracking code. Please buy label first."))
        }

        val trackingCode = shipping!!.trackingCode!!
        val trackingUrl = order.trackingUrl ?: ""

        val results = mutableListOf<SendTrackingResult>()

        // Handle combined orders - send tracking to each combined order as well
        val combinedOrderIds = getCombinedOrderIds(order)
        if (combinedOrderIds.isNotEmpty()) {
            logger.info("Order $orderId has combined orders: $combinedOrderIds")
            for (combinedOrderId in combinedOrderIds) {
                val result = processOrderTracking(tenantId, combinedOrderId, trackingCode, trackingUrl, useMainOrderTracking = true)
                results.add(result)
            }
        }

        // Process main order
        val mainResult = processOrderTracking(tenantId, orderId, trackingCode, trackingUrl, useMainOrderTracking = false)
        results.add(mainResult)

        val allSuccess = results.all { it.success }
        val successCount = results.count { it.success }
        val failCount = results.count { !it.success }

        return Result.success(SendTrackingResponse(
            success = allSuccess,
            message = if (allSuccess) {
                "Tracking sent to marketplace for ${results.size} order(s)"
            } else {
                "$successCount succeeded, $failCount failed"
            },
            results = results
        ))
    }

    /**
     * Process tracking update for a single order
     */
    private suspend fun processOrderTracking(
        tenantId: Long,
        orderId: Long,
        trackingCode: String,
        trackingUrl: String,
        useMainOrderTracking: Boolean
    ): SendTrackingResult {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return SendTrackingResult(
                orderId = orderId,
                success = false,
                message = "Order not found"
            )

        val orderInfo = order.orderInfo
        val shipping = orderInfo?.shipping

        // Check if already sent to marketplace
        if (isTrackingSentToMarketplace(order)) {
            return SendTrackingResult(
                orderId = orderId,
                success = false,
                message = "Tracking has already been sent to marketplace"
            )
        }

        // Manual orders don't need marketplace updates
        val intOrderId = order.intOrderId
        if (intOrderId?.startsWith("MAN_") == true) {
            // Mark as sent and return success with warning
            markTrackingSentToMarketplace(orderId, tenantId)
            return SendTrackingResult(
                orderId = orderId,
                success = true,
                warning = true,
                trackingCode = trackingCode,
                message = "Manual order - cannot send tracking to marketplace"
            )
        }

        // Get store info to determine marketplace
        val storeId = order.storeId
        if (storeId == null) {
            return SendTrackingResult(
                orderId = orderId,
                success = false,
                message = "Order has no associated store"
            )
        }

        // Determine carrier from service
        val carrier = getCarrierFromService(shipping?.methodName ?: "USPS")
            .lowercase()
            .let { if (it == "upsdap") "ups" else it }

        // Get marketplace ID from store (we need to look this up)
        val marketplaceId = getMarketplaceId(tenantId, storeId)
        if (marketplaceId == null) {
            return SendTrackingResult(
                orderId = orderId,
                success = false,
                message = "Could not determine marketplace for store: $storeId"
            )
        }

        // Send tracking based on marketplace
        val sendResult = sendToMarketplace(
            tenantId = tenantId,
            orderId = orderId,
            order = order,
            marketplaceId = marketplaceId,
            trackingCode = trackingCode,
            trackingUrl = trackingUrl,
            carrier = carrier
        )

        if (sendResult.isSuccess && sendResult.getOrNull() == true) {
            // Mark as sent to marketplace
            markTrackingSentToMarketplace(orderId, tenantId)
            return SendTrackingResult(
                orderId = orderId,
                success = true,
                trackingCode = trackingCode,
                message = "Tracking sent to marketplace"
            )
        } else {
            val errorMessage = sendResult.exceptionOrNull()?.message ?: "Unknown error"
            return SendTrackingResult(
                orderId = orderId,
                success = false,
                message = "Error: $errorMessage"
            )
        }
    }

    /**
     * Send tracking to the appropriate marketplace
     */
    private suspend fun sendToMarketplace(
        tenantId: Long,
        orderId: Long,
        order: OrderFull,
        marketplaceId: Int,
        trackingCode: String,
        trackingUrl: String,
        carrier: String
    ): Result<Boolean> {
        val intOrderId = order.intOrderId ?: return Result.failure(IllegalStateException("No internal order ID"))

        return when (marketplaceId) {
            MARKETPLACE_ETSY -> {
                etsyService.updateTracking(orderId, trackingCode, carrier)
                    .map { true }
            }

            MARKETPLACE_AMAZON -> {
                amazonService.updateTracking(
                    tenantId = tenantId,
                    orderId = orderId,
                    trackingNumber = trackingCode,
                    carrier = carrier.uppercase()
                ).map { true }
            }

            MARKETPLACE_SHOPIFY -> {
                shopifyService.updateFulfillment(
                    orderId = orderId,
                    trackingNumber = trackingCode,
                    carrier = carrier.uppercase(),
                    trackingUrl = trackingUrl.takeIf { it.isNotBlank() }
                ).map { true }
            }

            MARKETPLACE_WALMART -> {
                val orderInfo = order.orderInfo
                val purchaseOrderId = extractPurchaseOrderId(orderInfo)
                    ?: return Result.failure(IllegalStateException("No purchase order ID for Walmart order"))

                val lineNumber = extractLineNumber(orderInfo) ?: "1"
                val credentials = getWalmartCredentials(tenantId, order.storeId ?: 0)
                    ?: return Result.failure(IllegalStateException("Walmart credentials not found"))

                walmartService.updateTracking(
                    storeId = order.storeId ?: 0,
                    clientId = credentials.first,
                    clientSecret = credentials.second,
                    purchaseOrderId = purchaseOrderId,
                    lineNumber = lineNumber,
                    orderId = orderId,
                    carrier = carrier.uppercase(),
                    trackingNumber = trackingCode,
                    trackingUrl = trackingUrl.takeIf { it.isNotBlank() }
                )
            }

            MARKETPLACE_TIKTOK -> {
                val orderInfo = order.orderInfo
                val deliveryOptionId = extractDeliveryOptionId(orderInfo)
                    ?: return Result.failure(IllegalStateException("No delivery option ID for TikTok order"))

                val storeId = order.storeId?.toString()
                    ?: return Result.failure(IllegalStateException("No store ID"))

                tiktokService.updateTracking(
                    tenantId = tenantId,
                    storeId = storeId,
                    orderId = intOrderId,
                    trackingNumber = trackingCode,
                    carrier = carrier.uppercase(),
                    deliveryOptionId = deliveryOptionId
                )
            }

            else -> {
                Result.failure(IllegalStateException("Unsupported marketplace: $marketplaceId"))
            }
        }
    }

    /**
     * Check if tracking has already been sent to marketplace
     */
    private fun isTrackingSentToMarketplace(order: OrderFull): Boolean {
        // Check order_info for sent_to_marketplace flag
        val orderInfo = order.orderInfo ?: return false
        val shipping = orderInfo.shipping ?: return false

        // The flag is stored in order_info -> shipping -> sent_to_marketplace
        // Since we're using data classes, we need to check the raw JSON or add the field
        // For now, we'll assume it's not sent if we can't determine
        return false
    }

    /**
     * Mark tracking as sent to marketplace in order_info
     */
    private fun markTrackingSentToMarketplace(orderId: Long, tenantId: Long) {
        orderRepository.updateOrderInfoFlag(orderId, tenantId, "shipping.sent_to_marketplace", true)
    }

    /**
     * Get combined order IDs from order
     */
    private fun getCombinedOrderIds(order: OrderFull): List<Long> {
        // Combined orders are stored in order_info or a separate field
        // This is a simplified implementation - adjust based on actual data structure
        return emptyList()
    }

    /**
     * Get marketplace ID from store
     */
    private fun getMarketplaceId(tenantId: Long, storeId: Long): Int? {
        // This should query the stores table to get marketplace_id
        // For now, return null which will cause an error - implement based on actual store repository
        return orderRepository.getStoreMarketplaceId(storeId, tenantId)
    }

    /**
     * Extract purchase order ID for Walmart orders
     */
    private fun extractPurchaseOrderId(orderInfo: OrderInfoFull?): String? {
        // Purchase order ID is typically stored in order_info
        return null // Implement based on actual data structure
    }

    /**
     * Extract line number for Walmart orders
     */
    private fun extractLineNumber(orderInfo: OrderInfoFull?): String? {
        return "1" // Default to 1
    }

    /**
     * Extract delivery option ID for TikTok orders
     */
    private fun extractDeliveryOptionId(orderInfo: OrderInfoFull?): String? {
        // Delivery option ID is stored in order_info for TikTok orders
        return null // Implement based on actual data structure
    }

    /**
     * Get Walmart API credentials for a store
     */
    private fun getWalmartCredentials(tenantId: Long, storeId: Long): Pair<String, String>? {
        // Query the store for Walmart credentials
        return null // Implement based on actual store repository
    }
}

// =====================================================
// RESPONSE DATA CLASSES
// =====================================================

@kotlinx.serialization.Serializable
data class LabelRefundResponse(
    val success: Boolean,
    val trackingCode: String,
    val provider: String,
    val status: String,
    val message: String
)

@kotlinx.serialization.Serializable
data class SendTrackingResponse(
    val success: Boolean,
    val message: String,
    val results: List<SendTrackingResult>
)

@kotlinx.serialization.Serializable
data class SendTrackingResult(
    val orderId: Long,
    val success: Boolean,
    val warning: Boolean = false,
    val trackingCode: String? = null,
    val message: String
)

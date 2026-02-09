package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.*
import com.printnest.domain.tables.Orders
import com.printnest.integrations.easypost.EasyPostService
import com.printnest.integrations.easypost.EasyPostException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Shipping Service
 *
 * Handles all shipping-related business logic including:
 * - Rate calculation
 * - Label creation and management
 * - Address validation
 * - Tracking
 * - Refunds
 */
class ShippingService(
    private val shippingRepository: ShippingRepository,
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val easyPostService: EasyPostService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShippingService::class.java)

    // Default from address for when tenant has no configured address
    private val defaultFromAddress = ShippingAddress(
        name = "PrintNest Fulfillment",
        street1 = "123 Print Street",
        city = "Houston",
        state = "TX",
        zip = "77001",
        country = "US",
        phone = "8322900939"
    )

    // =====================================================
    // RATE CALCULATION
    // =====================================================

    /**
     * Calculate shipping cost for an order
     */
    suspend fun calculateShippingCost(
        orderId: Long,
        tenantId: Long,
        destination: ShippingAddress? = null,
        weight: BigDecimal? = null
    ): Result<RateResponse> {
        logger.info("Calculating shipping cost for order: $orderId")

        // Get order details
        val order = orderRepository.findByIdWithProducts(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        // Get EasyPost API key from tenant settings
        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured for tenant: $tenantId"))

        // Determine destination address
        val toAddress = destination ?: order.shippingAddress?.let {
            ShippingAddress(
                name = it.name ?: order.customerName ?: "",
                company = it.company,
                street1 = it.street1 ?: "",
                street2 = it.street2,
                city = it.city ?: "",
                state = it.state ?: "",
                zip = it.postalCode ?: "",
                country = it.country ?: "US",
                phone = it.phone
            )
        } ?: return Result.failure(IllegalArgumentException("No destination address provided"))

        // Get from address (tenant's return address)
        val fromAddress = getFromAddress(tenantId, order.userId)

        // Calculate total weight from products if not provided
        val totalWeight = weight ?: calculateOrderWeight(order)

        // Create parcel
        val parcel = Parcel(weight = totalWeight)

        // Create customs info for international shipments
        val customsInfo = if (toAddress.country != "US") {
            createCustomsInfo(order, totalWeight)
        } else null

        // Create shipment and get rates
        return easyPostService.createShipment(
            apiKey = apiKey,
            fromAddress = fromAddress,
            toAddress = toAddress,
            parcel = parcel,
            customsInfo = customsInfo,
            reference = "Order-${order.id}"
        ).map { shipment ->
            // Apply any tenant-specific pricing adjustments
            val adjustedRates = applyPricingAdjustments(shipment.rates, tenantId)

            RateResponse(
                shipmentId = shipment.id,
                rates = adjustedRates,
                messages = shipment.messages.mapNotNull { it.message }
            )
        }
    }

    /**
     * Get available shipping rates for an order
     */
    suspend fun getAvailableRates(
        orderId: Long,
        tenantId: Long
    ): Result<RateResponse> {
        logger.info("Getting available rates for order: $orderId")

        val order = orderRepository.findByIdWithProducts(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        // Check if we already have a shipment ID from previous calculation
        val existingShipmentId = order.orderInfo?.shipping?.shipmentId

        if (existingShipmentId != null) {
            // Regenerate rates for existing shipment
            val apiKey = getEasyPostApiKey(tenantId)
                ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

            return easyPostService.getRates(apiKey, existingShipmentId).map { rates ->
                val adjustedRates = applyPricingAdjustments(rates, tenantId)
                RateResponse(
                    shipmentId = existingShipmentId,
                    rates = adjustedRates,
                    messages = emptyList()
                )
            }
        }

        // Calculate new rates
        return calculateShippingCost(orderId, tenantId)
    }

    // =====================================================
    // LABEL CREATION
    // =====================================================

    /**
     * Create a shipping label for an order
     */
    suspend fun createShippingLabel(
        orderId: Long,
        tenantId: Long,
        rateId: String,
        shipmentId: String
    ): Result<ShippingLabelResponse> {
        logger.info("Creating shipping label for order: $orderId with rate: $rateId")

        // Verify order exists and is ready for shipping
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        if (order.orderStatus < OrderStatus.PENDING.code) {
            return Result.failure(IllegalStateException("Order is not ready for shipping. Current status: ${order.orderStatus}"))
        }

        // Check if a label already exists for this order
        val existingLabel = shippingRepository.findLabelByOrderId(orderId, tenantId)
        if (existingLabel != null && !existingLabel.isVoided) {
            return Result.success(ShippingLabelResponse(
                id = existingLabel.id,
                orderId = orderId,
                carrier = existingLabel.carrier ?: "",
                service = existingLabel.service ?: "",
                trackingNumber = existingLabel.trackingNumber ?: "",
                trackingUrl = existingLabel.trackingUrl ?: "",
                labelUrl = existingLabel.labelUrl ?: "",
                labelFormat = existingLabel.labelFormat,
                cost = existingLabel.cost ?: BigDecimal.ZERO,
                rateId = existingLabel.rateId ?: "",
                shipmentId = existingLabel.shipmentId ?: "",
                createdAt = existingLabel.createdAt
            ))
        }

        // Get API key
        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

        // Buy the label
        return easyPostService.buyLabel(apiKey, shipmentId, rateId).map { shipment ->
            val postageLabel = shipment.postageLabel
            val selectedRate = shipment.selectedRate

            // Save label to database
            val label = shippingRepository.createLabel(
                tenantId = tenantId,
                orderId = orderId,
                carrier = selectedRate?.carrier,
                service = selectedRate?.service,
                trackingNumber = shipment.trackingCode,
                trackingUrl = shipment.tracker?.publicUrl,
                labelUrl = postageLabel?.labelUrl,
                labelFormat = postageLabel?.labelFileType ?: "PDF",
                rateId = rateId,
                shipmentId = shipmentId,
                cost = selectedRate?.rate,
                metadata = json.encodeToString(
                    PostageLabel.serializer(),
                    postageLabel ?: PostageLabel()
                )
            )

            // Update order with tracking info
            updateOrderWithShippingInfo(
                orderId = orderId,
                tenantId = tenantId,
                trackingNumber = shipment.trackingCode,
                trackingUrl = shipment.tracker?.publicUrl,
                carrier = selectedRate?.carrier,
                service = selectedRate?.service,
                labelUrl = postageLabel?.labelUrl,
                cost = selectedRate?.rate
            )

            logger.info("Label created for order $orderId: tracking ${shipment.trackingCode}")

            ShippingLabelResponse(
                id = label.id,
                orderId = orderId,
                carrier = selectedRate?.carrier ?: "",
                service = selectedRate?.service ?: "",
                trackingNumber = shipment.trackingCode ?: "",
                trackingUrl = shipment.tracker?.publicUrl ?: "",
                labelUrl = postageLabel?.labelUrl ?: "",
                labelFormat = postageLabel?.labelFileType ?: "PDF",
                cost = selectedRate?.rate ?: BigDecimal.ZERO,
                rateId = rateId,
                shipmentId = shipmentId,
                createdAt = label.createdAt
            )
        }
    }

    /**
     * Get a shipping label by ID
     */
    fun getShippingLabel(labelId: Long, tenantId: Long): ShippingLabelResponse? {
        val label = shippingRepository.findLabelById(labelId, tenantId) ?: return null

        return ShippingLabelResponse(
            id = label.id,
            orderId = label.orderId,
            carrier = label.carrier ?: "",
            service = label.service ?: "",
            trackingNumber = label.trackingNumber ?: "",
            trackingUrl = label.trackingUrl ?: "",
            labelUrl = label.labelUrl ?: "",
            labelFormat = label.labelFormat,
            cost = label.cost ?: BigDecimal.ZERO,
            rateId = label.rateId ?: "",
            shipmentId = label.shipmentId ?: "",
            createdAt = label.createdAt
        )
    }

    /**
     * Get label for an order
     */
    fun getLabelByOrderId(orderId: Long, tenantId: Long): ShippingLabelResponse? {
        val label = shippingRepository.findLabelByOrderId(orderId, tenantId) ?: return null

        return ShippingLabelResponse(
            id = label.id,
            orderId = label.orderId,
            carrier = label.carrier ?: "",
            service = label.service ?: "",
            trackingNumber = label.trackingNumber ?: "",
            trackingUrl = label.trackingUrl ?: "",
            labelUrl = label.labelUrl ?: "",
            labelFormat = label.labelFormat,
            cost = label.cost ?: BigDecimal.ZERO,
            rateId = label.rateId ?: "",
            shipmentId = label.shipmentId ?: "",
            createdAt = label.createdAt
        )
    }

    // =====================================================
    // TRACKING
    // =====================================================

    /**
     * Get tracking information for a shipment
     */
    suspend fun updateTrackingStatus(
        orderId: Long,
        tenantId: Long
    ): Result<TrackingResponse> {
        logger.info("Updating tracking status for order: $orderId")

        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        val trackingNumber = order.trackingNumber
            ?: return Result.failure(IllegalArgumentException("Order has no tracking number"))

        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

        // Extract carrier from methodName (e.g., "USPS Priority Mail" -> "USPS")
        val carrier = order.orderInfo?.shipping?.methodName?.split(" ")?.firstOrNull()

        return easyPostService.getTrackingInfo(apiKey, trackingNumber, carrier)
    }

    /**
     * Get tracking info by tracking code
     */
    suspend fun getTrackingInfo(
        tenantId: Long,
        trackingCode: String,
        carrier: String? = null
    ): Result<TrackingResponse> {
        logger.info("Getting tracking info for: $trackingCode")

        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

        return easyPostService.getTrackingInfo(apiKey, trackingCode, carrier)
    }

    // =====================================================
    // VOID/REFUND
    // =====================================================

    /**
     * Void a shipping label
     */
    suspend fun voidLabel(
        labelId: Long,
        tenantId: Long
    ): Result<ShippingRefundResponse> {
        logger.info("Voiding label: $labelId")

        val label = shippingRepository.findLabelById(labelId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Label not found: $labelId"))

        if (label.isVoided) {
            return Result.failure(IllegalStateException("Label is already voided"))
        }

        val shipmentId = label.shipmentId
            ?: return Result.failure(IllegalStateException("Label has no shipment ID"))

        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

        return easyPostService.refundLabel(apiKey, shipmentId).also { result ->
            if (result.isSuccess) {
                // Mark label as voided in database
                shippingRepository.voidLabel(labelId, tenantId)

                // Clear tracking info from order
                clearOrderShippingInfo(label.orderId, tenantId)

                logger.info("Label $labelId voided successfully")
            }
        }
    }

    /**
     * Void a label by order ID
     */
    suspend fun voidLabelByOrderId(
        orderId: Long,
        tenantId: Long
    ): Result<ShippingRefundResponse> {
        val label = shippingRepository.findLabelByOrderId(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("No active label found for order: $orderId"))

        return voidLabel(label.id, tenantId)
    }

    // =====================================================
    // ADDRESS VALIDATION
    // =====================================================

    /**
     * Validate a shipping address
     */
    suspend fun validateAddress(
        tenantId: Long,
        address: ShippingAddress
    ): Result<ValidateAddressResponse> {
        logger.info("Validating address for tenant: $tenantId")

        val apiKey = getEasyPostApiKey(tenantId)
            ?: return Result.failure(IllegalStateException("EasyPost API key not configured"))

        return easyPostService.createAndVerifyAddress(apiKey, address)
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

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

    /**
     * Get the from address for shipping labels
     */
    private fun getFromAddress(tenantId: Long, userId: Long): ShippingAddress {
        // Try to get tenant's default label address
        val labelAddress = shippingRepository.findDefaultLabelAddress(tenantId)
        if (labelAddress != null) {
            return labelAddress.toShippingAddress()
        }

        // Fallback to default address
        return defaultFromAddress
    }

    /**
     * Calculate total weight of an order
     */
    private fun calculateOrderWeight(order: OrderFull): BigDecimal {
        var totalWeight = BigDecimal.ZERO

        for (product in order.products) {
            val productWeight = product.productDetail?.variantModification?.weight ?: BigDecimal("4.0")
            val quantity = product.quantity
            totalWeight += productWeight * BigDecimal(quantity)
        }

        // Minimum weight of 4 oz
        if (totalWeight < BigDecimal("4.0")) {
            totalWeight = BigDecimal("4.0")
        }

        return totalWeight.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Create customs info for international shipments
     */
    private fun createCustomsInfo(order: OrderFull, totalWeight: BigDecimal): CustomsInfo {
        val totalQuantity = order.products.sumOf { it.quantity }
        val totalValue = if (order.totalAmount > BigDecimal.ZERO) {
            order.totalAmount
        } else {
            BigDecimal("5.00")
        }

        return CustomsInfo(
            eelPfc = "NOEEI 30.37(a)",
            customsCertify = true,
            customsSigner = order.customerName ?: "PrintNest",
            contentsType = "merchandise",
            contentsExplanation = "Printed apparel",
            restrictionType = "none",
            nonDeliveryOption = "return",
            customsItems = listOf(
                CustomsItem(
                    description = "T-shirts",
                    quantity = totalQuantity,
                    weight = totalWeight,
                    value = totalValue,
                    hsTariffNumber = "610910",
                    originCountry = "US"
                )
            )
        )
    }

    /**
     * Apply tenant-specific pricing adjustments to rates
     */
    private fun applyPricingAdjustments(rates: List<ShippingRate>, tenantId: Long): List<ShippingRate> {
        val profile = shippingRepository.findDefaultShippingProfile(tenantId)
        val methods = shippingRepository.findAllShippingMethods(tenantId)

        // Parse profile pricing
        val profilePricing = try {
            profile?.let {
                json.decodeFromString<ShippingProfilePricing>(it.profilePricing)
            }
        } catch (e: Exception) {
            null
        }

        return rates.map { rate ->
            // Find matching method
            val method = methods.find {
                it.apiMethod == "${rate.carrier} ${rate.service}"
            }

            var adjustedPrice = rate.rate

            // Apply profile-level adjustments
            if (profilePricing != null) {
                val differenceAmount = profilePricing.differenceAmount ?: BigDecimal.ZERO
                if (profilePricing.differenceType == "0" || profilePricing.differenceType == "percent") {
                    // Percentage adjustment
                    adjustedPrice += adjustedPrice * differenceAmount / BigDecimal(100)
                } else {
                    // Dollar adjustment
                    adjustedPrice += differenceAmount
                }
            }

            // Apply method-level extra fee
            if (method != null) {
                adjustedPrice += method.extraFee
            }

            rate.copy(rate = adjustedPrice.setScale(2, RoundingMode.HALF_UP))
        }.sortedBy { it.rate }
    }

    /**
     * Update order with shipping information
     */
    private fun updateOrderWithShippingInfo(
        orderId: Long,
        tenantId: Long,
        trackingNumber: String?,
        trackingUrl: String?,
        carrier: String?,
        service: String?,
        labelUrl: String?,
        cost: BigDecimal?
    ) {
        val order = orderRepository.findById(orderId, tenantId) ?: return
        val currentOrderInfo = order.orderInfo ?: OrderInfoFull()

        val updatedShipping = currentOrderInfo.shipping?.copy(
            trackingCode = trackingNumber,
            labelUrl = labelUrl
        ) ?: ShippingSelection(
            trackingCode = trackingNumber,
            labelUrl = labelUrl
        )

        val updatedOrderInfo = currentOrderInfo.copy(shipping = updatedShipping)
        orderRepository.updateOrderInfo(orderId, tenantId, updatedOrderInfo)

        // Also update order tracking fields
        orderRepository.update(orderId, tenantId) {
            it[Orders.trackingNumber] = trackingNumber
            it[Orders.trackingUrl] = trackingUrl
        }
    }

    /**
     * Clear shipping info from order (after void)
     */
    private fun clearOrderShippingInfo(orderId: Long, tenantId: Long) {
        val order = orderRepository.findById(orderId, tenantId) ?: return
        val currentOrderInfo = order.orderInfo ?: return

        val updatedShipping = currentOrderInfo.shipping?.copy(
            labelUrl = null,
            trackingCode = null
        )

        val updatedOrderInfo = currentOrderInfo.copy(shipping = updatedShipping)
        orderRepository.updateOrderInfo(orderId, tenantId, updatedOrderInfo)

        orderRepository.update(orderId, tenantId) {
            it[Orders.trackingNumber] = null
            it[Orders.trackingUrl] = null
        }
    }

    /**
     * Get label history for an order
     */
    fun getLabelHistory(orderId: Long, tenantId: Long): List<ShippingLabelHistory> {
        return shippingRepository.getLabelHistory(orderId, tenantId)
    }
}

// =====================================================
// INTERNAL DTOs
// =====================================================

@kotlinx.serialization.Serializable
private data class ShippingProfilePricing(
    val differenceType: String? = null, // "0" = percent, "1" = dollar
    val differenceAmount: @kotlinx.serialization.Serializable(with = BigDecimalSerializer::class) java.math.BigDecimal? = null,
    val lightFirst: String? = null,
    val lightSecond: String? = null,
    val lightThird: String? = null,
    val heavyFirst: String? = null,
    val heavySecond: String? = null,
    val heavyThird: String? = null
)

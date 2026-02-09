package com.printnest.integrations.nestshipper

import org.slf4j.LoggerFactory

/**
 * NestShipper Service
 *
 * Business logic layer for shipping operations.
 * Coordinates between the API client and application logic.
 */
class NestShipperService(
    private val client: NestShipperClient
) {
    private val logger = LoggerFactory.getLogger(NestShipperService::class.java)

    // ============================================
    // CREDENTIAL VALIDATION
    // ============================================

    /**
     * Validate NestShipper API credentials
     */
    suspend fun validateCredentials(apiKey: String): Boolean {
        return client.validateCredentials(apiKey)
    }

    // ============================================
    // ADDRESS OPERATIONS
    // ============================================

    /**
     * Create an address
     */
    suspend fun createAddress(
        apiKey: String,
        address: NestShipperAddressRequest,
        verify: Boolean = false
    ): Result<NestShipperAddressResponse> {
        logger.info("Creating address for: ${address.city}, ${address.state}")
        return client.createAddress(apiKey, address, verify)
    }

    /**
     * Verify an address
     */
    suspend fun verifyAddress(
        apiKey: String,
        addressId: String
    ): Result<NestShipperAddressResponse> {
        logger.info("Verifying address: $addressId")
        return client.verifyAddress(apiKey, addressId)
    }

    // ============================================
    // SHIPMENT & LABEL OPERATIONS
    // ============================================

    /**
     * Create a shipment and get available rates
     */
    suspend fun createShipment(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        reference: String? = null,
        carrierAccounts: List<String>? = null
    ): Result<NestShipperShipmentResponse> {
        logger.info("Creating shipment to: ${toAddress.city}, ${toAddress.state}")

        val shipmentRequest = NestShipperShipmentRequest(
            toAddress = toAddress,
            fromAddress = fromAddress,
            parcel = parcel,
            reference = reference,
            carrierAccounts = carrierAccounts
        )

        return client.createShipment(apiKey, shipmentRequest)
    }

    /**
     * Get a shipment by ID
     */
    suspend fun getShipment(
        apiKey: String,
        shipmentId: String
    ): Result<NestShipperShipmentResponse> {
        return client.getShipment(apiKey, shipmentId)
    }

    /**
     * Buy a label for a shipment using the selected rate
     */
    suspend fun buyLabel(
        apiKey: String,
        shipmentId: String,
        rateId: String,
        insurance: Double? = null
    ): Result<NestShipperShipmentResponse> {
        logger.info("Buying label for shipment: $shipmentId with rate: $rateId")
        return client.buyLabel(apiKey, shipmentId, rateId, insurance)
    }

    /**
     * Create shipment and buy label in one call (one-call buy)
     * This is more efficient as it reduces API calls.
     */
    suspend fun createAndBuyLabel(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        carrier: String,
        service: String,
        reference: String? = null,
        carrierAccounts: List<String>? = null
    ): Result<NestShipperShipmentResponse> {
        logger.info("Creating shipment and buying label with $carrier $service")

        val shipmentRequest = NestShipperShipmentRequest(
            toAddress = toAddress,
            fromAddress = fromAddress,
            parcel = parcel,
            service = service,
            reference = reference,
            carrierAccounts = carrierAccounts
        )

        return client.createAndBuyLabel(apiKey, shipmentRequest)
    }

    /**
     * Get the cheapest rate for a shipment
     */
    suspend fun getCheapestRate(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        carrierAccounts: List<String>? = null
    ): Result<NestShipperRateResponse?> {
        val ratesResult = client.getRates(apiKey, toAddress, fromAddress, parcel, carrierAccounts)

        return ratesResult.map { rates ->
            rates.minByOrNull { it.rate.toDoubleOrNull() ?: Double.MAX_VALUE }
        }
    }

    /**
     * Get the fastest rate for a shipment
     */
    suspend fun getFastestRate(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        carrierAccounts: List<String>? = null
    ): Result<NestShipperRateResponse?> {
        val ratesResult = client.getRates(apiKey, toAddress, fromAddress, parcel, carrierAccounts)

        return ratesResult.map { rates ->
            rates.filter { it.deliveryDays != null }
                .minByOrNull { it.deliveryDays ?: Int.MAX_VALUE }
        }
    }

    /**
     * Get all rates sorted by price
     */
    suspend fun getRatesByPrice(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        carrierAccounts: List<String>? = null
    ): Result<List<NestShipperRateResponse>> {
        val ratesResult = client.getRates(apiKey, toAddress, fromAddress, parcel, carrierAccounts)

        return ratesResult.map { rates ->
            rates.sortedBy { it.rate.toDoubleOrNull() ?: Double.MAX_VALUE }
        }
    }

    /**
     * Convert label format
     */
    suspend fun convertLabel(
        apiKey: String,
        shipmentId: String,
        format: String = "pdf"
    ): Result<NestShipperShipmentResponse> {
        logger.info("Converting label format to $format for shipment: $shipmentId")
        return client.convertLabel(apiKey, shipmentId, format)
    }

    /**
     * Refund a shipment (void the label)
     */
    suspend fun refundShipment(
        apiKey: String,
        shipmentId: String
    ): Result<NestShipperRefundResponse> {
        logger.info("Refunding shipment: $shipmentId")
        return client.refundShipment(apiKey, shipmentId)
    }

    // ============================================
    // TRACKING OPERATIONS
    // ============================================

    /**
     * Create a tracker for a tracking code
     */
    suspend fun createTracker(
        apiKey: String,
        trackingCode: String,
        carrier: String? = null
    ): Result<NestShipperTrackerResponse> {
        logger.info("Creating tracker for: $trackingCode")
        return client.createTracker(apiKey, trackingCode, carrier)
    }

    /**
     * Get tracking information
     */
    suspend fun getTracker(
        apiKey: String,
        trackerId: String
    ): Result<NestShipperTrackerResponse> {
        return client.getTracker(apiKey, trackerId)
    }

    /**
     * List all trackers
     */
    suspend fun listTrackers(
        apiKey: String,
        pageSize: Int = 20,
        beforeId: String? = null,
        afterId: String? = null
    ): Result<NestShipperTrackersResponse> {
        return client.listTrackers(apiKey, pageSize, beforeId, afterId)
    }

    // ============================================
    // CARRIER ACCOUNTS
    // ============================================

    /**
     * List available carrier accounts
     */
    suspend fun listCarrierAccounts(
        apiKey: String
    ): Result<List<NestShipperCarrierAccount>> {
        return client.listCarrierAccounts(apiKey)
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    /**
     * Calculate shipping cost for a package
     * Returns the cheapest rate amount.
     */
    suspend fun calculateShippingCost(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        weightOz: Double,
        lengthIn: Double,
        widthIn: Double,
        heightIn: Double
    ): Result<Double?> {
        val parcel = NestShipperParcelRequest(
            length = lengthIn,
            width = widthIn,
            height = heightIn,
            weight = weightOz
        )

        return getCheapestRate(apiKey, toAddress, fromAddress, parcel).map { rate ->
            rate?.rate?.toDoubleOrNull()
        }
    }

    /**
     * Create a label for an order (high-level method)
     * This method handles the full flow: create shipment → select cheapest rate → buy label
     */
    suspend fun createLabelForOrder(
        apiKey: String,
        orderId: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        preferredCarrier: String? = null,
        preferredService: String? = null
    ): Result<CreateLabelResult> {
        logger.info("Creating label for order: $orderId")

        // Step 1: Create shipment to get rates
        val shipmentResult = createShipment(
            apiKey = apiKey,
            toAddress = toAddress,
            fromAddress = fromAddress,
            parcel = parcel,
            reference = orderId
        )

        val shipment = shipmentResult.getOrElse { error ->
            return Result.failure(error)
        }

        if (shipment.rates.isEmpty()) {
            return Result.failure(Exception("No rates available for this shipment"))
        }

        // Step 2: Select rate (preferred or cheapest)
        val selectedRate = if (preferredCarrier != null && preferredService != null) {
            shipment.rates.find {
                it.carrier.equals(preferredCarrier, ignoreCase = true) &&
                it.service.equals(preferredService, ignoreCase = true)
            } ?: shipment.rates.minByOrNull { it.rate.toDoubleOrNull() ?: Double.MAX_VALUE }
        } else {
            shipment.rates.minByOrNull { it.rate.toDoubleOrNull() ?: Double.MAX_VALUE }
        }

        if (selectedRate == null) {
            return Result.failure(Exception("Could not select a rate"))
        }

        // Step 3: Buy label
        val labelResult = buyLabel(
            apiKey = apiKey,
            shipmentId = shipment.id,
            rateId = selectedRate.id
        )

        return labelResult.map { purchasedShipment ->
            CreateLabelResult(
                shipmentId = purchasedShipment.id,
                trackingCode = purchasedShipment.trackingCode ?: "",
                labelUrl = purchasedShipment.postageLabel?.labelUrl ?: "",
                labelPdfUrl = purchasedShipment.postageLabel?.labelPdfUrl,
                carrier = selectedRate.carrier,
                service = selectedRate.service,
                rate = selectedRate.rate.toDoubleOrNull() ?: 0.0,
                estimatedDeliveryDays = selectedRate.deliveryDays
            )
        }
    }
}

/**
 * Result of label creation
 */
data class CreateLabelResult(
    val shipmentId: String,
    val trackingCode: String,
    val labelUrl: String,
    val labelPdfUrl: String?,
    val carrier: String,
    val service: String,
    val rate: Double,
    val estimatedDeliveryDays: Int?
)

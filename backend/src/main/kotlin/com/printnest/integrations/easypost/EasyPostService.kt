package com.printnest.integrations.easypost

import com.printnest.domain.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.Base64

/**
 * EasyPost API Integration Service
 *
 * Provides shipping label creation, address validation, rate calculation,
 * and tracking functionality through the EasyPost API.
 *
 * API Documentation: https://www.easypost.com/docs/api
 */
class EasyPostService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(EasyPostService::class.java)
    private val baseUrl = "https://api.easypost.com/v2"

    /**
     * Creates Basic auth header from API key
     */
    private fun getAuthHeader(apiKey: String): String {
        // EasyPost uses API key as username with empty password
        val credentials = "$apiKey:"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    // =====================================================
    // ADDRESS OPERATIONS
    // =====================================================

    /**
     * Create an address in EasyPost
     */
    suspend fun createAddress(
        apiKey: String,
        address: ShippingAddress
    ): Result<EasyPostAddress> {
        return try {
            val requestBody = mapOf(
                "address" to address.toEasyPostFormat()
            )

            val response = httpClient.post("$baseUrl/addresses") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val easyPostAddress: EasyPostAddress = response.body()
                logger.info("EasyPost address created: ${easyPostAddress.id}")
                Result.success(easyPostAddress)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost createAddress failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost createAddress exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Verify an existing address
     */
    suspend fun verifyAddress(
        apiKey: String,
        addressId: String
    ): Result<EasyPostAddress> {
        return try {
            val response = httpClient.get("$baseUrl/addresses/$addressId/verify") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val wrapper: AddressVerifyResponse = response.body()
                logger.info("EasyPost address verified: $addressId")
                Result.success(wrapper.address)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost verifyAddress failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost verifyAddress exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Create and verify an address in one step
     */
    suspend fun createAndVerifyAddress(
        apiKey: String,
        address: ShippingAddress
    ): Result<ValidateAddressResponse> {
        return try {
            val requestBody = mapOf(
                "address" to address.copy(verify = true).toEasyPostFormat()
            )

            val response = httpClient.post("$baseUrl/addresses") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val easyPostAddress: EasyPostAddress = response.body()
                val verifications = easyPostAddress.verifications

                val isValid = verifications?.delivery?.success == true
                val errors = verifications?.delivery?.errors?.map { it.message ?: "Unknown error" } ?: emptyList()

                val validatedAddress = address.copy(
                    id = easyPostAddress.id,
                    verificationStatus = if (isValid) AddressVerificationStatus.VERIFIED else AddressVerificationStatus.INVALID,
                    verificationMessages = errors
                )

                // Create suggested address if verification succeeded with corrections
                val suggestedAddress = if (isValid && easyPostAddress.street1 != address.street1) {
                    ShippingAddress(
                        id = easyPostAddress.id,
                        name = easyPostAddress.name ?: address.name,
                        company = easyPostAddress.company ?: address.company,
                        street1 = easyPostAddress.street1 ?: address.street1,
                        street2 = easyPostAddress.street2,
                        city = easyPostAddress.city ?: address.city,
                        state = easyPostAddress.state ?: address.state,
                        zip = easyPostAddress.zip ?: address.zip,
                        country = easyPostAddress.country ?: address.country,
                        phone = easyPostAddress.phone ?: address.phone,
                        verificationStatus = AddressVerificationStatus.VERIFIED
                    )
                } else null

                Result.success(ValidateAddressResponse(
                    isValid = isValid,
                    address = validatedAddress,
                    suggestedAddress = suggestedAddress,
                    messages = if (suggestedAddress != null) listOf("Address was corrected") else emptyList(),
                    errors = errors
                ))
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost createAndVerifyAddress failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost createAndVerifyAddress exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // SHIPMENT OPERATIONS
    // =====================================================

    /**
     * Create a shipment and get rates
     */
    suspend fun createShipment(
        apiKey: String,
        fromAddress: ShippingAddress,
        toAddress: ShippingAddress,
        parcel: Parcel,
        customsInfo: CustomsInfo? = null,
        options: ShipmentOptions? = null,
        reference: String? = null
    ): Result<ShipmentResponse> {
        return try {
            val shipmentData = buildMap<String, Any?> {
                put("from_address", fromAddress.toEasyPostFormat())
                put("to_address", toAddress.toEasyPostFormat())
                put("parcel", parcel.toEasyPostFormat())

                customsInfo?.let {
                    put("customs_info", mapOf(
                        "eel_pfc" to it.eelPfc,
                        "customs_certify" to it.customsCertify,
                        "customs_signer" to it.customsSigner,
                        "contents_type" to it.contentsType,
                        "contents_explanation" to it.contentsExplanation,
                        "restriction_type" to it.restrictionType,
                        "non_delivery_option" to it.nonDeliveryOption,
                        "customs_items" to it.customsItems.map { item ->
                            mapOf(
                                "description" to item.description,
                                "quantity" to item.quantity,
                                "weight" to item.weight.toDouble(),
                                "value" to item.value.toDouble(),
                                "hs_tariff_number" to item.hsTariffNumber,
                                "origin_country" to item.originCountry
                            )
                        }
                    ))
                }

                options?.let {
                    put("options", mapOf(
                        "label_format" to it.labelFormat,
                        "label_size" to it.labelSize,
                        "print_custom_1" to it.printCustom1,
                        "print_custom_2" to it.printCustom2,
                        "print_custom_3" to it.printCustom3,
                        "invoice_number" to it.invoiceNumber,
                        "currency" to it.currency
                    ).filterValues { v -> v != null })
                }

                reference?.let { put("reference", it) }
            }

            val requestBody = mapOf("shipment" to shipmentData)

            val response = httpClient.post("$baseUrl/shipments") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                logger.info("EasyPost shipment created: ${shipment.id} with ${shipment.rates.size} rates")
                Result.success(shipment)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost createShipment failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost createShipment exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get a shipment by ID
     */
    suspend fun getShipment(
        apiKey: String,
        shipmentId: String
    ): Result<ShipmentResponse> {
        return try {
            val response = httpClient.get("$baseUrl/shipments/$shipmentId") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                Result.success(shipment)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost getShipment failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost getShipment exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Regenerate rates for an existing shipment
     */
    suspend fun getRates(
        apiKey: String,
        shipmentId: String
    ): Result<List<ShippingRate>> {
        return try {
            val response = httpClient.post("$baseUrl/shipments/$shipmentId/rerate") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                logger.info("EasyPost rates regenerated for shipment: $shipmentId - ${shipment.rates.size} rates")
                Result.success(shipment.rates)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost getRates failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost getRates exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Get a specific rate by ID
     */
    suspend fun getRate(
        apiKey: String,
        rateId: String
    ): Result<ShippingRate> {
        return try {
            val response = httpClient.get("$baseUrl/rates/$rateId") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val rate: ShippingRate = response.body()
                Result.success(rate)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost getRate failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost getRate exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // LABEL OPERATIONS
    // =====================================================

    /**
     * Buy a shipping label for a shipment
     */
    suspend fun buyLabel(
        apiKey: String,
        shipmentId: String,
        rateId: String
    ): Result<ShipmentResponse> {
        return try {
            val requestBody = mapOf(
                "rate" to mapOf("id" to rateId)
            )

            val response = httpClient.post("$baseUrl/shipments/$shipmentId/buy") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                logger.info("EasyPost label purchased: ${shipment.trackingCode} for shipment ${shipment.id}")
                Result.success(shipment)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost buyLabel failed: ${response.status} - $errorBody")

                // Handle already purchased shipment
                val errorDetail = try {
                    json.decodeFromString<EasyPostError>(errorBody)
                } catch (e: Exception) { null }

                if (errorDetail?.error?.code == "SHIPMENT.POSTAGE.EXISTS") {
                    // Shipment already purchased, retrieve and return it
                    logger.info("Shipment $shipmentId already purchased, retrieving...")
                    return getShipment(apiKey, shipmentId)
                }

                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost buyLabel exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Convert label format
     */
    suspend fun convertLabel(
        apiKey: String,
        shipmentId: String,
        format: String = "PDF"
    ): Result<ShipmentResponse> {
        return try {
            val requestBody = mapOf(
                "file_format" to format
            )

            val response = httpClient.get("$baseUrl/shipments/$shipmentId/label") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                parameter("file_format", format)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                Result.success(shipment)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost convertLabel failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost convertLabel exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // TRACKING OPERATIONS
    // =====================================================

    /**
     * Get tracking info for a shipment
     */
    suspend fun getTrackingInfo(
        apiKey: String,
        trackingCode: String,
        carrier: String? = null
    ): Result<TrackingResponse> {
        return try {
            val response = httpClient.get("$baseUrl/trackers") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                parameter("tracking_code", trackingCode)
                carrier?.let { parameter("carrier", it) }
            }

            if (response.status.isSuccess()) {
                val trackerList: TrackerListResponse = response.body()
                val tracker = trackerList.trackers.firstOrNull()

                if (tracker != null) {
                    Result.success(convertToTrackingResponse(tracker))
                } else {
                    // Create a new tracker if not found
                    createTracker(apiKey, trackingCode, carrier)
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost getTrackingInfo failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost getTrackingInfo exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Create a tracker for a tracking code
     */
    suspend fun createTracker(
        apiKey: String,
        trackingCode: String,
        carrier: String? = null
    ): Result<TrackingResponse> {
        return try {
            val requestBody = buildMap<String, Any?> {
                put("tracker", buildMap {
                    put("tracking_code", trackingCode)
                    carrier?.let { put("carrier", it) }
                })
            }

            val response = httpClient.post("$baseUrl/trackers") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val tracker: EasyPostTracker = response.body()
                logger.info("EasyPost tracker created: ${tracker.id} for $trackingCode")
                Result.success(convertToTrackingResponse(tracker))
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost createTracker failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost createTracker exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    // =====================================================
    // REFUND OPERATIONS
    // =====================================================

    /**
     * Request a refund for a shipment label
     */
    suspend fun refundLabel(
        apiKey: String,
        shipmentId: String
    ): Result<ShippingRefundResponse> {
        return try {
            val response = httpClient.post("$baseUrl/shipments/$shipmentId/refund") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                val shipment: ShipmentResponse = response.body()
                val refundStatus = when (shipment.refundStatus) {
                    "submitted" -> RefundStatus.SUBMITTED
                    "refunded" -> RefundStatus.REFUNDED
                    "rejected" -> RefundStatus.REJECTED
                    else -> RefundStatus.PENDING
                }

                logger.info("EasyPost refund requested for shipment: $shipmentId - status: ${shipment.refundStatus}")

                Result.success(ShippingRefundResponse(
                    id = shipment.id,
                    shipmentId = shipmentId,
                    trackingCode = shipment.trackingCode ?: "",
                    status = refundStatus,
                    message = "Refund request ${shipment.refundStatus}"
                ))
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost refundLabel failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost refundLabel exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Request refund by tracking codes
     */
    suspend fun requestRefundByTrackingCodes(
        apiKey: String,
        carrier: String,
        trackingCodes: List<String>
    ): Result<List<ShippingRefundResponse>> {
        return try {
            val requestBody = mapOf(
                "refund" to mapOf(
                    "carrier" to carrier,
                    "tracking_codes" to trackingCodes
                )
            )

            val response = httpClient.post("$baseUrl/refunds") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val refundsWrapper: RefundsListResponse = response.body()
                val refundResponses = refundsWrapper.refunds.map { refund ->
                    ShippingRefundResponse(
                        id = refund.id,
                        shipmentId = refund.shipmentId ?: "",
                        trackingCode = refund.trackingCode ?: "",
                        status = when (refund.status) {
                            "submitted" -> RefundStatus.SUBMITTED
                            "refunded" -> RefundStatus.REFUNDED
                            "rejected" -> RefundStatus.REJECTED
                            else -> RefundStatus.PENDING
                        },
                        message = refund.confirmationNumber
                    )
                }
                Result.success(refundResponses)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("EasyPost requestRefundByTrackingCodes failed: ${response.status} - $errorBody")
                val error = parseEasyPostError(errorBody)
                Result.failure(EasyPostException(error, response.status.value))
            }
        } catch (e: Exception) {
            logger.error("EasyPost requestRefundByTrackingCodes exception", e)
            Result.failure(EasyPostException("Connection error: ${e.message}", 0))
        }
    }

    /**
     * Request refund for a single tracking code
     * This is a convenience method that wraps requestRefundByTrackingCodes
     *
     * @param apiKey EasyPost API key
     * @param trackingCode The tracking code to refund
     * @param carrier The carrier name (e.g., "USPS", "UPS", "FedEx")
     * @return ShippingRefundResponse with refund status
     */
    suspend fun requestRefund(
        apiKey: String,
        trackingCode: String,
        carrier: String
    ): Result<ShippingRefundResponse> {
        logger.info("Requesting refund for tracking code: $trackingCode, carrier: $carrier")

        // Normalize carrier name - extract base carrier from service string
        val normalizedCarrier = normalizeCarrierName(carrier)

        return requestRefundByTrackingCodes(apiKey, normalizedCarrier, listOf(trackingCode))
            .map { refunds ->
                refunds.firstOrNull() ?: ShippingRefundResponse(
                    id = "",
                    shipmentId = "",
                    trackingCode = trackingCode,
                    status = RefundStatus.PENDING,
                    message = "No refund response received"
                )
            }
    }

    /**
     * Normalize carrier name from service string
     * E.g., "USPS Priority" -> "USPS", "UPS Ground" -> "UPS"
     */
    private fun normalizeCarrierName(carrier: String): String {
        val carrierUpper = carrier.uppercase()
        return when {
            carrierUpper.startsWith("USPS") -> "USPS"
            carrierUpper.startsWith("UPS") || carrierUpper.startsWith("UPSDAP") -> "UPS"
            carrierUpper.startsWith("FEDEX") -> "FedEx"
            carrierUpper.startsWith("DHL") -> "DHL"
            else -> carrier.split(" ").firstOrNull()?.uppercase() ?: carrier
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun parseEasyPostError(errorBody: String): String {
        return try {
            val error = json.decodeFromString<EasyPostError>(errorBody)
            val message = error.error?.message ?: "Unknown error"
            val details = error.error?.errors?.joinToString(", ") { "${it.field}: ${it.message}" }
            if (details.isNullOrEmpty()) message else "$message - $details"
        } catch (e: Exception) {
            "Error parsing response: $errorBody"
        }
    }

    private fun convertToTrackingResponse(tracker: EasyPostTracker): TrackingResponse {
        val status = when (tracker.status?.lowercase()) {
            "pre_transit" -> TrackingStatus.PRE_TRANSIT
            "in_transit" -> TrackingStatus.IN_TRANSIT
            "out_for_delivery" -> TrackingStatus.OUT_FOR_DELIVERY
            "delivered" -> TrackingStatus.DELIVERED
            "available_for_pickup" -> TrackingStatus.AVAILABLE_FOR_PICKUP
            "return_to_sender" -> TrackingStatus.RETURN_TO_SENDER
            "failure" -> TrackingStatus.FAILURE
            "cancelled" -> TrackingStatus.CANCELLED
            "error" -> TrackingStatus.ERROR
            else -> TrackingStatus.UNKNOWN
        }

        return TrackingResponse(
            id = tracker.id,
            trackingCode = tracker.trackingCode ?: "",
            carrier = tracker.carrier ?: "",
            status = status,
            statusDetail = tracker.statusDetail,
            estimatedDeliveryDate = tracker.estDeliveryDate,
            signedBy = tracker.signedBy,
            publicUrl = tracker.publicUrl,
            trackingDetails = tracker.trackingDetails
        )
    }

    /**
     * Validate API credentials
     */
    suspend fun validateCredentials(apiKey: String): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/users") {
                header(HttpHeaders.Authorization, getAuthHeader(apiKey))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.error("EasyPost credentials validation failed", e)
            false
        }
    }
}

// =====================================================
// INTERNAL DTOs FOR API RESPONSES
// =====================================================

@Serializable
private data class AddressVerifyResponse(
    val address: EasyPostAddress
)

@Serializable
private data class TrackerListResponse(
    val trackers: List<EasyPostTracker>
)

@Serializable
private data class RefundsListResponse(
    val refunds: List<EasyPostRefund>
)

@Serializable
private data class EasyPostRefund(
    val id: String,
    val shipmentId: String? = null,
    val trackingCode: String? = null,
    val status: String? = null,
    val confirmationNumber: String? = null,
    val carrier: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// =====================================================
// EXCEPTION
// =====================================================

class EasyPostException(
    message: String,
    val statusCode: Int
) : Exception(message)

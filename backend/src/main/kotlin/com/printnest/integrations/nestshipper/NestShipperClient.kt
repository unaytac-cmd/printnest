package com.printnest.integrations.nestshipper

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * NestShipper API Client
 *
 * Handles all HTTP communication with NestShipper API.
 * Based on common shipping API patterns (similar to EasyPost).
 *
 * TODO: Update BASE_URL and authentication method based on actual NestShipper API documentation.
 */
class NestShipperClient(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(NestShipperClient::class.java)

    companion object {
        // TODO: Update with actual NestShipper API base URL
        const val BASE_URL = "https://api.nestshipper.com/v1"
    }

    // ============================================
    // ADDRESS ENDPOINTS
    // ============================================

    /**
     * Create and verify an address
     */
    suspend fun createAddress(
        apiKey: String,
        address: NestShipperAddressRequest,
        verify: Boolean = false
    ): Result<NestShipperAddressResponse> {
        return try {
            val endpoint = if (verify) "/addresses/create_and_verify" else "/addresses"
            val response = httpClient.post("$BASE_URL$endpoint") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(json.encodeToString(NestShipperAddressRequest.serializer(), address))
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to create address", e)
            Result.failure(e)
        }
    }

    /**
     * Verify an existing address
     */
    suspend fun verifyAddress(
        apiKey: String,
        addressId: String
    ): Result<NestShipperAddressResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/addresses/$addressId/verify") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to verify address: $addressId", e)
            Result.failure(e)
        }
    }

    // ============================================
    // SHIPMENT ENDPOINTS
    // ============================================

    /**
     * Create a shipment and get rates
     */
    suspend fun createShipment(
        apiKey: String,
        shipment: NestShipperShipmentRequest
    ): Result<NestShipperShipmentResponse> {
        return try {
            val response = httpClient.post("$BASE_URL/shipments") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(json.encodeToString(NestShipperShipmentRequest.serializer(), shipment))
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to create shipment", e)
            Result.failure(e)
        }
    }

    /**
     * Get a shipment by ID
     */
    suspend fun getShipment(
        apiKey: String,
        shipmentId: String
    ): Result<NestShipperShipmentResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/shipments/$shipmentId") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to get shipment: $shipmentId", e)
            Result.failure(e)
        }
    }

    /**
     * List shipments with pagination
     */
    suspend fun listShipments(
        apiKey: String,
        pageSize: Int = 20,
        beforeId: String? = null,
        afterId: String? = null
    ): Result<NestShipperShipmentsResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/shipments") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                parameter("page_size", pageSize)
                beforeId?.let { parameter("before_id", it) }
                afterId?.let { parameter("after_id", it) }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to list shipments", e)
            Result.failure(e)
        }
    }

    /**
     * Buy a label for a shipment
     */
    suspend fun buyLabel(
        apiKey: String,
        shipmentId: String,
        rateId: String,
        insurance: Double? = null
    ): Result<NestShipperShipmentResponse> {
        return try {
            val buyRequest = NestShipperBuyRequest(
                rateId = rateId,
                insurance = insurance
            )

            val response = httpClient.post("$BASE_URL/shipments/$shipmentId/buy") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(json.encodeToString(NestShipperBuyRequest.serializer(), buyRequest))
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to buy label for shipment: $shipmentId", e)
            Result.failure(e)
        }
    }

    /**
     * One-call label creation (create shipment + buy label in one API call)
     * Pass service and carrierAccounts in the shipment request to automatically purchase.
     */
    suspend fun createAndBuyLabel(
        apiKey: String,
        shipment: NestShipperShipmentRequest
    ): Result<NestShipperShipmentResponse> {
        // The shipment request should include service and carrier_accounts
        // to trigger one-call buy
        return createShipment(apiKey, shipment)
    }

    /**
     * Convert label format (PNG to PDF, ZPL, or EPL2)
     */
    suspend fun convertLabel(
        apiKey: String,
        shipmentId: String,
        format: String = "pdf"  // "pdf", "zpl", "epl2"
    ): Result<NestShipperShipmentResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/shipments/$shipmentId/label") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                parameter("file_format", format)
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to convert label format for shipment: $shipmentId", e)
            Result.failure(e)
        }
    }

    /**
     * Refund a shipment (void the label)
     */
    suspend fun refundShipment(
        apiKey: String,
        shipmentId: String
    ): Result<NestShipperRefundResponse> {
        return try {
            val response = httpClient.post("$BASE_URL/shipments/$shipmentId/refund") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to refund shipment: $shipmentId", e)
            Result.failure(e)
        }
    }

    // ============================================
    // TRACKING ENDPOINTS
    // ============================================

    /**
     * Create a tracker for a tracking code
     */
    suspend fun createTracker(
        apiKey: String,
        trackingCode: String,
        carrier: String? = null
    ): Result<NestShipperTrackerResponse> {
        return try {
            val request = NestShipperCreateTrackerRequest(
                trackingCode = trackingCode,
                carrier = carrier
            )

            val response = httpClient.post("$BASE_URL/trackers") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(json.encodeToString(NestShipperCreateTrackerRequest.serializer(), request))
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to create tracker for: $trackingCode", e)
            Result.failure(e)
        }
    }

    /**
     * Get a tracker by ID
     */
    suspend fun getTracker(
        apiKey: String,
        trackerId: String
    ): Result<NestShipperTrackerResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/trackers/$trackerId") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to get tracker: $trackerId", e)
            Result.failure(e)
        }
    }

    /**
     * List trackers with pagination
     */
    suspend fun listTrackers(
        apiKey: String,
        pageSize: Int = 20,
        beforeId: String? = null,
        afterId: String? = null
    ): Result<NestShipperTrackersResponse> {
        return try {
            val response = httpClient.get("$BASE_URL/trackers") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                parameter("page_size", pageSize)
                beforeId?.let { parameter("before_id", it) }
                afterId?.let { parameter("after_id", it) }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to list trackers", e)
            Result.failure(e)
        }
    }

    // ============================================
    // CARRIER ACCOUNTS
    // ============================================

    /**
     * List carrier accounts
     */
    suspend fun listCarrierAccounts(
        apiKey: String
    ): Result<List<NestShipperCarrierAccount>> {
        return try {
            val response = httpClient.get("$BASE_URL/carrier_accounts") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            handleResponse(response)
        } catch (e: Exception) {
            logger.error("Failed to list carrier accounts", e)
            Result.failure(e)
        }
    }

    // ============================================
    // RATES
    // ============================================

    /**
     * Get rates for a shipment (without creating a full shipment)
     * This is useful for rate shopping before committing to a shipment.
     */
    suspend fun getRates(
        apiKey: String,
        toAddress: NestShipperAddressRequest,
        fromAddress: NestShipperAddressRequest,
        parcel: NestShipperParcelRequest,
        carrierAccounts: List<String>? = null
    ): Result<List<NestShipperRateResponse>> {
        // Create a temporary shipment to get rates
        val shipmentResult = createShipment(
            apiKey = apiKey,
            shipment = NestShipperShipmentRequest(
                toAddress = toAddress,
                fromAddress = fromAddress,
                parcel = parcel,
                carrierAccounts = carrierAccounts
            )
        )

        return shipmentResult.map { it.rates }
    }

    // ============================================
    // VALIDATION
    // ============================================

    /**
     * Validate API credentials
     */
    suspend fun validateCredentials(apiKey: String): Boolean {
        return try {
            val response = httpClient.get("$BASE_URL/carrier_accounts") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error("Failed to validate NestShipper credentials", e)
            false
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): Result<T> {
        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> {
                try {
                    val body = response.bodyAsText()
                    val result = json.decodeFromString<T>(body)
                    Result.success(result)
                } catch (e: Exception) {
                    logger.error("Failed to parse response", e)
                    Result.failure(e)
                }
            }
            HttpStatusCode.Unauthorized -> {
                Result.failure(Exception("Invalid API key"))
            }
            HttpStatusCode.NotFound -> {
                Result.failure(Exception("Resource not found"))
            }
            HttpStatusCode.UnprocessableEntity, HttpStatusCode.BadRequest -> {
                try {
                    val body = response.bodyAsText()
                    val error = json.decodeFromString<NestShipperErrorResponse>(body)
                    Result.failure(Exception(error.error.message))
                } catch (e: Exception) {
                    Result.failure(Exception("Request failed: ${response.status}"))
                }
            }
            else -> {
                Result.failure(Exception("API request failed with status: ${response.status}"))
            }
        }
    }
}

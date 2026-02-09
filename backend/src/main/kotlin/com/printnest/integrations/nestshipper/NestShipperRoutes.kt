package com.printnest.integrations.nestshipper

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

fun Route.nestShipperRoutes() {
    val nestShipperService: NestShipperService = GlobalContext.get().get()

    route("/nestshipper") {

        /**
         * POST /api/v1/nestshipper/validate
         * Validate NestShipper API credentials
         */
        post("/validate") {
            val request = call.receive<ValidateCredentialsRequest>()
            val isValid = nestShipperService.validateCredentials(request.apiKey)

            if (isValid) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "NestShipper credentials are valid"
                ))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Invalid NestShipper credentials"
                ))
            }
        }

        /**
         * POST /api/v1/nestshipper/address/verify
         * Verify an address
         */
        post("/address/verify") {
            val request = call.receive<VerifyAddressRequest>()

            val addressRequest = NestShipperAddressRequest(
                name = request.name,
                company = request.company,
                street1 = request.street1,
                street2 = request.street2,
                city = request.city,
                state = request.state,
                zip = request.zip,
                country = request.country,
                phone = request.phone,
                email = request.email
            )

            val result = nestShipperService.createAddress(
                apiKey = request.apiKey,
                address = addressRequest,
                verify = true
            )

            result.fold(
                onSuccess = { address ->
                    call.respond(HttpStatusCode.OK, AddressVerificationResponse(
                        success = true,
                        verified = address.verified ?: false,
                        address = address
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Address verification failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/rates
         * Get shipping rates for a package
         */
        post("/rates") {
            val request = call.receive<GetRatesRequest>()

            val toAddress = NestShipperAddressRequest(
                name = request.toAddress.name,
                company = request.toAddress.company,
                street1 = request.toAddress.street1,
                street2 = request.toAddress.street2,
                city = request.toAddress.city,
                state = request.toAddress.state,
                zip = request.toAddress.zip,
                country = request.toAddress.country,
                phone = request.toAddress.phone,
                email = request.toAddress.email
            )

            val fromAddress = NestShipperAddressRequest(
                name = request.fromAddress.name,
                company = request.fromAddress.company,
                street1 = request.fromAddress.street1,
                street2 = request.fromAddress.street2,
                city = request.fromAddress.city,
                state = request.fromAddress.state,
                zip = request.fromAddress.zip,
                country = request.fromAddress.country,
                phone = request.fromAddress.phone,
                email = request.fromAddress.email
            )

            val parcel = NestShipperParcelRequest(
                length = request.parcel.length,
                width = request.parcel.width,
                height = request.parcel.height,
                weight = request.parcel.weight
            )

            val result = nestShipperService.getRatesByPrice(
                apiKey = request.apiKey,
                toAddress = toAddress,
                fromAddress = fromAddress,
                parcel = parcel
            )

            result.fold(
                onSuccess = { rates ->
                    call.respond(HttpStatusCode.OK, GetRatesResponse(
                        success = true,
                        rates = rates
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to get rates",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/shipment
         * Create a shipment
         */
        post("/shipment") {
            val request = call.receive<CreateShipmentRequest>()

            val toAddress = NestShipperAddressRequest(
                name = request.toAddress.name,
                company = request.toAddress.company,
                street1 = request.toAddress.street1,
                street2 = request.toAddress.street2,
                city = request.toAddress.city,
                state = request.toAddress.state,
                zip = request.toAddress.zip,
                country = request.toAddress.country,
                phone = request.toAddress.phone,
                email = request.toAddress.email
            )

            val fromAddress = NestShipperAddressRequest(
                name = request.fromAddress.name,
                company = request.fromAddress.company,
                street1 = request.fromAddress.street1,
                street2 = request.fromAddress.street2,
                city = request.fromAddress.city,
                state = request.fromAddress.state,
                zip = request.fromAddress.zip,
                country = request.fromAddress.country,
                phone = request.fromAddress.phone,
                email = request.fromAddress.email
            )

            val parcel = NestShipperParcelRequest(
                length = request.parcel.length,
                width = request.parcel.width,
                height = request.parcel.height,
                weight = request.parcel.weight
            )

            val result = nestShipperService.createShipment(
                apiKey = request.apiKey,
                toAddress = toAddress,
                fromAddress = fromAddress,
                parcel = parcel,
                reference = request.reference
            )

            result.fold(
                onSuccess = { shipment ->
                    call.respond(HttpStatusCode.OK, shipment)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to create shipment",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/nestshipper/shipment/{id}
         * Get a shipment by ID
         */
        get("/shipment/{id}") {
            val shipmentId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Shipment ID required"))

            val apiKey = call.request.queryParameters["apiKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Key required"))

            val result = nestShipperService.getShipment(apiKey, shipmentId)

            result.fold(
                onSuccess = { shipment ->
                    call.respond(HttpStatusCode.OK, shipment)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Shipment not found",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/shipment/{id}/buy
         * Buy a label for a shipment
         */
        post("/shipment/{id}/buy") {
            val shipmentId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Shipment ID required"))

            val request = call.receive<BuyLabelRequest>()

            val result = nestShipperService.buyLabel(
                apiKey = request.apiKey,
                shipmentId = shipmentId,
                rateId = request.rateId,
                insurance = request.insurance
            )

            result.fold(
                onSuccess = { shipment ->
                    call.respond(HttpStatusCode.OK, BuyLabelResponse(
                        success = true,
                        shipmentId = shipment.id,
                        trackingCode = shipment.trackingCode,
                        labelUrl = shipment.postageLabel?.labelUrl,
                        labelPdfUrl = shipment.postageLabel?.labelPdfUrl,
                        carrier = shipment.selectedRate?.carrier,
                        service = shipment.selectedRate?.service,
                        rate = shipment.selectedRate?.rate
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to buy label",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/label
         * Create a label (one-call: create shipment + buy in one step)
         */
        post("/label") {
            val request = call.receive<CreateLabelRequest>()

            val toAddress = NestShipperAddressRequest(
                name = request.toAddress.name,
                company = request.toAddress.company,
                street1 = request.toAddress.street1,
                street2 = request.toAddress.street2,
                city = request.toAddress.city,
                state = request.toAddress.state,
                zip = request.toAddress.zip,
                country = request.toAddress.country,
                phone = request.toAddress.phone,
                email = request.toAddress.email
            )

            val fromAddress = NestShipperAddressRequest(
                name = request.fromAddress.name,
                company = request.fromAddress.company,
                street1 = request.fromAddress.street1,
                street2 = request.fromAddress.street2,
                city = request.fromAddress.city,
                state = request.fromAddress.state,
                zip = request.fromAddress.zip,
                country = request.fromAddress.country,
                phone = request.fromAddress.phone,
                email = request.fromAddress.email
            )

            val parcel = NestShipperParcelRequest(
                length = request.parcel.length,
                width = request.parcel.width,
                height = request.parcel.height,
                weight = request.parcel.weight
            )

            val result = nestShipperService.createLabelForOrder(
                apiKey = request.apiKey,
                orderId = request.orderId,
                toAddress = toAddress,
                fromAddress = fromAddress,
                parcel = parcel,
                preferredCarrier = request.carrier,
                preferredService = request.service
            )

            result.fold(
                onSuccess = { labelResult ->
                    call.respond(HttpStatusCode.OK, CreateLabelResponse(
                        success = true,
                        shipmentId = labelResult.shipmentId,
                        trackingCode = labelResult.trackingCode,
                        labelUrl = labelResult.labelUrl,
                        labelPdfUrl = labelResult.labelPdfUrl,
                        carrier = labelResult.carrier,
                        service = labelResult.service,
                        rate = labelResult.rate,
                        estimatedDeliveryDays = labelResult.estimatedDeliveryDays
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to create label",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/shipment/{id}/refund
         * Refund a shipment (void the label)
         */
        post("/shipment/{id}/refund") {
            val shipmentId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Shipment ID required"))

            val request = call.receive<RefundRequest>()

            val result = nestShipperService.refundShipment(
                apiKey = request.apiKey,
                shipmentId = shipmentId
            )

            result.fold(
                onSuccess = { refund ->
                    call.respond(HttpStatusCode.OK, RefundResponse(
                        success = true,
                        refundId = refund.id,
                        status = refund.status,
                        confirmationNumber = refund.confirmationNumber
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to refund shipment",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/nestshipper/tracker
         * Create a tracker for a tracking code
         */
        post("/tracker") {
            val request = call.receive<CreateTrackerRequest>()

            val result = nestShipperService.createTracker(
                apiKey = request.apiKey,
                trackingCode = request.trackingCode,
                carrier = request.carrier
            )

            result.fold(
                onSuccess = { tracker ->
                    call.respond(HttpStatusCode.OK, tracker)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to create tracker",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/nestshipper/tracker/{id}
         * Get tracking information
         */
        get("/tracker/{id}") {
            val trackerId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tracker ID required"))

            val apiKey = call.request.queryParameters["apiKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Key required"))

            val result = nestShipperService.getTracker(apiKey, trackerId)

            result.fold(
                onSuccess = { tracker ->
                    call.respond(HttpStatusCode.OK, tracker)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Tracker not found",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/nestshipper/carriers
         * List available carrier accounts
         */
        get("/carriers") {
            val apiKey = call.request.queryParameters["apiKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Key required"))

            val result = nestShipperService.listCarrierAccounts(apiKey)

            result.fold(
                onSuccess = { carriers ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "carriers" to carriers
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to list carriers",
                        "message" to error.message
                    ))
                }
            )
        }
    }
}

// ============================================
// REQUEST/RESPONSE MODELS
// ============================================

@Serializable
data class ValidateCredentialsRequest(
    val apiKey: String
)

@Serializable
data class AddressInput(
    val name: String? = null,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String,
    val zip: String,
    val country: String = "US",
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class ParcelInput(
    val length: Double,
    val width: Double,
    val height: Double,
    val weight: Double  // in ounces
)

@Serializable
data class VerifyAddressRequest(
    val apiKey: String,
    val name: String? = null,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String,
    val zip: String,
    val country: String = "US",
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class AddressVerificationResponse(
    val success: Boolean,
    val verified: Boolean,
    val address: NestShipperAddressResponse
)

@Serializable
data class GetRatesRequest(
    val apiKey: String,
    val toAddress: AddressInput,
    val fromAddress: AddressInput,
    val parcel: ParcelInput
)

@Serializable
data class GetRatesResponse(
    val success: Boolean,
    val rates: List<NestShipperRateResponse>
)

@Serializable
data class CreateShipmentRequest(
    val apiKey: String,
    val toAddress: AddressInput,
    val fromAddress: AddressInput,
    val parcel: ParcelInput,
    val reference: String? = null
)

@Serializable
data class BuyLabelRequest(
    val apiKey: String,
    val rateId: String,
    val insurance: Double? = null
)

@Serializable
data class BuyLabelResponse(
    val success: Boolean,
    val shipmentId: String,
    val trackingCode: String?,
    val labelUrl: String?,
    val labelPdfUrl: String?,
    val carrier: String?,
    val service: String?,
    val rate: String?
)

@Serializable
data class CreateLabelRequest(
    val apiKey: String,
    val orderId: String,
    val toAddress: AddressInput,
    val fromAddress: AddressInput,
    val parcel: ParcelInput,
    val carrier: String? = null,
    val service: String? = null
)

@Serializable
data class CreateLabelResponse(
    val success: Boolean,
    val shipmentId: String,
    val trackingCode: String,
    val labelUrl: String,
    val labelPdfUrl: String?,
    val carrier: String,
    val service: String,
    val rate: Double,
    val estimatedDeliveryDays: Int?
)

@Serializable
data class RefundRequest(
    val apiKey: String
)

@Serializable
data class RefundResponse(
    val success: Boolean,
    val refundId: String,
    val status: String,
    val confirmationNumber: String?
)

@Serializable
data class CreateTrackerRequest(
    val apiKey: String,
    val trackingCode: String,
    val carrier: String? = null
)

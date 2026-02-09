package com.printnest.routes

import com.printnest.domain.models.ShipStationSettings
import com.printnest.domain.models.UpdateTenantSettingsRequest
import com.printnest.domain.service.SettingsService
import com.printnest.integrations.shipstation.ShipStationService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

fun Route.shipStationRoutes() {
    val shipStationService: ShipStationService = GlobalContext.get().get()
    val settingsService: SettingsService = GlobalContext.get().get()

    route("/shipstation") {

        /**
         * POST /api/v1/shipstation/connect
         * Connect ShipStation account with API credentials
         */
        post("/connect") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<ConnectShipStationRequest>()

            // Validate credentials
            val isValid = shipStationService.validateCredentials(request.apiKey, request.apiSecret)

            if (!isValid) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Invalid ShipStation credentials",
                    "message" to "Could not connect to ShipStation with the provided API key and secret"
                ))
                return@post
            }

            // Save credentials to tenant settings
            val updateRequest = UpdateTenantSettingsRequest(
                shipstationSettings = ShipStationSettings(
                    apiKey = request.apiKey,
                    apiSecret = request.apiSecret
                )
            )

            settingsService.updateTenantSettings(tenantId, updateRequest)
                .onSuccess {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "ShipStation connected successfully"
                    ))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to "Failed to save credentials",
                        "message" to error.message
                    ))
                }
        }

        /**
         * GET /api/v1/shipstation/status
         * Check ShipStation connection status
         */
        get("/status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            // Get credentials from tenant settings
            val settings = settingsService.getTenantSettings(tenantId)
            val shipstationSettings = settings?.shipstationSettings

            if (shipstationSettings?.apiKey.isNullOrBlank() || shipstationSettings?.apiSecret.isNullOrBlank()) {
                call.respond(HttpStatusCode.OK, ShipStationStatusResponse(
                    isConnected = false,
                    lastSyncAt = null,
                    storeCount = 0
                ))
                return@get
            }

            // Validate connection
            val isValid = shipStationService.validateCredentials(shipstationSettings.apiKey!!, shipstationSettings.apiSecret!!)
            val stores = if (isValid) shipStationService.getStores(tenantId) else emptyList()

            call.respond(HttpStatusCode.OK, ShipStationStatusResponse(
                isConnected = isValid,
                lastSyncAt = null,
                storeCount = stores.size
            ))
        }

        /**
         * POST /api/v1/shipstation/sync-stores
         * Sync stores from ShipStation
         */
        post("/sync-stores") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            // Get credentials from tenant settings
            val settings = settingsService.getTenantSettings(tenantId)
            val shipstationSettings = settings?.shipstationSettings

            if (shipstationSettings?.apiKey.isNullOrBlank() || shipstationSettings?.apiSecret.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "ShipStation not connected",
                    "message" to "Please connect ShipStation first"
                ))
                return@post
            }

            val result = shipStationService.syncStores(
                tenantId = tenantId,
                apiKey = shipstationSettings.apiKey!!,
                apiSecret = shipstationSettings.apiSecret!!
            )

            result.fold(
                onSuccess = { stores ->
                    call.respond(HttpStatusCode.OK, SyncStoresResponse(
                        success = true,
                        message = "Synced ${stores.size} stores",
                        stores = stores.map { store ->
                            StoreInfo(
                                id = store.id,
                                shipstationStoreId = store.shipstationStoreId,
                                storeName = store.storeName,
                                marketplaceName = store.marketplaceName,
                                isActive = store.isActive
                            )
                        }
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/shipstation/stores
         * Get list of synced ShipStation stores
         */
        get("/stores") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))
            val includeInactive = call.parameters["includeInactive"]?.toBoolean() ?: false

            val stores = if (includeInactive) {
                shipStationService.getAllStores(tenantId)
            } else {
                shipStationService.getStores(tenantId)
            }

            call.respond(HttpStatusCode.OK, stores.map { store ->
                StoreInfo(
                    id = store.id,
                    shipstationStoreId = store.shipstationStoreId,
                    storeName = store.storeName,
                    marketplaceName = store.marketplaceName,
                    isActive = store.isActive
                )
            })
        }

        /**
         * PUT /api/v1/shipstation/stores/{id}/status
         * Update store active status
         */
        put("/stores/{id}/status") {
            val storeId = call.parameters["id"]?.toLongOrNull()
            if (storeId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid store ID"))
                return@put
            }

            val request = call.receive<UpdateStoreStatusRequest>()
            val success = shipStationService.updateStoreStatus(storeId, request.isActive)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "Store status updated"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf(
                    "error" to "Store not found"
                ))
            }
        }

        /**
         * POST /api/v1/shipstation/sync-orders
         * Sync orders from ShipStation
         */
        post("/sync-orders") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<ShipStationSyncOrdersRequest>()

            // Use credentials from request or fall back to tenant settings
            val apiKey: String
            val apiSecret: String

            if (!request.apiKey.isNullOrBlank() && !request.apiSecret.isNullOrBlank()) {
                apiKey = request.apiKey
                apiSecret = request.apiSecret
            } else {
                val settings = settingsService.getTenantSettings(tenantId)
                val shipstationSettings = settings?.shipstationSettings

                if (shipstationSettings?.apiKey.isNullOrBlank() || shipstationSettings?.apiSecret.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "ShipStation not connected",
                        "message" to "Please connect ShipStation first or provide API credentials"
                    ))
                    return@post
                }

                apiKey = shipstationSettings.apiKey!!
                apiSecret = shipstationSettings.apiSecret!!
            }

            val result = shipStationService.syncOrders(
                tenantId = tenantId,
                apiKey = apiKey,
                apiSecret = apiSecret,
                storeId = request.storeId,
                modifyDateStart = request.modifyDateStart,
                modifyDateEnd = request.modifyDateEnd
            )

            result.fold(
                onSuccess = { syncResult ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Synced ${syncResult.totalFetched} orders",
                        "totalFetched" to syncResult.totalFetched,
                        "totalPages" to syncResult.totalPages,
                        "currentPage" to syncResult.currentPage
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Sync failed",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/shipstation/orders
         * Get orders from ShipStation (with filters)
         */
        get("/orders") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val apiKey = call.request.queryParameters["apiKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Key required"))
            val apiSecret = call.request.queryParameters["apiSecret"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Secret required"))

            val storeId = call.request.queryParameters["storeId"]?.toLongOrNull()
            val orderStatus = call.request.queryParameters["orderStatus"] ?: "awaiting_shipment"
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 100

            val result = shipStationService.getOrdersByStatus(
                apiKey = apiKey,
                apiSecret = apiSecret,
                storeId = storeId,
                orderStatus = orderStatus,
                page = page,
                pageSize = pageSize
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to fetch orders",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * GET /api/v1/shipstation/orders/{orderId}
         * Get a single order by ID
         */
        get("/orders/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val apiKey = call.request.queryParameters["apiKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Key required"))
            val apiSecret = call.request.queryParameters["apiSecret"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "API Secret required"))

            val result = shipStationService.getOrderById(apiKey, apiSecret, orderId)

            result.fold(
                onSuccess = { order ->
                    call.respond(HttpStatusCode.OK, order)
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Order not found",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/shipstation/orders/{orderId}/mark-shipped
         * Mark an order as shipped in ShipStation
         */
        post("/orders/{orderId}/mark-shipped") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val request = call.receive<MarkOrderShippedRequest>()

            val result = shipStationService.markOrderAsShipped(
                apiKey = request.apiKey,
                apiSecret = request.apiSecret,
                orderId = orderId,
                carrierCode = request.carrierCode,
                trackingNumber = request.trackingNumber,
                shipDate = request.shipDate,
                notifyCustomer = request.notifyCustomer,
                notifySalesChannel = request.notifySalesChannel
            )

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Order marked as shipped",
                        "orderId" to orderId,
                        "carrierCode" to request.carrierCode,
                        "trackingNumber" to request.trackingNumber
                    ))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Failed to mark order as shipped",
                        "message" to error.message
                    ))
                }
            )
        }

        /**
         * POST /api/v1/shipstation/create-label
         * Create a shipping label in ShipStation
         */
        post("/create-label") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateLabelRequest>()

            val labelRequest = com.printnest.integrations.shipstation.ShipStationCreateLabelRequest(
                orderId = request.orderId,
                carrierCode = request.carrierCode,
                serviceCode = request.serviceCode,
                packageCode = request.packageCode,
                confirmation = request.confirmation,
                shipDate = request.shipDate,
                weight = com.printnest.integrations.shipstation.ShipStationWeightResponse(
                    value = request.weight.value,
                    units = request.weight.units
                ),
                dimensions = request.dimensions?.let {
                    com.printnest.integrations.shipstation.ShipStationDimensionsResponse(
                        length = it.length,
                        width = it.width,
                        height = it.height,
                        units = it.units
                    )
                },
                shipFrom = com.printnest.integrations.shipstation.ShipStationAddressResponse(
                    name = request.shipFrom.name,
                    company = request.shipFrom.company,
                    street1 = request.shipFrom.street1,
                    street2 = request.shipFrom.street2,
                    city = request.shipFrom.city,
                    state = request.shipFrom.state,
                    postalCode = request.shipFrom.postalCode,
                    country = request.shipFrom.country,
                    phone = request.shipFrom.phone
                ),
                shipTo = com.printnest.integrations.shipstation.ShipStationAddressResponse(
                    name = request.shipTo.name,
                    company = request.shipTo.company,
                    street1 = request.shipTo.street1,
                    street2 = request.shipTo.street2,
                    city = request.shipTo.city,
                    state = request.shipTo.state,
                    postalCode = request.shipTo.postalCode,
                    country = request.shipTo.country,
                    phone = request.shipTo.phone
                ),
                testLabel = request.testLabel
            )

            val result = shipStationService.createShippingLabel(
                apiKey = request.apiKey,
                apiSecret = request.apiSecret,
                request = labelRequest
            )

            result.fold(
                onSuccess = { label ->
                    call.respond(HttpStatusCode.OK, CreateLabelResponse(
                        success = true,
                        shipmentId = label.shipmentId,
                        shipmentCost = label.shipmentCost,
                        trackingNumber = label.trackingNumber,
                        labelData = label.labelData
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
    }
}

// Request/Response models
@Serializable
data class ConnectShipStationRequest(
    val apiKey: String,
    val apiSecret: String
)

@Serializable
data class SyncStoresRequest(
    val apiKey: String,
    val apiSecret: String
)

@Serializable
data class ShipStationSyncOrdersRequest(
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val storeId: Long? = null,
    val modifyDateStart: String? = null,
    val modifyDateEnd: String? = null
)

@Serializable
data class UpdateStoreStatusRequest(
    val isActive: Boolean
)

@Serializable
data class ShipStationStatusResponse(
    val isConnected: Boolean,
    val lastSyncAt: String? = null,
    val storeCount: Int = 0
)

@Serializable
data class SyncStoresResponse(
    val success: Boolean,
    val message: String,
    val stores: List<StoreInfo>
)

@Serializable
data class StoreInfo(
    val id: Long,
    val shipstationStoreId: Long,
    val storeName: String,
    val marketplaceName: String? = null,
    val isActive: Boolean
)

@Serializable
data class MarkOrderShippedRequest(
    val apiKey: String,
    val apiSecret: String,
    val carrierCode: String,
    val trackingNumber: String? = null,
    val shipDate: String? = null,
    val notifyCustomer: Boolean = true,
    val notifySalesChannel: Boolean = true
)

@Serializable
data class CreateLabelRequest(
    val apiKey: String,
    val apiSecret: String,
    val orderId: Long,
    val carrierCode: String,
    val serviceCode: String,
    val packageCode: String? = null,
    val confirmation: String? = null,
    val shipDate: String,
    val weight: WeightRequest,
    val dimensions: DimensionsRequest? = null,
    val shipFrom: AddressRequest,
    val shipTo: AddressRequest,
    val testLabel: Boolean = false
)

@Serializable
data class WeightRequest(
    val value: Double,
    val units: String = "ounces"
)

@Serializable
data class DimensionsRequest(
    val length: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val units: String = "inches"
)

@Serializable
data class AddressRequest(
    val name: String? = null,
    val company: String? = null,
    val street1: String? = null,
    val street2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val phone: String? = null
)

@Serializable
data class CreateLabelResponse(
    val success: Boolean,
    val shipmentId: Long,
    val shipmentCost: Double,
    val trackingNumber: String,
    val labelData: String
)

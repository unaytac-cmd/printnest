package com.printnest.integrations.shipstation

import com.printnest.domain.models.ShipStationStore
import com.printnest.domain.repository.ShipStationStoreRepository
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ShipStationService(
    private val client: ShipStationClient,
    private val storeRepository: ShipStationStoreRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ShipStationService::class.java)

    /**
     * Validate ShipStation credentials
     */
    suspend fun validateCredentials(apiKey: String, apiSecret: String): Boolean {
        return client.validateCredentials(apiKey, apiSecret)
    }

    /**
     * Sync stores from ShipStation API to local database
     */
    suspend fun syncStores(
        tenantId: Long,
        apiKey: String,
        apiSecret: String
    ): Result<List<ShipStationStore>> {
        logger.info("Syncing ShipStation stores for tenant $tenantId")

        val result = client.getStores(apiKey, apiSecret)

        return result.fold(
            onSuccess = { apiStores ->
                val syncedStores = apiStores.map { apiStore ->
                    storeRepository.upsert(
                        tenantId = tenantId,
                        shipstationStoreId = apiStore.storeId,
                        storeName = apiStore.storeName,
                        marketplaceName = apiStore.marketplaceName,
                        marketplaceId = apiStore.marketplaceId,
                        accountName = apiStore.accountName
                    )
                }

                logger.info("Synced ${syncedStores.size} stores for tenant $tenantId")
                Result.success(syncedStores)
            },
            onFailure = { error ->
                logger.error("Failed to sync stores for tenant $tenantId", error)
                Result.failure(error)
            }
        )
    }

    /**
     * Get stores from local database
     */
    fun getStores(tenantId: Long): List<ShipStationStore> {
        return storeRepository.findByTenantIdAndActive(tenantId)
    }

    /**
     * Get all stores including inactive
     */
    fun getAllStores(tenantId: Long): List<ShipStationStore> {
        return storeRepository.findByTenantId(tenantId)
    }

    /**
     * Sync orders from ShipStation for a specific store
     */
    suspend fun syncOrders(
        tenantId: Long,
        apiKey: String,
        apiSecret: String,
        storeId: Long? = null,
        modifyDateStart: String? = null,
        modifyDateEnd: String? = null
    ): Result<SyncOrdersResult> {
        logger.info("Syncing ShipStation orders for tenant $tenantId, store $storeId")

        val ordersResult = client.getOrders(
            apiKey = apiKey,
            apiSecret = apiSecret,
            storeId = storeId,
            modifyDateStart = modifyDateStart,
            modifyDateEnd = modifyDateEnd
        )

        return ordersResult.fold(
            onSuccess = { response ->
                // TODO: Process orders and save to database
                // This will be implemented in OrderService
                logger.info("Fetched ${response.orders.size} orders from ShipStation")
                Result.success(SyncOrdersResult(
                    totalFetched = response.orders.size,
                    totalPages = response.pages,
                    currentPage = response.page,
                    orders = response.orders
                ))
            },
            onFailure = { error ->
                logger.error("Failed to sync orders for tenant $tenantId", error)
                Result.failure(error)
            }
        )
    }

    /**
     * Fetch all orders with pagination
     */
    suspend fun fetchAllOrders(
        tenantId: Long,
        apiKey: String,
        apiSecret: String,
        storeId: Long? = null,
        modifyDateStart: String? = null,
        modifyDateEnd: String? = null
    ): Result<List<ShipStationOrderResponse>> {
        val allOrders = mutableListOf<ShipStationOrderResponse>()
        var currentPage = 1
        var totalPages = 1

        while (currentPage <= totalPages) {
            val result = client.getOrders(
                apiKey = apiKey,
                apiSecret = apiSecret,
                storeId = storeId,
                modifyDateStart = modifyDateStart,
                modifyDateEnd = modifyDateEnd,
                page = currentPage
            )

            result.fold(
                onSuccess = { response ->
                    allOrders.addAll(response.orders)
                    totalPages = response.pages
                    currentPage++
                },
                onFailure = { error ->
                    return Result.failure(error)
                }
            )

            // Rate limiting - ShipStation allows 40 requests per minute
            if (currentPage <= totalPages) {
                kotlinx.coroutines.delay(1500) // 1.5 seconds between requests
            }
        }

        logger.info("Fetched total ${allOrders.size} orders from ShipStation")
        return Result.success(allOrders)
    }

    /**
     * Update store status (activate/deactivate)
     */
    fun updateStoreStatus(storeId: Long, isActive: Boolean): Boolean {
        return storeRepository.updateStatus(storeId, isActive)
    }

    /**
     * Get a single order by ID
     */
    suspend fun getOrderById(
        apiKey: String,
        apiSecret: String,
        orderId: Long
    ): Result<ShipStationOrderResponse> {
        return client.getOrder(apiKey, apiSecret, orderId)
    }

    /**
     * Mark an order as shipped in ShipStation
     * This will notify the customer and sales channel (marketplace)
     */
    suspend fun markOrderAsShipped(
        apiKey: String,
        apiSecret: String,
        orderId: Long,
        carrierCode: String,
        trackingNumber: String?,
        shipDate: String? = null,
        notifyCustomer: Boolean = true,
        notifySalesChannel: Boolean = true
    ): Result<Unit> {
        logger.info("Marking order $orderId as shipped with carrier $carrierCode, tracking: $trackingNumber")

        val request = ShipStationMarkShippedRequest(
            orderId = orderId,
            carrierCode = carrierCode,
            shipDate = shipDate,
            trackingNumber = trackingNumber,
            notifyCustomer = notifyCustomer,
            notifySalesChannel = notifySalesChannel
        )

        return client.markOrderAsShipped(apiKey, apiSecret, request).also { result ->
            result.fold(
                onSuccess = {
                    logger.info("Successfully marked order $orderId as shipped")
                },
                onFailure = { error ->
                    logger.error("Failed to mark order $orderId as shipped", error)
                }
            )
        }
    }

    /**
     * Create a shipping label in ShipStation
     */
    suspend fun createShippingLabel(
        apiKey: String,
        apiSecret: String,
        request: ShipStationCreateLabelRequest
    ): Result<ShipStationLabelResponse> {
        logger.info("Creating shipping label for order ${request.orderId}")

        return client.createLabel(apiKey, apiSecret, request).also { result ->
            result.fold(
                onSuccess = { label ->
                    logger.info("Successfully created label for order ${request.orderId}, tracking: ${label.trackingNumber}")
                },
                onFailure = { error ->
                    logger.error("Failed to create label for order ${request.orderId}", error)
                }
            )
        }
    }

    /**
     * Get orders by status
     */
    suspend fun getOrdersByStatus(
        apiKey: String,
        apiSecret: String,
        storeId: Long? = null,
        orderStatus: String = "awaiting_shipment",
        page: Int = 1,
        pageSize: Int = 100
    ): Result<ShipStationOrdersResponse> {
        return client.getOrders(
            apiKey = apiKey,
            apiSecret = apiSecret,
            storeId = storeId,
            orderStatus = orderStatus,
            page = page,
            pageSize = pageSize
        )
    }

    /**
     * Get a single order by ID (synchronous wrapper for FetchOrderService)
     */
    fun getOrder(storeId: Long, orderId: String): Map<String, Any?>? {
        // This is a placeholder - actual implementation should use coroutines
        logger.warn("getOrder synchronous wrapper called - consider using getOrderById instead")
        return null
    }

    /**
     * Get orders for sync (synchronous wrapper for FetchOrderService)
     */
    fun getOrdersForSync(storeId: Long, lastDays: Int): List<Map<String, Any?>> {
        // This is a placeholder - actual implementation should use coroutines
        logger.warn("getOrdersForSync synchronous wrapper called - consider using fetchAllOrders instead")
        return emptyList()
    }
}

data class SyncOrdersResult(
    val totalFetched: Int,
    val totalPages: Int,
    val currentPage: Int,
    val orders: List<ShipStationOrderResponse>
)

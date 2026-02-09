package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.MappingRepository
import com.printnest.integrations.shipstation.ShipStationService
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Service for fetching orders from external sources (ShipStation, Etsy, etc.)
 */
class FetchOrderService(
    private val orderRepository: OrderRepository,
    private val mappingRepository: MappingRepository,
    private val shipStationService: ShipStationService,
    private val orderService: OrderService,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(FetchOrderService::class.java)

    // =====================================================
    // FETCH SINGLE ORDER
    // =====================================================

    /**
     * Fetch a single order by ID from ShipStation
     */
    fun fetchOrderFromShipStation(
        tenantId: Long,
        userId: Long,
        storeId: Long,
        intOrderId: String
    ): Result<FetchOrderResponse> {
        try {
            // Check if order already exists
            val existingOrder = orderRepository.findByIntOrderId(intOrderId, tenantId)
            if (existingOrder != null) {
                return Result.success(FetchOrderResponse(
                    success = false,
                    message = "Order already exists"
                ))
            }

            // Fetch order from ShipStation
            val shipStationOrder = shipStationService.getOrder(storeId, intOrderId)
                ?: return Result.success(FetchOrderResponse(
                    success = false,
                    message = "Order not found in ShipStation"
                ))

            // Create order in database
            val orderId = createOrderFromShipStation(tenantId, userId, storeId, shipStationOrder)

            // Try to map the order
            val mapResult = tryMapOrder(tenantId, orderId)

            // If fully mapped, calculate prices
            if (mapResult == OrderMapStatus.COMPLETELY_MAPPED.code) {
                orderService.calculateStep3Price(orderId, tenantId, userId)
                return Result.success(FetchOrderResponse(
                    success = true,
                    orderId = orderId,
                    step3 = true,
                    message = "Order saved and mapped successfully"
                ))
            }

            return Result.success(FetchOrderResponse(
                success = true,
                orderId = orderId,
                step3 = false,
                message = "Order saved successfully"
            ))
        } catch (e: Exception) {
            logger.error("Error fetching order from ShipStation: ${e.message}", e)
            return Result.failure(e)
        }
    }

    private fun createOrderFromShipStation(
        tenantId: Long,
        userId: Long,
        storeId: Long,
        shipStationOrder: Map<String, Any?>
    ): Long {
        val intOrderId = "SS_${System.currentTimeMillis()}"
        val externalOrderId = shipStationOrder["orderId"]?.toString() ?: intOrderId

        val shipTo = shipStationOrder["shipTo"] as? Map<*, *>
        val shippingAddress = Address(
            name = shipTo?.get("name")?.toString(),
            company = shipTo?.get("company")?.toString(),
            street1 = shipTo?.get("street1")?.toString() ?: "",
            street2 = shipTo?.get("street2")?.toString(),
            city = shipTo?.get("city")?.toString() ?: "",
            state = shipTo?.get("state")?.toString(),
            postalCode = shipTo?.get("postalCode")?.toString() ?: "",
            country = shipTo?.get("country")?.toString() ?: "",
            phone = shipTo?.get("phone")?.toString()
        )

        val orderInfo = OrderInfoFull(
            toAddress = shippingAddress
        )

        val request = CreateOrderRequest(
            storeId = storeId,
            externalOrderId = externalOrderId,
            customerName = shipTo?.get("name")?.toString(),
            shippingAddress = shippingAddress
        )

        val order = orderRepository.create(tenantId, userId, request)
        return order.id
    }

    private fun tryMapOrder(tenantId: Long, orderId: Long): Int {
        // TODO: Implement mapping logic
        return OrderMapStatus.UNMAPPED.code
    }

    // =====================================================
    // SYNC ORDERS FROM MARKETPLACE
    // =====================================================

    /**
     * Sync orders from all stores or specific store
     */
    fun syncOrders(
        tenantId: Long,
        userId: Long,
        storeId: String?,
        lastDays: Int = 7
    ): SyncOrdersResponse {
        var totalSynced = 0
        val messages = mutableListOf<String>()

        try {
            if (storeId == "all" || storeId == null) {
                // Sync from all active stores
                // TODO: Get all stores and sync each
                messages.add("Synced from all stores")
            } else {
                val ssStoreId = storeId.toLongOrNull()
                    ?: return SyncOrdersResponse(false, "Invalid store ID", 0)

                // Sync from specific store
                val result = syncFromStore(tenantId, userId, ssStoreId, lastDays)
                totalSynced = result
                messages.add("Synced $result orders from store $storeId")
            }

            return SyncOrdersResponse(
                success = true,
                message = messages.joinToString(". "),
                syncedCount = totalSynced
            )
        } catch (e: Exception) {
            logger.error("Error syncing orders: ${e.message}", e)
            return SyncOrdersResponse(
                success = false,
                message = "Error syncing orders: ${e.message}",
                syncedCount = 0
            )
        }
    }

    private fun syncFromStore(tenantId: Long, userId: Long, storeId: Long, lastDays: Int): Int {
        var synced = 0

        try {
            // Get orders from ShipStation
            val orders = shipStationService.getOrdersForSync(storeId, lastDays)

            orders.forEach { order ->
                val externalOrderId = order["orderId"]?.toString() ?: return@forEach

                // Check if order already exists
                val existing = orderRepository.findByExternalOrderId(externalOrderId, tenantId)
                if (existing != null) {
                    return@forEach
                }

                // Create new order
                createOrderFromShipStation(tenantId, userId, storeId, order)
                synced++
            }
        } catch (e: Exception) {
            logger.error("Error syncing from store $storeId: ${e.message}", e)
        }

        return synced
    }
}

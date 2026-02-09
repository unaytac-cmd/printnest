package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.BatchRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.GangsheetRepository
import com.printnest.domain.tables.Orders
import com.printnest.domain.tables.OrderProducts
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class BatchService(
    private val batchRepository: BatchRepository,
    private val orderRepository: OrderRepository,
    private val gangsheetRepository: GangsheetRepository,
    private val gangsheetService: GangsheetService
) : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // BATCH LISTING
    // =====================================================

    fun getBatches(tenantId: Long, filters: BatchFilters): BatchListResponse {
        val (batches, total) = batchRepository.findAll(tenantId, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return BatchListResponse(
            batches = batches,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getBatch(id: Long, tenantId: Long, withOrders: Boolean = true): Batch? {
        return if (withOrders) {
            batchRepository.findByIdWithOrders(id, tenantId)
        } else {
            batchRepository.findById(id, tenantId)
        }
    }

    fun getBatchHistory(batchId: Long): List<BatchHistoryItem> {
        return batchRepository.findBatchHistory(batchId)
    }

    // =====================================================
    // BATCH CREATION
    // =====================================================

    fun createBatch(tenantId: Long, name: String, orderIds: List<Long>): Result<Batch> {
        // Validate that orders exist and belong to tenant
        if (orderIds.isNotEmpty()) {
            val validOrderIds = validateOrderIds(orderIds, tenantId)
            if (validOrderIds.size != orderIds.size) {
                val invalidIds = orderIds.filter { it !in validOrderIds }
                return Result.failure(IllegalArgumentException("Invalid order IDs: ${invalidIds.joinToString(", ")}"))
            }
        }

        val batch = batchRepository.create(tenantId, name, orderIds)
        return Result.success(batchRepository.findByIdWithOrders(batch.id, tenantId)!!)
    }

    // =====================================================
    // BATCH UPDATES
    // =====================================================

    fun updateBatch(id: Long, tenantId: Long, userId: Long, request: UpdateBatchRequest): Result<Batch> {
        val batch = batchRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        // Cannot update a completed or processing batch
        if (batch.status == BatchStatus.PROCESSING.code || batch.status == BatchStatus.COMPLETED.code) {
            return Result.failure(IllegalStateException("Cannot update batch in current status"))
        }

        // Update name if provided
        request.name?.let { newName ->
            batchRepository.updateName(id, tenantId, newName)
        }

        // Update status if provided
        request.status?.let { newStatus ->
            // Validate status transition
            if (!isValidStatusTransition(batch.status, newStatus)) {
                return Result.failure(IllegalStateException("Invalid status transition from ${BatchStatus.fromCode(batch.status)} to ${BatchStatus.fromCode(newStatus)}"))
            }
            batchRepository.updateStatus(id, tenantId, userId, newStatus)
        }

        return Result.success(batchRepository.findByIdWithOrders(id, tenantId)!!)
    }

    fun renameBatch(id: Long, tenantId: Long, newName: String): Result<Batch> {
        val batch = batchRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        batchRepository.updateName(id, tenantId, newName)
        return Result.success(batchRepository.findById(id, tenantId)!!)
    }

    fun deleteBatch(id: Long, tenantId: Long, userId: Long): Result<Boolean> {
        val batch = batchRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        // Cannot delete a processing batch
        if (batch.status == BatchStatus.PROCESSING.code) {
            return Result.failure(IllegalStateException("Cannot delete batch while processing"))
        }

        return Result.success(batchRepository.delete(id, tenantId, userId))
    }

    // =====================================================
    // ORDER MANAGEMENT IN BATCH
    // =====================================================

    fun addOrdersToBatch(id: Long, tenantId: Long, userId: Long, orderIds: List<Long>): Result<Batch> {
        val batch = batchRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        // Cannot modify a processing or completed batch
        if (batch.status == BatchStatus.PROCESSING.code || batch.status == BatchStatus.COMPLETED.code) {
            return Result.failure(IllegalStateException("Cannot add orders to batch in current status"))
        }

        // Validate order IDs
        val validOrderIds = validateOrderIds(orderIds, tenantId)
        if (validOrderIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid order IDs provided"))
        }

        batchRepository.addOrdersToBatch(id, tenantId, validOrderIds, userId)
        return Result.success(batchRepository.findByIdWithOrders(id, tenantId)!!)
    }

    fun removeOrderFromBatch(id: Long, tenantId: Long, userId: Long, orderId: Long): Result<Batch?> {
        val batch = batchRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        // Cannot modify a processing or completed batch
        if (batch.status == BatchStatus.PROCESSING.code || batch.status == BatchStatus.COMPLETED.code) {
            return Result.failure(IllegalStateException("Cannot remove orders from batch in current status"))
        }

        // Check if order is in batch
        if (orderId !in batch.orderIds) {
            return Result.failure(IllegalArgumentException("Order not found in batch"))
        }

        batchRepository.removeOrderFromBatch(id, tenantId, orderId, userId)

        // If batch was deleted (no orders left), return null
        val updatedBatch = batchRepository.findByIdWithOrders(id, tenantId)
        return Result.success(updatedBatch)
    }

    // =====================================================
    // COMBINE ORDERS
    // =====================================================

    fun combineOrders(tenantId: Long, userId: Long, orderIds: List<Long>, primaryOrderId: Long?): Result<CombineOrdersResponse> {
        if (orderIds.size < 2) {
            return Result.failure(IllegalArgumentException("At least 2 orders required to combine"))
        }

        // Validate orders exist and belong to tenant
        val validOrderIds = validateOrderIds(orderIds, tenantId)
        if (validOrderIds.size != orderIds.size) {
            val invalidIds = orderIds.filter { it !in validOrderIds }
            return Result.failure(IllegalArgumentException("Invalid order IDs: ${invalidIds.joinToString(", ")}"))
        }

        // Validate orders can be combined (same customer, compatible statuses)
        val orders = orderIds.mapNotNull { orderRepository.findByIdWithProducts(it, tenantId) }

        // Check if all orders have the same customer email or shipping address
        val customerEmails = orders.mapNotNull { it.customerEmail }.distinct()
        if (customerEmails.size > 1) {
            return Result.failure(IllegalArgumentException("Cannot combine orders from different customers"))
        }

        // Check order statuses - only combine orders in editable statuses
        val nonEditableOrders = orders.filter {
            OrderStatus.fromCode(it.orderStatus) !in OrderStatus.EDITABLE_STATUSES
        }
        if (nonEditableOrders.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot combine orders that are not in editable status"))
        }

        // Determine primary order (keep this one, merge others into it)
        val primary = primaryOrderId?.let { pid ->
            orders.find { it.id == pid }
        } ?: orders.first()

        val otherOrders = orders.filter { it.id != primary.id }

        // Combine products from other orders into primary
        transaction {
            otherOrders.forEach { order ->
                // Move products to primary order
                OrderProducts.update(
                    where = { OrderProducts.orderId eq order.id }
                ) {
                    it[orderId] = primary.id
                }

                // Mark original order as COMBINED
                orderRepository.updateStatus(order.id, tenantId, userId, OrderStatus.COMBINED.code, "Combined into order #${primary.id}")
            }

            // Recalculate primary order totals
            val allProducts = orderRepository.findOrderProducts(primary.id, tenantId)
            val newTotal: java.math.BigDecimal = allProducts.fold(java.math.BigDecimal.ZERO) { acc, it ->
                acc + (it.priceBreakdown?.lineTotal ?: it.unitPrice * java.math.BigDecimal(it.quantity))
            }

            orderRepository.update(primary.id, tenantId) {
                it[Orders.totalAmount] = newTotal
            }

            // Add history entry
            orderRepository.createHistoryEntry(
                tenantId, primary.id, userId,
                primary.orderStatus, primary.orderStatus,
                "orders_combined",
                "Combined orders: ${otherOrders.map { "#${it.id}" }.joinToString(", ")}"
            )
        }

        return Result.success(CombineOrdersResponse(
            success = true,
            combinedOrderId = primary.id,
            originalOrderIds = orderIds,
            message = "Successfully combined ${orderIds.size} orders into order #${primary.id}"
        ))
    }

    // =====================================================
    // BATCH PROCESSING (GANGSHEET GENERATION)
    // =====================================================

    fun processBatch(id: Long, tenantId: Long, userId: Long, settings: GangsheetSettingsFull? = null): Result<ProcessBatchResponse> {
        val batch = batchRepository.findByIdWithOrders(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Batch not found"))

        // Check batch status
        if (batch.status == BatchStatus.PROCESSING.code) {
            return Result.failure(IllegalStateException("Batch is already processing"))
        }

        if (batch.status == BatchStatus.COMPLETED.code) {
            return Result.failure(IllegalStateException("Batch already completed"))
        }

        // Check if batch has orders
        if (batch.orderIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("Batch has no orders to process"))
        }

        // Validate all orders are in processable status (PENDING or URGENT)
        val invalidOrders = batch.orders.filter {
            val status = OrderStatus.fromCode(it.orderStatus)
            status != OrderStatus.PENDING && status != OrderStatus.URGENT
        }
        if (invalidOrders.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(
                "Some orders are not ready for processing: ${invalidOrders.map { "#${it.orderId}" }.joinToString(", ")}"
            ))
        }

        // Update batch status to PROCESSING
        batchRepository.updateStatus(id, tenantId, userId, BatchStatus.PROCESSING.code, "Started gangsheet generation")

        // Create gangsheet
        val createRequest = CreateGangsheetRequest(
            orderIds = batch.orderIds,
            name = "Gangsheet for ${batch.name}",
            settings = settings
        )

        val gangsheetResult = gangsheetService.createGangsheet(tenantId, createRequest)

        return gangsheetResult.fold(
            onSuccess = { gangsheetResponse ->
                // Link gangsheet to batch
                batchRepository.setGangsheet(id, tenantId, gangsheetResponse.gangsheet.id)

                Result.success(ProcessBatchResponse(
                    batchId = id,
                    gangsheetId = gangsheetResponse.gangsheet.id,
                    status = BatchStatus.PROCESSING.code,
                    message = "Gangsheet generation started"
                ))
            },
            onFailure = { error ->
                // Revert batch status on failure
                batchRepository.updateStatus(id, tenantId, userId, BatchStatus.READY.code, "Gangsheet creation failed: ${error.message}")
                Result.failure(error)
            }
        )
    }

    fun markBatchCompleted(id: Long, tenantId: Long, userId: Long?): Boolean {
        return batchRepository.updateStatus(id, tenantId, userId, BatchStatus.COMPLETED.code, "Processing completed")
    }

    // =====================================================
    // BATCH FILTERING BY STATUS
    // =====================================================

    fun getBatchesByStatus(tenantId: Long, status: BatchStatus, page: Int = 1, limit: Int = 20): BatchListResponse {
        val filters = BatchFilters(
            page = page,
            limit = limit,
            status = status.code
        )
        return getBatches(tenantId, filters)
    }

    fun getDraftBatches(tenantId: Long, page: Int = 1, limit: Int = 20): BatchListResponse {
        return getBatchesByStatus(tenantId, BatchStatus.DRAFT, page, limit)
    }

    fun getReadyBatches(tenantId: Long, page: Int = 1, limit: Int = 20): BatchListResponse {
        return getBatchesByStatus(tenantId, BatchStatus.READY, page, limit)
    }

    fun getProcessingBatches(tenantId: Long, page: Int = 1, limit: Int = 20): BatchListResponse {
        return getBatchesByStatus(tenantId, BatchStatus.PROCESSING, page, limit)
    }

    fun getCompletedBatches(tenantId: Long, page: Int = 1, limit: Int = 20): BatchListResponse {
        return getBatchesByStatus(tenantId, BatchStatus.COMPLETED, page, limit)
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun validateOrderIds(orderIds: List<Long>, tenantId: Long): List<Long> = transaction {
        Orders.selectAll()
            .where { (Orders.id inList orderIds) and (Orders.tenantId eq tenantId) }
            .map { it[Orders.id].value }
    }

    private fun isValidStatusTransition(currentStatus: Int, newStatus: Int): Boolean {
        val current = BatchStatus.fromCode(currentStatus)
        val new = BatchStatus.fromCode(newStatus)

        return when (current) {
            BatchStatus.DRAFT -> new in listOf(BatchStatus.READY, BatchStatus.DELETED)
            BatchStatus.READY -> new in listOf(BatchStatus.DRAFT, BatchStatus.PROCESSING, BatchStatus.DELETED)
            BatchStatus.PROCESSING -> new in listOf(BatchStatus.COMPLETED, BatchStatus.READY) // Can go back to READY on failure
            BatchStatus.COMPLETED -> new == BatchStatus.DELETED
            BatchStatus.DELETED -> false // Cannot transition from deleted
        }
    }

    fun getBatchStats(tenantId: Long): Map<String, Int> {
        val statusCounts = batchRepository.countBatchesByStatus(tenantId)
        return mapOf(
            "draft" to (statusCounts[BatchStatus.DRAFT] ?: 0),
            "ready" to (statusCounts[BatchStatus.READY] ?: 0),
            "processing" to (statusCounts[BatchStatus.PROCESSING] ?: 0),
            "completed" to (statusCounts[BatchStatus.COMPLETED] ?: 0),
            "total" to statusCounts.values.sum()
        )
    }

    fun findBatchesForOrder(orderId: Long, tenantId: Long): List<BatchListItem> {
        return batchRepository.findBatchesContainingOrder(orderId, tenantId)
    }
}

package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class BatchRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // BATCHES - CRUD OPERATIONS
    // =====================================================

    fun findAll(tenantId: Long, filters: BatchFilters): Pair<List<BatchListItem>, Int> = transaction {
        var query = OrderBatches.selectAll()
            .where { (OrderBatches.tenantId eq tenantId) and (OrderBatches.status neq BatchStatus.DELETED.code) }

        // Apply status filter
        filters.status?.let { status ->
            query = query.andWhere { OrderBatches.status eq status }
        }

        // Apply search filter
        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere { OrderBatches.name like "%$search%" }
            }
        }

        // Apply gangsheet filter
        filters.hasGangsheet?.let { hasGangsheet ->
            query = if (hasGangsheet) {
                query.andWhere { OrderBatches.gangsheetId.isNotNull() }
            } else {
                query.andWhere { OrderBatches.gangsheetId.isNull() }
            }
        }

        // Get total count
        val total = query.count().toInt()

        // Apply sorting
        val sortColumn = when (filters.sortBy) {
            "createdAt" -> OrderBatches.createdAt
            "updatedAt" -> OrderBatches.updatedAt
            "name" -> OrderBatches.name
            "status" -> OrderBatches.status
            else -> OrderBatches.createdAt
        }

        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Apply pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val batches = query.map { row ->
            val orderIds = parseOrderIds(row[OrderBatches.orderIds])
            BatchListItem(
                id = row[OrderBatches.id].value,
                name = row[OrderBatches.name],
                status = row[OrderBatches.status],
                statusLabel = BatchStatus.fromCode(row[OrderBatches.status]).label,
                orderCount = orderIds.size,
                gangsheetId = row[OrderBatches.gangsheetId]?.value,
                createdAt = row[OrderBatches.createdAt].toString(),
                updatedAt = row[OrderBatches.updatedAt].toString()
            )
        }

        Pair(batches, total)
    }

    fun findById(id: Long, tenantId: Long): Batch? = transaction {
        OrderBatches.selectAll()
            .where { (OrderBatches.id eq id) and (OrderBatches.tenantId eq tenantId) and (OrderBatches.status neq BatchStatus.DELETED.code) }
            .singleOrNull()
            ?.toBatch()
    }

    fun findByIdWithOrders(id: Long, tenantId: Long): Batch? = transaction {
        val batch = findById(id, tenantId) ?: return@transaction null

        val orderInfoList = if (batch.orderIds.isNotEmpty()) {
            Orders.selectAll()
                .where { (Orders.id inList batch.orderIds) and (Orders.tenantId eq tenantId) }
                .map { row ->
                    val productCount = OrderProducts.selectAll()
                        .where { OrderProducts.orderId eq row[Orders.id].value }
                        .count().toInt()

                    BatchOrderInfo(
                        orderId = row[Orders.id].value,
                        intOrderId = row[Orders.intOrderId],
                        externalOrderId = row[Orders.externalOrderId],
                        customerName = row[Orders.customerName],
                        customerEmail = row[Orders.customerEmail],
                        orderStatus = row[Orders.orderStatus],
                        orderStatusLabel = OrderStatus.fromCode(row[Orders.orderStatus]).name,
                        totalAmount = row[Orders.totalAmount],
                        productCount = productCount,
                        createdAt = row[Orders.createdAt].toString()
                    )
                }
        } else {
            emptyList()
        }

        batch.copy(orders = orderInfoList)
    }

    fun create(tenantId: Long, name: String, orderIds: List<Long>): Batch = transaction {
        val uniqueOrderIds = orderIds.distinct()
        val orderIdsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
            uniqueOrderIds
        )

        val id = OrderBatches.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.name] = name
            it[this.orderIds] = orderIdsJson
            it[status] = BatchStatus.DRAFT.code
        }

        // Create history entry
        createHistoryEntry(tenantId, id.value, null, null, BatchStatus.DRAFT.code, "created", "Batch created")

        findById(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, block: OrderBatches.(UpdateBuilder<Int>) -> Unit): Boolean = transaction {
        OrderBatches.update(
            where = { (OrderBatches.id eq id) and (OrderBatches.tenantId eq tenantId) }
        ) {
            block(it)
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateName(id: Long, tenantId: Long, newName: String): Boolean = transaction {
        update(id, tenantId) {
            it[name] = newName
        }
    }

    fun updateStatus(id: Long, tenantId: Long, userId: Long?, newStatus: Int, notes: String? = null): Boolean = transaction {
        val batch = findById(id, tenantId) ?: return@transaction false
        val previousStatus = batch.status

        val updated = update(id, tenantId) {
            it[status] = newStatus
        }

        if (updated) {
            createHistoryEntry(tenantId, id, userId, previousStatus, newStatus, "status_changed", notes)
        }

        updated
    }

    fun setGangsheet(id: Long, tenantId: Long, gangsheetId: Long): Boolean = transaction {
        update(id, tenantId) {
            it[OrderBatches.gangsheetId] = gangsheetId
        }
    }

    fun delete(id: Long, tenantId: Long, userId: Long?): Boolean = transaction {
        updateStatus(id, tenantId, userId, BatchStatus.DELETED.code, "Batch deleted")
    }

    // =====================================================
    // BATCH ORDERS MANAGEMENT
    // =====================================================

    fun addOrdersToBatch(id: Long, tenantId: Long, orderIds: List<Long>, userId: Long?): Boolean = transaction {
        val batch = findById(id, tenantId) ?: return@transaction false

        // Combine existing and new order IDs, removing duplicates
        val existingOrderIds = batch.orderIds.toMutableList()
        val newOrderIds = orderIds.filter { it !in existingOrderIds }
        existingOrderIds.addAll(newOrderIds)

        val orderIdsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
            existingOrderIds
        )

        val updated = update(id, tenantId) {
            it[OrderBatches.orderIds] = orderIdsJson
        }

        if (updated && newOrderIds.isNotEmpty()) {
            createHistoryEntry(
                tenantId, id, userId, null, batch.status,
                "orders_added",
                "Added ${newOrderIds.size} order(s): ${newOrderIds.joinToString(", ")}"
            )
        }

        updated
    }

    fun removeOrderFromBatch(id: Long, tenantId: Long, orderId: Long, userId: Long?): Boolean = transaction {
        val batch = findById(id, tenantId) ?: return@transaction false

        val updatedOrderIds = batch.orderIds.filter { it != orderId }

        // If no orders left, optionally delete the batch
        if (updatedOrderIds.isEmpty()) {
            return@transaction delete(id, tenantId, userId)
        }

        val orderIdsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
            updatedOrderIds
        )

        val updated = update(id, tenantId) {
            it[OrderBatches.orderIds] = orderIdsJson
        }

        if (updated) {
            createHistoryEntry(
                tenantId, id, userId, null, batch.status,
                "order_removed",
                "Removed order #$orderId"
            )
        }

        updated
    }

    fun getOrderIdsInBatch(id: Long, tenantId: Long): List<Long> = transaction {
        val batch = findById(id, tenantId) ?: return@transaction emptyList()
        batch.orderIds
    }

    // =====================================================
    // BATCH HISTORY
    // =====================================================

    fun findBatchHistory(batchId: Long): List<BatchHistoryItem> = transaction {
        BatchHistory.selectAll()
            .where { BatchHistory.batchId eq batchId }
            .orderBy(BatchHistory.createdAt, SortOrder.DESC)
            .map { it.toBatchHistoryItem() }
    }

    fun createHistoryEntry(
        tenantId: Long,
        batchId: Long,
        userId: Long?,
        previousStatus: Int?,
        newStatus: Int,
        action: String,
        notes: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Long = transaction {
        val metadataJson = json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                kotlinx.serialization.serializer<String>(),
                kotlinx.serialization.serializer<String>()
            ),
            metadata
        )

        BatchHistory.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.batchId] = batchId
            it[this.userId] = userId
            it[this.previousStatus] = previousStatus
            it[this.newStatus] = newStatus
            it[this.action] = action
            it[this.notes] = notes
            it[this.metadata] = metadataJson
        }.value
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    fun findBatchesContainingOrder(orderId: Long, tenantId: Long): List<BatchListItem> = transaction {
        OrderBatches.selectAll()
            .where { (OrderBatches.tenantId eq tenantId) and (OrderBatches.status neq BatchStatus.DELETED.code) }
            .mapNotNull { row ->
                val orderIds = parseOrderIds(row[OrderBatches.orderIds])
                if (orderId in orderIds) {
                    BatchListItem(
                        id = row[OrderBatches.id].value,
                        name = row[OrderBatches.name],
                        status = row[OrderBatches.status],
                        statusLabel = BatchStatus.fromCode(row[OrderBatches.status]).label,
                        orderCount = orderIds.size,
                        gangsheetId = row[OrderBatches.gangsheetId]?.value,
                        createdAt = row[OrderBatches.createdAt].toString(),
                        updatedAt = row[OrderBatches.updatedAt].toString()
                    )
                } else null
            }
    }

    fun countBatchesByStatus(tenantId: Long): Map<BatchStatus, Int> = transaction {
        OrderBatches.selectAll()
            .where { (OrderBatches.tenantId eq tenantId) and (OrderBatches.status neq BatchStatus.DELETED.code) }
            .groupBy { BatchStatus.fromCode(it[OrderBatches.status]) }
            .mapValues { it.value.size }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun parseOrderIds(orderIdsJson: String): List<Long> {
        return try {
            if (orderIdsJson.isNotEmpty() && orderIdsJson != "[]") {
                json.decodeFromString<List<Long>>(orderIdsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun ResultRow.toBatch(): Batch {
        val orderIds = parseOrderIds(this[OrderBatches.orderIds])

        return Batch(
            id = this[OrderBatches.id].value,
            tenantId = this[OrderBatches.tenantId].value,
            name = this[OrderBatches.name],
            status = this[OrderBatches.status],
            statusLabel = BatchStatus.fromCode(this[OrderBatches.status]).label,
            orderIds = orderIds,
            gangsheetId = this[OrderBatches.gangsheetId]?.value,
            orderCount = orderIds.size,
            createdAt = this[OrderBatches.createdAt].toString(),
            updatedAt = this[OrderBatches.updatedAt].toString()
        )
    }

    private fun ResultRow.toBatchHistoryItem(): BatchHistoryItem {
        val metadataJson = this[BatchHistory.metadata]
        val metadata = try {
            if (metadataJson.isNotEmpty() && metadataJson != "{}") {
                json.decodeFromString<Map<String, String>>(metadataJson)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }

        return BatchHistoryItem(
            id = this[BatchHistory.id].value,
            batchId = this[BatchHistory.batchId].value,
            userId = this[BatchHistory.userId]?.value,
            previousStatus = this[BatchHistory.previousStatus],
            newStatus = this[BatchHistory.newStatus],
            action = this[BatchHistory.action],
            notes = this[BatchHistory.notes],
            metadata = metadata,
            createdAt = this[BatchHistory.createdAt].toString()
        )
    }
}

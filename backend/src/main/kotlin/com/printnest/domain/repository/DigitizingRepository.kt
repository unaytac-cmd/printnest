package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.DigitizingOrders
import com.printnest.domain.tables.EmbroideryThreadColors
import com.printnest.domain.tables.Users
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DigitizingRepository(
    private val json: Json
) {

    // =====================================================
    // DIGITIZING ORDER QUERIES
    // =====================================================

    fun findOrderById(id: Long, tenantId: Long): DigitizingOrder? = transaction {
        (DigitizingOrders leftJoin Users)
            .selectAll()
            .where { (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId) }
            .singleOrNull()
            ?.toDigitizingOrder()
    }

    fun findAllOrders(
        tenantId: Long,
        filters: DigitizingOrderFilters
    ): Pair<List<DigitizingOrderListItem>, Int> = transaction {
        var query = DigitizingOrders.selectAll()
            .where { DigitizingOrders.tenantId eq tenantId }

        // Status filter
        filters.status?.let { status ->
            query = query.andWhere { DigitizingOrders.digitizingStatus eq status }
        }

        // Digitizer filter
        filters.digitizerId?.let { digitizerId ->
            query = query.andWhere { DigitizingOrders.digitizerId eq digitizerId }
        }

        // User filter
        filters.userId?.let { userId ->
            query = query.andWhere { DigitizingOrders.userId eq userId }
        }

        // Search filter (by name in details)
        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere {
                    DigitizingOrders.digitizingDetails.lowerCase() like "%${search.lowercase()}%"
                }
            }
        }

        // Date range filters
        filters.startDate?.let { startDate ->
            try {
                val date = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE)
                query = query.andWhere { DigitizingOrders.createdAt greaterEq date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) }
            } catch (_: Exception) { }
        }

        filters.endDate?.let { endDate ->
            try {
                val date = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE)
                query = query.andWhere { DigitizingOrders.createdAt lessEq date.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC) }
            } catch (_: Exception) { }
        }

        // Count total
        val total = query.count().toInt()

        // Sorting
        val sortColumn = when (filters.sortBy) {
            "status" -> DigitizingOrders.digitizingStatus
            "updatedAt" -> DigitizingOrders.updatedAt
            else -> DigitizingOrders.createdAt
        }
        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val orders = query.map { it.toDigitizingOrderListItem() }

        Pair(orders, total)
    }

    fun findOrdersByUser(userId: Long, tenantId: Long): List<DigitizingOrder> = transaction {
        DigitizingOrders.selectAll()
            .where { (DigitizingOrders.userId eq userId) and (DigitizingOrders.tenantId eq tenantId) }
            .orderBy(DigitizingOrders.createdAt, SortOrder.DESC)
            .map { it.toDigitizingOrder() }
    }

    fun findOrdersByDigitizer(digitizerId: Long, tenantId: Long): List<DigitizingOrder> = transaction {
        DigitizingOrders.selectAll()
            .where { (DigitizingOrders.digitizerId eq digitizerId) and (DigitizingOrders.tenantId eq tenantId) }
            .orderBy(DigitizingOrders.createdAt, SortOrder.DESC)
            .map { it.toDigitizingOrder() }
    }

    fun findPendingOrders(tenantId: Long): List<DigitizingOrder> = transaction {
        DigitizingOrders.selectAll()
            .where {
                (DigitizingOrders.tenantId eq tenantId) and
                (DigitizingOrders.digitizingStatus eq DigitizingStatus.PENDING.code)
            }
            .orderBy(DigitizingOrders.createdAt, SortOrder.ASC)
            .map { it.toDigitizingOrder() }
    }

    // =====================================================
    // DIGITIZING ORDER MUTATIONS
    // =====================================================

    fun createOrder(
        tenantId: Long,
        userId: Long,
        request: CreateDigitizingOrderRequest,
        initialStatus: Int = DigitizingStatus.PAYMENT_PENDING.code
    ): DigitizingOrder = transaction {
        val details = DigitizingDetails(
            name = request.name,
            fileUrl = request.fileUrl,
            width = request.width,
            height = request.height,
            orderNote = request.notes,
            stitchCount = request.estimatedStitchCount,
            colorCount = request.colorCount,
            colors = request.colors ?: emptyList(),
            payment = null,
            resultFiles = null
        )

        val id = DigitizingOrders.insertAndGetId {
            it[DigitizingOrders.tenantId] = tenantId
            it[DigitizingOrders.userId] = userId
            it[digitizerId] = null
            it[designId] = request.designId
            it[digitizingStatus] = initialStatus
            it[digitizingDetails] = json.encodeToString(DigitizingDetails.serializer(), details)
        }

        findOrderById(id.value, tenantId)!!
    }

    fun updateOrderStatus(
        id: Long,
        tenantId: Long,
        status: Int,
        requestedPrice: Double? = null
    ): DigitizingOrder? = transaction {
        val existingOrder = findOrderById(id, tenantId) ?: return@transaction null

        // Update details if price is set
        val updatedDetails = if (requestedPrice != null) {
            val details = parseDetails(existingOrder)
            val updatedPayment = (details.payment ?: DigitizingPayment()).copy(
                requestedPrice = requestedPrice
            )
            details.copy(payment = updatedPayment)
        } else null

        val updated = DigitizingOrders.update({
            (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId)
        }) {
            it[digitizingStatus] = status
            it[updatedAt] = Instant.now()
            if (updatedDetails != null) {
                it[digitizingDetails] = json.encodeToString(DigitizingDetails.serializer(), updatedDetails)
            }
        }

        if (updated > 0) findOrderById(id, tenantId) else null
    }

    fun assignDigitizer(id: Long, tenantId: Long, digitizerId: Long): DigitizingOrder? = transaction {
        val updated = DigitizingOrders.update({
            (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId)
        }) {
            it[DigitizingOrders.digitizerId] = digitizerId
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findOrderById(id, tenantId) else null
    }

    fun uploadResultFiles(
        id: Long,
        tenantId: Long,
        request: UploadDigitizedFileRequest
    ): DigitizingOrder? = transaction {
        val existingOrder = findOrderById(id, tenantId) ?: return@transaction null
        val details = parseDetails(existingOrder)

        val resultFiles = DigitizingResultFiles(
            dstFileUrl = request.dstFileUrl,
            pdfFileUrl = request.pdfFileUrl,
            pngFileUrl = request.pngFileUrl
        )

        val updatedDetails = details.copy(
            resultFiles = resultFiles,
            stitchCount = request.stitchCount ?: details.stitchCount,
            colorCount = request.colorCount ?: details.colorCount,
            colors = request.colors ?: details.colors
        )

        val updated = DigitizingOrders.update({
            (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId)
        }) {
            it[digitizingStatus] = DigitizingStatus.COMPLETED.code
            it[digitizingDetails] = json.encodeToString(DigitizingDetails.serializer(), updatedDetails)
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findOrderById(id, tenantId) else null
    }

    fun completePayment(
        id: Long,
        tenantId: Long,
        paymentMethod: String,
        paidAmount: Double
    ): DigitizingOrder? = transaction {
        val existingOrder = findOrderById(id, tenantId) ?: return@transaction null
        val details = parseDetails(existingOrder)

        val updatedPayment = (details.payment ?: DigitizingPayment()).copy(
            paymentMethod = paymentMethod,
            userPaidAmount = paidAmount
        )

        val updatedDetails = details.copy(payment = updatedPayment)

        val updated = DigitizingOrders.update({
            (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId)
        }) {
            it[digitizingStatus] = DigitizingStatus.PENDING.code
            it[digitizingDetails] = json.encodeToString(DigitizingDetails.serializer(), updatedDetails)
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findOrderById(id, tenantId) else null
    }

    fun cancelOrder(id: Long, tenantId: Long): Boolean = transaction {
        DigitizingOrders.update({
            (DigitizingOrders.id eq id) and (DigitizingOrders.tenantId eq tenantId)
        }) {
            it[digitizingStatus] = DigitizingStatus.CANCELLED.code
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // THREAD COLOR QUERIES
    // =====================================================

    fun findColorById(id: Long, tenantId: Long): ThreadColor? = transaction {
        EmbroideryThreadColors.selectAll()
            .where { (EmbroideryThreadColors.id eq id) and (EmbroideryThreadColors.tenantId eq tenantId) }
            .singleOrNull()
            ?.toThreadColor()
    }

    fun findAllColors(tenantId: Long, inStockOnly: Boolean = false): List<ThreadColor> = transaction {
        var query = EmbroideryThreadColors.selectAll()
            .where { (EmbroideryThreadColors.tenantId eq tenantId) and (EmbroideryThreadColors.status eq 1) }

        if (inStockOnly) {
            query = query.andWhere { EmbroideryThreadColors.inStock eq true }
        }

        query.orderBy(EmbroideryThreadColors.sortOrder, SortOrder.ASC)
            .map { it.toThreadColor() }
    }

    // =====================================================
    // THREAD COLOR MUTATIONS
    // =====================================================

    fun createColor(tenantId: Long, request: CreateThreadColorRequest): ThreadColor = transaction {
        val id = EmbroideryThreadColors.insertAndGetId {
            it[EmbroideryThreadColors.tenantId] = tenantId
            it[name] = request.name
            it[hexColor] = request.hexCode
            it[pantone] = request.pantone
            it[inStock] = request.inStock
            it[sortOrder] = request.sortOrder
            it[status] = 1
        }

        findColorById(id.value, tenantId)!!
    }

    fun updateColor(id: Long, tenantId: Long, request: UpdateThreadColorRequest): ThreadColor? = transaction {
        val updated = EmbroideryThreadColors.update({
            (EmbroideryThreadColors.id eq id) and (EmbroideryThreadColors.tenantId eq tenantId)
        }) {
            request.name?.let { n -> it[name] = n }
            request.hexCode?.let { h -> it[hexColor] = h }
            request.pantone?.let { p -> it[pantone] = p }
            request.inStock?.let { s -> it[inStock] = s }
            request.sortOrder?.let { o -> it[sortOrder] = o }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findColorById(id, tenantId) else null
    }

    fun deleteColor(id: Long, tenantId: Long): Boolean = transaction {
        EmbroideryThreadColors.update({
            (EmbroideryThreadColors.id eq id) and (EmbroideryThreadColors.tenantId eq tenantId)
        }) {
            it[status] = 0
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // DIGITIZER QUERIES
    // =====================================================

    fun findDigitizers(tenantId: Long): List<Digitizer> = transaction {
        // Find employees (user_type = 'EMPLOYEE') who can do digitizing work
        val digitizersBase = Users.selectAll()
            .where { (Users.tenantId eq tenantId) and (Users.role eq "EMPLOYEE") and (Users.status eq 1) }
            .map { row ->
                val userId = row[Users.id].value
                val firstName = row[Users.firstName] ?: ""
                val lastName = row[Users.lastName] ?: ""
                val email = row[Users.email]

                // Count active tasks
                val taskCount = DigitizingOrders.selectAll()
                    .where {
                        (DigitizingOrders.digitizerId eq userId) and
                        (DigitizingOrders.digitizingStatus inList listOf(
                            DigitizingStatus.PENDING.code,
                            DigitizingStatus.IN_PROGRESS.code
                        ))
                    }
                    .count().toInt()

                Digitizer(
                    id = userId,
                    name = "$firstName $lastName".trim().ifEmpty { email },
                    email = email,
                    taskCount = taskCount
                )
            }

        digitizersBase.sortedBy { it.taskCount }
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    fun getOrderStats(tenantId: Long): Map<String, Int> = transaction {
        val stats = mutableMapOf<String, Int>()

        DigitizingStatus.entries.forEach { status ->
            val count = DigitizingOrders.selectAll()
                .where {
                    (DigitizingOrders.tenantId eq tenantId) and
                    (DigitizingOrders.digitizingStatus eq status.code)
                }
                .count().toInt()
            stats[status.label] = count
        }

        stats["total"] = stats.values.sum()
        stats
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun parseDetails(order: DigitizingOrder): DigitizingDetails {
        return DigitizingDetails(
            name = order.name,
            fileUrl = order.fileUrl,
            width = order.width,
            height = order.height,
            orderNote = order.notes,
            stitchCount = order.stitchCount,
            colorCount = order.colorCount,
            colors = order.colors,
            payment = order.payment,
            resultFiles = order.resultFiles
        )
    }

    private fun ResultRow.toDigitizingOrder(): DigitizingOrder {
        val statusCode = this[DigitizingOrders.digitizingStatus]
        val statusLabel = DigitizingStatus.fromCode(statusCode)?.label ?: "Unknown"

        val detailsJson = this[DigitizingOrders.digitizingDetails]
        val details = try {
            if (detailsJson.isNotBlank() && detailsJson != "{}") {
                json.decodeFromString<DigitizingDetails>(detailsJson)
            } else null
        } catch (e: Exception) {
            null
        }

        return DigitizingOrder(
            id = this[DigitizingOrders.id].value,
            tenantId = this[DigitizingOrders.tenantId].value,
            userId = this[DigitizingOrders.userId].value,
            digitizerId = this[DigitizingOrders.digitizerId]?.value,
            designId = this[DigitizingOrders.designId]?.value,
            status = statusCode,
            statusLabel = statusLabel,
            name = details?.name ?: "",
            notes = details?.orderNote,
            fileUrl = details?.fileUrl ?: "",
            width = details?.width,
            height = details?.height,
            stitchCount = details?.stitchCount,
            colorCount = details?.colorCount,
            colors = details?.colors ?: emptyList(),
            payment = details?.payment,
            resultFiles = details?.resultFiles,
            createdAt = this[DigitizingOrders.createdAt].toString(),
            updatedAt = this[DigitizingOrders.updatedAt].toString()
        )
    }

    private fun ResultRow.toDigitizingOrderListItem(): DigitizingOrderListItem {
        val statusCode = this[DigitizingOrders.digitizingStatus]
        val statusLabel = DigitizingStatus.fromCode(statusCode)?.label ?: "Unknown"

        val detailsJson = this[DigitizingOrders.digitizingDetails]
        val details = try {
            if (detailsJson.isNotBlank() && detailsJson != "{}") {
                json.decodeFromString<DigitizingDetails>(detailsJson)
            } else null
        } catch (e: Exception) {
            null
        }

        return DigitizingOrderListItem(
            id = this[DigitizingOrders.id].value,
            name = details?.name ?: "",
            status = statusCode,
            statusLabel = statusLabel,
            thumbnailUrl = details?.resultFiles?.pngFileUrl ?: details?.fileUrl,
            requestedPrice = details?.payment?.requestedPrice,
            customerName = null, // Would need join to get this
            digitizerName = null, // Would need join to get this
            createdAt = this[DigitizingOrders.createdAt].toString(),
            updatedAt = this[DigitizingOrders.updatedAt].toString()
        )
    }

    private fun ResultRow.toThreadColor(): ThreadColor {
        return ThreadColor(
            id = this[EmbroideryThreadColors.id].value,
            name = this[EmbroideryThreadColors.name],
            hexCode = this[EmbroideryThreadColors.hexColor],
            pantone = this[EmbroideryThreadColors.pantone],
            inStock = this[EmbroideryThreadColors.inStock],
            sortOrder = this[EmbroideryThreadColors.sortOrder]
        )
    }
}

package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExportRepository {

    private val logger = LoggerFactory.getLogger(ExportRepository::class.java)

    // =====================================================
    // ORDER EXPORT DATA
    // =====================================================

    fun getOrdersForExport(tenantId: Long, orderIds: List<Long>): List<OrderExportRow> = transaction {
        if (orderIds.isEmpty()) return@transaction emptyList()

        val query = """
            SELECT
                o.id AS order_id,
                o.int_order_id,
                CONCAT(u.first_name, ' ', u.last_name) AS customer_name,
                s.store_name,
                o.created_at AS order_date,
                pc.name AS product_category,
                p.title AS product_name,
                op.quantity,
                v.option1_value AS option1,
                v.option2_value AS option2,
                op.modification_detail,
                (o.total_amount - o.shipping_amount) AS product_amount,
                o.shipping_amount,
                o.total_amount,
                o.order_info,
                COALESCE(o.order_info, '{}') AS order_info_raw,
                o.order_status
            FROM orders o
            INNER JOIN order_products op ON o.id = op.order_id
            LEFT JOIN users u ON o.user_id = u.id
            LEFT JOIN stores s ON o.store_id = s.id
            LEFT JOIN products p ON op.product_id = p.id
            LEFT JOIN product_categories pc ON p.category_id = pc.id
            LEFT JOIN variants v ON op.variant_id = v.id
            WHERE o.tenant_id = ? AND o.id IN (${orderIds.joinToString(",") { "?" }})
            ORDER BY o.id ASC, op.id ASC
        """.trimIndent()

        // Use the Exposed DSL fallback method instead of raw SQL
        // Raw SQL exec has compatibility issues with current Exposed version
        getOrdersForExportFallback(tenantId, orderIds)
    }

    private fun getOrdersForExportFallback(tenantId: Long, orderIds: List<Long>): List<OrderExportRow> = transaction {
        val results = mutableListOf<OrderExportRow>()

        Orders.innerJoin(OrderProducts, { id }, { orderId })
            .leftJoin(Users, { Orders.userId }, { id })
            .leftJoin(Stores, { Orders.storeId }, { id })
            .leftJoin(Products, { OrderProducts.productId }, { id })
            .leftJoin(ProductCategories, { Products.categoryId }, { id })
            .leftJoin(Variants, { OrderProducts.variantId }, { id })
            .selectAll()
            .where { (Orders.tenantId eq tenantId) and (Orders.id inList orderIds) }
            .orderBy(Orders.id, SortOrder.ASC)
            .forEach { row ->
                val modificationDetail = row[OrderProducts.modificationDetail]
                val modifications = parseModifications(modificationDetail)

                val orderInfo = row[Orders.orderInfo]
                val shippingPaid = parseShippingPaid(orderInfo)

                results.add(
                    OrderExportRow(
                        orderId = row[Orders.id].value,
                        intOrderId = row[Orders.intOrderId],
                        customerName = "${row.getOrNull(Users.firstName) ?: ""} ${row.getOrNull(Users.lastName) ?: ""}".trim(),
                        storeName = row.getOrNull(Stores.storeName),
                        orderDate = row[Orders.createdAt].toString(),
                        productCategory = row.getOrNull(ProductCategories.name),
                        productName = row.getOrNull(Products.title),
                        quantity = row[OrderProducts.quantity],
                        option1 = row.getOrNull(Variants.option1Value),
                        option2 = row.getOrNull(Variants.option2Value),
                        modifications = modifications,
                        productAmount = row[Orders.totalAmount].subtract(row[Orders.shippingAmount]),
                        shippingAmount = row[Orders.shippingAmount],
                        totalAmount = row[Orders.totalAmount],
                        shippingPaid = shippingPaid,
                        orderNote = parseOrderNote(orderInfo),
                        orderStatus = row[Orders.orderStatus]
                    )
                )
            }

        results
    }

    // =====================================================
    // OUT OF STOCK EXPORT DATA
    // =====================================================

    fun getOutOfStockProducts(tenantId: Long, orderIds: List<Long>): List<OutOfStockProductRow> = transaction {
        if (orderIds.isEmpty()) return@transaction emptyList()

        val results = mutableListOf<OutOfStockProductRow>()

        OrderProducts.innerJoin(Orders, { orderId }, { id })
            .innerJoin(Variants, { OrderProducts.variantId }, { id })
            .innerJoin(Products, { OrderProducts.productId }, { id })
            .leftJoin(Suppliers, { Products.supplierId }, { id })
            .selectAll()
            .where {
                (Orders.tenantId eq tenantId) and
                (Orders.id inList orderIds) and
                (Variants.inStock eq false)
            }
            .orderBy(Orders.id, SortOrder.ASC)
            .forEach { row ->
                val productDetail = row[OrderProducts.productDetail]
                val quantity = parseQuantityFromProductDetail(productDetail)

                results.add(
                    OutOfStockProductRow(
                        productTitle = row[Products.title],
                        option1 = row.getOrNull(Variants.option1Value),
                        option2 = row.getOrNull(Variants.option2Value),
                        supplierName = row.getOrNull(Suppliers.name),
                        quantity = quantity
                    )
                )
            }

        results
    }

    fun getOutOfStockOrderData(tenantId: Long, orderIds: List<Long>): List<OutOfStockOrderDataRow> = transaction {
        if (orderIds.isEmpty()) return@transaction emptyList()

        val results = mutableListOf<OutOfStockOrderDataRow>()

        OrderProducts.innerJoin(Orders, { orderId }, { id })
            .innerJoin(Variants, { OrderProducts.variantId }, { id })
            .innerJoin(Products, { OrderProducts.productId }, { id })
            .leftJoin(Users, { Orders.userId }, { id })
            .selectAll()
            .where {
                (Orders.tenantId eq tenantId) and
                (Orders.id inList orderIds) and
                (Variants.inStock eq false)
            }
            .orderBy(Orders.id, SortOrder.ASC)
            .forEach { row ->
                val productDetail = row[OrderProducts.productDetail]
                val quantity = parseQuantityFromProductDetail(productDetail)

                results.add(
                    OutOfStockOrderDataRow(
                        orderId = row[Orders.id].value,
                        productTitle = row[Products.title],
                        option1 = row.getOrNull(Variants.option1Value),
                        option2 = row.getOrNull(Variants.option2Value),
                        quantity = quantity,
                        customerName = "${row.getOrNull(Users.firstName) ?: ""} ${row.getOrNull(Users.lastName) ?: ""}".trim()
                    )
                )
            }

        results
    }

    // =====================================================
    // PRODUCT EXPORT DATA
    // =====================================================

    fun getProductsForExport(tenantId: Long, filters: ExportFilters?): List<ProductExportRow> = transaction {
        val results = mutableListOf<ProductExportRow>()

        var query = Products.leftJoin(ProductCategories, { categoryId }, { id })
            .selectAll()
            .where { Products.tenantId eq tenantId }

        filters?.status?.let { status ->
            query = query.andWhere { Products.status eq status }
        }

        filters?.categoryId?.let { categoryId ->
            query = query.andWhere { Products.categoryId eq categoryId }
        }

        query.orderBy(Products.id, SortOrder.ASC)
            .forEach { row ->
                val productId = row[Products.id].value

                // Get variant stats
                val variantStats = Variants.selectAll()
                    .where { Variants.productId eq productId }
                    .fold(Triple(0, 0, 0)) { acc, variantRow ->
                        val inStock = if (variantRow[Variants.inStock]) 1 else 0
                        Triple(acc.first + 1, acc.second + inStock, acc.third + (1 - inStock))
                    }

                val designTypeName = when (row[Products.designType]) {
                    1 -> "Image/DTF"
                    2 -> "Embroidery"
                    3 -> "Vector"
                    4 -> "Ornament"
                    else -> "Unknown"
                }

                results.add(
                    ProductExportRow(
                        productId = productId,
                        title = row[Products.title],
                        categoryName = row.getOrNull(ProductCategories.name),
                        basePrice = row[Products.basePrice],
                        option1Name = row[Products.option1Name],
                        option2Name = row[Products.option2Name],
                        variantCount = variantStats.first,
                        inStockCount = variantStats.second,
                        outOfStockCount = variantStats.third,
                        designType = designTypeName,
                        status = row[Products.status],
                        createdAt = row[Products.createdAt].toString()
                    )
                )
            }

        results
    }

    fun getVariantsForExport(tenantId: Long, filters: ExportFilters?): List<VariantExportRow> = transaction {
        val results = mutableListOf<VariantExportRow>()

        var query = Variants.innerJoin(Products, { productId }, { id })
            .selectAll()
            .where { Variants.tenantId eq tenantId }

        filters?.productId?.let { productId ->
            query = query.andWhere { Variants.productId eq productId }
        }

        query.orderBy(Variants.productId, SortOrder.ASC)
            .orderBy(Variants.id, SortOrder.ASC)
            .forEach { row ->
                results.add(
                    VariantExportRow(
                        variantId = row[Variants.id].value,
                        productId = row[Variants.productId].value,
                        productTitle = row[Products.title],
                        option1Value = row[Variants.option1Value],
                        option2Value = row[Variants.option2Value],
                        sku = row[Variants.sku],
                        price = row[Variants.price],
                        cost = row[Variants.cost],
                        weight = row[Variants.weight]?.toDouble(),
                        inStock = row[Variants.inStock],
                        stockQuantity = row[Variants.stockQuantity]
                    )
                )
            }

        results
    }

    // =====================================================
    // TRANSACTION EXPORT DATA
    // =====================================================

    fun getTransactionsForExport(
        tenantId: Long,
        userId: Long?,
        startDate: String?,
        endDate: String?
    ): List<TransactionExportRow> = transaction {
        val results = mutableListOf<TransactionExportRow>()

        var query = Transactions.leftJoin(Users, { Transactions.userId }, { id })
            .selectAll()
            .where { Transactions.tenantId eq tenantId }

        userId?.let { uid ->
            query = query.andWhere { Transactions.userId eq uid }
        }

        startDate?.let { start ->
            try {
                val startInstant = LocalDate.parse(start).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                query = query.andWhere { Transactions.createdAt greaterEq startInstant }
            } catch (e: Exception) {
                logger.warn("Invalid start date format: $start")
            }
        }

        endDate?.let { end ->
            try {
                val endInstant = LocalDate.parse(end).plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                query = query.andWhere { Transactions.createdAt less endInstant }
            } catch (e: Exception) {
                logger.warn("Invalid end date format: $end")
            }
        }

        query.orderBy(Transactions.createdAt, SortOrder.DESC)
            .forEach { row ->
                val typeName = when (row[Transactions.type]) {
                    0 -> "Purchase"
                    1 -> "Add Funds"
                    2 -> "Promotional Credit"
                    3 -> "Credit Card Payment"
                    4 -> "Referral Credit"
                    5 -> "Order Refund"
                    6 -> "Multi Purchase"
                    else -> "Unknown"
                }

                results.add(
                    TransactionExportRow(
                        transactionId = row[Transactions.id].value,
                        userId = row[Transactions.userId].value,
                        customerName = "${row.getOrNull(Users.firstName) ?: ""} ${row.getOrNull(Users.lastName) ?: ""}".trim(),
                        type = typeName,
                        typeCode = row[Transactions.type],
                        amount = row[Transactions.amount],
                        description = row[Transactions.description],
                        referenceId = row[Transactions.referenceId],
                        balanceBefore = row[Transactions.balanceBefore],
                        balanceAfter = row[Transactions.balanceAfter],
                        createdAt = row[Transactions.createdAt].toString()
                    )
                )
            }

        results
    }

    // =====================================================
    // DESIGN EXPORT DATA
    // =====================================================

    fun getDesignsForExport(tenantId: Long, filters: ExportFilters?): List<DesignExportRow> = transaction {
        val results = mutableListOf<DesignExportRow>()

        var query = Designs.selectAll()
            .where { Designs.tenantId eq tenantId }

        filters?.status?.let { status ->
            query = query.andWhere { Designs.status eq status }
        }

        filters?.userId?.let { userId ->
            query = query.andWhere { Designs.userId eq userId }
        }

        query.orderBy(Designs.createdAt, SortOrder.DESC)
            .forEach { row ->
                val designId = row[Designs.id].value

                // Count usage in order products
                val usageCount = OrderProducts.selectAll()
                    .where { OrderProducts.designId eq designId }
                    .count().toInt()

                val designTypeName = when (row[Designs.designType]) {
                    1 -> "Image/DTF"
                    2 -> "Embroidery"
                    3 -> "Vector"
                    4 -> "Ornament"
                    else -> "Unknown"
                }

                results.add(
                    DesignExportRow(
                        designId = designId,
                        title = row[Designs.title],
                        designType = row[Designs.designType],
                        designTypeName = designTypeName,
                        designUrl = row[Designs.designUrl],
                        width = row[Designs.width]?.toDouble(),
                        height = row[Designs.height]?.toDouble(),
                        usageCount = usageCount,
                        createdAt = row[Designs.createdAt].toString(),
                        status = row[Designs.status]
                    )
                )
            }

        results
    }

    // =====================================================
    // EXPORT HISTORY
    // =====================================================

    fun createExportHistory(
        tenantId: Long,
        userId: Long,
        exportType: String,
        fileName: String,
        fileUrl: String?,
        recordCount: Int,
        status: Int,
        filters: String? = null,
        errorMessage: String? = null
    ): Long = transaction {
        ExportHistoryTable.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.exportType] = exportType
            it[this.fileName] = fileName
            it[this.fileUrl] = fileUrl
            it[this.recordCount] = recordCount
            it[this.status] = status
            it[this.filters] = filters
            it[this.errorMessage] = errorMessage
            if (status == ExportStatus.COMPLETED.code) {
                it[completedAt] = Instant.now()
            }
        }.value
    }

    fun getExportHistory(
        tenantId: Long,
        userId: Long?,
        page: Int,
        limit: Int
    ): Pair<List<ExportHistory>, Int> = transaction {
        var query = ExportHistoryTable.selectAll()
            .where { ExportHistoryTable.tenantId eq tenantId }

        userId?.let { uid ->
            query = query.andWhere { ExportHistoryTable.userId eq uid }
        }

        val total = query.count().toInt()

        val offset = ((page - 1) * limit).toLong()
        val exports = query
            .orderBy(ExportHistoryTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toExportHistory() }

        Pair(exports, total)
    }

    fun getExportById(tenantId: Long, exportId: Long): ExportHistory? = transaction {
        ExportHistoryTable.selectAll()
            .where { (ExportHistoryTable.id eq exportId) and (ExportHistoryTable.tenantId eq tenantId) }
            .singleOrNull()
            ?.toExportHistory()
    }

    fun updateExportStatus(
        exportId: Long,
        status: Int,
        fileUrl: String? = null,
        errorMessage: String? = null
    ): Boolean = transaction {
        ExportHistoryTable.update({ ExportHistoryTable.id eq exportId }) {
            it[this.status] = status
            fileUrl?.let { url -> it[this.fileUrl] = url }
            errorMessage?.let { msg -> it[this.errorMessage] = msg }
            if (status == ExportStatus.COMPLETED.code || status == ExportStatus.FAILED.code) {
                it[completedAt] = Instant.now()
            }
        } > 0
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toExportHistory(): ExportHistory = ExportHistory(
        id = this[ExportHistoryTable.id].value,
        tenantId = this[ExportHistoryTable.tenantId].value,
        userId = this[ExportHistoryTable.userId].value,
        exportType = this[ExportHistoryTable.exportType],
        fileName = this[ExportHistoryTable.fileName],
        fileUrl = this[ExportHistoryTable.fileUrl],
        recordCount = this[ExportHistoryTable.recordCount],
        status = this[ExportHistoryTable.status],
        errorMessage = this[ExportHistoryTable.errorMessage],
        filters = this[ExportHistoryTable.filters],
        createdAt = this[ExportHistoryTable.createdAt].toString(),
        completedAt = this[ExportHistoryTable.completedAt]?.toString()
    )

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun parseModifications(modificationDetail: String): String {
        return try {
            // Simple JSON parsing to extract modification names
            val regex = """"modification_name"\s*:\s*"([^"]+)"""".toRegex()
            regex.findAll(modificationDetail)
                .map { it.groupValues[1] }
                .joinToString(", ")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseShippingPaid(orderInfo: String): BigDecimal? {
        return try {
            // Parse shipping.api_price or shipping.label_price from JSON
            val apiPriceRegex = """"api_price"\s*:\s*([0-9.]+)""".toRegex()
            val labelPriceRegex = """"label_price"\s*:\s*([0-9.]+)""".toRegex()

            val apiMatch = apiPriceRegex.find(orderInfo)
            val labelMatch = labelPriceRegex.find(orderInfo)

            apiMatch?.groupValues?.get(1)?.toBigDecimalOrNull()
                ?: labelMatch?.groupValues?.get(1)?.toBigDecimalOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOrderNote(orderInfo: String): String? {
        return try {
            val regex = """"order_note"\s*:\s*"([^"]*?)"""".toRegex()
            regex.find(orderInfo)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQuantityFromProductDetail(productDetail: String): Int {
        return try {
            val regex = """"quantity"\s*:\s*"?(\d+)"?""".toRegex()
            regex.find(productDetail)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        } catch (e: Exception) {
            1
        }
    }
}

// =====================================================
// EXPORT HISTORY TABLE
// =====================================================

object ExportHistoryTable : LongIdTable("export_history") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val exportType = varchar("export_type", 50)
    val fileName = varchar("file_name", 255)
    val fileUrl = text("file_url").nullable()
    val recordCount = integer("record_count").default(0)
    val status = integer("status").default(0)
    val errorMessage = text("error_message").nullable()
    val filters = text("filters").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val completedAt = timestamp("completed_at").nullable()
}

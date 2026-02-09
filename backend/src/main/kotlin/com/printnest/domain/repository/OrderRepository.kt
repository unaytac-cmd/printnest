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

class OrderRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // ORDERS
    // =====================================================

    fun findAll(tenantId: Long, filters: OrderFiltersExtended): Pair<List<OrderFull>, Int> = transaction {
        var query = Orders.selectAll()
            .where { Orders.tenantId eq tenantId }

        // Apply filters
        filters.status?.let { status ->
            query = query.andWhere { Orders.orderStatus eq status }
        }

        filters.statuses?.let { statuses ->
            if (statuses.isNotEmpty()) {
                query = query.andWhere { Orders.orderStatus inList statuses }
            }
        }

        filters.mapStatus?.let { mapStatus ->
            query = query.andWhere { Orders.orderMapStatus eq mapStatus }
        }

        filters.storeId?.let { storeId ->
            query = query.andWhere { Orders.storeId eq storeId }
        }

        filters.shipstationStoreId?.let { ssStoreId ->
            query = query.andWhere { Orders.shipstationStoreId eq ssStoreId }
        }

        filters.userId?.let { userId ->
            query = query.andWhere { Orders.userId eq userId }
        }

        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere {
                    (Orders.customerName like "%$search%") or
                    (Orders.customerEmail like "%$search%") or
                    (Orders.externalOrderId like "%$search%") or
                    (Orders.intOrderId like "%$search%") or
                    (Orders.trackingNumber like "%$search%")
                }
            }
        }

        // Get total count
        val total = query.count().toInt()

        // Apply sorting
        val sortColumn = when (filters.sortBy) {
            "createdAt" -> Orders.createdAt
            "updatedAt" -> Orders.updatedAt
            "totalAmount" -> Orders.totalAmount
            "orderStatus" -> Orders.orderStatus
            else -> Orders.createdAt
        }

        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Apply pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val orders = query.map { it.toOrderFull() }

        Pair(orders, total)
    }

    fun findById(id: Long, tenantId: Long): OrderFull? = transaction {
        Orders.selectAll()
            .where { (Orders.id eq id) and (Orders.tenantId eq tenantId) }
            .singleOrNull()
            ?.toOrderFull()
    }

    fun findByIdWithProducts(id: Long, tenantId: Long): OrderFull? = transaction {
        val order = findById(id, tenantId) ?: return@transaction null

        val products = OrderProducts.selectAll()
            .where { (OrderProducts.orderId eq id) and (OrderProducts.tenantId eq tenantId) }
            .map { it.toOrderProductFull() }

        val history = OrderHistory.selectAll()
            .where { OrderHistory.orderId eq id }
            .orderBy(OrderHistory.createdAt, SortOrder.DESC)
            .map { it.toOrderHistoryItem() }

        order.copy(products = products, history = history)
    }

    fun findByIntOrderId(intOrderId: String, tenantId: Long): OrderFull? = transaction {
        Orders.selectAll()
            .where { (Orders.intOrderId eq intOrderId) and (Orders.tenantId eq tenantId) }
            .singleOrNull()
            ?.toOrderFull()
    }

    fun findByExternalOrderId(externalOrderId: String, tenantId: Long): OrderFull? = transaction {
        Orders.selectAll()
            .where { (Orders.externalOrderId eq externalOrderId) and (Orders.tenantId eq tenantId) }
            .singleOrNull()
            ?.toOrderFull()
    }

    /**
     * Find order by ID (without tenant check - for internal use)
     */
    fun findById(id: Long): OrderFull? = transaction {
        Orders.selectAll()
            .where { Orders.id eq id }
            .singleOrNull()
            ?.toOrderFull()
    }

    /**
     * Create order from Shopify integration
     */
    fun createFromShopify(
        tenantId: Long,
        storeId: Long,
        intOrderId: String,
        externalOrderId: String,
        orderInfo: String,
        orderDetail: String,
        customerEmail: String?,
        customerName: String?,
        shippingAddress: Address
    ): Long = transaction {
        // For Shopify stores, we use a default user ID or store owner
        val store = Stores.selectAll()
            .where { Stores.id eq storeId }
            .singleOrNull()

        val userId = store?.get(Stores.userId)?.value ?: 1L

        val shippingAddressJson = json.encodeToString(Address.serializer(), shippingAddress)

        Orders.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.storeId] = storeId
            it[this.intOrderId] = intOrderId
            it[this.externalOrderId] = externalOrderId
            it[orderType] = 0
            it[orderStatus] = 0 // NEW_ORDER
            it[orderMapStatus] = 0 // NOT_MAPPED
            it[this.orderInfo] = orderInfo
            it[this.orderDetail] = orderDetail
            it[this.customerEmail] = customerEmail
            it[this.customerName] = customerName
            it[this.shippingAddress] = shippingAddressJson
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }.value
    }

    /**
     * Create order product from Shopify/marketplace integration
     */
    fun createOrderProduct(
        orderId: Long,
        tenantId: Long,
        listingId: String,
        quantity: Int,
        productDetail: String,
        listingImageUrl: String?
    ): Long = transaction {
        OrderProducts.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[this.listingId] = listingId
            it[this.quantity] = quantity
            it[OrderProducts.productDetail] = productDetail
            it[this.listingImageUrl] = listingImageUrl
            it[status] = 0
            it[createdAt] = Instant.now()
        }.value
    }

    /**
     * Find order by internal order ID and store ID (for Walmart integration)
     */
    fun findByIntOrderIdAndStoreId(intOrderId: String, storeId: Long): OrderFull? = transaction {
        Orders.selectAll()
            .where { (Orders.intOrderId eq intOrderId) and (Orders.storeId eq storeId) }
            .singleOrNull()
            ?.toOrderFull()
    }

    /**
     * Insert a Walmart order
     */
    fun insertWalmartOrder(
        tenantId: Long,
        storeId: Long,
        intOrderId: String,
        purchaseOrderId: String,
        orderInfo: String,
        orderDetail: String,
        customerName: String,
        orderDate: String
    ): Long = transaction {
        // Get the user ID associated with the store
        val store = Stores.selectAll()
            .where { Stores.id eq storeId }
            .singleOrNull()

        val userId = store?.get(Stores.userId)?.value ?: throw IllegalArgumentException("Store not found: $storeId")

        Orders.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.storeId] = storeId
            it[this.intOrderId] = intOrderId
            it[externalOrderId] = purchaseOrderId
            it[orderType] = 0
            it[orderStatus] = 0 // NEW_ORDER
            it[orderMapStatus] = 0 // NOT_MAPPED
            it[this.orderInfo] = orderInfo
            it[this.orderDetail] = orderDetail
            it[this.customerName] = customerName
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }.value
    }

    /**
     * Update orders to completed status based on Walmart delivered status
     */
    fun updateOrdersToCompleted(intOrderIds: List<String>, storeId: Long): Int = transaction {
        Orders.update(
            where = {
                (Orders.intOrderId inList intOrderIds) and
                (Orders.storeId eq storeId) and
                (Orders.orderStatus inList listOf(0, 4)) // NEW_ORDER or SHIPPED
            }
        ) {
            it[orderStatus] = -3 // COMPLETED/DELIVERED
            it[updatedAt] = Instant.now()
        }
    }

    fun create(tenantId: Long, userId: Long, request: CreateOrderRequest): OrderFull = transaction {
        val intOrderId = "MAN_${System.currentTimeMillis()}"

        val orderInfoJson = json.encodeToString(OrderInfoFull.serializer(), OrderInfoFull(
            toAddress = request.shippingAddress,
            orderNote = request.orderNote
        ))

        val shippingAddressJson = request.shippingAddress?.let {
            json.encodeToString(Address.serializer(), it)
        } ?: "{}"

        val billingAddressJson = request.billingAddress?.let {
            json.encodeToString(Address.serializer(), it)
        } ?: "{}"

        val id = Orders.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.intOrderId] = intOrderId
            it[storeId] = request.storeId
            it[shipstationStoreId] = request.shipstationStoreId
            it[externalOrderId] = request.externalOrderId
            it[orderStatus] = OrderStatus.NEW_ORDER.code
            it[orderInfo] = orderInfoJson
            it[customerEmail] = request.customerEmail
            it[customerName] = request.customerName
            it[shippingAddress] = shippingAddressJson
            it[billingAddress] = billingAddressJson
        }

        // Create order products
        request.products.forEach { product ->
            createOrderProduct(id.value, tenantId, product)
        }

        // Create history entry
        createHistoryEntry(tenantId, id.value, userId, null, OrderStatus.NEW_ORDER.code, "created")

        findByIdWithProducts(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, block: Orders.(UpdateBuilder<Int>) -> Unit): Boolean = transaction {
        Orders.update(
            where = { (Orders.id eq id) and (Orders.tenantId eq tenantId) }
        ) {
            block(it)
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateStatus(id: Long, tenantId: Long, userId: Long?, newStatus: Int, notes: String? = null): Boolean = transaction {
        val order = findById(id, tenantId) ?: return@transaction false
        val previousStatus = order.orderStatus

        val updated = Orders.update(
            where = { (Orders.id eq id) and (Orders.tenantId eq tenantId) }
        ) {
            it[orderStatus] = newStatus
            it[updatedAt] = Instant.now()
        } > 0

        if (updated) {
            createHistoryEntry(tenantId, id, userId, previousStatus, newStatus, "status_changed", notes)
        }

        updated
    }

    fun updateOrderInfo(id: Long, tenantId: Long, orderInfo: OrderInfoFull): Boolean = transaction {
        Orders.update(
            where = { (Orders.id eq id) and (Orders.tenantId eq tenantId) }
        ) {
            it[Orders.orderInfo] = json.encodeToString(OrderInfoFull.serializer(), orderInfo)
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updatePriceDetail(id: Long, tenantId: Long, priceDetail: List<PriceDetailItem>, summary: OrderPriceSummary): Boolean = transaction {
        Orders.update(
            where = { (Orders.id eq id) and (Orders.tenantId eq tenantId) }
        ) {
            it[Orders.priceDetail] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(PriceDetailItem.serializer()), priceDetail)
            it[totalAmount] = summary.totalPrice
            it[shippingAmount] = summary.shippingPrice
            it[urgentAmount] = summary.urgentPrice
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun delete(id: Long, tenantId: Long, userId: Long?): Boolean = transaction {
        updateStatus(id, tenantId, userId, OrderStatus.DELETED.code, "Deleted by user")
    }

    // =====================================================
    // ORDER PRODUCTS
    // =====================================================

    fun findOrderProducts(orderId: Long, tenantId: Long): List<OrderProductFull> = transaction {
        OrderProducts.selectAll()
            .where { (OrderProducts.orderId eq orderId) and (OrderProducts.tenantId eq tenantId) }
            .map { it.toOrderProductFull() }
    }

    fun findOrderProductById(id: Long, tenantId: Long): OrderProductFull? = transaction {
        OrderProducts.selectAll()
            .where { (OrderProducts.id eq id) and (OrderProducts.tenantId eq tenantId) }
            .singleOrNull()
            ?.toOrderProductFull()
    }

    fun createOrderProduct(orderId: Long, tenantId: Long, request: CreateOrderProductRequest): OrderProductFull = transaction {
        val modificationDetailJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ModificationDetailItem.serializer()),
            request.modificationDetail
        )

        val id = OrderProducts.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[productId] = request.productId
            it[variantId] = request.variantId
            it[listingId] = request.listingId
            it[quantity] = request.quantity
            it[listingImageUrl] = request.listingImageUrl
            it[modificationDetail] = modificationDetailJson
        }

        findOrderProductById(id.value, tenantId)!!
    }

    fun updateOrderProduct(id: Long, tenantId: Long, request: UpdateOrderProductRequest): OrderProductFull? = transaction {
        val modificationDetailJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ModificationDetailItem.serializer()),
            request.modificationDetail
        )

        val updated = OrderProducts.update(
            where = { (OrderProducts.id eq id) and (OrderProducts.tenantId eq tenantId) }
        ) {
            request.productId?.let { pid -> it[productId] = pid }
            request.variantId?.let { vid -> it[variantId] = vid }
            it[quantity] = request.quantity
            it[modificationDetail] = modificationDetailJson
            it[stitchCount] = request.stitchCount
        }

        if (updated > 0) findOrderProductById(id, tenantId) else null
    }

    fun updateOrderProductDetail(id: Long, tenantId: Long, productDetail: ProductDetailFull, priceBreakdown: PriceBreakdownItem): Boolean = transaction {
        OrderProducts.update(
            where = { (OrderProducts.id eq id) and (OrderProducts.tenantId eq tenantId) }
        ) {
            it[OrderProducts.productDetail] = json.encodeToString(ProductDetailFull.serializer(), productDetail)
            it[OrderProducts.priceBreakdown] = json.encodeToString(PriceBreakdownItem.serializer(), priceBreakdown)
            it[unitPrice] = priceBreakdown.unitTotal
        } > 0
    }

    fun deleteOrderProduct(id: Long, tenantId: Long): Boolean = transaction {
        OrderProducts.deleteWhere {
            (OrderProducts.id eq id) and (OrderProducts.tenantId eq tenantId)
        } > 0
    }

    // =====================================================
    // ORDER HISTORY
    // =====================================================

    fun findOrderHistory(orderId: Long): List<OrderHistoryItem> = transaction {
        OrderHistory.selectAll()
            .where { OrderHistory.orderId eq orderId }
            .orderBy(OrderHistory.createdAt, SortOrder.DESC)
            .map { it.toOrderHistoryItem() }
    }

    fun createHistoryEntry(
        tenantId: Long,
        orderId: Long,
        userId: Long?,
        previousStatus: Int?,
        newStatus: Int,
        action: String,
        notes: String? = null
    ): Long = transaction {
        OrderHistory.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[this.userId] = userId
            it[this.previousStatus] = previousStatus
            it[this.newStatus] = newStatus
            it[this.action] = action
            it[this.notes] = notes
        }.value
    }

    // =====================================================
    // PAYMENTS
    // =====================================================

    fun findPaymentById(id: Long, tenantId: Long): Payment? = transaction {
        Payments.selectAll()
            .where { (Payments.id eq id) and (Payments.tenantId eq tenantId) }
            .singleOrNull()
            ?.toPayment()
    }

    fun findPaymentByOrderId(orderId: Long, tenantId: Long): Payment? = transaction {
        Payments.selectAll()
            .where { (Payments.orderId eq orderId) and (Payments.tenantId eq tenantId) }
            .singleOrNull()
            ?.toPayment()
    }

    fun createPayment(
        tenantId: Long,
        userId: Long,
        orderId: Long,
        paymentMethod: String,
        amount: java.math.BigDecimal,
        stripePaymentIntentId: String? = null
    ): Payment = transaction {
        val id = Payments.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.orderId] = orderId
            it[this.paymentMethod] = paymentMethod
            it[this.amount] = amount
            // Store in stripeSessionId for backward compatibility
            it[this.stripeSessionId] = stripePaymentIntentId
            it[status] = if (paymentMethod == "balance") "completed" else "pending"
            if (paymentMethod == "balance") {
                it[completedAt] = Instant.now()
            }
        }

        findPaymentById(id.value, tenantId)!!
    }

    fun completePayment(id: Long, tenantId: Long, stripePaymentIntent: String? = null): Boolean = transaction {
        Payments.update(
            where = { (Payments.id eq id) and (Payments.tenantId eq tenantId) }
        ) {
            it[status] = "completed"
            it[completedAt] = Instant.now()
            stripePaymentIntent?.let { intent -> it[Payments.stripePaymentIntent] = intent }
        } > 0
    }

    fun findPaymentByStripeId(tenantId: Long, stripeId: String): Payment? = transaction {
        Payments.selectAll()
            .where {
                (Payments.tenantId eq tenantId) and
                ((Payments.stripeSessionId eq stripeId) or (Payments.stripePaymentIntent eq stripeId))
            }
            .singleOrNull()
            ?.toPayment()
    }

    fun updatePaymentStatus(id: Long, status: String, stripePaymentIntent: String? = null): Boolean = transaction {
        Payments.update(
            where = { Payments.id eq id }
        ) {
            it[Payments.status] = status
            if (status == "completed") {
                it[completedAt] = Instant.now()
            }
            stripePaymentIntent?.let { intent -> it[Payments.stripePaymentIntent] = intent }
        } > 0
    }

    fun updateOrderPaymentStatus(orderId: Long, tenantId: Long, newStatus: Int, stripePaymentIntent: String? = null): Boolean = transaction {
        Orders.update(
            where = { (Orders.id eq orderId) and (Orders.tenantId eq tenantId) }
        ) {
            it[orderStatus] = newStatus
            it[paymentMethod] = "stripe"
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // TRANSACTIONS
    // =====================================================

    fun createTransaction(
        tenantId: Long,
        userId: Long,
        type: Int,
        amount: java.math.BigDecimal,
        description: String?,
        referenceId: String?,
        balanceBefore: java.math.BigDecimal,
        balanceAfter: java.math.BigDecimal
    ): Long = transaction {
        Transactions.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.type] = type
            it[this.amount] = amount
            it[this.description] = description
            it[this.referenceId] = referenceId
            it[this.balanceBefore] = balanceBefore
            it[this.balanceAfter] = balanceAfter
        }.value
    }

    // =====================================================
    // SHIPPING LABELS
    // =====================================================

    fun findShippingLabelByOrderId(orderId: Long, tenantId: Long): ShippingLabel? = transaction {
        ShippingLabels.selectAll()
            .where { (ShippingLabels.orderId eq orderId) and (ShippingLabels.tenantId eq tenantId) and (ShippingLabels.voidedAt.isNull()) }
            .singleOrNull()
            ?.toShippingLabel()
    }

    fun createShippingLabel(
        tenantId: Long,
        orderId: Long,
        carrier: String?,
        service: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        labelUrl: String?,
        rateId: String?,
        shipmentId: String?,
        cost: java.math.BigDecimal?
    ): ShippingLabel = transaction {
        val id = ShippingLabels.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[this.carrier] = carrier
            it[this.service] = service
            it[this.trackingNumber] = trackingNumber
            it[this.trackingUrl] = trackingUrl
            it[this.labelUrl] = labelUrl
            it[this.rateId] = rateId
            it[this.shipmentId] = shipmentId
            it[this.cost] = cost
        }

        ShippingLabels.selectAll()
            .where { ShippingLabels.id eq id }
            .single()
            .toShippingLabel()
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toOrderFull(): OrderFull {
        val orderInfoJson = this[Orders.orderInfo]
        val orderInfo = try {
            if (orderInfoJson.isNotEmpty() && orderInfoJson != "{}") {
                json.decodeFromString<OrderInfoFull>(orderInfoJson)
            } else null
        } catch (e: Exception) { null }

        val priceDetailJson = this[Orders.priceDetail]
        val priceDetail = try {
            if (priceDetailJson.isNotEmpty() && priceDetailJson != "[]") {
                json.decodeFromString<List<PriceDetailItem>>(priceDetailJson)
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val shippingAddressJson = this[Orders.shippingAddress]
        val shippingAddress = try {
            if (shippingAddressJson.isNotEmpty() && shippingAddressJson != "{}") {
                json.decodeFromString<Address>(shippingAddressJson)
            } else null
        } catch (e: Exception) { null }

        val billingAddressJson = this[Orders.billingAddress]
        val billingAddress = try {
            if (billingAddressJson.isNotEmpty() && billingAddressJson != "{}") {
                json.decodeFromString<Address>(billingAddressJson)
            } else null
        } catch (e: Exception) { null }

        return OrderFull(
            id = this[Orders.id].value,
            tenantId = this[Orders.tenantId].value,
            userId = this[Orders.userId].value,
            storeId = this[Orders.storeId]?.value,
            intOrderId = this[Orders.intOrderId],
            externalOrderId = this[Orders.externalOrderId],
            orderType = this[Orders.orderType],
            orderStatus = this[Orders.orderStatus],
            orderMapStatus = this[Orders.orderMapStatus],
            orderInfo = orderInfo,
            priceDetail = priceDetail,
            totalAmount = this[Orders.totalAmount],
            shippingAmount = this[Orders.shippingAmount],
            taxAmount = this[Orders.taxAmount],
            urgentAmount = this[Orders.urgentAmount],
            giftNote = this[Orders.giftNote],
            customerEmail = this[Orders.customerEmail],
            customerName = this[Orders.customerName],
            shippingAddress = shippingAddress,
            billingAddress = billingAddress,
            trackingNumber = this[Orders.trackingNumber],
            trackingUrl = this[Orders.trackingUrl],
            paymentMethod = this[Orders.paymentMethod],
            shipstationStoreId = this[Orders.shipstationStoreId]?.value,
            shipstationOrderId = this[Orders.shipstationOrderId],
            shippedAt = this[Orders.shippedAt]?.toString(),
            createdAt = this[Orders.createdAt].toString(),
            updatedAt = this[Orders.updatedAt].toString()
        )
    }

    private fun ResultRow.toOrderProductFull(): OrderProductFull {
        val productDetailJson = this[OrderProducts.productDetail]
        val productDetail = try {
            if (productDetailJson.isNotEmpty() && productDetailJson != "{}") {
                json.decodeFromString<ProductDetailFull>(productDetailJson)
            } else null
        } catch (e: Exception) { null }

        val modificationDetailJson = this[OrderProducts.modificationDetail]
        val modificationDetail = try {
            if (modificationDetailJson.isNotEmpty() && modificationDetailJson != "[]") {
                json.decodeFromString<List<ModificationDetailItem>>(modificationDetailJson)
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val priceBreakdownJson = this[OrderProducts.priceBreakdown]
        val priceBreakdown = try {
            if (priceBreakdownJson.isNotEmpty() && priceBreakdownJson != "{}") {
                json.decodeFromString<PriceBreakdownItem>(priceBreakdownJson)
            } else null
        } catch (e: Exception) { null }

        return OrderProductFull(
            id = this[OrderProducts.id].value,
            tenantId = this[OrderProducts.tenantId].value,
            orderId = this[OrderProducts.orderId].value,
            productId = this[OrderProducts.productId]?.value,
            variantId = this[OrderProducts.variantId]?.value,
            listingId = this[OrderProducts.listingId],
            quantity = this[OrderProducts.quantity],
            unitPrice = this[OrderProducts.unitPrice],
            productDetail = productDetail,
            modificationDetail = modificationDetail,
            priceBreakdown = priceBreakdown,
            designId = this[OrderProducts.designId]?.value,
            mappingId = this[OrderProducts.mappingId],
            listingImageUrl = this[OrderProducts.listingImageUrl],
            stitchCount = this[OrderProducts.stitchCount],
            status = this[OrderProducts.status],
            createdAt = this[OrderProducts.createdAt].toString()
        )
    }

    private fun ResultRow.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
        id = this[OrderHistory.id].value,
        orderId = this[OrderHistory.orderId].value,
        userId = this[OrderHistory.userId]?.value,
        previousStatus = this[OrderHistory.previousStatus],
        newStatus = this[OrderHistory.newStatus],
        action = this[OrderHistory.action],
        notes = this[OrderHistory.notes],
        createdAt = this[OrderHistory.createdAt].toString()
    )

    private fun ResultRow.toPayment(): Payment = Payment(
        id = this[Payments.id].value,
        tenantId = this[Payments.tenantId].value,
        userId = this[Payments.userId].value,
        orderId = this[Payments.orderId]?.value,
        paymentMethod = this[Payments.paymentMethod],
        amount = this[Payments.amount],
        status = this[Payments.status],
        stripeSessionId = this[Payments.stripeSessionId],
        stripePaymentIntent = this[Payments.stripePaymentIntent],
        createdAt = this[Payments.createdAt].toString(),
        completedAt = this[Payments.completedAt]?.toString()
    )

    private fun ResultRow.toShippingLabel(): ShippingLabel = ShippingLabel(
        id = this[ShippingLabels.id].value,
        tenantId = this[ShippingLabels.tenantId].value,
        orderId = this[ShippingLabels.orderId].value,
        carrier = this[ShippingLabels.carrier],
        service = this[ShippingLabels.service],
        trackingNumber = this[ShippingLabels.trackingNumber],
        trackingUrl = this[ShippingLabels.trackingUrl],
        labelUrl = this[ShippingLabels.labelUrl],
        labelFormat = this[ShippingLabels.labelFormat],
        rateId = this[ShippingLabels.rateId],
        shipmentId = this[ShippingLabels.shipmentId],
        cost = this[ShippingLabels.cost],
        createdAt = this[ShippingLabels.createdAt].toString(),
        voidedAt = this[ShippingLabels.voidedAt]?.toString()
    )

    // =====================================================
    // ETSY INTEGRATION METHODS
    // =====================================================

    /**
     * Insert an Etsy order
     */
    fun insertEtsyOrder(
        tenantId: Long,
        userId: Long,
        externalOrderId: String,
        orderInfo: String,
        orderDetail: String,
        customerName: String?,
        customerEmail: String?,
        shippingAddress: String,
        totalAmount: java.math.BigDecimal,
        shippingAmount: java.math.BigDecimal,
        taxAmount: java.math.BigDecimal,
        orderStatus: Int,
        isGift: Boolean,
        giftNote: String?
    ): Long = transaction {
        val intOrderId = "ETSY_${System.currentTimeMillis()}"

        Orders.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.intOrderId] = intOrderId
            it[this.externalOrderId] = externalOrderId
            it[orderType] = 0
            it[this.orderStatus] = orderStatus
            it[orderMapStatus] = 0 // NOT_MAPPED
            it[this.orderInfo] = orderInfo
            it[this.orderDetail] = orderDetail
            it[this.customerName] = customerName
            it[this.customerEmail] = customerEmail
            it[this.shippingAddress] = shippingAddress
            it[this.totalAmount] = totalAmount
            it[this.shippingAmount] = shippingAmount
            it[this.taxAmount] = taxAmount
            it[this.giftNote] = if (isGift) giftNote else null
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }.value
    }

    /**
     * Update order tracking information
     */
    fun updateTracking(orderId: Long, trackingNumber: String, trackingUrl: String?): Boolean = transaction {
        Orders.update({ Orders.id eq orderId }) {
            it[this.trackingNumber] = trackingNumber
            trackingUrl?.let { url -> it[this.trackingUrl] = url }
            it[orderStatus] = 4 // SHIPPED
            it[shippedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // AMAZON INTEGRATION METHODS
    // =====================================================

    /**
     * Find order by internal order ID (Amazon Order ID)
     */
    fun findByIntOrderId(tenantId: Long, intOrderId: String): OrderFull? = transaction {
        Orders.selectAll()
            .where { (Orders.tenantId eq tenantId) and (Orders.intOrderId eq intOrderId) }
            .singleOrNull()
            ?.toOrderFull()
    }

    /**
     * Find order by ID with tenant context (alias for findById(id, tenantId))
     * NOTE: This is a duplicate method - use findById(id, tenantId) instead
     */
    // fun findByIdWithTenant(tenantId: Long, orderId: Long): OrderFull? = findById(orderId, tenantId)

    /**
     * Insert an Amazon order
     */
    fun insert(orderData: com.printnest.integrations.amazon.PrintNestOrderData): Long = transaction {
        // Get the user ID associated with the store
        val store = Stores.selectAll()
            .where { Stores.id eq orderData.storeId }
            .singleOrNull()

        val userId = store?.get(Stores.userId)?.value
            ?: throw IllegalArgumentException("Store not found: ${orderData.storeId}")

        Orders.insertAndGetId {
            it[tenantId] = orderData.tenantId
            it[this.userId] = userId
            it[storeId] = orderData.storeId
            it[intOrderId] = orderData.intOrderId
            it[externalOrderId] = orderData.externalOrderId
            it[orderType] = orderData.orderType
            it[orderStatus] = orderData.orderStatus
            it[orderMapStatus] = orderData.orderMapStatus
            it[orderDetail] = orderData.orderDetail
            it[orderInfo] = orderData.orderInfo
            it[customerEmail] = orderData.customerEmail
            it[customerName] = orderData.customerName
            it[shippingAddress] = orderData.shippingAddress
            it[totalAmount] = orderData.totalAmount
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }.value
    }

    /**
     * Insert order product for Amazon order
     */
    fun insertOrderProduct(
        tenantId: Long,
        orderId: Long,
        listingId: String?,
        quantity: Int,
        unitPrice: java.math.BigDecimal,
        productDetail: String,
        listingImageUrl: String?
    ): Long = transaction {
        OrderProducts.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[this.listingId] = listingId
            it[this.quantity] = quantity
            it[this.unitPrice] = unitPrice
            it[this.productDetail] = productDetail
            it[this.listingImageUrl] = listingImageUrl
        }.value
    }

    /**
     * Update tracking information with carrier
     */
    fun updateTracking(
        tenantId: Long,
        orderId: Long,
        trackingNumber: String,
        trackingUrl: String?,
        carrier: String?
    ): Boolean = transaction {
        Orders.update({ (Orders.id eq orderId) and (Orders.tenantId eq tenantId) }) {
            it[this.trackingNumber] = trackingNumber
            trackingUrl?.let { url -> it[this.trackingUrl] = url }
            it[shippedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }

    /**
     * Update order status
     */
    fun updateStatus(tenantId: Long, orderId: Long, newStatus: Int): Boolean = transaction {
        Orders.update({ (Orders.id eq orderId) and (Orders.tenantId eq tenantId) }) {
            it[orderStatus] = newStatus
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // LABEL SERVICE METHODS
    // =====================================================

    /**
     * Get store marketplace ID for an order
     */
    fun getStoreMarketplaceId(storeId: Long, tenantId: Long): Int? = transaction {
        Stores.selectAll()
            .where { (Stores.id eq storeId) and (Stores.tenantId eq tenantId) }
            .singleOrNull()
            ?.get(Stores.marketplaceId)?.value?.toInt()
    }

    /**
     * Update a specific flag in order_info JSON
     * Uses JSONB path update to set a nested value
     *
     * @param orderId Order ID
     * @param tenantId Tenant ID
     * @param path JSON path (e.g., "shipping.sent_to_marketplace")
     * @param value Boolean value to set
     */
    fun updateOrderInfoFlag(orderId: Long, tenantId: Long, path: String, value: Boolean): Boolean = transaction {
        // Build the JSONB path from the dot-separated path
        val pathParts = path.split(".")
        val jsonPath = pathParts.joinToString(",") { "'$it'" }

        // Use raw SQL for JSONB update with path
        val sql = """
            UPDATE orders
            SET order_info = jsonb_set(
                COALESCE(order_info, '{}'::jsonb),
                ARRAY[$jsonPath],
                '$value'::jsonb,
                true
            ),
            updated_at = NOW()
            WHERE id = $orderId AND tenant_id = $tenantId
        """.trimIndent()

        exec(sql)
        true
    }

    // NOTE: findOrderProducts is defined earlier in this file at line 361
    // Use findOrderProducts(orderId, tenantId) instead
}

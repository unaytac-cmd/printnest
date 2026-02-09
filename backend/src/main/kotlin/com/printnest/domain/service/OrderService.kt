package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.CategoryRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.ProductRepository
import com.printnest.domain.repository.ProfileRepository
import com.printnest.domain.tables.Orders
import com.printnest.domain.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode

class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val profileRepository: ProfileRepository
) {

    // =====================================================
    // ORDER LISTING
    // =====================================================

    fun getOrders(tenantId: Long, filters: OrderFiltersExtended): OrderListResponse {
        val (orders, total) = orderRepository.findAll(tenantId, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return OrderListResponse(
            orders = orders,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getOrder(id: Long, tenantId: Long, withProducts: Boolean = true): OrderFull? {
        return if (withProducts) {
            orderRepository.findByIdWithProducts(id, tenantId)
        } else {
            orderRepository.findById(id, tenantId)
        }
    }

    fun getOrderHistory(orderId: Long): List<OrderHistoryItem> {
        return orderRepository.findOrderHistory(orderId)
    }

    // =====================================================
    // STEP 1 - ORDER CREATION
    // =====================================================

    fun createOrder(tenantId: Long, userId: Long, request: CreateOrderRequest): Result<OrderFull> {
        // Validate products if provided
        request.products.forEach { product ->
            if (product.variantId != null) {
                val variant = productRepository.findVariantById(product.variantId, tenantId)
                if (variant == null) {
                    return Result.failure(IllegalArgumentException("Variant ${product.variantId} not found"))
                }
            }
        }

        val order = orderRepository.create(tenantId, userId, request)

        // Trigger mapping if applicable
        // TODO: Implement mapping service call

        return Result.success(order)
    }

    // =====================================================
    // STEP 2 - ORDER EDITING
    // =====================================================

    fun updateOrderStep2(id: Long, tenantId: Long, userId: Long, request: UpdateOrderStep2Request): Result<OrderFull> {
        val order = orderRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        // Check if order is editable
        val status = OrderStatus.fromCode(order.orderStatus)
        if (status !in OrderStatus.EDITABLE_STATUSES) {
            return Result.failure(IllegalStateException("Order cannot be edited in current status"))
        }

        // If order was PENDING or URGENT, change to EDITING
        if (status == OrderStatus.PENDING || status == OrderStatus.URGENT) {
            orderRepository.updateStatus(id, tenantId, userId, OrderStatus.EDITING.code, "Editing order")
        }

        // Update each product
        request.products.forEach { productUpdate ->
            updateOrderProduct(productUpdate, tenantId)
        }

        // Update address if provided
        request.shippingAddress?.let { address ->
            val currentInfo = order.orderInfo ?: OrderInfoFull()
            val updatedInfo = currentInfo.copy(
                toAddress = address,
                orderNote = request.orderNote ?: currentInfo.orderNote,
                giftNote = request.giftNote ?: currentInfo.giftNote
            )
            orderRepository.updateOrderInfo(id, tenantId, updatedInfo)
        }

        return Result.success(orderRepository.findByIdWithProducts(id, tenantId)!!)
    }

    private fun updateOrderProduct(request: UpdateOrderProductRequest, tenantId: Long) {
        // Get variant details if variantId is provided
        val variant = request.variantId?.let { productRepository.findVariantById(it, tenantId) }

        // Get product details
        val product = request.productId?.let { productRepository.findById(it, tenantId) }

        // Get category for modifications
        val category = request.categoryId?.let { categoryRepository.findById(it, tenantId) }

        // Build product detail
        val productDetail = ProductDetailFull(
            productId = request.productId,
            product = product?.title,
            productCategoryId = request.categoryId,
            productCategories = category?.name,
            option1Id = request.option1Id,
            option2Id = request.option2Id,
            quantity = request.quantity,
            variants = variant?.let {
                VariantDetailItem(
                    variantId = it.id,
                    width1 = it.width1,
                    width2 = it.width2,
                    price = it.price,
                    status = 1
                )
            },
            modificationDetail = request.modificationDetail
        )

        // Calculate price breakdown (preliminary)
        val priceBreakdown = calculateProductPrice(
            variant?.price ?: BigDecimal.ZERO,
            request.modificationDetail,
            request.stitchCount,
            request.quantity,
            null // No price profile at this stage
        )

        orderRepository.updateOrderProduct(request.orderProductId, tenantId, request)
        orderRepository.updateOrderProductDetail(request.orderProductId, tenantId, productDetail, priceBreakdown)
    }

    // =====================================================
    // STEP 3 - PRICE CALCULATION & PAYMENT
    // =====================================================

    fun calculateStep3Price(id: Long, tenantId: Long, userId: Long): Result<Step3PriceResponse> {
        val order = orderRepository.findByIdWithProducts(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        // Get user's price profile
        val priceProfile = getUserPriceProfile(userId, tenantId)

        // Calculate price for each product
        val priceDetails = mutableListOf<PriceDetailItem>()
        var subtotal = BigDecimal.ZERO

        order.products.forEach { orderProduct ->
            val variant = orderProduct.variantId?.let { productRepository.findVariantById(it, tenantId) }
            val basePrice = variant?.price ?: orderProduct.unitPrice

            val priceDetail = calculatePriceDetail(
                orderProductId = orderProduct.id,
                basePrice = basePrice,
                modifications = orderProduct.modificationDetail,
                stitchCount = orderProduct.stitchCount,
                quantity = orderProduct.quantity,
                priceProfile = priceProfile,
                variantId = orderProduct.variantId
            )

            priceDetails.add(priceDetail)
            subtotal += priceDetail.lineTotal
        }

        // Calculate shipping options
        val shippingOptions = calculateShippingOptions(order, tenantId, userId)

        // Get selected shipping or default
        val selectedShipping = order.orderInfo?.shipping ?: shippingOptions.firstOrNull()?.let {
            ShippingSelection(
                rateId = it.rateId,
                shipmentId = it.shipmentId,
                methodName = it.methodName,
                methodPrice = it.methodPrice,
                shippingId = it.shippingId,
                isInternational = it.isInternational
            )
        }

        // Calculate gift note price
        val giftNotePrice = if (order.giftNote != null || order.orderInfo?.giftNote != null) {
            priceProfile?.giftNotePrice ?: BigDecimal.ZERO
        } else {
            BigDecimal.ZERO
        }

        // Build summary
        val summary = OrderPriceSummary(
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP),
            shippingPrice = selectedShipping?.methodPrice ?: BigDecimal.ZERO,
            giftNotePrice = giftNotePrice.setScale(2, RoundingMode.HALF_UP),
            urgentPrice = BigDecimal.ZERO,
            totalPrice = (subtotal + (selectedShipping?.methodPrice ?: BigDecimal.ZERO) + giftNotePrice)
                .setScale(2, RoundingMode.HALF_UP)
        )

        // Get user balance
        val userBalance = getUserBalance(userId, tenantId)

        // Update order with price details
        orderRepository.updatePriceDetail(id, tenantId, priceDetails, summary)

        // Update order info with shipping options
        val updatedOrderInfo = (order.orderInfo ?: OrderInfoFull()).copy(
            shippingOptions = shippingOptions,
            shipping = selectedShipping
        )
        orderRepository.updateOrderInfo(id, tenantId, updatedOrderInfo)

        return Result.success(Step3PriceResponse(
            orderId = id,
            products = priceDetails,
            shippingOptions = shippingOptions,
            selectedShipping = selectedShipping,
            summary = summary,
            userBalance = userBalance
        ))
    }

    fun selectShipping(id: Long, tenantId: Long, request: SelectShippingRequest): Result<OrderPriceSummary> {
        val order = orderRepository.findByIdWithProducts(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val orderInfo = order.orderInfo ?: return Result.failure(IllegalStateException("Order info not found"))

        // Find the selected shipping option
        val selectedOption = when {
            request.shippingOptionIndex != null && request.shippingOptionIndex < orderInfo.shippingOptions.size -> {
                orderInfo.shippingOptions[request.shippingOptionIndex]
            }
            request.rateId != null -> {
                orderInfo.shippingOptions.find { it.rateId == request.rateId }
            }
            request.shippingMethodId != null -> {
                orderInfo.shippingOptions.find { it.shippingId == request.shippingMethodId }
            }
            else -> null
        } ?: return Result.failure(IllegalArgumentException("Shipping option not found"))

        // Update shipping selection
        val updatedInfo = orderInfo.copy(
            shipping = ShippingSelection(
                rateId = selectedOption.rateId,
                shipmentId = selectedOption.shipmentId,
                methodName = selectedOption.methodName,
                methodPrice = selectedOption.methodPrice,
                shippingId = selectedOption.shippingId,
                isInternational = selectedOption.isInternational
            )
        )
        orderRepository.updateOrderInfo(id, tenantId, updatedInfo)

        // Recalculate total
        val subtotal = order.products.sumOf { it.priceBreakdown?.lineTotal ?: BigDecimal.ZERO }
        val giftNotePrice = if (order.giftNote != null) {
            getUserPriceProfile(order.userId, tenantId)?.giftNotePrice ?: BigDecimal.ZERO
        } else BigDecimal.ZERO

        val summary = OrderPriceSummary(
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP),
            shippingPrice = selectedOption.methodPrice,
            giftNotePrice = giftNotePrice,
            urgentPrice = BigDecimal.ZERO,
            totalPrice = (subtotal + selectedOption.methodPrice + giftNotePrice).setScale(2, RoundingMode.HALF_UP)
        )

        orderRepository.updatePriceDetail(id, tenantId, order.priceDetail, summary)

        return Result.success(summary)
    }

    fun processPayment(id: Long, tenantId: Long, userId: Long, request: PaymentRequest): Result<PaymentResponse> {
        val order = orderRepository.findByIdWithProducts(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        // Calculate final total
        var total = order.totalAmount

        // Add urgent fee if requested
        val urgentPrice = if (request.isUrgent) {
            // TODO: Get urgent price from settings
            BigDecimal("10.00")
        } else BigDecimal.ZERO

        total += urgentPrice

        return when (request.paymentMethod) {
            "balance" -> processBalancePayment(id, tenantId, userId, total, request.isUrgent)
            "stripe" -> processStripePayment(id, tenantId, userId, total, request.isUrgent)
            else -> Result.failure(IllegalArgumentException("Invalid payment method"))
        }
    }

    private fun processBalancePayment(
        orderId: Long,
        tenantId: Long,
        userId: Long,
        amount: BigDecimal,
        isUrgent: Boolean
    ): Result<PaymentResponse> {
        val userBalance = getUserBalance(userId, tenantId)

        if (userBalance < amount) {
            return Result.failure(IllegalStateException("Insufficient balance. Required: $amount, Available: $userBalance"))
        }

        // Deduct from balance
        val newBalance = userBalance - amount
        updateUserBalance(userId, tenantId, newBalance)

        // Create transactions
        // 1. CC_PAYMENT (positive - recording the payment)
        orderRepository.createTransaction(
            tenantId = tenantId,
            userId = userId,
            type = TransactionType.CC_PAYMENT.code,
            amount = amount,
            description = "Order payment #$orderId",
            referenceId = orderId.toString(),
            balanceBefore = userBalance,
            balanceAfter = newBalance
        )

        // 2. PURCHASE (negative - the expense)
        orderRepository.createTransaction(
            tenantId = tenantId,
            userId = userId,
            type = TransactionType.PURCHASE.code,
            amount = amount.negate(),
            description = "Order #$orderId",
            referenceId = orderId.toString(),
            balanceBefore = userBalance,
            balanceAfter = newBalance
        )

        // Create payment record
        val payment = orderRepository.createPayment(tenantId, userId, orderId, "balance", amount)

        // Update order status
        val newStatus = if (isUrgent) OrderStatus.URGENT.code else OrderStatus.PENDING.code
        orderRepository.updateStatus(orderId, tenantId, userId, newStatus, "Payment completed via balance")

        // Update payment info on order
        orderRepository.update(orderId, tenantId) {
            it[paymentId] = payment.id
            it[paymentMethod] = "balance"
            if (isUrgent) {
                it[urgentAmount] = BigDecimal("10.00")
            }
        }

        return Result.success(PaymentResponse(
            success = true,
            orderId = orderId,
            newStatus = newStatus,
            paymentId = payment.id,
            message = "Payment successful"
        ))
    }

    private fun processStripePayment(
        orderId: Long,
        tenantId: Long,
        userId: Long,
        amount: BigDecimal,
        isUrgent: Boolean
    ): Result<PaymentResponse> {
        // TODO: Create Stripe checkout session
        // For now, return a placeholder

        val payment = orderRepository.createPayment(tenantId, userId, orderId, "stripe", amount, "stripe_session_placeholder")

        return Result.success(PaymentResponse(
            success = true,
            orderId = orderId,
            newStatus = OrderStatus.PAYMENT_PENDING.code,
            paymentId = payment.id,
            stripeSessionUrl = "https://checkout.stripe.com/placeholder",
            message = "Redirect to Stripe checkout"
        ))
    }

    // =====================================================
    // STEP 4 - CONFIRMATION
    // =====================================================

    fun getStep4(id: Long, tenantId: Long): Result<Step4Response> {
        val order = orderRepository.findByIdWithProducts(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val status = OrderStatus.fromCode(order.orderStatus)
        if (status != OrderStatus.PENDING && status != OrderStatus.URGENT) {
            return Result.failure(IllegalStateException("Order is not ready for confirmation"))
        }

        val issues = mutableListOf<String>()

        // Check for unmapped products
        if (order.orderMapStatus == OrderMapStatus.UNMAPPED.code) {
            issues.add("Some products are not mapped")
        }

        // Check for missing address
        if (order.shippingAddress == null && order.orderInfo?.toAddress == null) {
            issues.add("Shipping address is missing")
        }

        val summary = OrderPriceSummary(
            subtotal = order.products.sumOf { it.priceBreakdown?.lineTotal ?: BigDecimal.ZERO },
            shippingPrice = order.shippingAmount,
            giftNotePrice = if (order.giftNote != null) {
                getUserPriceProfile(order.userId, tenantId)?.giftNotePrice ?: BigDecimal.ZERO
            } else BigDecimal.ZERO,
            urgentPrice = order.urgentAmount,
            totalPrice = order.totalAmount
        )

        return Result.success(Step4Response(
            orderId = id,
            orderStatus = order.orderStatus,
            orderMapStatus = order.orderMapStatus,
            summary = summary,
            shippingInfo = order.orderInfo?.shipping,
            products = order.products,
            address = order.shippingAddress ?: order.orderInfo?.toAddress,
            canProceed = issues.isEmpty(),
            issues = issues
        ))
    }

    fun confirmOrder(id: Long, tenantId: Long, userId: Long, request: ConfirmOrderRequest): Result<OrderFull> {
        val order = orderRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val status = OrderStatus.fromCode(order.orderStatus)
        if (status != OrderStatus.PENDING && status != OrderStatus.URGENT) {
            return Result.failure(IllegalStateException("Order cannot be confirmed in current status"))
        }

        if (!request.confirmProduction) {
            return Result.failure(IllegalArgumentException("Production confirmation required"))
        }

        // Move to IN_PRODUCTION
        orderRepository.updateStatus(id, tenantId, userId, OrderStatus.IN_PRODUCTION.code, request.notes ?: "Order confirmed for production")

        // TODO: Trigger gangsheet generation
        // TODO: Create shipping label

        return Result.success(orderRepository.findByIdWithProducts(id, tenantId)!!)
    }

    // =====================================================
    // ORDER ACTIONS
    // =====================================================

    fun changeOrderStatus(id: Long, tenantId: Long, userId: Long, request: ChangeStatusRequest): Result<OrderFull> {
        val order = orderRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        orderRepository.updateStatus(id, tenantId, userId, request.newStatus, request.notes)

        return Result.success(orderRepository.findByIdWithProducts(id, tenantId)!!)
    }

    fun deleteOrder(id: Long, tenantId: Long, userId: Long): Result<Boolean> {
        val order = orderRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        return Result.success(orderRepository.delete(id, tenantId, userId))
    }

    fun markAsShipped(id: Long, tenantId: Long, userId: Long, trackingNumber: String?, trackingUrl: String?): Result<OrderFull> {
        val order = orderRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        orderRepository.update(id, tenantId) {
            it[Orders.trackingNumber] = trackingNumber
            it[Orders.trackingUrl] = trackingUrl
            it[Orders.shippedAt] = java.time.Instant.now()
        }

        orderRepository.updateStatus(id, tenantId, userId, OrderStatus.SHIPPED.code, "Order shipped")

        return Result.success(orderRepository.findByIdWithProducts(id, tenantId)!!)
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun calculateProductPrice(
        basePrice: BigDecimal,
        modifications: List<ModificationDetailItem>,
        stitchCount: Int,
        quantity: Int,
        priceProfile: PriceProfileFull?
    ): PriceBreakdownItem {
        // Calculate modification total
        val modificationTotal = modifications.sumOf { it.priceDifference }

        // Calculate stitch charges
        val stitchTotal = if (stitchCount > 0 && priceProfile != null) {
            priceProfile.stitchPrice * BigDecimal(stitchCount)
        } else BigDecimal.ZERO

        // Calculate unit total before discount
        val unitTotal = basePrice + modificationTotal + stitchTotal

        // Apply discount (placeholder - will be calculated properly in Step 3)
        val discount = BigDecimal.ZERO

        val finalUnitTotal = (unitTotal - discount).setScale(2, RoundingMode.HALF_UP)
        val lineTotal = (finalUnitTotal * BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP)

        return PriceBreakdownItem(
            basePrice = basePrice,
            modificationTotal = modificationTotal.setScale(2, RoundingMode.HALF_UP),
            stitchTotal = stitchTotal.setScale(2, RoundingMode.HALF_UP),
            discount = discount,
            unitTotal = finalUnitTotal,
            quantity = quantity,
            lineTotal = lineTotal
        )
    }

    private fun calculatePriceDetail(
        orderProductId: Long,
        basePrice: BigDecimal,
        modifications: List<ModificationDetailItem>,
        stitchCount: Int,
        quantity: Int,
        priceProfile: PriceProfileFull?,
        variantId: Long?
    ): PriceDetailItem {
        // Calculate modification prices
        val modificationPrices = modifications.associate { it.modificationName to it.priceDifference }
        val afterModificationPrice = basePrice + modifications.sumOf { it.priceDifference }

        // Calculate stitch price
        val stitchUnitPrice = priceProfile?.stitchPrice ?: BigDecimal.ZERO
        val stitchPrice = stitchUnitPrice * BigDecimal(stitchCount)

        // Calculate discount
        var discountSource: String? = null
        var discountType: String? = null
        var discountAmount = BigDecimal.ZERO

        if (priceProfile != null) {
            // Check for variant-specific override
            val variantOverride = variantId?.let { vid ->
                priceProfile.productOverrides.find { it.variantId == vid }
            }

            if (variantOverride != null) {
                discountSource = "variants"
                discountType = if (variantOverride.discountType == 0) "percent" else "dollar"
                discountAmount = if (variantOverride.discountType == 0) {
                    (afterModificationPrice + stitchPrice) * variantOverride.discountAmount / BigDecimal(100)
                } else {
                    variantOverride.discountAmount
                }
            } else if (priceProfile.discountAmount > BigDecimal.ZERO) {
                discountSource = "priceProfile"
                discountType = if (priceProfile.discountType == 0) "percent" else "dollar"
                discountAmount = if (priceProfile.discountType == 0) {
                    (afterModificationPrice + stitchPrice) * priceProfile.discountAmount / BigDecimal(100)
                } else {
                    priceProfile.discountAmount
                }
            }
        }

        val afterDiscountPrice = (afterModificationPrice + stitchPrice - discountAmount).setScale(2, RoundingMode.HALF_UP)
        val lineTotal = (afterDiscountPrice * BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP)

        return PriceDetailItem(
            orderProductId = orderProductId,
            price = basePrice,
            modifications = modificationPrices,
            stitchPrice = stitchPrice.setScale(2, RoundingMode.HALF_UP),
            stitchCount = stitchCount,
            stitchUnitPrice = stitchUnitPrice,
            afterModificationPrice = afterModificationPrice.setScale(2, RoundingMode.HALF_UP),
            discountSource = discountSource,
            discountType = discountType,
            discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP),
            afterDiscountPrice = afterDiscountPrice,
            quantity = quantity,
            lineTotal = lineTotal
        )
    }

    private fun calculateShippingOptions(order: OrderFull, tenantId: Long, userId: Long): List<ShippingOption> {
        // Get user's shipping profile
        val shippingProfile = profileRepository.findDefaultShippingProfile(tenantId)
            ?: return emptyList()

        val methods = profileRepository.findAllShippingMethods(tenantId, shippingProfile.id)

        // For quantity-based, calculate using profile pricing
        return if (shippingProfile.profileType == 0) {
            val pricing = shippingProfile.profilePricing ?: return emptyList()

            // Count items (simplified - would need to check is_heavy from categories)
            val totalItems = order.products.sumOf { it.quantity }

            val baseRate = when {
                totalItems == 1 -> pricing.lightFirst ?: BigDecimal.ZERO
                totalItems == 2 -> (pricing.lightFirst ?: BigDecimal.ZERO) + (pricing.lightSecond ?: BigDecimal.ZERO)
                else -> (pricing.lightFirst ?: BigDecimal.ZERO) + (pricing.lightSecond ?: BigDecimal.ZERO) +
                        ((pricing.lightThird ?: BigDecimal.ZERO) * BigDecimal(totalItems - 2))
            }

            methods.map { method ->
                ShippingOption(
                    methodName = method.name,
                    methodPrice = (baseRate + method.extraFee).setScale(2, RoundingMode.HALF_UP),
                    service = method.apiMethod,
                    shippingId = method.id,
                    isInternational = method.isInternational,
                    estimatedDays = method.processingInfo?.let {
                        "${it.minDeliveryDays}-${it.maxDeliveryDays} business days"
                    }
                )
            }
        } else {
            // API-based - would call EasyPost here
            // For now return empty
            emptyList()
        }
    }

    private fun getUserPriceProfile(userId: Long, tenantId: Long): PriceProfileFull? {
        // TODO: Get user's assigned price profile from users table
        // For now, return default
        return profileRepository.findDefaultPriceProfile(tenantId)
    }

    private fun getUserBalance(userId: Long, tenantId: Long): BigDecimal = transaction {
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.tenantId eq tenantId) }
            .singleOrNull()
            ?.get(Users.totalCredit) ?: BigDecimal.ZERO
    }

    private fun updateUserBalance(userId: Long, tenantId: Long, newBalance: BigDecimal) = transaction {
        Users.update(
            where = { (Users.id eq userId) and (Users.tenantId eq tenantId) }
        ) {
            it[totalCredit] = newBalance
        }
    }

    // =====================================================
    // ORDER ACTIONS - BULK OPERATIONS
    // =====================================================

    fun bulkUpdateStatus(tenantId: Long, userId: Long, orderIds: List<Long>, status: String): Result<Int> {
        val statusCode = when (status.lowercase()) {
            "shipped" -> OrderStatus.SHIPPED.code
            "editing" -> OrderStatus.EDITING.code
            "pending" -> OrderStatus.PENDING.code
            "urgent" -> OrderStatus.URGENT.code
            "cancelled" -> OrderStatus.CANCELLED.code
            "awaitingresponse" -> OrderStatus.AWAITING_RESPONSE.code
            "inproduction" -> OrderStatus.IN_PRODUCTION.code
            else -> return Result.failure(IllegalArgumentException("Invalid status: $status"))
        }

        var updatedCount = 0
        orderIds.forEach { orderId ->
            if (orderRepository.updateStatus(orderId, tenantId, userId, statusCode, "Bulk status update to $status")) {
                updatedCount++
            }
        }

        return Result.success(updatedCount)
    }

    fun cancelOrder(tenantId: Long, userId: Long, orderId: Long, refundLabel: Boolean = false): Result<Boolean> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val status = OrderStatus.fromCode(order.orderStatus)
        val cancelableStatuses = listOf(OrderStatus.PENDING, OrderStatus.URGENT)

        if (status !in cancelableStatuses) {
            return Result.failure(IllegalStateException("Cannot cancel order in current status: ${status.name}"))
        }

        // Record refund transaction
        orderRepository.createTransaction(
            tenantId = tenantId,
            userId = order.userId,
            type = TransactionType.ORDER_REFUND.code,
            amount = order.totalAmount,
            description = "Complete refund of order: $orderId",
            referenceId = orderId.toString(),
            balanceBefore = getUserBalance(order.userId, tenantId),
            balanceAfter = getUserBalance(order.userId, tenantId) + order.totalAmount
        )

        // Update user balance
        val currentBalance = getUserBalance(order.userId, tenantId)
        updateUserBalance(order.userId, tenantId, currentBalance + order.totalAmount)

        // Update order status to cancelled
        orderRepository.updateStatus(orderId, tenantId, userId, OrderStatus.CANCELLED.code, "Order cancelled by user")

        // TODO: If refundLabel is true, request refund from shipping provider

        return Result.success(true)
    }

    fun combineOrders(tenantId: Long, userId: Long, orderIds: List<Long>): Result<CombineOrdersResponse> {
        if (orderIds.size < 2) {
            return Result.failure(IllegalArgumentException("At least 2 orders required to combine"))
        }

        val mainOrderId = orderIds.first()
        val otherOrderIds = orderIds.drop(1)

        // Verify all orders exist and are NEW_ORDER status
        val orders = orderIds.mapNotNull { orderRepository.findById(it, tenantId) }
        if (orders.size != orderIds.size) {
            return Result.failure(IllegalArgumentException("Some orders not found"))
        }

        orders.forEach { order ->
            if (order.orderStatus != OrderStatus.NEW_ORDER.code) {
                return Result.failure(IllegalStateException("Order ${order.intOrderId} is not a new order"))
            }
        }

        val mainOrder = orders.first()
        var productsCopied = 0

        // Copy products from other orders to main order
        otherOrderIds.forEach { otherId ->
            val otherProducts = orderRepository.findOrderProducts(otherId, tenantId)
            otherProducts.forEach { product ->
                orderRepository.createOrderProduct(
                    orderId = mainOrderId,
                    tenantId = tenantId,
                    request = CreateOrderProductRequest(
                        listingId = product.listingId,
                        productId = product.productId,
                        variantId = product.variantId,
                        quantity = product.quantity,
                        listingImageUrl = product.listingImageUrl,
                        modificationDetail = product.modificationDetail
                    )
                )
                productsCopied++
            }

            // Mark other orders as COMBINED
            orderRepository.updateStatus(otherId, tenantId, userId, OrderStatus.COMBINED.code, "Combined into order $mainOrderId")
        }

        return Result.success(CombineOrdersResponse(
            success = true,
            combinedOrderId = mainOrderId,
            originalOrderIds = orderIds,
            message = "Orders combined successfully. $productsCopied products copied."
        ))
    }

    // =====================================================
    // ORDER ACTIONS - TRACKING & SHIPPING
    // =====================================================

    fun updateTrackingCode(tenantId: Long, orderId: Long, trackingCode: String): Result<Boolean> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val orderInfo = order.orderInfo ?: OrderInfoFull()
        val shipping = orderInfo.shipping?.copy(trackingCode = trackingCode)
            ?: ShippingSelection(trackingCode = trackingCode)

        val updatedInfo = orderInfo.copy(shipping = shipping)
        orderRepository.updateOrderInfo(orderId, tenantId, updatedInfo)

        return Result.success(true)
    }

    fun getShippingMethods(tenantId: Long, orderId: Long): Result<GetShippingMethodsResponse> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        if (order.orderStatus == OrderStatus.NEW_ORDER.code) {
            return Result.success(GetShippingMethodsResponse(
                success = false,
                message = "This order has not been processed yet"
            ))
        }

        val orderInfo = order.orderInfo
        val shipping = orderInfo?.shipping
        if (shipping?.labelUrl != null) {
            return Result.success(GetShippingMethodsResponse(
                success = false,
                message = "Shipping label has already been created for this order"
            ))
        }

        val selectedRateId = shipping?.rateId
        val methods = orderInfo?.shippingOptions?.map { option ->
            ShippingMethodItem(
                rateId = option.rateId ?: "",
                methodName = option.methodName,
                selected = option.rateId == selectedRateId
            )
        } ?: emptyList()

        return Result.success(GetShippingMethodsResponse(
            success = true,
            shippingMethods = methods
        ))
    }

    fun setShippingMethod(tenantId: Long, orderId: Long, rateId: String, methodName: String): Result<Boolean> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val orderInfo = order.orderInfo ?: OrderInfoFull()
        val shipping = orderInfo.shipping?.copy(rateId = rateId, methodName = methodName)
            ?: ShippingSelection(rateId = rateId, methodName = methodName)

        val updatedInfo = orderInfo.copy(shipping = shipping)
        orderRepository.updateOrderInfo(orderId, tenantId, updatedInfo)

        return Result.success(true)
    }

    fun uploadCustomLabel(tenantId: Long, orderId: Long, labelUrl: String): Result<Boolean> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val orderInfo = order.orderInfo ?: OrderInfoFull()
        val shipping = orderInfo.shipping?.copy(labelUrl = labelUrl)
            ?: ShippingSelection(labelUrl = labelUrl)

        val updatedInfo = orderInfo.copy(shipping = shipping)
        orderRepository.updateOrderInfo(orderId, tenantId, updatedInfo)

        return Result.success(true)
    }

    // =====================================================
    // ORDER ACTIONS - REFUNDS
    // =====================================================

    fun updateRefundAmount(tenantId: Long, userId: Long, orderId: Long, refundAmount: BigDecimal): Result<Boolean> {
        val order = orderRepository.findById(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        // Record refund transaction
        val currentBalance = getUserBalance(order.userId, tenantId)
        orderRepository.createTransaction(
            tenantId = tenantId,
            userId = order.userId,
            type = TransactionType.ORDER_REFUND.code,
            amount = refundAmount,
            description = "Partial refund of order: $orderId - Amount: $refundAmount",
            referenceId = orderId.toString(),
            balanceBefore = currentBalance,
            balanceAfter = currentBalance + refundAmount
        )

        // Update user balance
        updateUserBalance(order.userId, tenantId, currentBalance + refundAmount)

        return Result.success(true)
    }

    // =====================================================
    // ORDER ACTIONS - PACKING SLIPS
    // =====================================================

    fun getOrdersForPackingSlip(
        tenantId: Long,
        orderIds: List<Long>,
        includeLabels: Boolean = false,
        groupByModification: Boolean = false
    ): List<ProcessedOrder> {
        val processedOrders = mutableListOf<ProcessedOrder>()

        orderIds.forEach { orderId ->
            val order = orderRepository.findByIdWithProducts(orderId, tenantId) ?: return@forEach
            if (order.orderStatus == OrderStatus.AWAITING_RESPONSE.code) return@forEach

            val products = order.products.map { product ->
                val modifications = product.modificationDetail.map { mod ->
                    OrderPackingSlipModification(
                        modificationId = mod.modificationId,
                        modificationName = mod.modificationName,
                        modificationDesign = mod.modificationDesign,
                        modificationDesignId = mod.modificationDesignId
                    )
                }

                OrderPackingSlipProduct(
                    title = product.productDetail?.product ?: "Unknown",
                    option1 = product.productDetail?.option1,
                    option2 = product.productDetail?.option2,
                    quantity = product.quantity,
                    modifications = modifications
                )
            }

            val shipping = order.orderInfo?.shipping
            processedOrders.add(ProcessedOrder(
                orderId = order.id,
                orderStatus = order.orderStatus,
                orderDate = order.updatedAt,
                intOrderId = order.intOrderId,
                labelUrl = shipping?.labelUrl,
                trackingCode = shipping?.trackingCode,
                labelSize = "4x6",
                products = products,
                customerName = order.customerName,
                storeName = null, // TODO: Get store name
                batches = "",
                orderNote = order.orderInfo?.orderNote,
                giftNote = order.giftNote ?: order.orderInfo?.giftNote,
                includeLabel = includeLabels
            ))
        }

        if (groupByModification) {
            // Sort by modification name and stitch count
            processedOrders.sortBy { order ->
                order.products.flatMap { it.modifications }.firstOrNull()?.modificationName ?: "Unknown"
            }
        }

        return processedOrders
    }

    // =====================================================
    // ORDER ACTIONS - GANGSHEET PRODUCTS
    // =====================================================

    fun getProductsForGangsheet(tenantId: Long, orderIds: List<Long>): List<GangsheetOrderProduct> {
        val result = mutableListOf<GangsheetOrderProduct>()

        orderIds.forEach { orderId ->
            val products = orderRepository.findOrderProducts(orderId, tenantId)
            products.forEach { product ->
                val modifications = product.modificationDetail.map { mod ->
                    GangsheetModification(
                        modificationName = mod.modificationName,
                        designUrl = mod.modificationDesign
                    )
                }

                result.add(GangsheetOrderProduct(
                    orderId = orderId,
                    orderProductId = product.id,
                    productDetail = product.productDetail,
                    modifications = modifications
                ))
            }
        }

        return result
    }

    // =====================================================
    // ORDER ACTIONS - ANALYTICS
    // =====================================================

    fun getOrderAnalytics(
        tenantId: Long,
        userId: Long?,
        storeId: Long?,
        startDate: String?,
        endDate: String?
    ): List<OrderAnalyticsData> = transaction {
        // Build query for analytics
        var query = Orders.selectAll()
            .where { Orders.orderStatus eq OrderStatus.SHIPPED.code }

        userId?.let { uid ->
            query = query.andWhere { Orders.userId eq uid }
        }

        storeId?.let { sid ->
            query = query.andWhere { Orders.storeId eq sid }
        }

        // TODO: Add date filtering when needed

        query.groupBy(Orders.createdAt, Orders.storeId)
            .map { row ->
                OrderAnalyticsData(
                    storeName = "Store", // TODO: Join with stores table
                    orderDate = row[Orders.createdAt].toString().substring(0, 10),
                    totalAmountPerDay = row[Orders.totalAmount]
                )
            }
    }

    // =====================================================
    // ORDER SYNC FROM MARKETPLACES
    // =====================================================

    fun getOrdersByIdsForExport(tenantId: Long, orderIds: List<Long>): List<OrderFull> {
        return orderIds.mapNotNull { orderRepository.findByIdWithProducts(it, tenantId) }
    }

    fun getOrderIdsByBatch(tenantId: Long, batchId: Long): List<Long> {
        // TODO: Implement batch lookup
        return emptyList()
    }

    // =====================================================
    // PRODUCT SELECTION DATA (for Manual Orders)
    // =====================================================

    fun getProductSelectionData(tenantId: Long): ProductSelectionData {
        val categories = categoryRepository.findAll(tenantId)
        val products = productRepository.findAll(tenantId, null, false)

        val option1s = mutableListOf<Option1ForSelection>()
        val option2s = mutableListOf<Option2ForSelection>()
        val variants = mutableListOf<VariantForSelection>()
        val variantModifications = mutableListOf<VariantModificationForSelection>()
        val modifications = mutableListOf<ModificationForSelection>()

        // Fetch options and variants for each product
        products.forEach { product ->
            productRepository.findOption1s(product.id, tenantId).forEach { opt1 ->
                option1s.add(Option1ForSelection(
                    id = opt1.id,
                    productId = opt1.productId,
                    name = opt1.name,
                    sortOrder = opt1.sortOrder
                ))
            }

            productRepository.findOption2s(product.id, tenantId).forEach { opt2 ->
                option2s.add(Option2ForSelection(
                    id = opt2.id,
                    productId = opt2.productId,
                    name = opt2.name,
                    hexColor = opt2.hexColor,
                    isDark = opt2.isDark,
                    sortOrder = opt2.sortOrder
                ))
            }

            productRepository.findVariants(product.id, tenantId).forEach { variant ->
                variants.add(VariantForSelection(
                    id = variant.id,
                    productId = variant.productId,
                    option1Id = variant.option1Id,
                    option2Id = variant.option2Id,
                    price = variant.price,
                    width1 = variant.width1,
                    width2 = variant.width2,
                    inStock = variant.inStock,
                    inHouse = variant.inHouse,
                    status = variant.status
                ))
            }

            productRepository.findVariantModifications(product.id, tenantId).forEach { vm ->
                variantModifications.add(VariantModificationForSelection(
                    id = vm.id,
                    productId = vm.productId,
                    option1Id = vm.option1Id,
                    weight = vm.weight,
                    width = vm.width,
                    height = vm.height,
                    depth = vm.depth
                ))
            }
        }

        // Fetch modifications for each category
        categories.forEach { category ->
            categoryRepository.findModifications(category.id, tenantId).forEach { mod ->
                modifications.add(ModificationForSelection(
                    id = mod.id,
                    categoryId = mod.categoryId,
                    name = mod.name,
                    priceDifference = mod.priceDifference,
                    useWidth = mod.useWidth
                ))
            }
        }

        return ProductSelectionData(
            categories = categories.map { cat ->
                CategoryForSelection(
                    id = cat.id,
                    name = cat.name,
                    parentCategoryId = cat.parentCategoryId,
                    isHeavy = cat.isHeavy
                )
            },
            products = products.map { prod ->
                ProductForSelection(
                    id = prod.id,
                    categoryId = prod.categoryId,
                    title = prod.title,
                    designType = prod.designType,
                    option1Name = prod.option1Name,
                    option2Name = prod.option2Name
                )
            },
            option1s = option1s,
            option2s = option2s,
            variants = variants,
            variantModifications = variantModifications,
            modifications = modifications
        )
    }
}

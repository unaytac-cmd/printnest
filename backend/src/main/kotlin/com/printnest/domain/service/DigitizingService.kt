package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.DigitizingRepository
import com.printnest.domain.repository.DesignRepository
import com.printnest.domain.repository.ProfileRepository
import com.printnest.integrations.aws.S3Service
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

class DigitizingService(
    private val digitizingRepository: DigitizingRepository,
    private val designRepository: DesignRepository,
    private val profileRepository: ProfileRepository,
    private val s3Service: S3Service
) {
    private val logger = LoggerFactory.getLogger(DigitizingService::class.java)

    // Default pricing constants
    companion object {
        const val DEFAULT_BASE_PRICE = 25.00
        const val DEFAULT_RUSH_FEE_MULTIPLIER = 1.5
        const val COMPLEXITY_THRESHOLD_STITCHES = 10000
        const val COMPLEXITY_FEE_PER_1000_STITCHES = 2.00
        const val DEFAULT_TURNAROUND = "3-5 business days"
        const val RUSH_TURNAROUND = "24-48 hours"
    }

    // =====================================================
    // DIGITIZING ORDER QUERIES
    // =====================================================

    fun getDigitizingOrder(id: Long, tenantId: Long): Result<DigitizingOrder> {
        val order = digitizingRepository.findOrderById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

        return Result.success(order)
    }

    fun getDigitizingOrders(
        tenantId: Long,
        filters: DigitizingOrderFilters
    ): DigitizingOrderListResponse {
        val (orders, total) = digitizingRepository.findAllOrders(tenantId, filters)
        val totalPages = if (total == 0) 0 else (total + filters.limit - 1) / filters.limit

        return DigitizingOrderListResponse(
            orders = orders,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getOrdersByUser(userId: Long, tenantId: Long): List<DigitizingOrder> {
        return digitizingRepository.findOrdersByUser(userId, tenantId)
    }

    fun getOrdersByDigitizer(digitizerId: Long, tenantId: Long): List<DigitizingOrder> {
        return digitizingRepository.findOrdersByDigitizer(digitizerId, tenantId)
    }

    fun getPendingOrders(tenantId: Long): List<DigitizingOrder> {
        return digitizingRepository.findPendingOrders(tenantId)
    }

    // =====================================================
    // CREATE DIGITIZING ORDER
    // =====================================================

    fun createDigitizingOrder(
        tenantId: Long,
        userId: Long,
        designId: Long?,
        notes: String?
    ): Result<DigitizingOrder> {
        try {
            // If designId is provided, get design details
            val design = designId?.let { designRepository.findById(it, tenantId) }

            if (designId != null && design == null) {
                return Result.failure(IllegalArgumentException("Design not found"))
            }

            val request = CreateDigitizingOrderRequest(
                designId = designId,
                name = design?.title ?: "Digitizing Order",
                fileUrl = design?.designUrl ?: "",
                width = design?.width,
                height = design?.height,
                notes = notes,
                estimatedStitchCount = design?.metadata?.stitchCount,
                colorCount = design?.metadata?.colorCount
            )

            if (request.fileUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("File URL is required"))
            }

            val order = digitizingRepository.createOrder(
                tenantId = tenantId,
                userId = userId,
                request = request,
                initialStatus = DigitizingStatus.PAYMENT_PENDING.code
            )

            logger.info("Created digitizing order ${order.id} for user $userId in tenant $tenantId")

            return Result.success(order)
        } catch (e: Exception) {
            logger.error("Failed to create digitizing order", e)
            return Result.failure(e)
        }
    }

    fun createDigitizingOrderDirect(
        tenantId: Long,
        userId: Long,
        request: CreateDigitizingOrderRequest
    ): Result<DigitizingOrder> {
        try {
            if (request.name.isBlank()) {
                return Result.failure(IllegalArgumentException("Name is required"))
            }

            if (request.fileUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("File URL is required"))
            }

            val order = digitizingRepository.createOrder(
                tenantId = tenantId,
                userId = userId,
                request = request,
                initialStatus = DigitizingStatus.PAYMENT_PENDING.code
            )

            logger.info("Created digitizing order ${order.id} for user $userId in tenant $tenantId")

            return Result.success(order)
        } catch (e: Exception) {
            logger.error("Failed to create digitizing order", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // QUOTE CALCULATION
    // =====================================================

    fun getDigitizingQuote(
        tenantId: Long,
        userId: Long,
        request: DigitizingQuoteRequest
    ): Result<DigitizingQuoteResponse> {
        try {
            // Get user's price profile for custom pricing
            val priceProfile = profileRepository.findDefaultPriceProfile(tenantId)
            val basePrice = priceProfile?.let {
                // Try to get digitizing_price from profile
                BigDecimal(DEFAULT_BASE_PRICE)
            } ?: BigDecimal(DEFAULT_BASE_PRICE)

            // Calculate complexity fee based on estimated stitch count
            val estimatedStitches = request.estimatedStitchCount ?: estimateStitchCount(
                request.width,
                request.height,
                request.colorCount
            )

            val complexityFee = if (estimatedStitches > COMPLEXITY_THRESHOLD_STITCHES) {
                val extraThousands = (estimatedStitches - COMPLEXITY_THRESHOLD_STITCHES) / 1000.0
                BigDecimal(extraThousands * COMPLEXITY_FEE_PER_1000_STITCHES)
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            // Rush fee
            val rushFee = if (request.isRush) {
                basePrice.multiply(BigDecimal(DEFAULT_RUSH_FEE_MULTIPLIER - 1))
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            // Total price
            val totalPrice = basePrice.add(complexityFee).add(rushFee)
                .setScale(2, RoundingMode.HALF_UP)

            val turnaround = if (request.isRush) RUSH_TURNAROUND else DEFAULT_TURNAROUND

            logger.info("Generated quote for tenant $tenantId: base=$basePrice, complexity=$complexityFee, rush=$rushFee, total=$totalPrice")

            return Result.success(
                DigitizingQuoteResponse(
                    basePrice = basePrice.toDouble(),
                    rushFee = rushFee.toDouble(),
                    complexityFee = complexityFee.toDouble(),
                    totalPrice = totalPrice.toDouble(),
                    estimatedStitchCount = estimatedStitches,
                    estimatedTurnaround = turnaround,
                    notes = if (estimatedStitches > COMPLEXITY_THRESHOLD_STITCHES) {
                        "Complex design with high stitch count may require additional time."
                    } else null
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to calculate quote", e)
            return Result.failure(e)
        }
    }

    private fun estimateStitchCount(width: Double?, height: Double?, colorCount: Int?): Int {
        // Basic estimation: ~1000 stitches per square inch, adjusted by color count
        val area = (width ?: 3.0) * (height ?: 3.0)
        val baseStitches = (area * 1000).toInt()
        val colorMultiplier = 1 + ((colorCount ?: 1) * 0.1)
        return (baseStitches * colorMultiplier).toInt()
    }

    // =====================================================
    // STATUS UPDATES
    // =====================================================

    fun updateDigitizingStatus(
        id: Long,
        tenantId: Long,
        status: Int,
        requestedPrice: Double? = null
    ): Result<DigitizingOrder> {
        try {
            val existingOrder = digitizingRepository.findOrderById(id, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            // Validate status transition
            val newStatus = DigitizingStatus.fromCode(status)
                ?: return Result.failure(IllegalArgumentException("Invalid status code: $status"))

            val currentStatus = DigitizingStatus.fromCode(existingOrder.status)

            // Prevent invalid transitions
            if (currentStatus == DigitizingStatus.COMPLETED) {
                return Result.failure(IllegalArgumentException("Cannot update status of completed order"))
            }

            if (currentStatus == DigitizingStatus.CANCELLED) {
                return Result.failure(IllegalArgumentException("Cannot update status of cancelled order"))
            }

            val order = digitizingRepository.updateOrderStatus(id, tenantId, status, requestedPrice)
                ?: return Result.failure(IllegalArgumentException("Failed to update order status"))

            logger.info("Updated digitizing order $id status from ${currentStatus?.label} to ${newStatus.label}")

            return Result.success(order)
        } catch (e: Exception) {
            logger.error("Failed to update digitizing status", e)
            return Result.failure(e)
        }
    }

    fun acceptTask(
        orderId: Long,
        tenantId: Long,
        digitizerId: Long,
        requestedPrice: Double
    ): Result<DigitizingOrder> {
        try {
            val order = digitizingRepository.findOrderById(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            if (order.status != DigitizingStatus.PENDING.code) {
                return Result.failure(IllegalArgumentException("Order is not in pending status"))
            }

            // Assign digitizer if not already assigned
            if (order.digitizerId == null) {
                digitizingRepository.assignDigitizer(orderId, tenantId, digitizerId)
            }

            // Update status to in-progress with requested price
            val updatedOrder = digitizingRepository.updateOrderStatus(
                orderId,
                tenantId,
                DigitizingStatus.IN_PROGRESS.code,
                requestedPrice
            ) ?: return Result.failure(IllegalArgumentException("Failed to accept task"))

            logger.info("Digitizer $digitizerId accepted task $orderId with price $requestedPrice")

            return Result.success(updatedOrder)
        } catch (e: Exception) {
            logger.error("Failed to accept task", e)
            return Result.failure(e)
        }
    }

    fun assignDigitizer(
        orderId: Long,
        tenantId: Long,
        digitizerId: Long
    ): Result<DigitizingOrder> {
        try {
            val order = digitizingRepository.findOrderById(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            if (order.status != DigitizingStatus.PENDING.code) {
                return Result.failure(IllegalArgumentException("Can only assign digitizer to pending orders"))
            }

            val updatedOrder = digitizingRepository.assignDigitizer(orderId, tenantId, digitizerId)
                ?: return Result.failure(IllegalArgumentException("Failed to assign digitizer"))

            logger.info("Assigned digitizer $digitizerId to order $orderId")

            return Result.success(updatedOrder)
        } catch (e: Exception) {
            logger.error("Failed to assign digitizer", e)
            return Result.failure(e)
        }
    }

    fun cancelOrder(orderId: Long, tenantId: Long): Result<Boolean> {
        try {
            val order = digitizingRepository.findOrderById(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            // Cannot cancel completed or already cancelled orders
            if (order.status == DigitizingStatus.COMPLETED.code) {
                return Result.failure(IllegalArgumentException("Cannot cancel completed order"))
            }

            if (order.status == DigitizingStatus.CANCELLED.code) {
                return Result.failure(IllegalArgumentException("Order is already cancelled"))
            }

            // Cannot cancel in-progress orders (would need refund logic)
            if (order.status == DigitizingStatus.IN_PROGRESS.code) {
                return Result.failure(IllegalArgumentException("Cannot cancel order that is in progress"))
            }

            val cancelled = digitizingRepository.cancelOrder(orderId, tenantId)

            if (cancelled) {
                logger.info("Cancelled digitizing order $orderId")
                // TODO: Trigger refund if payment was made
            }

            return Result.success(cancelled)
        } catch (e: Exception) {
            logger.error("Failed to cancel order", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // FILE UPLOAD
    // =====================================================

    fun uploadDigitizedFile(
        orderId: Long,
        tenantId: Long,
        request: UploadDigitizedFileRequest
    ): Result<DigitizingOrder> {
        try {
            val order = digitizingRepository.findOrderById(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            if (order.status != DigitizingStatus.IN_PROGRESS.code) {
                return Result.failure(IllegalArgumentException("Order must be in-progress to upload files"))
            }

            if (request.dstFileUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("DST file URL is required"))
            }

            val updatedOrder = digitizingRepository.uploadResultFiles(orderId, tenantId, request)
                ?: return Result.failure(IllegalArgumentException("Failed to upload result files"))

            logger.info("Uploaded result files for digitizing order $orderId")

            // TODO: Send notification email to customer

            return Result.success(updatedOrder)
        } catch (e: Exception) {
            logger.error("Failed to upload digitized file", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // EMBROIDERY FILE PARSING (PLACEHOLDER)
    // =====================================================

    fun parseEmbroideryFile(fileUrl: String): Result<EmbroideryFileInfo> {
        // TODO: Implement actual DST file parsing using a library like pyembroidery equivalent
        // This is a placeholder that returns estimated values

        logger.info("Parsing embroidery file: $fileUrl")

        // For now, return a placeholder response
        // In production, this would:
        // 1. Download the DST file from S3
        // 2. Parse using embroidery library
        // 3. Extract stitch count, dimensions, colors, etc.

        return Result.success(
            EmbroideryFileInfo(
                stitchCount = 0,    // Would be parsed from file
                width = 0.0,        // mm
                height = 0.0,       // mm
                colorCount = 0,
                colors = emptyList(),
                format = "DST",
                jumpCount = null,
                trimCount = null
            )
        )
    }

    // =====================================================
    // THREAD COLORS
    // =====================================================

    fun getEmbroideryColors(tenantId: Long, inStockOnly: Boolean = false): ThreadColorListResponse {
        val colors = digitizingRepository.findAllColors(tenantId, inStockOnly)
        return ThreadColorListResponse(
            colors = colors,
            total = colors.size
        )
    }

    fun createThreadColor(tenantId: Long, request: CreateThreadColorRequest): Result<ThreadColor> {
        try {
            if (request.name.isBlank()) {
                return Result.failure(IllegalArgumentException("Color name is required"))
            }

            if (!request.hexCode.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                return Result.failure(IllegalArgumentException("Invalid hex color code"))
            }

            val color = digitizingRepository.createColor(tenantId, request)
            logger.info("Created thread color ${color.id}: ${color.name}")

            return Result.success(color)
        } catch (e: Exception) {
            logger.error("Failed to create thread color", e)
            return Result.failure(e)
        }
    }

    fun updateThreadColor(
        id: Long,
        tenantId: Long,
        request: UpdateThreadColorRequest
    ): Result<ThreadColor> {
        try {
            request.hexCode?.let { hex ->
                if (!hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    return Result.failure(IllegalArgumentException("Invalid hex color code"))
                }
            }

            val color = digitizingRepository.updateColor(id, tenantId, request)
                ?: return Result.failure(IllegalArgumentException("Thread color not found"))

            logger.info("Updated thread color $id")

            return Result.success(color)
        } catch (e: Exception) {
            logger.error("Failed to update thread color", e)
            return Result.failure(e)
        }
    }

    fun deleteThreadColor(id: Long, tenantId: Long): Result<Boolean> {
        try {
            val deleted = digitizingRepository.deleteColor(id, tenantId)
            if (deleted) {
                logger.info("Deleted thread color $id")
            }
            return Result.success(deleted)
        } catch (e: Exception) {
            logger.error("Failed to delete thread color", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // DIGITIZERS
    // =====================================================

    fun getDigitizers(tenantId: Long): DigitizerListResponse {
        val digitizers = digitizingRepository.findDigitizers(tenantId)
        return DigitizerListResponse(digitizers = digitizers)
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    fun getOrderStats(tenantId: Long): Map<String, Int> {
        return digitizingRepository.getOrderStats(tenantId)
    }

    // =====================================================
    // PAYMENT COMPLETION
    // =====================================================

    fun completePayment(
        orderId: Long,
        tenantId: Long,
        userId: Long,
        paymentMethod: String,
        paidAmount: Double
    ): Result<DigitizingOrder> {
        try {
            val order = digitizingRepository.findOrderById(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Digitizing order not found"))

            if (order.userId != userId) {
                return Result.failure(IllegalArgumentException("Order does not belong to this user"))
            }

            if (order.status != DigitizingStatus.PAYMENT_PENDING.code) {
                return Result.failure(IllegalArgumentException("Order is not pending payment"))
            }

            val updatedOrder = digitizingRepository.completePayment(
                orderId,
                tenantId,
                paymentMethod,
                paidAmount
            ) ?: return Result.failure(IllegalArgumentException("Failed to complete payment"))

            logger.info("Completed payment for order $orderId: method=$paymentMethod, amount=$paidAmount")

            return Result.success(updatedOrder)
        } catch (e: Exception) {
            logger.error("Failed to complete payment", e)
            return Result.failure(e)
        }
    }
}

package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// DIGITIZING STATUS
// =====================================================

enum class DigitizingStatus(val code: Int, val label: String) {
    CANCELLED(-1, "Cancelled"),
    PAYMENT_PENDING(0, "Payment Pending"),
    PENDING(1, "Pending"),
    IN_PROGRESS(2, "In Progress"),
    COMPLETED(3, "Completed"),
    REJECTED(4, "Rejected");

    companion object {
        fun fromCode(code: Int): DigitizingStatus? = entries.find { it.code == code }
        fun fromLabel(label: String): DigitizingStatus? = entries.find {
            it.label.equals(label, ignoreCase = true)
        }
    }
}

// =====================================================
// THREAD COLOR MODEL
// =====================================================

@Serializable
data class ThreadColor(
    val id: Long? = null,
    val name: String,
    val hexCode: String,
    val pantone: String? = null,
    val inStock: Boolean = true,
    val sortOrder: Int = 0
)

// =====================================================
// EMBROIDERY FILE INFO
// =====================================================

@Serializable
data class EmbroideryFileInfo(
    val stitchCount: Int,
    val width: Double,      // mm
    val height: Double,     // mm
    val colorCount: Int,
    val colors: List<ThreadColor> = emptyList(),
    val format: String? = null,     // DST, PES, JEF, etc.
    val jumpCount: Int? = null,
    val trimCount: Int? = null
)

// =====================================================
// DIGITIZING ORDER
// =====================================================

@Serializable
data class DigitizingOrder(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val digitizerId: Long? = null,      // Assigned digitizer
    val designId: Long? = null,          // Source design (PNG/image)
    val status: Int,
    val statusLabel: String,
    val name: String,
    val notes: String? = null,
    val fileUrl: String,                 // Original image file URL
    val width: Double? = null,           // Requested width in inches
    val height: Double? = null,          // Requested height in inches
    val stitchCount: Int? = null,
    val colorCount: Int? = null,
    val colors: List<ThreadColor> = emptyList(),
    val payment: DigitizingPayment? = null,
    val resultFiles: DigitizingResultFiles? = null,
    val createdAt: String,
    val updatedAt: String,
    // Computed fields
    val customerName: String? = null,
    val digitizerName: String? = null
)

@Serializable
data class DigitizingPayment(
    val paymentMethod: String? = null,      // "stripe", "balance"
    val requestedPrice: Double? = null,     // Price set by digitizer
    val userPaidAmount: Double? = null      // Amount paid by user
)

@Serializable
data class DigitizingResultFiles(
    val dstFileUrl: String? = null,
    val pdfFileUrl: String? = null,
    val pngFileUrl: String? = null          // Preview image from DST
)

// =====================================================
// DIGITIZING ORDER DETAILS (JSONB in database)
// =====================================================

@Serializable
data class DigitizingDetails(
    val name: String,
    val fileUrl: String,
    val width: Double? = null,
    val height: Double? = null,
    val orderNote: String? = null,
    val stitchCount: Int? = null,
    val colorCount: Int? = null,
    val colors: List<ThreadColor> = emptyList(),
    val payment: DigitizingPayment? = null,
    val resultFiles: DigitizingResultFiles? = null
)

// =====================================================
// DIGITIZING QUOTE REQUEST/RESPONSE
// =====================================================

@Serializable
data class DigitizingQuoteRequest(
    val designId: Long? = null,
    val fileUrl: String? = null,
    val width: Double? = null,          // inches
    val height: Double? = null,         // inches
    val estimatedStitchCount: Int? = null,
    val colorCount: Int? = null,
    val isRush: Boolean = false
)

@Serializable
data class DigitizingQuoteResponse(
    val basePrice: Double,
    val rushFee: Double = 0.0,
    val complexityFee: Double = 0.0,
    val totalPrice: Double,
    val estimatedStitchCount: Int? = null,
    val estimatedTurnaround: String,    // "24-48 hours", "3-5 business days"
    val notes: String? = null
)

// =====================================================
// CREATE/UPDATE REQUESTS
// =====================================================

@Serializable
data class CreateDigitizingOrderRequest(
    val designId: Long? = null,
    val name: String,
    val fileUrl: String,
    val width: Double? = null,
    val height: Double? = null,
    val notes: String? = null,
    val estimatedStitchCount: Int? = null,
    val colorCount: Int? = null,
    val colors: List<ThreadColor>? = null
)

@Serializable
data class UpdateDigitizingStatusRequest(
    val status: Int,
    val notes: String? = null,
    val requestedPrice: Double? = null      // For digitizer to set price
)

@Serializable
data class UploadDigitizedFileRequest(
    val dstFileUrl: String,
    val pdfFileUrl: String? = null,
    val pngFileUrl: String? = null,
    val stitchCount: Int? = null,
    val colorCount: Int? = null,
    val colors: List<ThreadColor>? = null
)

@Serializable
data class AssignDigitizerRequest(
    val digitizerId: Long
)

// =====================================================
// LIST RESPONSE
// =====================================================

@Serializable
data class DigitizingOrderListItem(
    val id: Long,
    val name: String,
    val status: Int,
    val statusLabel: String,
    val thumbnailUrl: String? = null,
    val requestedPrice: Double? = null,
    val customerName: String? = null,
    val digitizerName: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class DigitizingOrderListResponse(
    val orders: List<DigitizingOrderListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class DigitizingOrderFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val status: Int? = null,
    val digitizerId: Long? = null,
    val userId: Long? = null,
    val search: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
)

// =====================================================
// THREAD COLOR MANAGEMENT
// =====================================================

@Serializable
data class CreateThreadColorRequest(
    val name: String,
    val hexCode: String,
    val pantone: String? = null,
    val inStock: Boolean = true,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateThreadColorRequest(
    val name: String? = null,
    val hexCode: String? = null,
    val pantone: String? = null,
    val inStock: Boolean? = null,
    val sortOrder: Int? = null
)

@Serializable
data class ThreadColorListResponse(
    val colors: List<ThreadColor>,
    val total: Int
)

// =====================================================
// DIGITIZER (Employee who does digitizing work)
// =====================================================

@Serializable
data class Digitizer(
    val id: Long,
    val name: String,
    val email: String,
    val taskCount: Int = 0          // Active tasks count
)

@Serializable
data class DigitizerListResponse(
    val digitizers: List<Digitizer>
)

// =====================================================
// PAYMENT COMPLETION
// =====================================================

@Serializable
data class CompletePaymentRequest(
    val paymentMethod: String,              // "stripe" or "balance"
    val stripeSessionId: String? = null     // For Stripe payment
)

@Serializable
data class CompletePaymentResponse(
    val success: Boolean,
    val message: String,
    val orderId: Long? = null,
    val newStatus: Int? = null
)

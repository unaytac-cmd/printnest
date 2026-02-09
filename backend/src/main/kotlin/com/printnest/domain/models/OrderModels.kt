package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// ORDER STATUS ENUM
// =====================================================

enum class OrderStatus(val code: Int) {
    COMBINED(-4),           // Archived combined orders
    COMPLETED(-3),          // Archived completed orders
    INVALID_ADDRESS(-2),    // Address validation failed
    DELETED(-1),            // Soft-deleted
    NEW_ORDER(0),           // Initial state
    CANCELLED(2),           // Cancelled by user
    PAYMENT_PENDING(4),     // Awaiting payment (Step 3)
    EDITING(8),             // Re-editing after payment
    PENDING(12),            // Payment received, ready for production
    URGENT(14),             // Pending + rush processing
    AWAITING_RESPONSE(15),  // Waiting for user
    IN_PRODUCTION(16),      // Currently printing
    SHIPPED(20);            // Completed

    companion object {
        fun fromCode(code: Int): OrderStatus = entries.find { it.code == code } ?: NEW_ORDER

        val EDITABLE_STATUSES = listOf(NEW_ORDER, PAYMENT_PENDING, PENDING, URGENT, EDITING)
        val ACTIVE_STATUSES = listOf(NEW_ORDER, PAYMENT_PENDING, EDITING, PENDING, URGENT, AWAITING_RESPONSE, IN_PRODUCTION)
    }
}

// =====================================================
// ORDER MAP STATUS
// =====================================================

enum class OrderMapStatus(val code: Int) {
    UNMAPPED(0),
    COMPLETELY_MAPPED(1),
    PARTIALLY_MAPPED(2);

    companion object {
        fun fromCode(code: Int): OrderMapStatus = entries.find { it.code == code } ?: UNMAPPED
    }
}

// TransactionType is defined in WalletModels.kt

// =====================================================
// ORDER FULL DETAILS
// =====================================================

@Serializable
data class OrderFull(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val storeId: Long? = null,
    val intOrderId: String? = null,
    val externalOrderId: String? = null,
    val orderType: Int = 0,
    val orderStatus: Int = 0,
    val orderMapStatus: Int = 0,
    val orderDetail: OrderDetail? = null,
    val orderInfo: OrderInfoFull? = null,
    val priceDetail: List<PriceDetailItem> = emptyList(),
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val urgentAmount: BigDecimal = BigDecimal.ZERO,
    val giftNote: String? = null,
    val customerEmail: String? = null,
    val customerName: String? = null,
    val shippingAddress: Address? = null,
    val billingAddress: Address? = null,
    val trackingNumber: String? = null,
    val trackingUrl: String? = null,
    val paymentMethod: String? = null,
    val shipstationStoreId: Long? = null,
    val shipstationOrderId: Long? = null,
    val shippedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val products: List<OrderProductFull> = emptyList(),
    val history: List<OrderHistoryItem> = emptyList()
)

@Serializable
data class OrderDetail(
    val marketplace: String? = null,
    val marketplaceOrderId: String? = null,
    val rawData: String? = null // Original marketplace data
)

@Serializable
data class OrderInfoFull(
    val toAddress: Address? = null,
    val shipping: ShippingSelection? = null,
    val shippingOptions: List<ShippingOption> = emptyList(),
    val customsInfo: CustomsInfoFull? = null,
    val orderNote: String? = null,
    val giftNote: String? = null
)

@Serializable
data class ShippingSelection(
    val rateId: String? = null,
    val shipmentId: String? = null,
    val methodName: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val methodPrice: BigDecimal = BigDecimal.ZERO,
    val shippingId: Long? = null,
    val isInternational: Boolean = false,
    val labelUrl: String? = null,
    val trackingCode: String? = null
)

@Serializable
data class ShippingOption(
    val rateId: String? = null,
    val shipmentId: String? = null,
    val methodName: String,
    @Serializable(with = BigDecimalSerializer::class)
    val methodPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val apiPrice: BigDecimal? = null,
    val service: String? = null,
    val description: String? = null,
    val shippingId: Long? = null,
    val isInternational: Boolean = false,
    val estimatedDays: String? = null
)

@Serializable
data class CustomsInfoFull(
    val eelPfc: String = "NOEEI 30.37(a)",
    val customsCertify: Boolean = true,
    val customsSigner: String? = null,
    val contentsType: String = "merchandise",
    val contentsExplanation: String? = null,
    val restrictionType: String = "none",
    val nonDeliveryOption: String = "return",
    val customsItems: List<CustomsItem> = emptyList()
)

// CustomsItem is defined in ShippingModels.kt

// =====================================================
// ORDER PRODUCT
// =====================================================

@Serializable
data class OrderProductFull(
    val id: Long,
    val tenantId: Long,
    val orderId: Long,
    val productId: Long? = null,
    val variantId: Long? = null,
    val listingId: String? = null,
    val quantity: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    val productDetail: ProductDetailFull? = null,
    val modificationDetail: List<ModificationDetailItem> = emptyList(),
    val priceBreakdown: PriceBreakdownItem? = null,
    val designId: Long? = null,
    val mappingId: Long? = null,
    val listingImageUrl: String? = null,
    val stitchCount: Int = 0,
    val status: Int = 0,
    val createdAt: String
)

@Serializable
data class ProductDetailFull(
    val productId: Long? = null,
    val product: String? = null, // Product title
    val productCategoryId: Long? = null,
    val productCategories: String? = null, // Category name
    val option1Id: Long? = null,
    val option1: String? = null, // e.g., "Size: Large"
    val option2Id: Long? = null,
    val option2: String? = null, // e.g., "Color: Red"
    val quantity: Int = 1,
    val variants: VariantDetailItem? = null,
    val variantModification: VariantModificationDetail? = null,
    val modificationDetail: List<ModificationDetailItem> = emptyList()
)

@Serializable
data class VariantDetailItem(
    val variantId: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    val stock: Int? = null,
    val status: Int = 1
)

@Serializable
data class VariantModificationDetail(
    val variantModificationId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val depth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class ModificationDetailItem(
    val modificationId: Long,
    val modificationName: String, // e.g., "Front", "Back", "Sleeve"
    val modificationDesign: String? = null, // Design image URL
    val modificationDesignId: Long? = null,
    val modificationUseWidth: Int = 1,
    val modificationStitchCount: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal = BigDecimal.ZERO
)

// =====================================================
// PRICE DETAIL
// =====================================================

@Serializable
data class PriceDetailItem(
    val orderProductId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO, // Base product price
    val modifications: Map<String, @Serializable(with = BigDecimalSerializer::class) BigDecimal> = emptyMap(), // "Front": 2.50
    @Serializable(with = BigDecimalSerializer::class)
    val stitchPrice: BigDecimal = BigDecimal.ZERO,
    val stitchCount: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchUnitPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val afterModificationPrice: BigDecimal = BigDecimal.ZERO,
    val discountSource: String? = null, // "variants" or "priceProfile"
    val discountType: String? = null, // "percent" or "dollar"
    @Serializable(with = BigDecimalSerializer::class)
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val afterDiscountPrice: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val lineTotal: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class PriceBreakdownItem(
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val modificationTotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val stitchTotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val discount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val unitTotal: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val lineTotal: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class OrderPriceSummary(
    @Serializable(with = BigDecimalSerializer::class)
    val subtotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val giftNotePrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val urgentPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val totalPrice: BigDecimal = BigDecimal.ZERO
)

// =====================================================
// ORDER HISTORY
// =====================================================

@Serializable
data class OrderHistoryItem(
    val id: Long,
    val orderId: Long,
    val userId: Long? = null,
    val previousStatus: Int? = null,
    val newStatus: Int,
    val action: String? = null,
    val notes: String? = null,
    val createdAt: String
)

// =====================================================
// STEP 1 - ORDER CREATION
// =====================================================

@Serializable
data class CreateOrderRequest(
    val storeId: Long? = null,
    val shipstationStoreId: Long? = null,
    val externalOrderId: String? = null,
    val customerEmail: String? = null,
    val customerName: String? = null,
    val shippingAddress: Address? = null,
    val billingAddress: Address? = null,
    val orderNote: String? = null,
    val products: List<CreateOrderProductRequest> = emptyList()
)

@Serializable
data class CreateOrderProductRequest(
    val listingId: String? = null,
    val productId: Long? = null,
    val variantId: Long? = null,
    val quantity: Int = 1,
    val listingImageUrl: String? = null,
    val modificationDetail: List<ModificationDetailItem> = emptyList()
)

// =====================================================
// STEP 2 - ORDER EDITING
// =====================================================

@Serializable
data class UpdateOrderStep2Request(
    val products: List<UpdateOrderProductRequest> = emptyList(),
    val shippingAddress: Address? = null,
    val orderNote: String? = null,
    val giftNote: String? = null
)

@Serializable
data class UpdateOrderProductRequest(
    val orderProductId: Long,
    val productId: Long? = null,
    val variantId: Long? = null,
    val categoryId: Long? = null,
    val option1Id: Long? = null,
    val option2Id: Long? = null,
    val quantity: Int = 1,
    val modificationDetail: List<ModificationDetailItem> = emptyList(),
    val stitchCount: Int = 0
)

// =====================================================
// STEP 3 - PAYMENT
// =====================================================

@Serializable
data class Step3PriceResponse(
    val orderId: Long,
    val products: List<PriceDetailItem>,
    val shippingOptions: List<ShippingOption>,
    val selectedShipping: ShippingSelection? = null,
    val summary: OrderPriceSummary,
    val userBalance: @Serializable(with = BigDecimalSerializer::class) BigDecimal = BigDecimal.ZERO
)

@Serializable
data class SelectShippingRequest(
    val shippingOptionIndex: Int? = null,
    val shippingMethodId: Long? = null,
    val rateId: String? = null
)

@Serializable
data class PaymentRequest(
    val paymentMethod: String, // "balance" or "stripe"
    val isUrgent: Boolean = false,
    val hasGiftNote: Boolean = false
)

@Serializable
data class PaymentResponse(
    val success: Boolean,
    val orderId: Long,
    val newStatus: Int,
    val paymentId: Long? = null,
    val stripeSessionUrl: String? = null, // For Stripe redirect
    val message: String? = null
)

// =====================================================
// STEP 4 - CONFIRMATION
// =====================================================

@Serializable
data class Step4Response(
    val orderId: Long,
    val orderStatus: Int,
    val orderMapStatus: Int,
    val summary: OrderPriceSummary,
    val shippingInfo: ShippingSelection? = null,
    val products: List<OrderProductFull>,
    val address: Address? = null,
    val canProceed: Boolean = true,
    val issues: List<String> = emptyList()
)

@Serializable
data class ConfirmOrderRequest(
    val confirmProduction: Boolean = true,
    val notes: String? = null
)

// =====================================================
// PAYMENT & TRANSACTION
// =====================================================

@Serializable
data class Payment(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val orderId: Long? = null,
    val paymentMethod: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val status: String,
    val stripeSessionId: String? = null,
    val stripePaymentIntent: String? = null,
    val createdAt: String,
    val completedAt: String? = null
)

@Serializable
data class TransactionFull(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val type: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val description: String? = null,
    val referenceId: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceBefore: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceAfter: BigDecimal,
    val createdAt: String
)

// =====================================================
// SHIPPING LABEL
// =====================================================

@Serializable
data class ShippingLabel(
    val id: Long,
    val tenantId: Long,
    val orderId: Long,
    val carrier: String? = null,
    val service: String? = null,
    val trackingNumber: String? = null,
    val trackingUrl: String? = null,
    val labelUrl: String? = null,
    val labelFormat: String = "PDF",
    val rateId: String? = null,
    val shipmentId: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal? = null,
    val createdAt: String,
    val voidedAt: String? = null
)

@Serializable
data class CreateShippingLabelRequest(
    val rateId: String,
    val shipmentId: String
)

// =====================================================
// ORDER FILTERS
// =====================================================

@Serializable
data class OrderFiltersExtended(
    val status: Int? = null,
    val statuses: List<Int>? = null,
    val mapStatus: Int? = null,
    val storeId: Long? = null,
    val shipstationStoreId: Long? = null,
    val userId: Long? = null,
    val search: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 1,
    val limit: Int = 50,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
)

@Serializable
data class OrderListResponse(
    val orders: List<OrderFull>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// =====================================================
// ORDER ACTIONS
// =====================================================

@Serializable
data class ChangeStatusRequest(
    val newStatus: Int,
    val notes: String? = null
)

// CombineOrdersRequest is defined in BatchModels.kt

@Serializable
data class BulkActionRequest(
    val orderIds: List<Long>,
    val action: String, // "delete", "cancel", "mark_shipped"
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class ShipOrderRequest(
    val trackingNumber: String? = null,
    val trackingUrl: String? = null,
    val carrier: String? = null,
    val service: String? = null
)

// =====================================================
// PRODUCT SELECTION DATA (Manual Order)
// =====================================================

@Serializable
data class ProductSelectionData(
    val categories: List<CategoryForSelection>,
    val products: List<ProductForSelection>,
    val option1s: List<Option1ForSelection>,
    val option2s: List<Option2ForSelection>,
    val variants: List<VariantForSelection>,
    val variantModifications: List<VariantModificationForSelection>,
    val modifications: List<ModificationForSelection>
)

@Serializable
data class CategoryForSelection(
    val id: Long,
    val name: String,
    val parentCategoryId: Long? = null,
    val isHeavy: Boolean = false
)

@Serializable
data class ProductForSelection(
    val id: Long,
    val categoryId: Long?,
    val title: String,
    val designType: Int,
    val option1Name: String? = null,
    val option2Name: String? = null
)

@Serializable
data class Option1ForSelection(
    val id: Long,
    val productId: Long,
    val name: String,
    val sortOrder: Int = 0
)

@Serializable
data class Option2ForSelection(
    val id: Long,
    val productId: Long,
    val name: String,
    val hexColor: String? = null,
    val isDark: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class VariantForSelection(
    val id: Long,
    val productId: Long,
    val option1Id: Long? = null,
    val option2Id: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null,
    val inStock: Boolean = true,
    val inHouse: Boolean = false,
    val status: Int = 1
)

@Serializable
data class VariantModificationForSelection(
    val id: Long,
    val productId: Long,
    val option1Id: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val depth: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class ModificationForSelection(
    val id: Long,
    val categoryId: Long,
    val name: String,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal = BigDecimal.ZERO,
    val useWidth: Int = 1
)

// =====================================================
// EXCEL IMPORT
// =====================================================

@Serializable
data class SimpleExcelImportResult(
    val success: Boolean,
    val ordersCreated: Int = 0,
    val ordersWithErrors: Int = 0,
    val errors: List<SimpleExcelImportError> = emptyList(),
    val message: String? = null
)

@Serializable
data class SimpleExcelImportError(
    val rowNumber: Int,
    val customerName: String? = null,
    val errorMessage: String
)

// Type aliases for backward compatibility
typealias ExcelImportResult = SimpleExcelImportResult
typealias ExcelImportError = SimpleExcelImportError

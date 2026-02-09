package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// EXPORT TYPE ENUM
// =====================================================

enum class ExportType(val code: String) {
    ORDERS("orders"),
    PRODUCTS("products"),
    TRANSACTIONS("transactions"),
    DESIGNS("designs"),
    OUT_OF_STOCK("out_of_stock");

    companion object {
        fun fromCode(code: String): ExportType = entries.find { it.code == code } ?: ORDERS
    }
}

// =====================================================
// EXPORT STATUS ENUM
// =====================================================

enum class ExportStatus(val code: Int) {
    PENDING(0),
    PROCESSING(1),
    COMPLETED(2),
    FAILED(-1);

    companion object {
        fun fromCode(code: Int): ExportStatus = entries.find { it.code == code } ?: PENDING
    }
}

// =====================================================
// EXPORT REQUEST/RESPONSE
// =====================================================

@Serializable
data class ExportRequest(
    val type: String, // orders, products, transactions, designs, out_of_stock
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val filters: ExportFilters? = null,
    val columns: List<String>? = null, // Optional: specific columns to export
    val format: String = "xlsx" // xlsx, csv
)

@Serializable
data class ExportFilters(
    val startDate: String? = null,
    val endDate: String? = null,
    val status: Int? = null,
    val statuses: List<Int>? = null,
    val storeId: Long? = null,
    val userId: Long? = null,
    val productId: Long? = null,
    val categoryId: Long? = null,
    val search: String? = null,
    val includeDeleted: Boolean = false
)

@Serializable
data class ExportResponse(
    val success: Boolean,
    val exportId: Long? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val recordCount: Int = 0,
    val message: String? = null
)

@Serializable
data class ExportDownloadResponse(
    val success: Boolean,
    val downloadUrl: String? = null,
    val fileName: String? = null,
    val expiresAt: String? = null,
    val message: String? = null
)

// =====================================================
// EXPORT HISTORY
// =====================================================

@Serializable
data class ExportHistory(
    val id: Long,
    val tenantId: Long,
    val userId: Long,
    val exportType: String,
    val fileName: String,
    val fileUrl: String? = null,
    val recordCount: Int,
    val status: Int,
    val errorMessage: String? = null,
    val filters: String? = null, // JSON string of filters used
    val createdAt: String,
    val completedAt: String? = null
)

@Serializable
data class ExportHistoryListResponse(
    val exports: List<ExportHistory>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// =====================================================
// ORDER EXPORT DATA
// =====================================================

@Serializable
data class OrderExportRow(
    val orderId: Long,
    val intOrderId: String?,
    val customerName: String?,
    val storeName: String?,
    val orderDate: String,
    val productCategory: String?,
    val productName: String?,
    val quantity: Int,
    val option1: String?,
    val option2: String?,
    val modifications: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val productAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingPaid: BigDecimal?,
    val orderNote: String?,
    val orderStatus: Int
)

@Serializable
data class OrderSummaryRow(
    val orderId: Long,
    val customerName: String?,
    val orderDate: String,
    @Serializable(with = BigDecimalSerializer::class)
    val productAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingPaid: BigDecimal?,
    val storeName: String?
)

@Serializable
data class ProductStatsRow(
    val productName: String,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal
)

@Serializable
data class VariationStatsRow(
    val productName: String,
    val option1: String?,
    val option2: String?,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal
)

// =====================================================
// OUT OF STOCK EXPORT DATA
// =====================================================

@Serializable
data class OutOfStockProductRow(
    val productTitle: String,
    val option1: String?,
    val option2: String?,
    val supplierName: String?,
    val quantity: Int
)

@Serializable
data class OutOfStockOrderDataRow(
    val orderId: Long,
    val productTitle: String,
    val option1: String?,
    val option2: String?,
    val quantity: Int,
    val customerName: String?
)

// =====================================================
// TRANSACTION EXPORT DATA
// =====================================================

@Serializable
data class TransactionExportRow(
    val transactionId: Long,
    val userId: Long,
    val customerName: String?,
    val type: String,
    val typeCode: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val description: String?,
    val referenceId: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceBefore: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val balanceAfter: BigDecimal,
    val createdAt: String
)

// =====================================================
// DESIGN EXPORT DATA
// =====================================================

@Serializable
data class DesignExportRow(
    val designId: Long,
    val title: String,
    val designType: Int,
    val designTypeName: String,
    val designUrl: String,
    val width: Double?,
    val height: Double?,
    val usageCount: Int,
    val createdAt: String,
    val status: Int
)

// =====================================================
// PRODUCT EXPORT DATA
// =====================================================

@Serializable
data class ProductExportRow(
    val productId: Long,
    val title: String,
    val categoryName: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal,
    val option1Name: String?,
    val option2Name: String?,
    val variantCount: Int,
    val inStockCount: Int,
    val outOfStockCount: Int,
    val designType: String,
    val status: Int,
    val createdAt: String
)

@Serializable
data class VariantExportRow(
    val variantId: Long,
    val productId: Long,
    val productTitle: String,
    val option1Value: String?,
    val option2Value: String?,
    val sku: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal,
    val weight: Double?,
    val inStock: Boolean,
    val stockQuantity: Int?
)

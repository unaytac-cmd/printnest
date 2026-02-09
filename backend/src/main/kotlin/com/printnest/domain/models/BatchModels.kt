package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// BATCH STATUS ENUM
// =====================================================

enum class BatchStatus(val code: Int, val label: String) {
    DRAFT(0, "Draft"),
    READY(1, "Ready"),
    PROCESSING(2, "Processing"),
    COMPLETED(3, "Completed"),
    DELETED(-1, "Deleted");

    companion object {
        fun fromCode(code: Int): BatchStatus = entries.find { it.code == code } ?: DRAFT
    }
}

// =====================================================
// BATCH DATA CLASSES
// =====================================================

@Serializable
data class Batch(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val status: Int,
    val statusLabel: String,
    val orderIds: List<Long>,
    val gangsheetId: Long? = null,
    val orderCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val orders: List<BatchOrderInfo> = emptyList()
)

@Serializable
data class BatchOrderInfo(
    val orderId: Long,
    val intOrderId: String? = null,
    val externalOrderId: String? = null,
    val customerName: String? = null,
    val customerEmail: String? = null,
    val orderStatus: Int = 0,
    val orderStatusLabel: String? = null,
    val totalAmount: @Serializable(with = BigDecimalSerializer::class) java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val productCount: Int = 0,
    val createdAt: String? = null
)

@Serializable
data class BatchListItem(
    val id: Long,
    val name: String,
    val status: Int,
    val statusLabel: String,
    val orderCount: Int,
    val gangsheetId: Long? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class BatchListResponse(
    val batches: List<BatchListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// =====================================================
// BATCH HISTORY
// =====================================================

@Serializable
data class BatchHistoryItem(
    val id: Long,
    val batchId: Long,
    val userId: Long? = null,
    val previousStatus: Int? = null,
    val newStatus: Int,
    val action: String? = null,
    val notes: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: String
)

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class CreateBatchRequest(
    val name: String,
    val orderIds: List<Long> = emptyList()
)

@Serializable
data class UpdateBatchRequest(
    val name: String? = null,
    val status: Int? = null
)

@Serializable
data class AddOrdersToBatchRequest(
    val orderIds: List<Long>
)

@Serializable
data class RemoveOrderFromBatchRequest(
    val orderId: Long
)

@Serializable
data class CombineBatchOrdersRequest(
    val orderIds: List<Long>,
    val primaryOrderId: Long? = null // Optional: which order to keep as base
)

@Serializable
data class CombineOrdersRequest(
    val orderIds: List<Long>
)

@Serializable
data class CombineOrdersResponse(
    val success: Boolean,
    val combinedOrderId: Long,
    val originalOrderIds: List<Long>,
    val message: String? = null
)

@Serializable
data class ProcessBatchRequest(
    val gangsheetSettings: GangsheetSettingsFull? = null // Optional: override tenant defaults
)

@Serializable
data class ProcessBatchResponse(
    val batchId: Long,
    val gangsheetId: Long,
    val status: Int,
    val message: String? = null
)

@Serializable
data class BatchResponse(
    val batch: Batch,
    val message: String? = null
)

// =====================================================
// BATCH FILTERS
// =====================================================

@Serializable
data class BatchFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val status: Int? = null,
    val search: String? = null,
    val hasGangsheet: Boolean? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
)

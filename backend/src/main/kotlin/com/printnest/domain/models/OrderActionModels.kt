package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// FETCH ORDER FROM MARKETPLACE
// =====================================================

@Serializable
data class FetchOrderRequest(
    val storeId: Long,
    val intOrderId: String
)

@Serializable
data class FetchOrderResponse(
    val success: Boolean,
    val orderId: Long? = null,
    val step3: Boolean = false,
    val message: String? = null
)

// =====================================================
// BULK ORDER ACTIONS
// =====================================================

@Serializable
data class BulkStatusUpdateRequest(
    val orderIds: List<Long>,
    val status: String // shipped, editing, pending, urgent, cancelled, awaitingresponse, inproduction
)

@Serializable
data class BulkCancelRequest(
    val orderIds: List<Long>,
    val refundLabels: Boolean = false
)

// CombineOrdersRequest/Response defined in BatchModels.kt

// =====================================================
// GANGSHEET CREATION (Order-specific)
// =====================================================

@Serializable
data class OrderGangsheetRequest(
    val orderIds: List<Long>,
    val gangType: String = "order", // order, product, batch
    val gangsheetName: String? = null,
    val products: List<GangsheetProductInput> = emptyList(),
    val batchId: Long? = null
)

@Serializable
data class GangsheetProductInput(
    val orderProductId: Long,
    val modificationName: String,
    val designUrl: String
)

@Serializable
data class OrderGangsheetResponse(
    val success: Boolean,
    val gangsheetId: Long? = null,
    val serverIpAddress: String? = null,
    val downloadUrl: String? = null,
    val message: String? = null
)

@Serializable
data class OrderGangsheetStatusResponse(
    val success: Boolean,
    val phase: String? = null,
    val percentage: String? = null,
    val fileUrl: String? = null,
    val message: String? = null
)

// =====================================================
// PACKING SLIPS
// =====================================================

@Serializable
data class CreatePackingSlipsRequest(
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val includeLabels: Boolean = false,
    val groupByModification: Boolean = false
)

@Serializable
data class PackingSlipResponse(
    val success: Boolean,
    val fileUrl: String? = null,
    val message: String? = null
)

@Serializable
data class ProcessedOrder(
    val orderId: Long,
    val orderStatus: Int,
    val orderDate: String,
    val intOrderId: String?,
    val labelUrl: String?,
    val trackingCode: String?,
    val labelSize: String,
    val products: List<OrderPackingSlipProduct>,
    val customerName: String?,
    val storeName: String?,
    val batches: String,
    val orderNote: String?,
    val giftNote: String?,
    val includeLabel: Boolean
)

@Serializable
data class OrderPackingSlipProduct(
    val title: String,
    val option1: String?,
    val option2: String?,
    val quantity: Int,
    val modifications: List<OrderPackingSlipModification>
)

@Serializable
data class OrderPackingSlipModification(
    val modificationId: Long? = null,
    val modificationName: String,
    val modificationDesign: String? = null,
    val modificationDesignId: Long? = null,
    val modificationDesignType: Int? = null,
    val modificationDesignDetails: Map<String, String>? = null,
    val modificationFileName: String? = null
)

// =====================================================
// EXPORT ORDERS
// =====================================================

@Serializable
data class ExportOrdersRequest(
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val exportType: String = "order" // order, batch
)

@Serializable
data class ExportLabelsRequest(
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val exportType: String = "order"
)

@Serializable
data class ExportLabelsResponse(
    val success: Boolean,
    val fileUrl: String? = null,
    val message: String? = null
)

@Serializable
data class DownloadDesignsRequest(
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val exportType: String = "order"
)

// =====================================================
// SHIPPING LABEL ACTIONS
// =====================================================

@Serializable
data class UploadLabelRequest(
    val orderId: Long,
    val labelUrl: String
)

@Serializable
data class UpdateTrackingRequest(
    val orderId: Long,
    val trackingCode: String
)

@Serializable
data class RequestRefundRequest(
    val orderId: Long
)

@Serializable
data class OrderRefundResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class SendTrackingRequest(
    val orderId: Long
)

@Serializable
data class SendTrackingResponse(
    val results: List<SendTrackingResult>
)

@Serializable
data class SendTrackingResult(
    val success: Boolean,
    val orderId: Long,
    val message: String,
    val trackingCode: String? = null,
    val warn: Boolean = false
)

// =====================================================
// SHIPPING METHOD SELECTION
// =====================================================

@Serializable
data class ShippingMethodItem(
    val rateId: String,
    val methodName: String,
    val selected: Boolean = false
)

@Serializable
data class GetShippingMethodsResponse(
    val success: Boolean,
    val shippingMethods: List<ShippingMethodItem> = emptyList(),
    val message: String? = null
)

@Serializable
data class SetShippingMethodRequest(
    val orderId: Long,
    val rateId: String,
    val methodName: String
)

// =====================================================
// REFUND MANAGEMENT
// =====================================================

@Serializable
data class UpdateRefundRequest(
    val orderId: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val refundAmount: BigDecimal
)

@Serializable
data class RefundItem(
    @Serializable(with = BigDecimalSerializer::class)
    val refundAmount: BigDecimal
)

@Serializable
data class GetRefundsResponse(
    val success: Boolean,
    val refunds: List<RefundItem> = emptyList()
)

// =====================================================
// SYNC ORDERS FROM MARKETPLACE
// =====================================================

@Serializable
data class SyncOrdersRequest(
    val storeId: String? = null, // "all" or specific store ID
    val lastDays: Int = 7,
    val marketplaceId: Int? = null
)

@Serializable
data class SyncOrdersResponse(
    val success: Boolean,
    val message: String,
    val syncedCount: Int = 0
)

// =====================================================
// ORDER ANALYTICS
// =====================================================

@Serializable
data class OrderAnalyticsRequest(
    val userId: Long? = null,
    val storeId: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

@Serializable
data class OrderAnalyticsData(
    val storeName: String,
    val orderDate: String,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmountPerDay: BigDecimal
)

@Serializable
data class OrderAnalyticsResponse(
    val success: Boolean,
    val orders: List<OrderAnalyticsData> = emptyList()
)

// =====================================================
// EXCEL IMPORT
// =====================================================

@Serializable
data class OrderExcelImportResult(
    val success: Boolean,
    val message: String,
    val successfulOrders: List<ImportedOrderInfo> = emptyList(),
    val failedOrders: List<FailedOrderInfo> = emptyList(),
    val errorFileUrl: String? = null,
    val validOrdersCount: Int = 0,
    val problematicOrdersCount: Int = 0,
    val totalProcessed: Int = 0,
    val successRate: Int = 0
)

@Serializable
data class ImportedOrderInfo(
    val intOrderId: String,
    val orderDetail: String? = null
)

@Serializable
data class FailedOrderInfo(
    val orderDetail: String,
    val errorReason: String,
    val productsCount: Int = 0,
    val failedStep: String? = null
)

// =====================================================
// ORNAMENT GANGSHEET
// =====================================================

@Serializable
data class CreateOrnamentGangsheetRequest(
    val orderIds: List<Long>
)

@Serializable
data class OrnamentDesign(
    val designId: Long,
    val orderId: Long,
    val quantity: Int,
    val modificationName: String,
    val designUrl: String? = null,
    val title: String? = null,
    val template: String = "circle"
)

// =====================================================
// ORDER PRODUCTS FOR GANGSHEET
// =====================================================

@Serializable
data class GangsheetOrderProduct(
    val orderId: Long,
    val orderProductId: Long,
    val productDetail: ProductDetailFull?,
    val modifications: List<GangsheetModification>
)

@Serializable
data class GangsheetModification(
    val modificationName: String,
    val designUrl: String?
)

// =====================================================
// OUT OF STOCK EXPORT
// =====================================================

@Serializable
data class ExportOutOfStockRequest(
    val orderIds: List<Long>? = null,
    val batchId: Long? = null,
    val exportType: String = "order"
)

@Serializable
data class OutOfStockProduct(
    val title: String,
    val option1: String,
    val option2: String,
    val supplierName: String,
    val quantity: Int
)

// =====================================================
// STORE LIST BY MARKETPLACE
// =====================================================

@Serializable
data class StoreByMarketplace(
    val storeId: Long,
    val storeName: String,
    val marketplaceId: Int,
    val marketplaceName: String
)

package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// PACKING SLIP DATA
// =====================================================

@Serializable
data class PackingSlipData(
    val orderId: Long,
    val intOrderId: String?,
    val externalOrderId: String?,
    val orderDate: String,
    val customer: CustomerInfo,
    val shippingAddress: Address?,
    val products: List<PackingSlipProduct>,
    val orderNote: String? = null,
    val giftNote: String? = null,
    val trackingNumber: String? = null,
    val trackingUrl: String? = null,
    val storeName: String? = null,
    val batchNames: List<String> = emptyList(),
    val companyInfo: CompanyInfo? = null,
    val includeLabel: Boolean = false,
    val labelUrl: String? = null,
    val labelSize: String = "4x6"
)

@Serializable
data class PackingSlipProduct(
    val title: String,
    val option1: String? = null,
    val option2: String? = null,
    val quantity: Int,
    val modifications: List<PackingSlipModification> = emptyList(),
    val thumbnailUrl: String? = null
)

@Serializable
data class PackingSlipModification(
    val name: String,
    val designUrl: String? = null,
    val stitchCount: Int = 0
)

@Serializable
data class CustomerInfo(
    val name: String,
    val email: String? = null,
    val phone: String? = null
)

@Serializable
data class CompanyInfo(
    val name: String,
    val logoUrl: String? = null,
    val address: Address? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val taxId: String? = null
)

// =====================================================
// INVOICE DATA
// =====================================================

@Serializable
data class InvoiceData(
    val invoiceNumber: String,
    val invoiceDate: String,
    val dueDate: String? = null,
    val orderId: Long,
    val intOrderId: String?,
    val customer: CustomerInfo,
    val billingAddress: Address?,
    val shippingAddress: Address?,
    val lineItems: List<InvoiceLineItem>,
    val subtotal: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val shippingAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val taxAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val discountAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal = BigDecimal.ZERO,
    val totalAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val companyInfo: CompanyInfo? = null,
    val notes: String? = null,
    val paymentTerms: String? = null,
    val paymentStatus: String? = null
)

@Serializable
data class InvoiceLineItem(
    val description: String,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val unitPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalPrice: BigDecimal,
    val productId: Long? = null,
    val sku: String? = null
)

// =====================================================
// BULK INVOICE DATA (Period Summary)
// =====================================================

@Serializable
data class BulkInvoiceData(
    val invoiceNumber: String,
    val invoiceDate: String,
    val periodStart: String,
    val periodEnd: String,
    val customer: CustomerInfo,
    val billingAddress: Address?,
    val orders: List<BulkInvoiceOrderSummary>,
    val netAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val shippingAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val taxAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal = BigDecimal.ZERO,
    val totalAmount: @Serializable(with = BigDecimalSerializer::class) BigDecimal,
    val companyInfo: CompanyInfo? = null
)

@Serializable
data class BulkInvoiceOrderSummary(
    val orderId: Long,
    val intOrderId: String?,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val productTotal: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val shippingAmount: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalAmount: BigDecimal
)

// =====================================================
// SHIPPING LABEL DATA
// =====================================================

@Serializable
data class LabelData(
    val orderId: Long,
    val intOrderId: String?,
    val fromAddress: Address,
    val toAddress: Address,
    val carrier: String? = null,
    val service: String? = null,
    val trackingNumber: String? = null,
    val trackingBarcode: String? = null,
    val weight: Double? = null,
    val dimensions: PackageDimensions? = null,
    val labelFormat: String = "PDF",
    val labelSize: String = "4x6"
)

@Serializable
data class PackageDimensions(
    val length: Double,
    val width: Double,
    val height: Double,
    val unit: String = "in"
)

// =====================================================
// PDF GENERATION REQUEST/RESPONSE
// =====================================================

@Serializable
data class PdfGenerationRequest(
    val type: PdfType,
    val orderId: Long? = null,
    val orderIds: List<Long>? = null,
    val options: PdfGenerationOptions = PdfGenerationOptions()
)

@Serializable
enum class PdfType {
    PACKING_SLIP,
    INVOICE,
    BULK_INVOICE,
    SHIPPING_LABEL,
    BULK_PACKING_SLIPS
}

@Serializable
data class PdfGenerationOptions(
    val includeLabel: Boolean = false,
    val includeProductThumbnails: Boolean = true,
    val includeQrCode: Boolean = true,
    val includeBarcode: Boolean = false,
    val groupByModification: Boolean = false,
    val paperSize: String = "A4",  // A4, Letter, 4x6
    val orientation: String = "portrait"  // portrait, landscape
)

@Serializable
data class PdfGenerationResponse(
    val success: Boolean,
    val fileId: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val message: String? = null,
    val generatedAt: String? = null
)

@Serializable
data class BulkPdfGenerationResponse(
    val success: Boolean,
    val files: List<PdfFileInfo> = emptyList(),
    val zipFileUrl: String? = null,
    val totalGenerated: Int = 0,
    val totalFailed: Int = 0,
    val errors: List<String> = emptyList(),
    val message: String? = null
)

@Serializable
data class PdfFileInfo(
    val orderId: Long,
    val fileId: String,
    val fileUrl: String,
    val fileName: String
)

// =====================================================
// BULK PACKING SLIP REQUEST
// =====================================================

@Serializable
data class BulkPackingSlipRequest(
    val orderIds: List<Long>,
    val includeLabels: Boolean = false,
    val groupByModification: Boolean = false,
    val generateZip: Boolean = true
)

// =====================================================
// PDF DOWNLOAD INFO
// =====================================================

@Serializable
data class PdfDownloadInfo(
    val fileId: String,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val contentType: String = "application/pdf",
    val expiresAt: String? = null
)

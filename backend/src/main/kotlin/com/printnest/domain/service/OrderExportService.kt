package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.ExportRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.BatchRepository
import com.printnest.integrations.aws.S3Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for handling order exports including:
 * - Excel export of orders with products and stats
 * - Out of stock products export
 * - Design downloads as ZIP
 * - Packing slip PDF generation
 */
class OrderExportService(
    private val orderRepository: OrderRepository,
    private val exportRepository: ExportRepository,
    private val batchRepository: BatchRepository,
    private val excelService: ExcelService,
    private val pdfService: PdfService,
    private val s3Service: S3Service,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(OrderExportService::class.java)

    companion object {
        private const val DESIGNS_PREFIX = "exports/designs"
        private const val MAX_PARALLEL_DOWNLOADS = 10
    }

    // =====================================================
    // EXPORT ORDERS TO EXCEL
    // =====================================================

    /**
     * Export orders to Excel with products, order summary, and product statistics.
     * Creates multiple sheets: Products, Orders, Product Stats, Variation Stats.
     *
     * Reference: TeeDropV2 create_excel function
     */
    suspend fun exportOrdersToExcel(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<ExportResponse> = withContext(Dispatchers.IO) {
        try {
            logger.info("Exporting ${orderIds.size} orders to Excel for tenant $tenantId")

            if (orderIds.isEmpty()) {
                return@withContext Result.success(
                    ExportResponse(
                        success = false,
                        message = "No order IDs provided"
                    )
                )
            }

            // Use existing ExcelService method
            val result = excelService.createOrdersExport(tenantId, userId, orderIds)

            result.onFailure { e ->
                logger.error("Failed to export orders to Excel", e)
            }

            result
        } catch (e: Exception) {
            logger.error("Error exporting orders to Excel", e)
            Result.failure<ExportResponse>(e)
        }
    }

    // =====================================================
    // EXPORT OUT OF STOCK PRODUCTS
    // =====================================================

    /**
     * Export out of stock products from selected orders.
     * Creates an Excel with aggregated OOS products and raw order data.
     *
     * Reference: TeeDropV2 export_oos_products function
     */
    suspend fun exportOutOfStockProducts(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<ExportResponse> = withContext(Dispatchers.IO) {
        try {
            logger.info("Exporting OOS products for ${orderIds.size} orders, tenant $tenantId")

            if (orderIds.isEmpty()) {
                return@withContext Result.success(
                    ExportResponse(
                        success = false,
                        message = "No order IDs provided"
                    )
                )
            }

            // Use existing ExcelService method
            val result = excelService.createOutOfStockExport(tenantId, userId, orderIds)

            result.onFailure { e ->
                logger.error("Failed to export OOS products", e)
            }

            result
        } catch (e: Exception) {
            logger.error("Error exporting OOS products", e)
            Result.failure<ExportResponse>(e)
        }
    }

    // =====================================================
    // DOWNLOAD DESIGNS AS ZIP
    // =====================================================

    /**
     * Download designs for selected orders as a ZIP file.
     * Downloads all modification designs from S3 and packages them.
     *
     * Filename format based on design type:
     * - DST/AI: "{orderId} {productCounter} {modCounter} {modName}.{ext}"
     * - Others: "{orderId}_{productCounter}_{modCounter}_{modName}_{product}_{options}_x{qty}.{ext}"
     *
     * Reference: TeeDropV2 process_designs_to_download function
     */
    suspend fun downloadDesigns(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<DesignDownloadResponse> = coroutineScope {
        try {
            logger.info("Downloading designs for ${orderIds.size} orders, tenant $tenantId")

            if (orderIds.isEmpty()) {
                return@coroutineScope Result.success(
                    DesignDownloadResponse(
                        success = false,
                        message = "No order IDs provided"
                    )
                )
            }

            // Fetch all orders with products
            val orders = orderIds.mapNotNull { orderId ->
                orderRepository.findByIdWithProducts(orderId, tenantId)
            }

            if (orders.isEmpty()) {
                return@coroutineScope Result.success(
                    DesignDownloadResponse(
                        success = false,
                        message = "No orders found"
                    )
                )
            }

            // Build list of designs to download
            val designsToDownload = mutableListOf<DesignDownloadItem>()
            var lastOrderId: Long? = null
            var lastProductId: Long? = null
            var productCounter = 1
            var modCounter = 1

            orders.sortedBy { it.id }.forEach { order ->
                order.products.sortedBy { it.id }.forEach { product ->
                    product.modificationDetail.forEach { mod ->
                        // Update counters based on order/product changes
                        if (order.id != lastOrderId) {
                            productCounter = 1
                            modCounter = 1
                        } else if (product.id != lastProductId) {
                            productCounter++
                            modCounter = 1
                        } else {
                            modCounter++
                        }

                        lastOrderId = order.id
                        lastProductId = product.id

                        val designUrl = mod.modificationDesign
                        if (!designUrl.isNullOrBlank()) {
                            designsToDownload.add(
                                DesignDownloadItem(
                                    orderId = order.id,
                                    orderProductId = product.id,
                                    productCounter = productCounter,
                                    modCounter = modCounter,
                                    modificationName = mod.modificationName,
                                    productName = product.productDetail?.product ?: "Unknown",
                                    option1 = product.productDetail?.option1 ?: "",
                                    option2 = product.productDetail?.option2 ?: "",
                                    quantity = product.quantity,
                                    designUrl = designUrl,
                                    printSize = product.productDetail?.variants?.width1?.toString()
                                )
                            )
                        }
                    }
                }
            }

            if (designsToDownload.isEmpty()) {
                return@coroutineScope Result.success(
                    DesignDownloadResponse(
                        success = false,
                        message = "No designs found in the selected orders"
                    )
                )
            }

            logger.info("Found ${designsToDownload.size} designs to download")

            // Download designs in parallel with limited concurrency
            val downloadedFiles = mutableMapOf<String, ByteArray>()
            val failedDownloads = mutableListOf<String>()

            designsToDownload.chunked(MAX_PARALLEL_DOWNLOADS).forEach { chunk ->
                val deferredDownloads = chunk.map { design ->
                    async(Dispatchers.IO) {
                        try {
                            val bytes = downloadDesignFromS3(tenantId, design.designUrl)
                            if (bytes != null) {
                                val fileName = generateDesignFileName(design)
                                Pair(fileName, bytes)
                            } else {
                                failedDownloads.add("Order ${design.orderId}: ${design.modificationName}")
                                null
                            }
                        } catch (e: Exception) {
                            logger.warn("Failed to download design: ${design.designUrl}", e)
                            failedDownloads.add("Order ${design.orderId}: ${design.modificationName}")
                            null
                        }
                    }
                }

                deferredDownloads.awaitAll().filterNotNull().forEach { (fileName, bytes) ->
                    downloadedFiles[fileName] = bytes
                }
            }

            if (downloadedFiles.isEmpty()) {
                return@coroutineScope Result.success(
                    DesignDownloadResponse(
                        success = false,
                        message = "Failed to download any designs"
                    )
                )
            }

            // Create ZIP file
            val zipBytes = createZipFile(downloadedFiles)

            // Upload to S3
            val timestamp = System.currentTimeMillis()
            val zipFileName = "designs_${timestamp}.zip"
            val zipKey = "$DESIGNS_PREFIX/$tenantId/$zipFileName"

            val zipUrl = s3Service.uploadBytes(
                tenantId = tenantId,
                bytes = zipBytes,
                key = zipKey,
                contentType = "application/zip",
                fileName = zipFileName
            )

            // Generate presigned download URL
            val downloadUrl = s3Service.generateDownloadUrl(tenantId, zipKey, 3600) // 1 hour expiry

            // Record export history
            exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = "designs",
                fileName = zipFileName,
                fileUrl = zipUrl,
                recordCount = downloadedFiles.size,
                status = ExportStatus.COMPLETED.code,
                filters = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
                    orderIds
                )
            )

            logger.info("Design download completed: ${downloadedFiles.size} files, ${failedDownloads.size} failed")

            Result.success(
                DesignDownloadResponse(
                    success = true,
                    downloadUrl = downloadUrl,
                    fileName = zipFileName,
                    fileCount = downloadedFiles.size,
                    failedCount = failedDownloads.size,
                    failedDesigns = failedDownloads.take(10), // Limit error messages
                    message = if (failedDownloads.isEmpty())
                        "Successfully downloaded ${downloadedFiles.size} designs"
                    else
                        "Downloaded ${downloadedFiles.size} designs, ${failedDownloads.size} failed"
                )
            )
        } catch (e: Exception) {
            logger.error("Error downloading designs", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CREATE PACKING SLIPS
    // =====================================================

    /**
     * Create packing slip PDFs for selected orders.
     * Optionally includes shipping labels and groups by modification type.
     *
     * Reference: TeeDropV2 process_orders_for_packing_slip function
     */
    suspend fun createPackingSlips(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>,
        includeLabels: Boolean = false,
        groupByModification: Boolean = false
    ): Result<BulkPdfGenerationResponse> {
        return try {
            logger.info("Creating packing slips for ${orderIds.size} orders, tenant $tenantId")

            if (orderIds.isEmpty()) {
                return Result.success(
                    BulkPdfGenerationResponse(
                        success = false,
                        message = "No order IDs provided"
                    )
                )
            }

            // Use existing PdfService for bulk packing slip generation
            val options = PdfGenerationOptions(
                includeLabel = includeLabels,
                groupByModification = groupByModification,
                includeProductThumbnails = true,
                includeQrCode = true
            )

            val result = pdfService.generateBulkPackingSlips(
                orderIds = orderIds,
                tenantId = tenantId,
                options = options,
                generateZip = true
            )

            result.onSuccess { response ->
                // Record export history
                exportRepository.createExportHistory(
                    tenantId = tenantId,
                    userId = userId,
                    exportType = "packing_slips",
                    fileName = "packing-slips-${System.currentTimeMillis()}.zip",
                    fileUrl = response.zipFileUrl,
                    recordCount = response.totalGenerated,
                    status = if (response.totalFailed == 0) ExportStatus.COMPLETED.code else ExportStatus.COMPLETED.code,
                    filters = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
                        orderIds
                    )
                )
            }

            result
        } catch (e: Exception) {
            logger.error("Error creating packing slips", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // EXPORT SHIPPING LABELS
    // =====================================================

    /**
     * Export shipping labels for selected orders as a combined PDF or ZIP.
     */
    suspend fun exportShippingLabels(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<ExportLabelsResponse> = coroutineScope {
        try {
            logger.info("Exporting shipping labels for ${orderIds.size} orders, tenant $tenantId")

            if (orderIds.isEmpty()) {
                return@coroutineScope Result.success(
                    ExportLabelsResponse(
                        success = false,
                        message = "No order IDs provided"
                    )
                )
            }

            // Fetch orders and collect label URLs
            val labelUrls = mutableListOf<Pair<Long, String>>()

            orderIds.forEach { orderId ->
                val order = orderRepository.findByIdWithProducts(orderId, tenantId)
                if (order != null) {
                    val labelUrl = order.orderInfo?.shipping?.labelUrl
                    if (!labelUrl.isNullOrBlank()) {
                        labelUrls.add(Pair(orderId, labelUrl))
                    }
                }
            }

            if (labelUrls.isEmpty()) {
                return@coroutineScope Result.success(
                    ExportLabelsResponse(
                        success = false,
                        message = "No shipping labels found for the selected orders"
                    )
                )
            }

            // Download labels and create ZIP
            val downloadedLabels = mutableMapOf<String, ByteArray>()

            labelUrls.forEach { (orderId, labelUrl) ->
                try {
                    val labelBytes = downloadDesignFromS3(tenantId, labelUrl)
                    if (labelBytes != null) {
                        val extension = labelUrl.substringAfterLast(".").lowercase()
                            .takeIf { it in listOf("pdf", "png", "jpg", "jpeg") } ?: "pdf"
                        downloadedLabels["label_$orderId.$extension"] = labelBytes
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to download label for order $orderId", e)
                }
            }

            if (downloadedLabels.isEmpty()) {
                return@coroutineScope Result.success(
                    ExportLabelsResponse(
                        success = false,
                        message = "Failed to download any labels"
                    )
                )
            }

            // Create ZIP
            val zipBytes = createZipFile(downloadedLabels)

            // Upload to S3
            val timestamp = System.currentTimeMillis()
            val zipFileName = "shipping_labels_$timestamp.zip"
            val zipKey = "exports/labels/$tenantId/$zipFileName"

            val zipUrl = s3Service.uploadBytes(
                tenantId = tenantId,
                bytes = zipBytes,
                key = zipKey,
                contentType = "application/zip",
                fileName = zipFileName
            )

            val downloadUrl = s3Service.generateDownloadUrl(tenantId, zipKey, 3600)

            // Record export history
            exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = "labels",
                fileName = zipFileName,
                fileUrl = zipUrl,
                recordCount = downloadedLabels.size,
                status = ExportStatus.COMPLETED.code,
                filters = null
            )

            Result.success(
                ExportLabelsResponse(
                    success = true,
                    fileUrl = downloadUrl,
                    message = "Successfully exported ${downloadedLabels.size} labels"
                )
            )
        } catch (e: Exception) {
            logger.error("Error exporting shipping labels", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // HELPER: Resolve Order IDs from Batch
    // =====================================================

    /**
     * Get order IDs from a batch if batchId is provided, otherwise return orderIds.
     */
    fun resolveOrderIds(tenantId: Long, orderIds: List<Long>?, batchId: Long?): List<Long> {
        return when {
            batchId != null -> {
                batchRepository.getOrderIdsInBatch(batchId, tenantId)
            }
            orderIds != null -> orderIds
            else -> emptyList()
        }
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private suspend fun downloadDesignFromS3(tenantId: Long, designUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Extract key from URL if it's an S3 URL
            val key = extractS3Key(designUrl)
            if (key != null) {
                s3Service.downloadBytes(tenantId, key)
            } else {
                // If not an S3 key, try to download as URL
                java.net.URI(designUrl).toURL().openStream().use { it.readBytes() }
            }
        } catch (e: Exception) {
            logger.warn("Failed to download design from $designUrl: ${e.message}")
            null
        }
    }

    private fun extractS3Key(url: String): String? {
        return try {
            when {
                url.contains("s3.") && url.contains("amazonaws.com") -> {
                    url.substringAfter(".amazonaws.com/")
                }
                !url.startsWith("http") -> url
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateDesignFileName(design: DesignDownloadItem): String {
        val extension = design.designUrl.substringAfterLast(".").lowercase()

        return when (extension) {
            "dst", "ai" -> {
                // Embroidery/Vector format: "{orderId} {productCounter} {modCounter} {modName}.{ext}"
                "${design.orderId} ${design.productCounter} ${design.modCounter} ${design.modificationName.uppercase()}.$extension"
            }
            else -> {
                // Image format: "{orderId}_{productCounter}_{modCounter}_{modName}_{product}_{options}_x{qty}.{ext}"
                val sanitizedProduct = sanitizeFileName(design.productName)
                val options = "${sanitizeFileName(design.option1)}_${sanitizeFileName(design.option2)}"
                "${design.orderId}_${design.productCounter}_${design.modCounter}_${design.modificationName}_${sanitizedProduct}_${options}_x${design.quantity}.$extension"
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .take(50)
    }

    private fun createZipFile(files: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            files.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}

// =====================================================
// DATA CLASSES FOR DESIGN DOWNLOAD
// =====================================================

data class DesignDownloadItem(
    val orderId: Long,
    val orderProductId: Long,
    val productCounter: Int,
    val modCounter: Int,
    val modificationName: String,
    val productName: String,
    val option1: String,
    val option2: String,
    val quantity: Int,
    val designUrl: String,
    val printSize: String? = null
)

@kotlinx.serialization.Serializable
data class DesignDownloadResponse(
    val success: Boolean,
    val downloadUrl: String? = null,
    val fileName: String? = null,
    val fileCount: Int = 0,
    val failedCount: Int = 0,
    val failedDesigns: List<String> = emptyList(),
    val message: String? = null
)

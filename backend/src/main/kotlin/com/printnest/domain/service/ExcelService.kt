package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.ExportRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.ProductRepository
import com.printnest.domain.repository.WalletRepository
import com.printnest.integrations.aws.S3Service
import kotlinx.serialization.json.Json
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class ExcelService(
    private val exportRepository: ExportRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val walletRepository: WalletRepository,
    private val s3Service: S3Service,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(ExcelService::class.java)

    // =====================================================
    // CREATE ORDERS EXPORT
    // =====================================================

    fun createOrdersExport(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<ExportResponse> {
        return try {
            logger.info("Creating orders export for tenant $tenantId, orders: $orderIds")

            // Fetch order data with products
            val orderData = exportRepository.getOrdersForExport(tenantId, orderIds)

            if (orderData.isEmpty()) {
                return Result.success(
                    ExportResponse(
                        success = false,
                        message = "No orders found for the provided IDs"
                    )
                )
            }

            // Create Excel workbook
            val workbook = XSSFWorkbook()

            // Create sheets
            createProductsSheet(workbook, orderData)
            createOrdersSheet(workbook, orderData)
            createProductStatsSheet(workbook, orderData)
            createVariationStatsSheet(workbook, orderData)

            // Convert to byte array
            val excelBytes = workbookToBytes(workbook)
            workbook.close()

            // Generate filename
            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .substring(0, 19)
            val fileName = "orders_export_$timestamp.xlsx"

            // Upload to S3
            val fileUrl = s3Service.uploadExport(
                tenantId = tenantId,
                fileName = fileName,
                content = excelBytes
            )

            // Create export history record
            val exportId = exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = ExportType.ORDERS.code,
                fileName = fileName,
                fileUrl = fileUrl,
                recordCount = orderData.size,
                status = ExportStatus.COMPLETED.code,
                filters = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>()),
                    orderIds
                )
            )

            logger.info("Orders export completed: $fileName, records: ${orderData.size}")

            Result.success(
                ExportResponse(
                    success = true,
                    exportId = exportId,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    recordCount = orderData.size,
                    message = "Export completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create orders export", e)
            Result.failure(e)
        }
    }

    private fun createProductsSheet(workbook: XSSFWorkbook, orderData: List<OrderExportRow>) {
        val sheet = workbook.createSheet("Products")
        val headerStyle = createHeaderStyle(workbook)

        // Create header row
        val headers = listOf(
            "Order Nr", "Marketplace Order ID", "Customer Name", "Store Name", "Order Date",
            "Product Category", "Product Name", "Quantity", "Option 1", "Option 2",
            "Modifications", "Product Amount", "Shipping Amount", "Total Amount",
            "Shipping Paid", "Order Note"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Create data rows
        orderData.forEachIndexed { rowIndex, data ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(data.orderId.toDouble())
            row.createCell(1).setCellValue(data.intOrderId ?: "")
            row.createCell(2).setCellValue(data.customerName ?: "")
            row.createCell(3).setCellValue(data.storeName ?: "")
            row.createCell(4).setCellValue(data.orderDate)
            row.createCell(5).setCellValue(data.productCategory ?: "")
            row.createCell(6).setCellValue(data.productName ?: "")
            row.createCell(7).setCellValue(data.quantity.toDouble())
            row.createCell(8).setCellValue(data.option1 ?: "")
            row.createCell(9).setCellValue(data.option2 ?: "")
            row.createCell(10).setCellValue(data.modifications ?: "")
            row.createCell(11).setCellValue(data.productAmount.toDouble())
            row.createCell(12).setCellValue(data.shippingAmount.toDouble())
            row.createCell(13).setCellValue(data.totalAmount.toDouble())
            row.createCell(14).setCellValue(data.shippingPaid?.toDouble() ?: 0.0)
            row.createCell(15).setCellValue(data.orderNote ?: "")
        }

        // Auto-size columns
        autoSizeColumns(sheet, headers.size)
    }

    private fun createOrdersSheet(workbook: XSSFWorkbook, orderData: List<OrderExportRow>) {
        val sheet = workbook.createSheet("Orders")
        val headerStyle = createHeaderStyle(workbook)

        // Create unique orders summary
        val uniqueOrders = orderData
            .groupBy { it.orderId }
            .map { (orderId, orders) ->
                val first = orders.first()
                OrderSummaryRow(
                    orderId = orderId,
                    customerName = first.customerName,
                    orderDate = first.orderDate,
                    productAmount = first.productAmount,
                    shippingAmount = first.shippingAmount,
                    totalAmount = first.totalAmount,
                    shippingPaid = first.shippingPaid,
                    storeName = first.storeName
                )
            }

        val headers = listOf(
            "Order Nr", "Customer Name", "Order Date", "Product Amount",
            "Shipping Amount", "Total Amount", "Shipping Paid", "Store Name"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        uniqueOrders.forEachIndexed { rowIndex, data ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(data.orderId.toDouble())
            row.createCell(1).setCellValue(data.customerName ?: "")
            row.createCell(2).setCellValue(data.orderDate)
            row.createCell(3).setCellValue(data.productAmount.toDouble())
            row.createCell(4).setCellValue(data.shippingAmount.toDouble())
            row.createCell(5).setCellValue(data.totalAmount.toDouble())
            row.createCell(6).setCellValue(data.shippingPaid?.toDouble() ?: 0.0)
            row.createCell(7).setCellValue(data.storeName ?: "")
        }

        autoSizeColumns(sheet, headers.size)
    }

    private fun createProductStatsSheet(workbook: XSSFWorkbook, orderData: List<OrderExportRow>) {
        val sheet = workbook.createSheet("Product Stats")
        val headerStyle = createHeaderStyle(workbook)

        // Group by product name
        val productStats = orderData
            .groupBy { it.productName ?: "Unknown" }
            .map { (productName, orders) ->
                ProductStatsRow(
                    productName = productName,
                    quantity = orders.sumOf { it.quantity },
                    totalAmount = orders.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.totalAmount) }
                )
            }
            .sortedByDescending { it.quantity }

        val headers = listOf("Product Name", "Quantity", "Total Amount")

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        productStats.forEachIndexed { rowIndex, data ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(data.productName)
            row.createCell(1).setCellValue(data.quantity.toDouble())
            row.createCell(2).setCellValue(data.totalAmount.toDouble())
        }

        // Add totals row
        val totalsRow = sheet.createRow(productStats.size + 1)
        totalsRow.createCell(0).setCellValue("Total")
        totalsRow.createCell(1).setCellValue(productStats.sumOf { it.quantity }.toDouble())
        totalsRow.createCell(2).setCellValue(
            productStats.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.totalAmount) }.toDouble()
        )

        autoSizeColumns(sheet, headers.size)
    }

    private fun createVariationStatsSheet(workbook: XSSFWorkbook, orderData: List<OrderExportRow>) {
        val sheet = workbook.createSheet("Variation Stats")
        val headerStyle = createHeaderStyle(workbook)

        // Group by product name, option1, option2
        val variationStats = orderData
            .groupBy { Triple(it.productName ?: "Unknown", it.option1 ?: "", it.option2 ?: "") }
            .map { (key, orders) ->
                VariationStatsRow(
                    productName = key.first,
                    option1 = key.second.takeIf { it.isNotEmpty() },
                    option2 = key.third.takeIf { it.isNotEmpty() },
                    quantity = orders.sumOf { it.quantity },
                    totalAmount = orders.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.totalAmount) }
                )
            }
            .sortedWith(compareBy({ it.productName }, { it.option1 }, { it.option2 }))

        val headers = listOf("Product Name", "Option 1", "Option 2", "Quantity", "Total Amount")

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        variationStats.forEachIndexed { rowIndex, data ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(data.productName)
            row.createCell(1).setCellValue(data.option1 ?: "")
            row.createCell(2).setCellValue(data.option2 ?: "")
            row.createCell(3).setCellValue(data.quantity.toDouble())
            row.createCell(4).setCellValue(data.totalAmount.toDouble())
        }

        autoSizeColumns(sheet, headers.size)
    }

    // =====================================================
    // CREATE OUT OF STOCK EXPORT
    // =====================================================

    fun createOutOfStockExport(
        tenantId: Long,
        userId: Long,
        orderIds: List<Long>
    ): Result<ExportResponse> {
        return try {
            logger.info("Creating out-of-stock export for tenant $tenantId, orders: $orderIds")

            val oosData = exportRepository.getOutOfStockProducts(tenantId, orderIds)

            if (oosData.isEmpty()) {
                return Result.success(
                    ExportResponse(
                        success = true,
                        recordCount = 0,
                        message = "No out of stock products found"
                    )
                )
            }

            val workbook = XSSFWorkbook()
            val headerStyle = createHeaderStyle(workbook)

            // Create summary sheet - aggregate by variant
            val summarySheet = workbook.createSheet("Out of Stock Summary")
            val summaryHeaders = listOf("Product Title", "Option 1", "Option 2", "Supplier", "Quantity")

            val summaryHeaderRow = summarySheet.createRow(0)
            summaryHeaders.forEachIndexed { index, header ->
                val cell = summaryHeaderRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            // Group and aggregate by variant
            val aggregatedProducts = oosData
                .groupBy { Triple(it.productTitle, it.option1 ?: "", it.option2 ?: "") }
                .map { (key, products) ->
                    OutOfStockProductRow(
                        productTitle = key.first,
                        option1 = key.second.takeIf { it.isNotEmpty() },
                        option2 = key.third.takeIf { it.isNotEmpty() },
                        supplierName = products.first().supplierName,
                        quantity = products.sumOf { it.quantity }
                    )
                }
                .sortedWith(compareBy({ it.supplierName }, { it.productTitle }, { it.option1 }))

            aggregatedProducts.forEachIndexed { rowIndex, data ->
                val row = summarySheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(data.productTitle)
                row.createCell(1).setCellValue(data.option1 ?: "")
                row.createCell(2).setCellValue(data.option2 ?: "")
                row.createCell(3).setCellValue(data.supplierName ?: "")
                row.createCell(4).setCellValue(data.quantity.toDouble())
            }

            // Add totals row
            val totalsRow = summarySheet.createRow(aggregatedProducts.size + 2)
            totalsRow.createCell(0).setCellValue("Total")
            totalsRow.createCell(4).setCellValue(aggregatedProducts.sumOf { it.quantity }.toDouble())

            autoSizeColumns(summarySheet, summaryHeaders.size)

            // Create raw order data sheet
            val rawSheet = workbook.createSheet("Order Data")
            val rawHeaders = listOf("Order ID", "Product Title", "Option 1", "Option 2", "Quantity", "Customer Name")

            val rawHeaderRow = rawSheet.createRow(0)
            rawHeaders.forEachIndexed { index, header ->
                val cell = rawHeaderRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            val rawData = exportRepository.getOutOfStockOrderData(tenantId, orderIds)
            rawData.forEachIndexed { rowIndex, data ->
                val row = rawSheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(data.orderId.toDouble())
                row.createCell(1).setCellValue(data.productTitle)
                row.createCell(2).setCellValue(data.option1 ?: "")
                row.createCell(3).setCellValue(data.option2 ?: "")
                row.createCell(4).setCellValue(data.quantity.toDouble())
                row.createCell(5).setCellValue(data.customerName ?: "")
            }

            autoSizeColumns(rawSheet, rawHeaders.size)

            val excelBytes = workbookToBytes(workbook)
            workbook.close()

            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .substring(0, 19)
            val fileName = "oos_products_$timestamp.xlsx"

            val fileUrl = s3Service.uploadExport(
                tenantId = tenantId,
                fileName = fileName,
                content = excelBytes
            )

            val exportId = exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = ExportType.OUT_OF_STOCK.code,
                fileName = fileName,
                fileUrl = fileUrl,
                recordCount = oosData.size,
                status = ExportStatus.COMPLETED.code,
                filters = null
            )

            logger.info("Out-of-stock export completed: $fileName, records: ${oosData.size}")

            Result.success(
                ExportResponse(
                    success = true,
                    exportId = exportId,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    recordCount = oosData.size,
                    message = "Export completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create out-of-stock export", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CREATE PRODUCTS EXPORT
    // =====================================================

    fun createProductsExport(
        tenantId: Long,
        userId: Long,
        filters: ExportFilters?
    ): Result<ExportResponse> {
        return try {
            logger.info("Creating products export for tenant $tenantId")

            val productData = exportRepository.getProductsForExport(tenantId, filters)

            if (productData.isEmpty()) {
                return Result.success(
                    ExportResponse(
                        success = false,
                        message = "No products found"
                    )
                )
            }

            val workbook = XSSFWorkbook()
            val headerStyle = createHeaderStyle(workbook)

            // Products sheet
            val productsSheet = workbook.createSheet("Products")
            val productHeaders = listOf(
                "Product ID", "Title", "Category", "Base Price", "Option 1 Name", "Option 2 Name",
                "Variant Count", "In Stock", "Out of Stock", "Design Type", "Status", "Created At"
            )

            val productHeaderRow = productsSheet.createRow(0)
            productHeaders.forEachIndexed { index, header ->
                val cell = productHeaderRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            productData.forEachIndexed { rowIndex, data ->
                val row = productsSheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(data.productId.toDouble())
                row.createCell(1).setCellValue(data.title)
                row.createCell(2).setCellValue(data.categoryName ?: "")
                row.createCell(3).setCellValue(data.basePrice.toDouble())
                row.createCell(4).setCellValue(data.option1Name ?: "")
                row.createCell(5).setCellValue(data.option2Name ?: "")
                row.createCell(6).setCellValue(data.variantCount.toDouble())
                row.createCell(7).setCellValue(data.inStockCount.toDouble())
                row.createCell(8).setCellValue(data.outOfStockCount.toDouble())
                row.createCell(9).setCellValue(data.designType)
                row.createCell(10).setCellValue(if (data.status == 1) "Active" else "Inactive")
                row.createCell(11).setCellValue(data.createdAt)
            }

            autoSizeColumns(productsSheet, productHeaders.size)

            // Variants sheet
            val variantData = exportRepository.getVariantsForExport(tenantId, filters)
            if (variantData.isNotEmpty()) {
                val variantsSheet = workbook.createSheet("Variants")
                val variantHeaders = listOf(
                    "Variant ID", "Product ID", "Product Title", "Option 1", "Option 2",
                    "SKU", "Price", "Cost", "Weight", "In Stock", "Stock Quantity"
                )

                val variantHeaderRow = variantsSheet.createRow(0)
                variantHeaders.forEachIndexed { index, header ->
                    val cell = variantHeaderRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                variantData.forEachIndexed { rowIndex, data ->
                    val row = variantsSheet.createRow(rowIndex + 1)
                    row.createCell(0).setCellValue(data.variantId.toDouble())
                    row.createCell(1).setCellValue(data.productId.toDouble())
                    row.createCell(2).setCellValue(data.productTitle)
                    row.createCell(3).setCellValue(data.option1Value ?: "")
                    row.createCell(4).setCellValue(data.option2Value ?: "")
                    row.createCell(5).setCellValue(data.sku ?: "")
                    row.createCell(6).setCellValue(data.price.toDouble())
                    row.createCell(7).setCellValue(data.cost.toDouble())
                    row.createCell(8).setCellValue(data.weight ?: 0.0)
                    row.createCell(9).setCellValue(if (data.inStock) "Yes" else "No")
                    row.createCell(10).setCellValue((data.stockQuantity ?: 0).toDouble())
                }

                autoSizeColumns(variantsSheet, variantHeaders.size)
            }

            val excelBytes = workbookToBytes(workbook)
            workbook.close()

            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .substring(0, 19)
            val fileName = "products_export_$timestamp.xlsx"

            val fileUrl = s3Service.uploadExport(
                tenantId = tenantId,
                fileName = fileName,
                content = excelBytes
            )

            val exportId = exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = ExportType.PRODUCTS.code,
                fileName = fileName,
                fileUrl = fileUrl,
                recordCount = productData.size,
                status = ExportStatus.COMPLETED.code,
                filters = filters?.let { json.encodeToString(ExportFilters.serializer(), it) }
            )

            logger.info("Products export completed: $fileName, records: ${productData.size}")

            Result.success(
                ExportResponse(
                    success = true,
                    exportId = exportId,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    recordCount = productData.size,
                    message = "Export completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create products export", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CREATE TRANSACTIONS EXPORT
    // =====================================================

    fun createTransactionsExport(
        tenantId: Long,
        userId: Long,
        targetUserId: Long?,
        filters: ExportFilters?
    ): Result<ExportResponse> {
        return try {
            logger.info("Creating transactions export for tenant $tenantId, user: $targetUserId")

            val transactionData = exportRepository.getTransactionsForExport(
                tenantId = tenantId,
                userId = targetUserId,
                startDate = filters?.startDate,
                endDate = filters?.endDate
            )

            if (transactionData.isEmpty()) {
                return Result.success(
                    ExportResponse(
                        success = false,
                        message = "No transactions found"
                    )
                )
            }

            val workbook = XSSFWorkbook()
            val headerStyle = createHeaderStyle(workbook)

            val sheet = workbook.createSheet("Transactions")
            val headers = listOf(
                "Transaction ID", "User ID", "Customer Name", "Type", "Amount",
                "Description", "Reference ID", "Balance Before", "Balance After", "Date"
            )

            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            transactionData.forEachIndexed { rowIndex, data ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(data.transactionId.toDouble())
                row.createCell(1).setCellValue(data.userId.toDouble())
                row.createCell(2).setCellValue(data.customerName ?: "")
                row.createCell(3).setCellValue(data.type)
                row.createCell(4).setCellValue(data.amount.toDouble())
                row.createCell(5).setCellValue(data.description ?: "")
                row.createCell(6).setCellValue(data.referenceId ?: "")
                row.createCell(7).setCellValue(data.balanceBefore.toDouble())
                row.createCell(8).setCellValue(data.balanceAfter.toDouble())
                row.createCell(9).setCellValue(data.createdAt)
            }

            // Summary row
            val summaryRow = sheet.createRow(transactionData.size + 2)
            summaryRow.createCell(0).setCellValue("Summary")
            summaryRow.createCell(3).setCellValue("Total:")
            summaryRow.createCell(4).setCellValue(
                transactionData.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }.toDouble()
            )

            autoSizeColumns(sheet, headers.size)

            val excelBytes = workbookToBytes(workbook)
            workbook.close()

            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .substring(0, 19)
            val fileName = "transactions_export_$timestamp.xlsx"

            val fileUrl = s3Service.uploadExport(
                tenantId = tenantId,
                fileName = fileName,
                content = excelBytes
            )

            val exportId = exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = ExportType.TRANSACTIONS.code,
                fileName = fileName,
                fileUrl = fileUrl,
                recordCount = transactionData.size,
                status = ExportStatus.COMPLETED.code,
                filters = filters?.let { json.encodeToString(ExportFilters.serializer(), it) }
            )

            logger.info("Transactions export completed: $fileName, records: ${transactionData.size}")

            Result.success(
                ExportResponse(
                    success = true,
                    exportId = exportId,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    recordCount = transactionData.size,
                    message = "Export completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create transactions export", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CREATE DESIGNS EXPORT
    // =====================================================

    fun createDesignsExport(
        tenantId: Long,
        userId: Long,
        filters: ExportFilters?
    ): Result<ExportResponse> {
        return try {
            logger.info("Creating designs export for tenant $tenantId")

            val designData = exportRepository.getDesignsForExport(tenantId, filters)

            if (designData.isEmpty()) {
                return Result.success(
                    ExportResponse(
                        success = false,
                        message = "No designs found"
                    )
                )
            }

            val workbook = XSSFWorkbook()
            val headerStyle = createHeaderStyle(workbook)

            val sheet = workbook.createSheet("Designs")
            val headers = listOf(
                "Design ID", "Title", "Design Type", "Type Name", "URL",
                "Width (inch)", "Height (inch)", "Usage Count", "Status", "Created At"
            )

            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            designData.forEachIndexed { rowIndex, data ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(data.designId.toDouble())
                row.createCell(1).setCellValue(data.title)
                row.createCell(2).setCellValue(data.designType.toDouble())
                row.createCell(3).setCellValue(data.designTypeName)
                row.createCell(4).setCellValue(data.designUrl)
                row.createCell(5).setCellValue(data.width ?: 0.0)
                row.createCell(6).setCellValue(data.height ?: 0.0)
                row.createCell(7).setCellValue(data.usageCount.toDouble())
                row.createCell(8).setCellValue(if (data.status == 1) "Active" else "Inactive")
                row.createCell(9).setCellValue(data.createdAt)
            }

            autoSizeColumns(sheet, headers.size)

            val excelBytes = workbookToBytes(workbook)
            workbook.close()

            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .substring(0, 19)
            val fileName = "designs_export_$timestamp.xlsx"

            val fileUrl = s3Service.uploadExport(
                tenantId = tenantId,
                fileName = fileName,
                content = excelBytes
            )

            val exportId = exportRepository.createExportHistory(
                tenantId = tenantId,
                userId = userId,
                exportType = ExportType.DESIGNS.code,
                fileName = fileName,
                fileUrl = fileUrl,
                recordCount = designData.size,
                status = ExportStatus.COMPLETED.code,
                filters = filters?.let { json.encodeToString(ExportFilters.serializer(), it) }
            )

            logger.info("Designs export completed: $fileName, records: ${designData.size}")

            Result.success(
                ExportResponse(
                    success = true,
                    exportId = exportId,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    recordCount = designData.size,
                    message = "Export completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to create designs export", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // EXPORT HISTORY
    // =====================================================

    fun getExportHistory(
        tenantId: Long,
        userId: Long?,
        page: Int = 1,
        limit: Int = 20
    ): ExportHistoryListResponse {
        val (exports, total) = exportRepository.getExportHistory(tenantId, userId, page, limit)
        val totalPages = (total + limit - 1) / limit

        return ExportHistoryListResponse(
            exports = exports,
            total = total,
            page = page,
            limit = limit,
            totalPages = totalPages
        )
    }

    fun getExportById(tenantId: Long, exportId: Long): ExportHistory? {
        return exportRepository.getExportById(tenantId, exportId)
    }

    fun getDownloadUrl(tenantId: Long, exportId: Long): ExportDownloadResponse {
        val export = exportRepository.getExportById(tenantId, exportId)
            ?: return ExportDownloadResponse(
                success = false,
                message = "Export not found"
            )

        if (export.fileUrl == null) {
            return ExportDownloadResponse(
                success = false,
                message = "Export file not available"
            )
        }

        // Generate presigned URL valid for 1 hour
        val presignedUrl = s3Service.generatePresignedUrl(tenantId, export.fileUrl, 3600)

        return ExportDownloadResponse(
            success = true,
            downloadUrl = presignedUrl ?: export.fileUrl,
            fileName = export.fileName,
            expiresAt = Instant.now().plusSeconds(3600).toString()
        )
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun createHeaderStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        return style
    }

    private fun autoSizeColumns(sheet: Sheet, columnCount: Int) {
        for (i in 0 until columnCount) {
            sheet.autoSizeColumn(i)
            // Add a little extra width
            val currentWidth = sheet.getColumnWidth(i)
            sheet.setColumnWidth(i, (currentWidth * 1.1).toInt().coerceAtMost(255 * 256))
        }
    }

    private fun workbookToBytes(workbook: Workbook): ByteArray {
        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        return outputStream.toByteArray()
    }
}

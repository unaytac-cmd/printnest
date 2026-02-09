package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.SettingsRepository
import com.printnest.integrations.aws.S3Service
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.client.j2se.MatrixToImageWriter

class PdfService(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val s3Service: S3Service
) {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)

    companion object {
        private const val PDF_PREFIX = "pdfs"
        private const val MARGIN = 40f
        private const val LINE_HEIGHT = 14f
        private const val HEADER_HEIGHT = 80f
        private const val FOOTER_HEIGHT = 40f

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("America/Chicago"))
    }

    // =====================================================
    // PACKING SLIP GENERATION
    // =====================================================

    /**
     * Generate a packing slip PDF for a single order
     */
    suspend fun generatePackingSlip(
        orderId: Long,
        tenantId: Long,
        options: PdfGenerationOptions = PdfGenerationOptions()
    ): Result<PdfGenerationResponse> {
        return try {
            val order = orderRepository.findByIdWithProducts(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

            val companyInfo = getCompanyInfo(tenantId)
            val packingSlipData = buildPackingSlipData(order, companyInfo, options)

            val pdfBytes = createPackingSlipPdf(packingSlipData, options)

            val fileId = UUID.randomUUID().toString()
            val fileName = "packing-slip-${order.intOrderId ?: orderId}.pdf"
            val key = "$PDF_PREFIX/$tenantId/packing-slips/$fileId/$fileName"

            val fileUrl = uploadPdfToS3(tenantId, pdfBytes, key, fileName)

            Result.success(PdfGenerationResponse(
                success = true,
                fileId = fileId,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = pdfBytes.size.toLong(),
                generatedAt = Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error generating packing slip for order $orderId", e)
            Result.failure(e)
        }
    }

    /**
     * Generate packing slips for multiple orders
     */
    suspend fun generateBulkPackingSlips(
        orderIds: List<Long>,
        tenantId: Long,
        options: PdfGenerationOptions = PdfGenerationOptions(),
        generateZip: Boolean = true
    ): Result<BulkPdfGenerationResponse> {
        return try {
            val companyInfo = getCompanyInfo(tenantId)
            val generatedFiles = mutableListOf<PdfFileInfo>()
            val errors = mutableListOf<String>()
            val pdfBytesMap = mutableMapOf<String, ByteArray>()

            // Fetch and process all orders
            val orders = orderIds.mapNotNull { orderId ->
                try {
                    orderRepository.findByIdWithProducts(orderId, tenantId)
                } catch (e: Exception) {
                    errors.add("Failed to fetch order $orderId: ${e.message}")
                    null
                }
            }

            // Group by modification if requested
            val orderedOrders = if (options.groupByModification) {
                orders.sortedWith(compareBy(
                    { getTopModificationName(it) },
                    { -getTopStitchCount(it) }
                ))
            } else {
                orders.sortedBy { it.id }
            }

            // Generate PDFs for each order
            orderedOrders.forEach { order ->
                try {
                    val packingSlipData = buildPackingSlipData(order, companyInfo, options)
                    val pdfBytes = createPackingSlipPdf(packingSlipData, options)

                    val fileId = UUID.randomUUID().toString()
                    val fileName = "packing-slip-${order.intOrderId ?: order.id}.pdf"

                    if (generateZip) {
                        pdfBytesMap[fileName] = pdfBytes
                    }

                    val key = "$PDF_PREFIX/$tenantId/packing-slips/$fileId/$fileName"
                    val fileUrl = uploadPdfToS3(tenantId, pdfBytes, key, fileName)

                    generatedFiles.add(PdfFileInfo(
                        orderId = order.id,
                        fileId = fileId,
                        fileUrl = fileUrl,
                        fileName = fileName
                    ))
                } catch (e: Exception) {
                    logger.error("Error generating packing slip for order ${order.id}", e)
                    errors.add("Order ${order.id}: ${e.message}")
                }
            }

            // Generate ZIP if requested
            var zipFileUrl: String? = null
            if (generateZip && pdfBytesMap.isNotEmpty()) {
                val zipBytes = createZipFile(pdfBytesMap)
                val zipFileId = UUID.randomUUID().toString()
                val zipFileName = "packing-slips-${System.currentTimeMillis()}.zip"
                val zipKey = "$PDF_PREFIX/$tenantId/packing-slips/$zipFileId/$zipFileName"
                zipFileUrl = uploadPdfToS3(tenantId, zipBytes, zipKey, zipFileName)
            }

            Result.success(BulkPdfGenerationResponse(
                success = errors.isEmpty(),
                files = generatedFiles,
                zipFileUrl = zipFileUrl,
                totalGenerated = generatedFiles.size,
                totalFailed = errors.size,
                errors = errors,
                message = if (errors.isEmpty())
                    "Successfully generated ${generatedFiles.size} packing slips"
                else
                    "Generated ${generatedFiles.size} packing slips with ${errors.size} failures"
            ))
        } catch (e: Exception) {
            logger.error("Error generating bulk packing slips", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // INVOICE GENERATION
    // =====================================================

    /**
     * Generate an invoice PDF for a single order
     */
    suspend fun generateInvoice(
        orderId: Long,
        tenantId: Long,
        options: PdfGenerationOptions = PdfGenerationOptions()
    ): Result<PdfGenerationResponse> {
        return try {
            val order = orderRepository.findByIdWithProducts(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

            val companyInfo = getCompanyInfo(tenantId)
            val invoiceData = buildInvoiceData(order, companyInfo)

            val pdfBytes = createInvoicePdf(invoiceData, options)

            val fileId = UUID.randomUUID().toString()
            val fileName = "invoice-${invoiceData.invoiceNumber}.pdf"
            val key = "$PDF_PREFIX/$tenantId/invoices/$fileId/$fileName"

            val fileUrl = uploadPdfToS3(tenantId, pdfBytes, key, fileName)

            Result.success(PdfGenerationResponse(
                success = true,
                fileId = fileId,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = pdfBytes.size.toLong(),
                generatedAt = Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error generating invoice for order $orderId", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // SHIPPING LABEL GENERATION
    // =====================================================

    /**
     * Generate a shipping label PDF
     */
    suspend fun generateShippingLabel(
        orderId: Long,
        tenantId: Long,
        options: PdfGenerationOptions = PdfGenerationOptions()
    ): Result<PdfGenerationResponse> {
        return try {
            val order = orderRepository.findByIdWithProducts(orderId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

            val companyInfo = getCompanyInfo(tenantId)
            val labelData = buildLabelData(order, companyInfo)

            val pdfBytes = createLabelPdf(labelData, options)

            val fileId = UUID.randomUUID().toString()
            val fileName = "label-${order.intOrderId ?: orderId}.pdf"
            val key = "$PDF_PREFIX/$tenantId/labels/$fileId/$fileName"

            val fileUrl = uploadPdfToS3(tenantId, pdfBytes, key, fileName)

            Result.success(PdfGenerationResponse(
                success = true,
                fileId = fileId,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = pdfBytes.size.toLong(),
                generatedAt = Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error generating shipping label for order $orderId", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // PDF CREATION - PACKING SLIP
    // =====================================================

    private fun createPackingSlipPdf(
        data: PackingSlipData,
        options: PdfGenerationOptions
    ): ByteArray {
        val document = PDDocument()

        try {
            val pageSize = getPageSize(options.paperSize)
            val page = PDPage(pageSize)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = pageSize.width
            val pageHeight = pageSize.height
            var yPosition = pageHeight - MARGIN

            // Load fonts
            val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

            // ===== HEADER =====
            yPosition = drawPackingSlipHeader(
                contentStream, document, data, pageWidth, yPosition, fontBold, fontRegular, options
            )

            // ===== ORDER INFO =====
            yPosition -= 20f
            yPosition = drawOrderInfo(contentStream, data, pageWidth, yPosition, fontBold, fontRegular)

            // ===== CUSTOMER & SHIPPING ADDRESS =====
            yPosition -= 20f
            yPosition = drawAddressSection(contentStream, data, pageWidth, yPosition, fontBold, fontRegular)

            // ===== PRODUCTS TABLE =====
            yPosition -= 30f
            yPosition = drawProductsTable(contentStream, document, data, pageWidth, yPosition, fontBold, fontRegular, options)

            // ===== NOTES SECTION =====
            if (!data.orderNote.isNullOrBlank() || !data.giftNote.isNullOrBlank()) {
                yPosition -= 20f
                yPosition = drawNotesSection(contentStream, data, pageWidth, yPosition, fontBold, fontRegular)
            }

            // ===== QR CODE =====
            if (options.includeQrCode && data.trackingNumber != null) {
                yPosition -= 20f
                drawQrCode(contentStream, document, data.trackingUrl ?: data.trackingNumber, MARGIN, yPosition - 80f)
            }

            // ===== FOOTER =====
            drawFooter(contentStream, data.companyInfo, pageWidth, fontRegular)

            contentStream.close()

            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun drawPackingSlipHeader(
        contentStream: PDPageContentStream,
        document: PDDocument,
        data: PackingSlipData,
        pageWidth: Float,
        startY: Float,
        fontBold: PDType1Font,
        fontRegular: PDType1Font,
        options: PdfGenerationOptions
    ): Float {
        var yPosition = startY

        // Draw company logo if available
        data.companyInfo?.logoUrl?.let { logoUrl ->
            try {
                val logoBytes = downloadImage(logoUrl)
                if (logoBytes != null) {
                    val logoImage = PDImageXObject.createFromByteArray(document, logoBytes, "logo")
                    val logoHeight = 48f
                    val logoWidth = logoHeight * logoImage.width / logoImage.height
                    contentStream.drawImage(logoImage, MARGIN, yPosition - logoHeight, logoWidth, logoHeight)
                }
            } catch (e: Exception) {
                logger.warn("Failed to load logo: ${e.message}")
            }
        }

        // Title
        val titleX = pageWidth / 2 - 60f
        contentStream.beginText()
        contentStream.setFont(fontBold, 24f)
        contentStream.newLineAtOffset(titleX, yPosition - 20f)
        contentStream.showText("Packing Slip")
        contentStream.endText()

        // Date and Order ID on the right
        val dateStr = data.orderDate
        contentStream.beginText()
        contentStream.setFont(fontRegular, 10f)
        contentStream.newLineAtOffset(pageWidth - MARGIN - 150f, yPosition - 10f)
        contentStream.showText("Date: $dateStr")
        contentStream.endText()

        contentStream.beginText()
        contentStream.setFont(fontRegular, 10f)
        contentStream.newLineAtOffset(pageWidth - MARGIN - 150f, yPosition - 24f)
        contentStream.showText("Order: ${data.intOrderId ?: data.orderId}")
        contentStream.endText()

        // Draw separator line
        yPosition -= HEADER_HEIGHT
        contentStream.moveTo(MARGIN, yPosition)
        contentStream.lineTo(pageWidth - MARGIN, yPosition)
        contentStream.stroke()

        return yPosition
    }

    private fun drawOrderInfo(
        contentStream: PDPageContentStream,
        data: PackingSlipData,
        pageWidth: Float,
        startY: Float,
        fontBold: PDType1Font,
        fontRegular: PDType1Font
    ): Float {
        var yPosition = startY

        // Store and batch info
        if (!data.storeName.isNullOrBlank()) {
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("Store: ${data.storeName}")
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        if (data.batchNames.isNotEmpty()) {
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("Batch: ${data.batchNames.joinToString(", ")}")
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        // Tracking info
        if (!data.trackingNumber.isNullOrBlank()) {
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("Tracking: ${data.trackingNumber}")
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        return yPosition
    }

    private fun drawAddressSection(
        contentStream: PDPageContentStream,
        data: PackingSlipData,
        pageWidth: Float,
        startY: Float,
        fontBold: PDType1Font,
        fontRegular: PDType1Font
    ): Float {
        var yPosition = startY
        val columnWidth = (pageWidth - MARGIN * 2) / 2

        // Customer Info (left column)
        contentStream.beginText()
        contentStream.setFont(fontBold, 12f)
        contentStream.newLineAtOffset(MARGIN, yPosition)
        contentStream.showText("Customer")
        contentStream.endText()
        yPosition -= LINE_HEIGHT + 4f

        contentStream.beginText()
        contentStream.setFont(fontRegular, 10f)
        contentStream.newLineAtOffset(MARGIN, yPosition)
        contentStream.showText(data.customer.name)
        contentStream.endText()
        yPosition -= LINE_HEIGHT

        data.customer.email?.let { email ->
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText(email)
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        // Shipping Address (right column)
        var rightY = startY
        contentStream.beginText()
        contentStream.setFont(fontBold, 12f)
        contentStream.newLineAtOffset(MARGIN + columnWidth, rightY)
        contentStream.showText("Ship To")
        contentStream.endText()
        rightY -= LINE_HEIGHT + 4f

        data.shippingAddress?.let { addr ->
            val addressLines = buildAddressLines(addr)
            addressLines.forEach { line ->
                contentStream.beginText()
                contentStream.setFont(fontRegular, 10f)
                contentStream.newLineAtOffset(MARGIN + columnWidth, rightY)
                contentStream.showText(line)
                contentStream.endText()
                rightY -= LINE_HEIGHT
            }
        }

        return minOf(yPosition, rightY)
    }

    private fun drawProductsTable(
        contentStream: PDPageContentStream,
        document: PDDocument,
        data: PackingSlipData,
        pageWidth: Float,
        startY: Float,
        fontBold: PDType1Font,
        fontRegular: PDType1Font,
        options: PdfGenerationOptions
    ): Float {
        var yPosition = startY
        val tableWidth = pageWidth - MARGIN * 2
        val col1Width = if (options.includeProductThumbnails) 60f else 0f
        val col2Width = tableWidth - col1Width - 100f - 60f
        val col3Width = 100f
        val col4Width = 60f

        // Table header
        contentStream.setNonStrokingColor(0.9f, 0.9f, 0.9f)
        contentStream.addRect(MARGIN, yPosition - 20f, tableWidth, 20f)
        contentStream.fill()
        contentStream.setNonStrokingColor(0f, 0f, 0f)

        var headerX = MARGIN + 5f
        if (options.includeProductThumbnails) {
            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(headerX, yPosition - 14f)
            contentStream.showText("Image")
            contentStream.endText()
            headerX += col1Width
        }

        contentStream.beginText()
        contentStream.setFont(fontBold, 10f)
        contentStream.newLineAtOffset(headerX, yPosition - 14f)
        contentStream.showText("Product")
        contentStream.endText()

        contentStream.beginText()
        contentStream.setFont(fontBold, 10f)
        contentStream.newLineAtOffset(MARGIN + col1Width + col2Width + 5f, yPosition - 14f)
        contentStream.showText("Options")
        contentStream.endText()

        contentStream.beginText()
        contentStream.setFont(fontBold, 10f)
        contentStream.newLineAtOffset(MARGIN + col1Width + col2Width + col3Width + 5f, yPosition - 14f)
        contentStream.showText("Qty")
        contentStream.endText()

        yPosition -= 24f

        // Draw products
        data.products.forEach { product ->
            val rowHeight = if (options.includeProductThumbnails) 50f else 30f

            // Draw row background for alternating rows
            if (data.products.indexOf(product) % 2 == 1) {
                contentStream.setNonStrokingColor(0.97f, 0.97f, 0.97f)
                contentStream.addRect(MARGIN, yPosition - rowHeight, tableWidth, rowHeight)
                contentStream.fill()
                contentStream.setNonStrokingColor(0f, 0f, 0f)
            }

            var cellX = MARGIN + 5f

            // Thumbnail
            if (options.includeProductThumbnails && !product.thumbnailUrl.isNullOrBlank()) {
                try {
                    val thumbBytes = downloadImage(product.thumbnailUrl)
                    if (thumbBytes != null) {
                        val thumbImage = PDImageXObject.createFromByteArray(document, thumbBytes, "thumb")
                        contentStream.drawImage(thumbImage, cellX, yPosition - 45f, 40f, 40f)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load thumbnail: ${e.message}")
                }
                cellX += col1Width
            }

            // Product title and modifications
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(cellX, yPosition - 14f)
            contentStream.showText(truncateText(product.title, 40))
            contentStream.endText()

            if (product.modifications.isNotEmpty()) {
                val modText = product.modifications.joinToString(", ") { it.name }
                contentStream.beginText()
                contentStream.setFont(fontRegular, 8f)
                contentStream.newLineAtOffset(cellX, yPosition - 26f)
                contentStream.showText("Mods: $modText")
                contentStream.endText()
            }

            // Options
            val optionText = listOfNotNull(product.option1, product.option2)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(MARGIN + col1Width + col2Width + 5f, yPosition - 14f)
            contentStream.showText(truncateText(optionText, 15))
            contentStream.endText()

            // Quantity
            contentStream.beginText()
            contentStream.setFont(fontBold, 12f)
            contentStream.newLineAtOffset(MARGIN + col1Width + col2Width + col3Width + 20f, yPosition - 14f)
            contentStream.showText(product.quantity.toString())
            contentStream.endText()

            yPosition -= rowHeight
        }

        // Draw table border
        contentStream.moveTo(MARGIN, startY)
        contentStream.lineTo(MARGIN, yPosition)
        contentStream.lineTo(MARGIN + tableWidth, yPosition)
        contentStream.lineTo(MARGIN + tableWidth, startY)
        contentStream.lineTo(MARGIN, startY)
        contentStream.stroke()

        return yPosition
    }

    private fun drawNotesSection(
        contentStream: PDPageContentStream,
        data: PackingSlipData,
        pageWidth: Float,
        startY: Float,
        fontBold: PDType1Font,
        fontRegular: PDType1Font
    ): Float {
        var yPosition = startY

        if (!data.orderNote.isNullOrBlank()) {
            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("Order Notes:")
            contentStream.endText()
            yPosition -= LINE_HEIGHT

            contentStream.beginText()
            contentStream.setFont(fontRegular, 9f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText(truncateText(data.orderNote, 80))
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        if (!data.giftNote.isNullOrBlank()) {
            yPosition -= 10f
            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("Gift Message:")
            contentStream.endText()
            yPosition -= LINE_HEIGHT

            contentStream.beginText()
            contentStream.setFont(fontRegular, 9f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText(truncateText(data.giftNote, 80))
            contentStream.endText()
            yPosition -= LINE_HEIGHT
        }

        return yPosition
    }

    private fun drawQrCode(
        contentStream: PDPageContentStream,
        document: PDDocument,
        content: String,
        x: Float,
        y: Float
    ) {
        try {
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 100, 100)
            val qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix)

            val baos = ByteArrayOutputStream()
            ImageIO.write(qrImage, "PNG", baos)
            val qrImageXObject = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "qr")

            contentStream.drawImage(qrImageXObject, x, y, 80f, 80f)
        } catch (e: Exception) {
            logger.warn("Failed to generate QR code: ${e.message}")
        }
    }

    private fun drawFooter(
        contentStream: PDPageContentStream,
        companyInfo: CompanyInfo?,
        pageWidth: Float,
        fontRegular: PDType1Font
    ) {
        val footerY = MARGIN

        contentStream.beginText()
        contentStream.setFont(fontRegular, 8f)
        contentStream.newLineAtOffset(pageWidth / 2 - 100f, footerY)
        contentStream.showText("Thank you for your order!")
        contentStream.endText()

        companyInfo?.let { info ->
            val websiteText = info.website ?: ""
            if (websiteText.isNotBlank()) {
                contentStream.beginText()
                contentStream.setFont(fontRegular, 8f)
                contentStream.newLineAtOffset(pageWidth / 2 - 60f, footerY - 12f)
                contentStream.showText(websiteText)
                contentStream.endText()
            }
        }
    }

    // =====================================================
    // PDF CREATION - INVOICE
    // =====================================================

    private fun createInvoicePdf(
        data: InvoiceData,
        options: PdfGenerationOptions
    ): ByteArray {
        val document = PDDocument()

        try {
            val pageSize = getPageSize(options.paperSize)
            val page = PDPage(pageSize)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = pageSize.width
            val pageHeight = pageSize.height
            var yPosition = pageHeight - MARGIN

            val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

            // ===== HEADER =====
            // Company logo
            data.companyInfo?.logoUrl?.let { logoUrl ->
                try {
                    val logoBytes = downloadImage(logoUrl)
                    if (logoBytes != null) {
                        val logoImage = PDImageXObject.createFromByteArray(document, logoBytes, "logo")
                        val logoHeight = 48f
                        val logoWidth = logoHeight * logoImage.width / logoImage.height
                        contentStream.drawImage(logoImage, MARGIN, yPosition - logoHeight, logoWidth, logoHeight)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load logo: ${e.message}")
                }
            }

            // Invoice title
            contentStream.beginText()
            contentStream.setFont(fontBold, 28f)
            contentStream.newLineAtOffset(pageWidth / 2 - 50f, yPosition - 25f)
            contentStream.showText("INVOICE")
            contentStream.endText()

            // Invoice number and date
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(pageWidth - MARGIN - 150f, yPosition - 10f)
            contentStream.showText("Invoice #: ${data.invoiceNumber}")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(pageWidth - MARGIN - 150f, yPosition - 24f)
            contentStream.showText("Date: ${data.invoiceDate}")
            contentStream.endText()

            yPosition -= HEADER_HEIGHT

            // Separator
            contentStream.moveTo(MARGIN, yPosition)
            contentStream.lineTo(pageWidth - MARGIN, yPosition)
            contentStream.stroke()

            // ===== BILL FROM / BILL TO =====
            yPosition -= 30f
            val columnWidth = (pageWidth - MARGIN * 2) / 2

            // Bill From
            contentStream.beginText()
            contentStream.setFont(fontBold, 12f)
            contentStream.newLineAtOffset(MARGIN, yPosition)
            contentStream.showText("BILL FROM")
            contentStream.endText()
            yPosition -= LINE_HEIGHT + 4f

            data.companyInfo?.let { company ->
                contentStream.beginText()
                contentStream.setFont(fontBold, 11f)
                contentStream.newLineAtOffset(MARGIN, yPosition)
                contentStream.showText(company.name)
                contentStream.endText()
                yPosition -= LINE_HEIGHT

                company.address?.let { addr ->
                    buildAddressLines(addr).forEach { line ->
                        contentStream.beginText()
                        contentStream.setFont(fontRegular, 10f)
                        contentStream.newLineAtOffset(MARGIN, yPosition)
                        contentStream.showText(line)
                        contentStream.endText()
                        yPosition -= LINE_HEIGHT
                    }
                }

                company.taxId?.let { taxId ->
                    contentStream.beginText()
                    contentStream.setFont(fontRegular, 10f)
                    contentStream.newLineAtOffset(MARGIN, yPosition)
                    contentStream.showText("Tax ID: $taxId")
                    contentStream.endText()
                    yPosition -= LINE_HEIGHT
                }
            }

            // Bill To (right column)
            var rightY = yPosition + (LINE_HEIGHT * 5) // Reset to same starting point
            contentStream.beginText()
            contentStream.setFont(fontBold, 12f)
            contentStream.newLineAtOffset(MARGIN + columnWidth, rightY + LINE_HEIGHT + 4f)
            contentStream.showText("BILL TO")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(fontBold, 11f)
            contentStream.newLineAtOffset(MARGIN + columnWidth, rightY)
            contentStream.showText(data.customer.name)
            contentStream.endText()
            rightY -= LINE_HEIGHT

            data.billingAddress?.let { addr ->
                buildAddressLines(addr).forEach { line ->
                    contentStream.beginText()
                    contentStream.setFont(fontRegular, 10f)
                    contentStream.newLineAtOffset(MARGIN + columnWidth, rightY)
                    contentStream.showText(line)
                    contentStream.endText()
                    rightY -= LINE_HEIGHT
                }
            }

            // ===== LINE ITEMS TABLE =====
            yPosition -= 40f
            val tableWidth = pageWidth - MARGIN * 2

            // Table header
            contentStream.setNonStrokingColor(0.2f, 0.2f, 0.2f)
            contentStream.addRect(MARGIN, yPosition - 20f, tableWidth, 20f)
            contentStream.fill()
            contentStream.setNonStrokingColor(1f, 1f, 1f)

            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(MARGIN + 10f, yPosition - 14f)
            contentStream.showText("Description")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(pageWidth - MARGIN - 200f, yPosition - 14f)
            contentStream.showText("Qty")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(pageWidth - MARGIN - 130f, yPosition - 14f)
            contentStream.showText("Unit Price")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(pageWidth - MARGIN - 60f, yPosition - 14f)
            contentStream.showText("Total")
            contentStream.endText()

            contentStream.setNonStrokingColor(0f, 0f, 0f)
            yPosition -= 24f

            // Line items
            data.lineItems.forEach { item ->
                if (data.lineItems.indexOf(item) % 2 == 0) {
                    contentStream.setNonStrokingColor(0.95f, 0.95f, 0.95f)
                    contentStream.addRect(MARGIN, yPosition - 18f, tableWidth, 18f)
                    contentStream.fill()
                    contentStream.setNonStrokingColor(0f, 0f, 0f)
                }

                contentStream.beginText()
                contentStream.setFont(fontRegular, 9f)
                contentStream.newLineAtOffset(MARGIN + 10f, yPosition - 12f)
                contentStream.showText(truncateText(item.description, 50))
                contentStream.endText()

                contentStream.beginText()
                contentStream.setFont(fontRegular, 9f)
                contentStream.newLineAtOffset(pageWidth - MARGIN - 195f, yPosition - 12f)
                contentStream.showText(item.quantity.toString())
                contentStream.endText()

                contentStream.beginText()
                contentStream.setFont(fontRegular, 9f)
                contentStream.newLineAtOffset(pageWidth - MARGIN - 130f, yPosition - 12f)
                contentStream.showText("$${item.unitPrice.setScale(2)}")
                contentStream.endText()

                contentStream.beginText()
                contentStream.setFont(fontRegular, 9f)
                contentStream.newLineAtOffset(pageWidth - MARGIN - 60f, yPosition - 12f)
                contentStream.showText("$${item.totalPrice.setScale(2)}")
                contentStream.endText()

                yPosition -= 18f
            }

            // ===== TOTALS =====
            yPosition -= 20f
            val totalsX = pageWidth - MARGIN - 160f

            // Subtotal
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(totalsX, yPosition)
            contentStream.showText("Subtotal:")
            contentStream.endText()
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(totalsX + 100f, yPosition)
            contentStream.showText("$${data.subtotal.setScale(2)}")
            contentStream.endText()
            yPosition -= LINE_HEIGHT

            // Shipping
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(totalsX, yPosition)
            contentStream.showText("Shipping:")
            contentStream.endText()
            contentStream.beginText()
            contentStream.setFont(fontRegular, 10f)
            contentStream.newLineAtOffset(totalsX + 100f, yPosition)
            contentStream.showText("$${data.shippingAmount.setScale(2)}")
            contentStream.endText()
            yPosition -= LINE_HEIGHT

            // Tax
            if (data.taxAmount > BigDecimal.ZERO) {
                contentStream.beginText()
                contentStream.setFont(fontRegular, 10f)
                contentStream.newLineAtOffset(totalsX, yPosition)
                contentStream.showText("Tax:")
                contentStream.endText()
                contentStream.beginText()
                contentStream.setFont(fontRegular, 10f)
                contentStream.newLineAtOffset(totalsX + 100f, yPosition)
                contentStream.showText("$${data.taxAmount.setScale(2)}")
                contentStream.endText()
                yPosition -= LINE_HEIGHT
            }

            // Discount
            if (data.discountAmount > BigDecimal.ZERO) {
                contentStream.beginText()
                contentStream.setFont(fontRegular, 10f)
                contentStream.newLineAtOffset(totalsX, yPosition)
                contentStream.showText("Discount:")
                contentStream.endText()
                contentStream.beginText()
                contentStream.setFont(fontRegular, 10f)
                contentStream.newLineAtOffset(totalsX + 100f, yPosition)
                contentStream.showText("-$${data.discountAmount.setScale(2)}")
                contentStream.endText()
                yPosition -= LINE_HEIGHT
            }

            // Total
            yPosition -= 5f
            contentStream.moveTo(totalsX, yPosition)
            contentStream.lineTo(totalsX + 160f, yPosition)
            contentStream.stroke()
            yPosition -= LINE_HEIGHT

            contentStream.beginText()
            contentStream.setFont(fontBold, 12f)
            contentStream.newLineAtOffset(totalsX, yPosition)
            contentStream.showText("Total:")
            contentStream.endText()
            contentStream.beginText()
            contentStream.setFont(fontBold, 12f)
            contentStream.newLineAtOffset(totalsX + 100f, yPosition)
            contentStream.showText("$${data.totalAmount.setScale(2)}")
            contentStream.endText()

            // ===== FOOTER =====
            val footerY = MARGIN + 20f
            contentStream.beginText()
            contentStream.setFont(fontRegular, 8f)
            contentStream.newLineAtOffset(MARGIN, footerY)
            contentStream.showText("This invoice was generated electronically and is valid without a signature.")
            contentStream.endText()

            contentStream.close()

            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }

    // =====================================================
    // PDF CREATION - SHIPPING LABEL
    // =====================================================

    private fun createLabelPdf(
        data: LabelData,
        options: PdfGenerationOptions
    ): ByteArray {
        val document = PDDocument()

        try {
            // 4x6 label size
            val pageSize = PDRectangle(288f, 432f) // 4" x 6" at 72 dpi
            val page = PDPage(pageSize)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = pageSize.width
            val pageHeight = pageSize.height
            var yPosition = pageHeight - 20f

            val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

            // FROM ADDRESS
            contentStream.beginText()
            contentStream.setFont(fontBold, 8f)
            contentStream.newLineAtOffset(20f, yPosition)
            contentStream.showText("FROM:")
            contentStream.endText()
            yPosition -= 12f

            buildAddressLines(data.fromAddress).forEach { line ->
                contentStream.beginText()
                contentStream.setFont(fontRegular, 8f)
                contentStream.newLineAtOffset(20f, yPosition)
                contentStream.showText(line)
                contentStream.endText()
                yPosition -= 10f
            }

            // Separator line
            yPosition -= 10f
            contentStream.moveTo(20f, yPosition)
            contentStream.lineTo(pageWidth - 20f, yPosition)
            contentStream.stroke()

            // TO ADDRESS
            yPosition -= 20f
            contentStream.beginText()
            contentStream.setFont(fontBold, 10f)
            contentStream.newLineAtOffset(20f, yPosition)
            contentStream.showText("SHIP TO:")
            contentStream.endText()
            yPosition -= 16f

            buildAddressLines(data.toAddress).forEach { line ->
                contentStream.beginText()
                contentStream.setFont(fontBold, 12f)
                contentStream.newLineAtOffset(20f, yPosition)
                contentStream.showText(line)
                contentStream.endText()
                yPosition -= 14f
            }

            // Carrier and service
            yPosition -= 20f
            data.carrier?.let { carrier ->
                contentStream.beginText()
                contentStream.setFont(fontBold, 10f)
                contentStream.newLineAtOffset(20f, yPosition)
                contentStream.showText("Carrier: $carrier")
                contentStream.endText()
                yPosition -= 14f
            }

            data.service?.let { service ->
                contentStream.beginText()
                contentStream.setFont(fontRegular, 9f)
                contentStream.newLineAtOffset(20f, yPosition)
                contentStream.showText("Service: $service")
                contentStream.endText()
                yPosition -= 14f
            }

            // Tracking barcode/QR
            yPosition -= 20f
            data.trackingNumber?.let { tracking ->
                // Draw tracking number text
                contentStream.beginText()
                contentStream.setFont(fontBold, 10f)
                contentStream.newLineAtOffset(pageWidth / 2 - 60f, yPosition)
                contentStream.showText(tracking)
                contentStream.endText()

                // Draw barcode as QR code
                yPosition -= 80f
                try {
                    val qrCodeWriter = QRCodeWriter()
                    val bitMatrix = qrCodeWriter.encode(tracking, BarcodeFormat.QR_CODE, 80, 80)
                    val qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix)

                    val baos = ByteArrayOutputStream()
                    ImageIO.write(qrImage, "PNG", baos)
                    val qrImageXObject = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "barcode")

                    contentStream.drawImage(qrImageXObject, pageWidth / 2 - 40f, yPosition, 80f, 80f)
                } catch (e: Exception) {
                    logger.warn("Failed to generate tracking barcode: ${e.message}")
                }
            }

            // Order reference
            contentStream.beginText()
            contentStream.setFont(fontRegular, 8f)
            contentStream.newLineAtOffset(20f, 30f)
            contentStream.showText("Order: ${data.intOrderId ?: data.orderId}")
            contentStream.endText()

            contentStream.close()

            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun buildPackingSlipData(
        order: OrderFull,
        companyInfo: CompanyInfo?,
        options: PdfGenerationOptions
    ): PackingSlipData {
        val products = order.products.map { product ->
            PackingSlipProduct(
                title = product.productDetail?.product ?: "Unknown Product",
                option1 = product.productDetail?.option1,
                option2 = product.productDetail?.option2,
                quantity = product.quantity,
                modifications = product.modificationDetail.map { mod ->
                    PackingSlipModification(
                        name = mod.modificationName,
                        designUrl = mod.modificationDesign,
                        stitchCount = mod.modificationStitchCount
                    )
                },
                thumbnailUrl = product.listingImageUrl
            )
        }

        return PackingSlipData(
            orderId = order.id,
            intOrderId = order.intOrderId,
            externalOrderId = order.externalOrderId,
            orderDate = formatDate(order.createdAt),
            customer = CustomerInfo(
                name = order.customerName ?: "Customer",
                email = order.customerEmail
            ),
            shippingAddress = order.shippingAddress ?: order.orderInfo?.toAddress,
            products = products,
            orderNote = order.orderInfo?.orderNote,
            giftNote = order.giftNote ?: order.orderInfo?.giftNote,
            trackingNumber = order.trackingNumber,
            trackingUrl = order.trackingUrl,
            companyInfo = companyInfo,
            includeLabel = options.includeLabel,
            labelUrl = order.orderInfo?.shipping?.labelUrl
        )
    }

    private fun buildInvoiceData(
        order: OrderFull,
        companyInfo: CompanyInfo?
    ): InvoiceData {
        val lineItems = order.products.map { product ->
            val description = buildString {
                append(product.productDetail?.product ?: "Product")
                product.productDetail?.option1?.let { if (it.isNotBlank()) append(" - $it") }
                product.productDetail?.option2?.let { if (it.isNotBlank()) append(" / $it") }
            }

            InvoiceLineItem(
                description = description,
                quantity = product.quantity,
                unitPrice = product.priceBreakdown?.unitTotal ?: product.unitPrice,
                totalPrice = product.priceBreakdown?.lineTotal ?: (product.unitPrice * BigDecimal(product.quantity)),
                productId = product.productId
            )
        }

        val subtotal = lineItems.sumOf { it.totalPrice }
        val invoiceNumber = "INV-${order.id}-${System.currentTimeMillis() / 1000}"

        return InvoiceData(
            invoiceNumber = invoiceNumber,
            invoiceDate = formatDate(Instant.now().toString()),
            orderId = order.id,
            intOrderId = order.intOrderId,
            customer = CustomerInfo(
                name = order.customerName ?: "Customer",
                email = order.customerEmail
            ),
            billingAddress = order.billingAddress ?: order.shippingAddress,
            shippingAddress = order.shippingAddress,
            lineItems = lineItems,
            subtotal = subtotal,
            shippingAmount = order.shippingAmount,
            taxAmount = order.taxAmount,
            totalAmount = order.totalAmount,
            companyInfo = companyInfo
        )
    }

    private fun buildLabelData(
        order: OrderFull,
        companyInfo: CompanyInfo?
    ): LabelData {
        val fromAddress = companyInfo?.address ?: Address(
            name = companyInfo?.name ?: "PrintNest",
            street1 = "123 Print Street",
            city = "Dallas",
            state = "TX",
            postalCode = "75001",
            country = "US"
        )

        val toAddress = order.shippingAddress ?: order.orderInfo?.toAddress ?: Address()

        return LabelData(
            orderId = order.id,
            intOrderId = order.intOrderId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            carrier = order.orderInfo?.shipping?.methodName?.split(" ")?.firstOrNull(),
            service = order.orderInfo?.shipping?.methodName,
            trackingNumber = order.trackingNumber
        )
    }

    private fun getCompanyInfo(tenantId: Long): CompanyInfo? {
        return try {
            val tenant = settingsRepository.getTenantById(tenantId)
            tenant?.let {
                CompanyInfo(
                    name = it.name,
                    logoUrl = it.settings?.logoUrl,
                    address = it.settings?.address?.let { addr ->
                        Address(
                            name = addr.name,
                            company = addr.company,
                            street1 = addr.street1,
                            street2 = addr.street2,
                            city = addr.city,
                            state = addr.state,
                            postalCode = addr.postalCode,
                            country = addr.country,
                            phone = addr.phone
                        )
                    },
                    email = it.settings?.contactEmail,
                    phone = it.settings?.contactPhone,
                    website = it.settings?.website,
                    taxId = it.settings?.taxId
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to get company info: ${e.message}")
            null
        }
    }

    private fun uploadPdfToS3(tenantId: Long, pdfBytes: ByteArray, key: String, fileName: String): String? {
        return s3Service.uploadBytes(tenantId, pdfBytes, key, "application/pdf", fileName)
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

    private fun downloadImage(url: String): ByteArray? {
        return try {
            URI(url).toURL().openStream().use { it.readBytes() }
        } catch (e: Exception) {
            logger.warn("Failed to download image from $url: ${e.message}")
            null
        }
    }

    private fun getPageSize(paperSize: String): PDRectangle {
        return when (paperSize.uppercase()) {
            "LETTER" -> PDRectangle.LETTER
            "4X6" -> PDRectangle(288f, 432f)
            else -> PDRectangle.A4
        }
    }

    private fun buildAddressLines(address: Address): List<String> {
        return listOfNotNull(
            address.name,
            address.company,
            address.street1,
            address.street2?.takeIf { it.isNotBlank() },
            listOfNotNull(
                address.city,
                address.state,
                address.postalCode
            ).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() },
            address.country
        )
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.take(maxLength - 3) + "..."
        } else {
            text
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val instant = Instant.parse(dateString)
            DATE_FORMATTER.format(instant)
        } catch (e: Exception) {
            dateString.take(10)
        }
    }

    private fun getTopModificationName(order: OrderFull): String {
        return order.products
            .flatMap { it.modificationDetail }
            .maxByOrNull { it.modificationStitchCount }
            ?.modificationName ?: "Unknown"
    }

    private fun getTopStitchCount(order: OrderFull): Int {
        return order.products
            .flatMap { it.modificationDetail }
            .maxOfOrNull { it.modificationStitchCount } ?: 0
    }

    /**
     * Get PDF download info by file ID
     */
    fun getDownloadInfo(fileId: String, tenantId: Long): PdfDownloadInfo? {
        val key = "$PDF_PREFIX/$tenantId"
        // Search for file in S3 with the given fileId
        // This is a simplified version - in production you might want to store file metadata in DB
        return try {
            val url = s3Service.generateDownloadUrl(tenantId, "$key/packing-slips/$fileId", 3600)
            PdfDownloadInfo(
                fileId = fileId,
                fileName = "document.pdf",
                fileUrl = url,
                fileSize = 0,
                contentType = "application/pdf",
                expiresAt = Instant.now().plusSeconds(3600).toString()
            )
        } catch (e: Exception) {
            logger.error("Failed to get download info for file $fileId", e)
            null
        }
    }
}

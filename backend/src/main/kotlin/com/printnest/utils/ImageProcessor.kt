package com.printnest.utils

import com.printnest.domain.models.DesignPlacement
import com.printnest.domain.models.GangsheetSettingsFull
import com.printnest.domain.models.RollPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Image Processor for Gangsheet Generation
 *
 * Handles:
 * - Downloading design images from URLs
 * - Resizing and rotating images
 * - Adding borders
 * - Placing designs on rolls
 * - Generating PNG output
 * - Creating ZIP archives
 */
class ImageProcessor {

    private val logger = LoggerFactory.getLogger(ImageProcessor::class.java)

    companion object {
        private const val FOOTER_HEIGHT_INCHES = 1.5
        private const val FONT_SIZE = 36
        private const val QR_SIZE = 150
    }

    /**
     * Download an image from URL
     */
    suspend fun downloadImage(url: String): BufferedImage? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            ImageIO.read(uri.toURL())
        } catch (e: Exception) {
            logger.error("Failed to download image from $url", e)
            null
        }
    }

    /**
     * Resize an image to target dimensions
     */
    fun resizeImage(
        image: BufferedImage,
        targetWidth: Int,
        targetHeight: Int,
        highQuality: Boolean = true
    ): BufferedImage {
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = resized.createGraphics()

        if (highQuality) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        return resized
    }

    /**
     * Rotate an image 90 degrees clockwise
     */
    fun rotateImage90(image: BufferedImage): BufferedImage {
        val rotated = BufferedImage(image.height, image.width, BufferedImage.TYPE_INT_ARGB)
        val g2d = rotated.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val transform = AffineTransform()
        transform.translate(image.height.toDouble(), 0.0)
        transform.rotate(Math.PI / 2)

        g2d.transform = transform
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()

        return rotated
    }

    /**
     * Add border around an image
     */
    fun addBorder(
        image: BufferedImage,
        borderSize: Int,
        borderColor: Color
    ): BufferedImage {
        val newWidth = image.width + (borderSize * 2)
        val newHeight = image.height + (borderSize * 2)

        val bordered = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = bordered.createGraphics()

        // Draw border
        g2d.color = borderColor
        g2d.fillRect(0, 0, newWidth, newHeight)

        // Draw image centered
        g2d.drawImage(image, borderSize, borderSize, null)
        g2d.dispose()

        return bordered
    }

    /**
     * Parse CSS color string to Color object
     */
    fun parseColor(colorString: String): Color {
        return when {
            colorString.startsWith("#") -> {
                val hex = colorString.substring(1)
                when (hex.length) {
                    3 -> {
                        val r = hex[0].toString().repeat(2).toInt(16)
                        val g = hex[1].toString().repeat(2).toInt(16)
                        val b = hex[2].toString().repeat(2).toInt(16)
                        Color(r, g, b)
                    }
                    6 -> Color(hex.toInt(16))
                    8 -> Color(hex.toLong(16).toInt(), true)
                    else -> Color.RED
                }
            }
            colorString.equals("red", ignoreCase = true) -> Color.RED
            colorString.equals("blue", ignoreCase = true) -> Color.BLUE
            colorString.equals("green", ignoreCase = true) -> Color.GREEN
            colorString.equals("black", ignoreCase = true) -> Color.BLACK
            colorString.equals("white", ignoreCase = true) -> Color.WHITE
            colorString.equals("yellow", ignoreCase = true) -> Color.YELLOW
            colorString.equals("cyan", ignoreCase = true) -> Color.CYAN
            colorString.equals("magenta", ignoreCase = true) -> Color.MAGENTA
            colorString.equals("orange", ignoreCase = true) -> Color.ORANGE
            colorString.equals("pink", ignoreCase = true) -> Color.PINK
            else -> Color.RED
        }
    }

    /**
     * Generate a gangsheet roll image
     */
    suspend fun generateRollImage(
        roll: RollPlacement,
        settings: GangsheetSettingsFull,
        gangsheetName: String,
        downloadDesign: suspend (String) -> BufferedImage?
    ): ByteArray = withContext(Dispatchers.IO) {
        val rollWidthPixels = (settings.rollWidth * settings.dpi).toInt()
        val footerHeightPixels = (FOOTER_HEIGHT_INCHES * settings.dpi).toInt()
        val rollHeightPixels = roll.maxHeight + footerHeightPixels

        // Create the roll canvas
        val rollImage = BufferedImage(rollWidthPixels, rollHeightPixels, BufferedImage.TYPE_INT_ARGB)
        val g2d = rollImage.createGraphics()

        // Set high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Fill background (transparent for DTF)
        g2d.color = Color(0, 0, 0, 0) // Transparent
        g2d.fillRect(0, 0, rollWidthPixels, rollHeightPixels)

        // Border settings
        val borderSizePixels = (settings.borderSize * settings.dpi).toInt()
        val borderColor = parseColor(settings.borderColor)
        val gapPixels = (settings.gap * settings.dpi).toInt()

        // Process each placement
        for (placement in roll.placements) {
            val designUrl = placement.designUrl ?: continue

            try {
                // Download the design image
                var designImage = downloadDesign(designUrl) ?: continue

                // Resize to print size
                designImage = resizeImage(designImage, placement.printWidth, placement.printHeight)

                // Rotate if needed
                if (placement.rotated) {
                    designImage = rotateImage90(designImage)
                }

                // Add border if enabled
                val finalImage = if (settings.border) {
                    addBorder(designImage, borderSizePixels, borderColor)
                } else {
                    designImage
                }

                // Calculate position with gap offset
                val x = placement.x + gapPixels
                val y = placement.y + gapPixels

                // Draw on roll
                g2d.drawImage(finalImage, x, y, null)

            } catch (e: Exception) {
                logger.error("Failed to process design at (${placement.x}, ${placement.y})", e)
            }
        }

        // Draw footer
        drawFooter(
            g2d,
            roll,
            gangsheetName,
            rollWidthPixels,
            roll.maxHeight,
            footerHeightPixels,
            settings.dpi
        )

        g2d.dispose()

        // Convert to PNG bytes
        val baos = ByteArrayOutputStream()
        ImageIO.write(rollImage, "PNG", baos)
        baos.toByteArray()
    }

    /**
     * Draw footer with gangsheet info
     */
    private fun drawFooter(
        g2d: Graphics2D,
        roll: RollPlacement,
        gangsheetName: String,
        rollWidth: Int,
        yOffset: Int,
        footerHeight: Int,
        dpi: Int
    ) {
        // Footer background (white)
        g2d.color = Color.WHITE
        g2d.fillRect(0, yOffset, rollWidth, footerHeight)

        // Footer border (top line)
        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(2f)
        g2d.drawLine(0, yOffset, rollWidth, yOffset)

        // Text settings
        val fontSize = (dpi * 0.15).toInt() // Scale font with DPI
        g2d.font = Font("Arial", Font.BOLD, fontSize)
        g2d.color = Color.BLACK

        val fm = g2d.fontMetrics
        val lineHeight = fm.height + 10
        var textY = yOffset + lineHeight

        // Line 1: Gangsheet name and roll number
        val line1 = "$gangsheetName - Roll ${roll.rollNumber}"
        g2d.drawString(line1, 20, textY)
        textY += lineHeight

        // Line 2: Design count
        val line2 = "Designs: ${roll.placements.size}"
        g2d.drawString(line2, 20, textY)
        textY += lineHeight

        // Line 3: Order IDs
        val orderIdsStr = "Orders: ${roll.orderIds.take(10).joinToString(", ")}" +
            if (roll.orderIds.size > 10) "..." else ""
        g2d.drawString(orderIdsStr, 20, textY)

        // Optional: Add QR code on the right side (placeholder)
        // In production, you would use a QR code library like ZXing
        val qrX = rollWidth - QR_SIZE - 20
        val qrY = yOffset + 20
        g2d.color = Color.LIGHT_GRAY
        g2d.fillRect(qrX, qrY, QR_SIZE, QR_SIZE)
        g2d.color = Color.BLACK
        g2d.drawRect(qrX, qrY, QR_SIZE, QR_SIZE)
        g2d.font = Font("Arial", Font.PLAIN, 12)
        g2d.drawString("QR", qrX + QR_SIZE / 2 - 10, qrY + QR_SIZE / 2 + 5)
    }

    /**
     * Create a ZIP archive from multiple roll images
     */
    fun createZipArchive(
        rolls: List<Pair<String, ByteArray>>,
        gangsheetName: String
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((fileName, imageData) in rolls) {
                val entry = ZipEntry(fileName)
                zos.putNextEntry(entry)
                zos.write(imageData)
                zos.closeEntry()
            }

            // Add info.txt with metadata
            val infoEntry = ZipEntry("info.txt")
            zos.putNextEntry(infoEntry)
            val info = """
                Gangsheet: $gangsheetName
                Generated: ${java.time.LocalDateTime.now()}
                Total Rolls: ${rolls.size}
            """.trimIndent()
            zos.write(info.toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    /**
     * Scale dimensions to fit within max dimensions while maintaining aspect ratio
     */
    fun scaleToFit(
        originalWidth: Int,
        originalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        val widthRatio = maxWidth.toDouble() / originalWidth
        val heightRatio = maxHeight.toDouble() / originalHeight
        val ratio = minOf(widthRatio, heightRatio)

        return Pair(
            (originalWidth * ratio).toInt(),
            (originalHeight * ratio).toInt()
        )
    }

    /**
     * Calculate dimensions for a specific print size (in inches) at given DPI
     */
    fun calculatePrintDimensions(
        originalWidth: Int,
        originalHeight: Int,
        printSizeInches: Double,
        dpi: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toDouble() / originalHeight

        return if (originalWidth > originalHeight) {
            // Landscape: width is the constraint
            val newWidth = (printSizeInches * dpi).toInt()
            val newHeight = (newWidth / aspectRatio).toInt()
            Pair(newWidth, newHeight)
        } else {
            // Portrait or square: height is the constraint
            val newHeight = (printSizeInches * dpi).toInt()
            val newWidth = (newHeight * aspectRatio).toInt()
            Pair(newWidth, newHeight)
        }
    }

    /**
     * Convert BufferedImage to PNG byte array
     */
    fun toPngBytes(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Load image from byte array
     */
    fun fromBytes(bytes: ByteArray): BufferedImage? {
        return try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            logger.error("Failed to load image from bytes", e)
            null
        }
    }

    /**
     * Save image to file (for debugging)
     */
    fun saveToFile(image: BufferedImage, path: String) {
        try {
            ImageIO.write(image, "PNG", File(path))
            logger.info("Saved image to $path")
        } catch (e: Exception) {
            logger.error("Failed to save image to $path", e)
        }
    }
}

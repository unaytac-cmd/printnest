package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.GangsheetRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.DesignRepository
import com.printnest.domain.repository.SettingsRepository
import com.printnest.integrations.aws.S3Service
import com.printnest.utils.ImageProcessor
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class GangsheetService(
    private val gangsheetRepository: GangsheetRepository,
    private val orderRepository: OrderRepository,
    private val designRepository: DesignRepository,
    private val settingsRepository: SettingsRepository,
    private val s3Service: S3Service
) {
    private val logger = LoggerFactory.getLogger(GangsheetService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val imageProcessor = ImageProcessor()
    private val imageCache = ConcurrentHashMap<String, BufferedImage>()

    companion object {
        private const val GANGSHEET_PREFIX = "gangsheets"
        private const val DEFAULT_DPI = 300
        private const val MAX_CACHE_SIZE = 100
    }

    // =====================================================
    // GANGSHEET LISTING
    // =====================================================

    fun getGangsheets(tenantId: Long, filters: GangsheetFilters): GangsheetListResponse {
        val (gangsheets, total) = gangsheetRepository.findAll(tenantId, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return GangsheetListResponse(
            gangsheets = gangsheets,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getGangsheet(id: Long, tenantId: Long, withRolls: Boolean = true): Gangsheet? {
        return if (withRolls) {
            gangsheetRepository.findByIdWithRolls(id, tenantId)
        } else {
            gangsheetRepository.findById(id, tenantId)
        }
    }

    // =====================================================
    // GANGSHEET CREATION
    // =====================================================

    fun createGangsheet(
        tenantId: Long,
        request: CreateGangsheetRequest
    ): Result<GangsheetResponse> {
        // Validate order IDs
        if (request.orderIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one order ID is required"))
        }

        // Get tenant default settings if not provided
        val settings = request.settings ?: getDefaultSettings(tenantId)

        // Generate name if not provided
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(java.time.LocalDateTime.now())
        val name = request.name ?: "GS_${timestamp}"

        // Create gangsheet record
        val gangsheet = gangsheetRepository.create(tenantId, name, request.orderIds, settings)

        // Start async processing
        startGangsheetGeneration(gangsheet.id, tenantId, request)

        return Result.success(GangsheetResponse(
            gangsheet = gangsheet,
            message = "Gangsheet creation started"
        ))
    }

    private fun startGangsheetGeneration(
        gangsheetId: Long,
        tenantId: Long,
        request: CreateGangsheetRequest
    ) {
        scope.launch {
            try {
                processGangsheet(gangsheetId, tenantId, request)
            } catch (e: Exception) {
                logger.error("Failed to generate gangsheet $gangsheetId", e)
                gangsheetRepository.fail(gangsheetId, tenantId, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun processGangsheet(
        gangsheetId: Long,
        tenantId: Long,
        request: CreateGangsheetRequest
    ) {
        val gangsheet = gangsheetRepository.findById(gangsheetId, tenantId)
            ?: throw IllegalStateException("Gangsheet not found")

        val settings = gangsheet.settings

        // Step 1: Fetch designs for orders
        gangsheetRepository.updateStatus(gangsheetId, tenantId, GangsheetStatus.FETCHING_DESIGNS)
        val designs = getOrdersForGangsheet(tenantId, request.orderIds, request.products)

        if (designs.isEmpty()) {
            gangsheetRepository.fail(gangsheetId, tenantId, "No designs found for the selected orders")
            return
        }

        gangsheetRepository.setTotalDesigns(gangsheetId, tenantId, designs.sumOf { it.quantity })

        // Step 2: Calculate new sizes for designs
        gangsheetRepository.updateStatus(gangsheetId, tenantId, GangsheetStatus.CALCULATING)
        val processedDesigns = calculateNewSizes(designs, settings)

        // Step 3: Calculate placements (row-based packing)
        val placements = calculateDesignPlacements(
            processedDesigns,
            settings.rollWidth,
            settings.rollLength,
            settings.dpi
        )

        gangsheetRepository.update(gangsheetId, tenantId) {
            it[totalRolls] = placements.totalRolls
        }

        // Step 4: Generate gangsheet images
        gangsheetRepository.updateStatus(gangsheetId, tenantId, GangsheetStatus.GENERATING)
        val generatedRolls = generateGangsheetImages(
            gangsheetId,
            tenantId,
            placements,
            settings,
            gangsheet.name
        )

        // Step 5: Upload to S3
        gangsheetRepository.updateStatus(gangsheetId, tenantId, GangsheetStatus.UPLOADING)
        val uploadResult = uploadToS3(gangsheetId, tenantId, gangsheet.name, generatedRolls)

        // Step 6: Complete
        gangsheetRepository.complete(
            gangsheetId,
            tenantId,
            uploadResult.downloadUrl,
            placements.totalRolls,
            uploadResult.rollUrls
        )

        logger.info("Gangsheet $gangsheetId completed successfully with ${placements.totalRolls} rolls")
    }

    // =====================================================
    // GANGSHEET STATUS
    // =====================================================

    fun getGangsheetStatus(id: Long, tenantId: Long): Result<GangsheetStatusResponse> {
        val gangsheet = gangsheetRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Gangsheet not found"))

        val status = GangsheetStatus.fromCode(gangsheet.status)
        val progress = calculateProgress(gangsheet)

        return Result.success(GangsheetStatusResponse(
            id = gangsheet.id,
            status = status.code,
            statusLabel = status.label,
            progress = progress,
            currentStep = status.label,
            totalDesigns = gangsheet.totalDesigns,
            processedDesigns = gangsheet.processedDesigns,
            totalRolls = gangsheet.totalRolls,
            errorMessage = gangsheet.errorMessage,
            downloadUrl = gangsheet.downloadUrl
        ))
    }

    private fun calculateProgress(gangsheet: Gangsheet): Int {
        return when (GangsheetStatus.fromCode(gangsheet.status)) {
            GangsheetStatus.PENDING -> 0
            GangsheetStatus.FETCHING_DESIGNS -> 10
            GangsheetStatus.CALCULATING -> 30
            GangsheetStatus.GENERATING -> {
                if (gangsheet.totalDesigns > 0) {
                    30 + ((gangsheet.processedDesigns.toDouble() / gangsheet.totalDesigns) * 50).toInt()
                } else 50
            }
            GangsheetStatus.UPLOADING -> 90
            GangsheetStatus.COMPLETED -> 100
            GangsheetStatus.FAILED -> 0
        }
    }

    // =====================================================
    // GET ORDERS FOR GANGSHEET
    // =====================================================

    fun getOrdersForGangsheet(
        tenantId: Long,
        orderIds: List<Long>,
        productOverrides: List<GangsheetProductOverride>? = null
    ): List<GangsheetDesign> {
        val designs = mutableListOf<GangsheetDesign>()

        for (orderId in orderIds) {
            val order = orderRepository.findByIdWithProducts(orderId, tenantId) ?: continue

            for (product in order.products) {
                // Skip non-DTF products (designType != 1)
                val productDetail = product.productDetail
                // For now, include all products with designs

                // Get design info
                val designId = product.designId
                if (designId == null) {
                    // Check modification detail for design
                    for (mod in product.modificationDetail) {
                        if (mod.modificationDesignId == null) continue

                        val design = designRepository.findById(mod.modificationDesignId, tenantId) ?: continue

                        // Get print size from variant
                        val printSize = productDetail?.variants?.let { variant ->
                            if (mod.modificationUseWidth == 1) {
                                variant.width1?.toDouble() ?: 10.0
                            } else {
                                variant.width2?.toDouble() ?: 10.0
                            }
                        } ?: 10.0

                        // Check for quantity override
                        val quantity = productOverrides?.find { it.orderProductId == product.id }?.quantity
                            ?: product.quantity

                        designs.add(GangsheetDesign(
                            orderProductId = product.id,
                            orderId = orderId,
                            designId = mod.modificationDesignId,
                            designHash = design.fileHash,
                            designUrl = design.designUrl,
                            originalWidth = (design.metadata?.widthPixels ?: ((design.width ?: 10.0) * 300).toInt()),
                            originalHeight = (design.metadata?.heightPixels ?: ((design.height ?: 10.0) * 300).toInt()),
                            printSize = printSize,
                            quantity = quantity,
                            modificationName = mod.modificationName,
                            useWidth = mod.modificationUseWidth
                        ))
                    }
                } else {
                    val design = designRepository.findById(designId, tenantId) ?: continue

                    val printSize = productDetail?.variants?.let { variant ->
                        variant.width1?.toDouble() ?: 10.0
                    } ?: 10.0

                    val quantity = productOverrides?.find { it.orderProductId == product.id }?.quantity
                        ?: product.quantity

                    designs.add(GangsheetDesign(
                        orderProductId = product.id,
                        orderId = orderId,
                        designId = designId,
                        designHash = design.fileHash,
                        designUrl = design.designUrl,
                        originalWidth = (design.metadata?.widthPixels ?: ((design.width ?: 10.0) * 300).toInt()),
                        originalHeight = (design.metadata?.heightPixels ?: ((design.height ?: 10.0) * 300).toInt()),
                        printSize = printSize,
                        quantity = quantity,
                        modificationName = "Primary",
                        useWidth = 1
                    ))
                }
            }
        }

        return designs
    }

    // =====================================================
    // SIZE CALCULATIONS
    // =====================================================

    private fun calculateNewSizes(
        designs: List<GangsheetDesign>,
        settings: GangsheetSettingsFull
    ): List<GangsheetDesign> {
        val emptySpace = if (settings.border) {
            (settings.gap * 2 + settings.borderSize) * settings.dpi
        } else {
            settings.gap * 2 * settings.dpi
        }

        return designs.map { design ->
            val width = design.originalWidth
            val height = design.originalHeight
            val printableSize = design.printSize

            val (newWidth, newHeight, rotated) = if (width > height) {
                // Landscape - scale by width
                val scaledWidth = (printableSize * settings.dpi).toInt()
                val scaledHeight = (height.toDouble() * scaledWidth / width).toInt()

                // Rotate if print size >= 11 inches and landscape
                if (printableSize >= 11) {
                    Triple(scaledHeight, scaledWidth, true)
                } else {
                    Triple(scaledWidth, scaledHeight, false)
                }
            } else {
                // Portrait or square - scale by height
                val scaledHeight = (printableSize * settings.dpi).toInt()
                val scaledWidth = (width.toDouble() * scaledHeight / height).toInt()
                Triple(scaledWidth, scaledHeight, false)
            }

            design.copy().apply {
                this.printWidth = newWidth
                this.printHeight = newHeight
                this.newWidth = (newWidth + emptySpace).toInt()
                this.newHeight = (newHeight + emptySpace).toInt()
                this.rotated = rotated
            }
        }
    }

    // =====================================================
    // DESIGN PLACEMENT ALGORITHM (Row-based packing)
    // =====================================================

    fun calculateDesignPlacements(
        designs: List<GangsheetDesign>,
        rollWidthInches: Double,
        rollLengthInches: Double,
        dpi: Int
    ): PlacementResult {
        val rollWidth = (rollWidthInches * dpi).toInt()
        val rollHeight = (rollLengthInches * dpi).toInt()

        // Expand designs by quantity and assign order numbers
        val expandedDesigns = mutableListOf<Pair<GangsheetDesign, Int>>()
        var orderNumber = 0
        for (design in designs) {
            for (i in 0 until design.quantity) {
                expandedDesigns.add(Pair(design, orderNumber))
                orderNumber++
            }
        }

        // Sort by height (tallest first for better packing)
        // Actually, keep order-based sorting to maintain order grouping
        // expandedDesigns.sortByDescending { it.first.newHeight }

        val rolls = mutableListOf<MutableList<DesignPlacement>>()
        var currentRoll = mutableListOf<DesignPlacement>()
        var x = 0
        var y = 0
        var maxHeightInRow = 0

        for ((design, seq) in expandedDesigns) {
            val designWidth = design.newWidth
            val designHeight = design.newHeight

            when {
                // Check if fits in current row
                x + designWidth <= rollWidth && y + designHeight <= rollHeight -> {
                    currentRoll.add(createPlacement(design, x, y, rolls.size))
                    x += designWidth
                    maxHeightInRow = max(maxHeightInRow, designHeight)
                }
                // Check if fits in next row
                y + maxHeightInRow + designHeight <= rollHeight -> {
                    y += maxHeightInRow
                    x = designWidth
                    currentRoll.add(createPlacement(design, 0, y, rolls.size))
                    maxHeightInRow = designHeight
                }
                // Start new roll
                else -> {
                    if (currentRoll.isNotEmpty()) {
                        rolls.add(currentRoll)
                    }
                    currentRoll = mutableListOf()
                    x = designWidth
                    y = 0
                    currentRoll.add(createPlacement(design, 0, 0, rolls.size))
                    maxHeightInRow = designHeight
                }
            }
        }

        // Add the last roll
        if (currentRoll.isNotEmpty()) {
            rolls.add(currentRoll)
        }

        // Convert to RollPlacement
        val rollPlacements = rolls.mapIndexed { index, placements ->
            val maxY = placements.maxOfOrNull { it.y + it.height } ?: 0
            val orderIdsInRoll = placements.mapNotNull { it.orderId }.distinct()

            RollPlacement(
                rollNumber = index + 1,
                placements = placements,
                maxHeight = maxY,
                orderIds = orderIdsInRoll
            )
        }

        return PlacementResult(
            rolls = rollPlacements,
            totalDesigns = expandedDesigns.size,
            totalRolls = rolls.size
        )
    }

    private fun createPlacement(design: GangsheetDesign, x: Int, y: Int, rollNumber: Int): DesignPlacement {
        return DesignPlacement(
            designId = design.designId,
            orderId = design.orderId,
            orderProductId = design.orderProductId,
            designHash = design.designHash,
            designUrl = design.designUrl,
            x = x,
            y = y,
            width = design.newWidth,
            height = design.newHeight,
            printWidth = design.printWidth,
            printHeight = design.printHeight,
            quantity = 1,
            modificationName = design.modificationName,
            rotated = design.rotated,
            rollNumber = rollNumber
        )
    }

    // =====================================================
    // IMAGE GENERATION
    // =====================================================

    private suspend fun generateGangsheetImages(
        gangsheetId: Long,
        tenantId: Long,
        placements: PlacementResult,
        settings: GangsheetSettingsFull,
        gangsheetName: String
    ): List<GeneratedRoll> {
        val generatedRolls = mutableListOf<GeneratedRoll>()
        var processedCount = 0

        // Clear image cache before processing
        imageCache.clear()

        for (roll in placements.rolls) {
            val rollWidthPixels = (settings.rollWidth * settings.dpi).toInt()
            val footerHeightPixels = (1.5 * settings.dpi).toInt()
            val rollHeightPixels = roll.maxHeight + footerHeightPixels

            try {
                // Generate the actual roll image using ImageProcessor
                val imageData = imageProcessor.generateRollImage(
                    roll = roll,
                    settings = settings,
                    gangsheetName = gangsheetName,
                    downloadDesign = { url -> downloadDesignImage(url) }
                )

                generatedRolls.add(GeneratedRoll(
                    rollNumber = roll.rollNumber,
                    widthPixels = rollWidthPixels,
                    heightPixels = rollHeightPixels,
                    designCount = roll.placements.size,
                    placements = roll.placements,
                    imageData = imageData
                ))

                processedCount += roll.placements.size
                gangsheetRepository.updateProgress(gangsheetId, tenantId, processedCount)

                logger.info("Generated roll ${roll.rollNumber} with ${roll.placements.size} designs")

            } catch (e: Exception) {
                logger.error("Failed to generate roll ${roll.rollNumber}", e)
                // Create placeholder for failed roll
                generatedRolls.add(GeneratedRoll(
                    rollNumber = roll.rollNumber,
                    widthPixels = rollWidthPixels,
                    heightPixels = rollHeightPixels,
                    designCount = roll.placements.size,
                    placements = roll.placements,
                    imageData = null
                ))
            }
        }

        // Clear cache after processing to free memory
        imageCache.clear()

        return generatedRolls
    }

    /**
     * Download design image with caching
     */
    private suspend fun downloadDesignImage(url: String): BufferedImage? {
        // Check cache first
        imageCache[url]?.let { return it }

        // Download image
        val image = imageProcessor.downloadImage(url)

        // Cache if successful and cache not too large
        if (image != null && imageCache.size < MAX_CACHE_SIZE) {
            imageCache[url] = image
        }

        return image
    }

    // =====================================================
    // S3 UPLOAD
    // =====================================================

    private suspend fun uploadToS3(
        gangsheetId: Long,
        tenantId: Long,
        name: String,
        rolls: List<GeneratedRoll>
    ): UploadResult = withContext(Dispatchers.IO) {
        val rollUrls = mutableListOf<String>()
        val rollFiles = mutableListOf<Pair<String, ByteArray>>()
        val filePrefix = "${GANGSHEET_PREFIX}/${tenantId}/${gangsheetId}"

        for (roll in rolls) {
            val fileName = "${name}_${roll.rollNumber.toString().padStart(3, '0')}.png"
            val key = "$filePrefix/$fileName"

            // Upload image data to S3 if available
            val url = if (roll.imageData != null) {
                try {
                    s3Service.uploadBytes(
                        bytes = roll.imageData,
                        key = key,
                        contentType = "image/png"
                    )
                    rollFiles.add(fileName to roll.imageData)
                    s3Service.getPublicUrl(key)
                } catch (e: Exception) {
                    logger.error("Failed to upload roll ${roll.rollNumber} to S3", e)
                    s3Service.getPublicUrl(key) // Return placeholder URL
                }
            } else {
                s3Service.getPublicUrl(key)
            }

            // Create roll in database
            val rollRecord = gangsheetRepository.createRoll(
                gangsheetId = gangsheetId,
                rollNumber = roll.rollNumber,
                widthPixels = roll.widthPixels,
                heightPixels = roll.heightPixels,
                designCount = roll.designCount,
                fileUrl = url
            )

            rollUrls.add(url)
        }

        // Create ZIP archive of all rolls
        val zipKey = "$filePrefix/${name}.zip"
        val downloadUrl = if (rollFiles.isNotEmpty()) {
            try {
                val zipData = imageProcessor.createZipArchive(rollFiles, name)
                s3Service.uploadBytes(
                    bytes = zipData,
                    key = zipKey,
                    contentType = "application/zip"
                )
                s3Service.getPublicUrl(zipKey)
            } catch (e: Exception) {
                logger.error("Failed to create/upload ZIP archive", e)
                s3Service.getPublicUrl(zipKey)
            }
        } else {
            s3Service.getPublicUrl(zipKey)
        }

        logger.info("Uploaded gangsheet $gangsheetId: ${rolls.size} rolls, ZIP at $downloadUrl")

        UploadResult(downloadUrl, rollUrls)
    }

    // =====================================================
    // DOWNLOAD
    // =====================================================

    fun downloadGangsheet(id: Long, tenantId: Long): Result<GangsheetDownloadResponse> {
        val gangsheet = gangsheetRepository.findByIdWithRolls(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Gangsheet not found"))

        if (gangsheet.status != GangsheetStatus.COMPLETED.code) {
            return Result.failure(IllegalStateException("Gangsheet is not yet completed"))
        }

        val downloadUrl = gangsheet.downloadUrl
            ?: return Result.failure(IllegalStateException("Download URL not available"))

        val rollDownloads = gangsheet.rolls.map { roll ->
            RollDownloadInfo(
                rollNumber = roll.rollNumber,
                downloadUrl = roll.fileUrl ?: "",
                width = roll.widthPixels,
                height = roll.heightPixels,
                designCount = roll.designCount
            )
        }

        return Result.success(GangsheetDownloadResponse(
            downloadUrl = downloadUrl,
            expiresIn = 3600,
            rollUrls = rollDownloads
        ))
    }

    // =====================================================
    // DELETE
    // =====================================================

    fun deleteGangsheet(id: Long, tenantId: Long): Result<Boolean> {
        val gangsheet = gangsheetRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Gangsheet not found"))

        // Delete rolls first
        gangsheetRepository.deleteRolls(id)

        // TODO: Delete S3 files if needed

        // Delete gangsheet
        val deleted = gangsheetRepository.delete(id, tenantId)
        return Result.success(deleted)
    }

    // =====================================================
    // SETTINGS
    // =====================================================

    fun getDefaultSettings(tenantId: Long): GangsheetSettingsFull {
        // Try to get from tenant settings
        val tenantSettings = settingsRepository.getTenantSettings(tenantId)
        return tenantSettings?.gangsheetSettings ?: GangsheetSettingsFull()
    }

    fun updateDefaultSettings(tenantId: Long, settings: GangsheetSettingsFull): Result<GangsheetSettingsFull> {
        // Convert to tenant settings format
        val gangsheetSettings = com.printnest.domain.models.GangsheetSettings(
            width = settings.rollWidth,
            height = settings.rollLength,
            dpi = settings.dpi,
            spacing = settings.gap,
            backgroundColor = "#FFFFFF",
            autoArrange = true
        )

        settingsRepository.updateGangsheetSettings(tenantId, gangsheetSettings)

        return Result.success(settings)
    }

    // =====================================================
    // INTERNAL CLASSES
    // =====================================================

    private data class GeneratedRoll(
        val rollNumber: Int,
        val widthPixels: Int,
        val heightPixels: Int,
        val designCount: Int,
        val placements: List<DesignPlacement>,
        val imageData: ByteArray? // PNG image bytes
    )

    private data class UploadResult(
        val downloadUrl: String,
        val rollUrls: List<String>
    )
}

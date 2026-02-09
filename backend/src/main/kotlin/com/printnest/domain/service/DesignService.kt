package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.DesignRepository
import com.printnest.integrations.aws.S3Service
import org.slf4j.LoggerFactory

class DesignService(
    private val designRepository: DesignRepository,
    private val s3Service: S3Service
) {
    private val logger = LoggerFactory.getLogger(DesignService::class.java)

    // =====================================================
    // QUERIES
    // =====================================================

    fun getDesign(id: Long, tenantId: Long): Result<Design> {
        val design = designRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Design not found"))

        return Result.success(design)
    }

    fun getDesigns(tenantId: Long, filters: DesignFilters): DesignListResponse {
        val (designs, total) = designRepository.findAll(tenantId, filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return DesignListResponse(
            designs = designs,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    fun getDesignsByIds(ids: List<Long>, tenantId: Long): List<Design> {
        return designRepository.findByIds(ids, tenantId)
    }

    // =====================================================
    // UPLOAD FLOW
    // =====================================================

    /**
     * Step 1: Generate pre-signed URL for upload
     */
    fun generateUploadUrl(
        tenantId: Long,
        userId: Long,
        request: UploadUrlRequest
    ): Result<UploadUrlResponse> {
        try {
            // Validate file
            val extension = request.fileName.substringAfterLast(".", "")
            if (extension.isBlank()) {
                return Result.failure(IllegalArgumentException("Invalid file name"))
            }

            // Detect design type from extension if not provided
            val designType = request.designType
                ?: DesignType.fromExtension(extension)?.code
                ?: return Result.failure(IllegalArgumentException("Unsupported file type: $extension"))

            // Validate file size (max 100MB for designs)
            val maxSize = 100 * 1024 * 1024L
            if (request.fileSize > maxSize) {
                return Result.failure(IllegalArgumentException("File size exceeds maximum allowed (100MB)"))
            }

            // Generate upload URL
            val uploadResult = s3Service.generateUploadUrl(
                tenantId = tenantId,
                userId = userId,
                fileName = request.fileName,
                contentType = request.mimeType
            )

            if (uploadResult == null) {
                return Result.failure(IllegalStateException("S3 not configured for this tenant"))
            }

            val (uploadUrl, designKey) = uploadResult

            // Generate thumbnail key for image types
            val thumbnailKey = if (designType == DesignType.DTF.code || designType == DesignType.UV.code) {
                s3Service.generateThumbnailKey(designKey)
            } else null

            logger.info("Generated upload URL for user $userId, key: $designKey")

            return Result.success(
                UploadUrlResponse(
                    uploadUrl = uploadUrl,
                    designKey = designKey,
                    thumbnailKey = thumbnailKey,
                    expiresIn = 3600
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to generate upload URL", e)
            return Result.failure(e)
        }
    }

    /**
     * Step 2: Complete upload and create design record
     */
    fun completeUpload(
        tenantId: Long,
        userId: Long,
        request: CompleteUploadRequest
    ): Result<Design> {
        try {
            // Verify file exists in S3
            if (!s3Service.fileExists(tenantId, request.designKey)) {
                return Result.failure(IllegalArgumentException("File not found in storage"))
            }

            // Get file metadata
            val fileMetadata = s3Service.getFileMetadata(tenantId, request.designKey)
            val fileHash = fileMetadata?.eTag?.replace("\"", "") ?: ""

            // Check for duplicates
            val existingDesign = designRepository.findByHash(fileHash, tenantId)
            if (existingDesign != null) {
                logger.info("Duplicate design found: ${existingDesign.id}")
                // Delete the uploaded file since it's a duplicate
                s3Service.deleteFile(tenantId, request.designKey)
                return Result.success(existingDesign)
            }

            // Get public URLs
            val designUrl = s3Service.getPublicUrl(tenantId, request.designKey)
                ?: return Result.failure(IllegalStateException("Failed to get design URL"))
            val thumbnailUrl = request.metadata?.let {
                // TODO: Generate thumbnail in background
                null
            }

            // Build metadata
            val metadata = request.metadata?.copy(
                fileSize = fileMetadata?.size,
                mimeType = fileMetadata?.contentType
            ) ?: DesignMetadata(
                fileSize = fileMetadata?.size,
                mimeType = fileMetadata?.contentType,
                uploadedFrom = "design_library"
            )

            // Create design record
            val design = designRepository.create(
                tenantId = tenantId,
                userId = userId,
                request = CreateDesignRequest(
                    title = request.title,
                    designType = request.designType,
                    designUrl = designUrl,
                    thumbnailUrl = thumbnailUrl,
                    width = request.width,
                    height = request.height,
                    fileHash = fileHash,
                    metadata = metadata
                )
            )

            logger.info("Created design ${design.id} for user $userId")

            return Result.success(design)
        } catch (e: Exception) {
            logger.error("Failed to complete upload", e)
            return Result.failure(e)
        }
    }

    /**
     * Direct create (for already uploaded files or external URLs)
     */
    fun createDesign(
        tenantId: Long,
        userId: Long,
        request: CreateDesignRequest
    ): Result<Design> {
        try {
            if (request.title.isBlank()) {
                return Result.failure(IllegalArgumentException("Title is required"))
            }
            if (request.designUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("Design URL is required"))
            }

            val design = designRepository.create(tenantId, userId, request)
            logger.info("Created design ${design.id} for user $userId")

            return Result.success(design)
        } catch (e: Exception) {
            logger.error("Failed to create design", e)
            return Result.failure(e)
        }
    }

    // =====================================================
    // MUTATIONS
    // =====================================================

    fun updateDesign(id: Long, tenantId: Long, request: UpdateDesignRequest): Result<Design> {
        val design = designRepository.update(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Design not found"))

        logger.info("Updated design $id")
        return Result.success(design)
    }

    fun deleteDesign(id: Long, tenantId: Long): Result<Boolean> {
        val design = designRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Design not found"))

        // Soft delete in database
        val deleted = designRepository.delete(id, tenantId)

        if (deleted) {
            // Optionally delete from S3 (or keep for audit)
            // s3Service.deleteFile(design.designUrl.substringAfter(".com/"))
            logger.info("Deleted design $id")
        }

        return Result.success(deleted)
    }

    fun bulkDelete(tenantId: Long, request: BulkDeleteRequest): BulkDeleteResponse {
        val deletedCount = designRepository.bulkDelete(request.designIds, tenantId)
        logger.info("Bulk deleted $deletedCount designs")

        return BulkDeleteResponse(
            deletedCount = deletedCount,
            failedIds = if (deletedCount < request.designIds.size) {
                // Find which ones failed
                val existingIds = designRepository.findByIds(request.designIds, tenantId).map { it.id }
                request.designIds.filter { it !in existingIds }
            } else emptyList()
        )
    }

    // =====================================================
    // DUPLICATE CHECK
    // =====================================================

    fun checkDuplicate(tenantId: Long, fileHash: String): DuplicateCheckResponse {
        val existingDesign = designRepository.findByHash(fileHash, tenantId)
        return DuplicateCheckResponse(
            isDuplicate = existingDesign != null,
            existingDesign = existingDesign
        )
    }

    // =====================================================
    // DESIGN TYPES
    // =====================================================

    fun getDesignTypes(): List<Map<String, Any>> {
        return DesignType.entries.map { type ->
            mapOf(
                "code" to type.code,
                "name" to type.name,
                "label" to type.label,
                "extensions" to type.extensions
            )
        }
    }
}

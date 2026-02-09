package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// DESIGN TYPES
// =====================================================
// 1 = DTF/Image (standard print)
// 2 = Embroidery (stitch file)
// 3 = Vector (AI/EPS/SVG)
// 4 = UV Print

enum class DesignType(val code: Int, val label: String, val extensions: List<String>) {
    DTF(1, "DTF/Image", listOf("png", "jpg", "jpeg", "tiff", "tif")),
    EMBROIDERY(2, "Embroidery", listOf("dst", "pes", "jef", "exp", "vp3")),
    VECTOR(3, "Vector", listOf("ai", "eps", "svg", "pdf")),
    UV(4, "UV Print", listOf("png", "jpg", "jpeg", "tiff", "tif"));

    companion object {
        fun fromCode(code: Int): DesignType? = entries.find { it.code == code }

        fun fromExtension(ext: String): DesignType? {
            val lowerExt = ext.lowercase()
            return entries.find { type -> type.extensions.contains(lowerExt) }
        }
    }
}

// =====================================================
// DESIGN STATUS
// =====================================================

enum class DesignStatus(val code: Int) {
    DELETED(-1),
    DRAFT(0),
    ACTIVE(1),
    PROCESSING(2); // Being used in gangsheet

    companion object {
        fun fromCode(code: Int): DesignStatus? = entries.find { it.code == code }
    }
}

// =====================================================
// DESIGN MODELS
// =====================================================

@Serializable
data class Design(
    val id: Long,
    val tenantId: Long,
    val userId: Long?,
    val title: String,
    val fileHash: String,
    val designType: Int,
    val designTypeLabel: String,
    val designUrl: String,
    val thumbnailUrl: String?,
    val width: Double?,  // inches
    val height: Double?, // inches
    val metadata: DesignMetadata? = null,
    val status: Int,
    val createdAt: String,
    val updatedAt: String,
    // Computed fields
    val fileSize: Long? = null,
    val fileName: String? = null
)

@Serializable
data class DesignMetadata(
    val originalFileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val dpi: Int? = null,
    val widthPixels: Int? = null,
    val heightPixels: Int? = null,
    val colorMode: String? = null, // RGB, CMYK
    val stitchCount: Int? = null,  // For embroidery
    val colorCount: Int? = null,   // For embroidery
    val uploadedFrom: String? = null // "design_library", "order_create"
)

@Serializable
data class DesignListItem(
    val id: Long,
    val title: String,
    val designType: Int,
    val designTypeLabel: String,
    val thumbnailUrl: String?,
    val width: Double?,
    val height: Double?,
    val status: Int,
    val createdAt: String
)

@Serializable
data class DesignListResponse(
    val designs: List<DesignListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class DesignFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val designType: Int? = null,
    val status: Int? = null,
    val search: String? = null,
    val userId: Long? = null, // Filter by uploader
    val sortBy: String = "createdAt", // createdAt, title
    val sortOrder: String = "DESC"
)

// =====================================================
// UPLOAD REQUEST/RESPONSE
// =====================================================

@Serializable
data class UploadUrlRequest(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val designType: Int? = null // Auto-detect if not provided
)

@Serializable
data class UploadUrlResponse(
    val uploadUrl: String,      // Pre-signed S3 URL for upload
    val designKey: String,      // S3 key for the design
    val thumbnailKey: String?,  // S3 key for thumbnail (if applicable)
    val expiresIn: Int = 3600   // URL expiration in seconds
)

@Serializable
data class CompleteUploadRequest(
    val designKey: String,
    val title: String,
    val designType: Int,
    val width: Double? = null,
    val height: Double? = null,
    val metadata: DesignMetadata? = null
)

@Serializable
data class CreateDesignRequest(
    val title: String,
    val designType: Int = DesignType.DTF.code,
    val designUrl: String,
    val thumbnailUrl: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val fileHash: String? = null,
    val metadata: DesignMetadata? = null
)

@Serializable
data class UpdateDesignRequest(
    val title: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val status: Int? = null
)

// =====================================================
// BULK OPERATIONS
// =====================================================

@Serializable
data class BulkDeleteRequest(
    val designIds: List<Long>
)

@Serializable
data class BulkDeleteResponse(
    val deletedCount: Int,
    val failedIds: List<Long> = emptyList()
)

// =====================================================
// DESIGN SELECTION (for orders)
// =====================================================

@Serializable
data class DesignSelection(
    val designId: Long,
    val modificationId: Long? = null, // Print location
    val printWidth: Double? = null,   // Override width
    val printHeight: Double? = null   // Override height
)

// =====================================================
// DUPLICATE CHECK
// =====================================================

@Serializable
data class DuplicateCheckResponse(
    val isDuplicate: Boolean,
    val existingDesign: Design? = null
)

package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// GANGSHEET STATUS
// =====================================================

enum class GangsheetStatus(val code: Int, val label: String) {
    PENDING(0, "Pending"),
    FETCHING_DESIGNS(1, "Fetching Designs"),
    CALCULATING(2, "Calculating Placements"),
    GENERATING(3, "Generating Images"),
    UPLOADING(4, "Uploading"),
    COMPLETED(5, "Completed"),
    FAILED(-1, "Failed");

    companion object {
        fun fromCode(code: Int): GangsheetStatus = entries.find { it.code == code } ?: PENDING
    }
}

// =====================================================
// GANGSHEET SETTINGS
// =====================================================

@Serializable
data class GangsheetSettingsFull(
    val rollWidth: Double = 22.0,          // Roll width in inches
    val rollLength: Double = 60.0,         // Roll length in inches
    val dpi: Int = 300,                    // Dots per inch
    val gap: Double = 0.3,                 // Gap between designs in inches
    val border: Boolean = true,            // Whether to add border
    val borderSize: Double = 0.1,          // Border size in inches
    val borderColor: String = "red"        // Border color (CSS color name or hex)
)

// =====================================================
// GANGSHEET DATA CLASSES
// =====================================================

@Serializable
data class Gangsheet(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val status: Int,
    val statusLabel: String,
    val orderIds: List<Long>,
    val settings: GangsheetSettingsFull,
    val downloadUrl: String? = null,
    val errorMessage: String? = null,
    val totalDesigns: Int = 0,
    val processedDesigns: Int = 0,
    val totalRolls: Int = 0,
    val rollUrls: List<String> = emptyList(),
    val createdAt: String,
    val completedAt: String? = null,
    // Nested data
    val rolls: List<GangsheetRollFull> = emptyList()
)

@Serializable
data class GangsheetRollFull(
    val id: Long,
    val gangsheetId: Long,
    val rollNumber: Int,
    val widthPixels: Int? = null,
    val heightPixels: Int? = null,
    val designCount: Int = 0,
    val fileUrl: String? = null,
    val placements: List<DesignPlacement> = emptyList(),
    val createdAt: String
)

@Serializable
data class GangsheetListItem(
    val id: Long,
    val name: String,
    val status: Int,
    val statusLabel: String,
    val totalDesigns: Int,
    val totalRolls: Int,
    val downloadUrl: String? = null,
    val createdAt: String
)

@Serializable
data class GangsheetListResponse(
    val gangsheets: List<GangsheetListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// =====================================================
// DESIGN PLACEMENT
// =====================================================

@Serializable
data class DesignPlacement(
    val designId: Long? = null,
    val orderId: Long? = null,
    val orderProductId: Long? = null,
    val designHash: String? = null,
    val designUrl: String? = null,
    val x: Int,                          // X position in pixels
    val y: Int,                          // Y position in pixels
    val width: Int,                      // Width in pixels (including gap/border)
    val height: Int,                     // Height in pixels (including gap/border)
    val printWidth: Int,                 // Actual print width in pixels
    val printHeight: Int,                // Actual print height in pixels
    val quantity: Int = 1,
    val modificationName: String? = null,
    val rotated: Boolean = false,
    val rollNumber: Int = 0
)

// =====================================================
// DESIGN FOR GANGSHEET (internal processing)
// =====================================================

@Serializable
data class GangsheetDesign(
    val orderProductId: Long,
    val orderId: Long,
    val designId: Long? = null,
    val designHash: String,
    val designUrl: String,
    val originalWidth: Int,              // Original design width in pixels
    val originalHeight: Int,             // Original design height in pixels
    val printSize: Double,               // Target print size in inches
    val quantity: Int,
    val modificationName: String,
    val useWidth: Int = 1,               // 1 = use width1, 2 = use width2
    // Calculated fields (set during processing)
    var printWidth: Int = 0,             // Calculated print width in pixels
    var printHeight: Int = 0,            // Calculated print height in pixels
    var newWidth: Int = 0,               // Width including gap/border in pixels
    var newHeight: Int = 0,              // Height including gap/border in pixels
    var rotated: Boolean = false,        // Whether design should be rotated 90 degrees
    var localFilePath: String? = null    // Local file path after download
)

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class CreateGangsheetRequest(
    val orderIds: List<Long>,
    val name: String? = null,            // Auto-generated if not provided
    val settings: GangsheetSettingsFull? = null, // Use tenant defaults if not provided
    val products: List<GangsheetProductOverride>? = null // Optional quantity overrides
)

@Serializable
data class GangsheetProductOverride(
    val orderProductId: Long,
    val quantity: Int
)

@Serializable
data class GangsheetResponse(
    val gangsheet: Gangsheet,
    val message: String? = null
)

@Serializable
data class GangsheetStatusResponse(
    val id: Long,
    val status: Int,
    val statusLabel: String,
    val progress: Int,                   // 0-100 percentage
    val currentStep: String,
    val totalDesigns: Int,
    val processedDesigns: Int,
    val totalRolls: Int,
    val errorMessage: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class GangsheetDownloadResponse(
    val downloadUrl: String,
    val expiresIn: Int = 3600,           // URL expiration in seconds
    val rollUrls: List<RollDownloadInfo> = emptyList()
)

@Serializable
data class RollDownloadInfo(
    val rollNumber: Int,
    val downloadUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val designCount: Int = 0
)

// =====================================================
// GANGSHEET FILTERS
// =====================================================

@Serializable
data class GangsheetFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val status: Int? = null,
    val search: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
)

// =====================================================
// PROCESSING RESULT
// =====================================================

@Serializable
data class PlacementResult(
    val rolls: List<RollPlacement>,
    val totalDesigns: Int,
    val totalRolls: Int
)

@Serializable
data class RollPlacement(
    val rollNumber: Int,
    val placements: List<DesignPlacement>,
    val maxHeight: Int,                  // Actual used height of the roll
    val orderIds: List<Long>             // Orders included in this roll
)

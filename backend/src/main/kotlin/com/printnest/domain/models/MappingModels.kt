package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// MAPPING STATUS ENUM
// =====================================================

enum class MappingStatus(val code: Int) {
    UNMAPPED(0),
    COMPLETELY_MAPPED(1),
    PARTIALLY_MAPPED(2);

    companion object {
        fun fromCode(code: Int): MappingStatus = entries.find { it.code == code } ?: UNMAPPED
    }
}

// =====================================================
// LISTING MAPPING STATUS
// =====================================================

enum class ListingMappingStatus(val code: Int) {
    NONE(0),           // No design found
    PARTIALLY_DESIGNED(1),  // Some modifications have designs
    COMPLETELY_DESIGNED(2); // All modifications have designs

    companion object {
        fun fromCode(code: Int): ListingMappingStatus = entries.find { it.code == code } ?: NONE
    }
}

// =====================================================
// MAPPING RESULT MODELS
// =====================================================

@Serializable
data class MappingResult(
    val success: Boolean,
    val orderId: Long,
    val orderMapStatus: Int,
    val mappedCount: Int = 0,
    val totalCount: Int = 0,
    val errors: List<String> = emptyList(),
    val productMappings: List<ProductMappingResult> = emptyList()
)

@Serializable
data class ProductMappingResult(
    val orderProductId: Long,
    val listingId: String? = null,
    val valueId1: String? = null,
    val valueId2: String? = null,
    val variantMapped: Boolean = false,
    val designMapped: Boolean = false,
    val listingMappingStatus: Int = 0,
    val error: String? = null
)

@Serializable
data class MappingStatusResponse(
    val orderId: Long,
    val orderMapStatus: Int,
    val orderMapStatusLabel: String,
    val products: List<ProductMappingStatus>
)

@Serializable
data class ProductMappingStatus(
    val orderProductId: Long,
    val listingId: String? = null,
    val hasValueMapping: Boolean = false,
    val hasListingMapping: Boolean = false,
    val listingMappingStatus: Int = 0,
    val listingMappingStatusLabel: String = "NONE",
    val mapValue: MapValue? = null,
    val mapListings: List<MapListing> = emptyList()
)

// =====================================================
// MAPPED VARIANT DETAILS
// =====================================================

@Serializable
data class MappedVariantDetails(
    val mapValueId: Long,
    val variantId: Long,
    val productId: Long,
    val productTitle: String,
    val categoryId: Long,
    val categoryName: String,
    val option1Id: Long,
    val option1Name: String,
    val option2Id: Long? = null,
    val option2Name: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null,
    val isDark: Boolean = false,
    val stock: Int = 0,
    val variantStatus: Int = 1,
    // Variant Modification
    val variantModificationId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val vmWidth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val vmHeight: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val vmDepth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val vmWeight: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class MappedModificationDetail(
    val modificationId: Long,
    val modificationName: String,
    val modificationDesignId: Long? = null,
    val modificationDesignUrl: String? = null,
    val modificationUseWidth: Int = 1
)

// =====================================================
// APPLIED MAPPING DETAIL
// =====================================================

@Serializable
data class AppliedMappingDetail(
    val option1Id: Long,
    val option2Id: Long? = null,
    val productId: Long,
    val product: String, // Product title
    val productCategories: String, // Category name
    val productCategoryId: Long,
    val option1: String, // Option1 name
    val option2: String? = null, // Option2 name
    val variants: AppliedVariantDetail,
    val modificationDetail: List<MappedModificationDetail> = emptyList(),
    val variantModification: AppliedVariantModificationDetail,
    val quantity: Int = 1
)

@Serializable
data class AppliedVariantDetail(
    val variantId: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    val stock: Int = 0,
    val status: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null
)

@Serializable
data class AppliedVariantModificationDetail(
    val variantModificationId: Long? = null,
    val variantModificationStatus: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    val depth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO
)

// =====================================================
// BULK MAPPING REQUESTS
// =====================================================

@Serializable
data class BulkMapValueRequest(
    val mappings: List<CreateMapValueRequest>
)

@Serializable
data class BulkMapListingRequest(
    val mappings: List<CreateMapListingRequest>
)

@Serializable
data class SaveDesignMapRequest(
    val orderProductId: Long,
    val listingId: String,
    val userId: Long? = null,
    val valueId1: String? = null,
    val valueId2: String? = null,
    val variantId: Long? = null,
    val variantModificationId: Long? = null,
    val mapListings: List<MapListingItem> = emptyList()
)

@Serializable
data class MapListingItem(
    val modificationId: Long,
    val lightDesignId: Long? = null,
    val darkDesignId: Long? = null
)

// =====================================================
// MAP VALUE EXTENDED (with joined data)
// =====================================================

@Serializable
data class MapValueExtended(
    val id: Long,
    val tenantId: Long,
    val userId: Long? = null,
    val valueId1: String? = null,
    val valueId2: String? = null,
    val variantId: Long? = null,
    val variantModificationId: Long? = null,
    val isDark: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    // Joined data
    val productId: Long? = null,
    val productTitle: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val option1Id: Long? = null,
    val option1Name: String? = null,
    val option2Id: Long? = null,
    val option2Name: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val variantPrice: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val productPrice: BigDecimal? = null
)

// =====================================================
// MAP LISTING EXTENDED (with joined data)
// =====================================================

@Serializable
data class MapListingExtended(
    val id: Long,
    val tenantId: Long,
    val userId: Long? = null,
    val listingId: String,
    val modificationId: Long? = null,
    val lightDesignId: Long? = null,
    val darkDesignId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
    // Joined data
    val modificationName: String? = null,
    val lightDesignUrl: String? = null,
    val lightThumbnailUrl: String? = null,
    val darkDesignUrl: String? = null,
    val darkThumbnailUrl: String? = null
)

// =====================================================
// LISTING DESIGNS RESPONSE
// =====================================================

@Serializable
data class ListingDesignsResponse(
    val listingId: String,
    val designs: List<ListingDesign>
)

@Serializable
data class ListingDesign(
    val type: String, // "light" or "dark"
    val designId: Long,
    val thumbnailUrl: String? = null,
    val designUrl: String? = null,
    val modificationId: Long? = null,
    val modificationName: String? = null
)

// =====================================================
// FILTER MODELS
// =====================================================

@Serializable
data class MapValueFilters(
    val userId: Long? = null,
    val valueId1: String? = null,
    val valueId2: String? = null,
    val page: Int = 1,
    val limit: Int = 50
)

@Serializable
data class MapListingFilters(
    val userId: Long? = null,
    val listingId: String? = null,
    val modificationId: Long? = null,
    val page: Int = 1,
    val limit: Int = 50
)

// =====================================================
// LIST RESPONSES
// =====================================================

@Serializable
data class MapValueListResponse(
    val mapValues: List<MapValue>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class MapListingListResponse(
    val mapListings: List<MapListing>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

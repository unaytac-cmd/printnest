package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// DesignType is defined in DesignModels.kt

// =====================================================
// PRODUCT CATEGORY
// =====================================================

@Serializable
data class ProductCategory(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val description: String? = null,
    val parentCategoryId: Long? = null,
    val isHeavy: Boolean = false,
    val status: Int = 1,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val modifications: List<Modification> = emptyList(),
    val childCategories: List<ProductCategory> = emptyList()
)

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val description: String? = null,
    val parentCategoryId: Long? = null,
    val isHeavy: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    val description: String? = null,
    val parentCategoryId: Long? = null,
    val isHeavy: Boolean? = null,
    val status: Int? = null,
    val sortOrder: Int? = null
)

// =====================================================
// PRODUCT
// =====================================================

@Serializable
data class Product(
    val id: Long,
    val tenantId: Long,
    val categoryId: Long? = null,
    val title: String,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val option1Name: String? = null, // e.g., "Size"
    val option2Name: String? = null, // e.g., "Color"
    val option3Name: String? = null,
    val designType: Int = 1,
    val supplierId: Long? = null,
    val status: Int = 1,
    val tags: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val category: ProductCategory? = null,
    val option1s: List<Option1> = emptyList(),
    val option2s: List<Option2> = emptyList(),
    val variants: List<Variant> = emptyList(),
    val supplier: Supplier? = null
)

@Serializable
data class CreateProductRequest(
    val categoryId: Long? = null,
    val title: String,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val option1Name: String? = null,
    val option2Name: String? = null,
    val option3Name: String? = null,
    val designType: Int = 1,
    val supplierId: Long? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateProductRequest(
    val categoryId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val basePrice: BigDecimal? = null,
    val option1Name: String? = null,
    val option2Name: String? = null,
    val option3Name: String? = null,
    val designType: Int? = null,
    val supplierId: Long? = null,
    val status: Int? = null,
    val tags: List<String>? = null
)

// =====================================================
// OPTIONS
// =====================================================

@Serializable
data class Option1(
    val id: Long,
    val tenantId: Long,
    val productId: Long,
    val name: String, // e.g., "Small", "Medium", "Large"
    val sortOrder: Int = 0,
    val status: Int = 1,
    val createdAt: String,
    // Nested data
    val variantModification: VariantModification? = null
)

@Serializable
data class Option2(
    val id: Long,
    val tenantId: Long,
    val productId: Long,
    val name: String, // e.g., "Red", "Blue", "Black"
    val hexColor: String? = null,
    val isDark: Boolean = false,
    val sortOrder: Int = 0,
    val status: Int = 1,
    val createdAt: String
)

@Serializable
data class CreateOption1Request(
    val name: String,
    val sortOrder: Int = 0
)

@Serializable
data class CreateOption2Request(
    val name: String,
    val hexColor: String? = null,
    val isDark: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class BulkCreateOptionsRequest(
    val option1s: List<CreateOption1Request> = emptyList(),
    val option2s: List<CreateOption2Request> = emptyList()
)

// =====================================================
// VARIANT
// =====================================================

@Serializable
data class Variant(
    val id: Long,
    val tenantId: Long,
    val productId: Long,
    val option1Value: String? = null,
    val option2Value: String? = null,
    val option3Value: String? = null,
    val option1Id: Long? = null,
    val option2Id: Long? = null,
    val sku: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null, // Primary print width (inches)
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null, // Secondary print width (inches)
    val isDark: Boolean = false,
    val inStock: Boolean = true,
    val inHouse: Boolean = false, // In-house inventory (vs dropship)
    val stockQuantity: Int? = null,
    val status: Int = 1, // 1=enabled, 0=disabled
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val option1: Option1? = null,
    val option2: Option2? = null
)

@Serializable
data class CreateVariantRequest(
    val option1Value: String? = null,
    val option2Value: String? = null,
    val option3Value: String? = null,
    val option1Id: Long? = null,
    val option2Id: Long? = null,
    val sku: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null,
    val isDark: Boolean = false,
    val inStock: Boolean = true,
    val inHouse: Boolean = false,
    val stockQuantity: Int? = null,
    val status: Int = 1
)

@Serializable
data class UpdateVariantRequest(
    val option1Value: String? = null,
    val option2Value: String? = null,
    val option3Value: String? = null,
    val sku: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val cost: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null,
    val isDark: Boolean? = null,
    val inStock: Boolean? = null,
    val inHouse: Boolean? = null,
    val stockQuantity: Int? = null,
    val status: Int? = null
)

@Serializable
data class BulkUpdateVariantsRequest(
    val variantIds: List<Long>? = null, // null = all variants of the product
    val inHouse: Boolean? = null,
    val inStock: Boolean? = null,
    val status: Int? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null
)

// =====================================================
// VARIANT MODIFICATION (Size-based dimensions)
// =====================================================

@Serializable
data class VariantModification(
    val id: Long,
    val tenantId: Long,
    val productId: Long,
    val option1Id: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val depth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO, // Shipping weight in oz
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateVariantModificationRequest(
    val option1Id: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val depth: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO
)

// =====================================================
// MODIFICATION (Print Location)
// =====================================================

@Serializable
data class Modification(
    val id: Long,
    val tenantId: Long,
    val categoryId: Long,
    val name: String, // "Front", "Back", "Left Sleeve", etc.
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal = BigDecimal.ZERO,
    val useWidth: Int = 1, // 1 = use width1, 2 = use width2
    val sortOrder: Int = 0,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateModificationRequest(
    val name: String,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal = BigDecimal.ZERO,
    val useWidth: Int = 1,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateModificationRequest(
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal? = null,
    val useWidth: Int? = null,
    val sortOrder: Int? = null,
    val status: Int? = null
)

// =====================================================
// SUPPLIER
// =====================================================

@Serializable
data class Supplier(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val address: Address? = null,
    val notes: String? = null,
    val status: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateSupplierRequest(
    val name: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val address: Address? = null,
    val notes: String? = null
)

// =====================================================
// MAPPING
// =====================================================

@Serializable
data class MapValue(
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
    // Nested data
    val variant: Variant? = null,
    val variantModification: VariantModification? = null
)

@Serializable
data class CreateMapValueRequest(
    val valueId1: String? = null,
    val valueId2: String? = null,
    val variantId: Long? = null,
    val variantModificationId: Long? = null,
    val isDark: Boolean = false
)

@Serializable
data class MapListing(
    val id: Long,
    val tenantId: Long,
    val userId: Long? = null,
    val listingId: String,
    val modificationId: Long? = null,
    val lightDesignId: Long? = null,
    val darkDesignId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
    // Nested data
    val modification: Modification? = null
)

@Serializable
data class CreateMapListingRequest(
    val listingId: String,
    val modificationId: Long? = null,
    val lightDesignId: Long? = null,
    val darkDesignId: Long? = null
)

// =====================================================
// PRODUCT DETAIL (for order_products.product_detail)
// =====================================================

@Serializable
data class OrderProductDetail(
    val productId: Long? = null,
    val option1: String? = null,
    val option2: String? = null,
    val variants: OrderVariantDetail? = null,
    val variantModification: OrderVariantModificationDetail? = null,
    val modificationDetail: List<OrderModificationDetail> = emptyList()
)

@Serializable
data class OrderVariantDetail(
    val variantId: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width1: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val width2: BigDecimal? = null
)

@Serializable
data class OrderVariantModificationDetail(
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val width: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val height: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class OrderModificationDetail(
    val modificationId: Long,
    val modificationName: String,
    val modificationDesignId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val priceDifference: BigDecimal = BigDecimal.ZERO
)

// =====================================================
// GENERATE VARIANTS REQUEST
// =====================================================

@Serializable
data class GenerateVariantsRequest(
    @Serializable(with = BigDecimalSerializer::class)
    val defaultPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val defaultCost: BigDecimal = BigDecimal.ZERO,
    val skuPrefix: String? = null
)

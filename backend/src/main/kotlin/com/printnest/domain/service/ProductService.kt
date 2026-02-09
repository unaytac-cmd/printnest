package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.CategoryRepository
import com.printnest.domain.repository.ProductRepository

class ProductService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {

    // =====================================================
    // PRODUCTS
    // =====================================================

    fun getProducts(tenantId: Long, categoryId: Long? = null, includeInactive: Boolean = false): List<Product> {
        return productRepository.findAll(tenantId, categoryId, includeInactive)
    }

    fun getProduct(id: Long, tenantId: Long): Product? {
        return productRepository.findById(id, tenantId)
    }

    fun getProductWithDetails(id: Long, tenantId: Long): Product? {
        val product = productRepository.findByIdWithDetails(id, tenantId) ?: return null

        // Attach category if exists
        val category = product.categoryId?.let { categoryRepository.findById(it, tenantId) }

        return product.copy(category = category)
    }

    fun createProduct(tenantId: Long, request: CreateProductRequest): Result<Product> {
        // Validate category if provided
        request.categoryId?.let { catId ->
            val category = categoryRepository.findById(catId, tenantId)
            if (category == null) {
                return Result.failure(IllegalArgumentException("Category not found"))
            }
        }

        val product = productRepository.create(tenantId, request)
        return Result.success(product)
    }

    fun updateProduct(id: Long, tenantId: Long, request: UpdateProductRequest): Result<Product> {
        val existing = productRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        // Validate category if changing
        request.categoryId?.let { catId ->
            val category = categoryRepository.findById(catId, tenantId)
            if (category == null) {
                return Result.failure(IllegalArgumentException("Category not found"))
            }
        }

        val updated = productRepository.update(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update product"))

        return Result.success(updated)
    }

    fun deleteProduct(id: Long, tenantId: Long): Result<Boolean> {
        val existing = productRepository.findById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val deleted = productRepository.delete(id, tenantId)
        return Result.success(deleted)
    }

    // =====================================================
    // OPTIONS
    // =====================================================

    fun getOption1s(productId: Long, tenantId: Long): List<Option1> {
        return productRepository.findOption1s(productId, tenantId)
    }

    fun getOption2s(productId: Long, tenantId: Long): List<Option2> {
        return productRepository.findOption2s(productId, tenantId)
    }

    fun createOption1(productId: Long, tenantId: Long, request: CreateOption1Request): Result<Option1> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val option = productRepository.createOption1(productId, tenantId, request)
        return Result.success(option)
    }

    fun createOption2(productId: Long, tenantId: Long, request: CreateOption2Request): Result<Option2> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val option = productRepository.createOption2(productId, tenantId, request)
        return Result.success(option)
    }

    fun bulkCreateOptions(productId: Long, tenantId: Long, request: BulkCreateOptionsRequest): Result<Map<String, List<Any>>> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val createdOption1s = request.option1s.map { opt ->
            productRepository.createOption1(productId, tenantId, opt)
        }

        val createdOption2s = request.option2s.map { opt ->
            productRepository.createOption2(productId, tenantId, opt)
        }

        return Result.success(mapOf(
            "option1s" to createdOption1s,
            "option2s" to createdOption2s
        ))
    }

    fun deleteOption1(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(productRepository.deleteOption1(id, tenantId))
    }

    fun deleteOption2(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(productRepository.deleteOption2(id, tenantId))
    }

    // =====================================================
    // VARIANTS
    // =====================================================

    fun getVariants(productId: Long, tenantId: Long): List<Variant> {
        return productRepository.findVariants(productId, tenantId)
    }

    fun getVariant(id: Long, tenantId: Long): Variant? {
        return productRepository.findVariantById(id, tenantId)
    }

    fun createVariant(productId: Long, tenantId: Long, request: CreateVariantRequest): Result<Variant> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val variant = productRepository.createVariant(productId, tenantId, request)
        return Result.success(variant)
    }

    fun updateVariant(id: Long, tenantId: Long, request: UpdateVariantRequest): Result<Variant> {
        val existing = productRepository.findVariantById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Variant not found"))

        val updated = productRepository.updateVariant(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update variant"))

        return Result.success(updated)
    }

    fun deleteVariant(id: Long, tenantId: Long): Result<Boolean> {
        val existing = productRepository.findVariantById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Variant not found"))

        val deleted = productRepository.deleteVariant(id, tenantId)
        return Result.success(deleted)
    }

    fun generateVariants(productId: Long, tenantId: Long, request: GenerateVariantsRequest): Result<List<Variant>> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val variants = productRepository.generateVariants(productId, tenantId, request)
        return Result.success(variants)
    }

    fun bulkUpdateVariants(productId: Long, tenantId: Long, request: BulkUpdateVariantsRequest): Result<List<Variant>> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val variants = productRepository.bulkUpdateVariants(productId, tenantId, request)
        return Result.success(variants)
    }

    // =====================================================
    // VARIANT MODIFICATIONS
    // =====================================================

    fun getVariantModifications(productId: Long, tenantId: Long): List<VariantModification> {
        return productRepository.findVariantModifications(productId, tenantId)
    }

    fun createVariantModification(productId: Long, tenantId: Long, request: CreateVariantModificationRequest): Result<VariantModification> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val modification = productRepository.upsertVariantModification(productId, tenantId, request)
        return Result.success(modification)
    }

    fun bulkUpdateVariantModifications(
        productId: Long,
        tenantId: Long,
        requests: List<CreateVariantModificationRequest>
    ): Result<List<VariantModification>> {
        val product = productRepository.findById(productId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val modifications = requests.map { request ->
            productRepository.upsertVariantModification(productId, tenantId, request)
        }

        return Result.success(modifications)
    }

    // =====================================================
    // PRODUCT WITH FULL DETAILS
    // =====================================================

    fun getProductFullDetails(id: Long, tenantId: Long): ProductFullDetails? {
        val product = productRepository.findByIdWithDetails(id, tenantId) ?: return null

        val category = product.categoryId?.let {
            categoryRepository.findByIdWithModifications(it, tenantId)
        }

        val variantModifications = productRepository.findVariantModifications(id, tenantId)

        // Attach variant modifications to option1s
        val option1sWithMods = product.option1s.map { opt1 ->
            val varMod = variantModifications.find { it.option1Id == opt1.id }
            opt1.copy(variantModification = varMod)
        }

        return ProductFullDetails(
            product = product.copy(
                option1s = option1sWithMods,
                category = category
            ),
            category = category,
            modifications = category?.modifications ?: emptyList(),
            variantModifications = variantModifications
        )
    }
}

@kotlinx.serialization.Serializable
data class ProductFullDetails(
    val product: Product,
    val category: ProductCategory? = null,
    val modifications: List<Modification> = emptyList(),
    val variantModifications: List<VariantModification> = emptyList()
)

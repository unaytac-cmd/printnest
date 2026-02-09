package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.MappingRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.ProductRepository
import com.printnest.domain.repository.CategoryRepository
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class MappingService(
    private val mappingRepository: MappingRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(MappingService::class.java)

    // =====================================================
    // MAP ORDER
    // =====================================================

    /**
     * Map a single order - applies variant and design mappings to all products
     */
    fun mapOrder(tenantId: Long, orderId: Long, userId: Long? = null): Result<MappingResult> {
        logger.info("Starting mapping process for order $orderId, tenant $tenantId")

        val order = orderRepository.findByIdWithProducts(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val products = order.products
        if (products.isEmpty()) {
            logger.warn("No products found for order $orderId")
            return Result.success(MappingResult(
                success = false,
                orderId = orderId,
                orderMapStatus = MappingStatus.UNMAPPED.code,
                errors = listOf("No products to map")
            ))
        }

        val productMappings = mutableListOf<ProductMappingResult>()
        var hasAnyMapping = false
        var hasPartialMapping = false

        for (product in products) {
            try {
                val result = mapOrderProduct(tenantId, orderId, product, userId)
                productMappings.add(result)

                if (result.variantMapped) {
                    hasAnyMapping = true
                    if (result.listingMappingStatus != ListingMappingStatus.COMPLETELY_DESIGNED.code) {
                        hasPartialMapping = true
                    }
                }
            } catch (e: Exception) {
                logger.error("Error mapping product ${product.id}: ${e.message}")
                productMappings.add(ProductMappingResult(
                    orderProductId = product.id,
                    listingId = product.listingId,
                    error = e.message
                ))
            }
        }

        // Determine final mapping status
        val finalStatus = when {
            !hasAnyMapping -> MappingStatus.UNMAPPED
            hasPartialMapping -> MappingStatus.PARTIALLY_MAPPED
            else -> MappingStatus.COMPLETELY_MAPPED
        }

        // Update order mapping status
        updateOrderMapStatus(tenantId, orderId, finalStatus.code)

        val mappedCount = productMappings.count { it.variantMapped }
        logger.info("Mapping completed for order $orderId: status=${finalStatus.name}, mapped=$mappedCount/${products.size}")

        return Result.success(MappingResult(
            success = hasAnyMapping,
            orderId = orderId,
            orderMapStatus = finalStatus.code,
            mappedCount = mappedCount,
            totalCount = products.size,
            productMappings = productMappings
        ))
    }

    /**
     * Map a single order product
     */
    private fun mapOrderProduct(
        tenantId: Long,
        orderId: Long,
        product: OrderProductFull,
        userId: Long?
    ): ProductMappingResult {
        val orderProductId = product.id
        val listingId = product.listingId
        logger.info("Processing product $orderProductId with listingId=$listingId")

        // Extract value IDs from product detail
        val productDetail = product.productDetail
        val valueId1 = productDetail?.productId?.toString() // Or extract from raw data if available
        val valueId2 = productDetail?.option2Id?.toString()

        if (valueId1.isNullOrBlank()) {
            logger.warn("No value IDs found for product $orderProductId")
            return ProductMappingResult(
                orderProductId = orderProductId,
                listingId = listingId,
                error = "Missing value IDs"
            )
        }

        // Find variant mapping
        val mapValueResult = findMappingWithDetails(tenantId, valueId1, valueId2, userId)
        if (mapValueResult == null) {
            logger.warn("No mapping found for valueId1=$valueId1, valueId2=$valueId2")
            return ProductMappingResult(
                orderProductId = orderProductId,
                listingId = listingId,
                valueId1 = valueId1,
                valueId2 = valueId2,
                error = "No variant mapping found"
            )
        }

        // Find listing/design mapping
        var listingMappingStatus = ListingMappingStatus.NONE
        var modificationDetails: List<MappedModificationDetail> = emptyList()

        if (!listingId.isNullOrBlank() && mapValueResult.categoryId != null) {
            val listingResult = mapListing(
                tenantId = tenantId,
                listingId = listingId,
                categoryId = mapValueResult.categoryId,
                isDark = mapValueResult.isDark,
                userId = userId
            )
            listingMappingStatus = ListingMappingStatus.fromCode(listingResult.first)
            modificationDetails = listingResult.second
        }

        // Apply mapping to order product
        applyMapping(
            tenantId = tenantId,
            orderId = orderId,
            orderProductId = orderProductId,
            mapValueResult = mapValueResult,
            modificationDetails = modificationDetails,
            quantity = product.quantity
        )

        return ProductMappingResult(
            orderProductId = orderProductId,
            listingId = listingId,
            valueId1 = valueId1,
            valueId2 = valueId2,
            variantMapped = true,
            designMapped = listingMappingStatus != ListingMappingStatus.NONE,
            listingMappingStatus = listingMappingStatus.code
        )
    }

    /**
     * Find variant mapping with joined product/variant details
     */
    private fun findMappingWithDetails(
        tenantId: Long,
        valueId1: String,
        valueId2: String?,
        userId: Long?
    ): MappedVariantDetails? = transaction {
        val query = MapValues
            .innerJoin(Variants, { MapValues.variantId }, { Variants.id })
            .innerJoin(Products, { Variants.productId }, { Products.id })
            .innerJoin(ProductCategories, { Products.categoryId }, { ProductCategories.id })
            .innerJoin(Option1s, { Variants.option1Id }, { Option1s.id })
            .leftJoin(Option2s, { Variants.option2Id }, { Option2s.id })
            .leftJoin(VariantModifications, { MapValues.variantModificationId }, { VariantModifications.id })
            .selectAll()
            .where { MapValues.tenantId eq tenantId }
            .andWhere { MapValues.valueId1 eq valueId1 }

        // Handle nullable valueId2
        val filteredQuery = if (valueId2 != null) {
            query.andWhere { MapValues.valueId2 eq valueId2 }
        } else {
            query.andWhere { MapValues.valueId2.isNull() }
        }

        // Optionally filter by userId
        val finalQuery = userId?.let {
            filteredQuery.andWhere { MapValues.userId eq it }
        } ?: filteredQuery

        val row = finalQuery.singleOrNull() ?: return@transaction null

        MappedVariantDetails(
            mapValueId = row[MapValues.id].value,
            variantId = row[Variants.id].value,
            productId = row[Products.id].value,
            productTitle = row[Products.title],
            categoryId = row[ProductCategories.id].value,
            categoryName = row[ProductCategories.name],
            option1Id = row[Option1s.id].value,
            option1Name = row[Option1s.name],
            option2Id = row.getOrNull(Option2s.id)?.value,
            option2Name = row.getOrNull(Option2s.name),
            price = row.getOrNull(Variants.price) ?: row[Products.basePrice],
            width1 = row.getOrNull(Variants.width1),
            width2 = row.getOrNull(Variants.width2),
            isDark = row[MapValues.isDark],
            stock = row.getOrNull(Variants.stockQuantity) ?: 0,
            variantStatus = 1,
            variantModificationId = row.getOrNull(VariantModifications.id)?.value,
            vmWidth = row.getOrNull(VariantModifications.width) ?: BigDecimal.ZERO,
            vmHeight = row.getOrNull(VariantModifications.height) ?: BigDecimal.ZERO,
            vmDepth = row.getOrNull(VariantModifications.depth) ?: BigDecimal.ZERO,
            vmWeight = row.getOrNull(VariantModifications.weight) ?: BigDecimal.ZERO
        )
    }

    /**
     * Find design mappings for a listing
     */
    private fun mapListing(
        tenantId: Long,
        listingId: String,
        categoryId: Long,
        isDark: Boolean,
        userId: Long?
    ): Pair<Int, List<MappedModificationDetail>> {
        logger.info("Mapping listing $listingId for category $categoryId, isDark=$isDark")

        // Get available modifications for this category
        val categoryModifications = getCategoryModifications(tenantId, categoryId)
        if (categoryModifications.isEmpty()) {
            logger.info("No modifications found for category $categoryId")
            return Pair(ListingMappingStatus.NONE.code, emptyList())
        }

        // Get listing mappings
        val listingMappings = getListingMappingsWithDesigns(tenantId, listingId, isDark, userId)
        if (listingMappings.isEmpty()) {
            logger.info("No listing mappings found for listingId=$listingId")
            return Pair(ListingMappingStatus.NONE.code, emptyList())
        }

        // Filter mappings that match category modifications
        val validMappings = listingMappings.filter { mapping ->
            mapping.modificationId in categoryModifications.map { it.id }
        }

        // Count how many modifications have designs
        val expectedCount = categoryModifications.size
        val matchedCount = validMappings.count { it.modificationDesignId != null }

        val status = when {
            matchedCount == 0 -> ListingMappingStatus.NONE
            matchedCount >= expectedCount -> ListingMappingStatus.COMPLETELY_DESIGNED
            else -> ListingMappingStatus.PARTIALLY_DESIGNED
        }

        logger.info("Listing mapping status: $status (matched=$matchedCount, expected=$expectedCount)")
        return Pair(status.code, validMappings)
    }

    /**
     * Get active modifications for a category
     */
    private fun getCategoryModifications(tenantId: Long, categoryId: Long): List<Modification> = transaction {
        Modifications.selectAll()
            .where { (Modifications.tenantId eq tenantId) and (Modifications.categoryId eq categoryId) and (Modifications.status eq 1) }
            .map { row ->
                Modification(
                    id = row[Modifications.id].value,
                    tenantId = row[Modifications.tenantId].value,
                    categoryId = row[Modifications.categoryId].value,
                    name = row[Modifications.name],
                    description = row[Modifications.description],
                    priceDifference = row[Modifications.priceDifference],
                    useWidth = row[Modifications.useWidth],
                    sortOrder = row[Modifications.sortOrder],
                    status = row[Modifications.status],
                    createdAt = row[Modifications.createdAt].toString(),
                    updatedAt = row[Modifications.updatedAt].toString()
                )
            }
    }

    /**
     * Get listing mappings with design details
     */
    private fun getListingMappingsWithDesigns(
        tenantId: Long,
        listingId: String,
        isDark: Boolean,
        userId: Long?
    ): List<MappedModificationDetail> = transaction {
        // Join with designs based on isDark flag
        val designJoin = if (isDark) {
            MapListings.leftJoin(Designs, { MapListings.lightDesignId }, { Designs.id })
        } else {
            MapListings.leftJoin(Designs, { MapListings.darkDesignId }, { Designs.id })
        }

        val baseQuery = designJoin
            .leftJoin(Modifications, { MapListings.modificationId }, { Modifications.id })
            .selectAll()
            .where { (MapListings.tenantId eq tenantId) and (MapListings.listingId eq listingId) }

        val query = userId?.let {
            baseQuery.andWhere { MapListings.userId eq it }
        } ?: baseQuery

        query.mapNotNull { row ->
            val modId = row.getOrNull(Modifications.id)?.value ?: return@mapNotNull null
            MappedModificationDetail(
                modificationId = modId,
                modificationName = row.getOrNull(Modifications.name) ?: "",
                modificationDesignId = if (isDark) row.getOrNull(MapListings.lightDesignId)?.value
                                       else row.getOrNull(MapListings.darkDesignId)?.value,
                modificationDesignUrl = row.getOrNull(Designs.thumbnailUrl),
                modificationUseWidth = row.getOrNull(Modifications.useWidth) ?: 1
            )
        }
    }

    /**
     * Apply mapping to order product
     */
    private fun applyMapping(
        tenantId: Long,
        orderId: Long,
        orderProductId: Long,
        mapValueResult: MappedVariantDetails,
        modificationDetails: List<MappedModificationDetail>,
        quantity: Int
    ) {
        logger.info("Applying mapping to order product $orderProductId")

        val productDetail = AppliedMappingDetail(
            option1Id = mapValueResult.option1Id,
            option2Id = mapValueResult.option2Id,
            productId = mapValueResult.productId,
            product = mapValueResult.productTitle,
            productCategories = mapValueResult.categoryName,
            productCategoryId = mapValueResult.categoryId,
            option1 = mapValueResult.option1Name,
            option2 = mapValueResult.option2Name,
            variants = AppliedVariantDetail(
                variantId = mapValueResult.variantId,
                price = mapValueResult.price,
                stock = mapValueResult.stock,
                status = mapValueResult.variantStatus,
                width1 = mapValueResult.width1,
                width2 = mapValueResult.width2
            ),
            modificationDetail = modificationDetails,
            variantModification = AppliedVariantModificationDetail(
                variantModificationId = mapValueResult.variantModificationId,
                variantModificationStatus = 1,
                depth = mapValueResult.vmDepth,
                width = mapValueResult.vmWidth,
                height = mapValueResult.vmHeight,
                weight = mapValueResult.vmWeight
            ),
            quantity = quantity
        )

        // Serialize and update order product
        val productDetailJson = json.encodeToString(AppliedMappingDetail.serializer(), productDetail)

        transaction {
            OrderProducts.update({ (OrderProducts.id eq orderProductId) and (OrderProducts.tenantId eq tenantId) }) {
                it[OrderProducts.productDetail] = productDetailJson
                it[productId] = mapValueResult.productId
                it[variantId] = mapValueResult.variantId
                it[mappingId] = mapValueResult.mapValueId
            }
        }

        logger.info("Mapping applied to order product $orderProductId")
    }

    /**
     * Update order mapping status
     */
    private fun updateOrderMapStatus(tenantId: Long, orderId: Long, status: Int) {
        transaction {
            Orders.update({ (Orders.id eq orderId) and (Orders.tenantId eq tenantId) }) {
                it[orderMapStatus] = status
            }
        }
    }

    // =====================================================
    // PUBLIC MAPPING METHODS
    // =====================================================

    /**
     * Find variant mapping by value IDs
     */
    fun findMapping(
        tenantId: Long,
        valueId1: String?,
        valueId2: String?,
        userId: Long? = null
    ): MapValue? {
        return mappingRepository.findMapValueByExternalIds(valueId1, valueId2, tenantId, userId)
    }

    /**
     * Find listing mapping by listing ID and modification
     */
    fun findListingMapping(
        tenantId: Long,
        listingId: String,
        modificationId: Long? = null,
        userId: Long? = null
    ): MapListing? {
        return mappingRepository.findMapListingByListingId(listingId, tenantId, userId, modificationId)
    }

    /**
     * Find all listing mappings for a listing
     */
    fun findListingMappings(
        tenantId: Long,
        listingId: String,
        userId: Long? = null
    ): List<MapListing> {
        return mappingRepository.findMapListingsByListingId(listingId, tenantId, userId)
    }

    /**
     * Get mapping status for an order
     */
    fun getMappingStatus(tenantId: Long, orderId: Long): Result<MappingStatusResponse> {
        val order = orderRepository.findByIdWithProducts(orderId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val productStatuses = order.products.map { product ->
            val mapValue = product.productDetail?.let { detail ->
                // Try to find existing mapping
                mappingRepository.findMapValueByExternalIds(
                    detail.productId?.toString(),
                    detail.option2Id?.toString(),
                    tenantId
                )
            }

            val mapListings = product.listingId?.let { listingId ->
                mappingRepository.findMapListingsByListingId(listingId, tenantId)
            } ?: emptyList()

            val hasValueMapping = mapValue != null
            val hasListingMapping = mapListings.isNotEmpty()
            val listingMappingStatus = when {
                !hasListingMapping -> ListingMappingStatus.NONE
                mapListings.all { it.lightDesignId != null && it.darkDesignId != null } -> ListingMappingStatus.COMPLETELY_DESIGNED
                else -> ListingMappingStatus.PARTIALLY_DESIGNED
            }

            ProductMappingStatus(
                orderProductId = product.id,
                listingId = product.listingId,
                hasValueMapping = hasValueMapping,
                hasListingMapping = hasListingMapping,
                listingMappingStatus = listingMappingStatus.code,
                listingMappingStatusLabel = listingMappingStatus.name,
                mapValue = mapValue,
                mapListings = mapListings
            )
        }

        val statusLabel = MappingStatus.fromCode(order.orderMapStatus).name

        return Result.success(MappingStatusResponse(
            orderId = orderId,
            orderMapStatus = order.orderMapStatus,
            orderMapStatusLabel = statusLabel,
            products = productStatuses
        ))
    }

    // =====================================================
    // MAP VALUES CRUD
    // =====================================================

    /**
     * Get all map values for tenant
     */
    fun getMapValues(tenantId: Long, userId: Long? = null): List<MapValue> {
        return mappingRepository.findMapValues(tenantId, userId)
    }

    /**
     * Get map value by ID
     */
    fun getMapValue(id: Long, tenantId: Long): MapValue? {
        return mappingRepository.findMapValueById(id, tenantId)
    }

    /**
     * Create a new map value
     */
    fun createMapValue(tenantId: Long, userId: Long?, request: CreateMapValueRequest): Result<MapValue> {
        // Check if mapping already exists
        val existing = mappingRepository.findMapValueByExternalIds(
            request.valueId1,
            request.valueId2,
            tenantId,
            userId
        )

        if (existing != null) {
            return Result.failure(IllegalArgumentException("Mapping already exists for these value IDs"))
        }

        // Validate variant if provided
        request.variantId?.let { variantId ->
            val variant = productRepository.findVariantById(variantId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Variant not found"))
        }

        val mapValue = mappingRepository.createMapValue(tenantId, userId, request)
        return Result.success(mapValue)
    }

    /**
     * Update a map value
     */
    fun updateMapValue(id: Long, tenantId: Long, request: CreateMapValueRequest): Result<MapValue> {
        val existing = mappingRepository.findMapValueById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Map value not found"))

        // Validate variant if provided
        request.variantId?.let { variantId ->
            val variant = productRepository.findVariantById(variantId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Variant not found"))
        }

        val updated = mappingRepository.updateMapValue(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update map value"))

        return Result.success(updated)
    }

    /**
     * Delete a map value
     */
    fun deleteMapValue(id: Long, tenantId: Long): Result<Boolean> {
        val deleted = mappingRepository.deleteMapValue(id, tenantId)
        return if (deleted) {
            Result.success(true)
        } else {
            Result.failure(IllegalArgumentException("Map value not found"))
        }
    }

    // =====================================================
    // MAP LISTINGS CRUD
    // =====================================================

    /**
     * Get all map listings for tenant
     */
    fun getMapListings(tenantId: Long, userId: Long? = null): List<MapListing> {
        return mappingRepository.findMapListings(tenantId, userId)
    }

    /**
     * Get map listing by ID
     */
    fun getMapListing(id: Long, tenantId: Long): MapListing? {
        return mappingRepository.findMapListingById(id, tenantId)
    }

    /**
     * Create a new map listing
     */
    fun createMapListing(tenantId: Long, userId: Long?, request: CreateMapListingRequest): Result<MapListing> {
        // Check if mapping already exists for this listing + modification
        val existing = mappingRepository.findMapListingByListingId(
            request.listingId,
            tenantId,
            userId,
            request.modificationId
        )

        if (existing != null) {
            return Result.failure(IllegalArgumentException("Mapping already exists for this listing and modification"))
        }

        val mapListing = mappingRepository.createMapListing(tenantId, userId, request)
        return Result.success(mapListing)
    }

    /**
     * Update a map listing
     */
    fun updateMapListing(id: Long, tenantId: Long, request: CreateMapListingRequest): Result<MapListing> {
        val existing = mappingRepository.findMapListingById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Map listing not found"))

        val updated = mappingRepository.updateMapListing(id, tenantId, request)
            ?: return Result.failure(IllegalStateException("Failed to update map listing"))

        return Result.success(updated)
    }

    /**
     * Delete a map listing
     */
    fun deleteMapListing(id: Long, tenantId: Long): Result<Boolean> {
        val deleted = mappingRepository.deleteMapListing(id, tenantId)
        return if (deleted) {
            Result.success(true)
        } else {
            Result.failure(IllegalArgumentException("Map listing not found"))
        }
    }

    /**
     * Delete all map listings for a listing ID
     */
    fun deleteMapListingsByListingId(listingId: String, tenantId: Long): Int {
        return mappingRepository.deleteMapListingsByListingId(listingId, tenantId)
    }

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Save design mappings from order (used when user maps from order view)
     */
    fun saveDesignMap(tenantId: Long, requests: List<SaveDesignMapRequest>): Result<List<MapListing>> {
        val createdMappings = mutableListOf<MapListing>()
        val seenKeys = mutableSetOf<Pair<String, Long?>>()

        for (request in requests) {
            // Create or update map value if variant info is provided
            if (request.valueId1 != null && request.userId != null) {
                val existingValue = mappingRepository.findMapValueByExternalIds(
                    request.valueId1,
                    request.valueId2,
                    tenantId,
                    request.userId
                )

                if (existingValue == null) {
                    mappingRepository.createMapValue(tenantId, request.userId, CreateMapValueRequest(
                        valueId1 = request.valueId1,
                        valueId2 = request.valueId2,
                        variantId = request.variantId,
                        variantModificationId = request.variantModificationId
                    ))
                } else {
                    mappingRepository.updateMapValue(existingValue.id, tenantId, CreateMapValueRequest(
                        valueId1 = request.valueId1,
                        valueId2 = request.valueId2,
                        variantId = request.variantId,
                        variantModificationId = request.variantModificationId
                    ))
                }
            }

            // Delete existing listing mappings for this listing
            mappingRepository.deleteMapListingsByListingId(request.listingId, tenantId)

            // Create new listing mappings
            for (mapping in request.mapListings) {
                val key = Pair(request.listingId, mapping.modificationId)
                if (key in seenKeys) continue
                seenKeys.add(key)

                val created = mappingRepository.createMapListing(tenantId, request.userId, CreateMapListingRequest(
                    listingId = request.listingId,
                    modificationId = mapping.modificationId,
                    lightDesignId = mapping.lightDesignId,
                    darkDesignId = mapping.darkDesignId
                ))
                createdMappings.add(created)
            }
        }

        return Result.success(createdMappings)
    }

    /**
     * Get listing designs (for display in UI)
     */
    fun getListingDesigns(tenantId: Long, listingId: String): ListingDesignsResponse = transaction {
        val query = MapListings
            .leftJoin(Designs, { MapListings.lightDesignId }, { Designs.id })
            .leftJoin(Modifications, { MapListings.modificationId }, { Modifications.id })
            .selectAll()
            .where { (MapListings.tenantId eq tenantId) and (MapListings.listingId eq listingId) }

        val designs = mutableListOf<ListingDesign>()

        for (row in query) {
            val modificationId = row.getOrNull(Modifications.id)?.value
            val modificationName = row.getOrNull(Modifications.name)

            // Light design
            row.getOrNull(MapListings.lightDesignId)?.value?.let { designId ->
                designs.add(ListingDesign(
                    type = "light",
                    designId = designId,
                    thumbnailUrl = row.getOrNull(Designs.thumbnailUrl),
                    designUrl = row.getOrNull(Designs.designUrl),
                    modificationId = modificationId,
                    modificationName = modificationName
                ))
            }

            // Dark design (need separate query)
            row.getOrNull(MapListings.darkDesignId)?.value?.let { designId ->
                val darkDesign = Designs.selectAll()
                    .where { Designs.id eq designId }
                    .singleOrNull()

                designs.add(ListingDesign(
                    type = "dark",
                    designId = designId,
                    thumbnailUrl = darkDesign?.getOrNull(Designs.thumbnailUrl),
                    designUrl = darkDesign?.getOrNull(Designs.designUrl),
                    modificationId = modificationId,
                    modificationName = modificationName
                ))
            }
        }

        ListingDesignsResponse(
            listingId = listingId,
            designs = designs
        )
    }
}

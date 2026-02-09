package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class ProductRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // PRODUCTS
    // =====================================================

    fun findAll(tenantId: Long, categoryId: Long? = null, includeInactive: Boolean = false): List<Product> = transaction {
        var query = Products.selectAll()
            .where { Products.tenantId eq tenantId }

        categoryId?.let {
            query = query.andWhere { Products.categoryId eq it }
        }

        if (!includeInactive) {
            query = query.andWhere { Products.status eq 1 }
        }

        query.orderBy(Products.createdAt, SortOrder.DESC)
            .map { it.toProduct() }
    }

    fun findById(id: Long, tenantId: Long): Product? = transaction {
        Products.selectAll()
            .where { (Products.id eq id) and (Products.tenantId eq tenantId) }
            .singleOrNull()
            ?.toProduct()
    }

    fun findByIdWithDetails(id: Long, tenantId: Long): Product? = transaction {
        val product = findById(id, tenantId) ?: return@transaction null

        val option1s = Option1s.selectAll()
            .where { (Option1s.productId eq id) and (Option1s.tenantId eq tenantId) and (Option1s.status eq 1) }
            .orderBy(Option1s.sortOrder, SortOrder.ASC)
            .map { it.toOption1() }

        val option2s = Option2s.selectAll()
            .where { (Option2s.productId eq id) and (Option2s.tenantId eq tenantId) and (Option2s.status eq 1) }
            .orderBy(Option2s.sortOrder, SortOrder.ASC)
            .map { it.toOption2() }

        val variants = Variants.selectAll()
            .where { (Variants.productId eq id) and (Variants.tenantId eq tenantId) }
            .map { it.toVariant() }

        product.copy(
            option1s = option1s,
            option2s = option2s,
            variants = variants
        )
    }

    fun create(tenantId: Long, request: CreateProductRequest): Product = transaction {
        val id = Products.insertAndGetId {
            it[this.tenantId] = tenantId
            it[categoryId] = request.categoryId
            it[title] = request.title
            it[description] = request.description
            it[basePrice] = request.basePrice
            it[option1Name] = request.option1Name
            it[option2Name] = request.option2Name
            it[option3Name] = request.option3Name
            it[designType] = request.designType
            it[supplierId] = request.supplierId
            it[tags] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), request.tags)
        }

        findById(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, request: UpdateProductRequest): Product? = transaction {
        val updated = Products.update(
            where = { (Products.id eq id) and (Products.tenantId eq tenantId) }
        ) {
            request.categoryId?.let { catId -> it[categoryId] = catId }
            request.title?.let { title -> it[Products.title] = title }
            request.description?.let { desc -> it[description] = desc }
            request.basePrice?.let { price -> it[basePrice] = price }
            request.option1Name?.let { name -> it[option1Name] = name }
            request.option2Name?.let { name -> it[option2Name] = name }
            request.option3Name?.let { name -> it[option3Name] = name }
            request.designType?.let { type -> it[designType] = type }
            request.supplierId?.let { supplier -> it[supplierId] = supplier }
            request.status?.let { status -> it[Products.status] = status }
            request.tags?.let { tagList ->
                it[tags] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), tagList)
            }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findById(id, tenantId) else null
    }

    fun delete(id: Long, tenantId: Long): Boolean = transaction {
        Products.update(
            where = { (Products.id eq id) and (Products.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // OPTION1S (Size options)
    // =====================================================

    fun findOption1s(productId: Long, tenantId: Long): List<Option1> = transaction {
        Option1s.selectAll()
            .where { (Option1s.productId eq productId) and (Option1s.tenantId eq tenantId) and (Option1s.status eq 1) }
            .orderBy(Option1s.sortOrder, SortOrder.ASC)
            .map { it.toOption1() }
    }

    fun createOption1(productId: Long, tenantId: Long, request: CreateOption1Request): Option1 = transaction {
        val id = Option1s.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.productId] = productId
            it[name] = request.name
            it[sortOrder] = request.sortOrder
        }

        Option1s.selectAll()
            .where { Option1s.id eq id }
            .single()
            .toOption1()
    }

    fun deleteOption1(id: Long, tenantId: Long): Boolean = transaction {
        Option1s.update(
            where = { (Option1s.id eq id) and (Option1s.tenantId eq tenantId) }
        ) {
            it[status] = -1
        } > 0
    }

    // =====================================================
    // OPTION2S (Color options)
    // =====================================================

    fun findOption2s(productId: Long, tenantId: Long): List<Option2> = transaction {
        Option2s.selectAll()
            .where { (Option2s.productId eq productId) and (Option2s.tenantId eq tenantId) and (Option2s.status eq 1) }
            .orderBy(Option2s.sortOrder, SortOrder.ASC)
            .map { it.toOption2() }
    }

    fun createOption2(productId: Long, tenantId: Long, request: CreateOption2Request): Option2 = transaction {
        val id = Option2s.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.productId] = productId
            it[name] = request.name
            it[hexColor] = request.hexColor
            it[isDark] = request.isDark
            it[sortOrder] = request.sortOrder
        }

        Option2s.selectAll()
            .where { Option2s.id eq id }
            .single()
            .toOption2()
    }

    fun deleteOption2(id: Long, tenantId: Long): Boolean = transaction {
        Option2s.update(
            where = { (Option2s.id eq id) and (Option2s.tenantId eq tenantId) }
        ) {
            it[status] = -1
        } > 0
    }

    // =====================================================
    // VARIANTS
    // =====================================================

    fun findVariants(productId: Long, tenantId: Long): List<Variant> = transaction {
        Variants.selectAll()
            .where { (Variants.productId eq productId) and (Variants.tenantId eq tenantId) }
            .map { it.toVariant() }
    }

    fun findVariantById(id: Long, tenantId: Long): Variant? = transaction {
        Variants.selectAll()
            .where { (Variants.id eq id) and (Variants.tenantId eq tenantId) }
            .singleOrNull()
            ?.toVariant()
    }

    fun createVariant(productId: Long, tenantId: Long, request: CreateVariantRequest): Variant = transaction {
        val id = Variants.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.productId] = productId
            it[option1Value] = request.option1Value
            it[option2Value] = request.option2Value
            it[option3Value] = request.option3Value
            it[option1Id] = request.option1Id
            it[option2Id] = request.option2Id
            it[sku] = request.sku
            it[price] = request.price
            it[cost] = request.cost
            it[weight] = request.weight
            it[width1] = request.width1
            it[width2] = request.width2
            it[isDark] = request.isDark
            it[inStock] = request.inStock
            it[inHouse] = request.inHouse
            it[stockQuantity] = request.stockQuantity
            it[status] = request.status
        }

        findVariantById(id.value, tenantId)!!
    }

    fun updateVariant(id: Long, tenantId: Long, request: UpdateVariantRequest): Variant? = transaction {
        val updated = Variants.update(
            where = { (Variants.id eq id) and (Variants.tenantId eq tenantId) }
        ) {
            request.option1Value?.let { value -> it[option1Value] = value }
            request.option2Value?.let { value -> it[option2Value] = value }
            request.option3Value?.let { value -> it[option3Value] = value }
            request.sku?.let { sku -> it[Variants.sku] = sku }
            request.price?.let { price -> it[Variants.price] = price }
            request.cost?.let { cost -> it[Variants.cost] = cost }
            request.weight?.let { weight -> it[Variants.weight] = weight }
            request.width1?.let { w1 -> it[width1] = w1 }
            request.width2?.let { w2 -> it[width2] = w2 }
            request.isDark?.let { dark -> it[isDark] = dark }
            request.inStock?.let { stock -> it[inStock] = stock }
            request.inHouse?.let { house -> it[inHouse] = house }
            request.stockQuantity?.let { qty -> it[stockQuantity] = qty }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findVariantById(id, tenantId) else null
    }

    fun deleteVariant(id: Long, tenantId: Long): Boolean = transaction {
        Variants.deleteWhere {
            (Variants.id eq id) and (Variants.tenantId eq tenantId)
        } > 0
    }

    fun bulkUpdateVariants(productId: Long, tenantId: Long, request: BulkUpdateVariantsRequest): List<Variant> = transaction {
        // Build the where clause based on variantIds or all variants of the product
        val whereClause: Op<Boolean> = if (request.variantIds.isNullOrEmpty()) {
            (Variants.productId eq productId) and (Variants.tenantId eq tenantId)
        } else {
            (Variants.productId eq productId) and (Variants.tenantId eq tenantId) and (Variants.id inList request.variantIds)
        }

        Variants.update(where = { whereClause }) {
            request.inHouse?.let { house -> it[inHouse] = house }
            request.inStock?.let { stock -> it[inStock] = stock }
            request.status?.let { s -> it[status] = s }
            request.price?.let { p -> it[price] = p }
            request.width1?.let { w1 -> it[width1] = w1 }
            request.width2?.let { w2 -> it[width2] = w2 }
            it[updatedAt] = Instant.now()
        }

        // Return updated variants
        findVariants(productId, tenantId)
    }

    fun generateVariants(productId: Long, tenantId: Long, request: GenerateVariantsRequest): List<Variant> = transaction {
        val option1s = findOption1s(productId, tenantId)
        val option2s = findOption2s(productId, tenantId)

        val variants = mutableListOf<Variant>()

        // Clear existing variants first
        Variants.deleteWhere {
            (Variants.productId eq productId) and (Variants.tenantId eq tenantId)
        }

        if (option1s.isEmpty() && option2s.isEmpty()) {
            // Single default variant
            val variant = createVariant(productId, tenantId, CreateVariantRequest(
                price = request.defaultPrice,
                cost = request.defaultCost,
                sku = request.skuPrefix
            ))
            variants.add(variant)
        } else if (option2s.isEmpty()) {
            // Only option1s
            option1s.forEach { opt1 ->
                val variant = createVariant(productId, tenantId, CreateVariantRequest(
                    option1Value = opt1.name,
                    option1Id = opt1.id,
                    price = request.defaultPrice,
                    cost = request.defaultCost,
                    sku = "${request.skuPrefix ?: ""}-${opt1.name}".trim('-')
                ))
                variants.add(variant)
            }
        } else if (option1s.isEmpty()) {
            // Only option2s
            option2s.forEach { opt2 ->
                val variant = createVariant(productId, tenantId, CreateVariantRequest(
                    option2Value = opt2.name,
                    option2Id = opt2.id,
                    isDark = opt2.isDark,
                    price = request.defaultPrice,
                    cost = request.defaultCost,
                    sku = "${request.skuPrefix ?: ""}-${opt2.name}".trim('-')
                ))
                variants.add(variant)
            }
        } else {
            // Cross product of option1s and option2s
            option1s.forEach { opt1 ->
                option2s.forEach { opt2 ->
                    val variant = createVariant(productId, tenantId, CreateVariantRequest(
                        option1Value = opt1.name,
                        option2Value = opt2.name,
                        option1Id = opt1.id,
                        option2Id = opt2.id,
                        isDark = opt2.isDark,
                        price = request.defaultPrice,
                        cost = request.defaultCost,
                        sku = "${request.skuPrefix ?: ""}-${opt1.name}-${opt2.name}".trim('-')
                    ))
                    variants.add(variant)
                }
            }
        }

        variants
    }

    // =====================================================
    // VARIANT MODIFICATIONS (Size-based dimensions)
    // =====================================================

    fun findVariantModifications(productId: Long, tenantId: Long): List<VariantModification> = transaction {
        VariantModifications.selectAll()
            .where { (VariantModifications.productId eq productId) and (VariantModifications.tenantId eq tenantId) and (VariantModifications.status eq 1) }
            .map { it.toVariantModification() }
    }

    fun findVariantModificationByOption1(productId: Long, option1Id: Long, tenantId: Long): VariantModification? = transaction {
        VariantModifications.selectAll()
            .where {
                (VariantModifications.productId eq productId) and
                (VariantModifications.option1Id eq option1Id) and
                (VariantModifications.tenantId eq tenantId)
            }
            .singleOrNull()
            ?.toVariantModification()
    }

    fun createVariantModification(productId: Long, tenantId: Long, request: CreateVariantModificationRequest): VariantModification = transaction {
        val id = VariantModifications.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.productId] = productId
            it[option1Id] = request.option1Id
            it[width] = request.width
            it[height] = request.height
            it[depth] = request.depth
            it[weight] = request.weight
        }

        VariantModifications.selectAll()
            .where { VariantModifications.id eq id }
            .single()
            .toVariantModification()
    }

    fun upsertVariantModification(productId: Long, tenantId: Long, request: CreateVariantModificationRequest): VariantModification = transaction {
        val existing = findVariantModificationByOption1(productId, request.option1Id, tenantId)

        if (existing != null) {
            VariantModifications.update(
                where = { VariantModifications.id eq existing.id }
            ) {
                it[width] = request.width
                it[height] = request.height
                it[depth] = request.depth
                it[weight] = request.weight
                it[updatedAt] = Instant.now()
            }
            findVariantModificationByOption1(productId, request.option1Id, tenantId)!!
        } else {
            createVariantModification(productId, tenantId, request)
        }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toProduct(): Product {
        val tagsJson = this[Products.tags]
        val tagsList = try {
            json.decodeFromString<List<String>>(tagsJson)
        } catch (e: Exception) {
            emptyList()
        }

        return Product(
            id = this[Products.id].value,
            tenantId = this[Products.tenantId].value,
            categoryId = this[Products.categoryId]?.value,
            title = this[Products.title],
            description = this[Products.description],
            basePrice = this[Products.basePrice],
            option1Name = this[Products.option1Name],
            option2Name = this[Products.option2Name],
            option3Name = this[Products.option3Name],
            designType = this[Products.designType],
            supplierId = this[Products.supplierId],
            status = this[Products.status],
            tags = tagsList,
            createdAt = this[Products.createdAt].toString(),
            updatedAt = this[Products.updatedAt].toString()
        )
    }

    private fun ResultRow.toOption1(): Option1 = Option1(
        id = this[Option1s.id].value,
        tenantId = this[Option1s.tenantId].value,
        productId = this[Option1s.productId].value,
        name = this[Option1s.name],
        sortOrder = this[Option1s.sortOrder],
        status = this[Option1s.status],
        createdAt = this[Option1s.createdAt].toString()
    )

    private fun ResultRow.toOption2(): Option2 = Option2(
        id = this[Option2s.id].value,
        tenantId = this[Option2s.tenantId].value,
        productId = this[Option2s.productId].value,
        name = this[Option2s.name],
        hexColor = this[Option2s.hexColor],
        isDark = this[Option2s.isDark],
        sortOrder = this[Option2s.sortOrder],
        status = this[Option2s.status],
        createdAt = this[Option2s.createdAt].toString()
    )

    private fun ResultRow.toVariant(): Variant = Variant(
        id = this[Variants.id].value,
        tenantId = this[Variants.tenantId].value,
        productId = this[Variants.productId].value,
        option1Value = this[Variants.option1Value],
        option2Value = this[Variants.option2Value],
        option3Value = this[Variants.option3Value],
        option1Id = this[Variants.option1Id]?.value,
        option2Id = this[Variants.option2Id]?.value,
        sku = this[Variants.sku],
        price = this[Variants.price],
        cost = this[Variants.cost],
        weight = this[Variants.weight],
        width1 = this[Variants.width1],
        width2 = this[Variants.width2],
        isDark = this[Variants.isDark],
        inStock = this[Variants.inStock],
        inHouse = this[Variants.inHouse],
        stockQuantity = this[Variants.stockQuantity],
        status = this[Variants.status],
        createdAt = this[Variants.createdAt].toString(),
        updatedAt = this[Variants.updatedAt].toString()
    )

    private fun ResultRow.toVariantModification(): VariantModification = VariantModification(
        id = this[VariantModifications.id].value,
        tenantId = this[VariantModifications.tenantId].value,
        productId = this[VariantModifications.productId].value,
        option1Id = this[VariantModifications.option1Id].value,
        width = this[VariantModifications.width],
        height = this[VariantModifications.height],
        depth = this[VariantModifications.depth],
        weight = this[VariantModifications.weight],
        status = this[VariantModifications.status],
        createdAt = this[VariantModifications.createdAt].toString(),
        updatedAt = this[VariantModifications.updatedAt].toString()
    )
}

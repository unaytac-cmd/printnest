package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.Modifications
import com.printnest.domain.tables.ProductCategories
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class CategoryRepository {

    fun findAll(tenantId: Long, includeInactive: Boolean = false): List<ProductCategory> = transaction {
        val query = ProductCategories.selectAll()
            .where { ProductCategories.tenantId eq tenantId }

        if (!includeInactive) {
            query.andWhere { ProductCategories.status eq 1 }
        }

        query.orderBy(ProductCategories.sortOrder, SortOrder.ASC)
            .map { it.toProductCategory() }
    }

    fun findById(id: Long, tenantId: Long): ProductCategory? = transaction {
        ProductCategories.selectAll()
            .where { (ProductCategories.id eq id) and (ProductCategories.tenantId eq tenantId) }
            .singleOrNull()
            ?.toProductCategory()
    }

    fun findByIdWithModifications(id: Long, tenantId: Long): ProductCategory? = transaction {
        val category = ProductCategories.selectAll()
            .where { (ProductCategories.id eq id) and (ProductCategories.tenantId eq tenantId) }
            .singleOrNull()
            ?.toProductCategory() ?: return@transaction null

        val modifications = Modifications.selectAll()
            .where { (Modifications.categoryId eq id) and (Modifications.tenantId eq tenantId) and (Modifications.status eq 1) }
            .orderBy(Modifications.sortOrder, SortOrder.ASC)
            .map { it.toModification() }

        category.copy(modifications = modifications)
    }

    fun findHierarchical(tenantId: Long): List<ProductCategory> = transaction {
        val allCategories = findAll(tenantId)
        buildHierarchy(allCategories, null)
    }

    private fun buildHierarchy(categories: List<ProductCategory>, parentId: Long?): List<ProductCategory> {
        return categories
            .filter { it.parentCategoryId == parentId }
            .map { category ->
                val children = buildHierarchy(categories, category.id)
                category.copy(childCategories = children)
            }
    }

    fun create(tenantId: Long, request: CreateCategoryRequest): ProductCategory = transaction {
        val id = ProductCategories.insertAndGetId {
            it[this.tenantId] = tenantId
            it[name] = request.name
            it[description] = request.description
            it[parentCategoryId] = request.parentCategoryId
            it[isHeavy] = request.isHeavy
            it[sortOrder] = request.sortOrder
        }

        findById(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, request: UpdateCategoryRequest): ProductCategory? = transaction {
        val updated = ProductCategories.update(
            where = { (ProductCategories.id eq id) and (ProductCategories.tenantId eq tenantId) }
        ) {
            request.name?.let { name -> it[ProductCategories.name] = name }
            request.description?.let { desc -> it[description] = desc }
            request.parentCategoryId?.let { parentId -> it[parentCategoryId] = parentId }
            request.isHeavy?.let { heavy -> it[isHeavy] = heavy }
            request.status?.let { status -> it[ProductCategories.status] = status }
            request.sortOrder?.let { order -> it[sortOrder] = order }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findById(id, tenantId) else null
    }

    fun delete(id: Long, tenantId: Long): Boolean = transaction {
        // Soft delete by setting status to -1
        val updated = ProductCategories.update(
            where = { (ProductCategories.id eq id) and (ProductCategories.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    fun hardDelete(id: Long, tenantId: Long): Boolean = transaction {
        ProductCategories.deleteWhere {
            (ProductCategories.id eq id) and (ProductCategories.tenantId eq tenantId)
        } > 0
    }

    // =====================================================
    // MODIFICATIONS (Print Locations)
    // =====================================================

    fun findModifications(categoryId: Long, tenantId: Long): List<Modification> = transaction {
        Modifications.selectAll()
            .where { (Modifications.categoryId eq categoryId) and (Modifications.tenantId eq tenantId) and (Modifications.status eq 1) }
            .orderBy(Modifications.sortOrder, SortOrder.ASC)
            .map { it.toModification() }
    }

    fun findModificationById(id: Long, tenantId: Long): Modification? = transaction {
        Modifications.selectAll()
            .where { (Modifications.id eq id) and (Modifications.tenantId eq tenantId) }
            .singleOrNull()
            ?.toModification()
    }

    fun createModification(categoryId: Long, tenantId: Long, request: CreateModificationRequest): Modification = transaction {
        val id = Modifications.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.categoryId] = categoryId
            it[name] = request.name
            it[description] = request.description
            it[priceDifference] = request.priceDifference
            it[useWidth] = request.useWidth
            it[sortOrder] = request.sortOrder
        }

        findModificationById(id.value, tenantId)!!
    }

    fun updateModification(id: Long, tenantId: Long, request: UpdateModificationRequest): Modification? = transaction {
        val updated = Modifications.update(
            where = { (Modifications.id eq id) and (Modifications.tenantId eq tenantId) }
        ) {
            request.name?.let { name -> it[Modifications.name] = name }
            request.description?.let { desc -> it[description] = desc }
            request.priceDifference?.let { price -> it[priceDifference] = price }
            request.useWidth?.let { width -> it[useWidth] = width }
            request.sortOrder?.let { order -> it[sortOrder] = order }
            request.status?.let { status -> it[Modifications.status] = status }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findModificationById(id, tenantId) else null
    }

    fun deleteModification(id: Long, tenantId: Long): Boolean = transaction {
        Modifications.update(
            where = { (Modifications.id eq id) and (Modifications.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toProductCategory(): ProductCategory = ProductCategory(
        id = this[ProductCategories.id].value,
        tenantId = this[ProductCategories.tenantId].value,
        name = this[ProductCategories.name],
        description = this[ProductCategories.description],
        parentCategoryId = this[ProductCategories.parentCategoryId]?.value,
        isHeavy = this[ProductCategories.isHeavy],
        status = this[ProductCategories.status],
        sortOrder = this[ProductCategories.sortOrder],
        createdAt = this[ProductCategories.createdAt].toString(),
        updatedAt = this[ProductCategories.updatedAt].toString()
    )

    private fun ResultRow.toModification(): Modification = Modification(
        id = this[Modifications.id].value,
        tenantId = this[Modifications.tenantId].value,
        categoryId = this[Modifications.categoryId].value,
        name = this[Modifications.name],
        description = this[Modifications.description],
        priceDifference = this[Modifications.priceDifference],
        useWidth = this[Modifications.useWidth],
        sortOrder = this[Modifications.sortOrder],
        status = this[Modifications.status],
        createdAt = this[Modifications.createdAt].toString(),
        updatedAt = this[Modifications.updatedAt].toString()
    )
}

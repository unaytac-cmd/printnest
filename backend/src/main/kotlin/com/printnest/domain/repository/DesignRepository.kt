package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.Designs
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant

class DesignRepository(
    private val json: Json
) {

    // =====================================================
    // QUERIES
    // =====================================================

    fun findById(id: Long, tenantId: Long): Design? = transaction {
        Designs.selectAll()
            .where { (Designs.id eq id) and (Designs.tenantId eq tenantId) and (Designs.status neq DesignStatus.DELETED.code) }
            .singleOrNull()
            ?.toDesign()
    }

    fun findByIdWithDeleted(id: Long, tenantId: Long): Design? = transaction {
        Designs.selectAll()
            .where { (Designs.id eq id) and (Designs.tenantId eq tenantId) }
            .singleOrNull()
            ?.toDesign()
    }

    fun findByHash(fileHash: String, tenantId: Long): Design? = transaction {
        Designs.selectAll()
            .where {
                (Designs.fileHash eq fileHash) and
                (Designs.tenantId eq tenantId) and
                (Designs.status neq DesignStatus.DELETED.code)
            }
            .singleOrNull()
            ?.toDesign()
    }

    fun findAll(
        tenantId: Long,
        filters: DesignFilters
    ): Pair<List<DesignListItem>, Int> = transaction {
        var query = Designs.selectAll()
            .where { (Designs.tenantId eq tenantId) and (Designs.status neq DesignStatus.DELETED.code) }

        // Design type filter
        filters.designType?.let { type ->
            query = query.andWhere { Designs.designType eq type }
        }

        // Status filter
        filters.status?.let { status ->
            query = query.andWhere { Designs.status eq status }
        }

        // User filter
        filters.userId?.let { userId ->
            query = query.andWhere { Designs.userId eq userId }
        }

        // Search filter
        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere { Designs.title.lowerCase() like "%${search.lowercase()}%" }
            }
        }

        // Count total
        val total = query.count().toInt()

        // Sorting
        val sortColumn = when (filters.sortBy) {
            "title" -> Designs.title
            "designType" -> Designs.designType
            else -> Designs.createdAt
        }
        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val designs = query.map { it.toDesignListItem() }

        Pair(designs, total)
    }

    fun findByIds(ids: List<Long>, tenantId: Long): List<Design> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()

        Designs.selectAll()
            .where {
                (Designs.id inList ids) and
                (Designs.tenantId eq tenantId) and
                (Designs.status neq DesignStatus.DELETED.code)
            }
            .map { it.toDesign() }
    }

    // =====================================================
    // MUTATIONS
    // =====================================================

    fun create(
        tenantId: Long,
        userId: Long?,
        request: CreateDesignRequest
    ): Design = transaction {
        val id = Designs.insertAndGetId {
            it[Designs.tenantId] = tenantId
            it[Designs.userId] = userId
            it[title] = request.title
            it[fileHash] = request.fileHash ?: ""
            it[designType] = request.designType
            it[designUrl] = request.designUrl
            it[thumbnailUrl] = request.thumbnailUrl
            it[width] = request.width?.let { w -> BigDecimal.valueOf(w) }
            it[height] = request.height?.let { h -> BigDecimal.valueOf(h) }
            it[metadata] = request.metadata?.let { m -> json.encodeToString(DesignMetadata.serializer(), m) } ?: "{}"
            it[status] = DesignStatus.ACTIVE.code
        }

        findByIdWithDeleted(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, request: UpdateDesignRequest): Design? = transaction {
        val updated = Designs.update({
            (Designs.id eq id) and (Designs.tenantId eq tenantId)
        }) {
            request.title?.let { title -> it[Designs.title] = title }
            request.width?.let { w -> it[width] = BigDecimal.valueOf(w) }
            request.height?.let { h -> it[height] = BigDecimal.valueOf(h) }
            request.status?.let { s -> it[status] = s }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findById(id, tenantId) else null
    }

    fun updateUrl(id: Long, tenantId: Long, designUrl: String, thumbnailUrl: String?): Boolean = transaction {
        Designs.update({
            (Designs.id eq id) and (Designs.tenantId eq tenantId)
        }) {
            it[Designs.designUrl] = designUrl
            thumbnailUrl?.let { url -> it[Designs.thumbnailUrl] = url }
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateHash(id: Long, tenantId: Long, fileHash: String): Boolean = transaction {
        Designs.update({
            (Designs.id eq id) and (Designs.tenantId eq tenantId)
        }) {
            it[Designs.fileHash] = fileHash
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun delete(id: Long, tenantId: Long): Boolean = transaction {
        Designs.update({
            (Designs.id eq id) and (Designs.tenantId eq tenantId)
        }) {
            it[status] = DesignStatus.DELETED.code
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun hardDelete(id: Long, tenantId: Long): Boolean = transaction {
        Designs.deleteWhere {
            (Designs.id eq id) and (Designs.tenantId eq tenantId)
        } > 0
    }

    fun bulkDelete(ids: List<Long>, tenantId: Long): Int = transaction {
        if (ids.isEmpty()) return@transaction 0

        Designs.update({
            (Designs.id inList ids) and (Designs.tenantId eq tenantId)
        }) {
            it[status] = DesignStatus.DELETED.code
            it[updatedAt] = Instant.now()
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun ResultRow.toDesign(): Design {
        val typeCode = this[Designs.designType]
        val typeLabel = DesignType.fromCode(typeCode)?.label ?: "Unknown"

        val metadataJson = this[Designs.metadata]
        val metadata = try {
            if (metadataJson.isNotBlank() && metadataJson != "{}") {
                json.decodeFromString<DesignMetadata>(metadataJson)
            } else null
        } catch (e: Exception) {
            null
        }

        return Design(
            id = this[Designs.id].value,
            tenantId = this[Designs.tenantId].value,
            userId = this[Designs.userId]?.value,
            title = this[Designs.title],
            fileHash = this[Designs.fileHash],
            designType = typeCode,
            designTypeLabel = typeLabel,
            designUrl = this[Designs.designUrl],
            thumbnailUrl = this[Designs.thumbnailUrl],
            width = this[Designs.width]?.toDouble(),
            height = this[Designs.height]?.toDouble(),
            metadata = metadata,
            status = this[Designs.status],
            createdAt = this[Designs.createdAt].toString(),
            updatedAt = this[Designs.updatedAt].toString(),
            fileSize = metadata?.fileSize,
            fileName = metadata?.originalFileName
        )
    }

    private fun ResultRow.toDesignListItem(): DesignListItem {
        val typeCode = this[Designs.designType]
        val typeLabel = DesignType.fromCode(typeCode)?.label ?: "Unknown"

        return DesignListItem(
            id = this[Designs.id].value,
            title = this[Designs.title],
            designType = typeCode,
            designTypeLabel = typeLabel,
            thumbnailUrl = this[Designs.thumbnailUrl],
            width = this[Designs.width]?.toDouble(),
            height = this[Designs.height]?.toDouble(),
            status = this[Designs.status],
            createdAt = this[Designs.createdAt].toString()
        )
    }
}

package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.Gangsheets
import com.printnest.domain.tables.GangsheetRolls
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class GangsheetRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // GANGSHEET CRUD
    // =====================================================

    fun findAll(tenantId: Long, filters: GangsheetFilters): Pair<List<GangsheetListItem>, Int> = transaction {
        var query = Gangsheets.selectAll()
            .where { Gangsheets.tenantId eq tenantId }

        // Apply filters
        filters.status?.let { status ->
            query = query.andWhere { Gangsheets.status eq status }
        }

        filters.search?.let { search ->
            if (search.isNotBlank()) {
                query = query.andWhere { Gangsheets.name like "%$search%" }
            }
        }

        // Get total count
        val total = query.count().toInt()

        // Apply sorting
        val sortColumn = when (filters.sortBy) {
            "createdAt" -> Gangsheets.createdAt
            "name" -> Gangsheets.name
            "status" -> Gangsheets.status
            else -> Gangsheets.createdAt
        }

        val sortOrder = if (filters.sortOrder.uppercase() == "ASC") SortOrder.ASC else SortOrder.DESC
        query = query.orderBy(sortColumn, sortOrder)

        // Apply pagination
        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query.limit(filters.limit).offset(offset)

        val gangsheets = query.map { it.toGangsheetListItem() }

        Pair(gangsheets, total)
    }

    fun findById(id: Long, tenantId: Long): Gangsheet? = transaction {
        Gangsheets.selectAll()
            .where { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
            .singleOrNull()
            ?.toGangsheet()
    }

    fun findByIdWithRolls(id: Long, tenantId: Long): Gangsheet? = transaction {
        val gangsheet = findById(id, tenantId) ?: return@transaction null

        val rolls = GangsheetRolls.selectAll()
            .where { GangsheetRolls.gangsheetId eq id }
            .orderBy(GangsheetRolls.rollNumber, SortOrder.ASC)
            .map { it.toGangsheetRollFull() }

        gangsheet.copy(rolls = rolls)
    }

    fun create(tenantId: Long, name: String, orderIds: List<Long>, settings: GangsheetSettingsFull): Gangsheet = transaction {
        val orderIdsJson = json.encodeToString(ListSerializer(Long.serializer()), orderIds)
        val settingsJson = json.encodeToString(GangsheetSettingsFull.serializer(), settings)

        val id = Gangsheets.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.name] = name
            it[this.status] = GangsheetStatus.PENDING.code
            it[this.orderIds] = orderIdsJson
            it[this.settings] = settingsJson
        }

        findById(id.value, tenantId)!!
    }

    fun update(id: Long, tenantId: Long, block: Gangsheets.(UpdateBuilder<Int>) -> Unit): Boolean = transaction {
        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) { block(it) } > 0
    }

    fun updateStatus(
        id: Long,
        tenantId: Long,
        status: GangsheetStatus,
        errorMessage: String? = null,
        processedDesigns: Int? = null
    ): Boolean = transaction {
        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) {
            it[Gangsheets.status] = status.code
            errorMessage?.let { msg -> it[Gangsheets.errorMessage] = msg }
            processedDesigns?.let { count -> it[Gangsheets.processedDesigns] = count }

            if (status == GangsheetStatus.COMPLETED || status == GangsheetStatus.FAILED) {
                it[completedAt] = Instant.now()
            }
        } > 0
    }

    fun updateProgress(id: Long, tenantId: Long, processedDesigns: Int): Boolean = transaction {
        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) {
            it[Gangsheets.processedDesigns] = processedDesigns
        } > 0
    }

    fun setTotalDesigns(id: Long, tenantId: Long, totalDesigns: Int): Boolean = transaction {
        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) {
            it[Gangsheets.totalDesigns] = totalDesigns
        } > 0
    }

    fun complete(
        id: Long,
        tenantId: Long,
        downloadUrl: String,
        totalRolls: Int,
        rollUrls: List<String>
    ): Boolean = transaction {
        val rollUrlsJson = json.encodeToString(ListSerializer(String.serializer()), rollUrls)

        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) {
            it[status] = GangsheetStatus.COMPLETED.code
            it[Gangsheets.downloadUrl] = downloadUrl
            it[Gangsheets.totalRolls] = totalRolls
            it[Gangsheets.rollUrls] = rollUrlsJson
            it[completedAt] = Instant.now()
        } > 0
    }

    fun fail(id: Long, tenantId: Long, errorMessage: String): Boolean = transaction {
        Gangsheets.update(
            where = { (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId) }
        ) {
            it[status] = GangsheetStatus.FAILED.code
            it[Gangsheets.errorMessage] = errorMessage
            it[completedAt] = Instant.now()
        } > 0
    }

    fun delete(id: Long, tenantId: Long): Boolean = transaction {
        Gangsheets.deleteWhere {
            (Gangsheets.id eq id) and (Gangsheets.tenantId eq tenantId)
        } > 0
    }

    // =====================================================
    // GANGSHEET ROLLS
    // =====================================================

    fun findRolls(gangsheetId: Long): List<GangsheetRollFull> = transaction {
        GangsheetRolls.selectAll()
            .where { GangsheetRolls.gangsheetId eq gangsheetId }
            .orderBy(GangsheetRolls.rollNumber, SortOrder.ASC)
            .map { it.toGangsheetRollFull() }
    }

    fun createRoll(
        gangsheetId: Long,
        rollNumber: Int,
        widthPixels: Int,
        heightPixels: Int,
        designCount: Int,
        fileUrl: String?
    ): GangsheetRollFull = transaction {
        val id = GangsheetRolls.insertAndGetId {
            it[this.gangsheetId] = gangsheetId
            it[this.rollNumber] = rollNumber
            it[this.widthPixels] = widthPixels
            it[this.heightPixels] = heightPixels
            it[this.designCount] = designCount
            it[this.fileUrl] = fileUrl
        }

        GangsheetRolls.selectAll()
            .where { GangsheetRolls.id eq id }
            .single()
            .toGangsheetRollFull()
    }

    fun updateRollUrl(rollId: Long, fileUrl: String): Boolean = transaction {
        GangsheetRolls.update(
            where = { GangsheetRolls.id eq rollId }
        ) {
            it[GangsheetRolls.fileUrl] = fileUrl
        } > 0
    }

    fun deleteRolls(gangsheetId: Long): Int = transaction {
        GangsheetRolls.deleteWhere { GangsheetRolls.gangsheetId eq gangsheetId }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toGangsheet(): Gangsheet {
        val orderIdsJson = this[Gangsheets.orderIds]
        val orderIds = try {
            if (orderIdsJson.isNotEmpty() && orderIdsJson != "[]") {
                json.decodeFromString<List<Long>>(orderIdsJson)
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val settingsJson = this[Gangsheets.settings]
        val settings = try {
            if (settingsJson.isNotEmpty() && settingsJson != "{}") {
                json.decodeFromString<GangsheetSettingsFull>(settingsJson)
            } else GangsheetSettingsFull()
        } catch (e: Exception) { GangsheetSettingsFull() }

        val rollUrlsJson = this[Gangsheets.rollUrls]
        val rollUrls = try {
            if (rollUrlsJson.isNotEmpty() && rollUrlsJson != "[]") {
                json.decodeFromString<List<String>>(rollUrlsJson)
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        val status = GangsheetStatus.fromCode(this[Gangsheets.status])

        return Gangsheet(
            id = this[Gangsheets.id].value,
            tenantId = this[Gangsheets.tenantId].value,
            name = this[Gangsheets.name],
            status = status.code,
            statusLabel = status.label,
            orderIds = orderIds,
            settings = settings,
            downloadUrl = this[Gangsheets.downloadUrl],
            errorMessage = this[Gangsheets.errorMessage],
            totalDesigns = this[Gangsheets.totalDesigns],
            processedDesigns = this[Gangsheets.processedDesigns],
            totalRolls = this[Gangsheets.totalRolls],
            rollUrls = rollUrls,
            createdAt = this[Gangsheets.createdAt].toString(),
            completedAt = this[Gangsheets.completedAt]?.toString()
        )
    }

    private fun ResultRow.toGangsheetListItem(): GangsheetListItem {
        val status = GangsheetStatus.fromCode(this[Gangsheets.status])

        return GangsheetListItem(
            id = this[Gangsheets.id].value,
            name = this[Gangsheets.name],
            status = status.code,
            statusLabel = status.label,
            totalDesigns = this[Gangsheets.totalDesigns],
            totalRolls = this[Gangsheets.totalRolls],
            downloadUrl = this[Gangsheets.downloadUrl],
            createdAt = this[Gangsheets.createdAt].toString()
        )
    }

    private fun ResultRow.toGangsheetRollFull(): GangsheetRollFull = GangsheetRollFull(
        id = this[GangsheetRolls.id].value,
        gangsheetId = this[GangsheetRolls.gangsheetId].value,
        rollNumber = this[GangsheetRolls.rollNumber],
        widthPixels = this[GangsheetRolls.widthPixels],
        heightPixels = this[GangsheetRolls.heightPixels],
        designCount = this[GangsheetRolls.designCount],
        fileUrl = this[GangsheetRolls.fileUrl],
        createdAt = this[GangsheetRolls.createdAt].toString()
    )
}

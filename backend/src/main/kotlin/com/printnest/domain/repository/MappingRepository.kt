package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class MappingRepository {

    // =====================================================
    // MAP VALUES - Variant Mapping
    // =====================================================

    fun findMapValues(tenantId: Long, userId: Long? = null): List<MapValue> = transaction {
        var query = MapValues.selectAll()
            .where { MapValues.tenantId eq tenantId }

        userId?.let {
            query = query.andWhere { MapValues.userId eq it }
        }

        query.map { it.toMapValue() }
    }

    fun findMapValueById(id: Long, tenantId: Long): MapValue? = transaction {
        MapValues.selectAll()
            .where { (MapValues.id eq id) and (MapValues.tenantId eq tenantId) }
            .singleOrNull()
            ?.toMapValue()
    }

    fun findMapValueByExternalIds(
        valueId1: String?,
        valueId2: String?,
        tenantId: Long,
        userId: Long? = null
    ): MapValue? = transaction {
        var query = MapValues.selectAll()
            .where { MapValues.tenantId eq tenantId }

        if (valueId1 != null) {
            query = query.andWhere { MapValues.valueId1 eq valueId1 }
        } else {
            query = query.andWhere { MapValues.valueId1.isNull() }
        }

        if (valueId2 != null) {
            query = query.andWhere { MapValues.valueId2 eq valueId2 }
        } else {
            query = query.andWhere { MapValues.valueId2.isNull() }
        }

        userId?.let {
            query = query.andWhere { MapValues.userId eq it }
        }

        query.singleOrNull()?.toMapValue()
    }

    fun createMapValue(tenantId: Long, userId: Long?, request: CreateMapValueRequest): MapValue = transaction {
        val id = MapValues.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[valueId1] = request.valueId1
            it[valueId2] = request.valueId2
            it[variantId] = request.variantId
            it[variantModificationId] = request.variantModificationId
            it[isDark] = request.isDark
        }

        findMapValueById(id.value, tenantId)!!
    }

    fun updateMapValue(id: Long, tenantId: Long, request: CreateMapValueRequest): MapValue? = transaction {
        val updated = MapValues.update(
            where = { (MapValues.id eq id) and (MapValues.tenantId eq tenantId) }
        ) {
            request.valueId1?.let { v1 -> it[valueId1] = v1 }
            request.valueId2?.let { v2 -> it[valueId2] = v2 }
            request.variantId?.let { variant -> it[variantId] = variant }
            request.variantModificationId?.let { varMod -> it[variantModificationId] = varMod }
            it[isDark] = request.isDark
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findMapValueById(id, tenantId) else null
    }

    fun deleteMapValue(id: Long, tenantId: Long): Boolean = transaction {
        MapValues.deleteWhere {
            (MapValues.id eq id) and (MapValues.tenantId eq tenantId)
        } > 0
    }

    // =====================================================
    // MAP LISTINGS - Design Mapping
    // =====================================================

    fun findMapListings(tenantId: Long, userId: Long? = null): List<MapListing> = transaction {
        var query = MapListings.selectAll()
            .where { MapListings.tenantId eq tenantId }

        userId?.let {
            query = query.andWhere { MapListings.userId eq it }
        }

        query.map { it.toMapListing() }
    }

    fun findMapListingById(id: Long, tenantId: Long): MapListing? = transaction {
        MapListings.selectAll()
            .where { (MapListings.id eq id) and (MapListings.tenantId eq tenantId) }
            .singleOrNull()
            ?.toMapListing()
    }

    fun findMapListingByListingId(
        listingId: String,
        tenantId: Long,
        userId: Long? = null,
        modificationId: Long? = null
    ): MapListing? = transaction {
        var query = MapListings.selectAll()
            .where { (MapListings.listingId eq listingId) and (MapListings.tenantId eq tenantId) }

        userId?.let {
            query = query.andWhere { MapListings.userId eq it }
        }

        modificationId?.let {
            query = query.andWhere { MapListings.modificationId eq it }
        }

        query.singleOrNull()?.toMapListing()
    }

    fun findMapListingsByListingId(
        listingId: String,
        tenantId: Long,
        userId: Long? = null
    ): List<MapListing> = transaction {
        var query = MapListings.selectAll()
            .where { (MapListings.listingId eq listingId) and (MapListings.tenantId eq tenantId) }

        userId?.let {
            query = query.andWhere { MapListings.userId eq it }
        }

        query.map { it.toMapListing() }
    }

    fun createMapListing(tenantId: Long, userId: Long?, request: CreateMapListingRequest): MapListing = transaction {
        val id = MapListings.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.userId] = userId
            it[this.listingId] = request.listingId
            it[modificationId] = request.modificationId
            it[lightDesignId] = request.lightDesignId
            it[darkDesignId] = request.darkDesignId
        }

        findMapListingById(id.value, tenantId)!!
    }

    fun updateMapListing(id: Long, tenantId: Long, request: CreateMapListingRequest): MapListing? = transaction {
        val updated = MapListings.update(
            where = { (MapListings.id eq id) and (MapListings.tenantId eq tenantId) }
        ) {
            it[listingId] = request.listingId
            request.modificationId?.let { mod -> it[modificationId] = mod }
            request.lightDesignId?.let { light -> it[lightDesignId] = light }
            request.darkDesignId?.let { dark -> it[darkDesignId] = dark }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findMapListingById(id, tenantId) else null
    }

    fun deleteMapListing(id: Long, tenantId: Long): Boolean = transaction {
        MapListings.deleteWhere {
            (MapListings.id eq id) and (MapListings.tenantId eq tenantId)
        } > 0
    }

    fun deleteMapListingsByListingId(listingId: String, tenantId: Long): Int = transaction {
        MapListings.deleteWhere {
            (MapListings.listingId eq listingId) and (MapListings.tenantId eq tenantId)
        }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toMapValue(): MapValue = MapValue(
        id = this[MapValues.id].value,
        tenantId = this[MapValues.tenantId].value,
        userId = this[MapValues.userId]?.value,
        valueId1 = this[MapValues.valueId1],
        valueId2 = this[MapValues.valueId2],
        variantId = this[MapValues.variantId]?.value,
        variantModificationId = this[MapValues.variantModificationId]?.value,
        isDark = this[MapValues.isDark],
        createdAt = this[MapValues.createdAt].toString(),
        updatedAt = this[MapValues.updatedAt].toString()
    )

    private fun ResultRow.toMapListing(): MapListing = MapListing(
        id = this[MapListings.id].value,
        tenantId = this[MapListings.tenantId].value,
        userId = this[MapListings.userId]?.value,
        listingId = this[MapListings.listingId],
        modificationId = this[MapListings.modificationId]?.value,
        lightDesignId = this[MapListings.lightDesignId]?.value,
        darkDesignId = this[MapListings.darkDesignId]?.value,
        createdAt = this[MapListings.createdAt].toString(),
        updatedAt = this[MapListings.updatedAt].toString()
    )
}

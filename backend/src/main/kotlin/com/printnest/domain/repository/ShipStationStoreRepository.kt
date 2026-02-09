package com.printnest.domain.repository

import com.printnest.domain.models.ShipStationStore
import com.printnest.domain.tables.ShipStationStores
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class ShipStationStoreRepository {

    fun findById(id: Long): ShipStationStore? = transaction {
        ShipStationStores.selectAll()
            .where { ShipStationStores.id eq id }
            .map { it.toShipStationStore() }
            .singleOrNull()
    }

    fun findByTenantId(tenantId: Long): List<ShipStationStore> = transaction {
        ShipStationStores.selectAll()
            .where { ShipStationStores.tenantId eq tenantId }
            .orderBy(ShipStationStores.storeName, SortOrder.ASC)
            .map { it.toShipStationStore() }
    }

    fun findByTenantIdAndActive(tenantId: Long): List<ShipStationStore> = transaction {
        ShipStationStores.selectAll()
            .where { (ShipStationStores.tenantId eq tenantId) and (ShipStationStores.isActive eq true) }
            .orderBy(ShipStationStores.storeName, SortOrder.ASC)
            .map { it.toShipStationStore() }
    }

    fun findByShipStationStoreId(tenantId: Long, shipstationStoreId: Long): ShipStationStore? = transaction {
        ShipStationStores.selectAll()
            .where {
                (ShipStationStores.tenantId eq tenantId) and
                (ShipStationStores.shipstationStoreId eq shipstationStoreId)
            }
            .map { it.toShipStationStore() }
            .singleOrNull()
    }

    fun findByIds(ids: List<Long>): List<ShipStationStore> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        ShipStationStores.selectAll()
            .where { ShipStationStores.id inList ids }
            .map { it.toShipStationStore() }
    }

    fun create(
        tenantId: Long,
        shipstationStoreId: Long,
        storeName: String,
        marketplaceName: String? = null,
        marketplaceId: Long? = null,
        accountName: String? = null
    ): ShipStationStore = transaction {
        val id = ShipStationStores.insertAndGetId {
            it[ShipStationStores.tenantId] = tenantId
            it[ShipStationStores.shipstationStoreId] = shipstationStoreId
            it[ShipStationStores.storeName] = storeName
            it[ShipStationStores.marketplaceName] = marketplaceName
            it[ShipStationStores.marketplaceId] = marketplaceId
            it[ShipStationStores.accountName] = accountName
            it[isActive] = true
            it[lastSyncAt] = Instant.now()
        }
        findById(id.value)!!
    }

    fun upsert(
        tenantId: Long,
        shipstationStoreId: Long,
        storeName: String,
        marketplaceName: String? = null,
        marketplaceId: Long? = null,
        accountName: String? = null
    ): ShipStationStore = transaction {
        val existing = findByShipStationStoreId(tenantId, shipstationStoreId)
        if (existing != null) {
            ShipStationStores.update({ ShipStationStores.id eq existing.id }) {
                it[ShipStationStores.storeName] = storeName
                it[ShipStationStores.marketplaceName] = marketplaceName
                it[ShipStationStores.marketplaceId] = marketplaceId
                it[ShipStationStores.accountName] = accountName
                it[lastSyncAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            findById(existing.id)!!
        } else {
            create(tenantId, shipstationStoreId, storeName, marketplaceName, marketplaceId, accountName)
        }
    }

    fun updateStatus(id: Long, isActive: Boolean): Boolean = transaction {
        ShipStationStores.update({ ShipStationStores.id eq id }) {
            it[ShipStationStores.isActive] = isActive
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateLastSyncAt(tenantId: Long): Int = transaction {
        ShipStationStores.update({ ShipStationStores.tenantId eq tenantId }) {
            it[lastSyncAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }
    }

    fun delete(id: Long): Boolean = transaction {
        ShipStationStores.deleteWhere { ShipStationStores.id eq id } > 0
    }

    private fun ResultRow.toShipStationStore() = ShipStationStore(
        id = this[ShipStationStores.id].value,
        tenantId = this[ShipStationStores.tenantId].value,
        shipstationStoreId = this[ShipStationStores.shipstationStoreId],
        storeName = this[ShipStationStores.storeName],
        marketplaceName = this[ShipStationStores.marketplaceName],
        marketplaceId = this[ShipStationStores.marketplaceId],
        accountName = this[ShipStationStores.accountName],
        isActive = this[ShipStationStores.isActive],
        lastSyncAt = this[ShipStationStores.lastSyncAt]?.toString(),
        createdAt = this[ShipStationStores.createdAt].toString(),
        updatedAt = this[ShipStationStores.updatedAt].toString()
    )
}

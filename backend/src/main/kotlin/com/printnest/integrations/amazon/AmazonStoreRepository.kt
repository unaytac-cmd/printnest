package com.printnest.integrations.amazon

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Amazon Stores Table
 * Stores Amazon SP-API credentials and configuration per tenant
 */
object AmazonStores : LongIdTable("amazon_stores") {
    val tenantId = long("tenant_id").index()
    val storeId = long("store_id").index()  // Reference to internal Stores table
    val sellerId = varchar("seller_id", 50)
    val marketplaceId = varchar("marketplace_id", 50)
    val refreshToken = text("refresh_token")
    val storeName = varchar("store_name", 255).nullable()
    val isActive = bool("is_active").default(true)
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_amazon_stores_tenant_store", tenantId, storeId)
    }
}

/**
 * Amazon Store Repository
 * Data access for Amazon store credentials and configuration
 */
class AmazonStoreRepository {
    private val logger = LoggerFactory.getLogger(AmazonStoreRepository::class.java)

    /**
     * Find Amazon store by tenant and internal store ID
     */
    fun findByStoreId(tenantId: Long, storeId: Long): AmazonStore? = transaction {
        AmazonStores.selectAll()
            .where { (AmazonStores.tenantId eq tenantId) and (AmazonStores.storeId eq storeId) }
            .map { rowToAmazonStore(it) }
            .firstOrNull()
    }

    /**
     * Find Amazon store by its primary ID
     */
    fun findById(id: Long): AmazonStore? = transaction {
        AmazonStores.selectAll()
            .where { AmazonStores.id eq id }
            .map { rowToAmazonStore(it) }
            .firstOrNull()
    }

    /**
     * Find all Amazon stores for a tenant
     */
    fun findByTenantId(tenantId: Long): List<AmazonStore> = transaction {
        AmazonStores.selectAll()
            .where { AmazonStores.tenantId eq tenantId }
            .orderBy(AmazonStores.createdAt, SortOrder.DESC)
            .map { rowToAmazonStore(it) }
    }

    /**
     * Find all active Amazon stores for a tenant
     */
    fun findActiveByTenantId(tenantId: Long): List<AmazonStore> = transaction {
        AmazonStores.selectAll()
            .where { (AmazonStores.tenantId eq tenantId) and (AmazonStores.isActive eq true) }
            .orderBy(AmazonStores.createdAt, SortOrder.DESC)
            .map { rowToAmazonStore(it) }
    }

    /**
     * Find Amazon store by seller ID
     */
    fun findBySellerId(tenantId: Long, sellerId: String): AmazonStore? = transaction {
        AmazonStores.selectAll()
            .where { (AmazonStores.tenantId eq tenantId) and (AmazonStores.sellerId eq sellerId) }
            .map { rowToAmazonStore(it) }
            .firstOrNull()
    }

    /**
     * Insert or update Amazon store
     * Updates if exists, inserts if new
     */
    fun upsert(
        tenantId: Long,
        storeId: Long,
        sellerId: String,
        marketplaceId: String,
        refreshToken: String,
        storeName: String? = null
    ): Long = transaction {
        val existing = AmazonStores.selectAll()
            .where { (AmazonStores.tenantId eq tenantId) and (AmazonStores.storeId eq storeId) }
            .firstOrNull()

        if (existing != null) {
            // Update existing
            AmazonStores.update({ AmazonStores.id eq existing[AmazonStores.id] }) {
                it[AmazonStores.sellerId] = sellerId
                it[AmazonStores.marketplaceId] = marketplaceId
                it[AmazonStores.refreshToken] = refreshToken
                if (storeName != null) {
                    it[AmazonStores.storeName] = storeName
                }
                it[AmazonStores.isActive] = true
                it[AmazonStores.updatedAt] = Instant.now()
            }
            logger.info("Updated Amazon store for tenant $tenantId, store $storeId")
            existing[AmazonStores.id].value
        } else {
            // Insert new
            val id = AmazonStores.insertAndGetId {
                it[AmazonStores.tenantId] = tenantId
                it[AmazonStores.storeId] = storeId
                it[AmazonStores.sellerId] = sellerId
                it[AmazonStores.marketplaceId] = marketplaceId
                it[AmazonStores.refreshToken] = refreshToken
                it[AmazonStores.storeName] = storeName
                it[isActive] = true
            }.value
            logger.info("Created Amazon store $id for tenant $tenantId, store $storeId")
            id
        }
    }

    /**
     * Update refresh token
     */
    fun updateRefreshToken(id: Long, refreshToken: String): Boolean = transaction {
        val updated = AmazonStores.update({ AmazonStores.id eq id }) {
            it[AmazonStores.refreshToken] = refreshToken
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Update last sync time
     */
    fun updateLastSyncTime(id: Long): Boolean = transaction {
        val updated = AmazonStores.update({ AmazonStores.id eq id }) {
            it[lastSyncAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Activate store
     */
    fun activate(id: Long): Boolean = transaction {
        val updated = AmazonStores.update({ AmazonStores.id eq id }) {
            it[isActive] = true
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Deactivate store (soft delete)
     */
    fun deactivate(id: Long): Boolean = transaction {
        val updated = AmazonStores.update({ AmazonStores.id eq id }) {
            it[isActive] = false
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Delete store (hard delete)
     */
    fun delete(id: Long): Boolean = transaction {
        val deleted = AmazonStores.deleteWhere { AmazonStores.id eq id }
        deleted > 0
    }

    /**
     * Delete all stores for a tenant
     */
    fun deleteByTenantId(tenantId: Long): Int = transaction {
        AmazonStores.deleteWhere { AmazonStores.tenantId eq tenantId }
    }

    /**
     * Convert database row to AmazonStore model
     */
    private fun rowToAmazonStore(row: ResultRow): AmazonStore {
        return AmazonStore(
            id = row[AmazonStores.id].value,
            tenantId = row[AmazonStores.tenantId],
            storeId = row[AmazonStores.storeId],
            sellerId = row[AmazonStores.sellerId],
            marketplaceId = row[AmazonStores.marketplaceId],
            refreshToken = row[AmazonStores.refreshToken],
            storeName = row[AmazonStores.storeName],
            isActive = row[AmazonStores.isActive],
            lastSyncAt = row[AmazonStores.lastSyncAt]?.toString(),
            createdAt = row[AmazonStores.createdAt].toString(),
            updatedAt = row[AmazonStores.updatedAt].toString()
        )
    }
}

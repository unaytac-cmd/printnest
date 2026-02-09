package com.printnest.integrations.etsy

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Database table for Etsy stores
 */
object EtsyStores : LongIdTable("etsy_stores") {
    val tenantId = long("tenant_id")
    val shopId = long("shop_id")
    val shopName = varchar("shop_name", 255)
    val accessToken = text("access_token")
    val refreshToken = text("refresh_token")
    val tokenExpiry = long("token_expiry")
    val userId = long("user_id").nullable()
    val isActive = bool("is_active").default(true)
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_etsy_stores_tenant_shop", tenantId, shopId)
    }
}

/**
 * Repository for Etsy store operations
 */
class EtsyStoreRepository {
    private val logger = LoggerFactory.getLogger(EtsyStoreRepository::class.java)

    /**
     * Create or update an Etsy store
     */
    fun upsert(store: EtsyStore): EtsyStore {
        return transaction {
            // Check if store exists
            val existing = EtsyStores.selectAll().where {
                (EtsyStores.tenantId eq store.tenantId) and (EtsyStores.shopId eq store.shopId)
            }.firstOrNull()

            val now = Instant.now()

            if (existing != null) {
                // Update existing store
                EtsyStores.update({
                    (EtsyStores.tenantId eq store.tenantId) and (EtsyStores.shopId eq store.shopId)
                }) {
                    it[shopName] = store.shopName
                    it[accessToken] = store.accessToken
                    it[refreshToken] = store.refreshToken
                    it[tokenExpiry] = store.tokenExpiry
                    it[userId] = store.userId
                    it[isActive] = store.isActive
                    it[updatedAt] = now
                }

                store.copy(
                    id = existing[EtsyStores.id].value,
                    updatedAt = now.toString()
                )
            } else {
                // Insert new store
                val id = EtsyStores.insertAndGetId {
                    it[tenantId] = store.tenantId
                    it[shopId] = store.shopId
                    it[shopName] = store.shopName
                    it[accessToken] = store.accessToken
                    it[refreshToken] = store.refreshToken
                    it[tokenExpiry] = store.tokenExpiry
                    it[userId] = store.userId
                    it[isActive] = store.isActive
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                store.copy(
                    id = id.value,
                    createdAt = now.toString(),
                    updatedAt = now.toString()
                )
            }
        }
    }

    /**
     * Find store by ID
     */
    fun findById(storeId: Long): EtsyStore? {
        return transaction {
            EtsyStores.selectAll().where { EtsyStores.id eq storeId }
                .firstOrNull()
                ?.toEtsyStore()
        }
    }

    /**
     * Find stores by tenant ID
     */
    fun findByTenantId(tenantId: Long): List<EtsyStore> {
        return transaction {
            EtsyStores.selectAll().where {
                (EtsyStores.tenantId eq tenantId) and (EtsyStores.isActive eq true)
            }
                .orderBy(EtsyStores.shopName to SortOrder.ASC)
                .map { it.toEtsyStore() }
        }
    }

    /**
     * Find store by tenant and shop ID
     */
    fun findByTenantAndShopId(tenantId: Long, shopId: Long): EtsyStore? {
        return transaction {
            EtsyStores.selectAll().where {
                (EtsyStores.tenantId eq tenantId) and (EtsyStores.shopId eq shopId)
            }
                .firstOrNull()
                ?.toEtsyStore()
        }
    }

    /**
     * Update store tokens
     */
    fun updateTokens(
        storeId: Long,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ): Boolean {
        return transaction {
            EtsyStores.update({ EtsyStores.id eq storeId }) {
                it[EtsyStores.accessToken] = accessToken
                it[EtsyStores.refreshToken] = refreshToken
                it[EtsyStores.tokenExpiry] = tokenExpiry
                it[updatedAt] = Instant.now()
            } > 0
        }
    }

    /**
     * Update last sync timestamp
     */
    fun updateLastSyncAt(storeId: Long): Boolean {
        return transaction {
            EtsyStores.update({ EtsyStores.id eq storeId }) {
                it[lastSyncAt] = Instant.now()
                it[updatedAt] = Instant.now()
            } > 0
        }
    }

    /**
     * Deactivate a store (soft delete)
     */
    fun deactivate(storeId: Long): Boolean {
        return transaction {
            EtsyStores.update({ EtsyStores.id eq storeId }) {
                it[isActive] = false
                it[updatedAt] = Instant.now()
            } > 0
        }
    }

    /**
     * Activate a store
     */
    fun activate(storeId: Long): Boolean {
        return transaction {
            EtsyStores.update({ EtsyStores.id eq storeId }) {
                it[isActive] = true
                it[updatedAt] = Instant.now()
            } > 0
        }
    }

    /**
     * Delete a store permanently
     */
    fun delete(storeId: Long): Boolean {
        return transaction {
            EtsyStores.deleteWhere { EtsyStores.id eq storeId } > 0
        }
    }

    /**
     * Get all stores that need token refresh (expiring within buffer time)
     */
    fun findStoresNeedingRefresh(bufferSeconds: Long = 300): List<EtsyStore> {
        val threshold = System.currentTimeMillis() / 1000 + bufferSeconds
        return transaction {
            EtsyStores.selectAll().where {
                (EtsyStores.isActive eq true) and (EtsyStores.tokenExpiry lessEq threshold)
            }.map { it.toEtsyStore() }
        }
    }

    /**
     * Convert database row to EtsyStore
     */
    private fun ResultRow.toEtsyStore(): EtsyStore {
        return EtsyStore(
            id = this[EtsyStores.id].value,
            tenantId = this[EtsyStores.tenantId],
            shopId = this[EtsyStores.shopId],
            shopName = this[EtsyStores.shopName],
            accessToken = this[EtsyStores.accessToken],
            refreshToken = this[EtsyStores.refreshToken],
            tokenExpiry = this[EtsyStores.tokenExpiry],
            userId = this[EtsyStores.userId],
            isActive = this[EtsyStores.isActive],
            lastSyncAt = this[EtsyStores.lastSyncAt]?.toString(),
            createdAt = this[EtsyStores.createdAt].toString(),
            updatedAt = this[EtsyStores.updatedAt].toString()
        )
    }
}

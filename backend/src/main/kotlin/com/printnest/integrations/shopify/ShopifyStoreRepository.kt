package com.printnest.integrations.shopify

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Repository for Shopify Store database operations
 */
class ShopifyStoreRepository {
    private val logger = LoggerFactory.getLogger(ShopifyStoreRepository::class.java)

    /**
     * Create a new Shopify store
     */
    fun create(
        tenantId: Long,
        shopUrl: String,
        accessToken: String?,
        scope: String?
    ): ShopifyStore {
        return transaction {
            val now = Instant.now().toString()

            val id = ShopifyStoresTable.insert {
                it[ShopifyStoresTable.tenantId] = tenantId
                it[ShopifyStoresTable.shopUrl] = shopUrl
                it[ShopifyStoresTable.accessToken] = accessToken
                it[ShopifyStoresTable.scope] = scope
                it[ShopifyStoresTable.isActive] = true
                it[ShopifyStoresTable.createdAt] = now
                it[ShopifyStoresTable.updatedAt] = now
            } get ShopifyStoresTable.id

            ShopifyStore(
                id = id,
                tenantId = tenantId,
                shopUrl = shopUrl,
                accessToken = accessToken,
                scope = scope,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    /**
     * Update an existing store's token and scope
     */
    fun update(
        storeId: Long,
        accessToken: String?,
        scope: String?
    ): ShopifyStore {
        return transaction {
            val now = Instant.now().toString()

            ShopifyStoresTable.update({ ShopifyStoresTable.id eq storeId }) {
                if (accessToken != null) it[ShopifyStoresTable.accessToken] = accessToken
                if (scope != null) it[ShopifyStoresTable.scope] = scope
                it[ShopifyStoresTable.isActive] = true
                it[updatedAt] = now
            }

            findById(storeId)!!
        }
    }

    /**
     * Find store by ID
     */
    fun findById(storeId: Long): ShopifyStore? {
        return transaction {
            ShopifyStoresTable.selectAll()
                .where { ShopifyStoresTable.id eq storeId }
                .map { it.toShopifyStore() }
                .singleOrNull()
        }
    }

    /**
     * Find store by shop URL
     */
    fun findByShopUrl(shopUrl: String): ShopifyStore? {
        return transaction {
            ShopifyStoresTable.selectAll()
                .where { ShopifyStoresTable.shopUrl eq shopUrl }
                .map { it.toShopifyStore() }
                .singleOrNull()
        }
    }

    /**
     * Find all stores for a tenant
     */
    fun findByTenantId(tenantId: Long): List<ShopifyStore> {
        return transaction {
            ShopifyStoresTable.selectAll()
                .where { ShopifyStoresTable.tenantId eq tenantId }
                .orderBy(ShopifyStoresTable.createdAt, SortOrder.DESC)
                .map { it.toShopifyStore() }
        }
    }

    /**
     * Find active stores for a tenant
     */
    fun findByTenantIdAndActive(tenantId: Long): List<ShopifyStore> {
        return transaction {
            ShopifyStoresTable.selectAll()
                .where {
                    (ShopifyStoresTable.tenantId eq tenantId) and
                    (ShopifyStoresTable.isActive eq true)
                }
                .orderBy(ShopifyStoresTable.createdAt, SortOrder.DESC)
                .map { it.toShopifyStore() }
        }
    }

    /**
     * Update shop owner email
     */
    fun updateShopOwnerEmail(storeId: Long, email: String) {
        transaction {
            ShopifyStoresTable.update({ ShopifyStoresTable.id eq storeId }) {
                it[shopOwnerEmail] = email
                it[updatedAt] = Instant.now().toString()
            }
        }
    }

    /**
     * Update last sync timestamp
     */
    fun updateLastSyncAt(storeId: Long) {
        transaction {
            val now = Instant.now().toString()
            ShopifyStoresTable.update({ ShopifyStoresTable.id eq storeId }) {
                it[lastSyncAt] = now
                it[updatedAt] = now
            }
        }
    }

    /**
     * Deactivate a store (disconnect)
     */
    fun deactivate(storeId: Long): Boolean {
        return transaction {
            val updated = ShopifyStoresTable.update({ ShopifyStoresTable.id eq storeId }) {
                it[isActive] = false
                it[accessToken] = null
                it[updatedAt] = Instant.now().toString()
            }
            updated > 0
        }
    }

    /**
     * Delete a store permanently
     */
    fun delete(storeId: Long): Boolean {
        return transaction {
            val deleted = ShopifyStoresTable.deleteWhere { id eq storeId }
            deleted > 0
        }
    }

    /**
     * Check if store exists for tenant
     */
    fun existsByShopUrlAndTenant(shopUrl: String, tenantId: Long): Boolean {
        return transaction {
            ShopifyStoresTable.selectAll()
                .where {
                    (ShopifyStoresTable.shopUrl eq shopUrl) and
                    (ShopifyStoresTable.tenantId eq tenantId)
                }
                .count() > 0
        }
    }

    private fun ResultRow.toShopifyStore(): ShopifyStore {
        return ShopifyStore(
            id = this[ShopifyStoresTable.id],
            tenantId = this[ShopifyStoresTable.tenantId],
            shopUrl = this[ShopifyStoresTable.shopUrl],
            accessToken = this[ShopifyStoresTable.accessToken],
            scope = this[ShopifyStoresTable.scope],
            shopOwnerEmail = this[ShopifyStoresTable.shopOwnerEmail],
            isActive = this[ShopifyStoresTable.isActive],
            lastSyncAt = this[ShopifyStoresTable.lastSyncAt],
            createdAt = this[ShopifyStoresTable.createdAt],
            updatedAt = this[ShopifyStoresTable.updatedAt]
        )
    }
}

/**
 * Database table definition for Shopify stores
 */
object ShopifyStoresTable : Table("shopify_stores") {
    val id = long("id").autoIncrement()
    val tenantId = long("tenant_id")
    val shopUrl = varchar("shop_url", 255)
    val accessToken = varchar("access_token", 512).nullable()
    val scope = text("scope").nullable()
    val shopOwnerEmail = varchar("shop_owner_email", 255).nullable()
    val isActive = bool("is_active").default(true)
    val lastSyncAt = varchar("last_sync_at", 50).nullable()
    val createdAt = varchar("created_at", 50)
    val updatedAt = varchar("updated_at", 50)

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, shopUrl, tenantId)  // Unique index on shop_url per tenant
        index(false, tenantId)
    }
}

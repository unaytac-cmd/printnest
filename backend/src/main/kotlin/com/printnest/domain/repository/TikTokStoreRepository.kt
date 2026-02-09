package com.printnest.domain.repository

import com.printnest.domain.tables.Orders
import com.printnest.domain.tables.OrderProducts
import com.printnest.domain.tables.TikTokStores
import com.printnest.domain.tables.Tenants
import com.printnest.domain.tables.Users
import com.printnest.integrations.tiktok.TikTokStore
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Repository for TikTok Store operations
 */
class TikTokStoreRepository {
    private val logger = LoggerFactory.getLogger(TikTokStoreRepository::class.java)

    /**
     * Find all TikTok stores for a tenant
     */
    fun findByTenantId(tenantId: Long): List<TikTokStore> = transaction {
        TikTokStores.selectAll()
            .where { TikTokStores.tenantId eq tenantId }
            .map { it.toTikTokStore() }
    }

    /**
     * Find active TikTok stores for a tenant
     */
    fun findActiveByTenantId(tenantId: Long): List<TikTokStore> = transaction {
        TikTokStores.selectAll()
            .where { (TikTokStores.tenantId eq tenantId) and (TikTokStores.isActive eq true) }
            .map { it.toTikTokStore() }
    }

    /**
     * Find a TikTok store by tenant ID and store ID
     */
    fun findByStoreId(tenantId: Long, storeId: String): TikTokStore? = transaction {
        TikTokStores.selectAll()
            .where { (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId) }
            .map { it.toTikTokStore() }
            .singleOrNull()
    }

    /**
     * Find a TikTok store by ID
     */
    fun findById(id: Long): TikTokStore? = transaction {
        TikTokStores.selectAll()
            .where { TikTokStores.id eq id }
            .map { it.toTikTokStore() }
            .singleOrNull()
    }

    /**
     * Upsert a TikTok store
     */
    fun upsert(
        tenantId: Long,
        userId: Long,
        storeId: String,
        shopId: String?,
        shopName: String?,
        shopCipher: String?,
        accessToken: String?,
        refreshToken: String?,
        accessTokenExpireAt: Long?,
        refreshTokenExpireAt: Long?,
        region: String?
    ): TikTokStore = transaction {
        val now = Instant.now()

        val existingStore = TikTokStores.selectAll()
            .where { (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId) }
            .singleOrNull()

        if (existingStore != null) {
            // Update existing store
            TikTokStores.update({ (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId) }) {
                it[TikTokStores.shopId] = shopId
                it[TikTokStores.shopName] = shopName
                it[TikTokStores.shopCipher] = shopCipher
                it[TikTokStores.accessToken] = accessToken
                it[TikTokStores.refreshToken] = refreshToken
                it[TikTokStores.accessTokenExpireAt] = accessTokenExpireAt
                it[TikTokStores.refreshTokenExpireAt] = refreshTokenExpireAt
                it[TikTokStores.region] = region
                it[TikTokStores.isActive] = true
                it[updatedAt] = now
            }

            findByStoreId(tenantId, storeId)!!
        } else {
            // Insert new store
            val id = TikTokStores.insertAndGetId {
                it[TikTokStores.tenantId] = tenantId
                it[TikTokStores.userId] = userId
                it[TikTokStores.storeId] = storeId
                it[TikTokStores.shopId] = shopId
                it[TikTokStores.shopName] = shopName
                it[TikTokStores.shopCipher] = shopCipher
                it[TikTokStores.accessToken] = accessToken
                it[TikTokStores.refreshToken] = refreshToken
                it[TikTokStores.accessTokenExpireAt] = accessTokenExpireAt
                it[TikTokStores.refreshTokenExpireAt] = refreshTokenExpireAt
                it[TikTokStores.region] = region
                it[isActive] = true
                it[createdAt] = now
                it[updatedAt] = now
            }

            findById(id.value)!!
        }
    }

    /**
     * Update tokens for a store
     */
    fun updateTokens(
        tenantId: Long,
        storeId: String,
        accessToken: String,
        refreshToken: String,
        accessTokenExpireAt: Long,
        refreshTokenExpireAt: Long
    ): Boolean = transaction {
        val updated = TikTokStores.update({
            (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId)
        }) {
            it[TikTokStores.accessToken] = accessToken
            it[TikTokStores.refreshToken] = refreshToken
            it[TikTokStores.accessTokenExpireAt] = accessTokenExpireAt
            it[TikTokStores.refreshTokenExpireAt] = refreshTokenExpireAt
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Update last sync time for a store
     */
    fun updateLastSyncAt(tenantId: Long, storeId: String): Boolean = transaction {
        val updated = TikTokStores.update({
            (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId)
        }) {
            it[lastSyncAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Disconnect a store (clear tokens)
     */
    fun disconnect(tenantId: Long, storeId: String): Boolean = transaction {
        val updated = TikTokStores.update({
            (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId)
        }) {
            it[shopCipher] = null
            it[accessToken] = null
            it[refreshToken] = null
            it[accessTokenExpireAt] = null
            it[refreshTokenExpireAt] = null
            it[isActive] = false
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    /**
     * Delete a store
     */
    fun delete(tenantId: Long, storeId: String): Boolean = transaction {
        val deleted = TikTokStores.deleteWhere {
            (TikTokStores.tenantId eq tenantId) and (TikTokStores.storeId eq storeId)
        }
        deleted > 0
    }

    /**
     * Check if an order already exists
     */
    fun orderExists(tenantId: Long, intOrderId: String): Boolean = transaction {
        Orders.selectAll()
            .where { (Orders.tenantId eq tenantId) and (Orders.intOrderId eq intOrderId) }
            .count() > 0
    }

    /**
     * Insert an order from TikTok
     */
    fun insertOrder(
        tenantId: Long,
        userId: Long,
        storeId: Long,
        intOrderId: String,
        orderType: Int,
        orderStatus: Int,
        orderInfo: String,
        orderDetail: String,
        orderMapStatus: Int
    ): Long = transaction {
        val now = Instant.now()

        Orders.insertAndGetId {
            it[Orders.tenantId] = tenantId
            it[Orders.userId] = userId
            it[Orders.storeId] = storeId
            it[Orders.intOrderId] = intOrderId
            it[Orders.orderType] = orderType
            it[Orders.orderStatus] = orderStatus
            it[Orders.orderInfo] = orderInfo
            it[Orders.orderDetail] = orderDetail
            it[Orders.orderMapStatus] = orderMapStatus
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    /**
     * Insert an order product from TikTok
     */
    fun insertOrderProduct(
        tenantId: Long,
        orderId: String,
        productDetail: String
    ): Long = transaction {
        // Get the order ID from intOrderId
        val order = Orders.selectAll()
            .where { Orders.intOrderId eq orderId }
            .singleOrNull() ?: throw IllegalArgumentException("Order not found: $orderId")

        val orderDbId = order[Orders.id].value
        val now = Instant.now()

        OrderProducts.insertAndGetId {
            it[OrderProducts.tenantId] = tenantId
            it[OrderProducts.orderId] = orderDbId
            it[OrderProducts.productDetail] = productDetail
            it[quantity] = 1
            it[status] = 0
            it[createdAt] = now
        }.value
    }

    private fun ResultRow.toTikTokStore(): TikTokStore {
        return TikTokStore(
            id = this[TikTokStores.id].value,
            tenantId = this[TikTokStores.tenantId].value,
            userId = this[TikTokStores.userId].value,
            storeId = this[TikTokStores.storeId],
            shopId = this[TikTokStores.shopId],
            shopName = this[TikTokStores.shopName],
            shopCipher = this[TikTokStores.shopCipher],
            accessToken = this[TikTokStores.accessToken],
            refreshToken = this[TikTokStores.refreshToken],
            accessTokenExpireAt = this[TikTokStores.accessTokenExpireAt],
            refreshTokenExpireAt = this[TikTokStores.refreshTokenExpireAt],
            region = this[TikTokStores.region],
            isActive = this[TikTokStores.isActive],
            lastSyncAt = this[TikTokStores.lastSyncAt]?.toString(),
            createdAt = this[TikTokStores.createdAt].toString(),
            updatedAt = this[TikTokStores.updatedAt].toString()
        )
    }
}

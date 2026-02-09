package com.printnest.domain.service

import com.printnest.integrations.redis.RedisService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * High-level caching service for application-specific caching needs.
 * Provides semantic methods for caching tokens, user data, and business entities.
 */
class CacheService(
    private val redisService: RedisService
) {
    private val logger = LoggerFactory.getLogger(CacheService::class.java)

    companion object {
        // Key prefixes for different cache types
        private const val PREFIX_AUTH_TOKEN = "auth:token"
        private const val PREFIX_REFRESH_TOKEN = "auth:refresh"
        private const val PREFIX_MARKETPLACE_TOKEN = "marketplace:token"
        private const val PREFIX_USER = "user"
        private const val PREFIX_TENANT = "tenant"
        private const val PREFIX_ORDER = "order"
        private const val PREFIX_SESSION = "session"
        private const val PREFIX_RATE_LIMIT = "ratelimit"

        // Default TTLs (in seconds)
        private const val DEFAULT_TOKEN_TTL = 900L // 15 minutes
        private const val DEFAULT_REFRESH_TOKEN_TTL = 2592000L // 30 days
        private const val DEFAULT_MARKETPLACE_TOKEN_TTL = 3600L // 1 hour
        private const val DEFAULT_USER_CACHE_TTL = 300L // 5 minutes
        private const val DEFAULT_ORDER_COUNT_TTL = 60L // 1 minute
    }

    // =====================================================
    // AUTH TOKEN CACHING
    // =====================================================

    /**
     * Cache an authentication token for a user
     */
    fun cacheToken(userId: Long, token: String, ttlSeconds: Long = DEFAULT_TOKEN_TTL): Boolean {
        val key = "$PREFIX_AUTH_TOKEN:$userId"
        logger.debug("Caching auth token for user: $userId")
        return redisService.set(key, token, ttlSeconds)
    }

    /**
     * Get cached authentication token for a user
     */
    fun getToken(userId: Long): String? {
        val key = "$PREFIX_AUTH_TOKEN:$userId"
        return redisService.get(key)
    }

    /**
     * Invalidate (remove) authentication token for a user
     */
    fun invalidateToken(userId: Long): Boolean {
        val key = "$PREFIX_AUTH_TOKEN:$userId"
        logger.debug("Invalidating auth token for user: $userId")
        return redisService.delete(key)
    }

    /**
     * Cache a refresh token for a user
     */
    fun cacheRefreshToken(userId: Long, token: String, ttlSeconds: Long = DEFAULT_REFRESH_TOKEN_TTL): Boolean {
        val key = "$PREFIX_REFRESH_TOKEN:$userId"
        logger.debug("Caching refresh token for user: $userId")
        return redisService.set(key, token, ttlSeconds)
    }

    /**
     * Get cached refresh token for a user
     */
    fun getRefreshToken(userId: Long): String? {
        val key = "$PREFIX_REFRESH_TOKEN:$userId"
        return redisService.get(key)
    }

    /**
     * Invalidate refresh token for a user
     */
    fun invalidateRefreshToken(userId: Long): Boolean {
        val key = "$PREFIX_REFRESH_TOKEN:$userId"
        logger.debug("Invalidating refresh token for user: $userId")
        return redisService.delete(key)
    }

    /**
     * Invalidate all tokens (access + refresh) for a user
     */
    fun invalidateAllTokens(userId: Long): Boolean {
        val accessKey = "$PREFIX_AUTH_TOKEN:$userId"
        val refreshKey = "$PREFIX_REFRESH_TOKEN:$userId"
        logger.debug("Invalidating all tokens for user: $userId")
        return redisService.delete(accessKey, refreshKey) > 0
    }

    // =====================================================
    // MARKETPLACE TOKEN CACHING (OAuth tokens)
    // =====================================================

    /**
     * Cache a marketplace OAuth token for a store
     */
    fun cacheMarketplaceToken(
        storeId: Long,
        token: MarketplaceTokenCache,
        ttlSeconds: Long = DEFAULT_MARKETPLACE_TOKEN_TTL
    ): Boolean {
        val key = "$PREFIX_MARKETPLACE_TOKEN:$storeId"
        logger.debug("Caching marketplace token for store: $storeId")
        return redisService.setJson(key, token, MarketplaceTokenCache.serializer(), ttlSeconds)
    }

    /**
     * Get cached marketplace OAuth token for a store
     */
    fun getMarketplaceToken(storeId: Long): MarketplaceTokenCache? {
        val key = "$PREFIX_MARKETPLACE_TOKEN:$storeId"
        return redisService.getJson(key, MarketplaceTokenCache.serializer())
    }

    /**
     * Invalidate marketplace token for a store
     */
    fun invalidateMarketplaceToken(storeId: Long): Boolean {
        val key = "$PREFIX_MARKETPLACE_TOKEN:$storeId"
        logger.debug("Invalidating marketplace token for store: $storeId")
        return redisService.delete(key)
    }

    // =====================================================
    // TENANT STATISTICS CACHING
    // =====================================================

    /**
     * Cache order count for a tenant
     */
    fun cacheOrderCount(tenantId: Long, count: Long, ttlSeconds: Long = DEFAULT_ORDER_COUNT_TTL): Boolean {
        val key = "$PREFIX_TENANT:$tenantId:order_count"
        logger.debug("Caching order count for tenant: $tenantId, count: $count")
        return redisService.set(key, count.toString(), ttlSeconds)
    }

    /**
     * Get cached order count for a tenant
     */
    fun getOrderCount(tenantId: Long): Long? {
        val key = "$PREFIX_TENANT:$tenantId:order_count"
        return redisService.get(key)?.toLongOrNull()
    }

    /**
     * Increment order count for a tenant
     */
    fun incrementOrderCount(tenantId: Long): Long {
        val key = "$PREFIX_TENANT:$tenantId:order_count"
        return redisService.increment(key)
    }

    /**
     * Cache tenant statistics
     */
    fun cacheTenantStats(tenantId: Long, stats: TenantStatsCache, ttlSeconds: Long = DEFAULT_USER_CACHE_TTL): Boolean {
        val key = "$PREFIX_TENANT:$tenantId:stats"
        return redisService.setJson(key, stats, TenantStatsCache.serializer(), ttlSeconds)
    }

    /**
     * Get cached tenant statistics
     */
    fun getTenantStats(tenantId: Long): TenantStatsCache? {
        val key = "$PREFIX_TENANT:$tenantId:stats"
        return redisService.getJson(key, TenantStatsCache.serializer())
    }

    // =====================================================
    // USER CACHING
    // =====================================================

    /**
     * Cache user balance
     */
    fun cacheUserBalance(userId: Long, balance: BigDecimal, ttlSeconds: Long = DEFAULT_USER_CACHE_TTL): Boolean {
        val key = "$PREFIX_USER:$userId:balance"
        logger.debug("Caching balance for user: $userId, balance: $balance")
        return redisService.set(key, balance.toPlainString(), ttlSeconds)
    }

    /**
     * Get cached user balance
     */
    fun getUserBalance(userId: Long): BigDecimal? {
        val key = "$PREFIX_USER:$userId:balance"
        return redisService.get(key)?.let { BigDecimal(it) }
    }

    /**
     * Cache user profile data
     */
    fun cacheUserProfile(userId: Long, profile: UserProfileCache, ttlSeconds: Long = DEFAULT_USER_CACHE_TTL): Boolean {
        val key = "$PREFIX_USER:$userId:profile"
        return redisService.setJson(key, profile, UserProfileCache.serializer(), ttlSeconds)
    }

    /**
     * Get cached user profile
     */
    fun getUserProfile(userId: Long): UserProfileCache? {
        val key = "$PREFIX_USER:$userId:profile"
        return redisService.getJson(key, UserProfileCache.serializer())
    }

    /**
     * Invalidate all user cache (profile, balance, tokens, etc.)
     */
    fun invalidateUserCache(userId: Long): Boolean {
        val keys = listOf(
            "$PREFIX_USER:$userId:profile",
            "$PREFIX_USER:$userId:balance",
            "$PREFIX_AUTH_TOKEN:$userId",
            "$PREFIX_REFRESH_TOKEN:$userId"
        )
        logger.debug("Invalidating all cache for user: $userId")
        return redisService.delete(*keys.toTypedArray()) > 0
    }

    // =====================================================
    // SESSION MANAGEMENT
    // =====================================================

    /**
     * Cache session data
     */
    fun cacheSession(sessionId: String, data: SessionCache, ttlSeconds: Long): Boolean {
        val key = "$PREFIX_SESSION:$sessionId"
        return redisService.setJson(key, data, SessionCache.serializer(), ttlSeconds)
    }

    /**
     * Get session data
     */
    fun getSession(sessionId: String): SessionCache? {
        val key = "$PREFIX_SESSION:$sessionId"
        return redisService.getJson(key, SessionCache.serializer())
    }

    /**
     * Invalidate session
     */
    fun invalidateSession(sessionId: String): Boolean {
        val key = "$PREFIX_SESSION:$sessionId"
        return redisService.delete(key)
    }

    /**
     * Extend session TTL
     */
    fun extendSession(sessionId: String, ttlSeconds: Long): Boolean {
        val key = "$PREFIX_SESSION:$sessionId"
        return redisService.expire(key, ttlSeconds)
    }

    // =====================================================
    // RATE LIMITING
    // =====================================================

    /**
     * Check and increment rate limit counter
     * Returns current count after increment
     */
    fun incrementRateLimit(identifier: String, windowSeconds: Long): Long {
        val key = "$PREFIX_RATE_LIMIT:$identifier"
        val count = redisService.increment(key)

        // Set expiration only on first request in the window
        if (count == 1L) {
            redisService.expire(key, windowSeconds)
        }

        return count
    }

    /**
     * Get current rate limit count
     */
    fun getRateLimitCount(identifier: String): Long {
        val key = "$PREFIX_RATE_LIMIT:$identifier"
        return redisService.get(key)?.toLongOrNull() ?: 0
    }

    /**
     * Check if rate limit is exceeded
     */
    fun isRateLimited(identifier: String, maxRequests: Long): Boolean {
        val count = getRateLimitCount(identifier)
        return count >= maxRequests
    }

    // =====================================================
    // DISTRIBUTED LOCKING
    // =====================================================

    /**
     * Acquire a distributed lock
     * Returns true if lock was acquired, false otherwise
     */
    fun acquireLock(lockName: String, lockValue: String, ttlSeconds: Long): Boolean {
        val key = "lock:$lockName"
        return redisService.setNx(key, lockValue, ttlSeconds)
    }

    /**
     * Release a distributed lock
     * Only releases if the lock value matches (to prevent releasing someone else's lock)
     */
    fun releaseLock(lockName: String, lockValue: String): Boolean {
        val key = "lock:$lockName"
        val currentValue = redisService.get(key)

        return if (currentValue == lockValue) {
            redisService.delete(key)
        } else {
            false
        }
    }

    // =====================================================
    // HEALTH CHECK
    // =====================================================

    // =====================================================
    // GENERIC STRING CACHE OPERATIONS
    // =====================================================

    /**
     * Set a generic string value with optional TTL
     */
    fun set(key: String, value: String, ttlSeconds: Long? = null): Boolean {
        return redisService.set(key, value, ttlSeconds)
    }

    /**
     * Get a generic string value
     */
    fun get(key: String): String? {
        return redisService.get(key)
    }

    /**
     * Delete a cache key
     */
    fun delete(key: String): Boolean {
        return redisService.delete(key)
    }

    /**
     * Check if cache service is healthy
     */
    fun isHealthy(): Boolean {
        return redisService.isHealthy()
    }
}

// =====================================================
// CACHE DATA CLASSES
// =====================================================

@Serializable
data class MarketplaceTokenCache(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAt: Long? = null,
    val scope: String? = null
)

@Serializable
data class TenantStatsCache(
    val totalOrders: Long,
    val pendingOrders: Long,
    val completedOrders: Long,
    val totalRevenue: String, // Store as string to preserve decimal precision
    val activeUsers: Int
)

@Serializable
data class UserProfileCache(
    val id: Long,
    val email: String,
    val fullName: String,
    val role: String,
    val tenantId: Long?,
    val isActive: Boolean
)

@Serializable
data class SessionCache(
    val userId: Long,
    val tenantId: Long?,
    val role: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: Long,
    val lastAccessedAt: Long
)

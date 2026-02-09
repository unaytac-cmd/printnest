package com.printnest.integrations.redis

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.SetParams

/**
 * Redis service providing low-level Redis operations with connection pooling.
 * Uses Jedis client for Redis communication.
 */
class RedisService(
    host: String,
    port: Int,
    password: String?,
    database: Int = 0,
    ssl: Boolean = false,
    poolConfig: RedisPoolConfig = RedisPoolConfig()
) {
    private val logger = LoggerFactory.getLogger(RedisService::class.java)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jedisPool: JedisPool

    init {
        val config = JedisPoolConfig().apply {
            maxTotal = poolConfig.maxTotal
            maxIdle = poolConfig.maxIdle
            minIdle = poolConfig.minIdle
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            blockWhenExhausted = true
        }

        jedisPool = if (password.isNullOrBlank()) {
            JedisPool(config, host, port, 2000, null, database, ssl)
        } else {
            JedisPool(config, host, port, 2000, password, database, ssl)
        }

        logger.info("Redis connection pool initialized - host: $host, port: $port, database: $database, ssl: $ssl")
    }

    // =====================================================
    // BASIC OPERATIONS
    // =====================================================

    /**
     * Set a key-value pair with optional TTL in seconds
     */
    fun set(key: String, value: String, ttlSeconds: Long? = null): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                if (ttlSeconds != null && ttlSeconds > 0) {
                    val params = SetParams().ex(ttlSeconds)
                    jedis.set(key, value, params)
                } else {
                    jedis.set(key, value)
                }
                true
            }
        } catch (e: Exception) {
            logger.error("Error setting key: $key", e)
            false
        }
    }

    /**
     * Get value by key
     */
    fun get(key: String): String? {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.get(key)
            }
        } catch (e: Exception) {
            logger.error("Error getting key: $key", e)
            null
        }
    }

    /**
     * Delete a key
     */
    fun delete(key: String): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.del(key) > 0
            }
        } catch (e: Exception) {
            logger.error("Error deleting key: $key", e)
            false
        }
    }

    /**
     * Delete multiple keys
     */
    fun delete(vararg keys: String): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.del(*keys)
            }
        } catch (e: Exception) {
            logger.error("Error deleting keys: ${keys.joinToString()}", e)
            0
        }
    }

    /**
     * Check if a key exists
     */
    fun exists(key: String): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.exists(key)
            }
        } catch (e: Exception) {
            logger.error("Error checking existence for key: $key", e)
            false
        }
    }

    // =====================================================
    // JSON OPERATIONS
    // =====================================================

    /**
     * Serialize object to JSON and store with optional TTL
     */
    fun <T> setJson(key: String, obj: T, serializer: kotlinx.serialization.SerializationStrategy<T>, ttlSeconds: Long? = null): Boolean {
        return try {
            val jsonValue = json.encodeToString(serializer, obj)
            set(key, jsonValue, ttlSeconds)
        } catch (e: Exception) {
            logger.error("Error serializing and setting JSON for key: $key", e)
            false
        }
    }

    /**
     * Get value and deserialize from JSON
     */
    fun <T> getJson(key: String, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T? {
        return try {
            val value = get(key) ?: return null
            json.decodeFromString(deserializer, value)
        } catch (e: Exception) {
            logger.error("Error getting and deserializing JSON for key: $key", e)
            null
        }
    }

    // =====================================================
    // NUMERIC OPERATIONS
    // =====================================================

    /**
     * Atomically increment a key's value by 1
     */
    fun increment(key: String): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.incr(key)
            }
        } catch (e: Exception) {
            logger.error("Error incrementing key: $key", e)
            0
        }
    }

    /**
     * Atomically increment a key's value by specified amount
     */
    fun incrementBy(key: String, amount: Long): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.incrBy(key, amount)
            }
        } catch (e: Exception) {
            logger.error("Error incrementing key by $amount: $key", e)
            0
        }
    }

    /**
     * Atomically decrement a key's value by 1
     */
    fun decrement(key: String): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.decr(key)
            }
        } catch (e: Exception) {
            logger.error("Error decrementing key: $key", e)
            0
        }
    }

    // =====================================================
    // EXPIRATION OPERATIONS
    // =====================================================

    /**
     * Set expiration time on a key (in seconds)
     */
    fun expire(key: String, seconds: Long): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.expire(key, seconds) == 1L
            }
        } catch (e: Exception) {
            logger.error("Error setting expiration for key: $key", e)
            false
        }
    }

    /**
     * Get remaining TTL for a key (in seconds)
     * Returns -2 if key doesn't exist, -1 if no TTL set
     */
    fun ttl(key: String): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.ttl(key)
            }
        } catch (e: Exception) {
            logger.error("Error getting TTL for key: $key", e)
            -2
        }
    }

    /**
     * Remove expiration from a key (make it persistent)
     */
    fun persist(key: String): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.persist(key) == 1L
            }
        } catch (e: Exception) {
            logger.error("Error persisting key: $key", e)
            false
        }
    }

    // =====================================================
    // KEY SEARCH OPERATIONS
    // =====================================================

    /**
     * Find keys matching a pattern
     * WARNING: KEYS command is O(N) and should be used carefully in production
     */
    fun keys(pattern: String): Set<String> {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.keys(pattern)
            }
        } catch (e: Exception) {
            logger.error("Error finding keys with pattern: $pattern", e)
            emptySet()
        }
    }

    /**
     * Scan keys matching a pattern (more efficient for large datasets)
     * Uses cursor-based iteration
     */
    fun scan(pattern: String, count: Int = 100): Set<String> {
        return try {
            jedisPool.resource.use { jedis ->
                val result = mutableSetOf<String>()
                var cursor = "0"

                do {
                    val scanParams = redis.clients.jedis.params.ScanParams()
                        .match(pattern)
                        .count(count)
                    val scanResult = jedis.scan(cursor, scanParams)
                    result.addAll(scanResult.result)
                    cursor = scanResult.cursor
                } while (cursor != "0")

                result
            }
        } catch (e: Exception) {
            logger.error("Error scanning keys with pattern: $pattern", e)
            emptySet()
        }
    }

    // =====================================================
    // HASH OPERATIONS
    // =====================================================

    /**
     * Set a hash field
     */
    fun hset(key: String, field: String, value: String): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.hset(key, field, value) >= 0
            }
        } catch (e: Exception) {
            logger.error("Error setting hash field: $key.$field", e)
            false
        }
    }

    /**
     * Get a hash field
     */
    fun hget(key: String, field: String): String? {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.hget(key, field)
            }
        } catch (e: Exception) {
            logger.error("Error getting hash field: $key.$field", e)
            null
        }
    }

    /**
     * Get all hash fields and values
     */
    fun hgetAll(key: String): Map<String, String> {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.hgetAll(key)
            }
        } catch (e: Exception) {
            logger.error("Error getting all hash fields: $key", e)
            emptyMap()
        }
    }

    /**
     * Delete hash fields
     */
    fun hdel(key: String, vararg fields: String): Long {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.hdel(key, *fields)
            }
        } catch (e: Exception) {
            logger.error("Error deleting hash fields: $key", e)
            0
        }
    }

    // =====================================================
    // UTILITY OPERATIONS
    // =====================================================

    /**
     * Set a value only if the key doesn't exist (useful for locks)
     */
    fun setNx(key: String, value: String, ttlSeconds: Long? = null): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                if (ttlSeconds != null && ttlSeconds > 0) {
                    val params = SetParams().nx().ex(ttlSeconds)
                    jedis.set(key, value, params) != null
                } else {
                    jedis.setnx(key, value) == 1L
                }
            }
        } catch (e: Exception) {
            logger.error("Error setting key with NX: $key", e)
            false
        }
    }

    /**
     * Get and set a key atomically
     */
    fun getSet(key: String, value: String): String? {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.getSet(key, value)
            }
        } catch (e: Exception) {
            logger.error("Error in getSet for key: $key", e)
            null
        }
    }

    /**
     * Check if Redis connection is healthy
     */
    fun isHealthy(): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.ping() == "PONG"
            }
        } catch (e: Exception) {
            logger.error("Redis health check failed", e)
            false
        }
    }

    /**
     * Flush all keys from the current database
     * WARNING: Use with extreme caution!
     */
    fun flushDb(): Boolean {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.flushDB() == "OK"
            }
        } catch (e: Exception) {
            logger.error("Error flushing database", e)
            false
        }
    }

    /**
     * Close the connection pool
     */
    fun close() {
        try {
            jedisPool.close()
            logger.info("Redis connection pool closed")
        } catch (e: Exception) {
            logger.error("Error closing Redis connection pool", e)
        }
    }
}

/**
 * Redis pool configuration
 */
data class RedisPoolConfig(
    val maxTotal: Int = 50,
    val maxIdle: Int = 10,
    val minIdle: Int = 5
)

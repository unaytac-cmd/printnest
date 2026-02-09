package com.printnest.domain.repository

import com.printnest.domain.models.AuthTenant
import com.printnest.domain.models.AuthUser
import com.printnest.domain.tables.Tenants
import com.printnest.domain.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

class AuthRepository {
    private val logger = LoggerFactory.getLogger(AuthRepository::class.java)

    // =====================================================
    // USER OPERATIONS
    // =====================================================

    fun findUserByEmail(email: String): AuthUser? = transaction {
        Users.selectAll()
            .where { Users.email eq email.lowercase() }
            .map { it.toAuthUser() }
            .firstOrNull()
    }

    fun findUserById(userId: Long): AuthUser? = transaction {
        Users.selectAll()
            .where { Users.id eq userId }
            .map { it.toAuthUser() }
            .firstOrNull()
    }

    fun findUserByIdAndTenant(userId: Long, tenantId: Long): AuthUser? = transaction {
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.tenantId eq tenantId) }
            .map { it.toAuthUser() }
            .firstOrNull()
    }

    fun createUser(
        tenantId: Long,
        email: String,
        passwordHash: String,
        firstName: String?,
        lastName: String?,
        role: String = "owner"
    ): AuthUser = transaction {
        val now = Instant.now()
        val userId = Users.insertAndGetId {
            it[Users.tenantId] = tenantId
            it[Users.email] = email.lowercase()
            it[Users.passwordHash] = passwordHash
            it[Users.firstName] = firstName
            it[Users.lastName] = lastName
            it[Users.role] = role
            it[Users.status] = 1
            it[Users.permissions] = "[]"
            it[Users.totalCredit] = BigDecimal.ZERO
            it[Users.emailVerified] = false
            it[Users.createdAt] = now
            it[Users.updatedAt] = now
        }

        findUserById(userId.value)!!
    }

    fun updateLastLogin(userId: Long): Boolean = transaction {
        Users.update({ Users.id eq userId }) {
            it[lastLoginAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updatePassword(userId: Long, newPasswordHash: String): Boolean = transaction {
        Users.update({ Users.id eq userId }) {
            it[passwordHash] = newPasswordHash
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun verifyEmail(userId: Long): Boolean = transaction {
        Users.update({ Users.id eq userId }) {
            it[emailVerified] = true
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // TENANT OPERATIONS
    // =====================================================

    fun findTenantBySubdomain(subdomain: String): AuthTenant? = transaction {
        Tenants.selectAll()
            .where { Tenants.subdomain eq subdomain.lowercase() }
            .map { it.toAuthTenant() }
            .firstOrNull()
    }

    fun findTenantById(tenantId: Long): AuthTenant? = transaction {
        Tenants.selectAll()
            .where { Tenants.id eq tenantId }
            .map { it.toAuthTenant() }
            .firstOrNull()
    }

    fun isSubdomainAvailable(subdomain: String): Boolean = transaction {
        Tenants.selectAll()
            .where { Tenants.subdomain eq subdomain.lowercase() }
            .count() == 0L
    }

    fun isEmailAvailable(email: String): Boolean = transaction {
        Users.selectAll()
            .where { Users.email eq email.lowercase() }
            .count() == 0L
    }

    fun createTenant(
        subdomain: String,
        name: String,
        customDomain: String? = null,
        settings: String = "{}"
    ): AuthTenant = transaction {
        val now = Instant.now()
        val tenantId = Tenants.insertAndGetId {
            it[Tenants.subdomain] = subdomain.lowercase()
            it[Tenants.name] = name
            it[Tenants.status] = 1 // Active
            it[Tenants.settings] = settings
            customDomain?.let { domain -> it[Tenants.customDomain] = domain }
            it[Tenants.createdAt] = now
            it[Tenants.updatedAt] = now
        }

        findTenantById(tenantId.value)!!
    }

    // =====================================================
    // PASSWORD UTILITIES
    // =====================================================

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            logger.error("Password verification failed", e)
            false
        }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toAuthUser(): AuthUser = AuthUser(
        id = this[Users.id].value,
        tenantId = this[Users.tenantId].value,
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        firstName = this[Users.firstName],
        lastName = this[Users.lastName],
        role = this[Users.role],
        status = this[Users.status],
        permissions = this[Users.permissions],
        totalCredit = this[Users.totalCredit],
        emailVerified = this[Users.emailVerified],
        lastLoginAt = this[Users.lastLoginAt]?.toString(),
        createdAt = this[Users.createdAt].toString(),
        updatedAt = this[Users.updatedAt].toString()
    )

    private fun ResultRow.toAuthTenant(): AuthTenant = AuthTenant(
        id = this[Tenants.id].value,
        subdomain = this[Tenants.subdomain],
        name = this[Tenants.name],
        status = this[Tenants.status],
        customDomain = this[Tenants.customDomain],
        stripeCustomerId = this[Tenants.stripeCustomerId],
        settings = this[Tenants.settings],
        createdAt = this[Tenants.createdAt].toString(),
        updatedAt = this[Tenants.updatedAt].toString()
    )
}

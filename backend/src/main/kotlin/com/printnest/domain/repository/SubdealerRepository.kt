package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.ShipStationStores
import com.printnest.domain.tables.SubdealerStoreAssignments
import com.printnest.domain.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

class SubdealerRepository(
    private val storeAssignmentRepository: SubdealerStoreAssignmentRepository,
    private val shipStationStoreRepository: ShipStationStoreRepository
) {

    fun findById(id: Long): Subdealer? = transaction {
        Users.selectAll()
            .where { (Users.id eq id) and (Users.role eq UserRole.SUBDEALER.name.lowercase()) }
            .map { it.toSubdealer() }
            .singleOrNull()
            ?.withAssignedStores()
    }

    fun findByTenantId(tenantId: Long): List<Subdealer> = transaction {
        Users.selectAll()
            .where {
                (Users.tenantId eq tenantId) and
                (Users.role eq UserRole.SUBDEALER.name.lowercase())
            }
            .orderBy(Users.createdAt, SortOrder.DESC)
            .map { it.toSubdealer().withAssignedStores() }
    }

    fun findByParentUserId(parentUserId: Long): List<Subdealer> = transaction {
        Users.selectAll()
            .where {
                (Users.parentUserId eq parentUserId) and
                (Users.role eq UserRole.SUBDEALER.name.lowercase())
            }
            .orderBy(Users.createdAt, SortOrder.DESC)
            .map { it.toSubdealer().withAssignedStores() }
    }

    fun findByEmail(tenantId: Long, email: String): Subdealer? = transaction {
        Users.selectAll()
            .where {
                (Users.tenantId eq tenantId) and
                (Users.email eq email) and
                (Users.role eq UserRole.SUBDEALER.name.lowercase())
            }
            .map { it.toSubdealer() }
            .singleOrNull()
    }

    fun existsByEmail(tenantId: Long, email: String): Boolean = transaction {
        Users.selectAll()
            .where { (Users.tenantId eq tenantId) and (Users.email eq email) }
            .count() > 0
    }

    fun create(
        tenantId: Long,
        parentUserId: Long,
        request: CreateSubdealerRequest
    ): Subdealer = transaction {
        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

        val id = Users.insertAndGetId {
            it[Users.tenantId] = tenantId
            it[email] = request.email.lowercase().trim()
            it[Users.passwordHash] = passwordHash
            it[firstName] = request.firstName
            it[lastName] = request.lastName
            it[role] = UserRole.SUBDEALER.name.lowercase()
            it[status] = 1
            it[Users.parentUserId] = parentUserId
            it[priceProfileId] = request.priceProfileId
            it[shippingProfileId] = request.shippingProfileId
        }

        // Assign stores if provided
        request.storeIds?.forEach { storeId ->
            storeAssignmentRepository.assign(tenantId, id.value, storeId)
        }

        findById(id.value)!!
    }

    fun update(id: Long, request: UpdateSubdealerRequest): Subdealer? = transaction {
        Users.update({ Users.id eq id }) {
            request.firstName?.let { value -> it[firstName] = value }
            request.lastName?.let { value -> it[lastName] = value }
            request.status?.let { value -> it[status] = value }
            request.priceProfileId?.let { value -> it[priceProfileId] = value }
            request.shippingProfileId?.let { value -> it[shippingProfileId] = value }
            it[updatedAt] = Instant.now()
        }
        findById(id)
    }

    fun updateStatus(id: Long, status: Int): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[Users.status] = status
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        // First remove all store assignments
        storeAssignmentRepository.removeAllBySubdealerId(id)
        // Then deactivate the user (soft delete)
        updateStatus(id, 0)
    }

    fun hardDelete(id: Long): Boolean = transaction {
        storeAssignmentRepository.removeAllBySubdealerId(id)
        Users.deleteWhere { Users.id eq id } > 0
    }

    fun count(tenantId: Long): Long = transaction {
        Users.selectAll()
            .where {
                (Users.tenantId eq tenantId) and
                (Users.role eq UserRole.SUBDEALER.name.lowercase()) and
                (Users.status eq 1)
            }
            .count()
    }

    private fun ResultRow.toSubdealer() = Subdealer(
        id = this[Users.id].value,
        tenantId = this[Users.tenantId].value,
        email = this[Users.email],
        firstName = this[Users.firstName],
        lastName = this[Users.lastName],
        status = this[Users.status],
        totalCredit = this[Users.totalCredit],
        parentUserId = this[Users.parentUserId]?.value ?: 0L,
        priceProfileId = this[Users.priceProfileId],
        shippingProfileId = this[Users.shippingProfileId],
        assignedStores = emptyList(),
        createdAt = this[Users.createdAt].toString(),
        updatedAt = this[Users.updatedAt].toString()
    )

    private fun Subdealer.withAssignedStores(): Subdealer {
        val storeIds = storeAssignmentRepository.findStoreIdsBySubdealerId(this.id)
        val stores = if (storeIds.isNotEmpty()) {
            shipStationStoreRepository.findByIds(storeIds)
        } else {
            emptyList()
        }
        return this.copy(assignedStores = stores)
    }
}

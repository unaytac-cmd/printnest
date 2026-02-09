package com.printnest.domain.repository

import com.printnest.domain.models.SubdealerStoreAssignment
import com.printnest.domain.tables.SubdealerStoreAssignments
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class SubdealerStoreAssignmentRepository {

    fun findBySubdealerId(subdealerId: Long): List<SubdealerStoreAssignment> = transaction {
        SubdealerStoreAssignments.selectAll()
            .where { SubdealerStoreAssignments.subdealerId eq subdealerId }
            .map { it.toAssignment() }
    }

    fun findStoreIdsBySubdealerId(subdealerId: Long): List<Long> = transaction {
        SubdealerStoreAssignments.selectAll()
            .where { SubdealerStoreAssignments.subdealerId eq subdealerId }
            .map { it[SubdealerStoreAssignments.shipstationStoreId].value }
    }

    fun findSubdealerIdsByStoreId(storeId: Long): List<Long> = transaction {
        SubdealerStoreAssignments.selectAll()
            .where { SubdealerStoreAssignments.shipstationStoreId eq storeId }
            .map { it[SubdealerStoreAssignments.subdealerId].value }
    }

    fun findByTenantId(tenantId: Long): List<SubdealerStoreAssignment> = transaction {
        SubdealerStoreAssignments.selectAll()
            .where { SubdealerStoreAssignments.tenantId eq tenantId }
            .map { it.toAssignment() }
    }

    fun exists(subdealerId: Long, storeId: Long): Boolean = transaction {
        SubdealerStoreAssignments.selectAll()
            .where {
                (SubdealerStoreAssignments.subdealerId eq subdealerId) and
                (SubdealerStoreAssignments.shipstationStoreId eq storeId)
            }
            .count() > 0
    }

    fun assign(tenantId: Long, subdealerId: Long, storeId: Long): SubdealerStoreAssignment? = transaction {
        if (exists(subdealerId, storeId)) {
            return@transaction null
        }

        val id = SubdealerStoreAssignments.insertAndGetId {
            it[SubdealerStoreAssignments.tenantId] = tenantId
            it[SubdealerStoreAssignments.subdealerId] = subdealerId
            it[SubdealerStoreAssignments.shipstationStoreId] = storeId
        }

        SubdealerStoreAssignments.selectAll()
            .where { SubdealerStoreAssignments.id eq id }
            .map { it.toAssignment() }
            .singleOrNull()
    }

    fun assignMultiple(tenantId: Long, subdealerId: Long, storeIds: List<Long>): List<SubdealerStoreAssignment> = transaction {
        storeIds.mapNotNull { storeId ->
            assign(tenantId, subdealerId, storeId)
        }
    }

    fun remove(subdealerId: Long, storeId: Long): Boolean = transaction {
        SubdealerStoreAssignments.deleteWhere {
            (SubdealerStoreAssignments.subdealerId eq subdealerId) and
            (SubdealerStoreAssignments.shipstationStoreId eq storeId)
        } > 0
    }

    fun removeAllBySubdealerId(subdealerId: Long): Int = transaction {
        SubdealerStoreAssignments.deleteWhere {
            SubdealerStoreAssignments.subdealerId eq subdealerId
        }
    }

    fun removeAllByStoreId(storeId: Long): Int = transaction {
        SubdealerStoreAssignments.deleteWhere {
            SubdealerStoreAssignments.shipstationStoreId eq storeId
        }
    }

    fun replaceAssignments(tenantId: Long, subdealerId: Long, newStoreIds: List<Long>): List<SubdealerStoreAssignment> = transaction {
        // Remove existing assignments
        removeAllBySubdealerId(subdealerId)
        // Add new assignments
        assignMultiple(tenantId, subdealerId, newStoreIds)
    }

    private fun ResultRow.toAssignment() = SubdealerStoreAssignment(
        id = this[SubdealerStoreAssignments.id].value,
        tenantId = this[SubdealerStoreAssignments.tenantId].value,
        subdealerId = this[SubdealerStoreAssignments.subdealerId].value,
        shipstationStoreId = this[SubdealerStoreAssignments.shipstationStoreId].value,
        createdAt = this[SubdealerStoreAssignments.createdAt].toString()
    )
}

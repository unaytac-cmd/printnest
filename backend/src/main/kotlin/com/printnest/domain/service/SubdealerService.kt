package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.ShipStationStoreRepository
import com.printnest.domain.repository.SubdealerRepository
import com.printnest.domain.repository.SubdealerStoreAssignmentRepository
import org.slf4j.LoggerFactory

class SubdealerService(
    private val subdealerRepository: SubdealerRepository,
    private val storeAssignmentRepository: SubdealerStoreAssignmentRepository,
    private val storeRepository: ShipStationStoreRepository
) {
    private val logger = LoggerFactory.getLogger(SubdealerService::class.java)

    /**
     * Get all subdealers for a tenant
     */
    fun getSubdealers(tenantId: Long): List<Subdealer> {
        return subdealerRepository.findByTenantId(tenantId)
    }

    /**
     * Get subdealers created by a specific producer
     */
    fun getSubdealersByProducer(producerId: Long): List<Subdealer> {
        return subdealerRepository.findByParentUserId(producerId)
    }

    /**
     * Get a single subdealer by ID
     */
    fun getSubdealer(id: Long): Subdealer? {
        return subdealerRepository.findById(id)
    }

    /**
     * Create a new subdealer
     */
    fun createSubdealer(
        tenantId: Long,
        producerId: Long,
        request: CreateSubdealerRequest
    ): Result<Subdealer> {
        // Check if email already exists
        if (subdealerRepository.existsByEmail(tenantId, request.email)) {
            return Result.failure(SubdealerException.EmailAlreadyExists(request.email))
        }

        // Validate store IDs if provided
        request.storeIds?.let { storeIds ->
            val existingStores = storeRepository.findByIds(storeIds)
            val missingIds = storeIds.filter { id -> existingStores.none { it.id == id } }
            if (missingIds.isNotEmpty()) {
                return Result.failure(SubdealerException.InvalidStoreIds(missingIds))
            }
        }

        return try {
            val subdealer = subdealerRepository.create(tenantId, producerId, request)
            logger.info("Created subdealer ${subdealer.id} for producer $producerId")
            Result.success(subdealer)
        } catch (e: Exception) {
            logger.error("Failed to create subdealer", e)
            Result.failure(SubdealerException.CreationFailed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Update a subdealer
     */
    fun updateSubdealer(id: Long, request: UpdateSubdealerRequest): Result<Subdealer> {
        val existing = subdealerRepository.findById(id)
            ?: return Result.failure(SubdealerException.NotFound(id))

        return try {
            val updated = subdealerRepository.update(id, request)
                ?: return Result.failure(SubdealerException.UpdateFailed(id))
            logger.info("Updated subdealer $id")
            Result.success(updated)
        } catch (e: Exception) {
            logger.error("Failed to update subdealer $id", e)
            Result.failure(SubdealerException.UpdateFailed(id))
        }
    }

    /**
     * Deactivate a subdealer (soft delete)
     */
    fun deactivateSubdealer(id: Long): Result<Boolean> {
        val existing = subdealerRepository.findById(id)
            ?: return Result.failure(SubdealerException.NotFound(id))

        return try {
            val success = subdealerRepository.delete(id)
            if (success) {
                logger.info("Deactivated subdealer $id")
                Result.success(true)
            } else {
                Result.failure(SubdealerException.DeactivationFailed(id))
            }
        } catch (e: Exception) {
            logger.error("Failed to deactivate subdealer $id", e)
            Result.failure(SubdealerException.DeactivationFailed(id))
        }
    }

    /**
     * Activate a subdealer
     */
    fun activateSubdealer(id: Long): Result<Boolean> {
        val existing = subdealerRepository.findById(id)
            ?: return Result.failure(SubdealerException.NotFound(id))

        return try {
            val success = subdealerRepository.updateStatus(id, 1)
            if (success) {
                logger.info("Activated subdealer $id")
                Result.success(true)
            } else {
                Result.failure(SubdealerException.ActivationFailed(id))
            }
        } catch (e: Exception) {
            logger.error("Failed to activate subdealer $id", e)
            Result.failure(SubdealerException.ActivationFailed(id))
        }
    }

    /**
     * Get assigned stores for a subdealer
     */
    fun getAssignedStores(subdealerId: Long): List<ShipStationStore> {
        val storeIds = storeAssignmentRepository.findStoreIdsBySubdealerId(subdealerId)
        return if (storeIds.isNotEmpty()) {
            storeRepository.findByIds(storeIds)
        } else {
            emptyList()
        }
    }

    /**
     * Assign stores to a subdealer
     */
    fun assignStores(
        tenantId: Long,
        subdealerId: Long,
        storeIds: List<Long>
    ): Result<List<ShipStationStore>> {
        val existing = subdealerRepository.findById(subdealerId)
            ?: return Result.failure(SubdealerException.NotFound(subdealerId))

        // Validate store IDs
        val existingStores = storeRepository.findByIds(storeIds)
        val missingIds = storeIds.filter { id -> existingStores.none { it.id == id } }
        if (missingIds.isNotEmpty()) {
            return Result.failure(SubdealerException.InvalidStoreIds(missingIds))
        }

        return try {
            storeAssignmentRepository.replaceAssignments(tenantId, subdealerId, storeIds)
            val assignedStores = getAssignedStores(subdealerId)
            logger.info("Assigned ${storeIds.size} stores to subdealer $subdealerId")
            Result.success(assignedStores)
        } catch (e: Exception) {
            logger.error("Failed to assign stores to subdealer $subdealerId", e)
            Result.failure(SubdealerException.StoreAssignmentFailed(subdealerId))
        }
    }

    /**
     * Add a store assignment
     */
    fun addStoreAssignment(
        tenantId: Long,
        subdealerId: Long,
        storeId: Long
    ): Result<ShipStationStore> {
        val existing = subdealerRepository.findById(subdealerId)
            ?: return Result.failure(SubdealerException.NotFound(subdealerId))

        val store = storeRepository.findById(storeId)
            ?: return Result.failure(SubdealerException.InvalidStoreIds(listOf(storeId)))

        // Check if already assigned
        if (storeAssignmentRepository.exists(subdealerId, storeId)) {
            return Result.failure(SubdealerException.StoreAlreadyAssigned(subdealerId, storeId))
        }

        return try {
            storeAssignmentRepository.assign(tenantId, subdealerId, storeId)
            logger.info("Added store $storeId to subdealer $subdealerId")
            Result.success(store)
        } catch (e: Exception) {
            logger.error("Failed to add store assignment", e)
            Result.failure(SubdealerException.StoreAssignmentFailed(subdealerId))
        }
    }

    /**
     * Remove a store assignment
     */
    fun removeStoreAssignment(subdealerId: Long, storeId: Long): Result<Boolean> {
        val existing = subdealerRepository.findById(subdealerId)
            ?: return Result.failure(SubdealerException.NotFound(subdealerId))

        return try {
            val success = storeAssignmentRepository.remove(subdealerId, storeId)
            if (success) {
                logger.info("Removed store $storeId from subdealer $subdealerId")
                Result.success(true)
            } else {
                Result.failure(SubdealerException.StoreNotAssigned(subdealerId, storeId))
            }
        } catch (e: Exception) {
            logger.error("Failed to remove store assignment", e)
            Result.failure(SubdealerException.StoreAssignmentFailed(subdealerId))
        }
    }

    /**
     * Get store IDs assigned to a subdealer (for order filtering)
     */
    fun getAssignedStoreIds(subdealerId: Long): List<Long> {
        return storeAssignmentRepository.findStoreIdsBySubdealerId(subdealerId)
    }

    /**
     * Count active subdealers for a tenant
     */
    fun countSubdealers(tenantId: Long): Long {
        return subdealerRepository.count(tenantId)
    }
}

sealed class SubdealerException(message: String) : Exception(message) {
    data class NotFound(val id: Long) : SubdealerException("Subdealer not found: $id")
    data class EmailAlreadyExists(val email: String) : SubdealerException("Email already exists: $email")
    data class InvalidStoreIds(val ids: List<Long>) : SubdealerException("Invalid store IDs: $ids")
    data class CreationFailed(val reason: String) : SubdealerException("Failed to create subdealer: $reason")
    data class UpdateFailed(val id: Long) : SubdealerException("Failed to update subdealer: $id")
    data class DeactivationFailed(val id: Long) : SubdealerException("Failed to deactivate subdealer: $id")
    data class ActivationFailed(val id: Long) : SubdealerException("Failed to activate subdealer: $id")
    data class StoreAssignmentFailed(val subdealerId: Long) : SubdealerException("Failed to assign stores to subdealer: $subdealerId")
    data class StoreAlreadyAssigned(val subdealerId: Long, val storeId: Long) : SubdealerException("Store $storeId already assigned to subdealer $subdealerId")
    data class StoreNotAssigned(val subdealerId: Long, val storeId: Long) : SubdealerException("Store $storeId not assigned to subdealer $subdealerId")
}

package com.printnest.routes

import com.printnest.domain.models.AssignStoresRequest
import com.printnest.domain.models.CreateSubdealerRequest
import com.printnest.domain.models.ErrorResponse
import com.printnest.domain.models.UpdateSubdealerRequest
import com.printnest.domain.service.SubdealerException
import com.printnest.domain.service.SubdealerService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

fun Route.subdealerRoutes() {
    val subdealerService: SubdealerService = GlobalContext.get().get()

    route("/subdealers") {

        /**
         * GET /api/v1/subdealers
         * List all subdealers for the current producer
         */
        get {
            // TODO: Get from JWT claims
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L
            val producerId = call.parameters["producerId"]?.toLongOrNull()

            val subdealers = if (producerId != null) {
                subdealerService.getSubdealersByProducer(producerId)
            } else {
                subdealerService.getSubdealers(tenantId)
            }

            call.respond(HttpStatusCode.OK, subdealers.map { it.toResponse() })
        }

        /**
         * POST /api/v1/subdealers
         * Create a new subdealer
         */
        post {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L
            val producerId = call.parameters["producerId"]?.toLongOrNull() ?: 1L // TODO: Get from JWT

            val request = call.receive<CreateSubdealerRequest>()

            val result = subdealerService.createSubdealer(tenantId, producerId, request)

            result.fold(
                onSuccess = { subdealer ->
                    call.respond(HttpStatusCode.Created, subdealer.toResponse())
                },
                onFailure = { error ->
                    when (error) {
                        is SubdealerException.EmailAlreadyExists -> {
                            call.respond(HttpStatusCode.Conflict, ErrorResponse(
                                error = "email_exists",
                                message = error.message ?: "Email already exists"
                            ))
                        }
                        is SubdealerException.InvalidStoreIds -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                                error = "invalid_stores",
                                message = error.message ?: "Invalid store IDs"
                            ))
                        }
                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                                error = "creation_failed",
                                message = error.message ?: "Failed to create subdealer"
                            ))
                        }
                    }
                }
            )
        }

        /**
         * GET /api/v1/subdealers/{id}
         * Get a single subdealer
         */
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@get
            }

            val subdealer = subdealerService.getSubdealer(id)
            if (subdealer == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Subdealer not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, subdealer.toResponse())
        }

        /**
         * PUT /api/v1/subdealers/{id}
         * Update a subdealer
         */
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@put
            }

            val request = call.receive<UpdateSubdealerRequest>()
            val result = subdealerService.updateSubdealer(id, request)

            result.fold(
                onSuccess = { subdealer ->
                    call.respond(HttpStatusCode.OK, subdealer.toResponse())
                },
                onFailure = { error ->
                    when (error) {
                        is SubdealerException.NotFound -> {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", error.message ?: "Subdealer not found"))
                        }
                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("update_failed", error.message ?: "Failed to update subdealer"))
                        }
                    }
                }
            )
        }

        /**
         * DELETE /api/v1/subdealers/{id}
         * Deactivate a subdealer
         */
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@delete
            }

            val result = subdealerService.deactivateSubdealer(id)

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Subdealer deactivated"))
                },
                onFailure = { error ->
                    when (error) {
                        is SubdealerException.NotFound -> {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", error.message ?: "Subdealer not found"))
                        }
                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("deactivation_failed", error.message ?: "Failed to deactivate subdealer"))
                        }
                    }
                }
            )
        }

        /**
         * POST /api/v1/subdealers/{id}/activate
         * Activate a subdealer
         */
        post("/{id}/activate") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@post
            }

            val result = subdealerService.activateSubdealer(id)

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Subdealer activated"))
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("activation_failed", error.message ?: "Failed to activate subdealer"))
                }
            )
        }

        /**
         * GET /api/v1/subdealers/{id}/stores
         * Get stores assigned to a subdealer
         */
        get("/{id}/stores") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@get
            }

            val stores = subdealerService.getAssignedStores(id)
            call.respond(HttpStatusCode.OK, stores.map { store ->
                StoreInfo(
                    id = store.id,
                    shipstationStoreId = store.shipstationStoreId,
                    storeName = store.storeName,
                    marketplaceName = store.marketplaceName,
                    isActive = store.isActive
                )
            })
        }

        /**
         * POST /api/v1/subdealers/{id}/stores
         * Assign stores to a subdealer (replaces existing assignments)
         */
        post("/{id}/stores") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid subdealer ID"))
                return@post
            }

            val request = call.receive<AssignStoresRequest>()
            val result = subdealerService.assignStores(tenantId, id, request.storeIds)

            result.fold(
                onSuccess = { stores ->
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Assigned ${stores.size} stores",
                        "stores" to stores.map { store ->
                            StoreInfo(
                                id = store.id,
                                shipstationStoreId = store.shipstationStoreId,
                                storeName = store.storeName,
                                marketplaceName = store.marketplaceName,
                                isActive = store.isActive
                            )
                        }
                    ))
                },
                onFailure = { error ->
                    when (error) {
                        is SubdealerException.NotFound -> {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", error.message ?: "Subdealer not found"))
                        }
                        is SubdealerException.InvalidStoreIds -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_stores", error.message ?: "Invalid store IDs"))
                        }
                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("assignment_failed", error.message ?: "Failed to assign stores"))
                        }
                    }
                }
            )
        }

        /**
         * DELETE /api/v1/subdealers/{id}/stores/{storeId}
         * Remove a store assignment from a subdealer
         */
        delete("/{id}/stores/{storeId}") {
            val id = call.parameters["id"]?.toLongOrNull()
            val storeId = call.parameters["storeId"]?.toLongOrNull()

            if (id == null || storeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_id", "Invalid ID"))
                return@delete
            }

            val result = subdealerService.removeStoreAssignment(id, storeId)

            result.fold(
                onSuccess = {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Store removed from subdealer"))
                },
                onFailure = { error ->
                    when (error) {
                        is SubdealerException.NotFound -> {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", error.message ?: "Subdealer not found"))
                        }
                        is SubdealerException.StoreNotAssigned -> {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_assigned", error.message ?: "Store not assigned"))
                        }
                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("removal_failed", error.message ?: "Failed to remove store"))
                        }
                    }
                }
            )
        }

        /**
         * GET /api/v1/subdealers/count
         * Get count of active subdealers
         */
        get("/count") {
            val tenantId = call.parameters["tenantId"]?.toLongOrNull() ?: 1L
            val count = subdealerService.countSubdealers(tenantId)
            call.respond(HttpStatusCode.OK, mapOf("count" to count))
        }
    }
}

// Response models
@Serializable
data class SubdealerResponse(
    val id: Long,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val fullName: String,
    val status: Int,
    val totalCredit: String,
    val assignedStores: List<StoreInfo>,
    val createdAt: String,
    val updatedAt: String
)

// Extension function to convert Subdealer to Response
private fun com.printnest.domain.models.Subdealer.toResponse() = SubdealerResponse(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    fullName = fullName,
    status = status,
    totalCredit = totalCredit.toPlainString(),
    assignedStores = assignedStores.map { store ->
        StoreInfo(
            id = store.id,
            shipstationStoreId = store.shipstationStoreId,
            storeName = store.storeName,
            marketplaceName = store.marketplaceName,
            isActive = store.isActive
        )
    },
    createdAt = createdAt,
    updatedAt = updatedAt
)

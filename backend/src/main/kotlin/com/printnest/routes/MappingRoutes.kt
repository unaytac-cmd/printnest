package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.MappingService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.mappingRoutes() {
    val mappingService: MappingService = GlobalContext.get().get()

    // =====================================================
    // ORDER MAPPING ROUTES
    // =====================================================

    route("/orders/{id}") {
        // POST /api/v1/orders/{id}/map - Run mapping on order
        post("/map") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()

            mappingService.mapOrder(tenantId, orderId, userId)
                .onSuccess { result ->
                    call.respond(result)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/orders/{id}/mapping-status - Get mapping status
        get("/mapping-status") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val orderId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            mappingService.getMappingStatus(tenantId, orderId)
                .onSuccess { status ->
                    call.respond(status)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // MAP VALUES ROUTES (Variant Mapping)
    // =====================================================

    route("/map-values") {
        // GET /api/v1/map-values - List all value mappings
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val mapValues = mappingService.getMapValues(tenantId, userId)

            call.respond(mapOf("mapValues" to mapValues, "total" to mapValues.size))
        }

        // GET /api/v1/map-values/{id} - Get map value by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map value ID"))

            val mapValue = mappingService.getMapValue(id, tenantId)
            if (mapValue != null) {
                call.respond(mapValue)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Map value not found"))
            }
        }

        // GET /api/v1/map-values/find - Find map value by value IDs
        get("/find") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val valueId1 = call.request.queryParameters["valueId1"]
            val valueId2 = call.request.queryParameters["valueId2"]
            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val mapValue = mappingService.findMapping(tenantId, valueId1, valueId2, userId)
            if (mapValue != null) {
                call.respond(mapValue)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Map value not found"))
            }
        }

        // POST /api/v1/map-values - Create value mapping
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
            val request = call.receive<CreateMapValueRequest>()

            mappingService.createMapValue(tenantId, userId, request)
                .onSuccess { mapValue ->
                    call.respond(HttpStatusCode.Created, mapValue)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/map-values/{id} - Update value mapping
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map value ID"))

            val request = call.receive<CreateMapValueRequest>()

            mappingService.updateMapValue(id, tenantId, request)
                .onSuccess { mapValue ->
                    call.respond(mapValue)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/map-values/{id} - Delete value mapping
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map value ID"))

            mappingService.deleteMapValue(id, tenantId)
                .onSuccess {
                    call.respond(HttpStatusCode.NoContent)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // MAP LISTINGS ROUTES (Design Mapping)
    // =====================================================

    route("/map-listings") {
        // GET /api/v1/map-listings - List all listing mappings
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val mapListings = mappingService.getMapListings(tenantId, userId)

            call.respond(mapOf("mapListings" to mapListings, "total" to mapListings.size))
        }

        // GET /api/v1/map-listings/{id} - Get map listing by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map listing ID"))

            val mapListing = mappingService.getMapListing(id, tenantId)
            if (mapListing != null) {
                call.respond(mapListing)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Map listing not found"))
            }
        }

        // GET /api/v1/map-listings/find - Find map listing by listing ID
        get("/find") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val listingId = call.request.queryParameters["listingId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Listing ID required"))

            val modificationId = call.request.queryParameters["modificationId"]?.toLongOrNull()
            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val mapListing = mappingService.findListingMapping(tenantId, listingId, modificationId, userId)
            if (mapListing != null) {
                call.respond(mapListing)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Map listing not found"))
            }
        }

        // GET /api/v1/map-listings/by-listing/{listingId} - Get all mappings for a listing
        get("/by-listing/{listingId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val listingId = call.parameters["listingId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid listing ID"))

            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val mapListings = mappingService.findListingMappings(tenantId, listingId, userId)

            call.respond(mapOf("mapListings" to mapListings, "total" to mapListings.size))
        }

        // GET /api/v1/map-listings/designs/{listingId} - Get listing designs
        get("/designs/{listingId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val listingId = call.parameters["listingId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid listing ID"))

            val designs = mappingService.getListingDesigns(tenantId, listingId)
            call.respond(designs)
        }

        // POST /api/v1/map-listings - Create listing mapping
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
            val request = call.receive<CreateMapListingRequest>()

            mappingService.createMapListing(tenantId, userId, request)
                .onSuccess { mapListing ->
                    call.respond(HttpStatusCode.Created, mapListing)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/map-listings/{id} - Update listing mapping
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map listing ID"))

            val request = call.receive<CreateMapListingRequest>()

            mappingService.updateMapListing(id, tenantId, request)
                .onSuccess { mapListing ->
                    call.respond(mapListing)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/map-listings/{id} - Delete listing mapping
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid map listing ID"))

            mappingService.deleteMapListing(id, tenantId)
                .onSuccess {
                    call.respond(HttpStatusCode.NoContent)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/map-listings/by-listing/{listingId} - Delete all mappings for a listing
        delete("/by-listing/{listingId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val listingId = call.parameters["listingId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid listing ID"))

            val deletedCount = mappingService.deleteMapListingsByListingId(listingId, tenantId)
            call.respond(mapOf("deleted" to deletedCount))
        }
    }

    // =====================================================
    // BULK MAPPING ROUTES
    // =====================================================

    route("/mappings") {
        // POST /api/v1/mappings/save-design-map - Save design mappings from order view
        post("/save-design-map") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val requests = call.receive<List<SaveDesignMapRequest>>()

            mappingService.saveDesignMap(tenantId, requests)
                .onSuccess { mappings ->
                    call.respond(HttpStatusCode.Created, mapOf(
                        "status" to "ok",
                        "message" to "Design mapping and product values successfully recorded",
                        "mappings" to mappings
                    ))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }
}

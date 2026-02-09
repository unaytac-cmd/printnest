package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.ProfileService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.profileRoutes() {
    val profileService: ProfileService = GlobalContext.get().get()

    // =====================================================
    // SHIPPING PROFILES
    // =====================================================

    route("/shipping-profiles") {
        // GET /api/v1/shipping-profiles - List all shipping profiles
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
            val profiles = profileService.getShippingProfiles(tenantId, includeInactive)

            call.respond(mapOf("shippingProfiles" to profiles))
        }

        // POST /api/v1/shipping-profiles - Create shipping profile
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateShippingProfileRequest>()

            profileService.createShippingProfile(tenantId, request)
                .onSuccess { profile ->
                    call.respond(HttpStatusCode.Created, profile)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/shipping-profiles/default - Get default shipping profile
        get("/default") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profile = profileService.getDefaultShippingProfile(tenantId)

            if (profile != null) {
                call.respond(profile)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No default shipping profile"))
            }
        }

        // GET /api/v1/shipping-profiles/{id} - Get shipping profile by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val withMethods = call.request.queryParameters["withMethods"]?.toBoolean() ?: false
            val profile = profileService.getShippingProfile(id, tenantId, withMethods)

            if (profile != null) {
                call.respond(profile)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Shipping profile not found"))
            }
        }

        // PUT /api/v1/shipping-profiles/{id} - Update shipping profile
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val request = call.receive<UpdateShippingProfileRequest>()

            profileService.updateShippingProfile(id, tenantId, request)
                .onSuccess { profile ->
                    call.respond(profile)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/shipping-profiles/{id} - Delete shipping profile
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            profileService.deleteShippingProfile(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Shipping profile not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    // =====================================================
    // SHIPPING METHODS
    // =====================================================

    route("/shipping-methods") {
        // GET /api/v1/shipping-methods - List all shipping methods
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.request.queryParameters["profileId"]?.toLongOrNull()
            val methods = profileService.getShippingMethods(tenantId, profileId)

            call.respond(mapOf("shippingMethods" to methods))
        }

        // POST /api/v1/shipping-methods - Create shipping method
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateShippingMethodRequest>()

            profileService.createShippingMethod(tenantId, request)
                .onSuccess { method ->
                    call.respond(HttpStatusCode.Created, method)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/shipping-methods/defaults - Create default shipping methods for profile
        post("/defaults") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.request.queryParameters["profileId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Profile ID required"))

            val methods = profileService.createDefaultShippingMethods(tenantId, profileId)

            call.respond(HttpStatusCode.Created, mapOf("shippingMethods" to methods))
        }

        // GET /api/v1/shipping-methods/{id} - Get shipping method by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid method ID"))

            val method = profileService.getShippingMethod(id, tenantId)

            if (method != null) {
                call.respond(method)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Shipping method not found"))
            }
        }

        // PUT /api/v1/shipping-methods/{id} - Update shipping method
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid method ID"))

            val request = call.receive<UpdateShippingMethodRequest>()

            profileService.updateShippingMethod(id, tenantId, request)
                .onSuccess { method ->
                    call.respond(method)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/shipping-methods/{id} - Delete shipping method
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid method ID"))

            profileService.deleteShippingMethod(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Shipping method not found"))
                    }
                }
        }
    }

    // =====================================================
    // PRICE PROFILES
    // =====================================================

    route("/price-profiles") {
        // GET /api/v1/price-profiles - List all price profiles
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
            val profiles = profileService.getPriceProfiles(tenantId, includeInactive)

            call.respond(mapOf("priceProfiles" to profiles))
        }

        // POST /api/v1/price-profiles - Create price profile
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreatePriceProfileRequest>()

            profileService.createPriceProfile(tenantId, request)
                .onSuccess { profile ->
                    call.respond(HttpStatusCode.Created, profile)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/price-profiles/default - Get default price profile
        get("/default") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profile = profileService.getDefaultPriceProfile(tenantId)

            if (profile != null) {
                call.respond(profile)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No default price profile"))
            }
        }

        // GET /api/v1/price-profiles/{id} - Get price profile by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val withProducts = call.request.queryParameters["withProducts"]?.toBoolean() ?: false
            val profile = profileService.getPriceProfile(id, tenantId, withProducts)

            if (profile != null) {
                call.respond(profile)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Price profile not found"))
            }
        }

        // PUT /api/v1/price-profiles/{id} - Update price profile
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val request = call.receive<UpdatePriceProfileRequest>()

            profileService.updatePriceProfile(id, tenantId, request)
                .onSuccess { profile ->
                    call.respond(profile)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/price-profiles/{id} - Delete price profile
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            profileService.deletePriceProfile(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Price profile not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // PRICE PROFILE PRODUCTS (Variant Overrides)
        // =====================================================

        // GET /api/v1/price-profiles/{id}/products - Get variant overrides
        get("/{id}/products") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val products = profileService.getPriceProfileProducts(profileId, tenantId)

            call.respond(mapOf("products" to products))
        }

        // POST /api/v1/price-profiles/{id}/products - Add variant override
        post("/{id}/products") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val request = call.receive<CreatePriceProfileProductRequest>()

            profileService.addPriceProfileProduct(profileId, tenantId, request)
                .onSuccess { product ->
                    call.respond(HttpStatusCode.Created, product)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/price-profiles/{id}/products/bulk - Bulk update variant overrides
        put("/{id}/products/bulk") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val request = call.receive<BulkPriceProfileProductRequest>()

            profileService.bulkAddPriceProfileProducts(profileId, tenantId, request.products)
                .onSuccess { products ->
                    call.respond(mapOf("products" to products))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/price-profiles/{id}/products/{variantId} - Remove variant override
        delete("/{id}/products/{variantId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val profileId = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))

            val variantId = call.parameters["variantId"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid variant ID"))

            profileService.removePriceProfileProduct(profileId, variantId, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Product override not found"))
                    }
                }
        }
    }

    // =====================================================
    // CALCULATIONS
    // =====================================================

    route("/calculate") {
        // POST /api/v1/calculate/shipping - Calculate shipping cost
        post("/shipping") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<ShippingCalculationRequest>()
            val result = profileService.calculateShipping(tenantId, request)

            call.respond(result)
        }

        // POST /api/v1/calculate/price - Calculate price with discounts
        post("/price") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<PriceCalculationRequest>()
            val result = profileService.calculatePrice(tenantId, request)

            call.respond(result)
        }
    }
}

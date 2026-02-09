package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.ProductService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.productRoutes() {
    val productService: ProductService = GlobalContext.get().get()

    route("/products") {
        // GET /api/v1/products - List all products
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val categoryId = call.request.queryParameters["categoryId"]?.toLongOrNull()
            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false

            val products = productService.getProducts(tenantId, categoryId, includeInactive)

            call.respond(mapOf("products" to products))
        }

        // POST /api/v1/products - Create product
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateProductRequest>()

            productService.createProduct(tenantId, request)
                .onSuccess { product ->
                    call.respond(HttpStatusCode.Created, product)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/products/{id} - Get product by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val withDetails = call.request.queryParameters["withDetails"]?.toBoolean() ?: false
            val fullDetails = call.request.queryParameters["fullDetails"]?.toBoolean() ?: false

            val result = when {
                fullDetails -> productService.getProductFullDetails(id, tenantId)
                withDetails -> productService.getProductWithDetails(id, tenantId)
                else -> productService.getProduct(id, tenantId)
            }

            if (result != null) {
                call.respond(result)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Product not found"))
            }
        }

        // PUT /api/v1/products/{id} - Update product
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<UpdateProductRequest>()

            productService.updateProduct(id, tenantId, request)
                .onSuccess { product ->
                    call.respond(product)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/products/{id} - Delete product
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            productService.deleteProduct(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Product not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // OPTIONS
        // =====================================================

        // GET /api/v1/products/{id}/options - Get all options
        get("/{id}/options") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val option1s = productService.getOption1s(productId, tenantId)
            val option2s = productService.getOption2s(productId, tenantId)

            call.respond(mapOf(
                "option1s" to option1s,
                "option2s" to option2s
            ))
        }

        // POST /api/v1/products/{id}/options - Bulk create options
        post("/{id}/options") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<BulkCreateOptionsRequest>()

            productService.bulkCreateOptions(productId, tenantId, request)
                .onSuccess { result ->
                    call.respond(HttpStatusCode.Created, result)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/products/{id}/option1s - Create option1
        post("/{id}/option1s") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<CreateOption1Request>()

            productService.createOption1(productId, tenantId, request)
                .onSuccess { option ->
                    call.respond(HttpStatusCode.Created, option)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/products/{id}/option2s - Create option2
        post("/{id}/option2s") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<CreateOption2Request>()

            productService.createOption2(productId, tenantId, request)
                .onSuccess { option ->
                    call.respond(HttpStatusCode.Created, option)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // VARIANTS
        // =====================================================

        // GET /api/v1/products/{id}/variants - Get all variants
        get("/{id}/variants") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val variants = productService.getVariants(productId, tenantId)

            call.respond(mapOf("variants" to variants))
        }

        // POST /api/v1/products/{id}/variants - Create variant
        post("/{id}/variants") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<CreateVariantRequest>()

            productService.createVariant(productId, tenantId, request)
                .onSuccess { variant ->
                    call.respond(HttpStatusCode.Created, variant)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // POST /api/v1/products/{id}/variants/generate - Generate variants from options
        post("/{id}/variants/generate") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<GenerateVariantsRequest>()

            productService.generateVariants(productId, tenantId, request)
                .onSuccess { variants ->
                    call.respond(HttpStatusCode.Created, mapOf("variants" to variants))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/products/{id}/variants/bulk - Bulk update variants
        put("/{id}/variants/bulk") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<BulkUpdateVariantsRequest>()

            productService.bulkUpdateVariants(productId, tenantId, request)
                .onSuccess { variants ->
                    call.respond(mapOf("variants" to variants))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // VARIANT MODIFICATIONS (Size-based dimensions)
        // =====================================================

        // GET /api/v1/products/{id}/variant-modifications - Get all variant modifications
        get("/{id}/variant-modifications") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val modifications = productService.getVariantModifications(productId, tenantId)

            call.respond(mapOf("variantModifications" to modifications))
        }

        // POST /api/v1/products/{id}/variant-modifications - Create/Update variant modification
        post("/{id}/variant-modifications") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val request = call.receive<CreateVariantModificationRequest>()

            productService.createVariantModification(productId, tenantId, request)
                .onSuccess { modification ->
                    call.respond(HttpStatusCode.Created, modification)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // PUT /api/v1/products/{id}/variant-modifications/bulk - Bulk update variant modifications
        put("/{id}/variant-modifications/bulk") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val productId = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid product ID"))

            val requests = call.receive<List<CreateVariantModificationRequest>>()

            productService.bulkUpdateVariantModifications(productId, tenantId, requests)
                .onSuccess { modifications ->
                    call.respond(mapOf("variantModifications" to modifications))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    route("/variants") {
        // GET /api/v1/variants/{id} - Get variant by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid variant ID"))

            val variant = productService.getVariant(id, tenantId)

            if (variant != null) {
                call.respond(variant)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Variant not found"))
            }
        }

        // PUT /api/v1/variants/{id} - Update variant
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid variant ID"))

            val request = call.receive<UpdateVariantRequest>()

            productService.updateVariant(id, tenantId, request)
                .onSuccess { variant ->
                    call.respond(variant)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/variants/{id} - Delete variant
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid variant ID"))

            productService.deleteVariant(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Variant not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    // Option deletion routes
    route("/option1s") {
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid option ID"))

            productService.deleteOption1(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Option not found"))
                    }
                }
        }
    }

    route("/option2s") {
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid option ID"))

            productService.deleteOption2(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Option not found"))
                    }
                }
        }
    }
}

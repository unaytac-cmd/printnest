package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.CategoryService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.categoryRoutes() {
    val categoryService: CategoryService = GlobalContext.get().get()

    route("/categories") {
        // GET /api/v1/categories - List all categories
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val hierarchical = call.request.queryParameters["hierarchical"]?.toBoolean() ?: false
            val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false

            val categories = if (hierarchical) {
                categoryService.getCategoriesHierarchical(tenantId)
            } else {
                categoryService.getCategories(tenantId, includeInactive)
            }

            call.respond(mapOf("categories" to categories))
        }

        // POST /api/v1/categories - Create category
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val request = call.receive<CreateCategoryRequest>()

            categoryService.createCategory(tenantId, request)
                .onSuccess { category ->
                    call.respond(HttpStatusCode.Created, category)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // GET /api/v1/categories/{id} - Get category by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))

            val withModifications = call.request.queryParameters["withModifications"]?.toBoolean() ?: false

            val category = if (withModifications) {
                categoryService.getCategoryWithModifications(id, tenantId)
            } else {
                categoryService.getCategory(id, tenantId)
            }

            if (category != null) {
                call.respond(category)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Category not found"))
            }
        }

        // PUT /api/v1/categories/{id} - Update category
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))

            val request = call.receive<UpdateCategoryRequest>()

            categoryService.updateCategory(id, tenantId, request)
                .onSuccess { category ->
                    call.respond(category)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/categories/{id} - Delete category
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))

            categoryService.deleteCategory(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Category not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // MODIFICATIONS (Print Locations)
        // =====================================================

        // GET /api/v1/categories/{id}/modifications - Get modifications for category
        get("/{id}/modifications") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val categoryId = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))

            val includeParent = call.request.queryParameters["includeParent"]?.toBoolean() ?: true

            val modifications = categoryService.getModificationsForCategory(categoryId, tenantId, includeParent)

            call.respond(mapOf("modifications" to modifications))
        }

        // POST /api/v1/categories/{id}/modifications - Create modification
        post("/{id}/modifications") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val categoryId = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))

            val request = call.receive<CreateModificationRequest>()

            categoryService.createModification(categoryId, tenantId, request)
                .onSuccess { modification ->
                    call.respond(HttpStatusCode.Created, modification)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }

    route("/modifications") {
        // GET /api/v1/modifications/{id} - Get modification by ID
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid modification ID"))

            val modification = categoryService.getModification(id, tenantId)

            if (modification != null) {
                call.respond(modification)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Modification not found"))
            }
        }

        // PUT /api/v1/modifications/{id} - Update modification
        put("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid modification ID"))

            val request = call.receive<UpdateModificationRequest>()

            categoryService.updateModification(id, tenantId, request)
                .onSuccess { modification ->
                    call.respond(modification)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // DELETE /api/v1/modifications/{id} - Delete modification
        delete("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid modification ID"))

            categoryService.deleteModification(id, tenantId)
                .onSuccess { deleted ->
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Modification not found"))
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }
    }
}

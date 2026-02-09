package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.TicketService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.ticketRoutes() {
    val ticketService: TicketService = GlobalContext.get().get()

    route("/tickets") {

        // =====================================================
        // CATEGORIES
        // =====================================================

        // GET /api/v1/tickets/categories - Get all ticket categories
        get("/categories") {
            val categories = ticketService.getCategories()
            call.respond(mapOf("categories" to categories))
        }

        // =====================================================
        // STATS
        // =====================================================

        // GET /api/v1/tickets/stats - Get ticket statistics
        get("/stats") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: "customer"

            val stats = ticketService.getStats(tenantId, userId, userRole)
            call.respond(stats)
        }

        // =====================================================
        // TICKET LIST & CREATE
        // =====================================================

        // GET /api/v1/tickets - List tickets
        get {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: "customer"

            val filters = TicketFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                statuses = call.request.queryParameters["statuses"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() },
                categoryId = call.request.queryParameters["categoryId"]?.toIntOrNull(),
                orderId = call.request.queryParameters["orderId"]?.toLongOrNull(),
                search = call.request.queryParameters["search"],
                sortBy = call.request.queryParameters["sortBy"] ?: "lastMessageAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = ticketService.getTickets(tenantId, userId, userRole, filters)
            call.respond(response)
        }

        // POST /api/v1/tickets - Create new ticket
        post {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<CreateTicketRequest>()

            ticketService.createTicket(tenantId, userId, request)
                .onSuccess { ticket ->
                    call.respond(HttpStatusCode.Created, ticket)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // SINGLE TICKET
        // =====================================================

        // GET /api/v1/tickets/{id} - Get ticket with messages
        get("/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket ID"))

            ticketService.getTicket(id, tenantId, userId)
                .onSuccess { ticket ->
                    call.respond(ticket)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                    }
                }
        }

        // =====================================================
        // MESSAGES
        // =====================================================

        // POST /api/v1/tickets/{id}/messages - Send message
        post("/{id}/messages") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket ID"))

            val request = call.receive<SendMessageRequest>()

            ticketService.sendMessage(id, tenantId, userId, request)
                .onSuccess { message ->
                    call.respond(HttpStatusCode.Created, message)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
                }
        }

        // =====================================================
        // TICKET ACTIONS
        // =====================================================

        // POST /api/v1/tickets/{id}/close - Close ticket
        post("/{id}/close") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket ID"))

            ticketService.closeTicket(id, tenantId, userId)
                .onSuccess { ticket ->
                    call.respond(ticket)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
                }
        }

        // POST /api/v1/tickets/{id}/reopen - Reopen ticket
        post("/{id}/reopen") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket ID"))

            ticketService.reopenTicket(id, tenantId, userId)
                .onSuccess { ticket ->
                    call.respond(ticket)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                    }
                }
        }

        // POST /api/v1/tickets/{id}/read - Mark as read
        post("/{id}/read") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket ID"))

            ticketService.markAsRead(id, tenantId, userId)
                .onSuccess { marked ->
                    call.respond(mapOf("success" to marked))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // ORDER TICKETS
        // =====================================================

        // GET /api/v1/tickets/order/{orderId} - Get tickets for an order
        get("/order/{orderId}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: "customer"

            val orderId = call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))

            val tickets = ticketService.getTicketsByOrder(orderId, tenantId, userId, userRole)
            call.respond(mapOf("tickets" to tickets))
        }
    }
}

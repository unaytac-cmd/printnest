package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.WalletService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.walletRoutes() {
    val walletService: WalletService = GlobalContext.get().get()

    route("/wallet") {

        // =====================================================
        // BALANCE
        // =====================================================

        // GET /api/v1/wallet/balance - Get current wallet balance
        get("/balance") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val balance = walletService.getBalance(tenantId, userId)
            call.respond(balance)
        }

        // =====================================================
        // TRANSACTIONS
        // =====================================================

        // GET /api/v1/wallet/transactions - Get transaction history
        get("/transactions") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val filters = TransactionFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                type = call.request.queryParameters["type"]?.toIntOrNull(),
                startDate = call.request.queryParameters["startDate"],
                endDate = call.request.queryParameters["endDate"],
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val response = walletService.getTransactions(tenantId, userId, filters)
            call.respond(response)
        }

        // =====================================================
        // ADD FUNDS
        // =====================================================

        // POST /api/v1/wallet/add-funds - Initiate Stripe checkout to add funds
        post("/add-funds") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<AddFundsRequest>()

            walletService.initiateAddFunds(tenantId, userId, request)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to initiate payment"))
                    }
                }
        }

        // GET /api/v1/wallet/add-funds/complete - Complete Stripe payment (callback)
        get("/add-funds/complete") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val sessionId = call.request.queryParameters["session_id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID required"))

            walletService.completeAddFunds(tenantId, userId, sessionId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to complete payment"))
                    }
                }
        }

        // POST variant for complete (webhook style)
        post("/add-funds/complete") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<AddFundsCompleteRequest>()

            walletService.completeAddFunds(tenantId, userId, request.sessionId)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to error.message))
                        is IllegalStateException -> call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to complete payment"))
                    }
                }
        }

        // =====================================================
        // ADMIN: MANUAL CREDIT
        // =====================================================

        // POST /api/v1/wallet/admin/credit - Add credit manually (admin only)
        post("/admin/credit") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val adminUserId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: ""
            if (userRole != "owner" && userRole != "admin" && userRole != "producer") {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val request = call.receive<AddCreditRequest>()

            walletService.addCredit(tenantId, adminUserId, request)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // REFUNDS
        // =====================================================

        // POST /api/v1/wallet/refund - Process a refund (admin only)
        post("/refund") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val adminUserId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"] ?: ""
            if (userRole != "owner" && userRole != "admin" && userRole != "producer") {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val request = call.receive<WalletRefundRequest>()

            // TODO: Get the order and find the user who owns it
            // For now, we need userId in the request
            val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required for refund"))

            walletService.processRefund(tenantId, userId, request)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // TRANSACTION TYPES (reference)
        // =====================================================

        // GET /api/v1/wallet/transaction-types - Get list of transaction types
        get("/transaction-types") {
            val types = TransactionType.entries.map { type ->
                mapOf(
                    "code" to type.code,
                    "name" to type.name,
                    "label" to type.label
                )
            }
            call.respond(mapOf("types" to types))
        }
    }
}

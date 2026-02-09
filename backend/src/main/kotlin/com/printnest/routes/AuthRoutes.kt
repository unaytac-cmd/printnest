package com.printnest.routes

import com.printnest.domain.models.ChangePasswordRequest
import com.printnest.domain.models.LoginRequest
import com.printnest.domain.models.OnboardingCompleteRequest
import com.printnest.domain.models.RefreshTokenRequest
import com.printnest.domain.models.RegisterRequest
import com.printnest.domain.service.AuthService
import com.printnest.plugins.UserSession
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.core.context.GlobalContext

fun Route.authRoutes() {
    val authService: AuthService = GlobalContext.get().get()

    route("/auth") {

        // =====================================================
        // REGISTRATION
        // =====================================================

        // POST /api/v1/auth/register - Register new user and tenant
        post("/register") {
            val request = call.receive<RegisterRequest>()

            authService.register(request)
                .onSuccess { response ->
                    // Set session cookie
                    call.sessions.set(
                        UserSession(
                            userId = response.user.id.toString(),
                            email = response.user.email,
                            tenantId = response.tenant.id.toString(),
                            roles = listOf(response.user.role)
                        )
                    )
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    val statusCode = when {
                        error.message?.contains("already") == true -> HttpStatusCode.Conflict
                        error.message?.contains("Invalid") == true -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(statusCode, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // ONBOARDING COMPLETE
        // =====================================================

        // POST /api/v1/auth/onboarding/complete - Complete onboarding with all settings
        post("/onboarding/complete") {
            val request = call.receive<OnboardingCompleteRequest>()

            authService.completeOnboarding(request)
                .onSuccess { response ->
                    // Set session cookie
                    call.sessions.set(
                        UserSession(
                            userId = response.user.id.toString(),
                            email = response.user.email,
                            tenantId = response.tenant.id.toString(),
                            roles = listOf(response.user.role)
                        )
                    )
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    val statusCode = when {
                        error.message?.contains("already") == true -> HttpStatusCode.Conflict
                        error.message?.contains("Invalid") == true -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(statusCode, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // LOGIN
        // =====================================================

        // POST /api/v1/auth/login - Login user
        post("/login") {
            val request = call.receive<LoginRequest>()

            authService.login(request)
                .onSuccess { response ->
                    // Set session cookie
                    call.sessions.set(
                        UserSession(
                            userId = response.user.id.toString(),
                            email = response.user.email,
                            tenantId = response.tenant.id.toString(),
                            roles = listOf(response.user.role)
                        )
                    )
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // LOGOUT
        // =====================================================

        // POST /api/v1/auth/logout - Logout user
        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
        }

        // =====================================================
        // TOKEN REFRESH
        // =====================================================

        // POST /api/v1/auth/refresh - Refresh access token
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            authService.refreshToken(request.refreshToken)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // PASSWORD MANAGEMENT
        // =====================================================

        // POST /api/v1/auth/change-password - Change password (authenticated)
        post("/change-password") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<ChangePasswordRequest>()

            authService.changePassword(userId, tenantId, request)
                .onSuccess {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed successfully"))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                }
        }

        // =====================================================
        // VALIDATION ENDPOINTS
        // =====================================================

        // GET /api/v1/auth/check-email?email=xxx - Check if email is available
        get("/check-email") {
            val email = call.request.queryParameters["email"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email required"))

            val available = authService.checkEmailAvailability(email)
            call.respond(mapOf("available" to available))
        }

        // GET /api/v1/auth/check-subdomain?subdomain=xxx - Check if subdomain is available
        get("/check-subdomain") {
            val subdomain = call.request.queryParameters["subdomain"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Subdomain required"))

            val available = authService.checkSubdomainAvailability(subdomain)
            call.respond(mapOf("available" to available))
        }

        // =====================================================
        // CURRENT USER
        // =====================================================

        // GET /api/v1/auth/me - Get current user info
        get("/me") {
            val session = call.sessions.get<UserSession>()
            if (session == null || session.isExpired()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))
                return@get
            }

            call.respond(
                mapOf(
                    "userId" to session.userId,
                    "email" to session.email,
                    "tenantId" to session.tenantId,
                    "roles" to session.roles
                )
            )
        }
    }
}

package com.printnest.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.printnest.domain.models.ServicePrincipal
import com.printnest.integrations.interservice.InterServiceAuth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.*

private val IS_PRODUCTION = System.getenv("KTOR_ENV") == "production"
private val JWT_SECRET: String = System.getenv("JWT_SECRET")
    ?: if (IS_PRODUCTION) throw IllegalStateException("JWT_SECRET must be set in production")
       else "dev-secret-key-DO-NOT-USE-IN-PRODUCTION"
private val JWT_ISSUER = System.getenv("JWT_ISSUER") ?: "printnest"
private val JWT_AUDIENCE = System.getenv("JWT_AUDIENCE") ?: "printnest-users"
private val SESSION_SECRET: String = System.getenv("SESSION_SECRET")
    ?: if (IS_PRODUCTION) throw IllegalStateException("SESSION_SECRET must be set in production")
       else "dev-session-secret-DO-NOT-USE-IN-PRODUCTION"

/**
 * Configure authentication for the application.
 */
fun Application.configureAuthentication() {
    val jwtVerifier = JWT
        .require(Algorithm.HMAC256(JWT_SECRET))
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE)
        .build()

    // Generate proper hex keys from secret
    val encryptKey = SESSION_SECRET.toByteArray().copyOf(16)
    val signKey = SESSION_SECRET.reversed().toByteArray().copyOf(16)

    install(Sessions) {
        cookie<UserSession>("printnest_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 604800 // 7 days
            cookie.httpOnly = true
            cookie.secure = IS_PRODUCTION // HTTPS only in production
            cookie.extensions["SameSite"] = if (IS_PRODUCTION) "Strict" else "Lax"

            transform(
                SessionTransportTransformerEncrypt(encryptKey, signKey)
            )
        }
    }

    install(Authentication) {
        // JWT Authentication
        jwt("jwt") {
            realm = "PrintNest API"
            verifier(jwtVerifier)

            validate { credential ->
                val payload = credential.payload

                val userId = payload.getClaim("userId").asString()
                val email = payload.getClaim("email").asString()

                if (userId.isNullOrBlank() || email.isNullOrBlank()) {
                    return@validate null
                }

                JWTPrincipal(payload)
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthErrorResponse(
                        error = "invalid_token",
                        message = "Token is invalid or has expired"
                    )
                )
            }
        }

        // Session Authentication
        session<UserSession>("session") {
            validate { session ->
                if (session.isExpired()) {
                    return@validate null
                }
                session
            }

            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthErrorResponse(
                        error = "session_invalid",
                        message = "Session is invalid or has expired"
                    )
                )
            }
        }

        // API Key Authentication (for webhooks)
        bearer("api-key") {
            realm = "PrintNest API"
            authenticate { tokenCredential ->
                if (tokenCredential.token.isNotBlank()) {
                    ApiKeyPrincipal(apiKey = tokenCredential.token)
                } else {
                    null
                }
            }
        }

        // Inter-service JWT authentication
        bearer("inter-service") {
            realm = "PrintNest Inter-Service"
            authenticate { tokenCredential ->
                val interServiceAuth: InterServiceAuth by inject()
                val principal = interServiceAuth.verifyServiceToken(tokenCredential.token)

                if (principal != null && !principal.isExpired()) {
                    principal
                } else {
                    null
                }
            }
        }

        // Simple internal API key authentication for internal services
        bearer("internal-api-key") {
            realm = "PrintNest Internal"
            authenticate { tokenCredential ->
                val internalToken = System.getenv("INTERNAL_API_TOKEN")
                    ?: if (IS_PRODUCTION) null else "dev-internal-token"

                if (internalToken != null && tokenCredential.token == internalToken) {
                    InternalServicePrincipal(serviceName = "internal")
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Principal for simple internal API key authentication.
 */
data class InternalServicePrincipal(
    val serviceName: String
) : Principal

/**
 * User session data.
 */
@Serializable
data class UserSession(
    val userId: String,
    val email: String,
    val tenantId: String,
    val roles: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
) : Principal {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}

/**
 * API Key principal.
 */
data class ApiKeyPrincipal(
    val apiKey: String
) : Principal

/**
 * Authentication error response.
 */
@Serializable
data class AuthErrorResponse(
    val error: String,
    val message: String
)

/**
 * JWT Token service for generating tokens.
 */
object JwtTokenService {
    private val algorithm = Algorithm.HMAC256(JWT_SECRET)

    fun generateAccessToken(
        userId: String,
        email: String,
        tenantId: String,
        roles: List<String> = emptyList()
    ): String {
        val now = Date()
        val expiration = Date(now.time + 15 * 60 * 1000) // 15 minutes

        return JWT.create()
            .withIssuer(JWT_ISSUER)
            .withAudience(JWT_AUDIENCE)
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("tenantId", tenantId)
            .withClaim("roles", roles)
            .withClaim("type", "access")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }

    fun generateRefreshToken(userId: String, tenantId: String): String {
        val now = Date()
        val expiration = Date(now.time + 7 * 24 * 60 * 60 * 1000) // 7 days

        return JWT.create()
            .withIssuer(JWT_ISSUER)
            .withAudience(JWT_AUDIENCE)
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("tenantId", tenantId)
            .withClaim("type", "refresh")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }

    fun generateTokenPair(
        userId: String,
        email: String,
        tenantId: String,
        roles: List<String> = emptyList()
    ): TokenPair {
        return TokenPair(
            accessToken = generateAccessToken(userId, email, tenantId, roles),
            refreshToken = generateRefreshToken(userId, tenantId),
            expiresIn = 900, // 15 minutes
            tokenType = "Bearer"
        )
    }
}

/**
 * Token pair response.
 */
@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

/**
 * Extension to get user ID from JWT principal.
 */
val JWTPrincipal.userId: String
    get() = payload.getClaim("userId").asString()

val JWTPrincipal.email: String
    get() = payload.getClaim("email").asString()

val JWTPrincipal.tenantId: String
    get() = payload.getClaim("tenantId").asString()

val JWTPrincipal.roles: List<String>
    get() = payload.getClaim("roles").asList(String::class.java) ?: emptyList()

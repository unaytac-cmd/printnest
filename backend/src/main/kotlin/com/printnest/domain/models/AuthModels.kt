package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// =====================================================
// REQUEST MODELS
// =====================================================

@Serializable
data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val storeName: String,
    val storeSlug: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class OnboardingCompleteRequest(
    // User info
    val fullName: String,
    val email: String,
    val password: String,

    // Store info
    val businessName: String,
    val subdomain: String,
    val customDomain: String? = null,

    // ShipStation
    val shipstationApiKey: String? = null,
    val shipstationApiSecret: String? = null,

    // Stripe
    val stripeSecretKey: String? = null,
    val stripePublishableKey: String? = null,
    val stripeWebhookSecret: String? = null,

    // Shipping
    val nestshipperApiKey: String? = null,
    val easypostApiKey: String? = null,

    // AWS S3
    val awsAccessKeyId: String? = null,
    val awsSecretAccessKey: String? = null,
    val awsRegion: String? = "us-east-1",
    val awsS3Bucket: String? = null,

    // Gangsheet Settings
    val gangsheetWidth: Double = 22.0,
    val gangsheetHeight: Double = 72.0,
    val gangsheetDpi: Int = 300,
    val gangsheetSpacing: Double = 0.25,
    val gangsheetBackgroundColor: String = "#FFFFFF",
    val gangsheetAutoArrange: Boolean = true
)

// =====================================================
// RESPONSE MODELS
// =====================================================

@Serializable
data class AuthResponse(
    val user: UserResponse,
    val tenant: TenantResponse,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 900 // 15 minutes
)

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val emailVerified: Boolean,
    val createdAt: String
)

@Serializable
data class TenantResponse(
    val id: Long,
    val name: String,
    val subdomain: String,
    val customDomain: String?,
    val status: Int,
    val createdAt: String
)

@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 900
)

// =====================================================
// INTERNAL MODELS (for AuthService only)
// =====================================================

data class AuthUser(
    val id: Long,
    val tenantId: Long,
    val email: String,
    val passwordHash: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val status: Int,
    val permissions: String,
    val totalCredit: BigDecimal,
    val emailVerified: Boolean,
    val lastLoginAt: String?,
    val createdAt: String,
    val updatedAt: String
)

data class AuthTenant(
    val id: Long,
    val subdomain: String,
    val name: String,
    val status: Int,
    val customDomain: String?,
    val stripeCustomerId: String?,
    val settings: String,
    val createdAt: String,
    val updatedAt: String
)

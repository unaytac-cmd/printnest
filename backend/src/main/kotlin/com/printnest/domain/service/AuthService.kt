package com.printnest.domain.service

import com.printnest.domain.models.AuthTenant
import com.printnest.domain.models.AuthUser
import com.printnest.domain.models.AuthResponse
import com.printnest.domain.models.ChangePasswordRequest
import com.printnest.domain.models.CreateCategoryRequest
import com.printnest.domain.models.LoginRequest
import com.printnest.domain.models.OnboardingCompleteRequest
import com.printnest.domain.models.RegisterRequest
import com.printnest.domain.models.TenantResponse
import com.printnest.domain.models.TokenRefreshResponse
import com.printnest.domain.models.UserResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.printnest.domain.repository.AuthRepository
import com.printnest.domain.repository.CategoryRepository
import com.printnest.plugins.JwtTokenService
import org.slf4j.LoggerFactory

class AuthService(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    // =====================================================
    // REGISTRATION
    // =====================================================

    fun register(request: RegisterRequest): Result<AuthResponse> {
        logger.info("Registration attempt for email: ${request.email}, store: ${request.storeSlug}")

        // Validate email format
        if (!isValidEmail(request.email)) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }

        // Validate password strength
        val passwordValidation = validatePassword(request.password)
        if (passwordValidation != null) {
            return Result.failure(IllegalArgumentException(passwordValidation))
        }

        // Validate subdomain format
        val subdomainValidation = validateSubdomain(request.storeSlug)
        if (subdomainValidation != null) {
            return Result.failure(IllegalArgumentException(subdomainValidation))
        }

        // Check if email is already taken
        if (!authRepository.isEmailAvailable(request.email)) {
            return Result.failure(IllegalArgumentException("Email is already registered"))
        }

        // Check if subdomain is already taken
        if (!authRepository.isSubdomainAvailable(request.storeSlug)) {
            return Result.failure(IllegalArgumentException("Store URL is already taken"))
        }

        return try {
            // Create tenant
            val defaultSettings = createDefaultSettings()
            val tenant = authRepository.createTenant(
                subdomain = request.storeSlug,
                name = request.storeName,
                settings = defaultSettings
            )
            logger.info("Created tenant: ${tenant.id} (${tenant.subdomain})")

            // Create user (owner)
            val passwordHash = authRepository.hashPassword(request.password)
            val user = authRepository.createUser(
                tenantId = tenant.id,
                email = request.email,
                passwordHash = passwordHash,
                firstName = request.firstName,
                lastName = request.lastName,
                role = "owner"
            )
            logger.info("Created user: ${user.id} (${user.email})")

            // Create default data for tenant
            createDefaultData(tenant.id)

            // Generate tokens
            val tokens = JwtTokenService.generateTokenPair(
                userId = user.id.toString(),
                email = user.email,
                tenantId = tenant.id.toString(),
                roles = listOf(user.role)
            )

            Result.success(
                AuthResponse(
                    user = user.toResponse(),
                    tenant = tenant.toResponse(),
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn
                )
            )
        } catch (e: Exception) {
            logger.error("Registration failed for ${request.email}", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // ONBOARDING COMPLETE
    // =====================================================

    fun completeOnboarding(request: OnboardingCompleteRequest): Result<AuthResponse> {
        logger.info("Onboarding completion for email: ${request.email}, store: ${request.subdomain}")

        // Parse full name
        val nameParts = request.fullName.trim().split(" ", limit = 2)
        val firstName = nameParts[0]
        val lastName = if (nameParts.size > 1) nameParts[1] else ""

        // Validate email format
        if (!isValidEmail(request.email)) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }

        // Validate password strength
        val passwordValidation = validatePassword(request.password)
        if (passwordValidation != null) {
            return Result.failure(IllegalArgumentException(passwordValidation))
        }

        // Validate subdomain format
        val subdomainValidation = validateSubdomain(request.subdomain)
        if (subdomainValidation != null) {
            return Result.failure(IllegalArgumentException(subdomainValidation))
        }

        // Check if email is already taken
        if (!authRepository.isEmailAvailable(request.email)) {
            return Result.failure(IllegalArgumentException("Email is already registered"))
        }

        // Check if subdomain is already taken
        if (!authRepository.isSubdomainAvailable(request.subdomain)) {
            return Result.failure(IllegalArgumentException("Store URL is already taken"))
        }

        return try {
            // Create settings JSON with all integration data
            val settings = createOnboardingSettings(request)

            // Create tenant
            val tenant = authRepository.createTenant(
                subdomain = request.subdomain,
                name = request.businessName,
                customDomain = request.customDomain,
                settings = settings
            )
            logger.info("Created tenant: ${tenant.id} (${tenant.subdomain})")

            // Create user (owner)
            val passwordHash = authRepository.hashPassword(request.password)
            val user = authRepository.createUser(
                tenantId = tenant.id,
                email = request.email,
                passwordHash = passwordHash,
                firstName = firstName,
                lastName = lastName,
                role = "owner"
            )
            logger.info("Created user: ${user.id} (${user.email})")

            // Create default data for tenant
            createDefaultData(tenant.id)

            // Generate tokens
            val tokens = JwtTokenService.generateTokenPair(
                userId = user.id.toString(),
                email = user.email,
                tenantId = tenant.id.toString(),
                roles = listOf(user.role)
            )

            Result.success(
                AuthResponse(
                    user = user.toResponse(),
                    tenant = tenant.toResponse(),
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn
                )
            )
        } catch (e: Exception) {
            logger.error("Onboarding failed for ${request.email}", e)
            Result.failure(e)
        }
    }

    private fun createOnboardingSettings(request: OnboardingCompleteRequest): String {
        val settings = mapOf(
            "shipstation" to mapOf(
                "apiKey" to request.shipstationApiKey,
                "apiSecret" to request.shipstationApiSecret,
                "isConnected" to (!request.shipstationApiKey.isNullOrBlank())
            ),
            "stripe" to mapOf(
                "publicKey" to request.stripePublishableKey,
                "secretKey" to request.stripeSecretKey,
                "webhookSecret" to request.stripeWebhookSecret,
                "isConnected" to (!request.stripeSecretKey.isNullOrBlank())
            ),
            "shipping" to mapOf(
                "nestshipperApiKey" to request.nestshipperApiKey,
                "easypostApiKey" to request.easypostApiKey
            ),
            "aws" to mapOf(
                "accessKeyId" to request.awsAccessKeyId,
                "secretAccessKey" to request.awsSecretAccessKey,
                "region" to request.awsRegion,
                "s3Bucket" to request.awsS3Bucket
            ),
            "gangsheet" to mapOf(
                "width" to request.gangsheetWidth,
                "height" to request.gangsheetHeight,
                "dpi" to request.gangsheetDpi,
                "spacing" to request.gangsheetSpacing,
                "backgroundColor" to request.gangsheetBackgroundColor,
                "autoArrange" to request.gangsheetAutoArrange
            ),
            "notifications" to mapOf(
                "emailOnNewOrder" to true,
                "emailOnShipment" to true
            ),
            "currency" to "USD",
            "timezone" to "America/New_York"
        )

        return Json.encodeToString(settings)
    }

    // =====================================================
    // LOGIN
    // =====================================================

    fun login(request: LoginRequest): Result<AuthResponse> {
        logger.info("Login attempt for email: ${request.email}")

        // Find user by email
        val user = authRepository.findUserByEmail(request.email)
        if (user == null) {
            logger.warn("Login failed: User not found for ${request.email}")
            return Result.failure(IllegalArgumentException("Invalid email or password"))
        }

        // Check user status
        if (user.status != 1) {
            logger.warn("Login failed: User account is disabled for ${request.email}")
            return Result.failure(IllegalArgumentException("Account is disabled"))
        }

        // Verify password
        if (!authRepository.verifyPassword(request.password, user.passwordHash)) {
            logger.warn("Login failed: Invalid password for ${request.email}")
            return Result.failure(IllegalArgumentException("Invalid email or password"))
        }

        // Get tenant
        val tenant = authRepository.findTenantById(user.tenantId)
        if (tenant == null) {
            logger.error("Login failed: Tenant not found for user ${user.id}")
            return Result.failure(IllegalArgumentException("Account configuration error"))
        }

        // Check tenant status
        if (tenant.status != 1) {
            logger.warn("Login failed: Tenant is disabled for ${request.email}")
            return Result.failure(IllegalArgumentException("Store is disabled"))
        }

        // Update last login
        authRepository.updateLastLogin(user.id)

        // Generate tokens
        val tokens = JwtTokenService.generateTokenPair(
            userId = user.id.toString(),
            email = user.email,
            tenantId = tenant.id.toString(),
            roles = listOf(user.role)
        )

        logger.info("Login successful for ${request.email}")

        return Result.success(
            AuthResponse(
                user = user.toResponse(),
                tenant = tenant.toResponse(),
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn
            )
        )
    }

    // =====================================================
    // TOKEN REFRESH
    // =====================================================

    fun refreshToken(refreshToken: String): Result<TokenRefreshResponse> {
        // In production, you would verify the refresh token from database/redis
        // For now, we'll decode and re-issue
        return try {
            // Decode refresh token to get user info
            val decoded = com.auth0.jwt.JWT.decode(refreshToken)
            val userId = decoded.getClaim("userId").asString()?.toLongOrNull()
            val tenantId = decoded.getClaim("tenantId").asString()?.toLongOrNull()

            if (userId == null || tenantId == null) {
                return Result.failure(IllegalArgumentException("Invalid refresh token"))
            }

            // Verify user still exists and is active
            val user = authRepository.findUserByIdAndTenant(userId, tenantId)
            if (user == null || user.status != 1) {
                return Result.failure(IllegalArgumentException("User not found or disabled"))
            }

            // Generate new tokens
            val tokens = JwtTokenService.generateTokenPair(
                userId = user.id.toString(),
                email = user.email,
                tenantId = tenantId.toString(),
                roles = listOf(user.role)
            )

            Result.success(
                TokenRefreshResponse(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn
                )
            )
        } catch (e: Exception) {
            logger.error("Token refresh failed", e)
            Result.failure(IllegalArgumentException("Invalid refresh token"))
        }
    }

    // =====================================================
    // PASSWORD MANAGEMENT
    // =====================================================

    fun changePassword(userId: Long, tenantId: Long, request: ChangePasswordRequest): Result<Boolean> {
        val user = authRepository.findUserByIdAndTenant(userId, tenantId)
        if (user == null) {
            return Result.failure(IllegalArgumentException("User not found"))
        }

        // Verify current password
        if (!authRepository.verifyPassword(request.currentPassword, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Current password is incorrect"))
        }

        // Validate new password
        val passwordValidation = validatePassword(request.newPassword)
        if (passwordValidation != null) {
            return Result.failure(IllegalArgumentException(passwordValidation))
        }

        // Update password
        val newHash = authRepository.hashPassword(request.newPassword)
        authRepository.updatePassword(userId, newHash)

        logger.info("Password changed for user $userId")
        return Result.success(true)
    }

    // =====================================================
    // UTILITIES
    // =====================================================

    fun checkSubdomainAvailability(subdomain: String): Boolean {
        val validation = validateSubdomain(subdomain)
        if (validation != null) return false
        return authRepository.isSubdomainAvailable(subdomain)
    }

    fun checkEmailAvailability(email: String): Boolean {
        if (!isValidEmail(email)) return false
        return authRepository.isEmailAvailable(email)
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (!password.any { it.isUpperCase() }) {
            return "Password must contain at least one uppercase letter"
        }
        if (!password.any { it.isLowerCase() }) {
            return "Password must contain at least one lowercase letter"
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least one number"
        }
        return null
    }

    private fun validateSubdomain(subdomain: String): String? {
        if (subdomain.length < 3) {
            return "Store URL must be at least 3 characters"
        }
        if (subdomain.length > 63) {
            return "Store URL must be less than 63 characters"
        }
        if (!subdomain.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$"))) {
            return "Store URL can only contain lowercase letters, numbers, and hyphens"
        }
        // Reserved subdomains
        val reserved = listOf("admin", "api", "www", "app", "mail", "ftp", "test", "dev", "staging", "support", "help")
        if (subdomain in reserved) {
            return "This store URL is reserved"
        }
        return null
    }

    private fun createDefaultSettings(): String {
        return """
        {
            "gangsheet": {
                "width": 22,
                "height": 60,
                "dpi": 300,
                "spacing": 0.25,
                "backgroundColor": "#FFFFFF",
                "autoArrange": true
            },
            "notifications": {
                "emailOnNewOrder": true,
                "emailOnShipment": true
            },
            "currency": "USD",
            "timezone": "America/New_York"
        }
        """.trimIndent()
    }

    private fun createDefaultData(tenantId: Long) {
        try {
            // Create default categories
            val defaultCategories = listOf(
                CreateCategoryRequest(name = "T-Shirts", description = "T-Shirt products", isHeavy = false),
                CreateCategoryRequest(name = "Hoodies", description = "Hoodie products", isHeavy = true),
                CreateCategoryRequest(name = "Accessories", description = "Accessories", isHeavy = false)
            )

            defaultCategories.forEach { category ->
                categoryRepository.create(tenantId, category)
            }

            logger.info("Created default data for tenant $tenantId")
        } catch (e: Exception) {
            logger.warn("Failed to create default data for tenant $tenantId", e)
            // Don't fail registration if default data creation fails
        }
    }

    // =====================================================
    // EXTENSION FUNCTIONS
    // =====================================================

    private fun AuthUser.toResponse(): UserResponse = UserResponse(
        id = this.id,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        role = this.role,
        emailVerified = this.emailVerified,
        createdAt = this.createdAt
    )

    private fun AuthTenant.toResponse(): TenantResponse = TenantResponse(
        id = this.id,
        name = this.name,
        subdomain = this.subdomain,
        customDomain = this.customDomain,
        status = this.status,
        createdAt = this.createdAt
    )
}

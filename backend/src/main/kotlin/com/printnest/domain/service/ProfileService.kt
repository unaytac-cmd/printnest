package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.ProductRepository
import com.printnest.domain.repository.ProfileRepository
import java.math.BigDecimal
import java.math.RoundingMode

class ProfileService(
    private val profileRepository: ProfileRepository,
    private val productRepository: ProductRepository
) {

    // =====================================================
    // SHIPPING PROFILES
    // =====================================================

    fun getShippingProfiles(tenantId: Long, includeInactive: Boolean = false): List<ShippingProfile> {
        return profileRepository.findAllShippingProfiles(tenantId, includeInactive)
    }

    fun getShippingProfile(id: Long, tenantId: Long, withMethods: Boolean = false): ShippingProfile? {
        return if (withMethods) {
            profileRepository.findShippingProfileWithMethods(id, tenantId)
        } else {
            profileRepository.findShippingProfileById(id, tenantId)
        }
    }

    fun getDefaultShippingProfile(tenantId: Long): ShippingProfile? {
        return profileRepository.findDefaultShippingProfile(tenantId)
    }

    fun createShippingProfile(tenantId: Long, request: CreateShippingProfileRequest): Result<ShippingProfile> {
        val profile = profileRepository.createShippingProfile(tenantId, request)
        return Result.success(profile)
    }

    fun updateShippingProfile(id: Long, tenantId: Long, request: UpdateShippingProfileRequest): Result<ShippingProfile> {
        val updated = profileRepository.updateShippingProfile(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Shipping profile not found"))
        return Result.success(updated)
    }

    fun deleteShippingProfile(id: Long, tenantId: Long): Result<Boolean> {
        val profile = profileRepository.findShippingProfileById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Shipping profile not found"))

        if (profile.isDefault) {
            return Result.failure(IllegalStateException("Cannot delete default shipping profile"))
        }

        return Result.success(profileRepository.deleteShippingProfile(id, tenantId))
    }

    // =====================================================
    // SHIPPING METHODS
    // =====================================================

    fun getShippingMethods(tenantId: Long, profileId: Long? = null): List<ShippingMethod> {
        return profileRepository.findAllShippingMethods(tenantId, profileId)
    }

    fun getShippingMethod(id: Long, tenantId: Long): ShippingMethod? {
        return profileRepository.findShippingMethodById(id, tenantId)
    }

    fun createShippingMethod(tenantId: Long, request: CreateShippingMethodRequest): Result<ShippingMethod> {
        val method = profileRepository.createShippingMethod(tenantId, request)
        return Result.success(method)
    }

    fun updateShippingMethod(id: Long, tenantId: Long, request: UpdateShippingMethodRequest): Result<ShippingMethod> {
        val updated = profileRepository.updateShippingMethod(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Shipping method not found"))
        return Result.success(updated)
    }

    fun deleteShippingMethod(id: Long, tenantId: Long): Result<Boolean> {
        return Result.success(profileRepository.deleteShippingMethod(id, tenantId))
    }

    fun createDefaultShippingMethods(tenantId: Long, profileId: Long): List<ShippingMethod> {
        return profileRepository.createDefaultShippingMethods(tenantId, profileId)
    }

    // =====================================================
    // PRICE PROFILES
    // =====================================================

    fun getPriceProfiles(tenantId: Long, includeInactive: Boolean = false): List<PriceProfileFull> {
        return profileRepository.findAllPriceProfiles(tenantId, includeInactive)
    }

    fun getPriceProfile(id: Long, tenantId: Long, withProducts: Boolean = false): PriceProfileFull? {
        return if (withProducts) {
            profileRepository.findPriceProfileWithProducts(id, tenantId)
        } else {
            profileRepository.findPriceProfileById(id, tenantId)
        }
    }

    fun getDefaultPriceProfile(tenantId: Long): PriceProfileFull? {
        return profileRepository.findDefaultPriceProfile(tenantId)
    }

    fun createPriceProfile(tenantId: Long, request: CreatePriceProfileRequest): Result<PriceProfileFull> {
        val profile = profileRepository.createPriceProfile(tenantId, request)
        return Result.success(profile)
    }

    fun updatePriceProfile(id: Long, tenantId: Long, request: UpdatePriceProfileRequest): Result<PriceProfileFull> {
        val updated = profileRepository.updatePriceProfile(id, tenantId, request)
            ?: return Result.failure(IllegalArgumentException("Price profile not found"))
        return Result.success(updated)
    }

    fun deletePriceProfile(id: Long, tenantId: Long): Result<Boolean> {
        val profile = profileRepository.findPriceProfileById(id, tenantId)
            ?: return Result.failure(IllegalArgumentException("Price profile not found"))

        if (profile.isDefault) {
            return Result.failure(IllegalStateException("Cannot delete default price profile"))
        }

        return Result.success(profileRepository.deletePriceProfile(id, tenantId))
    }

    // =====================================================
    // PRICE PROFILE PRODUCTS
    // =====================================================

    fun getPriceProfileProducts(profileId: Long, tenantId: Long): List<PriceProfileProduct> {
        return profileRepository.findPriceProfileProducts(profileId, tenantId)
    }

    fun addPriceProfileProduct(profileId: Long, tenantId: Long, request: CreatePriceProfileProductRequest): Result<PriceProfileProduct> {
        // Verify variant exists
        val variant = productRepository.findVariantById(request.variantId, tenantId)
            ?: return Result.failure(IllegalArgumentException("Variant not found"))

        val product = profileRepository.upsertPriceProfileProduct(profileId, tenantId, request)
        return Result.success(product)
    }

    fun bulkAddPriceProfileProducts(profileId: Long, tenantId: Long, requests: List<CreatePriceProfileProductRequest>): Result<List<PriceProfileProduct>> {
        val products = profileRepository.bulkUpsertPriceProfileProducts(profileId, tenantId, requests)
        return Result.success(products)
    }

    fun removePriceProfileProduct(profileId: Long, variantId: Long, tenantId: Long): Result<Boolean> {
        return Result.success(profileRepository.deletePriceProfileProductByVariant(profileId, variantId, tenantId))
    }

    // =====================================================
    // SHIPPING CALCULATION
    // =====================================================

    fun calculateShipping(tenantId: Long, request: ShippingCalculationRequest): ShippingCalculationResult {
        val profile = request.shippingProfileId?.let {
            profileRepository.findShippingProfileById(it, tenantId)
        } ?: profileRepository.findDefaultShippingProfile(tenantId)

        if (profile == null) {
            return ShippingCalculationResult(
                totalShipping = BigDecimal.ZERO,
                breakdown = null
            )
        }

        return when (profile.profileType) {
            0 -> calculateQuantityBasedShipping(profile, request.items)
            1 -> calculateApiBasedShipping(profile, request)
            else -> ShippingCalculationResult(totalShipping = BigDecimal.ZERO)
        }
    }

    private fun calculateQuantityBasedShipping(profile: ShippingProfile, items: List<ShippingItem>): ShippingCalculationResult {
        val pricing = profile.profilePricing ?: return ShippingCalculationResult(totalShipping = BigDecimal.ZERO)

        val heavyItems = items.filter { it.isHeavy }
        val lightItems = items.filter { !it.isHeavy }

        val heavyCount = heavyItems.sumOf { it.quantity }
        val lightCount = lightItems.sumOf { it.quantity }

        var total = BigDecimal.ZERO

        // Calculate heavy items
        if (heavyCount > 0) {
            total += pricing.heavyFirst ?: BigDecimal.ZERO
            if (heavyCount > 1) {
                total += pricing.heavySecond ?: BigDecimal.ZERO
            }
            if (heavyCount > 2) {
                val additionalItems = heavyCount - 2
                total += (pricing.heavyThird ?: BigDecimal.ZERO) * BigDecimal(additionalItems)
            }
        }

        // Calculate light items
        if (lightCount > 0) {
            total += pricing.lightFirst ?: BigDecimal.ZERO
            if (lightCount > 1) {
                total += pricing.lightSecond ?: BigDecimal.ZERO
            }
            if (lightCount > 2) {
                val additionalItems = lightCount - 2
                total += (pricing.lightThird ?: BigDecimal.ZERO) * BigDecimal(additionalItems)
            }
        }

        return ShippingCalculationResult(
            totalShipping = total.setScale(2, RoundingMode.HALF_UP),
            breakdown = ShippingBreakdown(
                profileType = "quantity-based",
                itemCount = heavyCount + lightCount,
                heavyItemCount = heavyCount,
                lightItemCount = lightCount,
                baseRate = total
            )
        )
    }

    private fun calculateApiBasedShipping(profile: ShippingProfile, request: ShippingCalculationRequest): ShippingCalculationResult {
        // TODO: Integrate with EasyPost API
        // For now, return placeholder
        val pricing = profile.profilePricing

        return ShippingCalculationResult(
            totalShipping = BigDecimal.ZERO,
            breakdown = ShippingBreakdown(
                profileType = "api-based",
                itemCount = request.items.sumOf { it.quantity },
                heavyItemCount = 0,
                lightItemCount = 0,
                baseRate = BigDecimal.ZERO,
                markup = pricing?.differenceAmount ?: BigDecimal.ZERO
            )
        )
    }

    // =====================================================
    // PRICE CALCULATION
    // =====================================================

    fun calculatePrice(tenantId: Long, request: PriceCalculationRequest): PriceCalculationResult {
        val profile = request.priceProfileId?.let {
            profileRepository.findPriceProfileWithProducts(it, tenantId)
        } ?: profileRepository.findDefaultPriceProfile(tenantId)

        var subtotal = BigDecimal.ZERO
        var totalDiscount = BigDecimal.ZERO
        var totalModifications = BigDecimal.ZERO
        var totalStitchCharges = BigDecimal.ZERO
        var totalGiftNotes = BigDecimal.ZERO

        val itemDetails = request.items.map { item ->
            val variant = productRepository.findVariantById(item.variantId, tenantId)
            val basePrice = variant?.price ?: BigDecimal.ZERO

            // Calculate modification price
            val modificationPrice = BigDecimal.ZERO // TODO: Calculate from modifications

            // Calculate stitch charges
            val stitchPrice = if (item.stitchCount != null && item.stitchCount > 0 && profile != null) {
                profile.stitchPrice * BigDecimal(item.stitchCount)
            } else {
                BigDecimal.ZERO
            }

            // Gift note price
            val giftNotePrice = if (item.hasGiftNote && profile != null) {
                profile.giftNotePrice
            } else {
                BigDecimal.ZERO
            }

            // Calculate discount
            val discount = calculateDiscount(profile, item.variantId, basePrice + modificationPrice)

            // Line total
            val lineTotal = ((basePrice + modificationPrice + stitchPrice + giftNotePrice - discount) * BigDecimal(item.quantity))
                .setScale(2, RoundingMode.HALF_UP)

            subtotal += basePrice * BigDecimal(item.quantity)
            totalDiscount += discount * BigDecimal(item.quantity)
            totalModifications += modificationPrice * BigDecimal(item.quantity)
            totalStitchCharges += stitchPrice * BigDecimal(item.quantity)
            totalGiftNotes += giftNotePrice * BigDecimal(item.quantity)

            PriceItemDetail(
                variantId = item.variantId,
                quantity = item.quantity,
                basePrice = basePrice,
                modificationPrice = modificationPrice,
                stitchPrice = stitchPrice,
                giftNotePrice = giftNotePrice,
                discount = discount,
                lineTotal = lineTotal
            )
        }

        val total = subtotal + totalModifications + totalStitchCharges + totalGiftNotes - totalDiscount

        return PriceCalculationResult(
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP),
            totalDiscount = totalDiscount.setScale(2, RoundingMode.HALF_UP),
            totalModifications = totalModifications.setScale(2, RoundingMode.HALF_UP),
            totalStitchCharges = totalStitchCharges.setScale(2, RoundingMode.HALF_UP),
            totalGiftNotes = totalGiftNotes.setScale(2, RoundingMode.HALF_UP),
            total = total.setScale(2, RoundingMode.HALF_UP),
            itemDetails = itemDetails
        )
    }

    private fun calculateDiscount(profile: PriceProfileFull?, variantId: Long, price: BigDecimal): BigDecimal {
        if (profile == null) return BigDecimal.ZERO

        // Check for variant-specific override
        val variantOverride = profile.productOverrides.find { it.variantId == variantId }

        val discountType = variantOverride?.discountType ?: profile.discountType
        val discountAmount = variantOverride?.discountAmount ?: profile.discountAmount

        return when (discountType) {
            0 -> { // Percentage
                (price * discountAmount / BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            }
            1 -> { // Dollar amount
                discountAmount
            }
            else -> BigDecimal.ZERO
        }
    }

    // =====================================================
    // PROFILE ASSIGNMENT
    // =====================================================

    fun getProfilesForUser(userId: Long, tenantId: Long): UserProfiles {
        // TODO: Get from user record
        val defaultPrice = profileRepository.findDefaultPriceProfile(tenantId)
        val defaultShipping = profileRepository.findDefaultShippingProfile(tenantId)

        return UserProfiles(
            priceProfile = defaultPrice,
            shippingProfile = defaultShipping
        )
    }
}

@kotlinx.serialization.Serializable
data class UserProfiles(
    val priceProfile: PriceProfileFull? = null,
    val shippingProfile: ShippingProfile? = null
)

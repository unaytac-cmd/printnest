package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class ProfileRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // SHIPPING PROFILES
    // =====================================================

    fun findAllShippingProfiles(tenantId: Long, includeInactive: Boolean = false): List<ShippingProfile> = transaction {
        var query = ShippingProfiles.selectAll()
            .where { ShippingProfiles.tenantId eq tenantId }

        if (!includeInactive) {
            query = query.andWhere { ShippingProfiles.status eq 1 }
        }

        query.orderBy(ShippingProfiles.createdAt, SortOrder.DESC)
            .map { it.toShippingProfile() }
    }

    fun findShippingProfileById(id: Long, tenantId: Long): ShippingProfile? = transaction {
        ShippingProfiles.selectAll()
            .where { (ShippingProfiles.id eq id) and (ShippingProfiles.tenantId eq tenantId) }
            .singleOrNull()
            ?.toShippingProfile()
    }

    fun findShippingProfileWithMethods(id: Long, tenantId: Long): ShippingProfile? = transaction {
        val profile = findShippingProfileById(id, tenantId) ?: return@transaction null

        val methods = ShippingMethods.selectAll()
            .where { (ShippingMethods.shippingProfileId eq id) and (ShippingMethods.tenantId eq tenantId) and (ShippingMethods.status eq 1) }
            .orderBy(ShippingMethods.sortOrder, SortOrder.ASC)
            .map { it.toShippingMethod() }

        profile.copy(methods = methods)
    }

    fun findDefaultShippingProfile(tenantId: Long): ShippingProfile? = transaction {
        ShippingProfiles.selectAll()
            .where { (ShippingProfiles.tenantId eq tenantId) and (ShippingProfiles.isDefault eq true) and (ShippingProfiles.status eq 1) }
            .singleOrNull()
            ?.toShippingProfile()
    }

    fun createShippingProfile(tenantId: Long, request: CreateShippingProfileRequest): ShippingProfile = transaction {
        // If this is default, remove default from others
        if (request.isDefault) {
            ShippingProfiles.update(
                where = { (ShippingProfiles.tenantId eq tenantId) and (ShippingProfiles.isDefault eq true) }
            ) {
                it[isDefault] = false
            }
        }

        val pricingJson = request.profilePricing?.let { json.encodeToString(ShippingProfilePricing.serializer(), it) } ?: "{}"

        val id = ShippingProfiles.insertAndGetId {
            it[this.tenantId] = tenantId
            it[name] = request.name
            it[profileType] = request.profileType
            it[profilePricing] = pricingJson
            it[isDefault] = request.isDefault
        }

        findShippingProfileById(id.value, tenantId)!!
    }

    fun updateShippingProfile(id: Long, tenantId: Long, request: UpdateShippingProfileRequest): ShippingProfile? = transaction {
        // If setting as default, remove default from others
        if (request.isDefault == true) {
            ShippingProfiles.update(
                where = { (ShippingProfiles.tenantId eq tenantId) and (ShippingProfiles.isDefault eq true) and (ShippingProfiles.id neq id) }
            ) {
                it[isDefault] = false
            }
        }

        val updated = ShippingProfiles.update(
            where = { (ShippingProfiles.id eq id) and (ShippingProfiles.tenantId eq tenantId) }
        ) {
            request.name?.let { name -> it[ShippingProfiles.name] = name }
            request.profileType?.let { type -> it[profileType] = type }
            request.profilePricing?.let { pricing ->
                it[profilePricing] = json.encodeToString(ShippingProfilePricing.serializer(), pricing)
            }
            request.isDefault?.let { default -> it[isDefault] = default }
            request.status?.let { status -> it[ShippingProfiles.status] = status }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findShippingProfileById(id, tenantId) else null
    }

    fun deleteShippingProfile(id: Long, tenantId: Long): Boolean = transaction {
        ShippingProfiles.update(
            where = { (ShippingProfiles.id eq id) and (ShippingProfiles.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // SHIPPING METHODS
    // =====================================================

    fun findAllShippingMethods(tenantId: Long, profileId: Long? = null): List<ShippingMethod> = transaction {
        var query = ShippingMethods.selectAll()
            .where { (ShippingMethods.tenantId eq tenantId) and (ShippingMethods.status eq 1) }

        profileId?.let {
            query = query.andWhere { ShippingMethods.shippingProfileId eq it }
        }

        query.orderBy(ShippingMethods.sortOrder, SortOrder.ASC)
            .map { it.toShippingMethod() }
    }

    fun findShippingMethodById(id: Long, tenantId: Long): ShippingMethod? = transaction {
        ShippingMethods.selectAll()
            .where { (ShippingMethods.id eq id) and (ShippingMethods.tenantId eq tenantId) }
            .singleOrNull()
            ?.toShippingMethod()
    }

    fun createShippingMethod(tenantId: Long, request: CreateShippingMethodRequest): ShippingMethod = transaction {
        val processingInfoJson = request.processingInfo?.let { json.encodeToString(ProcessingInfo.serializer(), it) } ?: "{}"

        val id = ShippingMethods.insertAndGetId {
            it[this.tenantId] = tenantId
            it[shippingProfileId] = request.shippingProfileId
            it[name] = request.name
            it[description] = request.description
            it[apiMethod] = request.apiMethod
            it[isInternational] = request.isInternational
            it[extraFee] = request.extraFee
            it[processingInfo] = processingInfoJson
            it[sortOrder] = request.sortOrder
        }

        findShippingMethodById(id.value, tenantId)!!
    }

    fun updateShippingMethod(id: Long, tenantId: Long, request: UpdateShippingMethodRequest): ShippingMethod? = transaction {
        val updated = ShippingMethods.update(
            where = { (ShippingMethods.id eq id) and (ShippingMethods.tenantId eq tenantId) }
        ) {
            request.shippingProfileId?.let { profileId -> it[shippingProfileId] = profileId }
            request.name?.let { name -> it[ShippingMethods.name] = name }
            request.description?.let { desc -> it[description] = desc }
            request.apiMethod?.let { method -> it[apiMethod] = method }
            request.isInternational?.let { intl -> it[isInternational] = intl }
            request.extraFee?.let { fee -> it[extraFee] = fee }
            request.processingInfo?.let { info ->
                it[processingInfo] = json.encodeToString(ProcessingInfo.serializer(), info)
            }
            request.sortOrder?.let { order -> it[sortOrder] = order }
            request.status?.let { status -> it[ShippingMethods.status] = status }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findShippingMethodById(id, tenantId) else null
    }

    fun deleteShippingMethod(id: Long, tenantId: Long): Boolean = transaction {
        ShippingMethods.update(
            where = { (ShippingMethods.id eq id) and (ShippingMethods.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun createDefaultShippingMethods(tenantId: Long, profileId: Long): List<ShippingMethod> = transaction {
        DefaultShippingMethods.allDomestic.mapIndexed { index, template ->
            createShippingMethod(tenantId, CreateShippingMethodRequest(
                shippingProfileId = profileId,
                name = template.name,
                apiMethod = template.apiMethod,
                isInternational = template.isInternational,
                processingInfo = template.processingInfo,
                sortOrder = index
            ))
        }
    }

    // =====================================================
    // PRICE PROFILES
    // =====================================================

    fun findAllPriceProfiles(tenantId: Long, includeInactive: Boolean = false): List<PriceProfileFull> = transaction {
        var query = PriceProfiles.selectAll()
            .where { PriceProfiles.tenantId eq tenantId }

        if (!includeInactive) {
            query = query.andWhere { PriceProfiles.status eq 1 }
        }

        query.orderBy(PriceProfiles.createdAt, SortOrder.DESC)
            .map { it.toPriceProfileFull() }
    }

    fun findPriceProfileById(id: Long, tenantId: Long): PriceProfileFull? = transaction {
        PriceProfiles.selectAll()
            .where { (PriceProfiles.id eq id) and (PriceProfiles.tenantId eq tenantId) }
            .singleOrNull()
            ?.toPriceProfileFull()
    }

    fun findPriceProfileWithProducts(id: Long, tenantId: Long): PriceProfileFull? = transaction {
        val profile = findPriceProfileById(id, tenantId) ?: return@transaction null

        val products = PriceProfileProducts.selectAll()
            .where { (PriceProfileProducts.priceProfileId eq id) and (PriceProfileProducts.tenantId eq tenantId) }
            .map { it.toPriceProfileProduct() }

        profile.copy(productOverrides = products)
    }

    fun findDefaultPriceProfile(tenantId: Long): PriceProfileFull? = transaction {
        PriceProfiles.selectAll()
            .where { (PriceProfiles.tenantId eq tenantId) and (PriceProfiles.isDefault eq true) and (PriceProfiles.status eq 1) }
            .singleOrNull()
            ?.toPriceProfileFull()
    }

    fun createPriceProfile(tenantId: Long, request: CreatePriceProfileRequest): PriceProfileFull = transaction {
        // If this is default, remove default from others
        if (request.isDefault) {
            PriceProfiles.update(
                where = { (PriceProfiles.tenantId eq tenantId) and (PriceProfiles.isDefault eq true) }
            ) {
                it[isDefault] = false
            }
        }

        val id = PriceProfiles.insertAndGetId {
            it[this.tenantId] = tenantId
            it[name] = request.name
            it[profileType] = request.profileType
            it[discountType] = request.discountType
            it[discountAmount] = request.discountAmount
            it[giftNotePrice] = request.giftNotePrice
            it[stitchPrice] = request.stitchPrice
            it[digitizingPrice] = request.digitizingPrice
            it[gangsheetPrice] = request.gangsheetPrice
            it[uvGangsheetPrice] = request.uvGangsheetPrice
            it[isDefault] = request.isDefault
        }

        findPriceProfileById(id.value, tenantId)!!
    }

    fun updatePriceProfile(id: Long, tenantId: Long, request: UpdatePriceProfileRequest): PriceProfileFull? = transaction {
        // If setting as default, remove default from others
        if (request.isDefault == true) {
            PriceProfiles.update(
                where = { (PriceProfiles.tenantId eq tenantId) and (PriceProfiles.isDefault eq true) and (PriceProfiles.id neq id) }
            ) {
                it[isDefault] = false
            }
        }

        val updated = PriceProfiles.update(
            where = { (PriceProfiles.id eq id) and (PriceProfiles.tenantId eq tenantId) }
        ) {
            request.name?.let { name -> it[PriceProfiles.name] = name }
            request.profileType?.let { type -> it[profileType] = type }
            request.discountType?.let { type -> it[discountType] = type }
            request.discountAmount?.let { amount -> it[discountAmount] = amount }
            request.giftNotePrice?.let { price -> it[giftNotePrice] = price }
            request.stitchPrice?.let { price -> it[stitchPrice] = price }
            request.digitizingPrice?.let { price -> it[digitizingPrice] = price }
            request.gangsheetPrice?.let { price -> it[gangsheetPrice] = price }
            request.uvGangsheetPrice?.let { price -> it[uvGangsheetPrice] = price }
            request.isDefault?.let { default -> it[isDefault] = default }
            request.status?.let { status -> it[PriceProfiles.status] = status }
            it[updatedAt] = Instant.now()
        }

        if (updated > 0) findPriceProfileById(id, tenantId) else null
    }

    fun deletePriceProfile(id: Long, tenantId: Long): Boolean = transaction {
        PriceProfiles.update(
            where = { (PriceProfiles.id eq id) and (PriceProfiles.tenantId eq tenantId) }
        ) {
            it[status] = -1
            it[updatedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // PRICE PROFILE PRODUCTS (Variant Overrides)
    // =====================================================

    fun findPriceProfileProducts(profileId: Long, tenantId: Long): List<PriceProfileProduct> = transaction {
        PriceProfileProducts.selectAll()
            .where { (PriceProfileProducts.priceProfileId eq profileId) and (PriceProfileProducts.tenantId eq tenantId) }
            .map { it.toPriceProfileProduct() }
    }

    fun findPriceProfileProductByVariant(profileId: Long, variantId: Long, tenantId: Long): PriceProfileProduct? = transaction {
        PriceProfileProducts.selectAll()
            .where {
                (PriceProfileProducts.priceProfileId eq profileId) and
                (PriceProfileProducts.variantId eq variantId) and
                (PriceProfileProducts.tenantId eq tenantId)
            }
            .singleOrNull()
            ?.toPriceProfileProduct()
    }

    fun createPriceProfileProduct(profileId: Long, tenantId: Long, request: CreatePriceProfileProductRequest): PriceProfileProduct = transaction {
        val id = PriceProfileProducts.insertAndGetId {
            it[this.tenantId] = tenantId
            it[priceProfileId] = profileId
            it[variantId] = request.variantId
            it[discountType] = request.discountType
            it[discountAmount] = request.discountAmount
        }

        PriceProfileProducts.selectAll()
            .where { PriceProfileProducts.id eq id }
            .single()
            .toPriceProfileProduct()
    }

    fun upsertPriceProfileProduct(profileId: Long, tenantId: Long, request: CreatePriceProfileProductRequest): PriceProfileProduct = transaction {
        val existing = findPriceProfileProductByVariant(profileId, request.variantId, tenantId)

        if (existing != null) {
            PriceProfileProducts.update(
                where = { PriceProfileProducts.id eq existing.id }
            ) {
                it[discountType] = request.discountType
                it[discountAmount] = request.discountAmount
                it[updatedAt] = Instant.now()
            }
            findPriceProfileProductByVariant(profileId, request.variantId, tenantId)!!
        } else {
            createPriceProfileProduct(profileId, tenantId, request)
        }
    }

    fun bulkUpsertPriceProfileProducts(profileId: Long, tenantId: Long, requests: List<CreatePriceProfileProductRequest>): List<PriceProfileProduct> = transaction {
        requests.map { request ->
            upsertPriceProfileProduct(profileId, tenantId, request)
        }
    }

    fun deletePriceProfileProduct(id: Long, tenantId: Long): Boolean = transaction {
        PriceProfileProducts.deleteWhere {
            (PriceProfileProducts.id eq id) and (PriceProfileProducts.tenantId eq tenantId)
        } > 0
    }

    fun deletePriceProfileProductByVariant(profileId: Long, variantId: Long, tenantId: Long): Boolean = transaction {
        PriceProfileProducts.deleteWhere {
            (PriceProfileProducts.priceProfileId eq profileId) and
            (PriceProfileProducts.variantId eq variantId) and
            (PriceProfileProducts.tenantId eq tenantId)
        } > 0
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toShippingProfile(): ShippingProfile {
        val pricingJson = this[ShippingProfiles.profilePricing]
        val pricing = try {
            if (pricingJson.isNotEmpty() && pricingJson != "{}") {
                json.decodeFromString<ShippingProfilePricing>(pricingJson)
            } else null
        } catch (e: Exception) {
            null
        }

        return ShippingProfile(
            id = this[ShippingProfiles.id].value,
            tenantId = this[ShippingProfiles.tenantId].value,
            name = this[ShippingProfiles.name],
            profileType = this[ShippingProfiles.profileType],
            profilePricing = pricing,
            isDefault = this[ShippingProfiles.isDefault],
            status = this[ShippingProfiles.status],
            createdAt = this[ShippingProfiles.createdAt].toString(),
            updatedAt = this[ShippingProfiles.updatedAt].toString()
        )
    }

    private fun ResultRow.toShippingMethod(): ShippingMethod {
        val processingInfoJson = this[ShippingMethods.processingInfo]
        val processingInfo = try {
            if (processingInfoJson.isNotEmpty() && processingInfoJson != "{}") {
                json.decodeFromString<ProcessingInfo>(processingInfoJson)
            } else null
        } catch (e: Exception) {
            null
        }

        return ShippingMethod(
            id = this[ShippingMethods.id].value,
            tenantId = this[ShippingMethods.tenantId].value,
            shippingProfileId = this[ShippingMethods.shippingProfileId]?.value,
            name = this[ShippingMethods.name],
            description = this[ShippingMethods.description],
            apiMethod = this[ShippingMethods.apiMethod],
            isInternational = this[ShippingMethods.isInternational],
            extraFee = this[ShippingMethods.extraFee],
            processingInfo = processingInfo,
            sortOrder = this[ShippingMethods.sortOrder],
            status = this[ShippingMethods.status],
            createdAt = this[ShippingMethods.createdAt].toString(),
            updatedAt = this[ShippingMethods.updatedAt].toString()
        )
    }

    private fun ResultRow.toPriceProfileFull(): PriceProfileFull = PriceProfileFull(
        id = this[PriceProfiles.id].value,
        tenantId = this[PriceProfiles.tenantId].value,
        name = this[PriceProfiles.name],
        profileType = this[PriceProfiles.profileType],
        discountType = this[PriceProfiles.discountType],
        discountAmount = this[PriceProfiles.discountAmount],
        giftNotePrice = this[PriceProfiles.giftNotePrice],
        stitchPrice = this[PriceProfiles.stitchPrice],
        digitizingPrice = this[PriceProfiles.digitizingPrice],
        gangsheetPrice = this[PriceProfiles.gangsheetPrice],
        uvGangsheetPrice = this[PriceProfiles.uvGangsheetPrice],
        isDefault = this[PriceProfiles.isDefault],
        status = this[PriceProfiles.status],
        createdAt = this[PriceProfiles.createdAt].toString(),
        updatedAt = this[PriceProfiles.updatedAt].toString()
    )

    private fun ResultRow.toPriceProfileProduct(): PriceProfileProduct = PriceProfileProduct(
        id = this[PriceProfileProducts.id].value,
        tenantId = this[PriceProfileProducts.tenantId].value,
        priceProfileId = this[PriceProfileProducts.priceProfileId].value,
        variantId = this[PriceProfileProducts.variantId].value,
        discountType = this[PriceProfileProducts.discountType],
        discountAmount = this[PriceProfileProducts.discountAmount],
        createdAt = this[PriceProfileProducts.createdAt].toString(),
        updatedAt = this[PriceProfileProducts.updatedAt].toString()
    )
}

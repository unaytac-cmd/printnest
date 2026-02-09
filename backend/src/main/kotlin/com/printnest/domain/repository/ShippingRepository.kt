package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.math.BigDecimal
import java.time.Instant

/**
 * Repository for shipping-related database operations
 */
class ShippingRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // SHIPPING LABELS
    // =====================================================

    /**
     * Find a shipping label by ID
     */
    fun findLabelById(id: Long, tenantId: Long): ShippingLabelEntity? = transaction {
        ShippingLabels.selectAll()
            .where { (ShippingLabels.id eq id) and (ShippingLabels.tenantId eq tenantId) }
            .singleOrNull()
            ?.toShippingLabelEntity()
    }

    /**
     * Find a shipping label by order ID (active, non-voided)
     */
    fun findLabelByOrderId(orderId: Long, tenantId: Long): ShippingLabelEntity? = transaction {
        ShippingLabels.selectAll()
            .where {
                (ShippingLabels.orderId eq orderId) and
                (ShippingLabels.tenantId eq tenantId) and
                (ShippingLabels.voidedAt.isNull())
            }
            .singleOrNull()
            ?.toShippingLabelEntity()
    }

    /**
     * Find all labels for an order (including voided)
     */
    fun findAllLabelsByOrderId(orderId: Long, tenantId: Long): List<ShippingLabelEntity> = transaction {
        ShippingLabels.selectAll()
            .where { (ShippingLabels.orderId eq orderId) and (ShippingLabels.tenantId eq tenantId) }
            .orderBy(ShippingLabels.createdAt, SortOrder.DESC)
            .map { it.toShippingLabelEntity() }
    }

    /**
     * Find a label by tracking number
     */
    fun findLabelByTrackingNumber(trackingNumber: String, tenantId: Long): ShippingLabelEntity? = transaction {
        ShippingLabels.selectAll()
            .where {
                (ShippingLabels.trackingNumber eq trackingNumber) and
                (ShippingLabels.tenantId eq tenantId)
            }
            .singleOrNull()
            ?.toShippingLabelEntity()
    }

    /**
     * Find a label by shipment ID
     */
    fun findLabelByShipmentId(shipmentId: String, tenantId: Long): ShippingLabelEntity? = transaction {
        ShippingLabels.selectAll()
            .where {
                (ShippingLabels.shipmentId eq shipmentId) and
                (ShippingLabels.tenantId eq tenantId)
            }
            .singleOrNull()
            ?.toShippingLabelEntity()
    }

    /**
     * Find labels by date range
     */
    fun findLabelsByDateRange(
        tenantId: Long,
        startDate: Instant,
        endDate: Instant,
        voidedOnly: Boolean = false,
        limit: Int = 100,
        offset: Long = 0
    ): List<ShippingLabelEntity> = transaction {
        var query = ShippingLabels.selectAll()
            .where {
                (ShippingLabels.tenantId eq tenantId) and
                (ShippingLabels.createdAt greaterEq startDate) and
                (ShippingLabels.createdAt lessEq endDate)
            }

        if (voidedOnly) {
            query = query.andWhere { ShippingLabels.voidedAt.isNotNull() }
        }

        query.orderBy(ShippingLabels.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toShippingLabelEntity() }
    }

    /**
     * Create a new shipping label
     */
    fun createLabel(
        tenantId: Long,
        orderId: Long,
        carrier: String?,
        service: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        labelUrl: String?,
        labelFormat: String = "PDF",
        rateId: String?,
        shipmentId: String?,
        cost: BigDecimal?,
        metadata: String = "{}"
    ): ShippingLabelEntity = transaction {
        val id = ShippingLabels.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.orderId] = orderId
            it[this.carrier] = carrier
            it[this.service] = service
            it[this.trackingNumber] = trackingNumber
            it[this.trackingUrl] = trackingUrl
            it[this.labelUrl] = labelUrl
            it[this.labelFormat] = labelFormat
            it[this.rateId] = rateId
            it[this.shipmentId] = shipmentId
            it[this.cost] = cost
            it[this.metadata] = metadata
        }

        findLabelById(id.value, tenantId)!!
    }

    /**
     * Update a shipping label
     */
    fun updateLabel(
        id: Long,
        tenantId: Long,
        trackingNumber: String? = null,
        trackingUrl: String? = null,
        labelUrl: String? = null,
        metadata: String? = null
    ): Boolean = transaction {
        ShippingLabels.update(
            where = { (ShippingLabels.id eq id) and (ShippingLabels.tenantId eq tenantId) }
        ) {
            trackingNumber?.let { tn -> it[ShippingLabels.trackingNumber] = tn }
            trackingUrl?.let { tu -> it[ShippingLabels.trackingUrl] = tu }
            labelUrl?.let { lu -> it[ShippingLabels.labelUrl] = lu }
            metadata?.let { md -> it[ShippingLabels.metadata] = md }
        } > 0
    }

    /**
     * Void a shipping label
     */
    fun voidLabel(id: Long, tenantId: Long): Boolean = transaction {
        ShippingLabels.update(
            where = { (ShippingLabels.id eq id) and (ShippingLabels.tenantId eq tenantId) }
        ) {
            it[voidedAt] = Instant.now()
        } > 0
    }

    /**
     * Void a label by shipment ID
     */
    fun voidLabelByShipmentId(shipmentId: String, tenantId: Long): Boolean = transaction {
        ShippingLabels.update(
            where = {
                (ShippingLabels.shipmentId eq shipmentId) and
                (ShippingLabels.tenantId eq tenantId) and
                (ShippingLabels.voidedAt.isNull())
            }
        ) {
            it[voidedAt] = Instant.now()
        } > 0
    }

    // =====================================================
    // LABEL HISTORY
    // =====================================================

    /**
     * Get label history for an order
     */
    fun getLabelHistory(orderId: Long, tenantId: Long): List<ShippingLabelHistory> = transaction {
        ShippingLabels.selectAll()
            .where { (ShippingLabels.orderId eq orderId) and (ShippingLabels.tenantId eq tenantId) }
            .orderBy(ShippingLabels.createdAt, SortOrder.DESC)
            .map {
                ShippingLabelHistory(
                    id = it[ShippingLabels.id].value,
                    orderId = it[ShippingLabels.orderId].value,
                    carrier = it[ShippingLabels.carrier],
                    service = it[ShippingLabels.service],
                    trackingNumber = it[ShippingLabels.trackingNumber],
                    cost = it[ShippingLabels.cost],
                    createdAt = it[ShippingLabels.createdAt].toString(),
                    voidedAt = it[ShippingLabels.voidedAt]?.toString(),
                    isActive = it[ShippingLabels.voidedAt] == null
                )
            }
    }

    // =====================================================
    // SHIPPING PROFILES
    // =====================================================

    /**
     * Find a shipping profile by ID
     */
    fun findShippingProfileById(id: Long, tenantId: Long): ShippingProfileEntity? = transaction {
        ShippingProfiles.selectAll()
            .where { (ShippingProfiles.id eq id) and (ShippingProfiles.tenantId eq tenantId) }
            .singleOrNull()
            ?.toShippingProfileEntity()
    }

    /**
     * Find the default shipping profile for a tenant
     */
    fun findDefaultShippingProfile(tenantId: Long): ShippingProfileEntity? = transaction {
        ShippingProfiles.selectAll()
            .where {
                (ShippingProfiles.tenantId eq tenantId) and
                (ShippingProfiles.isDefault eq true) and
                (ShippingProfiles.status eq 1)
            }
            .singleOrNull()
            ?.toShippingProfileEntity()
    }

    /**
     * Find all shipping profiles for a tenant
     */
    fun findAllShippingProfiles(tenantId: Long): List<ShippingProfileEntity> = transaction {
        ShippingProfiles.selectAll()
            .where { (ShippingProfiles.tenantId eq tenantId) and (ShippingProfiles.status eq 1) }
            .orderBy(ShippingProfiles.isDefault, SortOrder.DESC)
            .orderBy(ShippingProfiles.name, SortOrder.ASC)
            .map { it.toShippingProfileEntity() }
    }

    // =====================================================
    // SHIPPING METHODS
    // =====================================================

    /**
     * Find shipping methods for a profile
     */
    fun findShippingMethodsByProfileId(profileId: Long, tenantId: Long): List<ShippingMethodEntity> = transaction {
        ShippingMethods.selectAll()
            .where {
                (ShippingMethods.tenantId eq tenantId) and
                (ShippingMethods.shippingProfileId eq profileId) and
                (ShippingMethods.status eq 1)
            }
            .orderBy(ShippingMethods.sortOrder, SortOrder.ASC)
            .map { it.toShippingMethodEntity() }
    }

    /**
     * Find shipping methods for a tenant
     */
    fun findAllShippingMethods(tenantId: Long): List<ShippingMethodEntity> = transaction {
        ShippingMethods.selectAll()
            .where { (ShippingMethods.tenantId eq tenantId) and (ShippingMethods.status eq 1) }
            .orderBy(ShippingMethods.sortOrder, SortOrder.ASC)
            .map { it.toShippingMethodEntity() }
    }

    /**
     * Find a shipping method by ID
     */
    fun findShippingMethodById(id: Long, tenantId: Long): ShippingMethodEntity? = transaction {
        ShippingMethods.selectAll()
            .where { (ShippingMethods.id eq id) and (ShippingMethods.tenantId eq tenantId) }
            .singleOrNull()
            ?.toShippingMethodEntity()
    }

    /**
     * Find a shipping method by API method name
     */
    fun findShippingMethodByApiMethod(apiMethod: String, tenantId: Long): ShippingMethodEntity? = transaction {
        ShippingMethods.selectAll()
            .where {
                (ShippingMethods.tenantId eq tenantId) and
                (ShippingMethods.apiMethod eq apiMethod) and
                (ShippingMethods.status eq 1)
            }
            .singleOrNull()
            ?.toShippingMethodEntity()
    }

    // =====================================================
    // LABEL ADDRESSES
    // =====================================================

    /**
     * Find a label address by ID
     */
    fun findLabelAddressById(id: Long, tenantId: Long): LabelAddressEntity? = transaction {
        LabelAddresses.selectAll()
            .where { (LabelAddresses.id eq id) and (LabelAddresses.tenantId eq tenantId) }
            .singleOrNull()
            ?.toLabelAddressEntity()
    }

    /**
     * Find the default label address for a tenant
     */
    fun findDefaultLabelAddress(tenantId: Long): LabelAddressEntity? = transaction {
        LabelAddresses.selectAll()
            .where {
                (LabelAddresses.tenantId eq tenantId) and
                (LabelAddresses.isDefault eq true) and
                (LabelAddresses.status eq 1)
            }
            .singleOrNull()
            ?.toLabelAddressEntity()
    }

    /**
     * Find all label addresses for a tenant
     */
    fun findAllLabelAddresses(tenantId: Long): List<LabelAddressEntity> = transaction {
        LabelAddresses.selectAll()
            .where { (LabelAddresses.tenantId eq tenantId) and (LabelAddresses.status eq 1) }
            .orderBy(LabelAddresses.isDefault, SortOrder.DESC)
            .orderBy(LabelAddresses.name, SortOrder.ASC)
            .map { it.toLabelAddressEntity() }
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    /**
     * Count labels for a tenant
     */
    fun countLabels(tenantId: Long, includeVoided: Boolean = false): Long = transaction {
        var query = ShippingLabels.selectAll()
            .where { ShippingLabels.tenantId eq tenantId }

        if (!includeVoided) {
            query = query.andWhere { ShippingLabels.voidedAt.isNull() }
        }

        query.count()
    }

    /**
     * Get total shipping cost for a tenant
     */
    fun getTotalShippingCost(tenantId: Long, startDate: Instant? = null, endDate: Instant? = null): BigDecimal = transaction {
        var query = ShippingLabels.selectAll()
            .where {
                (ShippingLabels.tenantId eq tenantId) and
                (ShippingLabels.voidedAt.isNull())
            }

        startDate?.let { sd ->
            query = query.andWhere { ShippingLabels.createdAt greaterEq sd }
        }

        endDate?.let { ed ->
            query = query.andWhere { ShippingLabels.createdAt lessEq ed }
        }

        query.sumOf { it[ShippingLabels.cost] ?: BigDecimal.ZERO }
    }

    /**
     * Get carrier breakdown for a tenant
     */
    fun getCarrierBreakdown(tenantId: Long): Map<String, Int> = transaction {
        ShippingLabels.selectAll()
            .where {
                (ShippingLabels.tenantId eq tenantId) and
                (ShippingLabels.voidedAt.isNull())
            }
            .groupBy { it[ShippingLabels.carrier] ?: "Unknown" }
            .mapValues { it.value.size }
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private fun ResultRow.toShippingLabelEntity(): ShippingLabelEntity = ShippingLabelEntity(
        id = this[ShippingLabels.id].value,
        tenantId = this[ShippingLabels.tenantId].value,
        orderId = this[ShippingLabels.orderId].value,
        carrier = this[ShippingLabels.carrier],
        service = this[ShippingLabels.service],
        trackingNumber = this[ShippingLabels.trackingNumber],
        trackingUrl = this[ShippingLabels.trackingUrl],
        labelUrl = this[ShippingLabels.labelUrl],
        labelFormat = this[ShippingLabels.labelFormat],
        rateId = this[ShippingLabels.rateId],
        shipmentId = this[ShippingLabels.shipmentId],
        cost = this[ShippingLabels.cost],
        metadata = this[ShippingLabels.metadata],
        createdAt = this[ShippingLabels.createdAt].toString(),
        voidedAt = this[ShippingLabels.voidedAt]?.toString()
    )

    private fun ResultRow.toShippingProfileEntity(): ShippingProfileEntity = ShippingProfileEntity(
        id = this[ShippingProfiles.id].value,
        tenantId = this[ShippingProfiles.tenantId].value,
        name = this[ShippingProfiles.name],
        profileType = this[ShippingProfiles.profileType],
        pricing = this[ShippingProfiles.pricing],
        profilePricing = this[ShippingProfiles.profilePricing],
        isDefault = this[ShippingProfiles.isDefault],
        status = this[ShippingProfiles.status],
        createdAt = this[ShippingProfiles.createdAt].toString(),
        updatedAt = this[ShippingProfiles.updatedAt].toString()
    )

    private fun ResultRow.toShippingMethodEntity(): ShippingMethodEntity = ShippingMethodEntity(
        id = this[ShippingMethods.id].value,
        tenantId = this[ShippingMethods.tenantId].value,
        shippingProfileId = this[ShippingMethods.shippingProfileId]?.value,
        name = this[ShippingMethods.name],
        description = this[ShippingMethods.description],
        apiMethod = this[ShippingMethods.apiMethod],
        isInternational = this[ShippingMethods.isInternational],
        extraFee = this[ShippingMethods.extraFee],
        processingInfo = this[ShippingMethods.processingInfo],
        sortOrder = this[ShippingMethods.sortOrder],
        status = this[ShippingMethods.status],
        createdAt = this[ShippingMethods.createdAt].toString(),
        updatedAt = this[ShippingMethods.updatedAt].toString()
    )

    private fun ResultRow.toLabelAddressEntity(): LabelAddressEntity = LabelAddressEntity(
        id = this[LabelAddresses.id].value,
        tenantId = this[LabelAddresses.tenantId].value,
        name = this[LabelAddresses.name],
        phone = this[LabelAddresses.phone],
        street1 = this[LabelAddresses.street1],
        street2 = this[LabelAddresses.street2],
        city = this[LabelAddresses.city],
        state = this[LabelAddresses.state],
        stateIso = this[LabelAddresses.stateIso],
        country = this[LabelAddresses.country],
        countryIso = this[LabelAddresses.countryIso],
        postalCode = this[LabelAddresses.postalCode],
        isDefault = this[LabelAddresses.isDefault],
        status = this[LabelAddresses.status],
        createdAt = this[LabelAddresses.createdAt].toString(),
        updatedAt = this[LabelAddresses.updatedAt].toString()
    )
}

// =====================================================
// ENTITY CLASSES
// =====================================================

data class ShippingLabelEntity(
    val id: Long,
    val tenantId: Long,
    val orderId: Long,
    val carrier: String?,
    val service: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val labelUrl: String?,
    val labelFormat: String,
    val rateId: String?,
    val shipmentId: String?,
    val cost: BigDecimal?,
    val metadata: String,
    val createdAt: String,
    val voidedAt: String?
) {
    val isVoided: Boolean
        get() = voidedAt != null
}

data class ShippingLabelHistory(
    val id: Long,
    val orderId: Long,
    val carrier: String?,
    val service: String?,
    val trackingNumber: String?,
    val cost: BigDecimal?,
    val createdAt: String,
    val voidedAt: String?,
    val isActive: Boolean
)

data class ShippingProfileEntity(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val profileType: Int,
    val pricing: String,
    val profilePricing: String,
    val isDefault: Boolean,
    val status: Int,
    val createdAt: String,
    val updatedAt: String
)

data class ShippingMethodEntity(
    val id: Long,
    val tenantId: Long,
    val shippingProfileId: Long?,
    val name: String,
    val description: String?,
    val apiMethod: String?,
    val isInternational: Boolean,
    val extraFee: BigDecimal,
    val processingInfo: String,
    val sortOrder: Int,
    val status: Int,
    val createdAt: String,
    val updatedAt: String
)

data class LabelAddressEntity(
    val id: Long,
    val tenantId: Long,
    val name: String,
    val phone: String?,
    val street1: String,
    val street2: String?,
    val city: String,
    val state: String?,
    val stateIso: String?,
    val country: String,
    val countryIso: String,
    val postalCode: String,
    val isDefault: Boolean,
    val status: Int,
    val createdAt: String,
    val updatedAt: String
) {
    fun toShippingAddress(): ShippingAddress = ShippingAddress(
        name = name,
        street1 = street1,
        street2 = street2,
        city = city,
        state = stateIso ?: state ?: "",
        zip = postalCode,
        country = countryIso,
        phone = phone
    )
}

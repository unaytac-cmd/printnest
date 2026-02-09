package com.printnest.domain.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant

// JSON serializer for JSONB columns
private val jsonSerializer = Json { ignoreUnknownKeys = true }

// =====================================================
// PLATFORM TABLES
// =====================================================

object Tenants : LongIdTable("tenants") {
    val subdomain = varchar("subdomain", 63).uniqueIndex()
    val name = varchar("name", 255)
    val status = integer("status").default(0)
    val customDomain = varchar("custom_domain", 255).nullable()
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val settings = jsonb<String>("settings", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object SubscriptionPlans : LongIdTable("subscription_plans") {
    val name = varchar("name", 100)
    val stripePriceIdMonthly = varchar("stripe_price_id_monthly", 255).nullable()
    val stripePriceIdYearly = varchar("stripe_price_id_yearly", 255).nullable()
    val monthlyPrice = decimal("monthly_price", 10, 2)
    val yearlyPrice = decimal("yearly_price", 10, 2)
    val orderLimit = integer("order_limit").default(-1)
    val userLimit = integer("user_limit").default(-1)
    val marketplaceLimit = integer("marketplace_limit").default(-1)
    val features = jsonb<String>("features", jsonSerializer).default("[]")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object TenantSubscriptions : LongIdTable("tenant_subscriptions") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val planId = reference("plan_id", SubscriptionPlans)
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).nullable()
    val status = varchar("status", 50).default("active")
    val currentPeriodStart = timestamp("current_period_start").nullable()
    val currentPeriodEnd = timestamp("current_period_end").nullable()
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object SuperAdmins : LongIdTable("super_admins") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 255)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// =====================================================
// TENANT-SCOPED TABLES
// =====================================================

object Users : LongIdTable("users") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val email = varchar("email", 255)
    val passwordHash = varchar("password_hash", 255)
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val role = varchar("role", 50).default("customer")
    val status = integer("status").default(1)
    val permissions = jsonb<String>("permissions", jsonSerializer).default("[]")
    val totalCredit = decimal("total_credit", 10, 2).default(java.math.BigDecimal.ZERO)
    val emailVerified = bool("email_verified").default(false)
    val lastLoginAt = timestamp("last_login_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    // V2: Producer/Sub-dealer fields
    val parentUserId = reference("parent_user_id", Users).nullable()
    val priceProfileId = long("price_profile_id").nullable()
    val shippingProfileId = long("shipping_profile_id").nullable()

    init {
        uniqueIndex("idx_users_tenant_email", tenantId, email)
    }
}

object Marketplaces : LongIdTable("marketplaces") {
    val name = varchar("name", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val isActive = bool("is_active").default(true)
}

object Stores : LongIdTable("stores") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val storeName = varchar("store_name", 255)
    val marketplaceId = reference("marketplace_id", Marketplaces)
    val credentials = jsonb<String>("credentials", jsonSerializer).default("{}")
    val status = integer("status").default(1)
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val syncError = text("sync_error").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object ProductCategories : LongIdTable("product_categories") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val parentCategoryId = reference("parent_category_id", ProductCategories).nullable()
    val isHeavy = bool("is_heavy").default(false) // V3: For shipping calculations
    val status = integer("status").default(1)
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object Products : LongIdTable("products") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val categoryId = reference("category_id", ProductCategories).nullable()
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val basePrice = decimal("base_price", 10, 2).default(java.math.BigDecimal.ZERO)
    val option1Name = varchar("option1_name", 100).nullable()
    val option2Name = varchar("option2_name", 100).nullable()
    val option3Name = varchar("option3_name", 100).nullable()
    val designType = integer("design_type").default(1) // V3: 1=Image/DTF, 2=Embroidery, 3=Vector, 4=Ornament
    val supplierId = long("supplier_id").nullable() // V3: Supplier reference (FK in migration)
    val status = integer("status").default(1)
    val tags = jsonb<String>("tags", jsonSerializer).default("[]")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// V3: Option1s - Size options per product
object Option1s : LongIdTable("option1s") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val productId = reference("product_id", Products, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// V3: Option2s - Color options per product
object Option2s : LongIdTable("option2s") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val productId = reference("product_id", Products, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val hexColor = varchar("hex_color", 7).nullable()
    val isDark = bool("is_dark").default(false)
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object Variants : LongIdTable("variants") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val productId = reference("product_id", Products, onDelete = ReferenceOption.CASCADE)
    val option1Value = varchar("option1_value", 100).nullable()
    val option2Value = varchar("option2_value", 100).nullable()
    val option3Value = varchar("option3_value", 100).nullable()
    val option1Id = reference("option1_id", Option1s).nullable() // V3: Structured option reference
    val option2Id = reference("option2_id", Option2s).nullable() // V3: Structured option reference
    val sku = varchar("sku", 100).nullable()
    val price = decimal("price", 10, 2).default(java.math.BigDecimal.ZERO)
    val cost = decimal("cost", 10, 2).default(java.math.BigDecimal.ZERO)
    val weight = decimal("weight", 10, 4).nullable()
    val width1 = decimal("width1", 10, 2).nullable() // V3: Primary print width (inches)
    val width2 = decimal("width2", 10, 2).nullable() // V3: Secondary print width (inches)
    val isDark = bool("is_dark").default(false) // V3: Dark color flag
    val inStock = bool("in_stock").default(true)
    val inHouse = bool("in_house").default(false) // V13: In-house inventory flag
    val stockQuantity = integer("stock_quantity").nullable()
    val status = integer("status").default(1) // V13: 1=enabled, 0=disabled
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// V3: Variant Modifications - Size-based dimensions and weights
object VariantModifications : LongIdTable("variant_modifications") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val productId = reference("product_id", Products, onDelete = ReferenceOption.CASCADE)
    val option1Id = reference("option1_id", Option1s, onDelete = ReferenceOption.CASCADE)
    val width = decimal("width", 10, 2).default(java.math.BigDecimal.ZERO)
    val height = decimal("height", 10, 2).default(java.math.BigDecimal.ZERO)
    val depth = decimal("depth", 10, 2).default(java.math.BigDecimal.ZERO)
    val weight = decimal("weight", 10, 2).default(java.math.BigDecimal.ZERO) // Shipping weight in oz
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_variant_mods_unique", productId, option1Id)
    }
}

// V3: Modifications - Print locations (linked to CATEGORIES)
object Modifications : LongIdTable("modifications") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val categoryId = reference("category_id", ProductCategories, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100) // "Front", "Back", "Left Sleeve", etc.
    val description = text("description").nullable()
    val priceDifference = decimal("price_difference", 10, 2).default(java.math.BigDecimal.ZERO)
    val useWidth = integer("use_width").default(1) // 1 = use width1, 2 = use width2
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object Designs : LongIdTable("designs") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val title = varchar("title", 255)
    val fileHash = varchar("file_hash", 64)
    val designType = integer("design_type").default(1)
    val designUrl = text("design_url")
    val thumbnailUrl = text("thumbnail_url").nullable()
    val width = decimal("width", 10, 2).nullable()
    val height = decimal("height", 10, 2).nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object Orders : LongIdTable("orders") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val storeId = reference("store_id", Stores).nullable()
    val intOrderId = varchar("int_order_id", 100).nullable() // V5: Internal order ID
    val externalOrderId = varchar("external_order_id", 255).nullable()
    val orderType = integer("order_type").default(0) // V5: 0=standard, 1=bulk, 2=sample
    val orderStatus = integer("order_status").default(0)
    val orderMapStatus = integer("order_map_status").default(0) // V3: 0=Not Mapped, 1=Completely, 2=Partially
    val orderDetail = jsonb<String>("order_detail", jsonSerializer).default("{}") // V5: Raw marketplace data
    val orderInfo = jsonb<String>("order_info", jsonSerializer).default("{}")
    val priceDetail = jsonb<String>("price_detail", jsonSerializer).default("[]")
    val totalAmount = decimal("total_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val shippingAmount = decimal("shipping_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val taxAmount = decimal("tax_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val urgentAmount = decimal("urgent_amount", 10, 2).default(java.math.BigDecimal.ZERO) // V5
    val giftNote = text("gift_note").nullable() // V5
    val customerEmail = varchar("customer_email", 255).nullable()
    val customerName = varchar("customer_name", 255).nullable()
    val shippingAddress = jsonb<String>("shipping_address", jsonSerializer).default("{}")
    val billingAddress = jsonb<String>("billing_address", jsonSerializer).default("{}")
    val trackingNumber = varchar("tracking_number", 255).nullable()
    val trackingUrl = text("tracking_url").nullable()
    val paymentId = long("payment_id").nullable() // V5
    val paymentMethod = varchar("payment_method", 50).nullable() // V5
    val shippedAt = timestamp("shipped_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    // V2: ShipStation integration
    val shipstationStoreId = reference("shipstation_store_id", ShipStationStores).nullable()
    val shipstationOrderId = long("shipstation_order_id").nullable()
}

object OrderProducts : LongIdTable("order_products") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val productId = reference("product_id", Products).nullable()
    val variantId = reference("variant_id", Variants).nullable()
    val listingId = varchar("listing_id", 255).nullable()
    val quantity = integer("quantity").default(1)
    val unitPrice = decimal("unit_price", 10, 2).default(java.math.BigDecimal.ZERO)
    val productDetail = jsonb<String>("product_detail", jsonSerializer).default("{}")
    val designId = reference("design_id", Designs).nullable()
    val modificationDetail = jsonb<String>("modification_detail", jsonSerializer).default("[]")
    val priceBreakdown = jsonb<String>("price_breakdown", jsonSerializer).default("{}") // V5
    val mappingId = long("mapping_id").nullable() // V5
    val listingImageUrl = text("listing_image_url").nullable() // V5
    val stitchCount = integer("stitch_count").default(0) // V5
    val status = integer("status").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// =====================================================
// V5: ORDER PROCESSING TABLES
// =====================================================

object Payments : LongIdTable("payments") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val orderId = reference("order_id", Orders).nullable()
    val paymentMethod = varchar("payment_method", 50)
    val amount = decimal("amount", 10, 2)
    val status = varchar("status", 50).default("pending")
    val stripeSessionId = varchar("stripe_session_id", 255).nullable()
    val stripePaymentIntent = varchar("stripe_payment_intent", 255).nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val completedAt = timestamp("completed_at").nullable()
}

object OrderHistory : LongIdTable("order_history") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val previousStatus = integer("previous_status").nullable()
    val newStatus = integer("new_status")
    val action = varchar("action", 100).nullable()
    val notes = text("notes").nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object OrderNotes : LongIdTable("order_notes") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val note = text("note")
    val isInternal = bool("is_internal").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object OrderCustoms : LongIdTable("order_customs") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val eelPfc = varchar("eel_pfc", 50).default("NOEEI 30.37(a)")
    val customsCertify = bool("customs_certify").default(true)
    val customsSigner = varchar("customs_signer", 255).nullable()
    val contentsType = varchar("contents_type", 50).default("merchandise")
    val contentsExplanation = text("contents_explanation").nullable()
    val restrictionType = varchar("restriction_type", 50).default("none")
    val nonDeliveryOption = varchar("non_delivery_option", 50).default("return")
    val customsItems = jsonb<String>("customs_items", jsonSerializer).default("[]")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_order_customs_order", orderId)
    }
}

object ShippingLabels : LongIdTable("shipping_labels") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val carrier = varchar("carrier", 100).nullable()
    val service = varchar("service", 100).nullable()
    val trackingNumber = varchar("tracking_number", 255).nullable()
    val trackingUrl = text("tracking_url").nullable()
    val labelUrl = text("label_url").nullable()
    val labelFormat = varchar("label_format", 20).default("PDF")
    val rateId = varchar("rate_id", 255).nullable()
    val shipmentId = varchar("shipment_id", 255).nullable()
    val cost = decimal("cost", 10, 2).nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val voidedAt = timestamp("voided_at").nullable()
}

object Transactions : LongIdTable("transactions") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val type = integer("type")
    val amount = decimal("amount", 10, 2)
    val description = text("description").nullable()
    val referenceId = varchar("reference_id", 255).nullable()
    val balanceBefore = decimal("balance_before", 10, 2)
    val balanceAfter = decimal("balance_after", 10, 2)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object ShippingProfiles : LongIdTable("shipping_profiles") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val profileType = integer("profile_type").default(0) // 0=quantity-based, 1=api-based
    val pricing = jsonb<String>("pricing", jsonSerializer).default("{}")
    val profilePricing = jsonb<String>("profile_pricing", jsonSerializer).default("{}") // V4: JSONB pricing config
    val isDefault = bool("is_default").default(false)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// V4: Shipping Methods - Available shipping options within a profile
object ShippingMethods : LongIdTable("shipping_methods") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val shippingProfileId = reference("shipping_profile_id", ShippingProfiles, onDelete = ReferenceOption.CASCADE).nullable()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val apiMethod = varchar("api_method", 100).nullable() // EasyPost service name
    val isInternational = bool("is_international").default(false)
    val extraFee = decimal("extra_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val processingInfo = jsonb<String>("processing_info", jsonSerializer).default("{}") // JSONB
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object Gangsheets : LongIdTable("gangsheets") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val status = integer("status").default(0) // V3: 0=Starting, 1-5=Processing, 6=Completed, -1=Failed
    val orderIds = jsonb<String>("order_ids", jsonSerializer).default("[]")
    val settings = jsonb<String>("settings", jsonSerializer).default("{}")
    val downloadUrl = text("download_url").nullable()
    val errorMessage = text("error_message").nullable()
    val totalDesigns = integer("total_designs").default(0) // V3: Total designs to process
    val processedDesigns = integer("processed_designs").default(0) // V3: Designs processed so far
    val totalRolls = integer("total_rolls").default(0) // V3: Number of rolls generated
    val rollUrls = jsonb<String>("roll_urls", jsonSerializer).default("[]") // V3: URLs for individual roll images
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val completedAt = timestamp("completed_at").nullable()
}

object SupportTickets : LongIdTable("support_tickets") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val subject = varchar("subject", 255)
    val status = integer("status").default(0)
    val priority = integer("priority").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object SupportMessages : LongIdTable("support_messages") {
    val ticketId = reference("ticket_id", SupportTickets, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val message = text("message")
    val attachments = jsonb<String>("attachments", jsonSerializer).default("[]")
    val isInternal = bool("is_internal").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object Invoices : LongIdTable("invoices") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val stripeInvoiceId = varchar("stripe_invoice_id", 255).nullable()
    val amount = decimal("amount", 10, 2)
    val status = varchar("status", 50).default("draft")
    val invoiceUrl = text("invoice_url").nullable()
    val invoicePdf = text("invoice_pdf").nullable()
    val periodStart = timestamp("period_start").nullable()
    val periodEnd = timestamp("period_end").nullable()
    val paidAt = timestamp("paid_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// =====================================================
// V2: PRODUCER/SUBDEALER TABLES
// =====================================================

object ShipStationStores : LongIdTable("shipstation_stores") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val shipstationStoreId = long("shipstation_store_id")
    val storeName = varchar("store_name", 255)
    val marketplaceName = varchar("marketplace_name", 100).nullable()
    val marketplaceId = long("marketplace_id").nullable()
    val accountName = varchar("account_name", 255).nullable()
    val isActive = bool("is_active").default(true)
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_ss_stores_tenant_store", tenantId, shipstationStoreId)
    }
}

object SubdealerStoreAssignments : LongIdTable("subdealer_store_assignments") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val subdealerId = reference("subdealer_id", Users, onDelete = ReferenceOption.CASCADE)
    val shipstationStoreId = reference("shipstation_store_id", ShipStationStores, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_subdealer_store_unique", subdealerId, shipstationStoreId)
    }
}

object PriceProfiles : LongIdTable("price_profiles") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val profileType = integer("profile_type").default(0)
    val pricing = jsonb<String>("pricing", jsonSerializer).default("{}")
    val discountType = integer("discount_type").default(0) // V4: 0=percentage, 1=dollar
    val discountAmount = decimal("discount_amount", 10, 2).default(java.math.BigDecimal.ZERO) // V4
    val giftNotePrice = decimal("gift_note_price", 10, 2).default(java.math.BigDecimal.ZERO) // V4
    val stitchPrice = decimal("stitch_price", 10, 4).default(java.math.BigDecimal.ZERO) // V4: Per-stitch cost
    val digitizingPrice = decimal("digitizing_price", 10, 2).default(java.math.BigDecimal.ZERO) // V4
    val gangsheetPrice = decimal("gangsheet_price", 10, 2).default(java.math.BigDecimal.ZERO) // V4
    val uvGangsheetPrice = decimal("uv_gangsheet_price", 10, 2).default(java.math.BigDecimal.ZERO) // V4
    val isDefault = bool("is_default").default(false)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// V4: Price Profile Products - Variant-specific pricing overrides
object PriceProfileProducts : LongIdTable("price_profile_products") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val priceProfileId = reference("price_profile_id", PriceProfiles, onDelete = ReferenceOption.CASCADE)
    val variantId = reference("variant_id", Variants, onDelete = ReferenceOption.CASCADE)
    val discountType = integer("discount_type").default(0) // 0=percentage, 1=dollar
    val discountAmount = decimal("discount_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_price_profile_products_unique", priceProfileId, variantId)
    }
}

// =====================================================
// V3: SUPPLIERS
// =====================================================

object Suppliers : LongIdTable("suppliers") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val contactEmail = varchar("contact_email", 255).nullable()
    val contactPhone = varchar("contact_phone", 50).nullable()
    val address = jsonb<String>("address", jsonSerializer).default("{}")
    val notes = text("notes").nullable()
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// =====================================================
// V3: MAPPING TABLES
// =====================================================

// Map external variation IDs to internal variants
object MapValues : LongIdTable("map_values") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val valueId1 = varchar("value_id_1", 255).nullable() // External variation ID 1
    val valueId2 = varchar("value_id_2", 255).nullable() // External variation ID 2
    val variantId = reference("variant_id", Variants).nullable()
    val variantModificationId = reference("variant_modification_id", VariantModifications).nullable()
    val isDark = bool("is_dark").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// Map external listings to modifications and designs
object MapListings : LongIdTable("map_listings") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val listingId = varchar("listing_id", 255)
    val modificationId = reference("modification_id", Modifications).nullable()
    val lightDesignId = reference("light_design_id", Designs).nullable()
    val darkDesignId = reference("dark_design_id", Designs).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// =====================================================
// V6: SETTINGS FEATURES TABLES
// =====================================================

// Announcements - Tenant-wide announcements
object Announcements : LongIdTable("announcements") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val content = text("content")
    val displayFrom = timestamp("display_from").clientDefault { Instant.now() }
    val displayTo = timestamp("display_to").nullable()
    val showAsPopup = bool("show_as_popup").default(false)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// Referral Campaigns
object ReferralCampaigns : LongIdTable("referral_campaigns") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val referrerId = reference("referrer_id", Users).nullable()
    val referralCode = varchar("referral_code", 50)
    val minPayment = decimal("min_payment", 10, 2).default(java.math.BigDecimal.ZERO)
    val amountToAdd = decimal("amount_to_add", 10, 2).default(java.math.BigDecimal.ZERO)
    val startDate = timestamp("start_date").clientDefault { Instant.now() }
    val endDate = timestamp("end_date").nullable()
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_referral_campaigns_code", tenantId, referralCode)
    }
}

// Referral Credits - Track earned referral credits
object ReferralCredits : LongIdTable("referral_credits") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val campaignId = reference("campaign_id", ReferralCampaigns).nullable()
    val referrerId = reference("referrer_id", Users)
    val referredUserId = reference("referred_user_id", Users).nullable()
    val orderId = reference("order_id", Orders).nullable()
    val creditAmount = decimal("credit_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val status = integer("status").default(0) // 0=pending, 1=processed, 2=rejected
    val processedAt = timestamp("processed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// Embroidery Colors - Color library for embroidery
object EmbroideryColors : LongIdTable("embroidery_colors") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val hexColor = varchar("hex_color", 7) // #RRGGBB format
    val inStock = bool("in_stock").default(true)
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// Label Addresses - Return/shipping label addresses
object LabelAddresses : LongIdTable("label_addresses") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val phone = varchar("phone", 50).nullable()
    val street1 = varchar("street1", 255)
    val street2 = varchar("street2", 255).nullable()
    val city = varchar("city", 100)
    val state = varchar("state", 100).nullable()
    val stateIso = varchar("state_iso", 10).nullable()
    val country = varchar("country", 100).default("United States")
    val countryIso = varchar("country_iso", 10).default("US")
    val postalCode = varchar("postal_code", 20)
    val isDefault = bool("is_default").default(false)
    val status = integer("status").default(1)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// Notifications - In-app notifications
object Notifications : LongIdTable("notifications") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val notificationType = varchar("notification_type", 50).default("system")
    val title = varchar("title", 255)
    val message = text("message")
    val url = varchar("url", 500).nullable()
    val priority = varchar("priority", 20).default("medium")
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

// User Permissions - Fine-grained permission control
object UserPermissions : LongIdTable("user_permissions") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val permissionId = integer("permission_id")
    val grantedBy = reference("granted_by", Users).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_user_permission_unique", userId, permissionId)
    }
}

// Permissions Reference - Available permissions
object PermissionsRef : LongIdTable("permissions") {
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val category = varchar("category", 50).nullable()
}

// Gangsheet Rolls - Individual rolls within a gangsheet
object GangsheetRolls : LongIdTable("gangsheet_rolls") {
    val gangsheetId = reference("gangsheet_id", Gangsheets, onDelete = ReferenceOption.CASCADE)
    val rollNumber = integer("roll_number")
    val widthPixels = integer("width_pixels").nullable()
    val heightPixels = integer("height_pixels").nullable()
    val designCount = integer("design_count").default(0)
    val fileUrl = text("file_url").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

// =====================================================
// V8: SCHEDULED JOBS (Background Job Scheduler)
// =====================================================

object ScheduledJobs : LongIdTable("scheduled_jobs") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE).nullable()
    val jobType = varchar("job_type", 50)
    val jobStatus = integer("job_status").default(0) // 0=PENDING, 1=RUNNING, 2=COMPLETED, -1=FAILED
    val jobData = jsonb<String>("job_data", jsonSerializer).default("{}")
    val scheduledAt = timestamp("scheduled_at").nullable()
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index("idx_scheduled_jobs_status", false, jobStatus)
        index("idx_scheduled_jobs_tenant", false, tenantId)
        index("idx_scheduled_jobs_type", false, jobType)
    }
}

// =====================================================
// V9: TIKTOK SHOP INTEGRATION
// =====================================================

object TikTokStores : LongIdTable("tiktok_stores") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users)
    val storeId = varchar("store_id", 100) // Internal store identifier
    val shopId = varchar("shop_id", 100).nullable() // TikTok shop ID
    val shopName = varchar("shop_name", 255).nullable()
    val shopCipher = varchar("shop_cipher", 500).nullable() // Used for multi-shop API calls
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val accessTokenExpireAt = long("access_token_expire_at").nullable() // Unix timestamp
    val refreshTokenExpireAt = long("refresh_token_expire_at").nullable() // Unix timestamp
    val region = varchar("region", 10).nullable() // e.g., "US", "UK", etc.
    val isActive = bool("is_active").default(true)
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        uniqueIndex("idx_tiktok_stores_tenant_store", tenantId, storeId)
    }
}

// =====================================================
// V10: MONITORING TABLES
// =====================================================

object ApiLogs : LongIdTable("api_logs") {
    val tenantId = long("tenant_id").nullable()
    val userId = long("user_id").nullable()
    val endpoint = varchar("endpoint", 500)
    val method = varchar("method", 10)
    val statusCode = integer("status_code")
    val durationMs = long("duration_ms")
    val requestSize = long("request_size").nullable()
    val responseSize = long("response_size").nullable()
    val userAgent = varchar("user_agent", 500).nullable()
    val ipAddress = varchar("ip_address", 50).nullable()
    val errorMessage = varchar("error_message", 2000).nullable()
    val errorStackTrace = text("error_stack_trace").nullable()
    val requestBody = text("request_body").nullable()
    val responseBody = text("response_body").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index("idx_api_logs_tenant", false, tenantId)
        index("idx_api_logs_created", false, createdAt)
        index("idx_api_logs_status", false, statusCode)
        index("idx_api_logs_endpoint", false, endpoint)
    }
}

object SyncStatuses : LongIdTable("sync_statuses") {
    val tenantId = long("tenant_id")
    val storeId = long("store_id").nullable()
    val marketplace = varchar("marketplace", 50)
    val storeName = varchar("store_name", 255).nullable()
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val lastSuccessAt = timestamp("last_success_at").nullable()
    val lastErrorAt = timestamp("last_error_at").nullable()
    val errorCount = integer("error_count").default(0)
    val lastErrorMessage = text("last_error_message").nullable()
    val syncState = varchar("sync_state", 20).default("idle") // idle, syncing, error, disabled
    val ordersImported = integer("orders_imported").default(0)
    val ordersSynced = integer("orders_synced").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        index("idx_sync_statuses_tenant", false, tenantId)
        index("idx_sync_statuses_store", false, storeId)
        uniqueIndex("idx_sync_statuses_tenant_store", tenantId, storeId)
    }
}

// =====================================================
// V11: DIGITIZING/EMBROIDERY TABLES
// =====================================================

object DigitizingOrders : LongIdTable("digitizing_orders") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val digitizerId = reference("digitizer_id", Users).nullable()
    val designId = reference("design_id", Designs).nullable()
    val digitizingStatus = integer("digitizing_status").default(0) // -1=cancelled, 0=payment_pending, 1=pending, 2=in_progress, 3=completed, 4=rejected
    val digitizingDetails = jsonb<String>("digitizing_details", jsonSerializer).default("{}") // JSONB
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        index("idx_digitizing_orders_tenant", false, tenantId)
        index("idx_digitizing_orders_user", false, userId)
        index("idx_digitizing_orders_digitizer", false, digitizerId)
        index("idx_digitizing_orders_status", false, digitizingStatus)
    }
}

object EmbroideryThreadColors : LongIdTable("embroidery_thread_colors") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val hexColor = varchar("hex_color", 7) // #RRGGBB format
    val pantone = varchar("pantone", 50).nullable()
    val inStock = bool("in_stock").default(true)
    val sortOrder = integer("sort_order").default(0)
    val status = integer("status").default(1) // 0=deleted, 1=active
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        index("idx_embroidery_colors_tenant", false, tenantId)
        index("idx_embroidery_colors_status", false, status)
    }
}

object DigitizingOrderHistory : LongIdTable("digitizing_order_history") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val digitizingOrderId = reference("digitizing_order_id", DigitizingOrders, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val previousStatus = integer("previous_status").nullable()
    val newStatus = integer("new_status")
    val action = varchar("action", 100).nullable()
    val notes = text("notes").nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index("idx_digitizing_history_order", false, digitizingOrderId)
        index("idx_digitizing_history_tenant", false, tenantId)
    }
}

// =====================================================
// V12: ORDER BATCH TABLES
// =====================================================

object OrderBatches : LongIdTable("order_batches") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val status = integer("status").default(0) // -1=deleted, 0=draft, 1=ready, 2=processing, 3=completed
    val orderIds = jsonb<String>("order_ids", jsonSerializer).default("[]") // JSON array of order IDs
    val gangsheetId = reference("gangsheet_id", Gangsheets).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    init {
        index("idx_order_batches_tenant", false, tenantId)
        index("idx_order_batches_status", false, status)
        index("idx_order_batches_gangsheet", false, gangsheetId)
    }
}

object BatchHistory : LongIdTable("batch_history") {
    val tenantId = reference("tenant_id", Tenants, onDelete = ReferenceOption.CASCADE)
    val batchId = reference("batch_id", OrderBatches, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users).nullable()
    val previousStatus = integer("previous_status").nullable()
    val newStatus = integer("new_status")
    val action = varchar("action", 100).nullable()
    val notes = text("notes").nullable()
    val metadata = jsonb<String>("metadata", jsonSerializer).default("{}")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        index("idx_batch_history_batch", false, batchId)
        index("idx_batch_history_tenant", false, tenantId)
    }
}

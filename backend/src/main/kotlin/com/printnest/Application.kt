package com.printnest

import com.printnest.config.koinModules
import com.printnest.domain.service.SchedulerService
import com.printnest.plugins.configureAuthentication
import com.printnest.plugins.configureCORS
import com.printnest.plugins.configureContentNegotiation
import com.printnest.plugins.configureDatabase
import com.printnest.plugins.configureErrorHandling
import com.printnest.plugins.configureApiLogging
import com.printnest.routes.authRoutes
import com.printnest.routes.categoryRoutes
import com.printnest.routes.orderRoutes
import com.printnest.routes.productRoutes
import com.printnest.routes.profileRoutes
import com.printnest.routes.settingsRoutes
import com.printnest.routes.shipStationRoutes
import com.printnest.routes.ticketRoutes
import com.printnest.routes.walletRoutes
import com.printnest.routes.designRoutes
import com.printnest.routes.subdealerRoutes
import com.printnest.routes.emailRoutes
import com.printnest.routes.gangsheetRoutes
import com.printnest.routes.shippingRoutes
import com.printnest.routes.etsyRoutes
import com.printnest.routes.walmartRoutes
import com.printnest.routes.amazonRoutes
import com.printnest.routes.tikTokRoutes
import com.printnest.routes.shopifyRoutes
import com.printnest.routes.aiRoutes
import com.printnest.routes.exportRoutes
import com.printnest.routes.monitorRoutes
import com.printnest.routes.pdfRoutes
import com.printnest.routes.digitizingRoutes
import com.printnest.routes.batchRoutes
import com.printnest.routes.mappingRoutes
import com.printnest.routes.interServiceRoutes
import com.printnest.integrations.nestshipper.nestShipperRoutes
import com.printnest.integrations.stripe.stripeRoutes
import com.printnest.plugins.configureInterServiceAuth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0"
    ) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    logger.info("Starting PrintNest Backend")

    // Configure database connection first
    configureDatabase()

    // Initialize Koin DI
    install(Koin) {
        slf4jLogger()
        modules(koinModules())
    }

    // Configure plugins
    configureContentNegotiation()
    configureCORS()
    configureErrorHandling()
    configureAuthentication()
    configureInterServiceAuth()
    configureApiLogging()

    // Start background scheduler
    val scheduler: SchedulerService by inject()
    scheduler.start()
    logger.info("Background scheduler started")

    // Configure routes
    routing {
        get("/health") {
            call.respond(mapOf(
                "status" to "healthy",
                "version" to "1.0.0",
                "service" to "printnest-backend"
            ))
        }

        get("/") {
            call.respond(mapOf(
                "message" to "Welcome to PrintNest API",
                "docs" to "/api/v1/docs"
            ))
        }

        route("/api/v1") {
            get("/status") {
                call.respond(mapOf("status" to "ok"))
            }

            // Auth routes (register, login, logout, refresh)
            authRoutes()

            // ShipStation integration routes
            shipStationRoutes()

            // Subdealer management routes
            subdealerRoutes()

            // Category and product routes
            categoryRoutes()
            productRoutes()

            // Profile routes (shipping & price)
            profileRoutes()

            // Order processing routes (Step 1-4)
            orderRoutes()

            // Settings routes (Announcements, Referrals, Colors, etc.)
            settingsRoutes()

            // Ticket/Support routes (WhatsApp-style chat)
            ticketRoutes()

            // Wallet routes (balance, transactions, add funds)
            walletRoutes()

            // Design routes (upload, library, management)
            designRoutes()

            // Email routes (send, logs, templates)
            emailRoutes()

            // Gangsheet routes (generation, status, download)
            gangsheetRoutes()

            // Shipping routes (labels, tracking, rates)
            shippingRoutes()

            // NestShipper integration routes (labels, rates, tracking)
            nestShipperRoutes()

            // Stripe payment routes (wallet, orders, webhooks)
            stripeRoutes()

            // Etsy integration routes
            etsyRoutes()

            // Walmart integration routes
            walmartRoutes()

            // Amazon integration routes
            amazonRoutes()

            // TikTok Shop integration routes
            tikTokRoutes()

            // Shopify integration routes
            shopifyRoutes()

            // AI routes (listing generation, image analysis, etc.)
            aiRoutes()

            // Export routes (Excel export for orders, products, transactions, designs)
            exportRoutes()

            // Monitor/Debug routes (system health, order debug, API logs)
            monitorRoutes()

            // PDF routes (packing slips, invoices, labels)
            pdfRoutes()

            // Digitizing/Embroidery routes (orders, quotes, colors)
            digitizingRoutes()

            // Batch routes (order batches, gangsheet grouping)
            batchRoutes()

            // Mapping routes (order product to variant/design mapping)
            mappingRoutes()

            // Inter-service authentication and webhook routes
            interServiceRoutes()
        }
    }

    logger.info("PrintNest Backend started successfully on port 8080")
}

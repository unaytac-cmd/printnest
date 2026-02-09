package com.printnest.config

import com.printnest.domain.repository.AuthRepository
import com.printnest.domain.repository.CategoryRepository
import com.printnest.domain.repository.MappingRepository
import com.printnest.domain.repository.OrderRepository
import com.printnest.domain.repository.ProductRepository
import com.printnest.domain.repository.ProfileRepository
import com.printnest.domain.repository.SettingsRepository
import com.printnest.domain.repository.ShipStationStoreRepository
import com.printnest.domain.repository.TicketRepository
import com.printnest.domain.repository.WalletRepository
import com.printnest.domain.repository.DesignRepository
import com.printnest.domain.repository.SubdealerRepository
import com.printnest.domain.repository.SubdealerStoreAssignmentRepository
import com.printnest.domain.repository.JobRepository
import com.printnest.domain.repository.EmailRepository
import com.printnest.domain.repository.GangsheetRepository
import com.printnest.domain.repository.ExportRepository
import com.printnest.domain.repository.ApiLogRepository
import com.printnest.domain.repository.DigitizingRepository
import com.printnest.domain.repository.BatchRepository
import com.printnest.domain.service.AuthService
import com.printnest.domain.service.CacheService
import com.printnest.domain.service.MonitorService
import com.printnest.domain.service.CategoryService
import com.printnest.domain.service.OrderService
import com.printnest.domain.service.ProductService
import com.printnest.domain.service.ProfileService
import com.printnest.domain.service.SettingsService
import com.printnest.domain.service.SubdealerService
import com.printnest.domain.service.TicketService
import com.printnest.domain.service.WalletService
import com.printnest.domain.service.DesignService
import com.printnest.domain.service.JobExecutor
import com.printnest.domain.service.SchedulerService
import com.printnest.domain.service.GangsheetService
import com.printnest.domain.service.ExcelService
import com.printnest.domain.service.DigitizingService
import com.printnest.domain.service.PdfService
import com.printnest.domain.service.BatchService
import com.printnest.domain.service.FetchOrderService
import com.printnest.domain.service.MappingService
import com.printnest.domain.service.OrderExportService
import com.printnest.domain.service.LabelService
import com.printnest.domain.service.ExcelImportService
import com.printnest.integrations.aws.S3Service
import com.printnest.integrations.email.EmailService
import com.printnest.integrations.redis.RedisPoolConfig
import com.printnest.integrations.redis.RedisService
import io.ktor.server.config.*
import com.printnest.integrations.shipstation.ShipStationClient
import com.printnest.integrations.shipstation.ShipStationService
import com.printnest.integrations.easypost.EasyPostService
import com.printnest.integrations.nestshipper.NestShipperClient
import com.printnest.integrations.nestshipper.NestShipperService
import com.printnest.integrations.stripe.StripeService
import com.printnest.integrations.walmart.WalmartAuthService
import com.printnest.integrations.walmart.WalmartClient
import com.printnest.integrations.walmart.WalmartService
import com.printnest.integrations.etsy.EtsyAuthService
import com.printnest.integrations.etsy.EtsyClient
import com.printnest.integrations.etsy.EtsyService
import com.printnest.integrations.etsy.EtsyStoreRepository
import com.printnest.integrations.amazon.AmazonAuthService
import com.printnest.integrations.amazon.AmazonClient
import com.printnest.integrations.amazon.AmazonService
import com.printnest.integrations.amazon.AmazonStoreRepository
import com.printnest.integrations.tiktok.TikTokAuthService
import com.printnest.integrations.tiktok.TikTokClient
import com.printnest.integrations.tiktok.TikTokService
import com.printnest.integrations.shopify.ShopifyAuthService
import com.printnest.integrations.shopify.ShopifyClient
import com.printnest.integrations.shopify.ShopifyService
import com.printnest.integrations.shopify.ShopifyStoreRepository
import com.printnest.domain.repository.ShippingRepository
import com.printnest.domain.repository.TikTokStoreRepository
import com.printnest.domain.service.ShippingService
import com.printnest.domain.service.AIService
import com.printnest.integrations.openai.OpenAIService
import com.printnest.integrations.interservice.InterServiceAuth
import com.printnest.integrations.interservice.InterServiceClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("KoinModules")

fun koinModules() = module {
    // JSON serializer
    single {
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    // Default HTTP client
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(get())
            }

            install(Logging) {
                logger = object : Logger {
                    private val log = LoggerFactory.getLogger("HttpClient")
                    override fun log(message: String) {
                        log.debug(message)
                    }
                }
                level = LogLevel.HEADERS
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            expectSuccess = false
        }
    }

    // =====================================================
    // REPOSITORIES
    // =====================================================

    single { ShipStationStoreRepository() }
    single { SubdealerStoreAssignmentRepository() }
    single { EtsyStoreRepository() }
    single { AmazonStoreRepository() }
    single { TikTokStoreRepository() }
    single { ShopifyStoreRepository() }
    single { SubdealerRepository(get(), get()) }
    single { AuthRepository() }
    single { CategoryRepository() }
    single { ProductRepository() }
    single { MappingRepository() }
    single { ProfileRepository() }
    single { OrderRepository() }
    single { SettingsRepository() }
    single { TicketRepository() }
    single { WalletRepository() }
    single { DesignRepository(get()) }
    single { JobRepository() }
    single { EmailRepository() }
    single { GangsheetRepository() }
    single { ShippingRepository() }
    single { ExportRepository() }
    single { ApiLogRepository() }
    single { DigitizingRepository(get()) }
    single { BatchRepository() }

    // =====================================================
    // INTEGRATIONS
    // =====================================================

    // Redis Service
    single {
        val host = System.getenv("REDIS_HOST") ?: "localhost"
        val port = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6380
        val password = System.getenv("REDIS_PASSWORD")?.takeIf { it.isNotBlank() }
        val database = System.getenv("REDIS_DATABASE")?.toIntOrNull() ?: 0
        val ssl = System.getenv("REDIS_SSL")?.toBoolean() ?: false

        val poolConfig = RedisPoolConfig(
            maxTotal = System.getenv("REDIS_POOL_MAX_TOTAL")?.toIntOrNull() ?: 50,
            maxIdle = System.getenv("REDIS_POOL_MAX_IDLE")?.toIntOrNull() ?: 10,
            minIdle = System.getenv("REDIS_POOL_MIN_IDLE")?.toIntOrNull() ?: 5
        )

        RedisService(
            host = host,
            port = port,
            password = password,
            database = database,
            ssl = ssl,
            poolConfig = poolConfig
        )
    }

    single { ShipStationClient(get(), get()) }
    single { ShipStationService(get(), get(), get()) }

    // Etsy Integration
    single { EtsyAuthService(get(), get()) }
    single { EtsyClient(get(), get(), get()) }
    single { EtsyService(get(), get(), get(), get(), get()) }

    // Walmart Integration
    single { WalmartAuthService(get(), get(), get()) }
    single { WalmartClient(get(), get(), get()) }
    single { WalmartService(get(), get(), get(), get(), get()) }

    // Amazon Integration
    single { AmazonAuthService(get(), get(), get()) }
    single { AmazonClient(get(), get(), get()) }
    single { AmazonService(get(), get(), get(), get()) }

    // TikTok Shop Integration
    single { TikTokAuthService(get(), get()) }
    single { TikTokClient(get(), get()) }
    single { TikTokService(get(), get(), get(), get()) }

    // Shopify Integration
    single { ShopifyAuthService(get(), get()) }
    single { ShopifyClient(get(), get()) }
    single { ShopifyService(get(), get(), get(), get(), get()) }

    // Inter-Service Authentication and Client
    single { InterServiceAuth() }
    single { InterServiceClient(get(), get(), get()) }

    // EasyPost Service
    single { EasyPostService(get(), get()) }

    // NestShipper Service
    single { NestShipperClient(get(), get()) }
    single { NestShipperService(get()) }

    // Stripe Service
    single { StripeService(get(), get(), get()) }

    // OpenAI Service
    single { OpenAIService(get(), get()) }

    // Email Service (Brevo/Sendinblue)
    single { EmailService(get(), get(), get()) }

    // AWS S3 Service
    single {
        val accessKeyId = System.getenv("AWS_ACCESS_KEY_ID") ?: ""
        val secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: ""
        val region = System.getenv("AWS_REGION") ?: "us-east-1"
        val bucket = System.getenv("AWS_S3_BUCKET") ?: "printnest-designs"
        val cdnDomain = System.getenv("AWS_S3_CDN_DOMAIN")

        S3Service(
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
            region = region,
            bucket = bucket,
            cdnDomain = cdnDomain
        )
    }

    // =====================================================
    // SERVICES
    // =====================================================

    single { CacheService(get()) }
    single { AuthService(get(), get()) }
    single { SubdealerService(get(), get(), get()) }
    single { CategoryService(get()) }
    single { ProductService(get(), get()) }
    single { ProfileService(get(), get()) }
    single { OrderService(get(), get(), get(), get()) }
    single { SettingsService(get()) }
    single { TicketService(get(), get()) }
    single { WalletService(get(), get()) }
    single { DesignService(get(), get()) }
    single { GangsheetService(get(), get(), get(), get(), get()) }
    single { ShippingService(get(), get(), get(), get(), get()) }
    single { AIService(get(), get(), get()) }
    single { ExcelService(get(), get(), get(), get(), get(), get()) }
    single { MonitorService(get(), get(), get(), get(), get()) }
    single { PdfService(get(), get(), get()) }
    single { DigitizingService(get(), get(), get(), get()) }
    single { BatchService(get(), get(), get(), get()) }
    single { FetchOrderService(get(), get(), get(), get(), get()) }
    single { MappingService(get(), get(), get(), get(), get()) }
    single { OrderExportService(get(), get(), get(), get(), get(), get(), get()) }
    single { LabelService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ExcelImportService(get(), get(), get()) }

    // =====================================================
    // BACKGROUND JOB SCHEDULER
    // =====================================================

    single { JobExecutor(get(), get(), get()) }
    single { SchedulerService(get(), get()) }
}

package com.printnest.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CORS")

/**
 * Configure CORS (Cross-Origin Resource Sharing) for the application.
 */
fun Application.configureCORS() {
    val isProduction = System.getenv("KTOR_ENV") == "production"

    install(CORS) {
        // Production domains
        if (isProduction) {
            allowHost("printnest.com", schemes = listOf("https"))
            allowHost("*.printnest.com", schemes = listOf("https"))
            allowHost("api.printnest.com", schemes = listOf("https"))
            allowHost("admin.printnest.com", schemes = listOf("https"))
            // Allow IP-based access (before domain setup)
            System.getenv("SERVER_IP")?.let { ip ->
                allowHost(ip, schemes = listOf("http", "https"))
                // Also allow nip.io subdomain
                allowHost("$ip.nip.io", schemes = listOf("http", "https"))
                allowHost("*.$ip.nip.io", schemes = listOf("http", "https"))
            }
        } else {
            // Development only
            allowHost("localhost:3000", schemes = listOf("http"))
            allowHost("localhost:5173", schemes = listOf("http"))
            allowHost("127.0.0.1:3000", schemes = listOf("http"))
        }

        // Allowed HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Head)

        // Allowed headers
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.AcceptLanguage)
        allowHeader(HttpHeaders.ContentLanguage)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.Referrer)
        allowHeader(HttpHeaders.UserAgent)
        allowHeader(HttpHeaders.CacheControl)

        // Custom headers
        allowHeader("X-Tenant-ID")
        allowHeader("X-Tenant-Id")
        allowHeader("X-Tenant-Slug")
        allowHeader("X-User-Id")
        allowHeader("X-Request-ID")
        allowHeader("X-Api-Key")

        // Expose headers to client
        exposeHeader(HttpHeaders.Location)
        exposeHeader(HttpHeaders.ContentDisposition)
        exposeHeader("X-Request-ID")
        exposeHeader("X-Total-Count")

        // Allow credentials
        allowCredentials = true

        // Preflight cache
        maxAgeInSeconds = 3600

        // Allow non-simple content types
        allowNonSimpleContentTypes = true
    }

    logger.info("CORS configuration complete (production: $isProduction)")
}

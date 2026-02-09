package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.repository.EmailRepository
import com.printnest.integrations.email.EmailService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.emailRoutes() {
    val emailService: EmailService = GlobalContext.get().get()
    val emailRepository: EmailRepository = GlobalContext.get().get()

    route("/emails") {

        // =====================================================
        // SEND DIRECT EMAIL (Admin only)
        // =====================================================

        /**
         * POST /api/v1/emails/send
         * Send a direct email with custom content
         *
         * Headers:
         * - X-Tenant-Id: Tenant ID
         * - X-User-Id: User ID (admin sending)
         * - X-User-Role: Must be admin/owner/producer
         *
         * Body: SendEmailRequest
         */
        post("/send") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"]?.lowercase() ?: ""

            // Only admins can send direct emails
            if (userRole !in listOf("owner", "admin", "producer", "subscriber")) {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val request = call.receive<SendEmailRequest>()

            // Validate request
            if (request.to.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Recipient email required"))
            }
            if (request.subject.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Subject required"))
            }
            if (request.htmlBody.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email body required"))
            }

            val result = emailService.sendEmail(
                tenantId = tenantId,
                userId = userId,
                to = request.to,
                toName = request.toName,
                subject = request.subject,
                htmlBody = request.htmlBody,
                textBody = request.textBody,
                replyTo = request.replyTo,
                tags = request.tags
            )

            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "messageId" to result.messageId
                ))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "success" to false,
                    "error" to result.error
                ))
            }
        }

        // =====================================================
        // SEND TEMPLATE EMAIL
        // =====================================================

        /**
         * POST /api/v1/emails/send-template
         * Send an email using a predefined template
         *
         * Headers:
         * - X-Tenant-Id: Tenant ID
         * - X-User-Id: User ID
         *
         * Body: SendTemplateEmailRequest
         */
        post("/send-template") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val request = call.receive<SendTemplateEmailRequest>()

            // Validate
            if (request.to.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Recipient email required"))
            }

            val templateId = request.template.templateId
            if (templateId == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Template ID required"))
            }

            val result = emailService.sendTemplateEmail(
                tenantId = tenantId,
                userId = userId,
                to = request.to,
                toName = request.toName,
                templateId = templateId,
                variables = request.variables,
                replyTo = request.replyTo,
                tags = request.tags
            )

            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "messageId" to result.messageId
                ))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "success" to false,
                    "error" to result.error
                ))
            }
        }

        // =====================================================
        // EMAIL LOGS
        // =====================================================

        /**
         * GET /api/v1/emails/logs
         * Get email history with pagination and filters
         *
         * Query params:
         * - page: Page number (default: 1)
         * - limit: Items per page (default: 20)
         * - status: Filter by status code
         * - template: Filter by template name
         * - recipientEmail: Filter by recipient email
         * - startDate: Filter by start date (YYYY-MM-DD)
         * - endDate: Filter by end date (YYYY-MM-DD)
         * - sortBy: Sort field (default: createdAt)
         * - sortOrder: Sort order ASC/DESC (default: DESC)
         */
        get("/logs") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userRole = call.request.headers["X-User-Role"]?.lowercase() ?: ""

            // Only admins can view email logs
            if (userRole !in listOf("owner", "admin", "producer", "subscriber")) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val filters = EmailLogFilters(
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                status = call.request.queryParameters["status"]?.toIntOrNull(),
                template = call.request.queryParameters["template"],
                recipientEmail = call.request.queryParameters["recipientEmail"],
                startDate = call.request.queryParameters["startDate"],
                endDate = call.request.queryParameters["endDate"],
                sortBy = call.request.queryParameters["sortBy"] ?: "createdAt",
                sortOrder = call.request.queryParameters["sortOrder"] ?: "DESC"
            )

            val (logs, total) = emailRepository.findAll(tenantId, filters)
            val totalPages = (total + filters.limit - 1) / filters.limit

            call.respond(EmailLogListResponse(
                logs = logs,
                total = total,
                page = filters.page,
                limit = filters.limit,
                totalPages = totalPages
            ))
        }

        /**
         * GET /api/v1/emails/logs/{id}
         * Get a specific email log by ID
         */
        get("/logs/{id}") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userRole = call.request.headers["X-User-Role"]?.lowercase() ?: ""

            if (userRole !in listOf("owner", "admin", "producer", "subscriber")) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid log ID"))

            val log = emailRepository.findById(id, tenantId)
            if (log != null) {
                call.respond(log)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Email log not found"))
            }
        }

        /**
         * GET /api/v1/emails/stats
         * Get email statistics for the tenant
         */
        get("/stats") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userRole = call.request.headers["X-User-Role"]?.lowercase() ?: ""

            if (userRole !in listOf("owner", "admin", "producer", "subscriber")) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val stats = emailRepository.getStats(tenantId)
            call.respond(mapOf(
                "totalSent" to stats.totalSent,
                "totalFailed" to stats.totalFailed,
                "totalBounced" to stats.totalBounced,
                "totalOpened" to stats.totalOpened,
                "totalClicked" to stats.totalClicked,
                "openRate" to stats.openRate,
                "clickRate" to stats.clickRate,
                "byTemplate" to stats.byTemplate
            ))
        }

        // =====================================================
        // TEST EMAIL
        // =====================================================

        /**
         * POST /api/v1/emails/test
         * Send a test email to verify configuration
         */
        post("/test") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val userId = call.request.headers["X-User-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID required"))

            val userRole = call.request.headers["X-User-Role"]?.lowercase() ?: ""

            if (userRole !in listOf("owner", "admin", "producer", "subscriber")) {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }

            val request = call.receive<TestEmailRequest>()

            if (request.to.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Recipient email required"))
            }

            // Determine which template to test
            val htmlContent = when (request.template?.lowercase()) {
                "welcome" -> emailService.generateWelcomeEmail(
                    WelcomeEmailData(
                        userName = "Test User",
                        activationCode = "TEST123",
                        activationUrl = "https://example.com/activate?code=TEST123"
                    )
                )
                "password_reset", "password-reset" -> emailService.generatePasswordResetEmail(
                    PasswordResetEmailData(
                        userName = "Test User",
                        resetUrl = "https://example.com/reset?token=TEST123"
                    )
                )
                "shipping" -> emailService.generateShippingNotificationEmail(
                    ShippingNotificationEmailData(
                        customerName = "Test Customer",
                        orderNumber = "TEST-001",
                        trackingNumber = "1Z999AA10123456784",
                        trackingUrl = "https://track.example.com/1Z999AA10123456784",
                        carrier = "UPS",
                        estimatedDelivery = "Feb 15, 2026"
                    )
                )
                "ticket" -> emailService.generateTicketReplyEmail(
                    TicketReplyEmailData(
                        userName = "Test User",
                        ticketId = 12345,
                        ticketTitle = "Test Ticket Subject",
                        ticketUrl = "https://example.com/tickets/12345",
                        replyPreview = "This is a test reply message..."
                    )
                )
                else -> """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test Email</title></head>
                    <body style="font-family: Arial, sans-serif; padding: 20px;">
                        <h1 style="color: #2563eb;">PrintNest Test Email</h1>
                        <p>This is a test email from PrintNest.</p>
                        <p>If you received this email, your email configuration is working correctly.</p>
                        <p>Sent at: ${java.time.Instant.now()}</p>
                    </body>
                    </html>
                """.trimIndent()
            }

            val subject = when (request.template?.lowercase()) {
                "welcome" -> "Test: Welcome to PrintNest"
                "password_reset", "password-reset" -> "Test: Reset Your Password"
                "shipping" -> "Test: Your Order Has Shipped!"
                "ticket" -> "Test: New Reply to Your Support Ticket"
                else -> "PrintNest Test Email"
            }

            val result = emailService.sendEmail(
                tenantId = tenantId,
                userId = userId,
                to = request.to,
                subject = subject,
                htmlBody = htmlContent,
                tags = listOf("test")
            )

            if (result.success) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "Test email sent successfully",
                    "messageId" to result.messageId,
                    "template" to (request.template ?: "default")
                ))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "success" to false,
                    "error" to result.error
                ))
            }
        }

        // =====================================================
        // WEBHOOKS (for Brevo callbacks)
        // =====================================================

        /**
         * POST /api/v1/emails/webhook
         * Handle Brevo webhook callbacks for email events
         */
        post("/webhook") {
            // TODO: Implement webhook signature verification
            // For now, just process the event

            val body = call.receiveText()

            // Brevo sends events like: opened, clicked, bounced, etc.
            // Parse and update email log status accordingly
            // This is a placeholder - implement based on Brevo webhook format

            call.respond(HttpStatusCode.OK, mapOf("received" to true))
        }
    }
}

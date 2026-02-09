package com.printnest.integrations.email

import com.printnest.domain.models.*
import com.printnest.domain.repository.EmailRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Email Service using Sendinblue/Brevo API
 *
 * Documentation: https://developers.brevo.com/reference/sendtransacemail
 */
class EmailService(
    private val httpClient: HttpClient,
    private val json: Json,
    private val emailRepository: EmailRepository
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val apiKey: String = System.getenv("SENDINBLUE_API_KEY") ?: ""
    private val apiEndpoint = "https://api.brevo.com/v3/smtp/email"
    private val templateEndpoint = "https://api.brevo.com/v3/smtp/templates"

    private val senderEmail: String = System.getenv("EMAIL_SENDER_ADDRESS") ?: "noreply@printnest.com"
    private val senderName: String = System.getenv("EMAIL_SENDER_NAME") ?: "PrintNest"
    private val platformUrl: String = System.getenv("PLATFORM_URL") ?: "https://printnest.com"
    private val logoUrl: String = System.getenv("PLATFORM_LOGO_URL") ?: "$platformUrl/logo.png"

    // =====================================================
    // DIRECT EMAIL SENDING
    // =====================================================

    /**
     * Send a direct email with custom HTML content
     */
    suspend fun sendEmail(
        tenantId: Long,
        userId: Long? = null,
        to: String,
        toName: String? = null,
        subject: String,
        htmlBody: String,
        textBody: String? = null,
        replyTo: String? = null,
        tags: List<String>? = null
    ): EmailSendResult {
        if (apiKey.isBlank()) {
            logger.warn("Sendinblue API key not configured")
            return EmailSendResult(success = false, error = "Email service not configured")
        }

        val request = BrevoEmailRequest(
            sender = BrevoSender(email = senderEmail, name = senderName),
            to = listOf(BrevoRecipient(email = to, name = toName)),
            subject = subject,
            htmlContent = htmlBody,
            textContent = textBody,
            replyTo = replyTo?.let { BrevoReplyTo(email = it) },
            tags = tags
        )

        return try {
            val response = httpClient.post(apiEndpoint) {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }

            val responseBody = response.bodyAsText()

            if (response.status.isSuccess()) {
                val brevoResponse = json.decodeFromString<BrevoEmailResponse>(responseBody)
                logger.info("Email sent successfully to $to, messageId: ${brevoResponse.messageId}")

                // Log the email
                emailRepository.logEmail(
                    tenantId = tenantId,
                    userId = userId,
                    recipientEmail = to,
                    recipientName = toName,
                    subject = subject,
                    template = null,
                    templateVariables = null,
                    status = EmailStatus.SENT.code,
                    messageId = brevoResponse.messageId
                )

                EmailSendResult(success = true, messageId = brevoResponse.messageId)
            } else {
                val error = "Failed to send email: ${response.status} - $responseBody"
                logger.error(error)

                // Log the failure
                emailRepository.logEmail(
                    tenantId = tenantId,
                    userId = userId,
                    recipientEmail = to,
                    recipientName = toName,
                    subject = subject,
                    template = null,
                    templateVariables = null,
                    status = EmailStatus.FAILED.code,
                    statusMessage = error
                )

                EmailSendResult(success = false, error = error)
            }
        } catch (e: Exception) {
            val error = "Exception sending email: ${e.message}"
            logger.error(error, e)

            // Log the failure
            emailRepository.logEmail(
                tenantId = tenantId,
                userId = userId,
                recipientEmail = to,
                recipientName = toName,
                subject = subject,
                template = null,
                templateVariables = null,
                status = EmailStatus.FAILED.code,
                statusMessage = error
            )

            EmailSendResult(success = false, error = error)
        }
    }

    // =====================================================
    // TEMPLATE EMAIL SENDING
    // =====================================================

    /**
     * Send an email using a Brevo template
     */
    suspend fun sendTemplateEmail(
        tenantId: Long,
        userId: Long? = null,
        to: String,
        toName: String? = null,
        templateId: Int,
        variables: Map<String, String> = emptyMap(),
        replyTo: String? = null,
        tags: List<String>? = null
    ): EmailSendResult {
        if (apiKey.isBlank()) {
            logger.warn("Sendinblue API key not configured")
            return EmailSendResult(success = false, error = "Email service not configured")
        }

        val request = BrevoTemplateEmailRequest(
            sender = BrevoSender(email = senderEmail, name = senderName),
            to = listOf(BrevoRecipient(email = to, name = toName)),
            templateId = templateId,
            params = variables.ifEmpty { null },
            replyTo = replyTo?.let { BrevoReplyTo(email = it) },
            tags = tags
        )

        return try {
            val response = httpClient.post(apiEndpoint) {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }

            val responseBody = response.bodyAsText()

            if (response.status.isSuccess()) {
                val brevoResponse = json.decodeFromString<BrevoEmailResponse>(responseBody)
                logger.info("Template email sent successfully to $to, templateId: $templateId, messageId: ${brevoResponse.messageId}")

                // Log the email
                emailRepository.logEmail(
                    tenantId = tenantId,
                    userId = userId,
                    recipientEmail = to,
                    recipientName = toName,
                    subject = "Template #$templateId",
                    template = templateId.toString(),
                    templateVariables = json.encodeToString(variables),
                    status = EmailStatus.SENT.code,
                    messageId = brevoResponse.messageId
                )

                EmailSendResult(success = true, messageId = brevoResponse.messageId)
            } else {
                val error = "Failed to send template email: ${response.status} - $responseBody"
                logger.error(error)

                emailRepository.logEmail(
                    tenantId = tenantId,
                    userId = userId,
                    recipientEmail = to,
                    recipientName = toName,
                    subject = "Template #$templateId",
                    template = templateId.toString(),
                    templateVariables = json.encodeToString(variables),
                    status = EmailStatus.FAILED.code,
                    statusMessage = error
                )

                EmailSendResult(success = false, error = error)
            }
        } catch (e: Exception) {
            val error = "Exception sending template email: ${e.message}"
            logger.error(error, e)

            emailRepository.logEmail(
                tenantId = tenantId,
                userId = userId,
                recipientEmail = to,
                recipientName = toName,
                subject = "Template #$templateId",
                template = templateId.toString(),
                templateVariables = json.encodeToString(variables),
                status = EmailStatus.FAILED.code,
                statusMessage = error
            )

            EmailSendResult(success = false, error = error)
        }
    }

    // =====================================================
    // EMAIL TEMPLATE GENERATORS
    // =====================================================

    /**
     * Generate Welcome email HTML
     */
    fun generateWelcomeEmail(data: WelcomeEmailData): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to ${data.platformName}</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="${data.platformName}" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #2563eb; text-align: center; margin-bottom: 20px;">Welcome to ${data.platformName}!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.userName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">Thank you for registering with us. Your account has been successfully created.</p>
                    <p style="font-size: 16px; line-height: 1.6;">To activate your account, please click the button below:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="${data.activationUrl}" style="display: inline-block; padding: 14px 30px; background-color: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 16px;">Activate Your Account</a>
                    </div>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">If the button doesn't work, copy and paste this URL into your browser:</p>
                    <p style="font-size: 14px; color: #2563eb; word-break: break-all;">${data.activationUrl}</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">If you have any questions or need assistance, feel free to contact our support team.</p>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">Thank you for choosing ${data.platformName}!</p>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">Best regards,<br>The ${data.platformName} Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Password Reset email HTML
     */
    fun generatePasswordResetEmail(data: PasswordResetEmailData): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="${data.platformName}" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #2563eb; text-align: center; margin-bottom: 20px;">Password Reset</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.userName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">We received a request to reset the password for your account.</p>
                    <p style="font-size: 16px; line-height: 1.6;">If you made this request, please click the button below to reset your password:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="${data.resetUrl}" style="display: inline-block; padding: 14px 30px; background-color: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 16px;">Reset Your Password</a>
                    </div>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">If the button doesn't work, copy and paste this URL into your browser:</p>
                    <p style="font-size: 14px; color: #2563eb; word-break: break-all;">${data.resetUrl}</p>
                    <p style="font-size: 14px; color: #e11d48; line-height: 1.6; margin-top: 20px;"><strong>Note:</strong> This link will expire in ${data.expirationMinutes} minutes.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">If you did not request a password reset, please ignore this email. Your account's security is important to us.</p>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">Best regards,<br>The ${data.platformName} Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Order Confirmation email HTML
     */
    fun generateOrderConfirmationEmail(data: OrderConfirmationEmailData): String {
        val itemsHtml = data.items.joinToString("") { item ->
            """
            <tr>
                <td style="padding: 15px; border-bottom: 1px solid #eee;">
                    ${if (item.imageUrl != null) "<img src=\"${item.imageUrl}\" alt=\"${item.name}\" style=\"width: 60px; height: 60px; object-fit: cover; border-radius: 4px;\">" else ""}
                </td>
                <td style="padding: 15px; border-bottom: 1px solid #eee;">${item.name}</td>
                <td style="padding: 15px; border-bottom: 1px solid #eee; text-align: center;">x${item.quantity}</td>
                <td style="padding: 15px; border-bottom: 1px solid #eee; text-align: right;">${item.price}</td>
            </tr>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Order Confirmation</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="PrintNest" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #16a34a; text-align: center; margin-bottom: 20px;">Order Confirmed!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.customerName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">Thank you for your order! We're excited to get it ready for you.</p>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 14px; color: #666;"><strong>Order Number:</strong> ${data.orderNumber}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>Order Date:</strong> ${data.orderDate}</p>
                        ${if (data.estimatedDelivery != null) "<p style=\"margin: 10px 0 0; font-size: 14px; color: #666;\"><strong>Estimated Delivery:</strong> ${data.estimatedDelivery}</p>" else ""}
                    </div>

                    <h2 style="color: #374151; font-size: 18px; margin: 30px 0 15px;">Order Details</h2>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f8fafc;">
                                <th style="padding: 12px; text-align: left; font-weight: 600;"></th>
                                <th style="padding: 12px; text-align: left; font-weight: 600;">Item</th>
                                <th style="padding: 12px; text-align: center; font-weight: 600;">Qty</th>
                                <th style="padding: 12px; text-align: right; font-weight: 600;">Price</th>
                            </tr>
                        </thead>
                        <tbody>
                            $itemsHtml
                        </tbody>
                    </table>

                    <div style="margin-top: 20px; text-align: right;">
                        <p style="margin: 5px 0; font-size: 14px; color: #666;">Subtotal: ${data.subtotal}</p>
                        <p style="margin: 5px 0; font-size: 14px; color: #666;">Shipping: ${data.shippingCost}</p>
                        <p style="margin: 5px 0; font-size: 14px; color: #666;">Tax: ${data.tax}</p>
                        <p style="margin: 10px 0; font-size: 18px; font-weight: bold; color: #2563eb;">Total: ${data.total}</p>
                    </div>

                    <h2 style="color: #374151; font-size: 18px; margin: 30px 0 15px;">Shipping Address</h2>
                    <p style="font-size: 14px; color: #666; line-height: 1.6; white-space: pre-line;">${data.shippingAddress}</p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6; text-align: center;">If you have any questions about your order, please contact our support team.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Shipping Notification email HTML
     */
    fun generateShippingNotificationEmail(data: ShippingNotificationEmailData): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Your Order Has Shipped!</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="PrintNest" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #16a34a; text-align: center; margin-bottom: 20px;">Your Order Has Shipped!</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.customerName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">Great news! Your order <strong>${data.orderNumber}</strong> is on its way.</p>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 14px; color: #666;"><strong>Carrier:</strong> ${data.carrier}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>Tracking Number:</strong> ${data.trackingNumber}</p>
                        ${if (data.estimatedDelivery != null) "<p style=\"margin: 10px 0 0; font-size: 14px; color: #666;\"><strong>Estimated Delivery:</strong> ${data.estimatedDelivery}</p>" else ""}
                    </div>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="${data.trackingUrl}" style="display: inline-block; padding: 14px 30px; background-color: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 16px;">Track Your Package</a>
                    </div>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6; text-align: center;">Thank you for shopping with us!</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Ticket Reply Notification email HTML
     */
    fun generateTicketReplyEmail(data: TicketReplyEmailData): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>New Reply to Your Support Ticket</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="${data.platformName}" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #2563eb; text-align: center; margin-bottom: 20px;">New Ticket Reply</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.userName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">Your support ticket has received a new reply.</p>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 14px; color: #666;"><strong>Ticket ID:</strong> #${data.ticketId}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>Subject:</strong> ${data.ticketTitle}</p>
                        ${if (data.replyPreview != null) "<p style=\"margin: 10px 0 0; font-size: 14px; color: #666;\"><strong>Preview:</strong> ${data.replyPreview}...</p>" else ""}
                    </div>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="${data.ticketUrl}" style="display: inline-block; padding: 14px 30px; background-color: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 16px;">View Ticket</a>
                    </div>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">If you have more questions, feel free to reply directly to the ticket.</p>
                    <p style="font-size: 14px; color: #666; line-height: 1.6;">Best regards,<br>The ${data.platformName} Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Wallet Transaction email HTML
     */
    fun generateWalletTransactionEmail(data: WalletTransactionEmailData): String {
        val isCredit = data.transactionType.lowercase() == "credit"
        val color = if (isCredit) "#16a34a" else "#e11d48"
        val sign = if (isCredit) "+" else "-"

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Wallet Transaction</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="PrintNest" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #2563eb; text-align: center; margin-bottom: 20px;">Wallet Transaction</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.userName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">A transaction has been made on your wallet.</p>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0; text-align: center;">
                        <p style="margin: 0; font-size: 32px; font-weight: bold; color: $color;">$sign${data.amount}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666; text-transform: uppercase;">${data.transactionType}</p>
                    </div>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 14px; color: #666;"><strong>Description:</strong> ${data.description}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>Date:</strong> ${data.transactionDate}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>New Balance:</strong> ${data.newBalance}</p>
                    </div>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6; text-align: center;">If you did not authorize this transaction, please contact support immediately.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate Refund email HTML
     */
    fun generateRefundEmail(data: RefundEmailData): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Refund Processed</title>
            </head>
            <body style="font-family: 'Arial', sans-serif; color: #333; background-color: #f5f5f5; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; padding: 30px; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <img src="$logoUrl" alt="PrintNest" style="max-width: 200px; height: auto;">
                    </div>
                    <h1 style="color: #16a34a; text-align: center; margin-bottom: 20px;">Refund Processed</h1>
                    <p style="font-size: 16px; line-height: 1.6;">Hello ${data.customerName},</p>
                    <p style="font-size: 16px; line-height: 1.6;">Your refund has been processed successfully.</p>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0; text-align: center;">
                        <p style="margin: 0; font-size: 32px; font-weight: bold; color: #16a34a;">${data.refundAmount}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;">REFUNDED</p>
                    </div>

                    <div style="background-color: #f8fafc; padding: 20px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 14px; color: #666;"><strong>Order Number:</strong> ${data.orderNumber}</p>
                        <p style="margin: 10px 0 0; font-size: 14px; color: #666;"><strong>Refund Date:</strong> ${data.refundDate}</p>
                        ${if (data.refundReason != null) "<p style=\"margin: 10px 0 0; font-size: 14px; color: #666;\"><strong>Reason:</strong> ${data.refundReason}</p>" else ""}
                        ${if (data.originalPaymentMethod != null) "<p style=\"margin: 10px 0 0; font-size: 14px; color: #666;\"><strong>Refund Method:</strong> ${data.originalPaymentMethod}</p>" else ""}
                    </div>

                    <p style="font-size: 14px; color: #666; line-height: 1.6; margin-top: 20px;">Please allow 5-10 business days for the refund to appear on your statement, depending on your financial institution.</p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #666; line-height: 1.6; text-align: center;">If you have any questions, please contact our support team.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    // =====================================================
    // CONVENIENCE METHODS
    // =====================================================

    /**
     * Send welcome email
     */
    suspend fun sendWelcomeEmail(
        tenantId: Long,
        userId: Long,
        to: String,
        userName: String,
        activationCode: String
    ): EmailSendResult {
        val activationUrl = "$platformUrl/activate?code=$activationCode"
        val data = WelcomeEmailData(
            userName = userName,
            activationCode = activationCode,
            activationUrl = activationUrl
        )
        val htmlBody = generateWelcomeEmail(data)

        return sendEmail(
            tenantId = tenantId,
            userId = userId,
            to = to,
            toName = userName,
            subject = EmailTemplate.WELCOME.subject,
            htmlBody = htmlBody,
            tags = listOf("welcome", "activation")
        )
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(
        tenantId: Long,
        userId: Long,
        to: String,
        userName: String,
        resetToken: String
    ): EmailSendResult {
        val resetUrl = "$platformUrl/reset-password?token=$resetToken"
        val data = PasswordResetEmailData(
            userName = userName,
            resetUrl = resetUrl
        )
        val htmlBody = generatePasswordResetEmail(data)

        return sendEmail(
            tenantId = tenantId,
            userId = userId,
            to = to,
            toName = userName,
            subject = EmailTemplate.PASSWORD_RESET.subject,
            htmlBody = htmlBody,
            tags = listOf("password-reset", "security")
        )
    }

    /**
     * Send shipping notification email
     */
    suspend fun sendShippingNotification(
        tenantId: Long,
        to: String,
        customerName: String,
        orderNumber: String,
        trackingNumber: String,
        trackingUrl: String,
        carrier: String,
        estimatedDelivery: String? = null
    ): EmailSendResult {
        val data = ShippingNotificationEmailData(
            customerName = customerName,
            orderNumber = orderNumber,
            trackingNumber = trackingNumber,
            trackingUrl = trackingUrl,
            carrier = carrier,
            estimatedDelivery = estimatedDelivery
        )
        val htmlBody = generateShippingNotificationEmail(data)

        return sendEmail(
            tenantId = tenantId,
            to = to,
            toName = customerName,
            subject = EmailTemplate.SHIPPING_NOTIFICATION.subject,
            htmlBody = htmlBody,
            tags = listOf("shipping", "order")
        )
    }

    /**
     * Send ticket reply notification email
     */
    suspend fun sendTicketReplyNotification(
        tenantId: Long,
        userId: Long,
        to: String,
        userName: String,
        ticketId: Long,
        ticketTitle: String,
        replyPreview: String? = null
    ): EmailSendResult {
        val ticketUrl = "$platformUrl/tickets/$ticketId"
        val data = TicketReplyEmailData(
            userName = userName,
            ticketId = ticketId,
            ticketTitle = ticketTitle,
            ticketUrl = ticketUrl,
            replyPreview = replyPreview?.take(100)
        )
        val htmlBody = generateTicketReplyEmail(data)

        return sendEmail(
            tenantId = tenantId,
            userId = userId,
            to = to,
            toName = userName,
            subject = EmailTemplate.TICKET_REPLY.subject,
            htmlBody = htmlBody,
            tags = listOf("ticket", "support")
        )
    }
}

// =====================================================
// BREVO API REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class BrevoSender(
    val email: String,
    val name: String? = null
)

@Serializable
data class BrevoRecipient(
    val email: String,
    val name: String? = null
)

@Serializable
data class BrevoReplyTo(
    val email: String,
    val name: String? = null
)

@Serializable
data class BrevoEmailRequest(
    val sender: BrevoSender,
    val to: List<BrevoRecipient>,
    val subject: String,
    val htmlContent: String,
    val textContent: String? = null,
    val replyTo: BrevoReplyTo? = null,
    val cc: List<BrevoRecipient>? = null,
    val bcc: List<BrevoRecipient>? = null,
    val tags: List<String>? = null
)

@Serializable
data class BrevoTemplateEmailRequest(
    val sender: BrevoSender,
    val to: List<BrevoRecipient>,
    val templateId: Int,
    val params: Map<String, String>? = null,
    val replyTo: BrevoReplyTo? = null,
    val tags: List<String>? = null
)

@Serializable
data class BrevoEmailResponse(
    val messageId: String? = null
)

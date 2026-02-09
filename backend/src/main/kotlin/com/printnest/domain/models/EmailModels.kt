package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// EMAIL TEMPLATES
// =====================================================

enum class EmailTemplate(val templateId: Int?, val subject: String) {
    WELCOME(1, "Welcome to PrintNest!"),
    PASSWORD_RESET(2, "Reset Your Password"),
    ORDER_CONFIRMATION(3, "Order Confirmation"),
    SHIPPING_NOTIFICATION(4, "Your Order Has Shipped!"),
    TICKET_REPLY(5, "New Reply to Your Support Ticket"),
    WALLET_TRANSACTION(6, "Wallet Transaction Notification"),
    REFUND(7, "Refund Processed"),
    CUSTOM(null, "") // For custom emails without template
}

// =====================================================
// EMAIL STATUS
// =====================================================

enum class EmailStatus(val code: Int) {
    PENDING(0),
    SENT(1),
    FAILED(-1),
    BOUNCED(-2),
    OPENED(2),
    CLICKED(3)
}

// =====================================================
// REQUEST MODELS
// =====================================================

@Serializable
data class SendEmailRequest(
    val to: String,
    val toName: String? = null,
    val subject: String,
    val htmlBody: String,
    val textBody: String? = null,
    val replyTo: String? = null,
    val cc: List<String>? = null,
    val bcc: List<String>? = null,
    val attachments: List<EmailAttachment>? = null,
    val tags: List<String>? = null
)

@Serializable
data class SendTemplateEmailRequest(
    val to: String,
    val toName: String? = null,
    val template: EmailTemplate,
    val variables: Map<String, String> = emptyMap(),
    val replyTo: String? = null,
    val tags: List<String>? = null
)

@Serializable
data class EmailAttachment(
    val name: String,
    val content: String, // Base64 encoded
    val contentType: String? = null
)

@Serializable
data class TestEmailRequest(
    val to: String,
    val template: String? = null
)

// =====================================================
// RESPONSE MODELS
// =====================================================

@Serializable
data class EmailSendResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

@Serializable
data class EmailLog(
    val id: Long,
    val tenantId: Long,
    val userId: Long? = null,
    val recipientEmail: String,
    val recipientName: String? = null,
    val subject: String,
    val template: String? = null,
    val templateVariables: String? = null, // JSON
    val status: Int,
    val statusMessage: String? = null,
    val messageId: String? = null,
    val sentAt: String? = null,
    val openedAt: String? = null,
    val clickedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class EmailLogListResponse(
    val logs: List<EmailLog>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class EmailLogFilters(
    val page: Int = 1,
    val limit: Int = 20,
    val status: Int? = null,
    val template: String? = null,
    val recipientEmail: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC"
)

// =====================================================
// EMAIL CONTENT MODELS (for template generation)
// =====================================================

@Serializable
data class WelcomeEmailData(
    val userName: String,
    val activationCode: String,
    val platformName: String = "PrintNest",
    val activationUrl: String
)

@Serializable
data class PasswordResetEmailData(
    val userName: String,
    val resetUrl: String,
    val platformName: String = "PrintNest",
    val expirationMinutes: Int = 60
)

@Serializable
data class OrderConfirmationEmailData(
    val customerName: String,
    val orderNumber: String,
    val orderDate: String,
    val items: List<OrderItemEmailData>,
    val subtotal: String,
    val shippingCost: String,
    val tax: String,
    val total: String,
    val shippingAddress: String,
    val estimatedDelivery: String? = null
)

@Serializable
data class OrderItemEmailData(
    val name: String,
    val quantity: Int,
    val price: String,
    val imageUrl: String? = null
)

@Serializable
data class ShippingNotificationEmailData(
    val customerName: String,
    val orderNumber: String,
    val trackingNumber: String,
    val trackingUrl: String,
    val carrier: String,
    val estimatedDelivery: String? = null,
    val items: List<OrderItemEmailData>? = null
)

@Serializable
data class TicketReplyEmailData(
    val userName: String,
    val ticketId: Long,
    val ticketTitle: String,
    val ticketUrl: String,
    val replyPreview: String? = null,
    val platformName: String = "PrintNest"
)

@Serializable
data class WalletTransactionEmailData(
    val userName: String,
    val transactionType: String, // "credit" or "debit"
    val amount: String,
    val description: String,
    val newBalance: String,
    val transactionDate: String
)

@Serializable
data class RefundEmailData(
    val customerName: String,
    val orderNumber: String,
    val refundAmount: String,
    val refundReason: String? = null,
    val refundDate: String,
    val originalPaymentMethod: String? = null
)

package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// TICKET STATUS & PRIORITY
// =====================================================

enum class TicketStatus(val code: Int) {
    CLOSED(-1),
    READ(0),
    OPEN(1);

    companion object {
        fun fromCode(code: Int): TicketStatus = entries.find { it.code == code } ?: OPEN
    }
}

enum class TicketPriority(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromValue(value: String): TicketPriority =
            entries.find { it.value == value } ?: MEDIUM
    }
}

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    FILE("file"),
    SYSTEM("system")
}

// =====================================================
// TICKET CATEGORY
// =====================================================

@Serializable
data class TicketCategory(
    val id: Int,
    val name: String,
    val description: String? = null,
    val requiresOrder: Boolean = false,
    val sortOrder: Int = 0,
    val status: Int = 1
)

// =====================================================
// TICKET FULL
// =====================================================

@Serializable
data class Ticket(
    val id: Long,
    val tenantId: Long,
    val createdBy: Long,
    val assignedTo: Long? = null,
    val orderId: Long? = null,
    val categoryId: Int? = null,
    val title: String,
    val status: Int = 1,
    val priority: String = "medium",
    val lastMessageAt: String? = null,
    val lastMessageBy: Long? = null,
    val lastMessagePreview: String? = null,
    val unreadCountCreator: Int = 0,
    val unreadCountAssignee: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String? = null,
    val closedBy: Long? = null,
    // Expanded data
    val category: TicketCategory? = null,
    val creatorName: String? = null,
    val creatorEmail: String? = null,
    val assigneeName: String? = null,
    val assigneeEmail: String? = null,
    val orderNumber: String? = null,
    // Messages for detail view
    val messages: List<TicketMessage> = emptyList(),
    val messageCount: Int = 0
)

@Serializable
data class TicketListItem(
    val id: Long,
    val tenantId: Long,
    val title: String,
    val status: Int,
    val priority: String,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val orderId: Long? = null,
    val orderNumber: String? = null,
    val createdBy: Long,
    val creatorName: String? = null,
    val assignedTo: Long? = null,
    val assigneeName: String? = null,
    val lastMessageAt: String? = null,
    val lastMessagePreview: String? = null,
    val unreadCount: Int = 0, // Based on current user perspective
    val createdAt: String,
    val updatedAt: String
)

// =====================================================
// TICKET MESSAGE
// =====================================================

@Serializable
data class TicketMessage(
    val id: Long,
    val ticketId: Long,
    val senderId: Long,
    val senderName: String? = null,
    val senderEmail: String? = null,
    val senderRole: String? = null,
    val content: String,
    val messageType: String = "text",
    val isSystemMessage: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val editedAt: String? = null,
    val createdAt: String,
    // Attachments
    val attachments: List<TicketAttachment> = emptyList(),
    // Read status for current user
    val isRead: Boolean = true
)

@Serializable
data class TicketAttachment(
    val id: Long,
    val messageId: Long,
    val fileName: String,
    val fileUrl: String,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val thumbnailUrl: String? = null,
    val createdAt: String
)

// =====================================================
// REQUEST/RESPONSE MODELS
// =====================================================

@Serializable
data class CreateTicketRequest(
    val orderId: Long? = null,
    val categoryId: Int? = null,
    val title: String,
    val message: String,
    val priority: String = "medium",
    val attachments: List<AttachmentInput> = emptyList()
)

@Serializable
data class AttachmentInput(
    val fileName: String,
    val fileUrl: String,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class SendMessageRequest(
    val content: String,
    val messageType: String = "text",
    val attachments: List<AttachmentInput> = emptyList()
)

@Serializable
data class UpdateTicketRequest(
    val status: Int? = null,
    val priority: String? = null,
    val assignedTo: Long? = null
)

@Serializable
data class TicketListResponse(
    val tickets: List<TicketListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class TicketFilters(
    val status: Int? = null,
    val statuses: List<Int>? = null,
    val categoryId: Int? = null,
    val orderId: Long? = null,
    val createdBy: Long? = null,
    val assignedTo: Long? = null,
    val search: String? = null,
    val page: Int = 1,
    val limit: Int = 20,
    val sortBy: String = "lastMessageAt",
    val sortOrder: String = "DESC"
)

// =====================================================
// TICKET STATS
// =====================================================

@Serializable
data class TicketStats(
    val totalOpen: Int = 0,
    val totalUnread: Int = 0,
    val totalClosed: Int = 0,
    val byCategory: Map<String, Int> = emptyMap(),
    val byPriority: Map<String, Int> = emptyMap()
)

// =====================================================
// NOTIFICATION HELPER
// =====================================================

@Serializable
data class TicketNotification(
    val ticketId: Long,
    val ticketTitle: String,
    val senderName: String,
    val messagePreview: String,
    val isNewTicket: Boolean = false
)

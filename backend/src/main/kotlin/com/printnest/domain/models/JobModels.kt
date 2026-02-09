package com.printnest.domain.models

import kotlinx.serialization.Serializable
import java.time.Instant

// =====================================================
// JOB STATUS ENUM
// =====================================================

enum class JobStatus(val code: Int) {
    PENDING(0),
    RUNNING(1),
    COMPLETED(2),
    FAILED(-1);

    companion object {
        fun fromCode(code: Int): JobStatus = entries.find { it.code == code } ?: PENDING
    }
}

// =====================================================
// JOB TYPE ENUM
// =====================================================

enum class JobType(val value: String) {
    ORDER_SYNC("order_sync"),
    GANGSHEET_GENERATE("gangsheet_generate"),
    EMAIL_SEND("email_send"),
    TRACKING_UPDATE("tracking_update"),
    CLEANUP("cleanup"),
    REPORT_GENERATE("report_generate");

    companion object {
        fun fromValue(value: String): JobType? = entries.find { it.value == value }
    }
}

// =====================================================
// SCHEDULED JOB
// =====================================================

@Serializable
data class ScheduledJob(
    val id: Long,
    val tenantId: Long? = null,
    val jobType: String,
    val jobStatus: Int = JobStatus.PENDING.code,
    val jobData: JobData = JobData(),
    val scheduledAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String
) {
    val status: JobStatus
        get() = JobStatus.fromCode(jobStatus)

    val type: JobType?
        get() = JobType.fromValue(jobType)
}

// =====================================================
// JOB DATA (JSONB)
// =====================================================

@Serializable
data class JobData(
    // ORDER_SYNC specific
    val storeId: Long? = null,
    val shipstationStoreId: Long? = null,

    // GANGSHEET_GENERATE specific
    val gangsheetId: Long? = null,
    val orderIds: List<Long>? = null,

    // EMAIL_SEND specific
    val emailTo: String? = null,
    val emailSubject: String? = null,
    val emailTemplate: String? = null,
    val emailData: Map<String, String>? = null,

    // TRACKING_UPDATE specific
    val orderId: Long? = null,
    val trackingNumber: String? = null,

    // General
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val priority: Int = 0,
    val metadata: Map<String, String>? = null
)

// =====================================================
// JOB RESULT
// =====================================================

@Serializable
data class JobResult(
    val jobId: Long,
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, String>? = null,
    val errorCode: String? = null,
    val errorDetails: String? = null,
    val durationMs: Long = 0,
    val processedAt: String = Instant.now().toString()
)

// =====================================================
// JOB REQUESTS
// =====================================================

@Serializable
data class CreateJobRequest(
    val jobType: String,
    val tenantId: Long? = null,
    val jobData: JobData = JobData(),
    val scheduledAt: String? = null
)

@Serializable
data class JobFilters(
    val tenantId: Long? = null,
    val jobType: String? = null,
    val status: Int? = null,
    val page: Int = 1,
    val limit: Int = 50
)

@Serializable
data class JobListResponse(
    val jobs: List<ScheduledJob>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// =====================================================
// SCHEDULED TASK (In-memory representation)
// =====================================================

data class ScheduledTask(
    val id: String,
    val jobType: JobType,
    val cronExpression: String? = null,
    val intervalMs: Long? = null,
    val handler: suspend () -> JobResult,
    val isRecurring: Boolean = false,
    var isRunning: Boolean = false,
    var lastRunAt: Instant? = null,
    var nextRunAt: Instant? = null
)

// =====================================================
// JOB STATISTICS
// =====================================================

@Serializable
data class JobStatistics(
    val totalJobs: Int = 0,
    val pendingJobs: Int = 0,
    val runningJobs: Int = 0,
    val completedJobs: Int = 0,
    val failedJobs: Int = 0,
    val jobsByType: Map<String, Int> = emptyMap(),
    val averageDurationMs: Long = 0,
    val lastExecutedAt: String? = null
)

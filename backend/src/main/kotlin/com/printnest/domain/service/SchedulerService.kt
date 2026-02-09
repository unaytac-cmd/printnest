package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.JobRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Background Job Scheduler Service
 *
 * Uses Kotlin coroutines for non-blocking job execution.
 * Supports both recurring scheduled jobs and one-time delayed jobs.
 */
class SchedulerService(
    private val jobRepository: JobRepository,
    private val jobExecutor: JobExecutor
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)

    // In-memory task registry for recurring jobs
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledTask>()

    // Active job coroutines
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // Polling interval for pending jobs from database
    private val pollingIntervalMs = 10_000L // 10 seconds

    // =====================================================
    // LIFECYCLE
    // =====================================================

    /**
     * Start the scheduler service
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting Scheduler Service...")

            // Start the main polling loop
            scope.launch {
                pollPendingJobs()
            }

            // Start recurring task executor
            scope.launch {
                executeRecurringTasks()
            }

            // Schedule default recurring jobs
            scheduleDefaultJobs()

            logger.info("Scheduler Service started successfully")
        } else {
            logger.warn("Scheduler Service is already running")
        }
    }

    /**
     * Stop the scheduler service gracefully
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping Scheduler Service...")

            // Cancel all active jobs
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            // Clear scheduled tasks
            scheduledTasks.clear()

            // Cancel the scope
            scope.cancel()

            logger.info("Scheduler Service stopped")
        }
    }

    // =====================================================
    // JOB SCHEDULING
    // =====================================================

    /**
     * Schedule a recurring job with a cron-like expression
     *
     * @param jobType The type of job to schedule
     * @param cronExpression Simple cron expression (supports: every_minute, hourly, daily, weekly)
     * @param handler The coroutine handler to execute
     * @return Task ID
     */
    fun scheduleJob(
        jobType: JobType,
        cronExpression: String,
        handler: suspend () -> JobResult
    ): String {
        val taskId = "${jobType.value}_${System.currentTimeMillis()}"
        val intervalMs = parseCronExpression(cronExpression)

        val task = ScheduledTask(
            id = taskId,
            jobType = jobType,
            cronExpression = cronExpression,
            intervalMs = intervalMs,
            handler = handler,
            isRecurring = true,
            nextRunAt = Instant.now().plusMillis(intervalMs)
        )

        scheduledTasks[taskId] = task
        logger.info("Scheduled recurring job: $taskId (type: ${jobType.value}, interval: ${intervalMs}ms)")

        return taskId
    }

    /**
     * Schedule a one-time job to run after a delay
     *
     * @param jobType The type of job
     * @param delayMs Delay in milliseconds before execution
     * @param handler The coroutine handler to execute
     * @return Task ID
     */
    fun scheduleOnce(
        jobType: JobType,
        delayMs: Long,
        handler: suspend () -> JobResult
    ): String {
        val taskId = "${jobType.value}_once_${System.currentTimeMillis()}"

        val task = ScheduledTask(
            id = taskId,
            jobType = jobType,
            intervalMs = delayMs,
            handler = handler,
            isRecurring = false,
            nextRunAt = Instant.now().plusMillis(delayMs)
        )

        scheduledTasks[taskId] = task
        logger.info("Scheduled one-time job: $taskId (type: ${jobType.value}, delay: ${delayMs}ms)")

        // Launch the one-time task
        scope.launch {
            delay(delayMs)
            if (scheduledTasks.containsKey(taskId)) {
                executeTask(task)
                scheduledTasks.remove(taskId)
            }
        }

        return taskId
    }

    /**
     * Schedule a job from database record
     *
     * @param tenantId Tenant ID
     * @param jobType Job type
     * @param jobData Job data
     * @param scheduledAt When to execute (null = immediately)
     * @return Created job
     */
    fun scheduleDbJob(
        tenantId: Long,
        jobType: JobType,
        jobData: JobData,
        scheduledAt: Instant? = null
    ): ScheduledJob {
        return jobRepository.createWithTenant(tenantId, jobType, jobData, scheduledAt)
    }

    /**
     * Cancel a scheduled job
     *
     * @param taskId The task ID to cancel
     * @return true if cancelled, false if not found
     */
    fun cancelJob(taskId: String): Boolean {
        val removed = scheduledTasks.remove(taskId)
        if (removed != null) {
            logger.info("Cancelled scheduled job: $taskId")
            return true
        }
        return false
    }

    /**
     * Cancel a database job by ID
     */
    fun cancelDbJob(jobId: Long): Boolean {
        // Cancel if currently running
        activeJobs[jobId]?.cancel()
        activeJobs.remove(jobId)

        // Delete from database
        return jobRepository.delete(jobId)
    }

    // =====================================================
    // JOB QUERIES
    // =====================================================

    /**
     * Get all currently running jobs
     */
    fun getRunningJobs(): List<ScheduledJob> {
        return jobRepository.findRunningJobs()
    }

    /**
     * Get job history with filters
     */
    fun getJobHistory(filters: JobFilters): JobListResponse {
        val (jobs, total) = jobRepository.findAll(filters)
        val totalPages = (total + filters.limit - 1) / filters.limit

        return JobListResponse(
            jobs = jobs,
            total = total,
            page = filters.page,
            limit = filters.limit,
            totalPages = totalPages
        )
    }

    /**
     * Get scheduled tasks (in-memory)
     */
    fun getScheduledTasks(): List<ScheduledTask> {
        return scheduledTasks.values.toList()
    }

    /**
     * Get job statistics
     */
    fun getStatistics(tenantId: Long? = null): JobStatistics {
        return jobRepository.getStatistics(tenantId)
    }

    // =====================================================
    // INTERNAL - POLLING & EXECUTION
    // =====================================================

    /**
     * Main polling loop for database jobs
     */
    private suspend fun pollPendingJobs() {
        while (isRunning.get()) {
            try {
                val pendingJobs = jobRepository.findPendingJobs(limit = 10)

                pendingJobs.forEach { job ->
                    if (!activeJobs.containsKey(job.id)) {
                        launchDatabaseJob(job)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error polling pending jobs", e)
            }

            delay(pollingIntervalMs)
        }
    }

    /**
     * Execute recurring tasks based on their schedules
     */
    private suspend fun executeRecurringTasks() {
        while (isRunning.get()) {
            val now = Instant.now()

            scheduledTasks.values
                .filter { it.isRecurring && !it.isRunning }
                .filter { it.nextRunAt != null && it.nextRunAt!! <= now }
                .forEach { task ->
                    scope.launch {
                        executeTask(task)
                        // Schedule next run
                        task.nextRunAt = Instant.now().plusMillis(task.intervalMs ?: 60_000L)
                    }
                }

            delay(1.seconds)
        }
    }

    /**
     * Launch a job from database
     */
    private fun launchDatabaseJob(job: ScheduledJob) {
        val coroutine = scope.launch {
            try {
                // Mark as running
                jobRepository.markAsRunning(job.id)

                val startTime = System.currentTimeMillis()

                // Execute based on job type
                val result = when (job.type) {
                    JobType.ORDER_SYNC -> jobExecutor.executeOrderSync(
                        job.tenantId ?: 0,
                        job.jobData.shipstationStoreId ?: 0
                    )
                    JobType.GANGSHEET_GENERATE -> jobExecutor.executeGangsheetGeneration(
                        job.tenantId ?: 0,
                        job.jobData.gangsheetId ?: 0
                    )
                    JobType.EMAIL_SEND -> jobExecutor.executeEmailQueue()
                    JobType.TRACKING_UPDATE -> jobExecutor.executeTrackingUpdate(
                        job.tenantId ?: 0,
                        job.jobData.orderId ?: 0
                    )
                    JobType.CLEANUP -> jobExecutor.executeCleanup()
                    JobType.REPORT_GENERATE -> jobExecutor.executeReportGeneration(
                        job.tenantId ?: 0,
                        job.jobData
                    )
                    null -> JobResult(
                        jobId = job.id,
                        success = false,
                        message = "Unknown job type: ${job.jobType}"
                    )
                }

                val duration = System.currentTimeMillis() - startTime

                if (result.success) {
                    jobRepository.markAsCompleted(job.id)
                    logger.info("Job ${job.id} (${job.jobType}) completed in ${duration}ms")
                } else {
                    // Check retry count
                    if (job.jobData.retryCount < job.jobData.maxRetries) {
                        val newJobData = job.jobData.copy(retryCount = job.jobData.retryCount + 1)
                        jobRepository.updateJobData(job.id, newJobData)
                        jobRepository.updateStatus(job.id, JobStatus.PENDING)
                        logger.warn("Job ${job.id} failed, scheduling retry ${newJobData.retryCount}/${newJobData.maxRetries}")
                    } else {
                        jobRepository.markAsFailed(job.id, result.message ?: "Unknown error")
                        logger.error("Job ${job.id} (${job.jobType}) failed after ${job.jobData.maxRetries} retries: ${result.message}")
                    }
                }
            } catch (e: CancellationException) {
                logger.info("Job ${job.id} was cancelled")
                jobRepository.markAsFailed(job.id, "Job cancelled")
            } catch (e: Exception) {
                logger.error("Job ${job.id} (${job.jobType}) failed with exception", e)
                jobRepository.markAsFailed(job.id, e.message ?: "Unknown error")
            } finally {
                activeJobs.remove(job.id)
            }
        }

        activeJobs[job.id] = coroutine
    }

    /**
     * Execute a scheduled task
     */
    private suspend fun executeTask(task: ScheduledTask) {
        if (task.isRunning) return

        try {
            task.isRunning = true
            task.lastRunAt = Instant.now()

            logger.debug("Executing task: ${task.id}")

            val result = task.handler()

            if (result.success) {
                logger.debug("Task ${task.id} completed successfully")
            } else {
                logger.warn("Task ${task.id} completed with error: ${result.message}")
            }
        } catch (e: Exception) {
            logger.error("Task ${task.id} failed with exception", e)
        } finally {
            task.isRunning = false
        }
    }

    // =====================================================
    // DEFAULT JOBS
    // =====================================================

    /**
     * Schedule default recurring jobs
     */
    private fun scheduleDefaultJobs() {
        // Cleanup old completed jobs every day at midnight
        scheduleJob(JobType.CLEANUP, "daily") {
            jobExecutor.executeCleanup()
        }

        logger.info("Default recurring jobs scheduled")
    }

    // =====================================================
    // HELPER
    // =====================================================

    /**
     * Parse simple cron expression to interval in milliseconds
     */
    private fun parseCronExpression(expression: String): Long {
        return when (expression.lowercase()) {
            "every_minute", "* * * * *" -> 60_000L
            "every_5_minutes" -> 5 * 60_000L
            "every_15_minutes" -> 15 * 60_000L
            "every_30_minutes" -> 30 * 60_000L
            "hourly", "0 * * * *" -> 60 * 60_000L
            "every_6_hours" -> 6 * 60 * 60_000L
            "every_12_hours" -> 12 * 60 * 60_000L
            "daily", "0 0 * * *" -> 24 * 60 * 60_000L
            "weekly", "0 0 * * 0" -> 7 * 24 * 60 * 60_000L
            else -> {
                // Try to parse as milliseconds
                expression.toLongOrNull() ?: 60_000L
            }
        }
    }
}

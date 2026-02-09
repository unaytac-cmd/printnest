package com.printnest.domain.repository

import com.printnest.domain.models.*
import com.printnest.domain.tables.ScheduledJobs
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class JobRepository : KoinComponent {

    private val json: Json by inject()

    // =====================================================
    // CREATE
    // =====================================================

    fun create(request: CreateJobRequest): ScheduledJob = transaction {
        val jobDataJson = json.encodeToString(JobData.serializer(), request.jobData)
        val scheduledAtInstant = request.scheduledAt?.let { Instant.parse(it) }

        val id = ScheduledJobs.insertAndGetId {
            it[tenantId] = request.tenantId
            it[jobType] = request.jobType
            it[jobStatus] = JobStatus.PENDING.code
            it[jobData] = jobDataJson
            it[scheduledAt] = scheduledAtInstant
        }

        findById(id.value)!!
    }

    fun createWithTenant(tenantId: Long, jobType: JobType, jobData: JobData, scheduledAt: Instant? = null): ScheduledJob = transaction {
        val jobDataJson = json.encodeToString(JobData.serializer(), jobData)

        val id = ScheduledJobs.insertAndGetId {
            it[this.tenantId] = tenantId
            it[this.jobType] = jobType.value
            it[jobStatus] = JobStatus.PENDING.code
            it[this.jobData] = jobDataJson
            it[this.scheduledAt] = scheduledAt
        }

        findById(id.value)!!
    }

    // =====================================================
    // READ
    // =====================================================

    fun findById(id: Long): ScheduledJob? = transaction {
        ScheduledJobs.selectAll()
            .where { ScheduledJobs.id eq id }
            .singleOrNull()
            ?.toScheduledJob()
    }

    fun findAll(filters: JobFilters): Pair<List<ScheduledJob>, Int> = transaction {
        var query = ScheduledJobs.selectAll()

        filters.tenantId?.let { tid ->
            query = query.andWhere { ScheduledJobs.tenantId eq tid }
        }

        filters.jobType?.let { type ->
            query = query.andWhere { ScheduledJobs.jobType eq type }
        }

        filters.status?.let { status ->
            query = query.andWhere { ScheduledJobs.jobStatus eq status }
        }

        val total = query.count().toInt()

        val offset = ((filters.page - 1) * filters.limit).toLong()
        query = query
            .orderBy(ScheduledJobs.createdAt, SortOrder.DESC)
            .limit(filters.limit)
            .offset(offset)

        val jobs = query.map { it.toScheduledJob() }

        Pair(jobs, total)
    }

    fun findPendingJobs(limit: Int = 100): List<ScheduledJob> = transaction {
        val now = Instant.now()

        ScheduledJobs.selectAll()
            .where {
                (ScheduledJobs.jobStatus eq JobStatus.PENDING.code) and
                ((ScheduledJobs.scheduledAt.isNull()) or (ScheduledJobs.scheduledAt lessEq now))
            }
            .orderBy(ScheduledJobs.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toScheduledJob() }
    }

    fun findRunningJobs(): List<ScheduledJob> = transaction {
        ScheduledJobs.selectAll()
            .where { ScheduledJobs.jobStatus eq JobStatus.RUNNING.code }
            .orderBy(ScheduledJobs.startedAt, SortOrder.ASC)
            .map { it.toScheduledJob() }
    }

    fun findByTenantAndType(tenantId: Long, jobType: JobType, limit: Int = 50): List<ScheduledJob> = transaction {
        ScheduledJobs.selectAll()
            .where {
                (ScheduledJobs.tenantId eq tenantId) and
                (ScheduledJobs.jobType eq jobType.value)
            }
            .orderBy(ScheduledJobs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toScheduledJob() }
    }

    fun findRecentByTenant(tenantId: Long, limit: Int = 50): List<ScheduledJob> = transaction {
        ScheduledJobs.selectAll()
            .where { ScheduledJobs.tenantId eq tenantId }
            .orderBy(ScheduledJobs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toScheduledJob() }
    }

    // =====================================================
    // UPDATE
    // =====================================================

    fun updateStatus(id: Long, status: JobStatus, errorMessage: String? = null): Boolean = transaction {
        ScheduledJobs.update(
            where = { ScheduledJobs.id eq id }
        ) {
            it[jobStatus] = status.code
            when (status) {
                JobStatus.RUNNING -> it[startedAt] = Instant.now()
                JobStatus.COMPLETED, JobStatus.FAILED -> it[completedAt] = Instant.now()
                else -> {}
            }
            errorMessage?.let { msg -> it[this.errorMessage] = msg }
        } > 0
    }

    fun markAsRunning(id: Long): Boolean = transaction {
        ScheduledJobs.update(
            where = { ScheduledJobs.id eq id }
        ) {
            it[jobStatus] = JobStatus.RUNNING.code
            it[startedAt] = Instant.now()
        } > 0
    }

    fun markAsCompleted(id: Long): Boolean = transaction {
        ScheduledJobs.update(
            where = { ScheduledJobs.id eq id }
        ) {
            it[jobStatus] = JobStatus.COMPLETED.code
            it[completedAt] = Instant.now()
        } > 0
    }

    fun markAsFailed(id: Long, errorMessage: String): Boolean = transaction {
        ScheduledJobs.update(
            where = { ScheduledJobs.id eq id }
        ) {
            it[jobStatus] = JobStatus.FAILED.code
            it[completedAt] = Instant.now()
            it[this.errorMessage] = errorMessage
        } > 0
    }

    fun updateJobData(id: Long, jobData: JobData): Boolean = transaction {
        ScheduledJobs.update(
            where = { ScheduledJobs.id eq id }
        ) {
            it[this.jobData] = json.encodeToString(JobData.serializer(), jobData)
        } > 0
    }

    // =====================================================
    // DELETE
    // =====================================================

    fun delete(id: Long): Boolean = transaction {
        ScheduledJobs.deleteWhere { ScheduledJobs.id eq id } > 0
    }

    fun deleteOldJobs(olderThanDays: Int = 30): Int = transaction {
        val cutoff = Instant.now().minusSeconds(olderThanDays.toLong() * 24 * 60 * 60)

        ScheduledJobs.deleteWhere {
            (ScheduledJobs.jobStatus eq JobStatus.COMPLETED.code) and
            (ScheduledJobs.completedAt lessEq cutoff)
        }
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    fun getStatistics(tenantId: Long? = null): JobStatistics = transaction {
        val baseQuery = if (tenantId != null) {
            ScheduledJobs.selectAll().where { ScheduledJobs.tenantId eq tenantId }
        } else {
            ScheduledJobs.selectAll()
        }

        val total = baseQuery.count().toInt()

        val pending = ScheduledJobs.selectAll()
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .andWhere { ScheduledJobs.jobStatus eq JobStatus.PENDING.code }
            .count().toInt()

        val running = ScheduledJobs.selectAll()
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .andWhere { ScheduledJobs.jobStatus eq JobStatus.RUNNING.code }
            .count().toInt()

        val completed = ScheduledJobs.selectAll()
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .andWhere { ScheduledJobs.jobStatus eq JobStatus.COMPLETED.code }
            .count().toInt()

        val failed = ScheduledJobs.selectAll()
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .andWhere { ScheduledJobs.jobStatus eq JobStatus.FAILED.code }
            .count().toInt()

        // Get jobs by type
        val jobsByType = ScheduledJobs.select(ScheduledJobs.jobType, ScheduledJobs.id.count())
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .groupBy(ScheduledJobs.jobType)
            .associate { it[ScheduledJobs.jobType] to it[ScheduledJobs.id.count()].toInt() }

        // Get last executed job
        val lastExecuted = ScheduledJobs.selectAll()
            .apply { tenantId?.let { andWhere { ScheduledJobs.tenantId eq it } } }
            .andWhere { ScheduledJobs.completedAt.isNotNull() }
            .orderBy(ScheduledJobs.completedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(ScheduledJobs.completedAt)
            ?.toString()

        JobStatistics(
            totalJobs = total,
            pendingJobs = pending,
            runningJobs = running,
            completedJobs = completed,
            failedJobs = failed,
            jobsByType = jobsByType,
            lastExecutedAt = lastExecuted
        )
    }

    // =====================================================
    // MAPPER
    // =====================================================

    private fun ResultRow.toScheduledJob(): ScheduledJob {
        val jobDataJson = this[ScheduledJobs.jobData]
        val jobData = try {
            if (jobDataJson.isNotEmpty() && jobDataJson != "{}") {
                json.decodeFromString<JobData>(jobDataJson)
            } else JobData()
        } catch (e: Exception) { JobData() }

        return ScheduledJob(
            id = this[ScheduledJobs.id].value,
            tenantId = this[ScheduledJobs.tenantId]?.value,
            jobType = this[ScheduledJobs.jobType],
            jobStatus = this[ScheduledJobs.jobStatus],
            jobData = jobData,
            scheduledAt = this[ScheduledJobs.scheduledAt]?.toString(),
            startedAt = this[ScheduledJobs.startedAt]?.toString(),
            completedAt = this[ScheduledJobs.completedAt]?.toString(),
            errorMessage = this[ScheduledJobs.errorMessage],
            createdAt = this[ScheduledJobs.createdAt].toString()
        )
    }
}

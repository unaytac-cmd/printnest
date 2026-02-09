package com.printnest.integrations.aws

import com.printnest.domain.repository.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * S3Service with per-tenant credentials
 *
 * Fetches AWS credentials from tenant settings in the database.
 * Maintains a cache of S3 clients per tenant for performance.
 */
class S3Service : KoinComponent {
    private val logger = LoggerFactory.getLogger(S3Service::class.java)
    private val settingsRepository: SettingsRepository by inject()

    // Cache S3 clients per tenant to avoid recreating them on every call
    private val clientCache = ConcurrentHashMap<Long, S3ClientWrapper>()
    private val presignerCache = ConcurrentHashMap<Long, S3PresignerWrapper>()

    companion object {
        private const val DESIGN_PREFIX = "designs"
        private const val THUMBNAIL_PREFIX = "thumbnails"
        private const val TEMP_PREFIX = "temp"
        private const val EXPORTS_PREFIX = "exports"
        private const val DEFAULT_EXPIRATION_SECONDS = 3600L // 1 hour
        private const val CLIENT_CACHE_TTL_MS = 300_000L // 5 minutes
    }

    // =====================================================
    // CLIENT MANAGEMENT
    // =====================================================

    private data class S3ClientWrapper(
        val client: S3Client,
        val bucket: String,
        val region: String,
        val cdnDomain: String?,
        val createdAt: Long = System.currentTimeMillis()
    )

    private data class S3PresignerWrapper(
        val presigner: S3Presigner,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * Get or create S3 client for a tenant
     */
    private fun getS3Client(tenantId: Long): S3ClientWrapper? {
        // Check cache first
        val cached = clientCache[tenantId]
        if (cached != null && System.currentTimeMillis() - cached.createdAt < CLIENT_CACHE_TTL_MS) {
            return cached
        }

        // Fetch credentials from database
        val settings = settingsRepository.getTenantSettings(tenantId)?.awsSettings
        if (settings == null) {
            logger.warn("No AWS settings configured for tenant $tenantId")
            return null
        }

        val accessKeyId = settings.accessKeyId
        val secretAccessKey = settings.secretAccessKey
        val bucket = settings.s3Bucket

        if (accessKeyId.isNullOrBlank() || secretAccessKey.isNullOrBlank() || bucket.isNullOrBlank()) {
            logger.warn("Incomplete AWS credentials for tenant $tenantId")
            return null
        }

        return try {
            val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            val credentialsProvider = StaticCredentialsProvider.create(credentials)
            val region = settings.region

            val client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()

            val wrapper = S3ClientWrapper(
                client = client,
                bucket = bucket,
                region = region,
                cdnDomain = settings.cdnDomain
            )

            clientCache[tenantId] = wrapper
            logger.info("Created S3 client for tenant $tenantId, bucket: $bucket, region: $region")
            wrapper
        } catch (e: Exception) {
            logger.error("Failed to create S3 client for tenant $tenantId", e)
            null
        }
    }

    /**
     * Get or create S3 presigner for a tenant
     */
    private fun getPresigner(tenantId: Long): S3Presigner? {
        // Check cache first
        val cached = presignerCache[tenantId]
        if (cached != null && System.currentTimeMillis() - cached.createdAt < CLIENT_CACHE_TTL_MS) {
            return cached.presigner
        }

        // Fetch credentials from database
        val settings = settingsRepository.getTenantSettings(tenantId)?.awsSettings
        if (settings == null) {
            logger.warn("No AWS settings configured for tenant $tenantId")
            return null
        }

        val accessKeyId = settings.accessKeyId
        val secretAccessKey = settings.secretAccessKey

        if (accessKeyId.isNullOrBlank() || secretAccessKey.isNullOrBlank()) {
            logger.warn("Incomplete AWS credentials for tenant $tenantId")
            return null
        }

        return try {
            val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            val credentialsProvider = StaticCredentialsProvider.create(credentials)
            val region = settings.region

            val presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()

            presignerCache[tenantId] = S3PresignerWrapper(presigner)
            presigner
        } catch (e: Exception) {
            logger.error("Failed to create S3 presigner for tenant $tenantId", e)
            null
        }
    }

    /**
     * Check if S3 is configured for a tenant
     */
    fun isConfigured(tenantId: Long): Boolean {
        val settings = settingsRepository.getTenantSettings(tenantId)?.awsSettings
        return settings != null &&
                !settings.accessKeyId.isNullOrBlank() &&
                !settings.secretAccessKey.isNullOrBlank() &&
                !settings.s3Bucket.isNullOrBlank()
    }

    /**
     * Validate S3 credentials for a tenant
     */
    fun validateCredentials(tenantId: Long): Boolean {
        val clientWrapper = getS3Client(tenantId) ?: return false
        return try {
            clientWrapper.client.listBuckets()
            true
        } catch (e: Exception) {
            logger.error("S3 credential validation failed for tenant $tenantId", e)
            false
        }
    }

    // =====================================================
    // PRE-SIGNED URL GENERATION
    // =====================================================

    /**
     * Generate a pre-signed URL for uploading a file
     */
    fun generateUploadUrl(
        tenantId: Long,
        userId: Long,
        fileName: String,
        contentType: String,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS
    ): Pair<String, String>? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        val presigner = getPresigner(tenantId) ?: return null

        val key = generateDesignKey(tenantId, userId, fileName)

        val putRequest = PutObjectRequest.builder()
            .bucket(clientWrapper.bucket)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .putObjectRequest(putRequest)
            .build()

        val presignedUrl = presigner.presignPutObject(presignRequest)

        logger.info("Generated upload URL for tenant $tenantId, key: $key")
        return Pair(presignedUrl.url().toString(), key)
    }

    /**
     * Generate a pre-signed URL for downloading/viewing a file
     */
    fun generateDownloadUrl(
        tenantId: Long,
        key: String,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS
    ): String? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        val presigner = getPresigner(tenantId) ?: return null

        // If CDN is configured, use it directly
        if (!clientWrapper.cdnDomain.isNullOrBlank()) {
            return "https://${clientWrapper.cdnDomain}/$key"
        }

        val getRequest = GetObjectRequest.builder()
            .bucket(clientWrapper.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .getObjectRequest(getRequest)
            .build()

        val presignedUrl = presigner.presignGetObject(presignRequest)
        return presignedUrl.url().toString()
    }

    // =====================================================
    // FILE OPERATIONS
    // =====================================================

    /**
     * Check if a file exists in S3
     */
    fun fileExists(tenantId: Long, key: String): Boolean {
        val clientWrapper = getS3Client(tenantId) ?: return false
        return try {
            clientWrapper.client.headObject(
                HeadObjectRequest.builder()
                    .bucket(clientWrapper.bucket)
                    .key(key)
                    .build()
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        } catch (e: Exception) {
            logger.error("Error checking file existence for tenant $tenantId: $key", e)
            false
        }
    }

    /**
     * Get file metadata from S3
     */
    fun getFileMetadata(tenantId: Long, key: String): FileMetadata? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        return try {
            val response = clientWrapper.client.headObject(
                HeadObjectRequest.builder()
                    .bucket(clientWrapper.bucket)
                    .key(key)
                    .build()
            )
            FileMetadata(
                key = key,
                size = response.contentLength(),
                contentType = response.contentType(),
                lastModified = response.lastModified(),
                eTag = response.eTag()
            )
        } catch (e: Exception) {
            logger.error("Error getting file metadata for tenant $tenantId: $key", e)
            null
        }
    }

    /**
     * Delete a file from S3
     */
    fun deleteFile(tenantId: Long, key: String): Boolean {
        val clientWrapper = getS3Client(tenantId) ?: return false
        return try {
            clientWrapper.client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(clientWrapper.bucket)
                    .key(key)
                    .build()
            )
            logger.info("Deleted file for tenant $tenantId: $key")
            true
        } catch (e: Exception) {
            logger.error("Error deleting file for tenant $tenantId: $key", e)
            false
        }
    }

    /**
     * Delete multiple files from S3
     */
    fun deleteFiles(tenantId: Long, keys: List<String>): Int {
        if (keys.isEmpty()) return 0
        val clientWrapper = getS3Client(tenantId) ?: return 0

        return try {
            val objects = keys.map { key ->
                ObjectIdentifier.builder().key(key).build()
            }

            val deleteRequest = DeleteObjectsRequest.builder()
                .bucket(clientWrapper.bucket)
                .delete(Delete.builder().objects(objects).build())
                .build()

            val result = clientWrapper.client.deleteObjects(deleteRequest)
            val deletedCount = result.deleted().size
            logger.info("Deleted $deletedCount files for tenant $tenantId")
            deletedCount
        } catch (e: Exception) {
            logger.error("Error deleting files for tenant $tenantId", e)
            0
        }
    }

    /**
     * Copy a file within S3 (e.g., from temp to permanent location)
     */
    fun copyFile(tenantId: Long, sourceKey: String, destinationKey: String): Boolean {
        val clientWrapper = getS3Client(tenantId) ?: return false
        return try {
            clientWrapper.client.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(clientWrapper.bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(clientWrapper.bucket)
                    .destinationKey(destinationKey)
                    .build()
            )
            logger.info("Copied $sourceKey to $destinationKey for tenant $tenantId")
            true
        } catch (e: Exception) {
            logger.error("Error copying file for tenant $tenantId: $sourceKey to $destinationKey", e)
            false
        }
    }

    /**
     * Upload bytes directly to S3
     * @param tenantId The tenant ID
     * @param bytes The byte array to upload
     * @param key The S3 key (path) for the file
     * @param contentType The MIME type of the file
     * @param fileName Optional filename for Content-Disposition header
     * @return The public URL of the uploaded file, or null if upload failed
     */
    fun uploadBytes(
        tenantId: Long,
        bytes: ByteArray,
        key: String,
        contentType: String,
        fileName: String? = null
    ): String? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        return try {
            val requestBuilder = PutObjectRequest.builder()
                .bucket(clientWrapper.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(bytes.size.toLong())

            // Add Content-Disposition if filename is provided
            fileName?.let {
                requestBuilder.contentDisposition("attachment; filename=\"$it\"")
            }

            clientWrapper.client.putObject(
                requestBuilder.build(),
                RequestBody.fromBytes(bytes)
            )

            logger.info("Uploaded ${bytes.size} bytes for tenant $tenantId, key: $key")
            getPublicUrl(tenantId, key)
        } catch (e: Exception) {
            logger.error("Error uploading bytes to S3 for tenant $tenantId: $key", e)
            null
        }
    }

    /**
     * Upload bytes and return a pre-signed download URL
     * @param tenantId The tenant ID
     * @param bytes The byte array to upload
     * @param key The S3 key (path) for the file
     * @param contentType The MIME type of the file
     * @param fileName Optional filename for Content-Disposition header
     * @param expirationSeconds URL expiration time in seconds
     * @return A pre-signed download URL, or null if upload failed
     */
    fun uploadBytesAndGetSignedUrl(
        tenantId: Long,
        bytes: ByteArray,
        key: String,
        contentType: String,
        fileName: String? = null,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS
    ): String? {
        val uploaded = uploadBytes(tenantId, bytes, key, contentType, fileName)
        if (uploaded == null) return null
        return generateDownloadUrl(tenantId, key, expirationSeconds)
    }

    /**
     * Download file bytes from S3
     * @param tenantId The tenant ID
     * @param key The S3 key of the file
     * @return The file contents as a byte array
     */
    fun downloadBytes(tenantId: Long, key: String): ByteArray? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        return try {
            val response = clientWrapper.client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(clientWrapper.bucket)
                    .key(key)
                    .build()
            )
            response.asByteArray()
        } catch (e: NoSuchKeyException) {
            logger.warn("File not found for tenant $tenantId: $key")
            null
        } catch (e: Exception) {
            logger.error("Error downloading file for tenant $tenantId: $key", e)
            null
        }
    }

    // =====================================================
    // EXPORT FILE OPERATIONS
    // =====================================================

    /**
     * Upload an export file to S3
     * @param tenantId The tenant ID for organizing exports
     * @param fileName The filename for the export
     * @param content The export file content as bytes
     * @param contentType The MIME type (default: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
     * @return The public URL of the uploaded file, or null if upload failed
     */
    fun uploadExport(
        tenantId: Long,
        fileName: String,
        content: ByteArray,
        contentType: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ): String? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        val timestamp = Instant.now().toEpochMilli()
        val key = "$EXPORTS_PREFIX/$tenantId/${timestamp}_$fileName"

        return try {
            val request = PutObjectRequest.builder()
                .bucket(clientWrapper.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(content.size.toLong())
                .contentDisposition("attachment; filename=\"$fileName\"")
                .build()

            clientWrapper.client.putObject(request, RequestBody.fromBytes(content))

            logger.info("Uploaded export for tenant $tenantId: $key (${content.size} bytes)")
            getPublicUrl(tenantId, key)
        } catch (e: Exception) {
            logger.error("Error uploading export for tenant $tenantId: $key", e)
            null
        }
    }

    /**
     * Generate a pre-signed URL for a given URL (extracts key from URL)
     * @param tenantId The tenant ID
     * @param fileUrl The S3 URL or CDN URL
     * @param expirationSeconds How long the URL should be valid
     * @return A pre-signed download URL, or null if URL parsing fails
     */
    fun generatePresignedUrl(tenantId: Long, fileUrl: String, expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS): String? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        val presigner = getPresigner(tenantId) ?: return null

        return try {
            // Extract key from URL
            val key = extractKeyFromUrl(fileUrl, clientWrapper.cdnDomain, clientWrapper.bucket)
                ?: return null

            val getRequest = GetObjectRequest.builder()
                .bucket(clientWrapper.bucket)
                .key(key)
                .build()

            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .getObjectRequest(getRequest)
                .build()

            presigner.presignGetObject(presignRequest).url().toString()
        } catch (e: Exception) {
            logger.error("Error generating presigned URL for tenant $tenantId: $fileUrl", e)
            null
        }
    }

    /**
     * Extract S3 key from a full URL
     */
    private fun extractKeyFromUrl(url: String, cdnDomain: String?, bucket: String): String? {
        return try {
            when {
                // CDN URL format: https://cdn.example.com/path/to/file
                cdnDomain != null && url.contains(cdnDomain) -> {
                    url.substringAfter("$cdnDomain/")
                }
                // S3 URL format: https://bucket.s3.region.amazonaws.com/path/to/file
                url.contains("s3.") && url.contains("amazonaws.com") -> {
                    url.substringAfter(".amazonaws.com/")
                }
                // Already a key
                !url.startsWith("http") -> url
                else -> null
            }
        } catch (e: Exception) {
            logger.warn("Could not extract key from URL: $url")
            null
        }
    }

    // =====================================================
    // KEY GENERATION
    // =====================================================

    /**
     * Generate a unique key for a design file
     * Format: designs/{tenant_id}/{user_id}/{timestamp}_{random}_{filename}
     */
    fun generateDesignKey(tenantId: Long, userId: Long, fileName: String): String {
        val timestamp = Instant.now().toEpochMilli()
        val random = UUID.randomUUID().toString().take(8)
        val safeFileName = sanitizeFileName(fileName)
        return "$DESIGN_PREFIX/$tenantId/$userId/${timestamp}_${random}_$safeFileName"
    }

    /**
     * Generate a key for thumbnail
     */
    fun generateThumbnailKey(designKey: String): String {
        val fileName = designKey.substringAfterLast("/")
        val baseName = fileName.substringBeforeLast(".")
        val path = designKey.substringBeforeLast("/")
            .replace(DESIGN_PREFIX, THUMBNAIL_PREFIX)
        return "$path/${baseName}_thumb.png"
    }

    /**
     * Get the public URL for a key
     */
    fun getPublicUrl(tenantId: Long, key: String): String? {
        val clientWrapper = getS3Client(tenantId) ?: return null
        return if (!clientWrapper.cdnDomain.isNullOrBlank()) {
            "https://${clientWrapper.cdnDomain}/$key"
        } else {
            "https://${clientWrapper.bucket}.s3.${clientWrapper.region}.amazonaws.com/$key"
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100) // Limit filename length
    }

    /**
     * Calculate MD5 hash for duplicate detection
     */
    fun calculateHash(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Clear cached clients for a tenant (useful when credentials are updated)
     */
    fun clearCache(tenantId: Long) {
        clientCache.remove(tenantId)?.client?.close()
        presignerCache.remove(tenantId)?.presigner?.close()
        logger.info("Cleared S3 cache for tenant $tenantId")
    }
}

data class FileMetadata(
    val key: String,
    val size: Long,
    val contentType: String?,
    val lastModified: Instant?,
    val eTag: String?
)

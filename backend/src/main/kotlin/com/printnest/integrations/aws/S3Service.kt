package com.printnest.integrations.aws

import io.ktor.server.application.*
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
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.*

class S3Service(
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val region: String,
    private val bucket: String,
    private val cdnDomain: String? = null
) {
    private val logger = LoggerFactory.getLogger(S3Service::class.java)

    private val isConfigured: Boolean = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()

    private val credentials by lazy {
        if (!isConfigured) throw IllegalStateException("AWS credentials not configured")
        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
    }

    private val credentialsProvider by lazy {
        StaticCredentialsProvider.create(credentials)
    }

    private val s3Client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build()
    }

    private val presigner: S3Presigner by lazy {
        S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build()
    }

    init {
        if (!isConfigured) {
            logger.warn("S3Service initialized without AWS credentials - S3 operations will fail")
        } else {
            logger.info("S3Service initialized for bucket: $bucket in region: $region")
        }
    }

    companion object {
        private const val DESIGN_PREFIX = "designs"
        private const val THUMBNAIL_PREFIX = "thumbnails"
        private const val TEMP_PREFIX = "temp"
        private const val EXPORTS_PREFIX = "exports"
        private const val DEFAULT_EXPIRATION_SECONDS = 3600L // 1 hour
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
    ): Pair<String, String> {
        val key = generateDesignKey(tenantId, userId, fileName)

        val putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .putObjectRequest(putRequest)
            .build()

        val presignedUrl = presigner.presignPutObject(presignRequest)

        logger.info("Generated upload URL for key: $key")
        return Pair(presignedUrl.url().toString(), key)
    }

    /**
     * Generate a pre-signed URL for downloading/viewing a file
     */
    fun generateDownloadUrl(
        key: String,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS
    ): String {
        // If CDN is configured, use it directly
        if (!cdnDomain.isNullOrBlank()) {
            return "https://$cdnDomain/$key"
        }

        val getRequest = GetObjectRequest.builder()
            .bucket(bucket)
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
    fun fileExists(key: String): Boolean {
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        } catch (e: Exception) {
            logger.error("Error checking file existence: $key", e)
            false
        }
    }

    /**
     * Get file metadata from S3
     */
    fun getFileMetadata(key: String): FileMetadata? {
        return try {
            val response = s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucket)
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
            logger.error("Error getting file metadata: $key", e)
            null
        }
    }

    /**
     * Delete a file from S3
     */
    fun deleteFile(key: String): Boolean {
        return try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
            )
            logger.info("Deleted file: $key")
            true
        } catch (e: Exception) {
            logger.error("Error deleting file: $key", e)
            false
        }
    }

    /**
     * Delete multiple files from S3
     */
    fun deleteFiles(keys: List<String>): Int {
        if (keys.isEmpty()) return 0

        return try {
            val objects = keys.map { key ->
                ObjectIdentifier.builder().key(key).build()
            }

            val deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).build())
                .build()

            val result = s3Client.deleteObjects(deleteRequest)
            val deletedCount = result.deleted().size
            logger.info("Deleted $deletedCount files")
            deletedCount
        } catch (e: Exception) {
            logger.error("Error deleting files", e)
            0
        }
    }

    /**
     * Copy a file within S3 (e.g., from temp to permanent location)
     */
    fun copyFile(sourceKey: String, destinationKey: String): Boolean {
        return try {
            s3Client.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(destinationKey)
                    .build()
            )
            logger.info("Copied $sourceKey to $destinationKey")
            true
        } catch (e: Exception) {
            logger.error("Error copying file: $sourceKey to $destinationKey", e)
            false
        }
    }

    /**
     * Upload bytes directly to S3
     * @param bytes The byte array to upload
     * @param key The S3 key (path) for the file
     * @param contentType The MIME type of the file
     * @param fileName Optional filename for Content-Disposition header
     * @return The public URL of the uploaded file
     */
    fun uploadBytes(
        bytes: ByteArray,
        key: String,
        contentType: String,
        fileName: String? = null
    ): String {
        return try {
            val requestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(bytes.size.toLong())

            // Add Content-Disposition if filename is provided
            fileName?.let {
                requestBuilder.contentDisposition("attachment; filename=\"$it\"")
            }

            s3Client.putObject(
                requestBuilder.build(),
                RequestBody.fromBytes(bytes)
            )

            logger.info("Uploaded ${bytes.size} bytes to key: $key")
            getPublicUrl(key)
        } catch (e: Exception) {
            logger.error("Error uploading bytes to S3: $key", e)
            throw e
        }
    }

    /**
     * Upload bytes and return a pre-signed download URL
     * @param bytes The byte array to upload
     * @param key The S3 key (path) for the file
     * @param contentType The MIME type of the file
     * @param fileName Optional filename for Content-Disposition header
     * @param expirationSeconds URL expiration time in seconds
     * @return A pre-signed download URL
     */
    fun uploadBytesAndGetSignedUrl(
        bytes: ByteArray,
        key: String,
        contentType: String,
        fileName: String? = null,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS
    ): String {
        uploadBytes(bytes, key, contentType, fileName)
        return generateDownloadUrl(key, expirationSeconds)
    }

    /**
     * Download file bytes from S3
     * @param key The S3 key of the file
     * @return The file contents as a byte array
     */
    fun downloadBytes(key: String): ByteArray? {
        return try {
            val response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
            )
            response.asByteArray()
        } catch (e: NoSuchKeyException) {
            logger.warn("File not found: $key")
            null
        } catch (e: Exception) {
            logger.error("Error downloading file: $key", e)
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
     * @return The public URL of the uploaded file
     */
    fun uploadExport(
        tenantId: Long,
        fileName: String,
        content: ByteArray,
        contentType: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ): String {
        val timestamp = Instant.now().toEpochMilli()
        val key = "$EXPORTS_PREFIX/$tenantId/${timestamp}_$fileName"

        return try {
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(content.size.toLong())
                .contentDisposition("attachment; filename=\"$fileName\"")
                .build()

            s3Client.putObject(request, RequestBody.fromBytes(content))

            logger.info("Uploaded export: $key (${content.size} bytes)")
            getPublicUrl(key)
        } catch (e: Exception) {
            logger.error("Error uploading export: $key", e)
            throw e
        }
    }

    /**
     * Generate a pre-signed URL for a given URL (extracts key from URL)
     * @param fileUrl The S3 URL or CDN URL
     * @param expirationSeconds How long the URL should be valid
     * @return A pre-signed download URL, or null if URL parsing fails
     */
    fun generatePresignedUrl(fileUrl: String, expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS): String? {
        return try {
            // Extract key from URL
            val key = extractKeyFromUrl(fileUrl)
                ?: return null

            val getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()

            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .getObjectRequest(getRequest)
                .build()

            presigner.presignGetObject(presignRequest).url().toString()
        } catch (e: Exception) {
            logger.error("Error generating presigned URL for: $fileUrl", e)
            null
        }
    }

    /**
     * Extract S3 key from a full URL
     */
    private fun extractKeyFromUrl(url: String): String? {
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
    fun getPublicUrl(key: String): String {
        return if (!cdnDomain.isNullOrBlank()) {
            "https://$cdnDomain/$key"
        } else {
            "https://$bucket.s3.$region.amazonaws.com/$key"
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
}

data class FileMetadata(
    val key: String,
    val size: Long,
    val contentType: String?,
    val lastModified: Instant?,
    val eTag: String?
)

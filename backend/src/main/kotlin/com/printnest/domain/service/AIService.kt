package com.printnest.domain.service

import com.printnest.domain.models.*
import com.printnest.domain.repository.DesignRepository
import com.printnest.integrations.openai.OpenAIService
import com.printnest.integrations.aws.S3Service
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Base64

class AIService(
    private val openAIService: OpenAIService,
    private val designRepository: DesignRepository,
    private val s3Service: S3Service
) {
    private val logger = LoggerFactory.getLogger(AIService::class.java)

    // =====================================================
    // ETSY LISTING GENERATION
    // =====================================================

    /**
     * Generate a complete Etsy listing for a design
     * This is the main entry point for listing generation from the design library
     */
    suspend fun generateEtsyListing(
        tenantId: Long,
        designId: Long,
        userPrompt: String = "",
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Result<AIGeneratedListing> {
        return try {
            // Get design details
            val design = designRepository.findById(designId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Design not found"))

            logger.info("Generating Etsy listing for design ${design.id}: ${design.title}")

            // Generate listing content using OpenAI Vision
            val listingResult = openAIService.generateListingContent(
                imageUrl = design.designUrl,
                userInput = userPrompt,
                mustInclude = mustInclude,
                mustExclude = mustExclude
            )

            listingResult.fold(
                onSuccess = { listing ->
                    val aiListing = AIGeneratedListing(
                        title = listing.title,
                        description = listing.description,
                        tags = listing.tags,
                        generatedAt = Instant.now().toString(),
                        model = "gpt-4o"
                    )
                    logger.info("Successfully generated listing for design ${design.id}")
                    Result.success(aiListing)
                },
                onFailure = { error ->
                    logger.error("Failed to generate listing for design ${design.id}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error generating Etsy listing", e)
            Result.failure(e)
        }
    }

    /**
     * Generate listing from an image URL directly (without design record)
     */
    suspend fun generateListingFromUrl(
        imageUrl: String,
        userPrompt: String = "",
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Result<ListingGenerationResponse> {
        return try {
            logger.info("Generating listing from URL: $imageUrl")

            openAIService.generateListingContent(
                imageUrl = imageUrl,
                userInput = userPrompt,
                mustInclude = mustInclude,
                mustExclude = mustExclude
            )
        } catch (e: Exception) {
            logger.error("Error generating listing from URL", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // IMAGE ANALYSIS
    // =====================================================

    /**
     * Analyze an image and return descriptive information
     */
    suspend fun analyzeImage(
        imageUrl: String,
        customPrompt: String? = null
    ): Result<ImageAnalysisResponse> {
        return try {
            logger.info("Analyzing image: $imageUrl")

            openAIService.analyzeImage(
                imageUrl = imageUrl,
                customPrompt = customPrompt
            )
        } catch (e: Exception) {
            logger.error("Error analyzing image", e)
            Result.failure(e)
        }
    }

    /**
     * Analyze a design from the design library
     */
    suspend fun analyzeDesign(
        tenantId: Long,
        designId: Long
    ): Result<ImageAnalysisResponse> {
        return try {
            val design = designRepository.findById(designId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Design not found"))

            analyzeImage(design.designUrl)
        } catch (e: Exception) {
            logger.error("Error analyzing design", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // BACKGROUND REMOVAL
    // =====================================================

    /**
     * Remove background from an image
     *
     * Note: This is a placeholder implementation. In production, this would
     * integrate with a background removal service like rembg, remove.bg API,
     * or a custom ML model.
     */
    suspend fun removeBackground(
        imageUrl: String
    ): Result<BackgroundRemovalResponse> {
        return try {
            logger.info("Background removal requested for: $imageUrl")

            // Placeholder implementation
            // In production, this would:
            // 1. Download the image
            // 2. Send to rembg or remove.bg API
            // 3. Upload the result to S3
            // 4. Return the new URL

            // For now, return a message indicating the feature needs external service
            Result.success(
                BackgroundRemovalResponse(
                    imageUrl = imageUrl,
                    imageBase64 = null,
                    success = false,
                    message = "Background removal requires integration with rembg or remove.bg API. " +
                            "Please configure the REMBG_API_URL or REMOVE_BG_API_KEY environment variable."
                )
            )
        } catch (e: Exception) {
            logger.error("Error removing background", e)
            Result.failure(e)
        }
    }

    /**
     * Remove background from a design in the library
     */
    suspend fun removeDesignBackground(
        tenantId: Long,
        designId: Long
    ): Result<BackgroundRemovalResponse> {
        return try {
            val design = designRepository.findById(designId, tenantId)
                ?: return Result.failure(IllegalArgumentException("Design not found"))

            removeBackground(design.designUrl)
        } catch (e: Exception) {
            logger.error("Error removing design background", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // TAG GENERATION
    // =====================================================

    /**
     * Generate SEO-optimized tags for a product
     */
    suspend fun generateTags(
        description: String,
        count: Int = 13,
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Result<TagGenerationResponse> {
        return try {
            logger.info("Generating $count tags for description")

            val tagsResult = openAIService.generateTags(
                description = description,
                count = count,
                mustInclude = mustInclude,
                mustExclude = mustExclude
            )

            tagsResult.fold(
                onSuccess = { tags ->
                    Result.success(TagGenerationResponse(tags = tags))
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error generating tags", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CATEGORY SUGGESTIONS
    // =====================================================

    /**
     * Suggest product categories based on description
     */
    suspend fun suggestCategories(
        description: String,
        platform: String = "etsy"
    ): Result<CategorySuggestionResponse> {
        return try {
            logger.info("Suggesting categories for $platform")

            val categoriesResult = openAIService.suggestCategories(
                description = description,
                platform = platform
            )

            categoriesResult.fold(
                onSuccess = { categories ->
                    Result.success(CategorySuggestionResponse(categories = categories))
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error suggesting categories", e)
            Result.failure(e)
        }
    }

    /**
     * Suggest categories based on an image
     */
    suspend fun suggestCategoriesFromImage(
        imageUrl: String,
        platform: String = "etsy"
    ): Result<CategorySuggestionResponse> {
        return try {
            // First analyze the image to get a description
            val analysisResult = analyzeImage(imageUrl)

            analysisResult.fold(
                onSuccess = { analysis ->
                    // Use the description to suggest categories
                    suggestCategories(analysis.description, platform)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error suggesting categories from image", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // TITLE OPTIMIZATION
    // =====================================================

    /**
     * Optimize a product title for better SEO
     */
    suspend fun optimizeTitle(
        title: String,
        platform: String = "etsy",
        maxLength: Int = 140
    ): Result<TitleOptimizationResponse> {
        return try {
            logger.info("Optimizing title for $platform: $title")

            openAIService.optimizeTitle(
                title = title,
                platform = platform,
                maxLength = maxLength
            )
        } catch (e: Exception) {
            logger.error("Error optimizing title", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // BATCH OPERATIONS
    // =====================================================

    /**
     * Generate listings for multiple designs
     */
    suspend fun batchGenerateListings(
        tenantId: Long,
        designIds: List<Long>,
        userPrompt: String = "",
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Map<Long, Result<AIGeneratedListing>> {
        logger.info("Batch generating listings for ${designIds.size} designs")

        return designIds.associateWith { designId ->
            generateEtsyListing(
                tenantId = tenantId,
                designId = designId,
                userPrompt = userPrompt,
                mustInclude = mustInclude,
                mustExclude = mustExclude
            )
        }
    }

    // =====================================================
    // GENERAL AI TASKS
    // =====================================================

    /**
     * Custom AI completion for general tasks
     */
    suspend fun customCompletion(
        systemPrompt: String?,
        userPrompt: String,
        temperature: Double = 0.7,
        jsonMode: Boolean = false
    ): Result<String> {
        return try {
            openAIService.chatCompletion(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = temperature,
                jsonMode = jsonMode
            )
        } catch (e: Exception) {
            logger.error("Error in custom completion", e)
            Result.failure(e)
        }
    }
}

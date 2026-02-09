package com.printnest.routes

import com.printnest.domain.models.*
import com.printnest.domain.service.AIService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.aiRoutes() {
    val aiService: AIService = GlobalContext.get().get()

    route("/ai") {

        // =====================================================
        // LISTING GENERATION
        // =====================================================

        /**
         * POST /api/v1/ai/generate-listing
         *
         * Generate Etsy listing content from an image.
         * Can use either a design ID or a direct image URL.
         *
         * Request body:
         * {
         *   "designId": 123,              // Optional: Use design from library
         *   "imageUrl": "https://...",    // Optional: Direct image URL
         *   "userInput": "Father's day gift",
         *   "mustInclude": ["dad", "gift"],
         *   "mustExclude": ["cheap"]
         * }
         */
        post("/generate-listing") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, Any?>>()

            val designId = (body["designId"] as? Number)?.toLong()
            val imageUrl = body["imageUrl"] as? String
            val userInput = body["userInput"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val mustInclude = (body["mustInclude"] as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val mustExclude = (body["mustExclude"] as? List<String>) ?: emptyList()

            if (designId == null && imageUrl.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Either designId or imageUrl is required")
                )
            }

            val result = if (designId != null) {
                aiService.generateEtsyListing(
                    tenantId = tenantId,
                    designId = designId,
                    userPrompt = userInput,
                    mustInclude = mustInclude,
                    mustExclude = mustExclude
                )
            } else {
                aiService.generateListingFromUrl(
                    imageUrl = imageUrl!!,
                    userPrompt = userInput,
                    mustInclude = mustInclude,
                    mustExclude = mustExclude
                ).map { listing ->
                    AIGeneratedListing(
                        title = listing.title,
                        description = listing.description,
                        tags = listing.tags,
                        generatedAt = java.time.Instant.now().toString(),
                        model = "gpt-4o"
                    )
                }
            }

            result.fold(
                onSuccess = { listing ->
                    call.respond(listing)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to generate listing"))
                    )
                }
            )
        }

        // =====================================================
        // IMAGE ANALYSIS
        // =====================================================

        /**
         * POST /api/v1/ai/analyze-image
         *
         * Analyze an image and describe its content.
         *
         * Request body:
         * {
         *   "imageUrl": "https://...",    // Required
         *   "designId": 123,              // Optional: Use design from library
         *   "prompt": "Custom analysis prompt"  // Optional
         * }
         */
        post("/analyze-image") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, Any?>>()

            val designId = (body["designId"] as? Number)?.toLong()
            val imageUrl = body["imageUrl"] as? String
            val prompt = body["prompt"] as? String

            val result = if (designId != null) {
                aiService.analyzeDesign(tenantId, designId)
            } else if (!imageUrl.isNullOrBlank()) {
                aiService.analyzeImage(imageUrl, prompt)
            } else {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Either designId or imageUrl is required")
                )
            }

            result.fold(
                onSuccess = { analysis ->
                    call.respond(analysis)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to analyze image"))
                    )
                }
            )
        }

        // =====================================================
        // TAG GENERATION
        // =====================================================

        /**
         * POST /api/v1/ai/generate-tags
         *
         * Generate SEO-optimized tags from a description.
         *
         * Request body:
         * {
         *   "description": "Product description...",
         *   "count": 13,                    // Optional, default 13 (Etsy limit)
         *   "mustInclude": ["keyword1"],    // Optional
         *   "mustExclude": ["keyword2"]     // Optional
         * }
         */
        post("/generate-tags") {
            val body = call.receive<TagGenerationRequest>()

            if (body.description.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Description is required")
                )
            }

            val result = aiService.generateTags(
                description = body.description,
                count = body.count,
                mustInclude = body.mustInclude,
                mustExclude = body.mustExclude
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to generate tags"))
                    )
                }
            )
        }

        // =====================================================
        // BACKGROUND REMOVAL
        // =====================================================

        /**
         * POST /api/v1/ai/remove-background
         *
         * Remove background from an image.
         *
         * Request body:
         * {
         *   "imageUrl": "https://...",    // Required if no designId
         *   "designId": 123               // Optional: Use design from library
         * }
         */
        post("/remove-background") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, Any?>>()

            val designId = (body["designId"] as? Number)?.toLong()
            val imageUrl = body["imageUrl"] as? String

            val result = if (designId != null) {
                aiService.removeDesignBackground(tenantId, designId)
            } else if (!imageUrl.isNullOrBlank()) {
                aiService.removeBackground(imageUrl)
            } else {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Either designId or imageUrl is required")
                )
            }

            result.fold(
                onSuccess = { response ->
                    call.respond(response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to remove background"))
                    )
                }
            )
        }

        // =====================================================
        // TITLE OPTIMIZATION
        // =====================================================

        /**
         * POST /api/v1/ai/optimize-title
         *
         * Optimize a product title for SEO.
         *
         * Request body:
         * {
         *   "title": "My Product Title",
         *   "platform": "etsy",           // Optional, default "etsy"
         *   "maxLength": 140              // Optional, default 140
         * }
         */
        post("/optimize-title") {
            val body = call.receive<TitleOptimizationRequest>()

            if (body.title.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Title is required")
                )
            }

            val result = aiService.optimizeTitle(
                title = body.title,
                platform = body.platform,
                maxLength = body.maxLength
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to optimize title"))
                    )
                }
            )
        }

        // =====================================================
        // CATEGORY SUGGESTIONS
        // =====================================================

        /**
         * POST /api/v1/ai/suggest-categories
         *
         * Suggest product categories based on description or image.
         *
         * Request body:
         * {
         *   "description": "Product description...",  // Optional if imageUrl provided
         *   "imageUrl": "https://...",               // Optional if description provided
         *   "platform": "etsy"                       // Optional, default "etsy"
         * }
         */
        post("/suggest-categories") {
            val body = call.receive<Map<String, String>>()

            val description = body["description"]
            val imageUrl = body["imageUrl"]
            val platform = body["platform"] ?: "etsy"

            val result = when {
                !description.isNullOrBlank() -> {
                    aiService.suggestCategories(description, platform)
                }
                !imageUrl.isNullOrBlank() -> {
                    aiService.suggestCategoriesFromImage(imageUrl, platform)
                }
                else -> {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Either description or imageUrl is required")
                    )
                }
            }

            result.fold(
                onSuccess = { response ->
                    call.respond(response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to suggest categories"))
                    )
                }
            )
        }

        // =====================================================
        // BATCH OPERATIONS
        // =====================================================

        /**
         * POST /api/v1/ai/batch-generate-listings
         *
         * Generate listings for multiple designs.
         *
         * Request body:
         * {
         *   "designIds": [1, 2, 3],
         *   "userInput": "Custom prompt",
         *   "mustInclude": [],
         *   "mustExclude": []
         * }
         */
        post("/batch-generate-listings") {
            val tenantId = call.request.headers["X-Tenant-Id"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tenant ID required"))

            val body = call.receive<Map<String, Any?>>()

            @Suppress("UNCHECKED_CAST")
            val designIds = (body["designIds"] as? List<Number>)?.map { it.toLong() }
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "designIds is required")
                )

            if (designIds.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "designIds cannot be empty")
                )
            }

            if (designIds.size > 10) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Maximum 10 designs per batch request")
                )
            }

            val userInput = body["userInput"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val mustInclude = (body["mustInclude"] as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val mustExclude = (body["mustExclude"] as? List<String>) ?: emptyList()

            val results = aiService.batchGenerateListings(
                tenantId = tenantId,
                designIds = designIds,
                userPrompt = userInput,
                mustInclude = mustInclude,
                mustExclude = mustExclude
            )

            val response = results.map { (designId, result) ->
                mapOf(
                    "designId" to designId,
                    "success" to result.isSuccess,
                    "listing" to result.getOrNull(),
                    "error" to result.exceptionOrNull()?.message
                )
            }

            call.respond(mapOf("results" to response))
        }

        // =====================================================
        // CUSTOM COMPLETION
        // =====================================================

        /**
         * POST /api/v1/ai/complete
         *
         * General purpose AI completion.
         *
         * Request body:
         * {
         *   "systemPrompt": "You are a helpful assistant",  // Optional
         *   "userPrompt": "Your prompt here",               // Required
         *   "temperature": 0.7,                             // Optional
         *   "jsonMode": false                               // Optional
         * }
         */
        post("/complete") {
            val body = call.receive<Map<String, Any?>>()

            val userPrompt = body["userPrompt"] as? String
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "userPrompt is required")
                )

            val systemPrompt = body["systemPrompt"] as? String
            val temperature = (body["temperature"] as? Number)?.toDouble() ?: 0.7
            val jsonMode = body["jsonMode"] as? Boolean ?: false

            val result = aiService.customCompletion(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = temperature,
                jsonMode = jsonMode
            )

            result.fold(
                onSuccess = { response ->
                    call.respond(mapOf("response" to response))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to complete request"))
                    )
                }
            )
        }
    }
}

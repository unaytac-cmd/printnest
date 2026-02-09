package com.printnest.integrations.openai

import com.printnest.domain.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class OpenAIService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(OpenAIService::class.java)

    private val baseUrl = "https://api.openai.com/v1"
    private val model = "gpt-4o"
    private val apiKey: String
        get() = System.getenv("OPENAI_API_KEY") ?: ""

    // =====================================================
    // LISTING CONTENT GENERATION (GPT-4 Vision)
    // =====================================================

    /**
     * Generate Etsy listing content from an image using GPT-4 Vision
     */
    suspend fun generateListingContent(
        imageUrl: String,
        userInput: String = "",
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Result<ListingGenerationResponse> {
        return try {
            val systemPrompt = """
                You are an expert Etsy seller and SEO strategist. You create high-quality product listings
                optimized for Etsy's search algorithm. Your goal is to maximize visibility, clickthrough rate,
                and conversion by using emotionally attaching language, trending keywords, and niche relevant tags.
            """.trimIndent()

            val mustIncludeText = if (mustInclude.isNotEmpty()) {
                "Must include these tags before the generated tags: ${mustInclude.joinToString(", ")}"
            } else ""

            val mustExcludeText = if (mustExclude.isNotEmpty()) {
                "Must exclude these tags at all cost: ${mustExclude.joinToString(", ")}"
            } else ""

            val userInputText = if (userInput.isNotBlank()) {
                """
                Follow these custom user given listing strategies. It can include details like target audience,
                gift type (father's day), style, keyword or tag preferences, etc... The main effect should be
                on the first 3 words of the title and the top 5 most relevant tags: $userInput
                """.trimIndent()
            } else ""

            val prompt = """
                Please analyze the image and respond with a JSON object optimized for Etsy's searching algorithm,
                to target the first page visibility for clothing product listings.

                Format your response as:
                {
                  "title": "A compelling, brandable product title for a product (max 140 characters)",
                  "description": "An enticing, benefit-driven description (max 300 characters) that highlights the design's unique visual appeal, emotional resonance, cultural relevance, and the lifestyle or identity it represents to potential buyers.",
                  "tags": ["25 high-traffic e-commerce keywords that emphasize the design's themes, artistic style, target audience, cultural references, emotional tone, and trending niches—optimized for search visibility and product discoverability. Place them in order of most relevance, to least relevance."]
                }

                Follow these rules for tag generation:
                $mustIncludeText
                $mustExcludeText
                $userInputText

                Return only valid JSON with no extra commentary.
            """.trimIndent()

            val requestBody = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", imageUrl)
                                }
                            }
                        }
                    }
                }
                put("temperature", 0.0)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            val parsed = json.parseToJsonElement(content).jsonObject

            val title = parsed["title"]?.jsonPrimitive?.content ?: "Untitled"
            val description = parsed["description"]?.jsonPrimitive?.content ?: "No description"
            val tags = parsed["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            logger.info("Generated listing: title='$title', ${tags.size} tags")

            Result.success(
                ListingGenerationResponse(
                    title = title,
                    description = description,
                    tags = tags
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to generate listing content", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // IMAGE ANALYSIS
    // =====================================================

    /**
     * Analyze an image and describe its content
     */
    suspend fun analyzeImage(
        imageUrl: String,
        customPrompt: String? = null
    ): Result<ImageAnalysisResponse> {
        return try {
            val prompt = customPrompt ?: """
                Analyze this image and provide a JSON response with the following structure:
                {
                  "description": "A detailed description of what's in the image",
                  "colors": ["list", "of", "dominant", "colors"],
                  "style": "The artistic or design style (e.g., minimalist, vintage, modern, cartoon)",
                  "themes": ["list", "of", "themes", "or", "concepts"],
                  "suggestedCategory": "Best product category for this design"
                }

                Be specific and detailed. Return only valid JSON.
            """.trimIndent()

            val requestBody = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", imageUrl)
                                }
                            }
                        }
                    }
                }
                put("temperature", 0.3)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            val parsed = json.parseToJsonElement(content).jsonObject

            Result.success(
                ImageAnalysisResponse(
                    description = parsed["description"]?.jsonPrimitive?.content ?: "",
                    colors = parsed["colors"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    style = parsed["style"]?.jsonPrimitive?.contentOrNull,
                    themes = parsed["themes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    suggestedCategory = parsed["suggestedCategory"]?.jsonPrimitive?.contentOrNull
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to analyze image", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // TAG GENERATION
    // =====================================================

    /**
     * Generate SEO tags from a description
     */
    suspend fun generateTags(
        description: String,
        count: Int = 13,
        mustInclude: List<String> = emptyList(),
        mustExclude: List<String> = emptyList()
    ): Result<List<String>> {
        return try {
            val mustIncludeText = if (mustInclude.isNotEmpty()) {
                "Must include these tags: ${mustInclude.joinToString(", ")}"
            } else ""

            val mustExcludeText = if (mustExclude.isNotEmpty()) {
                "Must NOT include these tags: ${mustExclude.joinToString(", ")}"
            } else ""

            val prompt = """
                Based on this product description, generate exactly $count SEO-optimized tags for Etsy.

                Description: $description

                $mustIncludeText
                $mustExcludeText

                Return a JSON object with a "tags" array containing exactly $count tags.
                Tags should be:
                - Single words or short phrases (max 3 words)
                - Relevant to e-commerce search
                - Ordered by relevance (most relevant first)

                Response format:
                {"tags": ["tag1", "tag2", ...]}
            """.trimIndent()

            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("temperature", 0.3)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            val parsed = json.parseToJsonElement(content).jsonObject
            val tags = parsed["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            logger.info("Generated ${tags.size} tags")
            Result.success(tags)
        } catch (e: Exception) {
            logger.error("Failed to generate tags", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // TITLE OPTIMIZATION
    // =====================================================

    /**
     * Optimize a product title for SEO
     */
    suspend fun optimizeTitle(
        title: String,
        platform: String = "etsy",
        maxLength: Int = 140
    ): Result<TitleOptimizationResponse> {
        return try {
            val prompt = """
                Optimize this $platform product title for SEO while keeping it under $maxLength characters.

                Original title: $title

                Return a JSON object with:
                {
                  "originalTitle": "the original title",
                  "optimizedTitle": "the optimized title under $maxLength chars",
                  "changes": ["list of changes made to improve SEO"]
                }

                Focus on:
                - Front-loading important keywords
                - Adding relevant search terms
                - Making it more compelling
                - Removing filler words
            """.trimIndent()

            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("temperature", 0.3)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            val parsed = json.parseToJsonElement(content).jsonObject

            Result.success(
                TitleOptimizationResponse(
                    originalTitle = parsed["originalTitle"]?.jsonPrimitive?.content ?: title,
                    optimizedTitle = parsed["optimizedTitle"]?.jsonPrimitive?.content ?: title,
                    changes = parsed["changes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to optimize title", e)
            Result.failure(e)
        }
    }

    // =====================================================
    // CHAT COMPLETION (General Purpose)
    // =====================================================

    /**
     * General chat completion for custom AI tasks
     */
    suspend fun chatCompletion(
        systemPrompt: String?,
        userPrompt: String,
        temperature: Double = 0.7,
        maxTokens: Int? = null,
        jsonMode: Boolean = false
    ): Result<String> {
        return try {
            val requestBody = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    if (systemPrompt != null) {
                        addJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                }
                put("temperature", temperature)
                if (maxTokens != null) {
                    put("max_tokens", maxTokens)
                }
                if (jsonMode) {
                    putJsonObject("response_format") {
                        put("type", "json_object")
                    }
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            Result.success(content)
        } catch (e: Exception) {
            logger.error("Failed to complete chat", e)
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
    ): Result<List<SuggestedCategory>> {
        return try {
            val prompt = """
                Based on this product description, suggest the top 5 most relevant $platform product categories.

                Description: $description

                Return a JSON object with:
                {
                  "categories": [
                    {
                      "name": "Category Name",
                      "confidence": 0.95,
                      "path": ["Parent", "Child", "Category Name"]
                    }
                  ]
                }

                Order by confidence (highest first). Confidence should be between 0 and 1.
            """.trimIndent()

            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("temperature", 0.3)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI API error: ${response.status} - $errorBody")
                return Result.failure(RuntimeException("OpenAI API error: ${response.status}"))
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return Result.failure(RuntimeException("No content in OpenAI response"))

            val parsed = json.parseToJsonElement(content).jsonObject
            val categories = parsed["categories"]?.jsonArray?.map { cat ->
                val catObj = cat.jsonObject
                SuggestedCategory(
                    name = catObj["name"]?.jsonPrimitive?.content ?: "",
                    confidence = catObj["confidence"]?.jsonPrimitive?.double ?: 0.0,
                    path = catObj["path"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                )
            } ?: emptyList()

            logger.info("Suggested ${categories.size} categories")
            Result.success(categories)
        } catch (e: Exception) {
            logger.error("Failed to suggest categories", e)
            Result.failure(e)
        }
    }
}

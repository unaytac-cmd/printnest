package com.printnest.domain.models

import kotlinx.serialization.Serializable

// =====================================================
// LISTING GENERATION
// =====================================================

@Serializable
data class ListingGenerationRequest(
    val imageUrl: String,
    val userInput: String = "",
    val mustInclude: List<String> = emptyList(),
    val mustExclude: List<String> = emptyList()
)

@Serializable
data class ListingGenerationResponse(
    val title: String,
    val description: String,
    val tags: List<String>
)

// =====================================================
// BACKGROUND REMOVAL
// =====================================================

@Serializable
data class BackgroundRemovalRequest(
    val imageUrl: String
)

@Serializable
data class BackgroundRemovalResponse(
    val imageUrl: String,
    val imageBase64: String? = null,
    val success: Boolean = true,
    val message: String? = null
)

// =====================================================
// IMAGE ANALYSIS
// =====================================================

@Serializable
data class ImageAnalysisRequest(
    val imageUrl: String,
    val prompt: String? = null
)

@Serializable
data class ImageAnalysisResponse(
    val description: String,
    val colors: List<String> = emptyList(),
    val style: String? = null,
    val themes: List<String> = emptyList(),
    val suggestedCategory: String? = null
)

// =====================================================
// TAG GENERATION
// =====================================================

@Serializable
data class TagGenerationRequest(
    val description: String,
    val count: Int = 13,
    val mustInclude: List<String> = emptyList(),
    val mustExclude: List<String> = emptyList()
)

@Serializable
data class TagGenerationResponse(
    val tags: List<String>
)

// =====================================================
// TITLE OPTIMIZATION
// =====================================================

@Serializable
data class TitleOptimizationRequest(
    val title: String,
    val platform: String = "etsy",
    val maxLength: Int = 140
)

@Serializable
data class TitleOptimizationResponse(
    val originalTitle: String,
    val optimizedTitle: String,
    val changes: List<String> = emptyList()
)

// =====================================================
// CATEGORY SUGGESTION
// =====================================================

@Serializable
data class CategorySuggestionRequest(
    val description: String,
    val platform: String = "etsy"
)

@Serializable
data class CategorySuggestionResponse(
    val categories: List<SuggestedCategory>
)

@Serializable
data class SuggestedCategory(
    val name: String,
    val confidence: Double,
    val path: List<String> = emptyList()
)

// =====================================================
// OPENAI INTERNAL MODELS
// =====================================================

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.0,
    val max_tokens: Int? = null,
    val response_format: OpenAIResponseFormat? = null
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: OpenAIMessageContent
)

@Serializable
sealed class OpenAIMessageContent {
    @Serializable
    data class Text(val text: String) : OpenAIMessageContent()

    @Serializable
    data class Multimodal(val parts: List<OpenAIContentPart>) : OpenAIMessageContent()
}

@Serializable
sealed class OpenAIContentPart

@Serializable
data class OpenAITextPart(
    val type: String = "text",
    val text: String
) : OpenAIContentPart()

@Serializable
data class OpenAIImageUrlPart(
    val type: String = "image_url",
    val image_url: OpenAIImageUrl
) : OpenAIContentPart()

@Serializable
data class OpenAIImageUrl(
    val url: String,
    val detail: String = "auto"
)

@Serializable
data class OpenAIResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class OpenAIChatResponse(
    val id: String,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIResponseMessage,
    val finish_reason: String? = null
)

@Serializable
data class OpenAIResponseMessage(
    val role: String,
    val content: String?
)

@Serializable
data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

// =====================================================
// AI GENERATED LISTING (stored result)
// =====================================================

@Serializable
data class AIGeneratedListing(
    val title: String,
    val description: String,
    val tags: List<String>,
    val generatedAt: String,
    val model: String = "gpt-4o",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

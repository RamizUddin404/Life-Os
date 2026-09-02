package com.example.ai

import com.example.data.network.openrouter.OpenRouterApiService
import com.example.data.network.openrouter.OpenRouterChoice
import com.example.data.network.openrouter.OpenRouterErrorDetail
import com.example.data.network.openrouter.OpenRouterMessage
import com.example.data.network.openrouter.OpenRouterRequest
import com.example.data.network.openrouter.OpenRouterResponse
import com.example.data.network.openrouter.OpenRouterResponseFormat
import com.example.data.repository.OpenRouterRepository
import com.example.data.repository.OpenRouterRepositoryImpl

// Re-export data types for AI package consumers
typealias OpenRouterMessage = com.example.data.network.openrouter.OpenRouterMessage
typealias OpenRouterRequest = com.example.data.network.openrouter.OpenRouterRequest
typealias OpenRouterResponse = com.example.data.network.openrouter.OpenRouterResponse
typealias OpenRouterChoice = com.example.data.network.openrouter.OpenRouterChoice
typealias OpenRouterErrorDetail = com.example.data.network.openrouter.OpenRouterErrorDetail
typealias OpenRouterResponseFormat = com.example.data.network.openrouter.OpenRouterResponseFormat

/**
 * Singleton client and facade for OpenRouter API operations,
 * delegating to [OpenRouterRepositoryImpl].
 */
object OpenRouterClient {
    val repository: OpenRouterRepository = OpenRouterRepositoryImpl()

    const val DEFAULT_MODEL = OpenRouterRepositoryImpl.DEFAULT_MODEL
    val POPULAR_MODELS = OpenRouterRepositoryImpl.POPULAR_MODELS.map { it.first }

    fun setCustomApiKey(key: String?) {
        repository.setCustomApiKey(key)
    }

    fun setActiveModel(model: String?) {
        if (!model.isNullOrBlank()) {
            repository.setActiveModel(model)
        }
    }

    fun getActiveModel(): String = repository.getActiveModel()

    fun getEffectiveApiKey(): String = repository.getEffectiveApiKey()

    suspend fun generate(
        prompt: String,
        systemInstruction: String? = null,
        history: List<OpenRouterMessage> = emptyList(),
        isJson: Boolean = false,
        model: String? = null,
        apiKeyOverride: String? = null
    ): String {
        val result = if (isJson) {
            repository.generateStructuredJson(
                prompt = prompt,
                systemInstruction = systemInstruction,
                history = history,
                model = model,
                apiKeyOverride = apiKeyOverride
            )
        } else {
            repository.generateText(
                prompt = prompt,
                systemInstruction = systemInstruction,
                history = history,
                model = model,
                apiKeyOverride = apiKeyOverride
            )
        }

        return result.getOrThrow()
    }

    suspend fun testConnection(
        apiKeyOverride: String? = null,
        modelOverride: String? = null
    ): String {
        return repository.testConnection(
            apiKeyOverride = apiKeyOverride,
            modelOverride = modelOverride
        ).getOrThrow()
    }
}

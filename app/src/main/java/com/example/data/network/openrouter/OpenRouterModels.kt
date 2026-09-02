package com.example.data.network.openrouter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenRouterMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponseFormat(
    @Json(name = "type") val type: String = "json_object"
)

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenRouterMessage>,
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "max_tokens") val max_tokens: Int? = null,
    @Json(name = "response_format") val response_format: OpenRouterResponseFormat? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterChoice(
    @Json(name = "message") val message: OpenRouterMessage? = null,
    @Json(name = "finish_reason") val finish_reason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterUsage(
    @Json(name = "prompt_tokens") val prompt_tokens: Int? = null,
    @Json(name = "completion_tokens") val completion_tokens: Int? = null,
    @Json(name = "total_tokens") val total_tokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterErrorDetail(
    @Json(name = "message") val message: String? = null,
    @Json(name = "code") val code: Any? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<OpenRouterChoice>? = null,
    @Json(name = "usage") val usage: OpenRouterUsage? = null,
    @Json(name = "error") val error: OpenRouterErrorDetail? = null
)

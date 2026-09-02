package com.example.data.network.openrouter

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://lifeos.aistudio.app",
        @Header("X-Title") appTitle: String = "LifeOS",
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}

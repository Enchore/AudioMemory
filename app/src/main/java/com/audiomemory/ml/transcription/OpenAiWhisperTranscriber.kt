package com.audiomemory.ml.transcription

import com.audiomemory.util.ApiConfig
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud transcription via a configurable Whisper-compatible API.
 * Supports any OpenAI-compatible transcription endpoint (OpenAI, Groq, local Whisper, etc.)
 * Used as fallback when on-device confidence is below threshold.
 */
@Singleton
class OpenAiWhisperTranscriber @Inject constructor(
    private val apiConfig: ApiConfig,
) : Transcriber {

    // Cache the API instance and recreate when config changes
    private var cachedBaseUrl: String = ""
    private var cachedApiKey: String = ""
    private var cachedApi: WhisperApi? = null

    private fun getApi(): WhisperApi {
        val currentBaseUrl = apiConfig.whisperBaseUrl
        val currentApiKey = apiConfig.whisperApiKey

        if (cachedApi == null || cachedBaseUrl != currentBaseUrl || cachedApiKey != currentApiKey) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $currentApiKey")
                        .build()
                    chain.proceed(request)
                }
                .build()

            cachedApi = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WhisperApi::class.java)

            cachedBaseUrl = currentBaseUrl
            cachedApiKey = currentApiKey
        }

        return cachedApi!!
    }

    override suspend fun transcribe(wavFilePath: String): TranscriptionResult {
        val file = File(wavFilePath)
        if (!file.exists()) {
            return TranscriptionResult("", "unknown", 0f)
        }

        val requestBody = file.asRequestBody("audio/wav".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

        return try {
            val api = getApi()
            val response = api.transcribe(
                file = filePart,
                model = apiConfig.whisperModel,
                responseFormat = "verbose_json",
                language = null, // auto-detect
            )

            TranscriptionResult(
                text = response.text,
                language = response.language ?: "unknown",
                confidence = response.segments?.map { it.avgLogprob }
                    ?.average()?.let { Math.exp(it).toFloat() } ?: 0.8f,
                segments = response.segments?.map {
                    TranscriptionSegment(
                        startMs = (it.start * 1000).toLong(),
                        endMs = (it.end * 1000).toLong(),
                        text = it.text,
                    )
                } ?: emptyList(),
            )
        } catch (e: Exception) {
            TranscriptionResult("", "unknown", 0f)
        }
    }
}

/**
 * Retrofit interface for OpenAI-compatible Whisper API.
 */
interface WhisperApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: String = "whisper-1",
        @Part("response_format") responseFormat: String = "verbose_json",
        @Part("language") language: String? = null,
    ): WhisperResponse
}

data class WhisperResponse(
    val text: String,
    val language: String?,
    val segments: List<WhisperSegment>?,
)

data class WhisperSegment(
    val start: Double,
    val end: Double,
    val text: String,
    @SerializedName("avg_logprob") val avgLogprob: Double = 0.0,
)

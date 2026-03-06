package com.audiomemory.service.processing

import com.audiomemory.util.ApiConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts structured memories from transcribed text using a configurable LLM API.
 *
 * Supports any OpenAI-compatible API (OpenAI, DeepSeek, Groq, local LLM, etc.)
 * by configuring base URL, model name, and API key in ApiConfig.
 */
@Singleton
class GptMemoryExtractor @Inject constructor(
    private val apiConfig: ApiConfig,
) {
    private val gson = Gson()

    // Cache the API instance and recreate when config changes
    private var cachedBaseUrl: String = ""
    private var cachedApiKey: String = ""
    private var cachedApi: ChatApi? = null

    private fun getApi(): ChatApi {
        val currentBaseUrl = apiConfig.llmBaseUrl
        val currentApiKey = apiConfig.llmApiKey

        // Recreate if config has changed
        if (cachedApi == null || cachedBaseUrl != currentBaseUrl || cachedApiKey != currentApiKey) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
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
                .create(ChatApi::class.java)

            cachedBaseUrl = currentBaseUrl
            cachedApiKey = currentApiKey
        }

        return cachedApi!!
    }

    /**
     * Extract memories from a transcription with speaker information.
     */
    suspend fun extract(
        transcription: String,
        speakerSegments: List<SpeakerTranscriptSegment>,
    ): ExtractionResult {
        val transcript = buildAnnotatedTranscript(speakerSegments)

        val systemPrompt = """
You are a memory extraction system for an audio recording app. Your job is to extract the most important information from conversations.

Analyze the following transcription and extract:
1. **memories**: Key facts, decisions, action items, insights, and preferences mentioned
2. **ownerInsights**: Observations about the Owner's communication patterns, preferences, interests
3. **tags**: Topic tags for categorization

Rules:
- Focus on actionable, memorable, or important information
- Assign importance scores 1-5 (5 = critical decision/action, 1 = minor detail)
- Identify memory type: FACT, DECISION, ACTION, INSIGHT, or PREFERENCE
- Mark which speaker said each memory item
- If the Owner is speaking, set isOwnerSpeech = true
- Generate concise, clear memory text (not raw transcript)
- Tags should be lowercase, topic-focused (e.g., "tech", "budget", "team")

Respond ONLY with valid JSON matching this schema:
{
  "memories": [
    {
      "content": "string - the extracted memory",
      "type": "FACT|DECISION|ACTION|INSIGHT|PREFERENCE",
      "importanceScore": 1-5,
      "speakerName": "string - who said it",
      "isOwnerSpeech": boolean,
      "tags": ["string"]
    }
  ],
  "ownerInsights": [
    {
      "observation": "string - what you learned about the owner",
      "category": "topic|preference|style|relationship"
    }
  ],
  "tags": ["string - all unique tags"]
}
""".trimIndent()

        val userMessage = if (transcript.isNotBlank()) {
            "Transcription:\n$transcript"
        } else {
            "Transcription:\n$transcription"
        }

        return try {
            val api = getApi()
            val response = api.chat(ChatRequest(
                model = apiConfig.llmModel,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userMessage),
                ),
                temperature = 0.3,
                maxTokens = 2000,
                responseFormat = ResponseFormat("json_object"),
            ))

            val content = response.choices.firstOrNull()?.message?.content ?: "{}"
            gson.fromJson(content, ExtractionResult::class.java)
        } catch (e: Exception) {
            ExtractionResult(emptyList(), emptyList(), emptyList())
        }
    }

    private fun buildAnnotatedTranscript(segments: List<SpeakerTranscriptSegment>): String {
        if (segments.isEmpty()) return ""
        return segments.joinToString("\n") { seg ->
            "[${seg.speakerName}]: ${seg.text}"
        }
    }
}

data class SpeakerTranscriptSegment(
    val speakerName: String,
    val isOwner: Boolean,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class ExtractionResult(
    val memories: List<ExtractedMemory> = emptyList(),
    val ownerInsights: List<OwnerInsight> = emptyList(),
    val tags: List<String> = emptyList(),
)

data class ExtractedMemory(
    val content: String,
    val type: String, // FACT, DECISION, ACTION, INSIGHT, PREFERENCE
    val importanceScore: Int = 1,
    val speakerName: String? = null,
    val isOwnerSpeech: Boolean = false,
    val tags: List<String> = emptyList(),
)

data class OwnerInsight(
    val observation: String,
    val category: String, // topic, preference, style, relationship
)

// ---- Retrofit API interfaces ----

interface ChatApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int = 2000,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
)

data class ChatMessage(val role: String, val content: String)
data class ResponseFormat(val type: String)

data class ChatResponse(
    val choices: List<ChatChoice>,
)

data class ChatChoice(
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String?,
)

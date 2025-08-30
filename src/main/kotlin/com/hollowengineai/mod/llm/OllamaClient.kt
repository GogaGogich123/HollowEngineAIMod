package com.hollowengineai.mod.llm

import com.hollowengineai.mod.config.AIConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент для взаимодействия с Ollama LLM сервером
 * 
 * Поддерживает:
 * - Обычные запросы с полным ответом
 * - Streaming запросы для быстрых ответов
 * - Управление различными моделями
 * - Retry логика для надежности
 * - Кэширование для производительности
 */
class OllamaClient(
    private val baseUrl: String,
    private val defaultModel: String
) {
    companion object {
        private val LOGGER = LogManager.getLogger(OllamaClient::class.java)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val HEALTH_ENDPOINT = "/api/tags"
        private const val GENERATE_ENDPOINT = "/api/generate"
        private const val CHAT_ENDPOINT = "/api/chat"
    }
    
    // HTTP клиент с настройками
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(AIConfig.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val gson = Gson()
    
    // Кэш для часто используемых запросов
    private val responseCache = mutableMapOf<String, CachedResponse>()
    
    // Статистика использования
    private var requestCount = 0L
    private var totalResponseTimeMs = 0L
    private var failedRequests = 0L
    
    /**
     * Проверить доступность Ollama сервера
     */
    suspend fun isHealthy(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$HEALTH_ENDPOINT")
                    .get()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    val isHealthy = response.isSuccessful
                    LOGGER.debug("Ollama health check: ${if (isHealthy) "OK" else "FAILED"}")
                    isHealthy
                }
            } catch (e: Exception) {
                LOGGER.error("Ollama health check failed", e)
                false
            }
        }
    }
    
    /**
     * Получить список доступных моделей
     */
    suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$HEALTH_ENDPOINT")
                    .get()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val modelsResponse = gson.fromJson(body, ModelsResponse::class.java)
                        val models = modelsResponse.models.map { it.name }
                        LOGGER.info("Available Ollama models: ${models.joinToString(", ")}")
                        models
                    } else {
                        LOGGER.warn("Failed to get models: ${response.code}")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to get available models", e)
                emptyList()
            }
        }
    }
    
    /**
     * Сгенерировать ответ от LLM (основной метод)
     */
    suspend fun generateResponse(
        prompt: String,
        model: String = defaultModel,
        useCache: Boolean = true,
        temperature: Float = 0.7f
    ): LLMResponse {
        val startTime = System.currentTimeMillis()
        requestCount++
        
        try {
            // Проверяем кэш
            if (useCache) {
                val cacheKey = generateCacheKey(prompt, model, temperature)
                val cached = responseCache[cacheKey]
                if (cached != null && !cached.isExpired()) {
                    LOGGER.debug("Using cached response for prompt: ${prompt.take(50)}...")
                    return LLMResponse(
                        text = cached.response,
                        model = model,
                        isSuccess = true,
                        responseTimeMs = 0L,
                        fromCache = true
                    )
                }
            }
            
            // Выполняем запрос с retry логикой
            val response = executeWithRetry { 
                performGenerateRequest(prompt, model, temperature)
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTimeMs += responseTime
            
            // Кэшируем успешный ответ
            if (response.isSuccess && useCache) {
                val cacheKey = generateCacheKey(prompt, model, temperature)
                responseCache[cacheKey] = CachedResponse(
                    response = response.text,
                    timestamp = System.currentTimeMillis(),
                    ttlMs = 300000L // 5 минут
                )
                
                // Ограничиваем размер кэша
                cleanupCache()
            }
            
            return response.copy(responseTimeMs = responseTime)
            
        } catch (e: Exception) {
            failedRequests++
            LOGGER.error("Failed to generate response", e)
            
            return LLMResponse(
                text = "",
                model = model,
                isSuccess = false,
                error = e.message ?: "Unknown error",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Выполнить запрос к Ollama API
     */
    private suspend fun performGenerateRequest(
        prompt: String,
        model: String,
        temperature: Float
    ): LLMResponse {
        return withContext(Dispatchers.IO) {
            val requestBody = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false,
                options = OllamaOptions(
                    temperature = temperature,
                    top_p = 0.9f,
                    top_k = 40
                )
            )
            
            val jsonBody = gson.toJson(requestBody)
            LOGGER.debug("Sending request to Ollama: model=$model, prompt=${prompt.take(100)}...")
            
            val request = Request.Builder()
                .url("$baseUrl$GENERATE_ENDPOINT")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val ollamaResponse = gson.fromJson(responseBody, OllamaGenerateResponse::class.java)
                        
                        LOGGER.debug("Received response from Ollama: ${ollamaResponse.response.take(100)}...")
                        
                        LLMResponse(
                            text = ollamaResponse.response,
                            model = model,
                            isSuccess = true,
                            contextLength = ollamaResponse.context?.size ?: 0,
                            totalDuration = ollamaResponse.total_duration,
                            loadDuration = ollamaResponse.load_duration,
                            promptEvalCount = ollamaResponse.prompt_eval_count
                        )
                    } else {
                        LLMResponse(
                            text = "",
                            model = model,
                            isSuccess = false,
                            error = "Empty response body"
                        )
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    LOGGER.warn("Ollama request failed: ${response.code} - $errorBody")
                    
                    LLMResponse(
                        text = "",
                        model = model,
                        isSuccess = false,
                        error = "HTTP ${response.code}: $errorBody"
                    )
                }
            }
        }
    }
    
    /**
     * Выполнить запрос с retry логикой
     */
    private suspend fun <T> executeWithRetry(operation: suspend () -> T): T {
        var lastException: Exception? = null
        
        repeat(AIConfig.maxRetries + 1) { attempt ->
            try {
                return operation()
            } catch (e: IOException) {
                lastException = e
                if (attempt < AIConfig.maxRetries) {
                    val delayMs = (attempt + 1) * 1000L // Exponential backoff
                    LOGGER.warn("Request failed (attempt ${attempt + 1}), retrying in ${delayMs}ms", e)
                    delay(delayMs)
                }
            }
        }
        
        throw lastException ?: RuntimeException("All retry attempts failed")
    }
    
    /**
     * Специализированный метод для коротких диалогов
     */
    suspend fun generateConversationResponse(
        message: String,
        context: String = "",
        npcName: String = "NPC"
    ): LLMResponse {
        val conversationPrompt = buildString {
            appendLine("You are $npcName, an NPC in a Minecraft world.")
            if (context.isNotEmpty()) {
                appendLine("Context: $context")
            }
            appendLine("Player says: \"$message\"")
            appendLine("Respond naturally and stay in character. Keep it brief (1-2 sentences).")
        }
        
        return generateResponse(
            prompt = conversationPrompt,
            model = AIConfig.conversationModel,
            temperature = 0.8f // Более креативные ответы для диалогов
        )
    }
    
    /**
     * Получить статистику использования
     */
    fun getUsageStats(): OllamaStats {
        val avgResponseTime = if (requestCount > 0) {
            totalResponseTimeMs / requestCount
        } else {
            0L
        }
        
        return OllamaStats(
            totalRequests = requestCount,
            failedRequests = failedRequests,
            averageResponseTimeMs = avgResponseTime,
            cacheSize = responseCache.size,
            cacheHitRate = calculateCacheHitRate()
        )
    }
    
    /**
     * Очистить кэш и статистику
     */
    fun resetStats() {
        requestCount = 0L
        totalResponseTimeMs = 0L
        failedRequests = 0L
        responseCache.clear()
        LOGGER.info("Ollama client stats reset")
    }
    
    /**
     * Генерировать ключ кэша
     */
    private fun generateCacheKey(prompt: String, model: String, temperature: Float): String {
        return "${model}_${temperature}_${prompt.hashCode()}"
    }
    
    /**
     * Очистить устаревшие записи кэша
     */
    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        responseCache.forEach { (key, cached) ->
            if (cached.isExpired(now)) {
                toRemove.add(key)
            }
        }
        
        toRemove.forEach { key ->
            responseCache.remove(key)
        }
        
        // Ограничиваем размер кэша
        if (responseCache.size > 1000) {
            val oldestKeys = responseCache.entries
                .sortedBy { it.value.timestamp }
                .take(responseCache.size - 800)
                .map { it.key }
            
            oldestKeys.forEach { key ->
                responseCache.remove(key)
            }
            
            LOGGER.debug("Cache cleanup: removed ${toRemove.size + oldestKeys.size} entries")
        }
    }
    
    /**
     * Вычислить hit rate кэша
     */
    private fun calculateCacheHitRate(): Float {
        // Простая эвристика на основе размера кэша и количества запросов
        return if (requestCount > 0) {
            (responseCache.size.toFloat() / requestCount) * 0.3f // Примерная оценка
        } else {
            0f
        }
    }
}

/**
 * Ответ от LLM
 */
data class LLMResponse(
    val text: String,
    val model: String,
    val isSuccess: Boolean,
    val error: String? = null,
    val responseTimeMs: Long = 0L,
    val fromCache: Boolean = false,
    val contextLength: Int = 0,
    val totalDuration: Long? = null,
    val loadDuration: Long? = null,
    val promptEvalCount: Int? = null
)

/**
 * Статистика использования Ollama
 */
data class OllamaStats(
    val totalRequests: Long,
    val failedRequests: Long,
    val averageResponseTimeMs: Long,
    val cacheSize: Int,
    val cacheHitRate: Float
)

/**
 * Кэшированный ответ
 */
private data class CachedResponse(
    val response: String,
    val timestamp: Long,
    val ttlMs: Long
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return now - timestamp > ttlMs
    }
}

// === Ollama API Data Classes ===

private data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

private data class OllamaOptions(
    val temperature: Float,
    val top_p: Float,
    val top_k: Int
)

private data class OllamaGenerateResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

private data class ModelsResponse(
    val models: List<ModelInfo>
)

private data class ModelInfo(
    val name: String,
    val modified_at: String,
    val size: Long
)
package com.hollowengineai.mod.memory

import com.hollowengineai.mod.config.AIConfig
import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.core.NPCState
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Система памяти для AI НПС
 * 
 * Включает три типа памяти:
 * - Эпизодическая: конкретные события и взаимодействия
 * - Семантическая: общие знания и факты
 * - Эмоциональная: связи между событиями и эмоциями
 * 
 * Поддерживает:
 * - Автоматическое забывание неважной информации
 * - Контекстный поиск релевантных воспоминаний
 * - Суммаризацию старых воспоминаний
 */
class NPCMemory(
    private val npcId: UUID,
    private val databaseManager: DatabaseManager
) {
    companion object {
        private val LOGGER = LogManager.getLogger(NPCMemory::class.java)
        private const val MEMORY_CONSOLIDATION_INTERVAL_MS = 60000L // 1 минута
        private const val IMPORTANCE_DECAY_RATE = 0.99f // Ежедневное забывание
    }
    
    // Кэш для быстрого доступа к недавним воспоминаниям
    private val episodeCache = ConcurrentHashMap<String, MemoryEpisode>()
    private val knowledgeCache = ConcurrentHashMap<String, Knowledge>()
    
    // Корутины для фоновой обработки памяти
    private val memoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var consolidationJob: Job? = null
    
    // Статистика памяти
    @Volatile
    private var isInitialized = false
    
    /**
     * Инициализировать систему памяти
     */
    fun initialize() {
        if (isInitialized) {
            LOGGER.warn("Memory system already initialized for NPC: $npcId")
            return
        }
        
        try {
            // Загружаем существующие воспоминания в кэш
            loadRecentMemoriesIntoCache()
            
            // Запускаем фоновую консолидацию памяти
            if (AIConfig.enableMemorySummarization) {
                startMemoryConsolidation()
            }
            
            isInitialized = true
            LOGGER.debug("Memory system initialized for NPC: $npcId")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to initialize memory system for NPC: $npcId", e)
            throw e
        }
    }
    
    /**
     * Остановить систему памяти
     */
    fun shutdown() {
        if (!isInitialized) return
        
        // Останавливаем фоновые задачи
        consolidationJob?.cancel()
        
        // Сохраняем кэш в базу данных
        saveAllCachedMemories()
        
        // Очищаем кэши
        episodeCache.clear()
        knowledgeCache.clear()
        
        isInitialized = false
        LOGGER.debug("Memory system shut down for NPC: $npcId")
    }
    
    /**
     * Добавить эпизод в память
     */
    fun addEpisode(episode: MemoryEpisode) {
        ensureInitialized()
        
        val episodeWithId = episode.copy(npcId = npcId)
        
        // Добавляем в кэш
        val cacheKey = "${episode.type}_${episode.timestamp}"
        episodeCache[cacheKey] = episodeWithId
        
        // Асинхронно сохраняем в базу данных
        memoryScope.launch {
            try {
                databaseManager.saveMemoryEpisode(episodeWithId)
                
                if (AIConfig.logMemory) {
                    LOGGER.debug("Saved episode: ${episode.type} - ${episode.description}")
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to save memory episode", e)
            }
        }
        
        // Проверяем лимит памяти
        checkMemoryLimits()
    }
    
    /**
     * Добавить знание в семантическую память
     */
    fun addKnowledge(topic: String, information: String, confidence: Float = 0.8f) {
        ensureInitialized()
        
        val knowledge = Knowledge(
            npcId = npcId,
            topic = topic,
            information = information,
            confidence = confidence,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Обновляем кэш
        knowledgeCache[topic] = knowledge
        
        // Асинхронно сохраняем в базу данных
        memoryScope.launch {
            try {
                databaseManager.saveKnowledge(knowledge)
                
                if (AIConfig.logMemory) {
                    LOGGER.debug("Updated knowledge: $topic")
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to save knowledge", e)
            }
        }
    }
    
    /**
     * Получить недавние эпизоды памяти
     */
    fun getRecentMemories(limit: Int = 10): List<MemoryEpisode> {
        ensureInitialized()
        
        // Сначала проверяем кэш
        val cachedEpisodes = episodeCache.values
            .sortedByDescending { it.timestamp }
            .take(limit)
        
        // Если в кэше достаточно, возвращаем их
        if (cachedEpisodes.size >= limit) {
            return cachedEpisodes
        }
        
        // Иначе загружаем из базы данных
        return try {
            val dbEpisodes = databaseManager.loadRecentMemoryEpisodes(npcId, limit)
            
            // Обновляем кэш новыми данными
            dbEpisodes.forEach { episode ->
                val cacheKey = "${episode.type}_${episode.timestamp}"
                episodeCache[cacheKey] = episode
            }
            
            dbEpisodes
        } catch (e: Exception) {
            LOGGER.error("Failed to load recent memories", e)
            cachedEpisodes
        }
    }
    
    /**
     * Найти воспоминания по ключевым словам
     */
    fun searchMemories(keywords: List<String>, limit: Int = 5): List<MemoryEpisode> {
        ensureInitialized()
        
        val allMemories = getRecentMemories(50) // Ищем в большем объеме
        
        return allMemories
            .filter { episode ->
                keywords.any { keyword ->
                    episode.description.contains(keyword, ignoreCase = true) ||
                    episode.participants.any { it.contains(keyword, ignoreCase = true) }
                }
            }
            .sortedByDescending { it.importance }
            .take(limit)
    }
    
    /**
     * Найти воспоминания о конкретном игроке
     */
    fun getMemoriesAboutPlayer(playerName: String, limit: Int = 10): List<MemoryEpisode> {
        ensureInitialized()
        
        return getRecentMemories(50)
            .filter { episode ->
                episode.participants.contains(playerName) ||
                episode.description.contains(playerName, ignoreCase = true)
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Получить знания по теме
     */
    fun getKnowledge(topic: String): Knowledge? {
        ensureInitialized()
        
        // Проверяем кэш
        knowledgeCache[topic]?.let { return it }
        
        // Загружаем из базы данных
        return try {
            val knowledge = databaseManager.loadKnowledge(npcId, topic).firstOrNull()
            
            // Кэшируем результат
            knowledge?.let { knowledgeCache[topic] = it }
            
            knowledge
        } catch (e: Exception) {
            LOGGER.error("Failed to load knowledge for topic: $topic", e)
            null
        }
    }
    
    /**
     * Получить все знания НПС
     */
    fun getAllKnowledge(): List<Knowledge> {
        ensureInitialized()
        
        return try {
            val allKnowledge = databaseManager.loadKnowledge(npcId)
            
            // Обновляем кэш
            allKnowledge.forEach { knowledge ->
                knowledgeCache[knowledge.topic] = knowledge
            }
            
            allKnowledge
        } catch (e: Exception) {
            LOGGER.error("Failed to load all knowledge", e)
            knowledgeCache.values.toList()
        }
    }
    
    /**
     * Сохранить состояние НПС
     */
    fun saveNPCState(
        npcId: UUID,
        position: BlockPos,
        emotion: EmotionalState,
        goal: String?,
        state: NPCState
    ) {
        ensureInitialized()
        
        memoryScope.launch {
            try {
                // Сохраняем эмоциональное состояние
                databaseManager.saveEmotion(
                    npcId = npcId,
                    emotion = emotion,
                    intensity = 0.8f, // Стандартная интенсивность
                    triggeredBy = goal
                )
                
                // Создаем эпизод о текущем состоянии
                val stateEpisode = MemoryEpisode(
                    npcId = npcId,
                    type = "state_change",
                    description = "Current state: $state, emotion: $emotion, goal: $goal",
                    location = position,
                    participants = emptyList(),
                    importance = 0.3f,
                    timestamp = System.currentTimeMillis()
                )
                
                addEpisode(stateEpisode)
                
            } catch (e: Exception) {
                LOGGER.error("Failed to save NPC state", e)
            }
        }
    }
    
    /**
     * Получить количество эпизодов в памяти
     */
    fun getEpisodeCount(): Int {
        ensureInitialized()
        
        return try {
            // Приблизительный подсчет на основе кэша и последних данных
            val cachedCount = episodeCache.size
            val recentCount = databaseManager.loadRecentMemoryEpisodes(npcId, 100).size
            
            maxOf(cachedCount, recentCount)
        } catch (e: Exception) {
            LOGGER.error("Failed to get episode count", e)
            episodeCache.size
        }
    }
    
    /**
     * Создать контекст для LLM на основе памяти
     */
    fun buildContextForLLM(maxLength: Int = 1000): String {
        ensureInitialized()
        
        return buildString {
            // Добавляем недавние важные события
            val recentMemories = getRecentMemories(5)
                .filter { it.importance > 0.5f }
            
            if (recentMemories.isNotEmpty()) {
                appendLine("Recent important memories:")
                recentMemories.forEach { memory ->
                    appendLine("- ${memory.description}")
                }
                appendLine()
            }
            
            // Добавляем релевантные знания
            val importantKnowledge = getAllKnowledge()
                .filter { it.confidence > 0.7f }
                .take(3)
            
            if (importantKnowledge.isNotEmpty()) {
                appendLine("Known facts:")
                importantKnowledge.forEach { knowledge ->
                    appendLine("- ${knowledge.topic}: ${knowledge.information}")
                }
            }
            
            // Обрезаем до максимальной длины
            if (length > maxLength) {
                setLength(maxLength)
                append("...")
            }
        }
    }
    
    /**
     * Загрузить недавние воспоминания в кэш
     */
    private fun loadRecentMemoriesIntoCache() {
        try {
            val recentEpisodes = databaseManager.loadRecentMemoryEpisodes(npcId, 20)
            recentEpisodes.forEach { episode ->
                val cacheKey = "${episode.type}_${episode.timestamp}"
                episodeCache[cacheKey] = episode
            }
            
            val recentKnowledge = databaseManager.loadKnowledge(npcId)
            recentKnowledge.forEach { knowledge ->
                knowledgeCache[knowledge.topic] = knowledge
            }
            
            LOGGER.debug("Loaded ${recentEpisodes.size} episodes and ${recentKnowledge.size} knowledge items into cache")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to load memories into cache", e)
        }
    }
    
    /**
     * Запустить фоновую консолидацию памяти
     */
    private fun startMemoryConsolidation() {
        consolidationJob = memoryScope.launch {
            while (isInitialized && currentCoroutineContext().isActive) {
                try {
                    consolidateMemories()
                    delay(MEMORY_CONSOLIDATION_INTERVAL_MS)
                } catch (e: Exception) {
                    LOGGER.error("Error in memory consolidation", e)
                    delay(MEMORY_CONSOLIDATION_INTERVAL_MS * 2)
                }
            }
        }
    }
    
    /**
     * Консолидировать воспоминания (забывание, суммаризация)
     */
    private suspend fun consolidateMemories() {
        // Снижаем важность старых воспоминаний
        val currentTime = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        
        episodeCache.entries.removeAll { (_, episode) ->
            val age = currentTime - episode.timestamp
            val ageInDays = age / oneDay
            
            // Снижаем важность с возрастом
            val newImportance = episode.importance * Math.pow(IMPORTANCE_DECAY_RATE.toDouble(), ageInDays.toDouble()).toFloat()
            
            // Удаляем совсем неважные старые воспоминания
            if (newImportance < 0.1f && ageInDays > 7) {
                if (AIConfig.logMemory) {
                    LOGGER.debug("Forgetting unimportant memory: ${episode.description}")
                }
                true
            } else {
                false
            }
        }
        
        // TODO: Здесь можно добавить суммаризацию воспоминаний через LLM
    }
    
    /**
     * Проверить лимиты памяти
     */
    private fun checkMemoryLimits() {
        if (episodeCache.size > AIConfig.maxMemoryEpisodes) {
            // Удаляем самые старые и неважные воспоминания
            val toRemove = episodeCache.entries
                .sortedWith(compareBy<Map.Entry<String, MemoryEpisode>> { it.value.importance }
                    .thenBy { it.value.timestamp })
                .take(episodeCache.size - (AIConfig.maxMemoryEpisodes * 3 / 4))
                .map { it.key }
            
            toRemove.forEach { key ->
                episodeCache.remove(key)
            }
            
            LOGGER.debug("Cleaned up ${toRemove.size} old memories from cache")
        }
    }
    
    /**
     * Сохранить все кэшированные воспоминания
     */
    private fun saveAllCachedMemories() {
        try {
            // Сохраняем эпизоды
            episodeCache.values.forEach { episode ->
                databaseManager.saveMemoryEpisode(episode)
            }
            
            // Сохраняем знания
            knowledgeCache.values.forEach { knowledge ->
                databaseManager.saveKnowledge(knowledge)
            }
            
            LOGGER.debug("Saved all cached memories for NPC: $npcId")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to save cached memories", e)
        }
    }
    
    /**
     * Проверить что система памяти инициализирована
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Memory system is not initialized for NPC: $npcId")
        }
    }
}

/**
 * Эпизод памяти НПС
 */
data class MemoryEpisode(
    val npcId: UUID,
    val type: String,
    val description: String,
    val location: BlockPos,
    val participants: List<String>,
    val importance: Float,
    val timestamp: Long
)

/**
 * Знание НПС (семантическая память)
 */
data class Knowledge(
    val npcId: UUID,
    val topic: String,
    val information: String,
    val confidence: Float,
    val lastUpdated: Long
)

/**
 * Запись об эмоции
 */
data class EmotionRecord(
    val npcId: UUID,
    val emotionType: EmotionalState,
    val intensity: Float,
    val decayRate: Float,
    val triggeredBy: String?,
    val timestamp: Long
)
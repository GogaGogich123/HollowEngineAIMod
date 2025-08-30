package com.hollowengineai.mod.reputation

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.events.NPCEvent
import com.hollowengineai.mod.events.NPCEventBus
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Глобальная система репутации для отслеживания отношений игроков с NPC и фракциями
 * 
 * Особенности:
 * - Персистентное сохранение данных репутации
 * - Множественные фракции и группы
 * - Автоматическое затухание репутации со временем
 * - События для изменения репутации
 * - Интеграция с действиями NPC
 */
@Serializable
object ReputationSystem {
    
    // Константы репутации
    private const val MAX_REPUTATION = 1000f
    private const val MIN_REPUTATION = -1000f
    private const val DECAY_RATE = 0.95f // Репутация затухает на 5% каждый день
    private const val SAVE_INTERVAL = 300000L // Сохранение каждые 5 минут
    
    // Главная карта репутации: PlayerUUID -> FactionId -> ReputationData
    private val playerReputations = ConcurrentHashMap<String, ConcurrentHashMap<String, ReputationData>>()
    
    // Кэш для быстрого доступа
    private val reputationCache = ConcurrentHashMap<String, Float>()
    
    // Глобальные модификаторы репутации
    private val globalModifiers = ConcurrentHashMap<String, Float>()
    
    // Настройки системы
    private var decayEnabled = true
    private var saveEnabled = true
    private var lastDecayTime = System.currentTimeMillis()
    private var lastSaveTime = System.currentTimeMillis()
    
    // Корутинная область для фоновых задач
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // JSON для сериализации
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Файл для сохранения данных
    private val reputationFile = File("config/hollowengineai/reputation_data.json")
    
    init {
        loadReputationData()
        startBackgroundTasks()
        
        HollowEngineAIMod.LOGGER.info("ReputationSystem initialized")
    }
    
    /**
     * Получить репутацию игрока у конкретной фракции
     */
    fun getReputation(playerUUID: String, factionId: String): Float {
        val cacheKey = "$playerUUID:$factionId"
        
        return reputationCache.getOrPut(cacheKey) {
            playerReputations[playerUUID]?.get(factionId)?.currentValue ?: 0f
        }
    }
    
    /**
     * Получить репутацию игрока у конкретной фракции (с Player объектом)
     */
    fun getReputation(player: Player, factionId: String): Float {
        return getReputation(player.stringUUID, factionId)
    }
    
    /**
     * Изменить репутацию игрока
     */
    fun modifyReputation(
        playerUUID: String,
        factionId: String,
        change: Float,
        reason: String = "Unknown",
        decay: Boolean = true
    ) {
        val playerReps = playerReputations.getOrPut(playerUUID) { ConcurrentHashMap() }
        val reputationData = playerReps.getOrPut(factionId) { 
            ReputationData(
                factionId = factionId,
                currentValue = 0f,
                baseValue = 0f,
                lastUpdate = System.currentTimeMillis(),
                history = mutableListOf(),
                decaysOverTime = decay
            )
        }
        
        // Применяем глобальные модификаторы
        val globalMod = globalModifiers.getOrDefault(factionId, 1f)
        val adjustedChange = change * globalMod
        
        // Обновляем репутацию
        val oldValue = reputationData.currentValue
        val newValue = clampReputation(oldValue + adjustedChange)
        
        reputationData.currentValue = newValue
        reputationData.baseValue = newValue
        reputationData.lastUpdate = System.currentTimeMillis()
        
        // Добавляем в историю
        reputationData.history.add(
            ReputationChange(
                change = adjustedChange,
                reason = reason,
                timestamp = System.currentTimeMillis(),
                finalValue = newValue
            )
        )
        
        // Ограничиваем размер истории
        if (reputationData.history.size > 100) {
            reputationData.history.removeFirst()
        }
        
        // Обновляем кэш
        val cacheKey = "$playerUUID:$factionId"
        reputationCache[cacheKey] = newValue
        
        // Логируем изменение
        HollowEngineAIMod.LOGGER.debug(
            "Reputation changed for player $playerUUID with faction $factionId: " +
            "$oldValue -> $newValue (change: $adjustedChange, reason: $reason)"
        )
        
        // Отправляем событие если есть eventBus (опционально)
        notifyReputationChange(playerUUID, factionId, oldValue, newValue, reason)
    }
    
    /**
     * Изменить репутацию игрока (с Player объектом)
     */
    fun modifyReputation(
        player: Player,
        factionId: String,
        change: Float,
        reason: String = "Unknown",
        decay: Boolean = true
    ) {
        modifyReputation(player.stringUUID, factionId, change, reason, decay)
    }
    
    /**
     * Получить все репутации игрока
     */
    fun getAllReputations(playerUUID: String): Map<String, Float> {
        return playerReputations[playerUUID]?.mapValues { it.value.currentValue } ?: emptyMap()
    }
    
    /**
     * Получить все репутации игрока (с Player объектом)
     */
    fun getAllReputations(player: Player): Map<String, Float> {
        return getAllReputations(player.stringUUID)
    }
    
    /**
     * Получить детальную информацию о репутации
     */
    fun getReputationDetails(playerUUID: String, factionId: String): ReputationData? {
        return playerReputations[playerUUID]?.get(factionId)
    }
    
    /**
     * Получить уровень репутации в текстовом виде
     */
    fun getReputationLevel(reputation: Float): ReputationLevel {
        return when {
            reputation >= 800f -> ReputationLevel.REVERED
            reputation >= 600f -> ReputationLevel.EXALTED
            reputation >= 400f -> ReputationLevel.HONORED
            reputation >= 200f -> ReputationLevel.FRIENDLY
            reputation >= 50f -> ReputationLevel.LIKED
            reputation >= -50f -> ReputationLevel.NEUTRAL
            reputation >= -200f -> ReputationLevel.DISLIKED
            reputation >= -400f -> ReputationLevel.UNFRIENDLY
            reputation >= -600f -> ReputationLevel.HOSTILE
            reputation >= -800f -> ReputationLevel.HATED
            else -> ReputationLevel.DESPISED
        }
    }
    
    /**
     * Получить уровень репутации игрока у фракции
     */
    fun getReputationLevel(playerUUID: String, factionId: String): ReputationLevel {
        val reputation = getReputation(playerUUID, factionId)
        return getReputationLevel(reputation)
    }
    
    /**
     * Проверить, может ли игрок выполнить действие на основе репутации
     */
    fun canPerformAction(
        playerUUID: String,
        factionId: String,
        requiredLevel: ReputationLevel
    ): Boolean {
        val currentLevel = getReputationLevel(playerUUID, factionId)
        return currentLevel.value >= requiredLevel.value
    }
    
    /**
     * Установить глобальный модификатор для фракции
     */
    fun setGlobalModifier(factionId: String, modifier: Float) {
        globalModifiers[factionId] = modifier
        HollowEngineAIMod.LOGGER.info("Set global reputation modifier for $factionId: $modifier")
    }
    
    /**
     * Получить топ игроков по репутации у фракции
     */
    fun getTopPlayers(factionId: String, limit: Int = 10): List<Pair<String, Float>> {
        return playerReputations.entries
            .mapNotNull { (playerUUID, factions) ->
                factions[factionId]?.let { data ->
                    playerUUID to data.currentValue
                }
            }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Получить среднюю репутацию игрока
     */
    fun getAverageReputation(playerUUID: String): Float {
        val reps = getAllReputations(playerUUID)
        return if (reps.isNotEmpty()) {
            reps.values.sum() / reps.size
        } else {
            0f
        }
    }
    
    /**
     * Сбросить репутацию игрока у фракции
     */
    fun resetReputation(playerUUID: String, factionId: String, reason: String = "Reset") {
        modifyReputation(playerUUID, factionId, -getReputation(playerUUID, factionId), reason, false)
    }
    
    /**
     * Сбросить всю репутацию игрока
     */
    fun resetAllReputation(playerUUID: String, reason: String = "Full reset") {
        playerReputations[playerUUID]?.keys?.forEach { factionId ->
            resetReputation(playerUUID, factionId, reason)
        }
    }
    
    /**
     * Получить статистику системы репутации
     */
    fun getSystemStats(): ReputationStats {
        val totalPlayers = playerReputations.size
        val totalFactions = playerReputations.values
            .flatMap { it.keys }
            .toSet()
            .size
        val totalEntries = playerReputations.values
            .sumOf { it.size }
        val averageReputation = playerReputations.values
            .flatMap { it.values }
            .map { it.currentValue }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat() ?: 0f
        
        return ReputationStats(
            totalPlayers = totalPlayers,
            totalFactions = totalFactions,
            totalEntries = totalEntries,
            averageReputation = averageReputation,
            cacheHitRate = calculateCacheHitRate(),
            lastDecayTime = lastDecayTime,
            lastSaveTime = lastSaveTime
        )
    }
    
    /**
     * Применить затухание репутации
     */
    private suspend fun applyDecay() {
        if (!decayEnabled) return
        
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - lastDecayTime
        val daysPassed = timeDelta / (24 * 60 * 60 * 1000f) // Дни
        
        if (daysPassed < 1f) return // Применяем затухание только раз в день
        
        var decayedEntries = 0
        
        playerReputations.values.forEach { playerFactions ->
            playerFactions.values.forEach { reputationData ->
                if (reputationData.decaysOverTime && reputationData.currentValue != 0f) {
                    val decayFactor = kotlin.math.pow(DECAY_RATE.toDouble(), daysPassed.toDouble()).toFloat()
                    val newValue = reputationData.currentValue * decayFactor
                    
                    // Применяем затухание только если изменение значительно
                    if (kotlin.math.abs(newValue - reputationData.currentValue) > 0.1f) {
                        reputationData.currentValue = if (kotlin.math.abs(newValue) < 1f) 0f else newValue
                        reputationData.lastUpdate = currentTime
                        decayedEntries++
                    }
                }
            }
        }
        
        // Очищаем кэш после затухания
        reputationCache.clear()
        
        lastDecayTime = currentTime
        
        if (decayedEntries > 0) {
            HollowEngineAIMod.LOGGER.debug("Applied decay to $decayedEntries reputation entries")
        }
    }
    
    /**
     * Сохранить данные репутации
     */
    private suspend fun saveReputationData() {
        if (!saveEnabled) return
        
        try {
            withContext(Dispatchers.IO) {
                // Создаем директорию если не существует
                reputationFile.parentFile?.mkdirs()
                
                // Подготавливаем данные для сохранения
                val saveData = ReputationSaveData(
                    playerReputations = playerReputations.mapValues { (_, factions) ->
                        factions.toMap()
                    }.toMap(),
                    globalModifiers = globalModifiers.toMap(),
                    lastDecayTime = lastDecayTime,
                    version = 1
                )
                
                // Сохраняем в JSON
                val jsonString = json.encodeToString(ReputationSaveData.serializer(), saveData)
                reputationFile.writeText(jsonString)
                
                lastSaveTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to save reputation data", e)
        }
    }
    
    /**
     * Загрузить данные репутации
     */
    private fun loadReputationData() {
        try {
            if (!reputationFile.exists()) {
                HollowEngineAIMod.LOGGER.info("No existing reputation data found, starting fresh")
                return
            }
            
            val jsonString = reputationFile.readText()
            val saveData = json.decodeFromString(ReputationSaveData.serializer(), jsonString)
            
            // Загружаем данные
            playerReputations.clear()
            saveData.playerReputations.forEach { (playerUUID, factions) ->
                val playerFactions = ConcurrentHashMap<String, ReputationData>()
                factions.forEach { (factionId, reputationData) ->
                    playerFactions[factionId] = reputationData
                }
                playerReputations[playerUUID] = playerFactions
            }
            
            globalModifiers.clear()
            globalModifiers.putAll(saveData.globalModifiers)
            
            lastDecayTime = saveData.lastDecayTime
            
            HollowEngineAIMod.LOGGER.info(
                "Loaded reputation data: ${playerReputations.size} players, " +
                "${globalModifiers.size} global modifiers"
            )
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to load reputation data", e)
        }
    }
    
    /**
     * Запуск фоновых задач
     */
    private fun startBackgroundTasks() {
        // Задача затухания репутации
        scope.launch {
            while (true) {
                delay(60000L) // Проверяем каждую минуту
                try {
                    applyDecay()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in reputation decay task", e)
                }
            }
        }
        
        // Задача автосохранения
        scope.launch {
            while (true) {
                delay(SAVE_INTERVAL)
                try {
                    saveReputationData()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in reputation save task", e)
                }
            }
        }
    }
    
    /**
     * Уведомить о изменении репутации
     */
    private fun notifyReputationChange(
        playerUUID: String,
        factionId: String,
        oldValue: Float,
        newValue: Float,
        reason: String
    ) {
        // Здесь можно интегрироваться с NPCEventBus если нужно
        // Пока просто логируем
    }
    
    /**
     * Ограничить репутацию в допустимых пределах
     */
    private fun clampReputation(value: Float): Float {
        return max(MIN_REPUTATION, min(MAX_REPUTATION, value))
    }
    
    /**
     * Рассчитать коэффициент попаданий в кэш
     */
    private fun calculateCacheHitRate(): Float {
        val totalEntries = playerReputations.values.sumOf { it.size }
        val cacheSize = reputationCache.size
        
        return if (totalEntries > 0) {
            (cacheSize.toFloat() / totalEntries) * 100f
        } else {
            0f
        }
    }
    
    /**
     * Очистка при выключении
     */
    fun shutdown() {
        scope.cancel()
        runBlocking {
            saveReputationData()
        }
        HollowEngineAIMod.LOGGER.info("ReputationSystem shut down")
    }
}

/**
 * Данные репутации для конкретной фракции
 */
@Serializable
data class ReputationData(
    val factionId: String,
    var currentValue: Float,
    var baseValue: Float,
    var lastUpdate: Long,
    val history: MutableList<ReputationChange> = mutableListOf(),
    var decaysOverTime: Boolean = true
)

/**
 * Запись об изменении репутации
 */
@Serializable
data class ReputationChange(
    val change: Float,
    val reason: String,
    val timestamp: Long,
    val finalValue: Float
)

/**
 * Уровни репутации
 */
enum class ReputationLevel(val displayName: String, val value: Int, val color: String) {
    DESPISED("Despised", -1000, "§4"),
    HATED("Hated", -800, "§c"),
    HOSTILE("Hostile", -600, "§c"),
    UNFRIENDLY("Unfriendly", -400, "§6"),
    DISLIKED("Disliked", -200, "§e"),
    NEUTRAL("Neutral", 0, "§7"),
    LIKED("Liked", 50, "§a"),
    FRIENDLY("Friendly", 200, "§a"),
    HONORED("Honored", 400, "§2"),
    EXALTED("Exalted", 600, "§b"),
    REVERED("Revered", 800, "§d")
}

/**
 * Статистика системы репутации
 */
data class ReputationStats(
    val totalPlayers: Int,
    val totalFactions: Int,
    val totalEntries: Int,
    val averageReputation: Float,
    val cacheHitRate: Float,
    val lastDecayTime: Long,
    val lastSaveTime: Long
)

/**
 * Данные для сохранения
 */
@Serializable
private data class ReputationSaveData(
    val playerReputations: Map<String, Map<String, ReputationData>>,
    val globalModifiers: Map<String, Float>,
    val lastDecayTime: Long,
    val version: Int
)

/**
 * Утилита для получения UUID игрока как строки
 */
private val Player.stringUUID: String
    get() = this.uuid.toString()
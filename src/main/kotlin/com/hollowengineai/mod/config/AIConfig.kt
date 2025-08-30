package com.hollowengineai.mod.config

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.config.ModConfigEvent
import org.apache.logging.log4j.LogManager

/**
 * Конфигурация для HollowEngineAI мода
 * 
 * Управляет настройками:
 * - LLM интеграции (Ollama)
 * - Производительности и ограничений
 * - Функций AI системы
 * - Отладки и логирования
 * - Новых систем: репутация, социальные группы, планировщик, оптимизации
 */
@Mod.EventBusSubscriber(modid = "hollowengineai", bus = Mod.EventBusSubscriber.Bus.MOD)
object AIConfig {
    private val LOGGER = LogManager.getLogger(AIConfig::class.java)
    
    // Конфигурационный билдер
    private val BUILDER = ForgeConfigSpec.Builder()
    
    // === OLLAMA SETTINGS ===
    private val OLLAMA_CATEGORY = BUILDER.comment("Ollama LLM integration settings")
        .push("ollama")
    
    private val OLLAMA_URL_CONFIG = BUILDER
        .comment("Ollama API URL (default: http://localhost:11434)")
        .define("url", "http://localhost:11434")
    
    private val DEFAULT_MODEL_CONFIG = BUILDER
        .comment("Default LLM model for decisions (llama2, mistral, codellama, etc.)")
        .define("defaultModel", "llama2")
    
    private val CONVERSATION_MODEL_CONFIG = BUILDER
        .comment("Specialized model for conversations (neural-chat recommended)")
        .define("conversationModel", "neural-chat")
    
    private val REQUEST_TIMEOUT_CONFIG = BUILDER
        .comment("Timeout for LLM requests in milliseconds")
        .defineInRange("requestTimeoutMs", 5000, 1000, 30000)
    
    private val MAX_RETRIES_CONFIG = BUILDER
        .comment("Maximum retries for failed LLM requests")
        .defineInRange("maxRetries", 2, 0, 5)
    
    private val USE_STREAMING_CONFIG = BUILDER
        .comment("Use streaming responses for faster interactions")
        .define("useStreaming", false)
    
    init {
        BUILDER.pop() // ollama
    }
    
    // === PERFORMANCE SETTINGS ===
    private val PERFORMANCE_CATEGORY = BUILDER.comment("Performance and resource management settings")
        .push("performance")
    
    private val MAX_AI_NPCS_CONFIG = BUILDER
        .comment("Maximum number of AI NPCs that can be active simultaneously")
        .defineInRange("maxAINPCs", 50, 1, 200)
    
    private val AI_UPDATE_INTERVAL_CONFIG = BUILDER
        .comment("AI update interval in server ticks (20 ticks = 1 second)")
        .defineInRange("aiUpdateInterval", 100, 20, 1200)
    
    private val DECISION_COOLDOWN_CONFIG = BUILDER
        .comment("Minimum time between AI decisions in milliseconds")
        .defineInRange("decisionCooldownMs", 1000, 100, 10000)
    
    private val MEMORY_CLEANUP_INTERVAL_CONFIG = BUILDER
        .comment("Memory cleanup interval in minutes")
        .defineInRange("memoryCleanupIntervalMin", 30, 5, 120)
    
    private val MAX_MEMORY_EPISODES_CONFIG = BUILDER
        .comment("Maximum memory episodes per NPC")
        .defineInRange("maxMemoryEpisodes", 1000, 100, 10000)
    
    private val LOD_ENABLED_CONFIG = BUILDER
        .comment("Enable Level of Detail optimization")
        .define("lodEnabled", true)
    
    private val LOD_DISTANCE_HIGH_CONFIG = BUILDER
        .comment("Distance for HIGH LOD (full AI processing)")
        .defineInRange("lodDistanceHigh", 32, 8, 128)
    
    private val LOD_DISTANCE_MEDIUM_CONFIG = BUILDER
        .comment("Distance for MEDIUM LOD (reduced processing)")
        .defineInRange("lodDistanceMedium", 64, 16, 256)
    
    private val LOD_DISTANCE_LOW_CONFIG = BUILDER
        .comment("Distance for LOW LOD (minimal processing)")
        .defineInRange("lodDistanceLow", 128, 32, 512)
    
    init {
        BUILDER.pop() // performance
    }
    
    // === FEATURES SETTINGS ===
    private val FEATURES_CATEGORY = BUILDER.comment("AI feature toggles")
        .push("features")
    
    private val ENABLE_TRADING_CONFIG = BUILDER
        .comment("Enable experimental trading system")
        .define("enableTrading", false)
    
    private val ENABLE_ADVANCED_PSYCHOLOGY_CONFIG = BUILDER
        .comment("Enable advanced psychology models (experimental)")
        .define("enableAdvancedPsychology", false)
    
    private val ENABLE_MEMORY_SUMMARIZATION_CONFIG = BUILDER
        .comment("Enable automatic memory summarization")
        .define("enableMemorySummarization", true)
    
    private val ENABLE_EMOTION_SYSTEM_CONFIG = BUILDER
        .comment("Enable emotion-based behavior")
        .define("enableEmotionSystem", true)
    
    private val ENABLE_RELATIONSHIP_SYSTEM_CONFIG = BUILDER
        .comment("Enable relationship tracking with players")
        .define("enableRelationshipSystem", true)
    
    private val ENABLE_WORLD_INTERACTION_CONFIG = BUILDER
        .comment("Allow NPCs to interact with world blocks")
        .define("enableWorldInteraction", true)
    
    private val ENABLE_NPC_TO_NPC_INTERACTION_CONFIG = BUILDER
        .comment("Enable NPC to NPC interactions")
        .define("enableNPCToNPCInteraction", false)
    
    init {
        BUILDER.pop() // features
    }
    
    // === DEBUG SETTINGS ===
    private val DEBUG_CATEGORY = BUILDER.comment("Debug and logging settings")
        .push("debug")
    
    private val DEBUG_MODE_CONFIG = BUILDER
        .comment("Enable debug mode with verbose logging")
        .define("debugMode", false)
    
    private val LOG_DECISIONS_CONFIG = BUILDER
        .comment("Log all AI decisions to console")
        .define("logDecisions", false)
    
    private val LOG_ACTIONS_CONFIG = BUILDER
        .comment("Log all NPC actions to console")
        .define("logActions", false)
    
    private val LOG_MEMORY_CONFIG = BUILDER
        .comment("Log memory operations")
        .define("logMemory", false)
    
    private val LOG_PERFORMANCE_CONFIG = BUILDER
        .comment("Log performance metrics")
        .define("logPerformance", true)
    
    private val SHOW_NPC_THOUGHTS_CONFIG = BUILDER
        .comment("Show NPC thoughts as chat messages (debug only)")
        .define("showNPCThoughts", false)
    
    init {
        BUILDER.pop() // debug
    }
    
    // === INTEGRATION SETTINGS ===
    private val INTEGRATION_CATEGORY = BUILDER.comment("Integration with other mods")
        .push("integration")
    
    private val HOLLOWENGINE_INTEGRATION_CONFIG = BUILDER
        .comment("Enable HollowEngine Legacy integration")
        .define("enableHollowEngineIntegration", true)
    
    private val CUSTOM_NPC_MODELS_CONFIG = BUILDER
        .comment("Support custom NPC models from other mods")
        .define("enableCustomNPCModels", true)
    
    private val VOICE_INTEGRATION_CONFIG = BUILDER
        .comment("Enable voice synthesis integration (experimental)")
        .define("enableVoiceIntegration", false)
    
    init {
        BUILDER.pop() // integration
    }
    
    // === REPUTATION SYSTEM SETTINGS ===
    private val REPUTATION_CATEGORY = BUILDER.comment("Reputation system settings")
        .push("reputation")
    
    private val REPUTATION_ENABLED_CONFIG = BUILDER
        .comment("Enable reputation system")
        .define("enabled", true)
    
    private val REPUTATION_DECAY_RATE_CONFIG = BUILDER
        .comment("Daily reputation decay rate (0.0 - 1.0)")
        .defineInRange("decayRate", 0.05, 0.0, 1.0)
    
    private val REPUTATION_MAX_VALUE_CONFIG = BUILDER
        .comment("Maximum reputation value")
        .defineInRange("maxValue", 1000, 100, 10000)
    
    private val REPUTATION_MIN_VALUE_CONFIG = BUILDER
        .comment("Minimum reputation value")
        .defineInRange("minValue", -1000, -10000, -100)
    
    private val REPUTATION_SAVE_INTERVAL_CONFIG = BUILDER
        .comment("Reputation data save interval in minutes")
        .defineInRange("saveIntervalMin", 5, 1, 60)
    
    init {
        BUILDER.pop() // reputation
    }
    
    // === SOCIAL GROUPS SETTINGS ===
    private val SOCIAL_GROUPS_CATEGORY = BUILDER.comment("Social groups and faction settings")
        .push("socialGroups")
    
    private val SOCIAL_GROUPS_ENABLED_CONFIG = BUILDER
        .comment("Enable social groups system")
        .define("enabled", true)
    
    private val MAX_GROUPS_CONFIG = BUILDER
        .comment("Maximum number of social groups")
        .defineInRange("maxGroups", 50, 1, 200)
    
    private val MAX_GROUP_SIZE_CONFIG = BUILDER
        .comment("Maximum NPCs per group")
        .defineInRange("maxGroupSize", 20, 2, 100)
    
    private val GROUP_DYNAMICS_UPDATE_INTERVAL_CONFIG = BUILDER
        .comment("Group dynamics update interval in minutes")
        .defineInRange("groupDynamicsUpdateMin", 10, 1, 120)
    
    private val LOYALTY_CHANGE_RATE_CONFIG = BUILDER
        .comment("Group loyalty change rate per update")
        .defineInRange("loyaltyChangeRate", 0.1, 0.01, 1.0)
    
    init {
        BUILDER.pop() // socialGroups
    }
    
    // === SCHEDULER SETTINGS ===
    private val SCHEDULER_CATEGORY = BUILDER.comment("NPC scheduler and task management settings")
        .push("scheduler")
    
    private val SCHEDULER_ENABLED_CONFIG = BUILDER
        .comment("Enable NPC scheduler")
        .define("enabled", true)
    
    private val MAX_TASKS_PER_NPC_CONFIG = BUILDER
        .comment("Maximum scheduled tasks per NPC")
        .defineInRange("maxTasksPerNPC", 10, 1, 50)
    
    private val SCHEDULER_UPDATE_INTERVAL_CONFIG = BUILDER
        .comment("Scheduler update interval in seconds")
        .defineInRange("updateIntervalSec", 30, 5, 300)
    
    private val TASK_CLEANUP_INTERVAL_CONFIG = BUILDER
        .comment("Completed task cleanup interval in minutes")
        .defineInRange("taskCleanupIntervalMin", 60, 10, 720)
    
    private val DEFAULT_SCHEDULES_ENABLED_CONFIG = BUILDER
        .comment("Enable default NPC schedules (guard, merchant, farmer)")
        .define("defaultSchedulesEnabled", true)
    
    init {
        BUILDER.pop() // scheduler
    }
    
    // === ADVANCED PERFORMANCE SETTINGS ===
    private val ADVANCED_PERFORMANCE_CATEGORY = BUILDER.comment("Advanced performance optimization settings")
        .push("advancedPerformance")
    
    private val COROUTINE_POOLS_ENABLED_CONFIG = BUILDER
        .comment("Enable optimized coroutine pools")
        .define("coroutinePoolsEnabled", true)
    
    private val AI_THREAD_COUNT_CONFIG = BUILDER
        .comment("Number of threads for AI processing (0 = auto)")
        .defineInRange("aiThreadCount", 0, 0, 16)
    
    private val ACTION_THREAD_COUNT_CONFIG = BUILDER
        .comment("Number of threads for action execution (0 = auto)")
        .defineInRange("actionThreadCount", 0, 0, 16)
    
    private val CACHING_ENABLED_CONFIG = BUILDER
        .comment("Enable advanced caching system")
        .define("cachingEnabled", true)
    
    private val CACHE_SIZE_CONFIG = BUILDER
        .comment("Cache size for performance optimization")
        .defineInRange("cacheSize", 1000, 100, 10000)
    
    private val CACHE_TTL_CONFIG = BUILDER
        .comment("Cache time-to-live in minutes")
        .defineInRange("cacheTTLMin", 30, 1, 240)
    
    private val BATCH_PROCESSING_ENABLED_CONFIG = BUILDER
        .comment("Enable batch processing for multiple NPCs")
        .define("batchProcessingEnabled", true)
    
    init {
        BUILDER.pop() // advancedPerformance
    }
    
    // === EVENT SYSTEM SETTINGS ===
    private val EVENT_SYSTEM_CATEGORY = BUILDER.comment("Event system and communication settings")
        .push("eventSystem")
    
    private val EVENT_BUS_ENABLED_CONFIG = BUILDER
        .comment("Enable NPC event bus system")
        .define("eventBusEnabled", true)
    
    private val EVENT_QUEUE_SIZE_CONFIG = BUILDER
        .comment("Maximum events in queue")
        .defineInRange("eventQueueSize", 1000, 100, 10000)
    
    private val EVENT_PROCESSING_BATCH_SIZE_CONFIG = BUILDER
        .comment("Number of events to process per batch")
        .defineInRange("eventProcessingBatchSize", 50, 10, 500)
    
    private val EVENT_TTL_CONFIG = BUILDER
        .comment("Event time-to-live in seconds")
        .defineInRange("eventTTLSec", 300, 10, 3600)
    
    init {
        BUILDER.pop() // eventSystem
    }
    
    // Финальная спецификация
    val SPEC: ForgeConfigSpec = BUILDER.build()
    
    // === ACCESSOR PROPERTIES ===
    
    // Ollama settings
    var ollamaUrl: String = "http://localhost:11434"
        private set
    var defaultModel: String = "llama2"
        private set
    var conversationModel: String = "neural-chat"
        private set
    var requestTimeoutMs: Int = 5000
        private set
    var maxRetries: Int = 2
        private set
    var useStreaming: Boolean = false
        private set
    
    // Performance settings
    var maxAINPCs: Int = 50
        private set
    var aiUpdateInterval: Int = 100
        private set
    var decisionCooldownMs: Int = 1000
        private set
    var memoryCleanupIntervalMin: Int = 30
        private set
    var maxMemoryEpisodes: Int = 1000
        private set
    var lodEnabled: Boolean = true
        private set
    var lodDistanceHigh: Int = 32
        private set
    var lodDistanceMedium: Int = 64
        private set
    var lodDistanceLow: Int = 128
        private set
    
    // Features
    var enableTrading: Boolean = false
        private set
    var enableAdvancedPsychology: Boolean = false
        private set
    var enableMemorySummarization: Boolean = true
        private set
    var enableEmotionSystem: Boolean = true
        private set
    var enableRelationshipSystem: Boolean = true
        private set
    var enableWorldInteraction: Boolean = true
        private set
    var enableNPCToNPCInteraction: Boolean = false
        private set
    
    // Debug
    var debugMode: Boolean = false
        private set
    var logDecisions: Boolean = false
        private set
    var logActions: Boolean = false
        private set
    var logMemory: Boolean = false
        private set
    var logPerformance: Boolean = true
        private set
    var showNPCThoughts: Boolean = false
        private set
    
    // Integration
    var enableHollowEngineIntegration: Boolean = true
        private set
    var enableCustomNPCModels: Boolean = true
        private set
    var enableVoiceIntegration: Boolean = false
        private set
    
    // Reputation System
    var reputationEnabled: Boolean = true
        private set
    var reputationDecayRate: Double = 0.05
        private set
    var reputationMaxValue: Int = 1000
        private set
    var reputationMinValue: Int = -1000
        private set
    var reputationSaveIntervalMin: Int = 5
        private set
    
    // Social Groups
    var socialGroupsEnabled: Boolean = true
        private set
    var maxGroups: Int = 50
        private set
    var maxGroupSize: Int = 20
        private set
    var groupDynamicsUpdateMin: Int = 10
        private set
    var loyaltyChangeRate: Double = 0.1
        private set
    
    // Scheduler
    var schedulerEnabled: Boolean = true
        private set
    var maxTasksPerNPC: Int = 10
        private set
    var schedulerUpdateIntervalSec: Int = 30
        private set
    var taskCleanupIntervalMin: Int = 60
        private set
    var defaultSchedulesEnabled: Boolean = true
        private set
    
    // Advanced Performance
    var coroutinePoolsEnabled: Boolean = true
        private set
    var aiThreadCount: Int = 0
        private set
    var actionThreadCount: Int = 0
        private set
    var cachingEnabled: Boolean = true
        private set
    var cacheSize: Int = 1000
        private set
    var cacheTTLMin: Int = 30
        private set
    var batchProcessingEnabled: Boolean = true
        private set
    
    // Event System
    var eventBusEnabled: Boolean = true
        private set
    var eventQueueSize: Int = 1000
        private set
    var eventProcessingBatchSize: Int = 50
        private set
    var eventTTLSec: Int = 300
        private set
    
    /**
     * Обновить значения из конфигурации
     */
    private fun updateValues() {
        // Ollama
        ollamaUrl = OLLAMA_URL_CONFIG.get()
        defaultModel = DEFAULT_MODEL_CONFIG.get()
        conversationModel = CONVERSATION_MODEL_CONFIG.get()
        requestTimeoutMs = REQUEST_TIMEOUT_CONFIG.get()
        maxRetries = MAX_RETRIES_CONFIG.get()
        useStreaming = USE_STREAMING_CONFIG.get()
        
        // Performance
        maxAINPCs = MAX_AI_NPCS_CONFIG.get()
        aiUpdateInterval = AI_UPDATE_INTERVAL_CONFIG.get()
        decisionCooldownMs = DECISION_COOLDOWN_CONFIG.get()
        memoryCleanupIntervalMin = MEMORY_CLEANUP_INTERVAL_CONFIG.get()
        maxMemoryEpisodes = MAX_MEMORY_EPISODES_CONFIG.get()
        lodEnabled = LOD_ENABLED_CONFIG.get()
        lodDistanceHigh = LOD_DISTANCE_HIGH_CONFIG.get()
        lodDistanceMedium = LOD_DISTANCE_MEDIUM_CONFIG.get()
        lodDistanceLow = LOD_DISTANCE_LOW_CONFIG.get()
        
        // Features
        enableTrading = ENABLE_TRADING_CONFIG.get()
        enableAdvancedPsychology = ENABLE_ADVANCED_PSYCHOLOGY_CONFIG.get()
        enableMemorySummarization = ENABLE_MEMORY_SUMMARIZATION_CONFIG.get()
        enableEmotionSystem = ENABLE_EMOTION_SYSTEM_CONFIG.get()
        enableRelationshipSystem = ENABLE_RELATIONSHIP_SYSTEM_CONFIG.get()
        enableWorldInteraction = ENABLE_WORLD_INTERACTION_CONFIG.get()
        enableNPCToNPCInteraction = ENABLE_NPC_TO_NPC_INTERACTION_CONFIG.get()
        
        // Debug
        debugMode = DEBUG_MODE_CONFIG.get()
        logDecisions = LOG_DECISIONS_CONFIG.get()
        logActions = LOG_ACTIONS_CONFIG.get()
        logMemory = LOG_MEMORY_CONFIG.get()
        logPerformance = LOG_PERFORMANCE_CONFIG.get()
        showNPCThoughts = SHOW_NPC_THOUGHTS_CONFIG.get()
        
        // Integration
        enableHollowEngineIntegration = HOLLOWENGINE_INTEGRATION_CONFIG.get()
        enableCustomNPCModels = CUSTOM_NPC_MODELS_CONFIG.get()
        enableVoiceIntegration = VOICE_INTEGRATION_CONFIG.get()
        
        // Reputation System
        reputationEnabled = REPUTATION_ENABLED_CONFIG.get()
        reputationDecayRate = REPUTATION_DECAY_RATE_CONFIG.get()
        reputationMaxValue = REPUTATION_MAX_VALUE_CONFIG.get()
        reputationMinValue = REPUTATION_MIN_VALUE_CONFIG.get()
        reputationSaveIntervalMin = REPUTATION_SAVE_INTERVAL_CONFIG.get()
        
        // Social Groups
        socialGroupsEnabled = SOCIAL_GROUPS_ENABLED_CONFIG.get()
        maxGroups = MAX_GROUPS_CONFIG.get()
        maxGroupSize = MAX_GROUP_SIZE_CONFIG.get()
        groupDynamicsUpdateMin = GROUP_DYNAMICS_UPDATE_INTERVAL_CONFIG.get()
        loyaltyChangeRate = LOYALTY_CHANGE_RATE_CONFIG.get()
        
        // Scheduler
        schedulerEnabled = SCHEDULER_ENABLED_CONFIG.get()
        maxTasksPerNPC = MAX_TASKS_PER_NPC_CONFIG.get()
        schedulerUpdateIntervalSec = SCHEDULER_UPDATE_INTERVAL_CONFIG.get()
        taskCleanupIntervalMin = TASK_CLEANUP_INTERVAL_CONFIG.get()
        defaultSchedulesEnabled = DEFAULT_SCHEDULES_ENABLED_CONFIG.get()
        
        // Advanced Performance
        coroutinePoolsEnabled = COROUTINE_POOLS_ENABLED_CONFIG.get()
        aiThreadCount = AI_THREAD_COUNT_CONFIG.get()
        actionThreadCount = ACTION_THREAD_COUNT_CONFIG.get()
        cachingEnabled = CACHING_ENABLED_CONFIG.get()
        cacheSize = CACHE_SIZE_CONFIG.get()
        cacheTTLMin = CACHE_TTL_CONFIG.get()
        batchProcessingEnabled = BATCH_PROCESSING_ENABLED_CONFIG.get()
        
        // Event System
        eventBusEnabled = EVENT_BUS_ENABLED_CONFIG.get()
        eventQueueSize = EVENT_QUEUE_SIZE_CONFIG.get()
        eventProcessingBatchSize = EVENT_PROCESSING_BATCH_SIZE_CONFIG.get()
        eventTTLSec = EVENT_TTL_CONFIG.get()
        
        LOGGER.info("Конфигурация обновлена с новыми системами")
        
        if (debugMode) {
            LOGGER.info("Debug mode enabled - verbose logging active")
        }
    }
    
    /**
     * Валидация новых систем
     */
    private fun validateNewSystemsConfig() {
        // Проверки системы репутации
        if (reputationMaxValue <= reputationMinValue) {
            LOGGER.error("Некорректные значения репутации: max ($reputationMaxValue) <= min ($reputationMinValue)")
        }
        
        if (reputationDecayRate < 0.0 || reputationDecayRate > 1.0) {
            LOGGER.error("Некорректная скорость затухания репутации: $reputationDecayRate (0.0-1.0)")
        }
        
        // Проверки социальных групп
        if (maxGroups <= 0 || maxGroupSize <= 1) {
            LOGGER.error("Некорректные настройки групп: maxGroups=$maxGroups, maxGroupSize=$maxGroupSize")
        }
        
        if (loyaltyChangeRate <= 0.0) {
            LOGGER.warn("Отключено изменение лояльности в группах (loyaltyChangeRate=0.0)")
        }
        
        // Проверки планировщика
        if (maxTasksPerNPC <= 0) {
            LOGGER.error("Некорректное максимальное количество задач: $maxTasksPerNPC")
        }
        
        if (schedulerUpdateIntervalSec < 1) {
            LOGGER.warn("Очень частое обновление планировщика: $schedulerUpdateIntervalSec сек")
        }
        
        // Проверки производительности
        if (aiThreadCount < 0 || actionThreadCount < 0) {
            LOGGER.error("Отрицательное количество потоков: AI=$aiThreadCount, Action=$actionThreadCount")
        }
        
        if (cacheSize <= 0) {
            LOGGER.warn("Кэширование отключено (cacheSize=$cacheSize)")
        }
        
        // Проверки системы событий
        if (eventQueueSize <= 0) {
            LOGGER.error("Некорректный размер очереди событий: $eventQueueSize")
        }
        
        if (eventProcessingBatchSize <= 0 || eventProcessingBatchSize > eventQueueSize) {
            LOGGER.error("Некорректный размер пакета событий: $eventProcessingBatchSize")
        }
    }
    
    /**
     * Обработчик события изменения конфигурации
     */
    @SubscribeEvent
    fun onLoad(event: ModConfigEvent) {
        LOGGER.info("Загрузка конфигурации HollowEngineAI...")
        updateValues()
        validateConfiguration()
        LOGGER.info("Конфигурация загружена успешно")
    }
    
    /**
     * Валидация конфигурации
     */
    private fun validateConfiguration() {
        // Проверяем URL Ollama
        // Новые проверки валидности
        validateNewSystemsConfig()
        if (!ollamaUrl.startsWith("http://") && !ollamaUrl.startsWith("https://")) {
            LOGGER.warn("Invalid Ollama URL format: $ollamaUrl")
        }
        
        // Проверяем LOD дистанции
        if (lodDistanceHigh >= lodDistanceMedium || lodDistanceMedium >= lodDistanceLow) {
            LOGGER.warn("Invalid LOD distance configuration - distances should increase: HIGH < MEDIUM < LOW")
        }
        
        // Предупреждения о производительности
        if (maxAINPCs > 100) {
            LOGGER.warn("High maxAINPCs setting ($maxAINPCs) may impact performance")
        }
        
        if (aiUpdateInterval < 20) {
            LOGGER.warn("Very low aiUpdateInterval ($aiUpdateInterval) may impact server performance")
        }
        
        // Информация о экспериментальных функциях
        if (enableTrading) {
            LOGGER.info("Experimental trading system enabled")
        }
        
        if (enableAdvancedPsychology) {
            LOGGER.info("Advanced psychology models enabled (experimental)")
        }
        
        if (enableVoiceIntegration) {
            LOGGER.info("Voice integration enabled (experimental)")
        }
        
        // Новые системы
        if (!reputationEnabled) {
            LOGGER.info("Система репутации отключена")
        }
        
        if (!socialGroupsEnabled) {
            LOGGER.info("Социальные группы отключены")
        }
        
        if (!schedulerEnabled) {
            LOGGER.info("Планировщик NPC отключен")
        }
        
        if (!coroutinePoolsEnabled) {
            LOGGER.warn("Оптимизации производительности отключены - может снизить производительность")
        }
        
        if (!eventBusEnabled) {
            LOGGER.warn("EventBus отключен - NPC не смогут взаимодействовать друг с другом")
        }
        
        // Предупреждения о производительности для новых систем
        if (maxGroups > 100) {
            LOGGER.warn("Большое количество групп ($maxGroups) может снизить производительность")
        }
        
        if (eventQueueSize > 5000) {
            LOGGER.warn("Большой размер очереди событий ($eventQueueSize) может потребовать много памяти")
        }
    }
    
    /**
     * Получить краткую сводку конфигурации
     */
    fun getConfigSummary(): String {
        return buildString {
            appendLine("Конфигурация HollowEngineAI:")
            appendLine("- Max AI NPCs: $maxAINPCs")
            appendLine("- Ollama URL: $ollamaUrl")
            appendLine("- Default Model: $defaultModel")
            appendLine("- LOD Enabled: $lodEnabled")
            appendLine("- Trading Enabled: $enableTrading")
            appendLine("- Debug Mode: $debugMode")
            appendLine()
            appendLine("Новые системы:")
            appendLine("- Репутация: $reputationEnabled")
            appendLine("- Социальные группы: $socialGroupsEnabled")
            appendLine("- Планировщик: $schedulerEnabled")
            appendLine("- Оптимизации: $coroutinePoolsEnabled")
            appendLine("- EventBus: $eventBusEnabled")
            appendLine("- Макс групп: $maxGroups")
            appendLine("- Макс задач на NPC: $maxTasksPerNPC")
        }
    }
    
    /**
     * Перезагрузить конфигурацию из файла
     * @return true если конфигурация успешно перезагружена
     */
    fun reloadConfig(): Boolean {
        return try {
            LOGGER.info("Перезагружаем конфигурацию HollowEngineAI...")
            
            // Обновляем значения из файла конфигурации
            updateValues()
            
            // Проверяем корректность новых значений
            validateConfiguration()
            
            LOGGER.info("Конфигурация успешно перезагружена")
            
            if (debugMode) {
                LOGGER.info("Новая конфигурация:\n${getConfigSummary()}")
            }
            
            true
        } catch (e: Exception) {
            LOGGER.error("Ошибка при перезагрузке конфигурации", e)
            false
        }
    }
    
    /**
     * Перезагрузить только определенную категорию конфигурации
     * Полезно для тонкой настройки без полной перезагрузки
     */
    fun reloadConfigCategory(category: ConfigCategory): Boolean {
        return try {
            LOGGER.info("Перезагружаем категорию конфигурации: ${category.name}")
            
            when (category) {
                ConfigCategory.OLLAMA -> {
                    ollamaUrl = OLLAMA_URL_CONFIG.get()
                    defaultModel = DEFAULT_MODEL_CONFIG.get()
                    conversationModel = CONVERSATION_MODEL_CONFIG.get()
                    requestTimeoutMs = REQUEST_TIMEOUT_CONFIG.get()
                    maxRetries = MAX_RETRIES_CONFIG.get()
                    useStreaming = USE_STREAMING_CONFIG.get()
                }
                ConfigCategory.PERFORMANCE -> {
                    maxAINPCs = MAX_AI_NPCS_CONFIG.get()
                    aiUpdateInterval = AI_UPDATE_INTERVAL_CONFIG.get()
                    decisionCooldownMs = DECISION_COOLDOWN_CONFIG.get()
                    memoryCleanupIntervalMin = MEMORY_CLEANUP_INTERVAL_CONFIG.get()
                    maxMemoryEpisodes = MAX_MEMORY_EPISODES_CONFIG.get()
                    lodEnabled = LOD_ENABLED_CONFIG.get()
                    lodDistanceHigh = LOD_DISTANCE_HIGH_CONFIG.get()
                    lodDistanceMedium = LOD_DISTANCE_MEDIUM_CONFIG.get()
                    lodDistanceLow = LOD_DISTANCE_LOW_CONFIG.get()
                }
                ConfigCategory.FEATURES -> {
                    enableTrading = ENABLE_TRADING_CONFIG.get()
                    enableAdvancedPsychology = ENABLE_ADVANCED_PSYCHOLOGY_CONFIG.get()
                    enableMemorySummarization = ENABLE_MEMORY_SUMMARIZATION_CONFIG.get()
                    enableEmotionSystem = ENABLE_EMOTION_SYSTEM_CONFIG.get()
                    enableRelationshipSystem = ENABLE_RELATIONSHIP_SYSTEM_CONFIG.get()
                    enableWorldInteraction = ENABLE_WORLD_INTERACTION_CONFIG.get()
                    enableNPCToNPCInteraction = ENABLE_NPC_TO_NPC_INTERACTION_CONFIG.get()
                }
                ConfigCategory.DEBUG -> {
                    debugMode = DEBUG_MODE_CONFIG.get()
                    logDecisions = LOG_DECISIONS_CONFIG.get()
                    logActions = LOG_ACTIONS_CONFIG.get()
                    logMemory = LOG_MEMORY_CONFIG.get()
                    logPerformance = LOG_PERFORMANCE_CONFIG.get()
                    showNPCThoughts = SHOW_NPC_THOUGHTS_CONFIG.get()
                }
                ConfigCategory.REPUTATION -> {
                    reputationEnabled = REPUTATION_ENABLED_CONFIG.get()
                    reputationDecayRate = REPUTATION_DECAY_RATE_CONFIG.get()
                    reputationMaxValue = REPUTATION_MAX_VALUE_CONFIG.get()
                    reputationMinValue = REPUTATION_MIN_VALUE_CONFIG.get()
                    reputationSaveIntervalMin = REPUTATION_SAVE_INTERVAL_CONFIG.get()
                }
                ConfigCategory.SOCIAL_GROUPS -> {
                    socialGroupsEnabled = SOCIAL_GROUPS_ENABLED_CONFIG.get()
                    maxGroups = MAX_GROUPS_CONFIG.get()
                    maxGroupSize = MAX_GROUP_SIZE_CONFIG.get()
                    groupDynamicsUpdateMin = GROUP_DYNAMICS_UPDATE_INTERVAL_CONFIG.get()
                    loyaltyChangeRate = LOYALTY_CHANGE_RATE_CONFIG.get()
                }
                ConfigCategory.SCHEDULER -> {
                    schedulerEnabled = SCHEDULER_ENABLED_CONFIG.get()
                    maxTasksPerNPC = MAX_TASKS_PER_NPC_CONFIG.get()
                    schedulerUpdateIntervalSec = SCHEDULER_UPDATE_INTERVAL_CONFIG.get()
                    taskCleanupIntervalMin = TASK_CLEANUP_INTERVAL_CONFIG.get()
                    defaultSchedulesEnabled = DEFAULT_SCHEDULES_ENABLED_CONFIG.get()
                }
            }
            
            // Проверяем только обновленную категорию
            when (category) {
                ConfigCategory.REPUTATION -> validateNewSystemsConfig()
                ConfigCategory.SOCIAL_GROUPS -> validateNewSystemsConfig()
                ConfigCategory.SCHEDULER -> validateNewSystemsConfig()
                else -> { /* остальные категории можно не проверять */ }
            }
            
            LOGGER.info("Категория конфигурации ${category.name} успешно перезагружена")
            true
        } catch (e: Exception) {
            LOGGER.error("Ошибка при перезагрузке категории конфигурации ${category.name}", e)
            false
        }
    }
}

/**
 * Категории конфигурации для селективной перезагрузки
 */
enum class ConfigCategory {
    OLLAMA,
    PERFORMANCE,
    FEATURES,
    DEBUG,
    REPUTATION,
    SOCIAL_GROUPS,
    SCHEDULER
}
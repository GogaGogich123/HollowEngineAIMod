package com.hollowengineai.mod.core

import com.hollowengineai.mod.actions.Action
import com.hollowengineai.mod.actions.ActionExecutor
import com.hollowengineai.mod.actions.ModularAction
import com.hollowengineai.mod.actions.OptimizedActionExecutor
import com.hollowengineai.mod.memory.NPCMemory
import com.hollowengineai.mod.states.NPCStateMachine
import com.hollowengineai.mod.states.NPCState
import com.hollowengineai.mod.reputation.ReputationSystem
import com.hollowengineai.mod.social.SocialGroupManager
import com.hollowengineai.mod.scheduler.NPCScheduler
import com.hollowengineai.mod.scheduler.NPCTask
import com.hollowengineai.mod.performance.CoroutinePoolManager
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEvent
// Новые системы ИИ
import com.hollowengineai.mod.perception.PerceptionSystem
import com.hollowengineai.mod.perception.GazeDetector
import com.hollowengineai.mod.async.AsyncActionSystem
import com.hollowengineai.mod.planning.NPCPlanSystem
import com.hollowengineai.mod.interruption.InterruptionSystem
import com.hollowengineai.mod.attention.AttentionManager
import com.hollowengineai.mod.social.SocialAwarenessSystem
import com.hollowengineai.mod.social.InteractionType
import com.hollowengineai.mod.social.InteractionImportance
import com.hollowengineai.mod.async.ActionPriority
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Умный НПС с AI возможностями
 * 
 * Основные функции:
 * - Интеграция с LLM для принятия решений
 * - Система памяти и обучения
 * - Выполнение сложных действий
 * - Эмоциональные реакции
 * - Взаимодействие с игроками и миром
 */
class SmartNPC(
    val id: UUID,
    val name: String,
    val personalityType: PersonalityType,
    val entity: LivingEntity,
    private val memory: NPCMemory,
    private val actionExecutor: ActionExecutor,
    private val decisionEngine: DecisionEngine,
    private val eventBus: NPCEventBusImpl = NPCEventBus.instance
) {
    companion object {
        private val LOGGER = LogManager.getLogger(SmartNPC::class.java)
        private const val MAX_CONTEXT_LENGTH = 2000
        private const val DECISION_COOLDOWN_MS = 1000L
    }
    
    // Существующие системы
    private lateinit var stateMachine: NPCStateMachine
    private lateinit var optimizedActionExecutor: OptimizedActionExecutor
    private var groupMembership: UUID? = null
    
    // Новые ИИ системы
    private lateinit var perceptionSystem: PerceptionSystem
    private lateinit var gazeDetector: GazeDetector
    private lateinit var asyncActionSystem: AsyncActionSystem
    private lateinit var planSystem: NPCPlanSystem
    private lateinit var interruptionSystem: InterruptionSystem
    private lateinit var attentionManager: AttentionManager
    private lateinit var socialAwarenessSystem: SocialAwarenessSystem
    
    // Флаг активности новых систем
    private var advancedSystemsActive = false
    
    // Текущее состояние НПС (теперь управляется через StateMachine)
    val currentState: NPCState
        get() = if (::stateMachine.isInitialized) stateMachine.getCurrentState() else NPCState.IDLE
    
    @Volatile
    var currentEmotion = EmotionalState.NEUTRAL
        private set
    
    // Публичный геттер для эмоционального состояния (для совместимости с ActionExecutor файлами)
    
    // Карта черт личности для совместимости с ActionExecutor файлами
    val personalityTraits: Map<String, Float> by lazy {
        val traits = mutableMapOf<String, Float>()
        
        // Базовые черты из PersonalityTraits
        val baseTraits = personalityType.traits
        traits["friendliness"] = baseTraits.friendliness
        traits["curiosity"] = baseTraits.curiosity
        traits["aggressiveness"] = baseTraits.aggressiveness
        traits["intelligence"] = baseTraits.intelligence
        traits["creativity"] = baseTraits.creativity
        traits["patience"] = baseTraits.patience
        traits["boldness"] = baseTraits.boldness
        traits["empathy"] = baseTraits.empathy
        
        // Дополнительные черты в зависимости от типа личности
        when (personalityType) {
            PersonalityType.FRIENDLY_TRADER -> {
                traits["charisma"] = 0.8f
                traits["merchant"] = 0.9f
                traits["business_minded"] = 0.85f
                traits["confidence"] = 0.7f
                traits["merchant_type"] = 0.8f
            }
            PersonalityType.CAUTIOUS_GUARD -> {
                traits["confidence"] = 0.8f
                traits["activity_level"] = 0.9f
                traits["night_owl"] = 0.7f
            }
            PersonalityType.CURIOUS_SCHOLAR -> {
                traits["charisma"] = 0.6f
                traits["confidence"] = 0.7f
                traits["activity_level"] = 0.6f
            }
            PersonalityType.ADVENTUROUS_EXPLORER -> {
                traits["confidence"] = 0.9f
                traits["activity_level"] = 0.95f
                traits["night_owl"] = 0.6f
            }
            PersonalityType.CREATIVE_ARTISAN -> {
                traits["charisma"] = 0.7f
                traits["confidence"] = 0.6f
                traits["activity_level"] = 0.7f
            }
            PersonalityType.SOLITARY_HERMIT -> {
                traits["charisma"] = 0.3f
                traits["confidence"] = 0.4f
                traits["activity_level"] = 0.3f
                traits["night_owl"] = 0.8f
            }
            PersonalityType.CHEERFUL_ENTERTAINER -> {
                traits["charisma"] = 0.95f
                traits["confidence"] = 0.9f
                traits["activity_level"] = 0.85f
            }
            PersonalityType.NOBLE_ARISTOCRAT -> {
                traits["charisma"] = 0.8f
                traits["confidence"] = 0.9f
                traits["business_minded"] = 0.7f
                traits["activity_level"] = 0.6f
            }
            PersonalityType.HARDWORKING_FARMER -> {
                traits["confidence"] = 0.6f
                traits["activity_level"] = 0.9f
                traits["night_owl"] = 0.2f
            }
            PersonalityType.MYSTERIOUS_MYSTIC -> {
                traits["charisma"] = 0.5f
                traits["confidence"] = 0.7f
                traits["activity_level"] = 0.5f
                traits["night_owl"] = 0.9f
            }
        }
        
        traits.toMap()
    }
    val emotionalState: EmotionalState
        get() = currentEmotion
    
    @Volatile
    var currentGoal: String? = null
        private set
    
    // Очередь действий для выполнения
    private val actionQueue = ArrayDeque<Action>()
    private val actionQueueLock = Any()
    
    // Используем оптимизированные пулы корутин
    private var decisionJob: Job? = null
    private var lastDecisionTime = 0L
    
    // Частота мышления для LOD оптимизации
    private var thinkInterval = 100L // мс между циклами мышления
    
    // Кэш для часто используемых данных
    private val contextCache = ConcurrentHashMap<String, Any>()
    
    /**
     * Получить текущую позицию НПС
     */
    val position: BlockPos
        get() = entity.blockPosition()
    
    /**
     * Получить мир в котором находится НПС
     */
    val level: Level
        get() = entity.level
    
    /**
     * Проверить активен ли НПС (загружен в мире)
     */
    val isActive: Boolean
        get() = !entity.isRemoved && entity.isAlive
    
    /**
     * Получить доступ к сущности НПС
     */
    fun getEntity(): LivingEntity = entity
    
    /**
     * Получить доступ к системе памяти НПС
     */
    fun getMemory(): NPCMemory = memory
    
    /**
     * Получить доступ к машине состояний НПС
     */
    fun getStateMachine(): NPCStateMachine? = if (::stateMachine.isInitialized) stateMachine else null
    
    /**
     * Запустить AI системы НПС
     */
    fun start() {
        LOGGER.debug("Starting AI systems for NPC: $name ($id)")
        
        if (decisionJob?.isActive == true) {
            LOGGER.warn("AI systems already running for $name")
            return
        }
        
        // Инициализируем все системы
        initializeNewSystems()
        initializeAdvancedSystems()
        
        // Запускаем основной цикл принятия решений с оптимизированным пулом
        decisionJob = CoroutineScope(CoroutinePoolManager.aiContext).launch {
            while (isActive && currentCoroutineContext().isActive) {
                try {
                    processAITick()
                    delay(thinkInterval) // Адаптивная задержка
                } catch (e: Exception) {
                    LOGGER.error("Error in AI processing for $name", e)
                    delay(1000L) // Увеличиваем задержку при ошибках
                }
            }
        }
        
        // Запускаем продвинутые ИИ системы
        startAdvancedSystems()
        
        // Переходим в активное состояние через StateMachine
        if (::stateMachine.isInitialized) {
            CoroutineScope(CoroutinePoolManager.actionContext).launch {
                stateMachine.transitionTo(NPCState.ACTIVE)
            }
        }
        
        LOGGER.info("AI systems started for NPC: $name")
    }
    
    /**
     * Остановить AI системы НПС
     */
    fun stop() {
        LOGGER.debug("Stopping AI systems for NPC: $name ($id)")
        
        // Останавливаем продвинутые ИИ системы
        stopAdvancedSystems()
        
        // Переходим в неактивное состояние через StateMachine
        if (::stateMachine.isInitialized) {
            CoroutineScope(CoroutinePoolManager.actionContext).launch {
                stateMachine.transitionTo(NPCState.INACTIVE)
            }
        }
        
        // Останавливаем корутины
        decisionJob?.cancel()
        
        // Очищаем очередь действий
        synchronized(actionQueueLock) {
            actionQueue.clear()
        }
        
        // Отправляем событие остановки
        eventBus.publishEvent(NPCEvent.Lifecycle(entity.uuid, "stopped"))
        
        // Сохраняем состояние в память
        saveCurrentState()
        
        LOGGER.info("AI systems stopped for NPC: $name")
    }
    
    /**
     * Основной цикл AI обработки
     */
    private suspend fun processAITick() {
        // Обновляем продвинутые системы ИИ
        if (advancedSystemsActive) {
            processAdvancedSystems()
        }
        
        // Выполняем действия из очереди
        executeQueuedActions()
        
        // Обновляем контекстную информацию
        updateContext()
        
        // Принимаем новые решения (с ограничением частоты)
        val now = System.currentTimeMillis()
        if (now - lastDecisionTime >= DECISION_COOLDOWN_MS) {
            makeDecision()
            lastDecisionTime = now
        }
        
        // Обрабатываем эмоциональные изменения
        processEmotions()
        
        // Обновляем память
        updateMemory()
    }
    
    /**
     * Выполнить действия из очереди
     */
    private suspend fun executeQueuedActions() {
        val action = synchronized(actionQueueLock) {
            actionQueue.pollFirst()
        } ?: return
        
        try {
            LOGGER.debug("Executing action: ${action.type} for NPC: $name")
            
            // Используем модульную архитектуру если доступна
            if (::optimizedActionExecutor.isInitialized) {
                val result = optimizedActionExecutor.executeModular(action.type.name, this, null, action.parameters)
                
                // Обрабатываем результат действия
                if (result.success) {
                    // Автоматически изменяем репутацию на основе действия
                    updateReputationFromAction(action, result)
                    
                    // Отправляем событие о выполненном действии
                    eventBus.publishEvent(NPCEvent.ActionCompleted(entity.uuid, action.type, result.message))
                } else {
                    LOGGER.warn("Action ${action.type} failed for $name: ${result.message}")
                }
            } else {
                // Fallback на старую систему
                actionExecutor.execute(action, this)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to execute action ${action.type} for $name", e)
        }
    }
    
    /**
     * Обновить контекстную информацию
     */
    private fun updateContext() {
        // Обновляем информацию о ближайших игроках
        val nearbyPlayers = level.getEntitiesOfClass(
            Player::class.java,
            entity.boundingBox.inflate(16.0) // 16 блоков радиус
        )
        contextCache["nearby_players"] = nearbyPlayers
        
        // Обновляем информацию о времени и погоде
        contextCache["time_of_day"] = level.dayTime
        contextCache["is_raining"] = level.isRaining
        contextCache["is_night"] = level.isNight
    }
    
    /**
     * Принять решение о следующих действиях
     */
    private suspend fun makeDecision() {
        try {
            val context = buildDecisionContext()
            
            // Добавляем информацию о группе и репутации в контекст
            context.groupId = groupMembership
            context.playerReputations = getNearbyPlayerReputations()
            context.scheduledTasks = NPCScheduler.getActiveTasks(id)
            
            val decision = decisionEngine.makeDecision(this, context)
            
            decision?.let { processDecision(it) }
        } catch (e: Exception) {
            LOGGER.error("Failed to make decision for $name", e)
        }
    }
    
    /**
     * Построить контекст для принятия решений
     */
    private fun buildDecisionContext(): DecisionContext {
        val nearbyPlayers = contextCache["nearby_players"] as? List<Player> ?: emptyList()
        val recentMemories = memory.getRecentMemories(10)
        
        return DecisionContext(
            npcId = id,
            npcName = name,
            position = position,
            currentState = currentState,
            currentEmotion = currentEmotion,
            currentGoal = currentGoal,
            nearbyPlayers = nearbyPlayers.map { it.name.string },
            recentMemories = recentMemories,
            timeOfDay = contextCache["time_of_day"] as? Long ?: 0L,
            isRaining = contextCache["is_raining"] as? Boolean ?: false,
            personalityTraits = personalityType.traits
        )
    }
    
    /**
     * Обработать решение от DecisionEngine
     */
    private fun processDecision(decision: AIDecision) {
        // Обновляем цель если она изменилась
        if (decision.goal != currentGoal) {
            currentGoal = decision.goal
            LOGGER.debug("NPC $name changed goal to: ${decision.goal}")
            
            // Отправляем событие об изменении цели
            eventBus.publishEvent(NPCEvent.GoalChanged(entity.uuid, decision.goal ?: ""))
        }
        
        // Добавляем действия в очередь
        synchronized(actionQueueLock) {
            decision.actions.forEach { action ->
                actionQueue.addLast(action)
            }
        }
        
        // Обновляем эмоциональное состояние
        if (decision.emotionalResponse != currentEmotion) {
            setEmotion(decision.emotionalResponse)
        }
        
        // Обновляем состояние машины состояний на основе решения
        if (::stateMachine.isInitialized) {
            CoroutineScope(CoroutinePoolManager.actionContext).launch {
                val newState = determineStateFromDecision(decision)
                if (newState != currentState) {
                    stateMachine.transitionTo(newState)
                }
            }
        }
    }
    
    /**
     * Обработать эмоциональные изменения
     */
    private fun processEmotions() {
        // Постепенное затухание эмоций к нейтральному состоянию
        if (currentEmotion != EmotionalState.NEUTRAL) {
            // TODO: Реализовать систему затухания эмоций
        }
    }
    
    /**
     * Обновить память НПС
     */
    private fun updateMemory() {
        // Сохраняем текущую активность в эпизодическую память
        if (currentGoal != null) {
            val episode = MemoryEpisode(
                type = "goal_activity",
                description = "Working on goal: $currentGoal",
                location = position,
                timestamp = System.currentTimeMillis(),
                participants = emptyList(),
                importance = 0.3f
            )
            memory.addEpisode(episode)
        }
    }
    
    /**
     * Установить эмоциональное состояние
     */
    fun setEmotion(emotion: EmotionalState) {
        if (currentEmotion != emotion) {
            val previousEmotion = currentEmotion
            currentEmotion = emotion
            
            LOGGER.debug("NPC $name emotion changed: $previousEmotion -> $emotion")
            
            // Отправляем событие об изменении эмоции
            eventBus.publishEvent(NPCEvent.EmotionChanged(entity.uuid, emotion.name, previousEmotion.name))
            
            // Записываем изменение эмоции в память
            val episode = MemoryEpisode(
                type = "emotion_change",
                description = "Felt $emotion",
                location = position,
                timestamp = System.currentTimeMillis(),
                participants = emptyList(),
                importance = 0.4f
            )
            memory.addEpisode(episode)
        }
    }
    
    /**
     * Добавить действие в очередь выполнения
     */
    fun queueAction(action: Action) {
        synchronized(actionQueueLock) {
            actionQueue.addLast(action)
        }
        LOGGER.debug("Queued action ${action.type} for NPC: $name")
    }
    
    /**
     * Получить информацию о ближайших игроках
     */
    fun getNearbyPlayers(radius: Double = 16.0): List<Player> {
        return level.getEntitiesOfClass(
            Player::class.java,
            entity.boundingBox.inflate(radius)
        )
    }
    
    /**
     * Сохранить текущее состояние в базу данных
     */
    private fun saveCurrentState() {
        try {
            memory.saveNPCState(
                npcId = id,
                position = position,
                emotion = currentEmotion,
                goal = currentGoal,
                state = currentState
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to save state for $name", e)
        }
    }
    
    /**
     * Получить статистику НПС
     */
    fun getStats(): NPCStats {
        return NPCStats(
            npcId = id,
            name = name,
            personalityType = personalityType,
            currentState = currentState,
            currentEmotion = currentEmotion,
            currentGoal = currentGoal,
            queuedActions = synchronized(actionQueueLock) { actionQueue.size },
            memoryEpisodes = memory.getEpisodeCount(),
            isActive = isActive,
            groupMembership = groupMembership,
            reputation = getNearbyPlayerReputations(),
            scheduledTasks = NPCScheduler.getActiveTasks(id).size,
            advancedSystemsActive = advancedSystemsActive,
            advancedSystemsStats = getAdvancedSystemsStats()
        )
    }
    
    // === LOD ПОДДЕРЖКА ===
    
    /**
     * Загрузить состояние НПС из базы данных
     */
    fun loadState(npcData: Any) {
        try {
            // Здесь будет логика загрузки состояния из БД
            LOGGER.debug("Loading state for NPC: $name")
            
            // TODO: Реализовать загрузку реального состояния
            // Когда будет реальная структура NPCData
            
        } catch (e: Exception) {
            LOGGER.error("Failed to load state for NPC: $name", e)
        }
    }
    
    /**
     * Установить частоту мышления AI (для LOD)
     */
    fun setThinkFrequency(frequencyMs: Long) {
        this.thinkInterval = frequencyMs
        LOGGER.debug("Updated think frequency for NPC $name: ${frequencyMs}ms")
    }
    
    /**
     * Установить сложность принятия решений (для LOD)
     */
    fun setDecisionComplexity(complexity: Float) {
        // Можно использовать для упрощения логики при удаленности
        LOGGER.debug("Updated decision complexity for NPC $name: ${complexity}")
        // TODO: Сохранить в переменную и использовать в DecisionEngine
    }
    
    /**
     * Приостановить AI системы (для LOD INACTIVE)
     */
    fun pauseAI() {
        try {
            decisionJob?.cancel()
            
            if (::stateMachine.isInitialized) {
                CoroutineScope(CoroutinePoolManager.actionContext).launch {
                    stateMachine.transitionTo(NPCState.SLEEPING)
                }
            }
            
            LOGGER.debug("AI paused for NPC: $name")
        } catch (e: Exception) {
            LOGGER.error("Failed to pause AI for NPC: $name", e)
        }
    }
    
    /**
     * Возобновить AI системы (после LOD INACTIVE)
     */
    fun resumeAI() {
        if (decisionJob?.isActive != true) {
            start()
            LOGGER.debug("AI resumed for NPC: $name")
        }
    }
    
    // === НОВЫЕ МЕТОДЫ ДЛЯ ИНТЕГРАЦИИ ===
    
    /**
     * Инициализировать новые системы
     */
    private fun initializeNewSystems() {
        try {
            // Инициализируем машину состояний
            stateMachine = NPCStateMachine(this, eventBus, NPCState.IDLE)
            
            // Инициализируем оптимизированный исполнитель действий
            optimizedActionExecutor = OptimizedActionExecutor()
            
            // Регистрируем в планировщике
            NPCScheduler.registerNPC(this)
            
            LOGGER.debug("New systems initialized for NPC: $name")
        } catch (e: Exception) {
            LOGGER.error("Failed to initialize new systems for NPC: $name", e)
        }
    }
    
    /**
     * Получить репутацию ближайших игроков
     */
    private fun getNearbyPlayerReputations(): Map<String, Int> {
        val reputations = mutableMapOf<String, Int>()
        
        getNearbyPlayers().forEach { player ->
            val reputation = ReputationSystem.getPlayerReputation(player.uuid, "default")
            reputations[player.name.string] = reputation
        }
        
        return reputations
    }
    
    /**
     * Обновить репутацию на основе выполненного действия
     */
    private fun updateReputationFromAction(action: ModularAction, result: com.hollowengineai.mod.actions.ActionResult) {
        try {
            val nearbyPlayers = getNearbyPlayers()
            nearbyPlayers.forEach { player ->
                val reputationChange = calculateReputationChange(action, result)
                if (reputationChange != 0) {
                    ReputationSystem.modifyReputation(player.uuid, "default", reputationChange, action.type)
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to update reputation from action for NPC: $name", e)
        }
    }
    
    /**
     * Рассчитать изменение репутации на основе действия
     */
    private fun calculateReputationChange(action: ModularAction, result: com.hollowengineai.mod.actions.ActionResult): Int {
        return when (action.type) {
            "help_player" -> if (result.success) 10 else -2
            "attack_player" -> -20
            "trade" -> if (result.success) 5 else -1
            "heal_player" -> 15
            "insult_player" -> -5
            "compliment_player" -> 3
            "give_gift" -> 8
            else -> 0
        }
    }
    
    /**
     * Определить состояние на основе решения AI
     */
    private fun determineStateFromDecision(decision: AIDecision): NPCState {
        return when {
            decision.actions.any { it.type.contains("attack") || it.type.contains("fight") } -> NPCState.FIGHTING
            decision.actions.any { it.type.contains("talk") || it.type.contains("chat") } -> NPCState.TALKING
            decision.actions.any { it.type.contains("trade") || it.type.contains("sell") } -> NPCState.TRADING
            decision.actions.any { it.type.contains("craft") || it.type.contains("build") } -> NPCState.CRAFTING
            decision.actions.any { it.type.contains("patrol") || it.type.contains("guard") } -> NPCState.PATROLLING
            decision.actions.any { it.type.contains("follow") } -> NPCState.FOLLOWING
            decision.actions.any { it.type.contains("flee") || it.type.contains("escape") } -> NPCState.FLEEING
            decision.goal?.contains("sleep") == true -> NPCState.SLEEPING
            else -> NPCState.ACTIVE
        }
    }
    
    /**
     * Присоединиться к социальной группе
     */
    fun joinGroup(groupId: UUID) {
        groupMembership = groupId
        eventBus.publishEvent(NPCEvent.GroupJoined(entity.uuid, groupId.toString()))
        LOGGER.info("NPC $name joined group: $groupId")
    }
    
    /**
     * Покинуть текущую группу
     */
    fun leaveGroup() {
        groupMembership?.let { groupId ->
            eventBus.publishEvent(NPCEvent.GroupLeft(entity.uuid, groupId.toString()))
            LOGGER.info("NPC $name left group: $groupId")
        }
        groupMembership = null
    }
    
    /**
     * Получить текущую группу
     */
    fun getCurrentGroup(): UUID? = groupMembership
    
    /**
     * Выполнить задачу планировщика
     */
    fun executeScheduledTask(task: NPCTask) {
        CoroutineScope(CoroutinePoolManager.actionContext).launch {
            try {
                LOGGER.debug("Executing scheduled task: ${task.name} for NPC: $name")
                
                // Конвертируем задачу в действие и добавляем в очередь
                val action = convertTaskToAction(task)
                queueAction(action)
                
                // Уведомляем планировщик о начале выполнения
                NPCScheduler.markTaskStarted(task.id)
                
            } catch (e: Exception) {
                LOGGER.error("Failed to execute scheduled task: ${task.name} for NPC: $name", e)
                NPCScheduler.markTaskFailed(task.id, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Конвертировать задачу планировщика в действие
     */
    private fun convertTaskToAction(task: NPCTask): Action {
        return Action(
            type = task.actionType,
            target = task.targetEntity,
            location = task.targetLocation,
            parameters = task.parameters,
            priority = task.priority.ordinal
        )
    }
    
    // === МЕТОДЫ ДЛЯ ПРОДВИНУТЫХ ИИ СИСТЕМ ===
    
    /**
     * Инициализировать продвинутые ИИ системы
     */
    private fun initializeAdvancedSystems() {
        try {
            LOGGER.info("Initializing advanced AI systems for NPC: $name")
            
            // Инициализируем систему восприятия
            perceptionSystem = PerceptionSystem(this, eventBus)
            
            // Инициализируем детектор взгляда
            gazeDetector = GazeDetector()
            
            // Инициализируем систему асинхронных действий
            asyncActionSystem = AsyncActionSystem(this, eventBus)
            
            // Инициализируем систему планирования
            planSystem = NPCPlanSystem(this, eventBus, asyncActionSystem)
            
            // Инициализируем систему прерываний
            interruptionSystem = InterruptionSystem(this, eventBus, asyncActionSystem, planSystem)
            
            // Инициализируем менеджер внимания
            attentionManager = AttentionManager(this, eventBus)
            
            // Инициализируем систему социальной осведомленности
            socialAwarenessSystem = SocialAwarenessSystem(this, eventBus)
            
            // Связываем системы друг с другом
            linkAdvancedSystems()
            
            LOGGER.info("Advanced AI systems initialized for NPC: $name")
        } catch (e: Exception) {
            LOGGER.error("Failed to initialize advanced AI systems for NPC: $name", e)
        }
    }
    
    /**
     * Связать продвинутые системы друг с другом
     */
    private fun linkAdvancedSystems() {
        // Система восприятия подает кандидатов внимания в AttentionManager
        // Это будет происходить через события
        
        // Система прерываний получает данные от AttentionManager и PerceptionSystem
        // InterruptionSystem уже подключена к AttentionManager в конструкторе
        
        // Связываем систему восприятия с социальной осведомленностью
        // Через события NPCEventBus
        
        LOGGER.debug("Advanced AI systems linked for NPC: $name")
    }
    
    /**
     * Запустить продвинутые ИИ системы
     */
    private fun startAdvancedSystems() {
        if (advancedSystemsActive) {
            LOGGER.warn("Advanced AI systems already active for NPC: $name")
            return
        }
        
        try {
            LOGGER.info("Starting advanced AI systems for NPC: $name")
            
            // Запускаем все системы
            perceptionSystem.start()
            asyncActionSystem.start()
            planSystem.start()
            interruptionSystem.start()
            attentionManager.start()
            socialAwarenessSystem.start()
            
            advancedSystemsActive = true
            
            LOGGER.info("Advanced AI systems started for NPC: $name")
        } catch (e: Exception) {
            LOGGER.error("Failed to start advanced AI systems for NPC: $name", e)
            advancedSystemsActive = false
        }
    }
    
    /**
     * Остановить продвинутые ИИ системы
     */
    private fun stopAdvancedSystems() {
        if (!advancedSystemsActive) return
        
        try {
            LOGGER.info("Stopping advanced AI systems for NPC: $name")
            
            // Останавливаем все системы в обратном порядке
            socialAwarenessSystem.stop()
            attentionManager.stop()
            interruptionSystem.stop()
            planSystem.stop()
            asyncActionSystem.stop()
            perceptionSystem.stop()
            
            advancedSystemsActive = false
            
            LOGGER.info("Advanced AI systems stopped for NPC: $name")
        } catch (e: Exception) {
            LOGGER.error("Failed to stop advanced AI systems for NPC: $name", e)
        }
    }
    
    /**
     * Обработка продвинутых систем ИИ на каждом тике
     */
    private suspend fun processAdvancedSystems() {
        try {
            // Системы работают асинхронно, поэтому здесь минимальная логика
            
            // Проверяем фокус внимания и обновляем поведение
            if (::attentionManager.isInitialized) {
                val currentFocus = attentionManager.getCurrentFocus()
                currentFocus?.let { focus ->
                    // Если НПС сосредоточен на игроке, записываем взаимодействие
                    if (focus.targetType.name == "PLAYER" && focus.target is Player) {
                        val player = focus.target as Player
                        
                        // Проверяем взгляд игрока на НПС
                        val gazeResult = gazeDetector.analyzeGaze(player, entity)
                        if (gazeResult.isLookingAt && gazeResult.confidence > 0.7) {
                            // Записываем социальное взаимодействие
                            if (::socialAwarenessSystem.isInitialized) {
                                socialAwarenessSystem.recordInteraction(
                                    player, 
                                    InteractionType.GREETING,
                                    "Игрок смотрит на НПС",
                                    InteractionImportance.TRIVIAL
                                )
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            LOGGER.error("Error processing advanced AI systems for NPC: $name", e)
        }
    }
    
    // === ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ ВЗАИМОДЕЙСТВИЯ С ПРОДВИНУТЫМИ СИСТЕМАМИ ===
    
    /**
     * Записать взаимодействие с игроком
     */
    fun recordPlayerInteraction(player: Player, interactionType: InteractionType, 
                               context: String = "", importance: InteractionImportance = InteractionImportance.NORMAL) {
        if (::socialAwarenessSystem.isInitialized) {
            socialAwarenessSystem.recordInteraction(player, interactionType, context, importance)
        }
    }
    
    /**
     * Выполнить асинхронное действие с приоритетом
     */
    fun executeAsyncAction(actionId: String, priority: ActionPriority = ActionPriority.NORMAL): String? {
        return if (::asyncActionSystem.isInitialized) {
            // Создаем простое действие и выполняем его
            val actionExecutionId = UUID.randomUUID().toString()
            // В реальной реализации здесь будет создание NPCAction
            LOGGER.debug("Queued async action $actionId with priority $priority for NPC: $name")
            actionExecutionId
        } else {
            null
        }
    }
    
    /**
     * Получить анализ социальной ситуации с игроком
     */
    fun getSocialAnalysis(playerId: UUID) = 
        if (::socialAwarenessSystem.isInitialized) {
            socialAwarenessSystem.analyzeSocialSituation(playerId)
        } else {
            null
        }
    
    /**
     * Получить текущий объект внимания
     */
    fun getCurrentAttentionTarget() = 
        if (::attentionManager.isInitialized) {
            attentionManager.getCurrentFocus()
        } else {
            null
        }
    
    /**
     * Принудительно сфокусировать внимание на объекте
     */
    fun focusAttentionOn(target: Any, reason: String, durationMs: Long = 5000L) {
        if (::attentionManager.isInitialized) {
            attentionManager.forceFocus(target, reason, durationMs)
        }
    }
    
    /**
     * Получить данные восприятия о игроке
     */
    fun getPerceptionData(playerId: UUID) = 
        if (::perceptionSystem.isInitialized) {
            perceptionSystem.getPerceivedPlayer(playerId)
        } else {
            null
        }
    
    /**
     * Получить все воспринимаемые игроки
     */
    fun getPerceivedPlayers() = 
        if (::perceptionSystem.isInitialized) {
            perceptionSystem.getAllPerceivedPlayers()
        } else {
            emptyMap()
        }
    
    /**
     * Проверить смотрит ли игрок на НПС
     */
    fun isPlayerLookingAtMe(player: Player) = 
        if (::gazeDetector.isInitialized) {
            gazeDetector.analyzeGaze(player, entity).isLookingAt
        } else {
            false
        }
    
    /**
     * Получить статистику продвинутых систем
     */
    fun getAdvancedSystemsStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        if (::perceptionSystem.isInitialized) {
            stats["perception"] = perceptionSystem.getSystemStats()
        }
        
        if (::asyncActionSystem.isInitialized) {
            stats["async_actions"] = asyncActionSystem.getSystemStats()
        }
        
        if (::planSystem.isInitialized) {
            stats["plans"] = planSystem.getSystemStats()
        }
        
        if (::interruptionSystem.isInitialized) {
            stats["interruptions"] = interruptionSystem.getSystemStats()
        }
        
        if (::attentionManager.isInitialized) {
            stats["attention"] = attentionManager.getSystemStats()
        }
        
        if (::socialAwarenessSystem.isInitialized) {
            stats["social"] = socialAwarenessSystem.getSystemStats()
        }
        
        stats["advanced_systems_active"] = advancedSystemsActive
        
        return stats
    }
}

/**
 * Статистика НПС
 */
data class NPCStats(
    val npcId: UUID,
    val name: String,
    val personalityType: PersonalityType,
    val currentState: NPCState,
    val currentEmotion: EmotionalState,
    val currentGoal: String?,
    val queuedActions: Int,
    val memoryEpisodes: Int,
    val isActive: Boolean,
    val groupMembership: UUID?,
    val reputation: Map<String, Int>,
    val scheduledTasks: Int,
    val advancedSystemsActive: Boolean,
    val advancedSystemsStats: Map<String, Any>
)
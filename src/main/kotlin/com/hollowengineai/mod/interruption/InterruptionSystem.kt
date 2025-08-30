package com.hollowengineai.mod.interruption

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.async.AsyncActionSystem
import com.hollowengineai.mod.planning.NPCPlanSystem
import com.hollowengineai.mod.perception.PerceptionSystem
import com.hollowengineai.mod.perception.PerceivedPlayer
import com.hollowengineai.mod.perception.PlayerIntent
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Система прерываний НПС
 * 
 * Возможности:
 * - Мониторинг важных событий и условий
 * - Прерывание текущих действий и планов при необходимости
 * - Приоритизация прерываний
 * - Сохранение состояния для возможного восстановления
 * - Автоматическое возобновление прерванных действий
 * - Интеграция с системами восприятия и планирования
 */
class InterruptionSystem(
    private val npc: SmartNPC,
    private val actionSystem: AsyncActionSystem,
    private val planSystem: NPCPlanSystem,
    private val perceptionSystem: PerceptionSystem,
    private val eventBus: NPCEventBus
) {
    companion object {
        private val LOGGER = LogManager.getLogger(InterruptionSystem::class.java)
        private const val MONITOR_INTERVAL = 250L // 4 раза в секунду
        private const val MAX_INTERRUPT_HISTORY = 100
    }
    
    // Система корутин
    private val interruptScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Активные прерывания и их состояния
    private val activeInterrupts = ConcurrentHashMap<UUID, ActiveInterrupt>()
    private val interruptHistory = mutableListOf<InterruptEvent>()
    private val savedStates = ConcurrentHashMap<UUID, SavedState>()
    
    // Обработчики прерываний
    private val interruptHandlers = mutableMapOf<InterruptType, InterruptHandler>()
    
    // Состояние системы
    private var isRunning = false
    private var monitoringJob: Job? = null
    
    // Статистика
    private var interruptsTriggered = 0L
    private var interruptsHandled = 0L
    private var statesRestored = 0L
    
    /**
     * Запустить систему прерываний
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        // Регистрируем базовые обработчики
        registerBaseHandlers()
        
        // Запускаем мониторинг
        monitoringJob = interruptScope.launch {
            while (isRunning) {
                try {
                    monitorForInterrupts()
                    delay(MONITOR_INTERVAL)
                } catch (e: Exception) {
                    LOGGER.error("Error in interrupt monitoring for NPC ${npc.name}", e)
                    delay(MONITOR_INTERVAL * 2)
                }
            }
        }
        
        LOGGER.debug("InterruptionSystem started for NPC ${npc.name}")
    }
    
    /**
     * Остановить систему прерываний
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        // Завершаем все активные прерывания
        val activeInterruptIds = activeInterrupts.keys.toList()
        activeInterruptIds.forEach { interruptId ->
            endInterrupt(interruptId, InterruptEndReason.SYSTEM_SHUTDOWN)
        }
        
        monitoringJob?.cancel()
        interruptScope.cancel()
        
        LOGGER.debug("InterruptionSystem stopped for NPC ${npc.name}")
    }
    
    /**
     * Зарегистрировать обработчик прерываний
     */
    fun registerHandler(type: InterruptType, handler: InterruptHandler) {
        interruptHandlers[type] = handler
        LOGGER.debug("Registered interrupt handler for type $type for NPC ${npc.name}")
    }
    
    /**
     * Вручную вызвать прерывание
     */
    suspend fun triggerInterrupt(
        type: InterruptType,
        reason: String,
        priority: InterruptPriority = InterruptPriority.NORMAL,
        data: Map<String, Any> = emptyMap()
    ): UUID? {
        val interrupt = InterruptData(
            type = type,
            reason = reason,
            priority = priority,
            sourceId = null,
            data = data,
            timestamp = System.currentTimeMillis()
        )
        
        return processInterrupt(interrupt)
    }
    
    /**
     * Завершить прерывание
     */
    fun endInterrupt(interruptId: UUID, reason: InterruptEndReason = InterruptEndReason.COMPLETED) {
        val activeInterrupt = activeInterrupts.remove(interruptId) ?: return
        
        // Записываем в историю
        recordInterruptEvent(
            interruptId,
            InterruptEventType.INTERRUPT_ENDED,
            "Interrupt ended: $reason"
        )
        
        // Восстанавливаем сохраненное состояние если нужно
        if (reason == InterruptEndReason.COMPLETED && activeInterrupt.canRestore) {
            restoreSavedState(interruptId)
        }
        
        publishInterruptEvent("interrupt_ended", activeInterrupt.interruptData)
        
        LOGGER.debug("Ended interrupt $interruptId for NPC ${npc.name}: $reason")
    }
    
    /**
     * Мониторинг условий для прерываний
     */
    private suspend fun monitorForInterrupts() {
        // Проверяем различные условия прерываний
        checkPerceptionInterrupts()
        checkHealthInterrupts()
        checkEnvironmentInterrupts()
        checkSocialInterrupts()
        checkTimeInterrupts()
    }
    
    /**
     * Проверить прерывания на основе восприятия
     */
    private suspend fun checkPerceptionInterrupts() {
        val perceivedPlayers = perceptionSystem.getPerceivedPlayers()
        
        perceivedPlayers.values.forEach { player ->
            checkPlayerInterrupts(player)
        }
        
        // Проверяем что игроки смотрят на НПС
        val playersLooking = perceptionSystem.getPlayersLookingAtNPC()
        if (playersLooking.isNotEmpty() && !hasActiveInterrupt(InterruptType.BEING_OBSERVED)) {
            val interrupt = InterruptData(
                type = InterruptType.BEING_OBSERVED,
                reason = "${playersLooking.size} player(s) looking at me",
                priority = InterruptPriority.LOW,
                data = mapOf("playerCount" to playersLooking.size, "playerNames" to playersLooking.map { it.playerName })
            )
            processInterrupt(interrupt)
        }
    }
    
    /**
     * Проверить прерывания связанные с конкретным игроком
     */
    private suspend fun checkPlayerInterrupts(player: PerceivedPlayer) {
        // Агрессивное поведение игрока
        if (player.behaviorAnalysis.inferredIntent == PlayerIntent.AGGRESSIVE) {
            val interrupt = InterruptData(
                type = InterruptType.THREAT_DETECTED,
                reason = "Player ${player.playerName} showing aggressive behavior",
                priority = InterruptPriority.CRITICAL,
                sourceId = player.playerId.toString(),
                data = mapOf(
                    "playerName" to player.playerName,
                    "distance" to player.distance,
                    "isHoldingWeapon" to player.behaviorAnalysis.actions.isHoldingWeapon
                )
            )
            processInterrupt(interrupt)
        }
        
        // Игрок хочет взаимодействовать
        if (player.behaviorAnalysis.inferredIntent == PlayerIntent.WANTS_TO_INTERACT && 
            !hasActiveInterrupt(InterruptType.INTERACTION_REQUEST)) {
            val interrupt = InterruptData(
                type = InterruptType.INTERACTION_REQUEST,
                reason = "Player ${player.playerName} wants to interact",
                priority = InterruptPriority.HIGH,
                sourceId = player.playerId.toString(),
                data = mapOf(
                    "playerName" to player.playerName,
                    "distance" to player.distance,
                    "gazeDuration" to player.gazeData.gazeDuration
                )
            )
            processInterrupt(interrupt)
        }
        
        // Нарушение личного пространства
        if (player.socialSignals.personalSpaceViolation && 
            !hasActiveInterrupt(InterruptType.PERSONAL_SPACE_VIOLATION)) {
            val interrupt = InterruptData(
                type = InterruptType.PERSONAL_SPACE_VIOLATION,
                reason = "Player ${player.playerName} invaded personal space",
                priority = InterruptPriority.NORMAL,
                sourceId = player.playerId.toString(),
                data = mapOf(
                    "playerName" to player.playerName,
                    "distance" to player.distance
                )
            )
            processInterrupt(interrupt)
        }
    }
    
    /**
     * Проверить прерывания связанные со здоровьем
     */
    private suspend fun checkHealthInterrupts() {
        val entityHealth = npc.getEntity().health
        val maxHealth = npc.getEntity().maxHealth
        val healthPercentage = entityHealth / maxHealth
        
        // Низкое здоровье
        if (healthPercentage < 0.3 && !hasActiveInterrupt(InterruptType.LOW_HEALTH)) {
            val interrupt = InterruptData(
                type = InterruptType.LOW_HEALTH,
                reason = "Health is critically low (${(healthPercentage * 100).toInt()}%)",
                priority = InterruptPriority.CRITICAL,
                data = mapOf(
                    "health" to entityHealth,
                    "maxHealth" to maxHealth,
                    "percentage" to healthPercentage
                )
            )
            processInterrupt(interrupt)
        }
    }
    
    /**
     * Проверить прерывания окружающей среды
     */
    private suspend fun checkEnvironmentInterrupts() {
        // Проверяем время суток
        val worldTime = npc.level.dayTime % 24000
        val isNight = worldTime > 13000 && worldTime < 23000
        
        if (isNight && !hasActiveInterrupt(InterruptType.TIME_CHANGE)) {
            val interrupt = InterruptData(
                type = InterruptType.TIME_CHANGE,
                reason = "Night time has arrived",
                priority = InterruptPriority.LOW,
                data = mapOf("timeOfDay" to "night", "worldTime" to worldTime)
            )
            processInterrupt(interrupt)
        }
        
        // Проверяем погоду (если есть дождь)
        if (npc.level.isRaining && !hasActiveInterrupt(InterruptType.WEATHER_CHANGE)) {
            val interrupt = InterruptData(
                type = InterruptType.WEATHER_CHANGE,
                reason = "It started raining",
                priority = InterruptPriority.LOW,
                data = mapOf("weather" to "rain", "isThundering" to npc.level.isThundering)
            )
            processInterrupt(interrupt)
        }
    }
    
    /**
     * Проверить социальные прерывания
     */
    private suspend fun checkSocialInterrupts() {
        val perceivedPlayers = perceptionSystem.getPerceivedPlayers()
        
        // Много игроков рядом
        if (perceivedPlayers.size >= 3 && !hasActiveInterrupt(InterruptType.CROWD_DETECTED)) {
            val interrupt = InterruptData(
                type = InterruptType.CROWD_DETECTED,
                reason = "Large group of ${perceivedPlayers.size} players nearby",
                priority = InterruptPriority.NORMAL,
                data = mapOf(
                    "playerCount" to perceivedPlayers.size,
                    "playerNames" to perceivedPlayers.values.map { it.playerName }
                )
            )
            processInterrupt(interrupt)
        }
    }
    
    /**
     * Проверить временные прерывания
     */
    private suspend fun checkTimeInterrupts() {
        // Можно добавить прерывания на основе времени
        // Например, регулярные проверки состояния
    }
    
    /**
     * Обработать прерывание
     */
    private suspend fun processInterrupt(interruptData: InterruptData): UUID? {
        interruptsTriggered++
        
        // Проверяем нужно ли обрабатывать это прерывание
        if (!shouldProcessInterrupt(interruptData)) {
            return null
        }
        
        val interruptId = UUID.randomUUID()
        
        recordInterruptEvent(
            interruptId,
            InterruptEventType.INTERRUPT_TRIGGERED,
            "Interrupt triggered: ${interruptData.reason}"
        )
        
        // Находим обработчик
        val handler = interruptHandlers[interruptData.type]
        if (handler == null) {
            LOGGER.warn("No handler found for interrupt type ${interruptData.type} for NPC ${npc.name}")
            return null
        }
        
        // Сохраняем текущее состояние если нужно
        val shouldSave = handler.shouldSaveState(npc, interruptData)
        if (shouldSave) {
            saveCurrentState(interruptId)
        }
        
        // Прерываем текущие действия/планы если нужно
        val shouldInterrupt = handler.shouldInterruptCurrent(npc, interruptData)
        if (shouldInterrupt) {
            interruptCurrentActivities(interruptData.priority)
        }
        
        // Обрабатываем прерывание
        try {
            handler.handle(npc, interruptData)
            interruptsHandled++
            
            val activeInterrupt = ActiveInterrupt(
                id = interruptId,
                interruptData = interruptData,
                handler = handler,
                startTime = System.currentTimeMillis(),
                canRestore = shouldSave
            )
            
            activeInterrupts[interruptId] = activeInterrupt
            
            recordInterruptEvent(
                interruptId,
                InterruptEventType.INTERRUPT_HANDLED,
                "Interrupt handled by ${handler::class.simpleName}"
            )
            
            publishInterruptEvent("interrupt_handled", interruptData)
            
            LOGGER.debug("Processed interrupt $interruptId (${interruptData.type}) for NPC ${npc.name}")
            
            return interruptId
            
        } catch (e: Exception) {
            LOGGER.error("Failed to handle interrupt ${interruptData.type} for NPC ${npc.name}", e)
            recordInterruptEvent(
                interruptId,
                InterruptEventType.INTERRUPT_FAILED,
                "Failed to handle interrupt: ${e.message}"
            )
            return null
        }
    }
    
    /**
     * Проверить нужно ли обрабатывать прерывание
     */
    private fun shouldProcessInterrupt(interruptData: InterruptData): Boolean {
        // Проверяем есть ли уже активное прерывание того же типа
        val existingInterrupt = activeInterrupts.values.find { it.interruptData.type == interruptData.type }
        if (existingInterrupt != null) {
            // Если новое прерывание с более высоким приоритетом, заменяем
            return interruptData.priority.ordinal > existingInterrupt.interruptData.priority.ordinal
        }
        
        // Проверяем не слишком ли много прерываний одного типа за короткое время
        val recentSimilar = interruptHistory
            .filter { it.interruptType == interruptData.type }
            .filter { System.currentTimeMillis() - it.timestamp < 10000 } // 10 секунд
            .size
        
        if (recentSimilar > 3) {
            LOGGER.debug("Too many recent ${interruptData.type} interrupts for NPC ${npc.name}, ignoring")
            return false
        }
        
        return true
    }
    
    /**
     * Прервать текущие активности
     */
    private fun interruptCurrentActivities(priority: InterruptPriority) {
        when (priority) {
            InterruptPriority.CRITICAL -> {
                // Отменяем все действия и планы
                actionSystem.cancelAllActions("Critical interrupt")
                planSystem.cancelAllPlans("Critical interrupt")
            }
            InterruptPriority.HIGH -> {
                // Отменяем действия с низким приоритетом
                actionSystem.cancelLowPriorityActions()
                // Приостанавливаем планы с низким приоритетом
                // TODO: добавить метод в planSystem для приостановки низкоприоритетных планов
            }
            InterruptPriority.NORMAL -> {
                // Отменяем только самые низкоприоритетные действия
                actionSystem.cancelLowPriorityActions()
            }
            InterruptPriority.LOW -> {
                // Не прерываем текущие действия
            }
        }
    }
    
    /**
     * Сохранить текущее состояние
     */
    private fun saveCurrentState(interruptId: UUID) {
        val currentTime = System.currentTimeMillis()
        
        val savedState = SavedState(
            interruptId = interruptId,
            timestamp = currentTime,
            activeActions = actionSystem.getActiveActions(),
            activePlans = planSystem.getActivePlans(),
            context = "State saved before interrupt"
        )
        
        savedStates[interruptId] = savedState
        
        LOGGER.debug("Saved state for interrupt $interruptId for NPC ${npc.name}")
    }
    
    /**
     * Восстановить сохраненное состояние
     */
    private fun restoreSavedState(interruptId: UUID) {
        val savedState = savedStates.remove(interruptId) ?: return
        
        // TODO: Реализовать восстановление состояния
        // Это сложная логика которая требует поддержки в ActionSystem и PlanSystem
        
        statesRestored++
        
        LOGGER.debug("Restored state for interrupt $interruptId for NPC ${npc.name}")
    }
    
    /**
     * Проверить есть ли активное прерывание определенного типа
     */
    private fun hasActiveInterrupt(type: InterruptType): Boolean {
        return activeInterrupts.values.any { it.interruptData.type == type }
    }
    
    /**
     * Записать событие прерывания
     */
    private fun recordInterruptEvent(
        interruptId: UUID,
        eventType: InterruptEventType,
        description: String
    ) {
        val event = InterruptEvent(
            interruptId = interruptId,
            eventType = eventType,
            interruptType = activeInterrupts[interruptId]?.interruptData?.type,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(interruptHistory) {
            interruptHistory.add(event)
            if (interruptHistory.size > MAX_INTERRUPT_HISTORY) {
                interruptHistory.removeAt(0)
            }
        }
    }
    
    /**
     * Опубликовать событие прерывания
     */
    private fun publishInterruptEvent(eventType: String, interruptData: InterruptData) {
        try {
            val event = NPCEvents.customEvent(
                npcId = npc.id,
                npcName = npc.name,
                eventData = mapOf(
                    "eventType" to eventType,
                    "interruptType" to interruptData.type.name,
                    "interruptReason" to interruptData.reason,
                    "interruptPriority" to interruptData.priority.name,
                    "interruptData" to interruptData.data
                ),
                position = npc.getEntity().blockPosition()
            )
            
            eventBus.sendEventSync(event)
        } catch (e: Exception) {
            LOGGER.warn("Failed to publish interrupt event for NPC ${npc.name}", e)
        }
    }
    
    /**
     * Зарегистрировать базовые обработчики
     */
    private fun registerBaseHandlers() {
        registerHandler(InterruptType.THREAT_DETECTED, ThreatHandler())
        registerHandler(InterruptType.INTERACTION_REQUEST, InteractionHandler())
        registerHandler(InterruptType.BEING_OBSERVED, ObservationHandler())
        registerHandler(InterruptType.PERSONAL_SPACE_VIOLATION, PersonalSpaceHandler())
        registerHandler(InterruptType.LOW_HEALTH, HealthHandler())
        registerHandler(InterruptType.TIME_CHANGE, TimeChangeHandler())
        registerHandler(InterruptType.WEATHER_CHANGE, WeatherHandler())
        registerHandler(InterruptType.CROWD_DETECTED, CrowdHandler())
    }
    
    // Геттеры для информации
    
    fun getActiveInterrupts(): List<InterruptInfo> {
        return activeInterrupts.values.map { activeInterrupt ->
            InterruptInfo(
                id = activeInterrupt.id,
                type = activeInterrupt.interruptData.type,
                reason = activeInterrupt.interruptData.reason,
                priority = activeInterrupt.interruptData.priority,
                startTime = activeInterrupt.startTime,
                canRestore = activeInterrupt.canRestore
            )
        }
    }
    
    fun getInterruptHistory(): List<InterruptEvent> {
        return synchronized(interruptHistory) {
            interruptHistory.toList()
        }
    }
    
    fun getStats(): InterruptionStats {
        return InterruptionStats(
            interruptsTriggered = interruptsTriggered,
            interruptsHandled = interruptsHandled,
            statesRestored = statesRestored,
            activeInterrupts = activeInterrupts.size,
            savedStates = savedStates.size
        )
    }
    
    fun isHealthy(): Boolean {
        return isRunning && 
               interruptScope.isActive &&
               activeInterrupts.size < 10 // Не слишком много активных прерываний
    }
}
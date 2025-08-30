package com.hollowengineai.mod.attention

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.perception.PerceptionSystem
import com.hollowengineai.mod.perception.PerceivedPlayer
import com.hollowengineai.mod.perception.PlayerIntent
import com.hollowengineai.mod.interruption.InterruptionSystem
import com.hollowengineai.mod.interruption.InterruptType
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Менеджер внимания НПС
 * 
 * Управляет фокусом внимания НПС, определяя на что НПС должен обращать внимание
 * в каждый момент времени на основе важности различных стимулов.
 * 
 * Возможности:
 * - Управление фокусом внимания
 * - Переключение внимания между объектами
 * - Приоритизация стимулов
 * - Адаптивные пороги внимания
 * - Память о предыдущих объектах внимания
 * - Интеграция с восприятием и прерываниями
 */
class AttentionManager(
    private val npc: SmartNPC,
    private val perceptionSystem: PerceptionSystem,
    private val interruptionSystem: InterruptionSystem,
    private val eventBus: NPCEventBusImpl,
    private val config: AttentionConfig = AttentionConfig()
) {
    companion object {
        private val LOGGER = LogManager.getLogger(AttentionManager::class.java)
        private const val UPDATE_INTERVAL = 500L // 2 раза в секунду
    }
    
    // Система корутин
    private val attentionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Текущее состояние внимания
    private var currentFocus: AttentionFocus? = null
    private var attentionHistory = mutableListOf<AttentionEvent>()
    private val candidateTargets = ConcurrentHashMap<String, AttentionCandidate>()
    
    // Модуляторы внимания
    private var arousalLevel = 0.5 // 0.0 - 1.0 (уровень возбуждения/активности)
    private var stressLevel = 0.0 // 0.0 - 1.0 (уровень стресса)
    private var fatigueLevel = 0.0 // 0.0 - 1.0 (уровень усталости)
    private var focusLevel = 0.5 // 0.0 - 1.0 (уровень фокуса/концентрации)
    
    // Состояние системы
    private var isRunning = false
    private var updateJob: Job? = null
    private var lastUpdateTime = 0L
    
    // Статистика
    private var focusChanges = 0L
    private var attentionUpdates = 0L
    
    /**
     * Запустить менеджер внимания
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        // Запускаем цикл обновления внимания
        updateJob = attentionScope.launch {
            while (isRunning) {
                try {
                    updateAttention()
                    delay(UPDATE_INTERVAL)
                } catch (e: Exception) {
                    LOGGER.error("Error in attention update for NPC ${npc.name}", e)
                    delay(UPDATE_INTERVAL * 2)
                }
            }
        }
        
        LOGGER.debug("AttentionManager started for NPC ${npc.name}")
    }
    
    /**
     * Остановить менеджер внимания
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        updateJob?.cancel()
        attentionScope.cancel()
        
        // Очищаем фокус
        if (currentFocus != null) {
            releaseFocus("System shutdown")
        }
        
        LOGGER.debug("AttentionManager stopped for NPC ${npc.name}")
    }
    
    /**
     * Принудительно установить фокус внимания
     */
    fun forceFocus(target: AttentionTarget, reason: String, duration: Long? = null) {
        val focus = AttentionFocus(
            target = target,
            startTime = System.currentTimeMillis(),
            reason = reason,
            strength = 1.0,
            isForced = true,
            forcedDuration = duration
        )
        
        setFocus(focus)
        LOGGER.debug("Forced focus on ${target.type} for NPC ${npc.name}: $reason")
    }
    
    /**
     * Освободить текущий фокус
     */
    fun releaseFocus(reason: String = "Released manually") {
        currentFocus?.let { focus ->
            recordAttentionEvent(
                AttentionEventType.FOCUS_LOST,
                focus.target,
                "Focus released: $reason"
            )
            
            publishAttentionEvent("focus_lost", focus.target, reason)
        }
        
        currentFocus = null
        LOGGER.debug("Released focus for NPC ${npc.name}: $reason")
    }
    
    /**
     * Получить текущий фокус внимания
     */
    fun getCurrentFocus(): AttentionFocus? = currentFocus
    
    /**
     * Проверить сфокусирован ли НПС на определенной цели
     */
    fun isFocusedOn(targetId: String): Boolean {
        return currentFocus?.target?.id == targetId
    }
    
    /**
     * Получить силу внимания к определенной цели (0.0 - 1.0)
     */
    fun getAttentionStrength(targetId: String): Double {
        return if (currentFocus?.target?.id == targetId) {
            currentFocus?.strength ?: 0.0
        } else {
            candidateTargets[targetId]?.attentionValue ?: 0.0
        }
    }
    
    /**
     * Добавить модификатор внимания
     */
    fun addAttentionModifier(modifier: AttentionModifier) {
        when (modifier.type) {
            AttentionModifierType.AROUSAL -> {
                arousalLevel = (arousalLevel + modifier.value).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.STRESS -> {
                stressLevel = (stressLevel + modifier.value).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.FATIGUE -> {
                fatigueLevel = (fatigueLevel + modifier.value).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.FOCUS_BOOST -> {
                focusLevel = (focusLevel + modifier.value).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.DISTRACTION -> {
                focusLevel = (focusLevel - modifier.value).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.INTEREST -> {
                arousalLevel = (arousalLevel + modifier.value * 0.5).coerceIn(0.0, 1.0)
                focusLevel = (focusLevel + modifier.value * 0.3).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.FEAR -> {
                stressLevel = (stressLevel + modifier.value).coerceIn(0.0, 1.0)
                arousalLevel = (arousalLevel + modifier.value * 0.7).coerceIn(0.0, 1.0)
            }
            AttentionModifierType.CURIOSITY -> {
                arousalLevel = (arousalLevel + modifier.value * 0.4).coerceIn(0.0, 1.0)
                focusLevel = (focusLevel + modifier.value * 0.6).coerceIn(0.0, 1.0)
            }
        }
        
        LOGGER.debug("Applied attention modifier ${modifier.type} (${modifier.value}) to NPC ${npc.name}")
    }
    
    /**
     * Основной цикл обновления внимания
     */
    private suspend fun updateAttention() {
        val currentTime = System.currentTimeMillis()
        lastUpdateTime = currentTime
        attentionUpdates++
        
        // Обновляем кандидатов на внимание
        updateAttentionCandidates()
        
        // Проверяем нужно ли переключить фокус
        evaluateFocusChange()
        
        // Обновляем модуляторы внимания
        updateAttentionModulators()
        
        // Убираем устаревшие кандидаты
        cleanupCandidates(currentTime)
        
        // Проверяем принудительный фокус
        checkForcedFocus(currentTime)
    }
    
    /**
     * Обновить кандидатов на внимание
     */
    private suspend fun updateAttentionCandidates() {
        // Получаем данные восприятия
        val perceivedPlayers = perceptionSystem.getPerceivedPlayers()
        val activeInterrupts = interruptionSystem.getActiveInterrupts()
        
        // Обрабатываем игроков
        perceivedPlayers.values.forEach { player ->
            val candidate = createPlayerCandidate(player)
            candidateTargets[candidate.target.id] = candidate
        }
        
        // Обрабатываем прерывания
        activeInterrupts.forEach { interrupt ->
            val candidate = createInterruptCandidate(interrupt)
            candidateTargets[candidate.target.id] = candidate
        }
        
        // Добавляем цели окружающей среды
        addEnvironmentalCandidates()
    }
    
    /**
     * Создать кандидата внимания для игрока
     */
    private fun createPlayerCandidate(player: PerceivedPlayer): AttentionCandidate {
        var attentionValue = 0.0
        val factors = mutableMapOf<String, Double>()
        
        // Базовая ценность игрока
        factors["base_player_value"] = 0.3
        attentionValue += 0.3
        
        // Расстояние (ближе = больше внимания)
        val distanceFactor = max(0.0, 1.0 - (player.distance / config.maxAttentionDistance))
        factors["distance"] = distanceFactor * 0.2
        attentionValue += distanceFactor * 0.2
        
        // Взгляд (смотрит на НПС = больше внимания)
        if (player.gazeData.isLookingAtNPC) {
            val gazeFactor = min(1.0, player.gazeData.gazeDuration / 5000.0) // 5 секунд = полная сила
            factors["gaze"] = gazeFactor * 0.3
            attentionValue += gazeFactor * 0.3
        }
        
        // Намерения игрока
        val intentFactor = when (player.behaviorAnalysis.inferredIntent) {
            PlayerIntent.AGGRESSIVE, PlayerIntent.THREATENING -> 0.8
            PlayerIntent.WANTS_TO_INTERACT, PlayerIntent.WANTS_TO_TRADE -> 0.6
            PlayerIntent.APPROACHING_TO_TALK -> 0.5
            PlayerIntent.CURIOUS -> 0.3
            PlayerIntent.STALKING -> 0.7
            else -> 0.1
        }
        factors["intent"] = intentFactor * 0.3
        attentionValue += intentFactor * 0.3
        
        // Движение (движущиеся объекты привлекают внимание)
        if (player.behaviorAnalysis.movement.isMoving) {
            val movementFactor = if (player.behaviorAnalysis.movement.isRunning) 0.3 else 0.1
            factors["movement"] = movementFactor
            attentionValue += movementFactor
        }
        
        // Социальные сигналы
        if (player.socialSignals.attentionSeeking) {
            factors["attention_seeking"] = 0.4
            attentionValue += 0.4
        }
        
        if (player.socialSignals.personalSpaceViolation) {
            factors["space_violation"] = 0.3
            attentionValue += 0.3
        }
        
        // Применяем модуляторы
        attentionValue = applyModulators(attentionValue)
        
        val target = AttentionTarget(
            id = player.playerId.toString(),
            type = AttentionTargetType.PLAYER,
            position = player.position,
            description = "Player: ${player.playerName}"
        )
        
        return AttentionCandidate(
            target = target,
            attentionValue = attentionValue.coerceIn(0.0, 1.0),
            factors = factors,
            lastUpdate = System.currentTimeMillis(),
            source = AttentionSource.PERCEPTION
        )
    }
    
    /**
     * Создать кандидата внимания для прерывания
     */
    private fun createInterruptCandidate(interrupt: com.hollowengineai.mod.interruption.InterruptInfo): AttentionCandidate {
        var attentionValue = 0.0
        val factors = mutableMapOf<String, Double>()
        
        // Приоритет прерывания напрямую влияет на внимание
        val priorityFactor = when (interrupt.priority) {
            com.hollowengineai.mod.interruption.InterruptPriority.CRITICAL -> 1.0
            com.hollowengineai.mod.interruption.InterruptPriority.HIGH -> 0.8
            com.hollowengineai.mod.interruption.InterruptPriority.NORMAL -> 0.5
            com.hollowengineai.mod.interruption.InterruptPriority.LOW -> 0.2
        }
        factors["priority"] = priorityFactor
        attentionValue += priorityFactor
        
        // Тип прерывания
        val typeFactor = when (interrupt.type) {
            InterruptType.THREAT_DETECTED -> 0.9
            InterruptType.INTERACTION_REQUEST -> 0.7
            InterruptType.BEING_OBSERVED -> 0.3
            InterruptType.LOW_HEALTH -> 0.8
            else -> 0.4
        }
        factors["type"] = typeFactor * 0.3
        attentionValue += typeFactor * 0.3
        
        // Время с начала прерывания (свежие прерывания важнее)
        val timeSinceStart = System.currentTimeMillis() - interrupt.startTime
        val freshnessFactor = exp(-timeSinceStart / 30000.0) // Экспоненциальное затухание за 30 секунд
        factors["freshness"] = freshnessFactor * 0.2
        attentionValue += freshnessFactor * 0.2
        
        val target = AttentionTarget(
            id = interrupt.id.toString(),
            type = AttentionTargetType.INTERRUPT,
            position = npc.position, // Прерывания обычно связаны с самим НПС
            description = "Interrupt: ${interrupt.reason}"
        )
        
        return AttentionCandidate(
            target = target,
            attentionValue = attentionValue.coerceIn(0.0, 1.0),
            factors = factors,
            lastUpdate = System.currentTimeMillis(),
            source = AttentionSource.INTERRUPTION
        )
    }
    
    /**
     * Добавить кандидатов окружающей среды
     */
    private fun addEnvironmentalCandidates() {
        // Можно добавить внимание к определенным блокам, сущностям, звукам и т.д.
        // Пока оставляем пустым
    }
    
    /**
     * Оценить нужно ли переключить фокус
     */
    private suspend fun evaluateFocusChange() {
        val bestCandidate = findBestAttentionCandidate()
        
        if (bestCandidate == null) {
            // Нет подходящих кандидатов
            if (currentFocus != null) {
                releaseFocus("No suitable targets")
            }
            return
        }
        
        // Проверяем нужно ли переключить фокус
        val shouldSwitch = shouldSwitchFocus(bestCandidate)
        
        if (shouldSwitch) {
            switchFocus(bestCandidate)
        } else {
            // Обновляем силу текущего фокуса
            currentFocus?.let { focus ->
                if (focus.target.id == bestCandidate.target.id) {
                    currentFocus = focus.copy(strength = bestCandidate.attentionValue)
                }
            }
        }
    }
    
    /**
     * Найти лучшего кандидата для внимания
     */
    private fun findBestAttentionCandidate(): AttentionCandidate? {
        return candidateTargets.values
            .filter { it.attentionValue >= config.minAttentionThreshold }
            .maxByOrNull { it.attentionValue }
    }
    
    /**
     * Определить нужно ли переключить фокус
     */
    private fun shouldSwitchFocus(candidate: AttentionCandidate): Boolean {
        val currentFocus = this.currentFocus
        
        // Если нет текущего фокуса, переключаемся
        if (currentFocus == null) {
            return candidate.attentionValue >= config.minAttentionThreshold
        }
        
        // Если это принудительный фокус, не переключаемся
        if (currentFocus.isForced) {
            return false
        }
        
        // Если это тот же объект, не переключаемся
        if (currentFocus.target.id == candidate.target.id) {
            return false
        }
        
        // Переключаемся если новый кандидат значительно лучше
        val switchThreshold = currentFocus.strength + config.switchThreshold
        return candidate.attentionValue > switchThreshold
    }
    
    /**
     * Переключить фокус внимания
     */
    private suspend fun switchFocus(candidate: AttentionCandidate) {
        // Освобождаем текущий фокус
        currentFocus?.let { oldFocus ->
            recordAttentionEvent(
                AttentionEventType.FOCUS_LOST,
                oldFocus.target,
                "Switched to ${candidate.target.description}"
            )
            
            publishAttentionEvent("focus_lost", oldFocus.target, "Switched focus")
        }
        
        // Устанавливаем новый фокус
        val newFocus = AttentionFocus(
            target = candidate.target,
            startTime = System.currentTimeMillis(),
            reason = "Best attention candidate (${String.format("%.2f", candidate.attentionValue)})",
            strength = candidate.attentionValue,
            isForced = false
        )
        
        setFocus(newFocus)
        focusChanges++
        
        recordAttentionEvent(
            AttentionEventType.FOCUS_GAINED,
            candidate.target,
            "Switched focus: ${newFocus.reason}"
        )
        
        publishAttentionEvent("focus_gained", candidate.target, newFocus.reason)
        
        LOGGER.debug("Switched focus to ${candidate.target.description} (${String.format("%.2f", candidate.attentionValue)}) for NPC ${npc.name}")
    }
    
    /**
     * Установить фокус внимания
     */
    private fun setFocus(focus: AttentionFocus) {
        currentFocus = focus
    }
    
    /**
     * Применить модуляторы внимания
     */
    private fun applyModulators(baseValue: Double): Double {
        var modifiedValue = baseValue
        
        // Уровень возбуждения влияет на общую чувствительность к стимулам
        val arousalModifier = 0.5 + arousalLevel * 0.5 // 0.5 - 1.0
        modifiedValue *= arousalModifier
        
        // Стресс повышает внимание к угрозам, но снижает к нейтральным стимулам
        val stressModifier = 1.0 + stressLevel * 0.3 // Небольшое повышение
        modifiedValue *= stressModifier
        
        // Усталость снижает общую внимательность
        val fatigueModifier = 1.0 - fatigueLevel * 0.4 // Снижение до 60%
        modifiedValue *= fatigueModifier
        
        return modifiedValue
    }
    
    /**
     * Обновить модуляторы внимания
     */
    private fun updateAttentionModulators() {
        // Естественное снижение уровней со временем
        val decay = 0.01 // 1% в обновление
        
        arousalLevel = max(0.1, arousalLevel - decay) // Минимальный уровень возбуждения
        stressLevel = max(0.0, stressLevel - decay * 2) // Стресс снижается быстрее
        fatigueLevel = min(1.0, fatigueLevel + decay * 0.5) // Усталость накапливается медленно
        
        // Корректировка на основе активности
        val perceivedPlayers = perceptionSystem.getPerceivedPlayers()
        val activeInterrupts = interruptionSystem.getActiveInterrupts()
        
        // Больше активности = больше возбуждение
        if (perceivedPlayers.isNotEmpty() || activeInterrupts.isNotEmpty()) {
            arousalLevel = min(1.0, arousalLevel + 0.005)
        }
        
        // Угрозы повышают стресс
        val threats = activeInterrupts.filter { 
            it.type in listOf(InterruptType.THREAT_DETECTED, InterruptType.DANGER_IMMINENT)
        }
        if (threats.isNotEmpty()) {
            stressLevel = min(1.0, stressLevel + 0.02 * threats.size)
        }
    }
    
    /**
     * Очистить устаревших кандидатов
     */
    private fun cleanupCandidates(currentTime: Long) {
        val maxAge = 5000L // 5 секунд
        candidateTargets.entries.removeIf { (_, candidate) ->
            currentTime - candidate.lastUpdate > maxAge
        }
    }
    
    /**
     * Проверить принудительный фокус
     */
    private fun checkForcedFocus(currentTime: Long) {
        currentFocus?.let { focus ->
            if (focus.isForced && focus.forcedDuration != null) {
                val elapsed = currentTime - focus.startTime
                if (elapsed >= focus.forcedDuration) {
                    releaseFocus("Forced focus duration expired")
                }
            }
        }
    }
    
    /**
     * Записать событие внимания
     */
    private fun recordAttentionEvent(
        eventType: AttentionEventType,
        target: AttentionTarget,
        description: String
    ) {
        val event = AttentionEvent(
            eventType = eventType,
            targetId = target.id,
            targetType = target.type,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(attentionHistory) {
            attentionHistory.add(event)
            if (attentionHistory.size > 100) {
                attentionHistory.removeAt(0)
            }
        }
    }
    
    /**
     * Опубликовать событие внимания
     */
    private fun publishAttentionEvent(eventType: String, target: AttentionTarget, reason: String) {
        try {
            val event = NPCEvents.customEvent(
                npcId = npc.id,
                npcName = npc.name,
                eventData = mapOf(
                    "eventType" to eventType,
                    "targetId" to target.id,
                    "targetType" to target.type.name,
                    "targetDescription" to target.description,
                    "reason" to reason
                ),
                position = npc.entity.blockPosition()
            )
            
            eventBus.sendEventSync(event)
        } catch (e: Exception) {
            LOGGER.warn("Failed to publish attention event for NPC ${npc.name}", e)
        }
    }
    
    // Геттеры и утилиты
    
    fun getAttentionHistory(): List<AttentionEvent> {
        return synchronized(attentionHistory) {
            attentionHistory.toList()
        }
    }
    
    fun getActiveCandidates(): List<AttentionCandidate> {
        return candidateTargets.values.sortedByDescending { it.attentionValue }
    }
    
    fun getModulators(): AttentionModulators {
        return AttentionModulators(
            arousal = arousalLevel,
            stress = stressLevel,
            fatigue = fatigueLevel
        )
    }
    
    fun getStats(): AttentionStats {
        return AttentionStats(
            focusChanges = focusChanges,
            attentionUpdates = attentionUpdates,
            currentCandidates = candidateTargets.size,
            hasFocus = currentFocus != null,
            focusDuration = currentFocus?.let { System.currentTimeMillis() - it.startTime },
            modulators = getModulators()
        )
    }
    
    fun isHealthy(): Boolean {
        return isRunning && 
               attentionScope.isActive &&
               candidateTargets.size < 50 // Не слишком много кандидатов
    }
}
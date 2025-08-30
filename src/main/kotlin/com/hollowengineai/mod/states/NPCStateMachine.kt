package com.hollowengineai.mod.states

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEvent
import com.hollowengineai.mod.events.NPCEventType
import com.hollowengineai.mod.events.NPCEventBus
import kotlinx.coroutines.*
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Конечный автомат для управления состояниями NPC
 * Поддерживает асинхронные переходы, условия и обработчики событий
 */
class NPCStateMachine(
    private val npc: SmartNPC,
    private val eventBus: NPCEventBus,
    initialState: NPCState = NPCState.IDLE
) {
    private val currentState = AtomicReference(initialState)
    private val stateHistory = mutableListOf<StateTransition>()
    private val stateListeners = ConcurrentHashMap<NPCState, MutableList<StateListener>>()
    private val globalListeners = mutableListOf<StateListener>()
    
    // Корутинный контекст для асинхронных операций
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Таймеры для автоматических переходов
    private var stateTimer: Job? = null
    private var lastStateChange = System.currentTimeMillis()
    
    // Блокировки для предотвращения конфликтующих переходов
    private val transitionMutex = kotlinx.coroutines.sync.Mutex()
    private var isTransitioning = false
    
    init {
        // Подписываемся на события для автоматических переходов
        eventBus.subscribe(npc.getEntity().uuid) { event ->
            handleEventBasedTransition(event)
        }
        
        // Запускаем мониторинг состояния
        startStateMonitoring()
    }
    
    /**
     * Получить текущее состояние
     */
    fun getCurrentState(): NPCState = currentState.get()
    
    /**
     * Проверить, можно ли перейти в указанное состояние
     */
    fun canTransitionTo(newState: NPCState): Boolean {
        val current = getCurrentState()
        return when {
            current == newState -> false // Уже в этом состоянии
            isTransitioning -> false // Уже выполняется переход
            current == NPCState.DEAD -> false // Мертвые NPC не могут менять состояние
            newState == NPCState.DEAD -> true // Всегда можно умереть
            else -> isValidTransition(current, newState)
        }
    }
    
    /**
     * Попытаться перейти в новое состояние
     */
    suspend fun transitionTo(
        newState: NPCState,
        reason: String = "Unknown",
        force: Boolean = false
    ): Boolean {
        return transitionMutex.withLock {
            if (!force && !canTransitionTo(newState)) {
                HollowEngineAIMod.LOGGER.debug("Transition from ${getCurrentState()} to $newState denied for ${npc.getEntity().name.string}")
                return@withLock false
            }
            
            isTransitioning = true
            val oldState = getCurrentState()
            
            try {
                // Выход из старого состояния
                onExitState(oldState)
                
                // Обновляем состояние
                currentState.set(newState)
                lastStateChange = System.currentTimeMillis()
                
                // Вход в новое состояние
                onEnterState(newState)
                
                // Записываем переход в историю
                val transition = StateTransition(
                    from = oldState,
                    to = newState,
                    timestamp = System.currentTimeMillis(),
                    reason = reason
                )
                stateHistory.add(transition)
                
                // Ограничиваем размер истории
                if (stateHistory.size > 100) {
                    stateHistory.removeFirstOrNull()
                }
                
                // Уведомляем слушателей
                notifyStateChange(oldState, newState, reason)
                
                // Отправляем событие
                eventBus.publishEvent(NPCEvent.createActionEvent(
                    npc = npc.getEntity(),
                    action = "state_change",
                    details = mapOf(
                        "from" to oldState.name,
                        "to" to newState.name,
                        "reason" to reason
                    )
                ))
                
                HollowEngineAIMod.LOGGER.debug("${npc.getEntity().name.string} transitioned from $oldState to $newState: $reason")
                return@withLock true
                
            } catch (e: Exception) {
                HollowEngineAIMod.LOGGER.error("Failed to transition from $oldState to $newState for ${npc.getEntity().name.string}", e)
                return@withLock false
            } finally {
                isTransitioning = false
            }
        }
    }
    
    /**
     * Принудительный переход в состояние (игнорирует ограничения)
     */
    suspend fun forceTransitionTo(newState: NPCState, reason: String = "Forced") {
        transitionTo(newState, reason, force = true)
    }
    
    /**
     * Получить историю переходов состояний
     */
    fun getStateHistory(): List<StateTransition> = stateHistory.toList()
    
    /**
     * Получить время в текущем состоянии (в миллисекундах)
     */
    fun getTimeInCurrentState(): Long = System.currentTimeMillis() - lastStateChange
    
    /**
     * Добавить слушатель для конкретного состояния
     */
    fun addStateListener(state: NPCState, listener: StateListener) {
        stateListeners.computeIfAbsent(state) { mutableListOf() }.add(listener)
    }
    
    /**
     * Добавить глобальный слушатель (срабатывает на любое изменение состояния)
     */
    fun addGlobalListener(listener: StateListener) {
        globalListeners.add(listener)
    }
    
    /**
     * Проверка валидности перехода между состояниями
     */
    private fun isValidTransition(from: NPCState, to: NPCState): Boolean {
        return when (from) {
            NPCState.IDLE -> true // Из IDLE можно перейти в любое состояние
            NPCState.ACTIVE -> to != NPCState.SLEEPING // Активный NPC не может сразу заснуть
            NPCState.TALKING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FIGHTING, NPCState.FLEEING)
            NPCState.FIGHTING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FLEEING, NPCState.DEAD)
            NPCState.TRADING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.TALKING)
            NPCState.CRAFTING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE)
            NPCState.PATROLLING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FIGHTING, NPCState.TALKING)
            NPCState.SLEEPING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FIGHTING)
            NPCState.FOLLOWING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FIGHTING, NPCState.FLEEING)
            NPCState.FLEEING -> to in listOf(NPCState.IDLE, NPCState.ACTIVE, NPCState.FIGHTING, NPCState.DEAD)
            NPCState.DEAD -> false // Из мертвого состояния нельзя выйти
        }
    }
    
    /**
     * Обработка входа в новое состояние
     */
    private suspend fun onEnterState(state: NPCState) {
        // Устанавливаем автоматические таймеры для некоторых состояний
        when (state) {
            NPCState.SLEEPING -> {
                // Автоматическое пробуждение через 8-12 часов игрового времени
                scheduleStateTransition(NPCState.IDLE, (8000..12000).random().milliseconds, "Natural awakening")
            }
            NPCState.CRAFTING -> {
                // Автоматическое завершение крафта через 30-60 секунд
                scheduleStateTransition(NPCState.IDLE, (30000..60000).random().milliseconds, "Crafting complete")
            }
            NPCState.FLEEING -> {
                // Прекращение бегства через 10-30 секунд
                scheduleStateTransition(NPCState.IDLE, (10000..30000).random().milliseconds, "Fled to safety")
            }
            else -> {
                // Для других состояний таймер не нужен
            }
        }
        
        // Выполняем специфичную для состояния логику
        when (state) {
            NPCState.FIGHTING -> {
                // Повышаем бдительность и агрессию
                npc.setEmotion(EmotionalState.ANGRY)
            }
            NPCState.TALKING -> {
                // Повышаем социальность
                npc.setEmotion(EmotionalState.HAPPY)
            }
            NPCState.SLEEPING -> {
                // Восстанавливаем энергию и снижаем стресс
                npc.setEmotion(EmotionalState.CONTENT)
            }
            else -> {
                // Базовые изменения для других состояний
            }
        }
    }
    
    /**
     * Обработка выхода из состояния
     */
    private suspend fun onExitState(state: NPCState) {
        // Отменяем автоматические таймеры
        stateTimer?.cancel()
        
        // Выполняем cleanup специфичный для состояния
        when (state) {
            NPCState.FIGHTING -> {
                // Постепенно снижаем агрессию
                npc.setEmotion(EmotionalState.NEUTRAL)
            }
            NPCState.CRAFTING -> {
                // Завершаем текущий процесс крафта
                // Здесь можно добавить логику завершения крафта
            }
            else -> {
                // Для других состояний специальный cleanup не нужен
            }
        }
    }
    
    /**
     * Запланировать автоматический переход состояния
     */
    private fun scheduleStateTransition(
        targetState: NPCState,
        delay: kotlin.time.Duration,
        reason: String
    ) {
        stateTimer?.cancel()
        stateTimer = scope.launch {
            delay(delay)
            if (getCurrentState() != targetState) {
                transitionTo(targetState, reason)
            }
        }
    }
    
    /**
     * Обработка событий для автоматических переходов
     */
    private suspend fun handleEventBasedTransition(event: NPCEvent) {
        when (event.type) {
            NPCEventType.NPC_ATTACKED -> {
                if (event.data["action"] == "attack" && getCurrentState() != NPCState.FIGHTING) {
                    transitionTo(NPCState.FIGHTING, "Combat detected")
                }
            }
            NPCEventType.NPC_SPOKE -> {
                if (event.data["type"] == "speak" && getCurrentState() == NPCState.IDLE) {
                    transitionTo(NPCState.TALKING, "Started conversation")
                }
            }
            NPCEvent.Type.TRADING -> {
                if (event.data["action"] == "start_trade" && getCurrentState() != NPCState.TRADING) {
                    transitionTo(NPCState.TRADING, "Trade initiated")
                }
            }
            else -> {
                // Другие типы событий пока не обрабатываем
            }
        }
    }
    
    /**
     * Мониторинг состояния для автоматических переходов
     */
    private fun startStateMonitoring() {
        scope.launch {
            while (true) {
                delay(1000) // Проверяем каждую секунду
                
                try {
                    checkForAutomaticTransitions()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in state monitoring for ${npc.getEntity().name.string}", e)
                }
            }
        }
    }
    
    /**
     * Проверка условий для автоматических переходов
     */
    private suspend fun checkForAutomaticTransitions() {
        val current = getCurrentState()
        val timeInState = getTimeInCurrentState()
        
        when (current) {
            NPCState.IDLE -> {
                // Если NPC долго ничего не делает, он может начать патрулирование
                if (timeInState > 60000 && npc.personalityTraits.getOrDefault("activity_level", 0.5f) > 0.7f) {
                    transitionTo(NPCState.PATROLLING, "Decided to patrol")
                }
                
                // Проверяем усталость
                if ((npc.emotionalState == EmotionalState.TIRED || npc.emotionalState == EmotionalState.BORED) && shouldSleep()) {
                    transitionTo(NPCState.SLEEPING, "Feeling tired")
                }
            }
            
            NPCState.ACTIVE -> {
                // Если нет активных задач, возвращаемся в IDLE
                if (timeInState > 30000 && !hasActiveTasks()) {
                    transitionTo(NPCState.IDLE, "No active tasks")
                }
            }
            
            NPCState.TALKING -> {
                // Если разговор длится слишком долго без игроков рядом
                if (timeInState > 120000 && !hasNearbyPlayers()) {
                    transitionTo(NPCState.IDLE, "Conversation ended")
                }
            }
            
            NPCState.PATROLLING -> {
                // Случайное завершение патрулирования
                if (timeInState > 180000 && Math.random() < 0.3) {
                    transitionTo(NPCState.IDLE, "Patrol complete")
                }
            }
            
            else -> {
                // Для других состояний дополнительные проверки не нужны
            }
        }
    }
    
    /**
     * Проверка, должен ли NPC спать
     */
    private fun shouldSleep(): Boolean {
        // Упрощенная логика - спим ночью если уровень активности низкий
        val level = npc.getEntity().level
        val dayTime = level.dayTime % 24000
        val isNight = dayTime > 13000 && dayTime < 23000
        
        return isNight && npc.personalityTraits.getOrDefault("night_owl", 0.0f) < 0.5f
    }
    
    /**
     * Проверка наличия активных задач
     */
    private fun hasActiveTasks(): Boolean {
        // Здесь можно интегрироваться с системой задач
        return false // Пока всегда возвращаем false
    }
    
    /**
     * Проверка наличия игроков поблизости
     */
    private fun hasNearbyPlayers(): Boolean {
        val level = npc.getEntity().level
        val players = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(10.0))
        return players.isNotEmpty()
    }
    
    /**
     * Уведомление слушателей об изменении состояния
     */
    private fun notifyStateChange(from: NPCState, to: NPCState, reason: String) {
        // Уведомляем слушателей конкретного состояния
        stateListeners[to]?.forEach { listener ->
            try {
                listener.onStateEntered(to, from, reason)
            } catch (e: Exception) {
                HollowEngineAIMod.LOGGER.error("Error in state listener", e)
            }
        }
        
        stateListeners[from]?.forEach { listener ->
            try {
                listener.onStateExited(from, to, reason)
            } catch (e: Exception) {
                HollowEngineAIMod.LOGGER.error("Error in state listener", e)
            }
        }
        
        // Уведомляем глобальных слушателей
        globalListeners.forEach { listener ->
            try {
                listener.onStateChanged(from, to, reason)
            } catch (e: Exception) {
                HollowEngineAIMod.LOGGER.error("Error in global state listener", e)
            }
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun cleanup() {
        stateTimer?.cancel()
        scope.cancel()
        stateListeners.clear()
        globalListeners.clear()
    }
}

/**
 * Перечисление всех возможных состояний NPC
 */
enum class NPCState {
    IDLE,        // Ничего не делает, ждет
    ACTIVE,      // Активно выполняет задачи
    TALKING,     // Разговаривает с игроком или другим NPC
    FIGHTING,    // Сражается
    TRADING,     // Торгует
    CRAFTING,    // Создает предметы
    PATROLLING,  // Патрулирует территорию
    SLEEPING,    // Спит
    FOLLOWING,   // Следует за кем-то
    FLEEING,     // Убегает от опасности
    DEAD         // Мертв
}

/**
 * Информация о переходе между состояниями
 */
data class StateTransition(
    val from: NPCState,
    val to: NPCState,
    val timestamp: Long,
    val reason: String
)

/**
 * Интерфейс для слушателей изменений состояния
 */
interface StateListener {
    /**
     * Вызывается при входе в состояние
     */
    fun onStateEntered(state: NPCState, from: NPCState, reason: String) {}
    
    /**
     * Вызывается при выходе из состояния
     */
    fun onStateExited(state: NPCState, to: NPCState, reason: String) {}
    
    /**
     * Вызывается при любом изменении состояния
     */
    fun onStateChanged(from: NPCState, to: NPCState, reason: String) {}
}
package com.hollowengineai.mod.async

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Система асинхронных действий НПС
 * 
 * Возможности:
 * - Выполнение долгосрочных действий в фоне
 * - Приоритизация и очередь действий
 * - Прерывание текущих действий
 * - Композитные действия (последовательности)
 * - Условные действия с проверками
 * - Повторяющиеся действия
 * - Интеграция с ИИ системой
 */
class AsyncActionSystem(
    private val npc: SmartNPC,
    private val eventBus: NPCEventBusImpl
) {
    companion object {
        private val LOGGER = LogManager.getLogger(AsyncActionSystem::class.java)
        private const val ACTION_QUEUE_SIZE = 100
        private const val MAX_CONCURRENT_ACTIONS = 5
    }
    
    // Система корутин
    private val actionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Очередь действий
    private val actionQueue = Channel<QueuedAction>(ACTION_QUEUE_SIZE)
    private val activeActions = ConcurrentHashMap<UUID, ActiveAction>()
    private val completedActions = mutableListOf<CompletedAction>()
    
    // Состояние системы
    private var isRunning = false
    private val actionIdGenerator = AtomicLong(0)
    
    // Статистика
    private var actionsExecuted = 0L
    private var actionsCancelled = 0L
    private var actionsCompleted = 0L
    private var actionsFailed = 0L
    
    /**
     * Запустить систему асинхронных действий
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        // Запускаем обработчик очереди действий
        actionScope.launch {
            try {
                actionQueue.consumeEach { queuedAction ->
                    processQueuedAction(queuedAction)
                }
            } catch (e: Exception) {
                LOGGER.error("Error in action queue processing for NPC ${npc.name}", e)
            }
        }
        
        LOGGER.debug("AsyncActionSystem started for NPC ${npc.name}")
    }
    
    /**
     * Остановить систему действий
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        // Отменяем все активные действия
        cancelAllActions("System shutdown")
        
        // Закрываем очередь и корутины
        actionQueue.close()
        actionScope.cancel()
        
        LOGGER.debug("AsyncActionSystem stopped for NPC ${npc.name}")
    }
    
    /**
     * Поставить действие в очередь
     */
    suspend fun queueAction(action: NPCAction, priority: ActionPriority = ActionPriority.NORMAL): UUID {
        val actionId = UUID.randomUUID()
        val queuedAction = QueuedAction(
            id = actionId,
            action = action,
            priority = priority,
            queueTime = System.currentTimeMillis()
        )
        
        if (!actionQueue.trySend(queuedAction).isSuccess) {
            LOGGER.warn("Action queue full for NPC ${npc.name}, dropping action: ${action.name}")
            throw ActionQueueFullException("Action queue is full")
        }
        
        LOGGER.debug("Queued action ${action.name} with priority $priority for NPC ${npc.name}")
        return actionId
    }
    
    /**
     * Поставить действие в очередь синхронно
     */
    fun queueActionSync(action: NPCAction, priority: ActionPriority = ActionPriority.NORMAL): UUID {
        return runBlocking {
            queueAction(action, priority)
        }
    }
    
    /**
     * Отменить действие по ID
     */
    fun cancelAction(actionId: UUID, reason: String = "Cancelled by request"): Boolean {
        val activeAction = activeActions[actionId]
        if (activeAction != null) {
            activeAction.job.cancel(CancellationException(reason))
            activeActions.remove(actionId)
            actionsCancelled++
            
            LOGGER.debug("Cancelled action $actionId for NPC ${npc.name}: $reason")
            return true
        }
        return false
    }
    
    /**
     * Отменить все действия
     */
    fun cancelAllActions(reason: String = "Cancelled all actions") {
        val cancelled = activeActions.keys.toList()
        cancelled.forEach { actionId ->
            cancelAction(actionId, reason)
        }
        LOGGER.debug("Cancelled ${cancelled.size} actions for NPC ${npc.name}: $reason")
    }
    
    /**
     * Отменить действия с низким приоритетом
     */
    fun cancelLowPriorityActions(higherThan: ActionPriority = ActionPriority.NORMAL): Int {
        val toCancel = activeActions.values.filter { 
            it.priority.ordinal <= higherThan.ordinal 
        }.map { it.id }
        
        toCancel.forEach { actionId ->
            cancelAction(actionId, "Cancelled for higher priority action")
        }
        
        return toCancel.size
    }
    
    /**
     * Прервать текущие действия и выполнить экстренное действие
     */
    suspend fun executeUrgentAction(action: NPCAction): UUID {
        // Отменяем действия с низким приоритетом
        cancelLowPriorityActions(ActionPriority.HIGH)
        
        // Выполняем экстренное действие с максимальным приоритетом
        return queueAction(action, ActionPriority.CRITICAL)
    }
    
    /**
     * Обработать действие из очереди
     */
    private suspend fun processQueuedAction(queuedAction: QueuedAction) {
        // Проверяем лимит одновременных действий
        if (activeActions.size >= MAX_CONCURRENT_ACTIONS) {
            // Отменяем действие с наименьшим приоритетом
            val lowestPriorityAction = activeActions.values.minByOrNull { it.priority.ordinal }
            if (lowestPriorityAction != null && lowestPriorityAction.priority.ordinal < queuedAction.priority.ordinal) {
                cancelAction(lowestPriorityAction.id, "Replaced by higher priority action")
            } else {
                LOGGER.warn("Cannot process action ${queuedAction.action.name}, too many active actions")
                return
            }
        }
        
        // Запускаем действие
        val job = actionScope.launch {
            executeAction(queuedAction)
        }
        
        val activeAction = ActiveAction(
            id = queuedAction.id,
            action = queuedAction.action,
            priority = queuedAction.priority,
            job = job,
            startTime = System.currentTimeMillis()
        )
        
        activeActions[queuedAction.id] = activeAction
        actionsExecuted++
        
        LOGGER.debug("Started executing action ${queuedAction.action.name} for NPC ${npc.name}")
    }
    
    /**
     * Выполнить действие
     */
    private suspend fun executeAction(queuedAction: QueuedAction) {
        val action = queuedAction.action
        val startTime = System.currentTimeMillis()
        
        try {
            // Проверяем предусловия
            if (!action.canExecute(npc)) {
                LOGGER.debug("Action ${action.name} preconditions failed for NPC ${npc.name}")
                recordActionCompletion(queuedAction.id, ActionResult.FAILED, "Preconditions not met")
                return
            }
            
            // Уведомляем о начале действия
            publishActionEvent("action_started", action)
            
            // Выполняем действие
            val result = action.execute(npc)
            
            // Обрабатываем результат
            when (result.status) {
                ActionStatus.COMPLETED -> {
                    actionsCompleted++
                    publishActionEvent("action_completed", action)
                    LOGGER.debug("Action ${action.name} completed for NPC ${npc.name}")
                }
                ActionStatus.FAILED -> {
                    actionsFailed++
                    publishActionEvent("action_failed", action)
                    LOGGER.warn("Action ${action.name} failed for NPC ${npc.name}: ${result.message}")
                }
                ActionStatus.CANCELLED -> {
                    actionsCancelled++
                    publishActionEvent("action_cancelled", action)
                    LOGGER.debug("Action ${action.name} was cancelled for NPC ${npc.name}")
                }
                ActionStatus.RUNNING -> {
                    LOGGER.warn("Action ${action.name} returned RUNNING status, this should not happen")
                }
            }
            
            recordActionCompletion(queuedAction.id, result.status.toActionResult(), result.message)
            
        } catch (e: CancellationException) {
            actionsCancelled++
            publishActionEvent("action_cancelled", action)
            recordActionCompletion(queuedAction.id, ActionResult.CANCELLED, e.message ?: "Cancelled")
            LOGGER.debug("Action ${action.name} was cancelled for NPC ${npc.name}")
        } catch (e: Exception) {
            actionsFailed++
            publishActionEvent("action_failed", action)
            recordActionCompletion(queuedAction.id, ActionResult.FAILED, e.message ?: "Unknown error")
            LOGGER.error("Action ${action.name} threw exception for NPC ${npc.name}", e)
        } finally {
            activeActions.remove(queuedAction.id)
        }
    }
    
    /**
     * Записать завершение действия
     */
    private fun recordActionCompletion(actionId: UUID, result: ActionResult, message: String?) {
        val completion = CompletedAction(
            id = actionId,
            result = result,
            endTime = System.currentTimeMillis(),
            message = message
        )
        
        synchronized(completedActions) {
            completedActions.add(completion)
            
            // Ограничиваем размер истории
            if (completedActions.size > 100) {
                completedActions.removeAt(0)
            }
        }
    }
    
    /**
     * Опубликовать событие действия
     */
    private fun publishActionEvent(eventType: String, action: NPCAction) {
        try {
            val event = NPCEvents.customEvent(
                npcId = npc.id,
                npcName = npc.name,
                eventData = mapOf(
                    "eventType" to eventType,
                    "actionName" to action.name,
                    "actionType" to action.type.name,
                    "timestamp" to System.currentTimeMillis()
                ),
                position = npc.getEntity().blockPosition()
            )
            
            eventBus.sendEventSync(event)
        } catch (e: Exception) {
            LOGGER.warn("Failed to publish action event for NPC ${npc.name}", e)
        }
    }
    
    // Геттеры для информации о состоянии системы
    
    /**
     * Получить список активных действий
     */
    fun getActiveActions(): List<ActionInfo> {
        return activeActions.values.map { activeAction ->
            ActionInfo(
                id = activeAction.id,
                name = activeAction.action.name,
                type = activeAction.action.type,
                priority = activeAction.priority,
                startTime = activeAction.startTime,
                isRunning = !activeAction.job.isCompleted
            )
        }
    }
    
    /**
     * Получить информацию о конкретном действии
     */
    fun getActionInfo(actionId: UUID): ActionInfo? {
        val activeAction = activeActions[actionId] ?: return null
        return ActionInfo(
            id = activeAction.id,
            name = activeAction.action.name,
            type = activeAction.action.type,
            priority = activeAction.priority,
            startTime = activeAction.startTime,
            isRunning = !activeAction.job.isCompleted
        )
    }
    
    /**
     * Проверить, выполняется ли действие
     */
    fun isActionRunning(actionId: UUID): Boolean {
        return activeActions[actionId]?.job?.isActive == true
    }
    
    /**
     * Получить количество активных действий
     */
    fun getActiveActionCount(): Int = activeActions.size
    
    /**
     * Получить статистику системы
     */
    fun getStats(): ActionSystemStats {
        return ActionSystemStats(
            actionsExecuted = actionsExecuted,
            actionsCompleted = actionsCompleted,
            actionsCancelled = actionsCancelled,
            actionsFailed = actionsFailed,
            activeCount = activeActions.size,
            queueSize = if (actionQueue.isEmpty) 0 else -1 // Приблизительно
        )
    }
    
    /**
     * Получить историю завершенных действий
     */
    fun getCompletedActions(): List<CompletedAction> {
        return synchronized(completedActions) {
            completedActions.toList()
        }
    }
    
    /**
     * Проверить здоровье системы
     */
    fun isHealthy(): Boolean {
        return isRunning && 
               actionScope.isActive && 
               !actionQueue.isClosedForSend &&
               activeActions.size <= MAX_CONCURRENT_ACTIONS
    }
    
    /**
     * Очистить историю завершенных действий
     */
    fun clearHistory() {
        synchronized(completedActions) {
            completedActions.clear()
        }
    }
}

/**
 * Действие в очереди
 */
private data class QueuedAction(
    val id: UUID,
    val action: NPCAction,
    val priority: ActionPriority,
    val queueTime: Long
)

/**
 * Активное действие
 */
private data class ActiveAction(
    val id: UUID,
    val action: NPCAction,
    val priority: ActionPriority,
    val job: Job,
    val startTime: Long
)

/**
 * Завершенное действие
 */
data class CompletedAction(
    val id: UUID,
    val result: ActionResult,
    val endTime: Long,
    val message: String?
)

/**
 * Информация о действии
 */
data class ActionInfo(
    val id: UUID,
    val name: String,
    val type: ActionType,
    val priority: ActionPriority,
    val startTime: Long,
    val isRunning: Boolean
)

/**
 * Статистика системы действий
 */
data class ActionSystemStats(
    val actionsExecuted: Long,
    val actionsCompleted: Long,
    val actionsCancelled: Long,
    val actionsFailed: Long,
    val activeCount: Int,
    val queueSize: Int
)

/**
 * Приоритет действий
 */
enum class ActionPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * Результат выполнения действия
 */
enum class ActionResult {
    COMPLETED, FAILED, CANCELLED
}

/**
 * Исключение при переполнении очереди действий
 */
class ActionQueueFullException(message: String) : Exception(message)

// Расширения для конвертации
private fun ActionStatus.toActionResult(): ActionResult {
    return when (this) {
        ActionStatus.COMPLETED -> ActionResult.COMPLETED
        ActionStatus.FAILED -> ActionResult.FAILED
        ActionStatus.CANCELLED -> ActionResult.CANCELLED
        ActionStatus.RUNNING -> ActionResult.FAILED // Не должно происходить
    }
}
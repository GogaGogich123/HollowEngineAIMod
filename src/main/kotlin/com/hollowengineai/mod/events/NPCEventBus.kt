package com.hollowengineai.mod.events

import com.hollowengineai.mod.core.SmartNPC
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Система событий для взаимодействия между AI NPC
 * 
 * Поддерживает:
 * - Асинхронную отправку и обработку событий
 * - Подписки на типы событий
 * - Фильтрацию по дистанции и условиям
 * - Приоритизацию событий
 */
class NPCEventBusImpl {
    companion object {
        private val LOGGER = LogManager.getLogger(NPCEventBusImpl::class.java)
        private const val EVENT_QUEUE_SIZE = 1000
        private const val MAX_DISTANCE_GLOBAL = 500.0
    }
    
    // Канал для обработки событий
    private val eventChannel = Channel<NPCEvent>(EVENT_QUEUE_SIZE)
    
    // Подписчики на события
    private val subscribers = ConcurrentHashMap<NPCEventType, MutableSet<NPCEventSubscriber>>()
    
    // Scope для корутин
    private val eventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Статистика
    private var eventsProcessed = 0L
    private var eventsDropped = 0L
    
    /**
     * Запустить систему обработки событий
     */
    fun start() {
        eventScope.launch {
            try {
                eventChannel.consumeEach { event ->
                    processEvent(event)
                }
            } catch (e: Exception) {
                LOGGER.error("Error in event processing loop", e)
            }
        }
        
        LOGGER.info("NPC EventBus started")
    }
    
    /**
     * Остановить систему событий
     */
    fun stop() {
        eventChannel.close()
        eventScope.cancel()
        LOGGER.info("NPC EventBus stopped")
    }
    
    /**
     * Отправить событие
     */
    suspend fun sendEvent(event: NPCEvent) {
        try {
            if (!eventChannel.trySend(event).isSuccess) {
                eventsDropped++
                if (eventsDropped % 100 == 0L) {
                    LOGGER.warn("Event queue full, dropping events (total dropped: $eventsDropped)")
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to send event: $event", e)
        }
    }
    
    /**
     * Отправить событие синхронно
     */
    fun sendEventSync(event: NPCEvent) {
        eventScope.launch {
            sendEvent(event)
        }
    }
    
    /**
     * Подписаться на тип событий
     */
    fun subscribe(eventType: NPCEventType, subscriber: NPCEventSubscriber) {
        subscribers.computeIfAbsent(eventType) { ConcurrentHashMap.newKeySet() }.add(subscriber)
        LOGGER.debug("Added subscriber for event type: $eventType")
    }
    
    /**
     * Отписаться от событий
     */
    fun unsubscribe(eventType: NPCEventType, subscriber: NPCEventSubscriber) {
        subscribers[eventType]?.remove(subscriber)
        LOGGER.debug("Removed subscriber for event type: $eventType")
    }
    
    /**
     * Отписаться NPC от всех событий
     */
    fun unsubscribeAll(npcId: UUID) {
        subscribers.values.forEach { subscriberSet ->
            subscriberSet.removeIf { it.npcId == npcId }
        }
        LOGGER.debug("Unsubscribed NPC $npcId from all events")
    }
    
    /**
     * Обработать событие
     */
    private suspend fun processEvent(event: NPCEvent) {
        try {
            eventsProcessed++
            
            val eventSubscribers = subscribers[event.type] ?: return
            
            // Фильтруем подписчиков по дистанции и условиям
            val validSubscribers = eventSubscribers.filter { subscriber ->
                isSubscriberValid(subscriber, event)
            }
            
            if (validSubscribers.isEmpty()) return
            
            // Обрабатываем событие для каждого подписчика
            validSubscribers.forEach { subscriber ->
                try {
                    subscriber.onEvent(event)
                } catch (e: Exception) {
                    LOGGER.error("Subscriber failed to process event", e)
                }
            }
            
            LOGGER.debug("Processed event ${event.type} for ${validSubscribers.size} subscribers")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to process event: $event", e)
        }
    }
    
    /**
     * Проверить валидность подписчика для события
     */
    private fun isSubscriberValid(subscriber: NPCEventSubscriber, event: NPCEvent): Boolean {
        // Проверяем дистанцию если указана позиция
        if (event.position != null && subscriber.maxDistance != null) {
            val distance = subscriber.npc?.position?.distSqr(event.position) ?: Double.MAX_VALUE
            if (distance > subscriber.maxDistance * subscriber.maxDistance) {
                return false
            }
        }
        
        // Проверяем кастомный фильтр
        if (subscriber.filter != null && !subscriber.filter.invoke(event)) {
            return false
        }
        
        // Проверяем что NPC активен
        return subscriber.npc?.isActive == true
    }
    
    /**
     * Получить статистику событий
     */
    fun getStats(): EventBusStats {
        return EventBusStats(
            eventsProcessed = eventsProcessed,
            eventsDropped = eventsDropped,
            queueSize = eventChannel.isEmpty.let { if (it) 0 else -1 }, // Приблизительно
            subscribersCount = subscribers.values.sumOf { it.size }
        )
    }
    
    /**
     * Получить полную отладочную информацию о EventBus
     */
    fun getDebugInfo(): String {
        val stats = getStats()
        val sb = StringBuilder()
        
        sb.appendLine("NPCEventBus Debug Info:")
        sb.appendLine("=======================")
        sb.appendLine("Events Processed: ${stats.eventsProcessed}")
        sb.appendLine("Events Dropped: ${stats.eventsDropped}")
        sb.appendLine("Current Queue Size: ${stats.queueSize}")
        sb.appendLine("Total Subscribers: ${stats.subscribersCount}")
        sb.appendLine("Drop Rate: ${if (stats.eventsProcessed > 0) String.format("%.2f%%", stats.eventsDropped.toDouble() / (stats.eventsProcessed + stats.eventsDropped) * 100) else "0.00%"}")
        sb.appendLine()
        
        sb.appendLine("Event Channel Status:")
        sb.appendLine("--------------------")
        sb.appendLine("Channel Closed: ${eventChannel.isClosedForSend}")
        sb.appendLine("Channel Full: ${!eventChannel.trySend(Unit).isSuccess}")
        sb.appendLine("Max Queue Size: $EVENT_QUEUE_SIZE")
        sb.appendLine()
        
        sb.appendLine("Scope Status:")
        sb.appendLine("-------------")
        sb.appendLine("Event Scope Active: ${eventScope.isActive}")
        sb.appendLine("Event Scope Cancelled: ${eventScope.coroutineContext[Job]?.isCancelled ?: false}")
        sb.appendLine()
        
        sb.appendLine("Subscribers by Event Type:")
        sb.appendLine("---------------------------")
        if (subscribers.isNotEmpty()) {
            NPCEventType.values().forEach { eventType ->
                val count = subscribers[eventType]?.size ?: 0
                if (count > 0) {
                    sb.appendLine("  $eventType: $count subscribers")
                    
                    // Показываем некоторую дополнительную информацию
                    subscribers[eventType]?.take(3)?.forEach { subscriber -> // ограничиваем 3-мя для компактности
                        val npcName = subscriber.npc?.name ?: "Unknown"
                        val distance = subscriber.maxDistance?.toString() ?: "No limit"
                        val hasFilter = if (subscriber.filter != null) "Yes" else "No"
                        sb.appendLine("    - NPC: $npcName, Distance: $distance, Filter: $hasFilter")
                    }
                    if ((subscribers[eventType]?.size ?: 0) > 3) {
                        sb.appendLine("    ... and ${(subscribers[eventType]?.size ?: 0) - 3} more")
                    }
                }
            }
        } else {
            sb.appendLine("  No subscribers found")
        }
        
        sb.appendLine()
        sb.appendLine("Performance Metrics:")
        sb.appendLine("--------------------")
        sb.appendLine("Max Distance (Global): $MAX_DISTANCE_GLOBAL")
        
        return sb.toString()
    }
    
    /**
     * Получить список всех типов событий с подписчиками
     */
    fun getSubscribersByType(): Map<NPCEventType, Int> {
        return NPCEventType.values().associateWith { eventType ->
            subscribers[eventType]?.size ?: 0
        }
    }
    
    /**
     * Проверить здоровье EventBus
     */
    fun isHealthy(): Boolean {
        return eventScope.isActive && 
               !eventChannel.isClosedForSend &&
               getStats().eventsDropped < 1000L // Порог потерянных событий
    }
    
    /**
     * Принудительно очистить очередь событий (для отладки)
     */
    suspend fun clearEventQueue() {
        // Читаем все оставшиеся события из канала
        var cleared = 0
        try {
            while (true) {
                if (eventChannel.tryReceive().isSuccess) {
                    cleared++
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Error clearing event queue", e)
        }
        
        if (cleared > 0) {
            LOGGER.info("Cleared $cleared events from queue")
        }
    }
    
    /**
     * Отправить тестовое событие (для отладки)
     */
    suspend fun sendTestEvent() {
        val testEvent = NPCEvent(
            type = NPCEventType.CUSTOM_EVENT,
            sourceNpcId = UUID.randomUUID(),
            sourceNpcName = "TestNPC",
            data = mapOf("test" to true, "timestamp" to System.currentTimeMillis()),
            priority = EventPriority.LOW
        )
        
        sendEvent(testEvent)
        LOGGER.info("Test event sent")
    }
    
    /**
     * Инициализация EventBus (для API совместимости)
     */
    fun initialize() {
        start()
    }
    
    /**
     * Остановка EventBus (для API совместимости)
     */
    fun shutdown() {
        stop()
    }
    
    /**
     * Сбросить статистику
     */
    fun resetStats() {
        eventsProcessed = 0L
        eventsDropped = 0L
    }
}

// Глобальный экземпляр EventBus для совместимости с object-синтаксисом
object NPCEventBus {
    val instance = NPCEventBusImpl()
    
    fun initialize() = instance.initialize()
    fun start() = instance.start()
    fun stop() = instance.stop()
    fun shutdown() = instance.shutdown()
    fun getDebugInfo(): String = instance.getDebugInfo()
    fun getStats() = instance.getStats()
    fun resetStats() = instance.resetStats()
    fun isHealthy() = instance.isHealthy()
    
    suspend fun sendEvent(event: NPCEvent) = instance.sendEvent(event)
    fun sendEventSync(event: NPCEvent) = instance.sendEventSync(event)
    suspend fun publishEvent(event: NPCEvent) = instance.sendEvent(event)
    
    fun subscribe(eventType: NPCEventType, subscriber: NPCEventSubscriber) = instance.subscribe(eventType, subscriber)
    fun subscribe(handler: suspend (NPCEvent) -> Unit) {
        // Подписка на все типы событий
        NPCEventType.values().forEach { eventType ->
            instance.subscribe(eventType, object : NPCEventSubscriber {
                override suspend fun onEvent(event: NPCEvent) = handler(event)
            })
        }
    }
    fun unsubscribe(eventType: NPCEventType, subscriber: NPCEventSubscriber) = instance.unsubscribe(eventType, subscriber)
    fun unsubscribeAll(npcId: UUID) = instance.unsubscribeAll(npcId)
    
    suspend fun clearEventQueue() = instance.clearEventQueue()
    suspend fun sendTestEvent() = instance.sendTestEvent()
}

/**
 * Событие NPC
 */
data class NPCEvent(
    val type: NPCEventType,
    val sourceNpcId: UUID,
    val sourceNpcName: String,
    val data: Map<String, Any>,
    val position: net.minecraft.core.BlockPos? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: EventPriority = EventPriority.NORMAL
)

/**
 * Типы событий NPC
 */
enum class NPCEventType {
    // Коммуникация
    NPC_SPOKE,
    NPC_SHOUTED,
    NPC_WHISPERED,
    
    // Действия
    NPC_MOVED,
    NPC_ATTACKED,
    NPC_DIED,
    NPC_SPAWNED,
    
    // Эмоции
    EMOTION_CHANGED,
    RELATIONSHIP_CHANGED,
    
    // Торговля
    TRADE_OFFER,
    TRADE_COMPLETED,
    
    // Квесты
    QUEST_GIVEN,
    QUEST_COMPLETED,
    QUEST_FAILED,
    
    // Группы
    GROUP_JOINED,
    GROUP_LEFT,
    GROUP_DISBANDED,
    
    // Кастомные
    CUSTOM_EVENT
}

/**
 * Приоритет событий
 */
enum class EventPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * Подписчик на события
 */
interface NPCEventSubscriber {
    val npcId: UUID
    val npc: SmartNPC?
    val maxDistance: Double? // null = без ограничений
    val filter: ((NPCEvent) -> Boolean)? // дополнительный фильтр
    
    suspend fun onEvent(event: NPCEvent)
}

/**
 * Простая реализация подписчика
 */
class SimpleNPCEventSubscriber(
    override val npcId: UUID,
    override val npc: SmartNPC?,
    override val maxDistance: Double? = null,
    override val filter: ((NPCEvent) -> Boolean)? = null,
    private val handler: suspend (NPCEvent) -> Unit
) : NPCEventSubscriber {
    
    override suspend fun onEvent(event: NPCEvent) {
        handler(event)
    }
}

/**
 * Статистика EventBus
 */
data class EventBusStats(
    val eventsProcessed: Long,
    val eventsDropped: Long,
    val queueSize: Int,
    val subscribersCount: Int
)

/**
 * Утилиты для создания событий
 */
object NPCEvents {
    
    fun npcSpoke(npcId: UUID, npcName: String, message: String, position: net.minecraft.core.BlockPos) = NPCEvent(
        type = NPCEventType.NPC_SPOKE,
        sourceNpcId = npcId,
        sourceNpcName = npcName,
        position = position,
        data = mapOf("message" to message)
    )
    
    fun npcMoved(npcId: UUID, npcName: String, from: net.minecraft.core.BlockPos, to: net.minecraft.core.BlockPos) = NPCEvent(
        type = NPCEventType.NPC_MOVED,
        sourceNpcId = npcId,
        sourceNpcName = npcName,
        position = to,
        data = mapOf("from" to from, "to" to to)
    )
    
    fun emotionChanged(npcId: UUID, npcName: String, oldEmotion: String, newEmotion: String, position: net.minecraft.core.BlockPos) = NPCEvent(
        type = NPCEventType.EMOTION_CHANGED,
        sourceNpcId = npcId,
        sourceNpcName = npcName,
        position = position,
        data = mapOf("old_emotion" to oldEmotion, "new_emotion" to newEmotion)
    )
    
    fun customEvent(npcId: UUID, npcName: String, eventData: Map<String, Any>, position: net.minecraft.core.BlockPos? = null) = NPCEvent(
        type = NPCEventType.CUSTOM_EVENT,
        sourceNpcId = npcId,
        sourceNpcName = npcName,
        position = position,
        data = eventData
    )
}
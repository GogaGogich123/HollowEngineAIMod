package com.hollowengineai.mod.interruption

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.async.ActionInfo
import com.hollowengineai.mod.planning.PlanInfo
import org.apache.logging.log4j.LogManager
import java.util.*

/**
 * Данные прерывания
 */
data class InterruptData(
    val type: InterruptType,
    val reason: String,
    val priority: InterruptPriority,
    val sourceId: String? = null,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Активное прерывание
 */
data class ActiveInterrupt(
    val id: UUID,
    val interruptData: InterruptData,
    val handler: InterruptHandler,
    val startTime: Long,
    val canRestore: Boolean = false
)

/**
 * Событие прерывания
 */
data class InterruptEvent(
    val interruptId: UUID,
    val eventType: InterruptEventType,
    val interruptType: InterruptType?,
    val description: String,
    val timestamp: Long
)

/**
 * Информация о прерывании
 */
data class InterruptInfo(
    val id: UUID,
    val type: InterruptType,
    val reason: String,
    val priority: InterruptPriority,
    val startTime: Long,
    val canRestore: Boolean
)

/**
 * Сохраненное состояние НПС
 */
data class SavedState(
    val interruptId: UUID,
    val timestamp: Long,
    val activeActions: List<ActionInfo>,
    val activePlans: List<PlanInfo>,
    val context: String
)

/**
 * Статистика системы прерываний
 */
data class InterruptionStats(
    val interruptsTriggered: Long,
    val interruptsHandled: Long,
    val statesRestored: Long,
    val activeInterrupts: Int,
    val savedStates: Int
)

// Енумы

/**
 * Типы прерываний
 */
enum class InterruptType {
    // Угрозы и опасности
    THREAT_DETECTED,              // Обнаружена угроза
    DANGER_IMMINENT,              // Непосредственная опасность
    ATTACK_INCOMING,              // Входящая атака
    
    // Здоровье и состояние
    LOW_HEALTH,                   // Низкое здоровье
    CRITICAL_HEALTH,              // Критически низкое здоровье
    STATUS_EFFECT,                // Негативный эффект
    
    // Социальные взаимодействия
    INTERACTION_REQUEST,          // Запрос на взаимодействие
    BEING_OBSERVED,               // За НПС наблюдают
    PERSONAL_SPACE_VIOLATION,     // Нарушение личного пространства
    CROWD_DETECTED,               // Обнаружена толпа
    HOSTILE_BEHAVIOR,             // Враждебное поведение
    
    // Окружающая среда
    TIME_CHANGE,                  // Изменение времени суток
    WEATHER_CHANGE,               // Изменение погоды
    ENVIRONMENT_DANGER,           // Опасность окружающей среды
    STRUCTURE_COLLAPSE,           // Разрушение структур
    
    // Коммуникация
    IMPORTANT_MESSAGE,            // Важное сообщение
    EMERGENCY_SIGNAL,             // Сигнал бедствия
    CALL_FOR_HELP,               // Призыв о помощи
    
    // Ресурсы и предметы
    RESOURCE_DEPLETION,           // Истощение ресурсов
    ITEM_STOLEN,                  // Предмет украден
    VALUABLE_ITEM_NEARBY,         // Ценный предмет рядом
    
    // Система и ИИ
    AI_OVERRIDE,                  // Переопределение ИИ
    SYSTEM_ALERT,                 // Системное предупреждение
    DEBUG_INTERRUPT,              // Отладочное прерывание
    
    // Кастомные
    CUSTOM                        // Кастомное прерывание
}

/**
 * Приоритет прерываний
 */
enum class InterruptPriority {
    LOW,        // Низкий - не прерывает важные действия
    NORMAL,     // Обычный - прерывает действия с низким приоритетом
    HIGH,       // Высокий - прерывает большинство действий
    CRITICAL    // Критический - прерывает все действия немедленно
}

/**
 * Типы событий прерываний
 */
enum class InterruptEventType {
    INTERRUPT_TRIGGERED,    // Прерывание активировано
    INTERRUPT_HANDLED,      // Прерывание обработано
    INTERRUPT_FAILED,       // Прерывание не удалось обработать
    INTERRUPT_ENDED,        // Прерывание завершено
    STATE_SAVED,           // Состояние сохранено
    STATE_RESTORED         // Состояние восстановлено
}

/**
 * Причины завершения прерывания
 */
enum class InterruptEndReason {
    COMPLETED,          // Успешно завершено
    TIMEOUT,           // Истекло время
    CANCELLED,         // Отменено
    FAILED,            // Провалено
    SUPERSEDED,        // Заменено более важным прерыванием
    SYSTEM_SHUTDOWN    // Остановка системы
}

/**
 * Интерфейс обработчика прерываний
 */
interface InterruptHandler {
    /**
     * Обработать прерывание
     */
    suspend fun handle(npc: SmartNPC, interruptData: InterruptData)
    
    /**
     * Определить нужно ли сохранять текущее состояние
     */
    fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean
    
    /**
     * Определить нужно ли прерывать текущие действия
     */
    fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean
    
    /**
     * Получить приоритет обработчика (при конфликтах)
     */
    fun getPriority(): Int = 0
}

/**
 * Абстрактный базовый обработчик
 */
abstract class BaseInterruptHandler : InterruptHandler {
    companion object {
        private val LOGGER = LogManager.getLogger(BaseInterruptHandler::class.java)
    }
    
    protected fun log(message: String, npc: SmartNPC) {
        LOGGER.debug("[${this::class.simpleName}] ${npc.name}: $message")
    }
    
    protected fun logError(message: String, npc: SmartNPC, throwable: Throwable? = null) {
        LOGGER.error("[${this::class.simpleName}] ${npc.name}: $message", throwable)
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        // По умолчанию сохраняем состояние для прерываний с высоким приоритетом
        return interruptData.priority.ordinal >= InterruptPriority.HIGH.ordinal
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        // По умолчанию прерываем для всех приоритетов кроме LOW
        return interruptData.priority != InterruptPriority.LOW
    }
}

// Базовые обработчики прерываний

/**
 * Обработчик угроз
 */
class ThreatHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Threat detected: ${interruptData.reason}", npc)
        
        val sourceId = interruptData.sourceId
        val data = interruptData.data
        
        // TODO: Реализовать реакцию на угрозу
        // Например: убежать, приготовиться к бою, вызвать помощь
        
        when (interruptData.priority) {
            InterruptPriority.CRITICAL -> {
                log("CRITICAL THREAT - immediate defensive action required", npc)
                // Немедленная защитная реакция
            }
            InterruptPriority.HIGH -> {
                log("High priority threat - taking defensive measures", npc)
                // Защитные меры
            }
            else -> {
                log("Potential threat detected - staying alert", npc)
                // Повышенная бдительность
            }
        }
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return true // Всегда сохраняем состояние при угрозах
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return true // Угрозы всегда прерывают текущие действия
    }
    
    override fun getPriority(): Int = 100 // Высший приоритет
}

/**
 * Обработчик запросов на взаимодействие
 */
class InteractionHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Interaction request: ${interruptData.reason}", npc)
        
        val playerName = interruptData.data["playerName"] as? String ?: "unknown player"
        val distance = interruptData.data["distance"] as? Double ?: 0.0
        
        // TODO: Реализовать реакцию на запрос взаимодействия
        // Например: повернуться к игроку, начать диалог, показать готовность к взаимодействию
        
        log("Ready to interact with $playerName (distance: ${String.format("%.1f", distance)})", npc)
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return interruptData.priority.ordinal >= InterruptPriority.NORMAL.ordinal
    }
    
    override fun getPriority(): Int = 50
}

/**
 * Обработчик наблюдения
 */
class ObservationHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Being observed: ${interruptData.reason}", npc)
        
        val playerCount = interruptData.data["playerCount"] as? Int ?: 1
        
        // TODO: Реализовать реакцию на наблюдение
        // Например: кивнуть, помахать, стать более осторожным
        
        when (playerCount) {
            1 -> log("One player is watching me", npc)
            in 2..3 -> log("Small group is watching me", npc)
            else -> log("Large group is watching me - feeling a bit nervous", npc)
        }
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return false // Наблюдение обычно не требует сохранения состояния
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return false // Наблюдение не прерывает важные действия
    }
    
    override fun getPriority(): Int = 10
}

/**
 * Обработчик нарушения личного пространства
 */
class PersonalSpaceHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Personal space violation: ${interruptData.reason}", npc)
        
        val playerName = interruptData.data["playerName"] as? String ?: "someone"
        val distance = interruptData.data["distance"] as? Double ?: 0.0
        
        // TODO: Реализовать реакцию на нарушение личного пространства
        // Например: отойти, выразить дискомфорт, предупредить
        
        if (distance < 1.0) {
            log("$playerName is too close! Feeling uncomfortable", npc)
        } else {
            log("$playerName entered my personal space", npc)
        }
    }
    
    override fun getPriority(): Int = 30
}

/**
 * Обработчик проблем со здоровьем
 */
class HealthHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Health issue: ${interruptData.reason}", npc)
        
        val health = interruptData.data["health"] as? Float ?: 0f
        val percentage = interruptData.data["percentage"] as? Double ?: 0.0
        
        // TODO: Реализовать реакцию на проблемы со здоровьем
        // Например: искать лечение, убегать от боя, просить помощи
        
        when {
            percentage < 0.1 -> {
                log("CRITICAL HEALTH - need immediate help!", npc)
            }
            percentage < 0.3 -> {
                log("Low health - need to be careful", npc)
            }
            else -> {
                log("Health could be better", npc)
            }
        }
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return true // Всегда сохраняем состояние при проблемах со здоровьем
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        val percentage = interruptData.data["percentage"] as? Double ?: 1.0
        return percentage < 0.3 // Прерываем только при серьезных проблемах
    }
    
    override fun getPriority(): Int = 80
}

/**
 * Обработчик изменения времени
 */
class TimeChangeHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Time change: ${interruptData.reason}", npc)
        
        val timeOfDay = interruptData.data["timeOfDay"] as? String ?: "unknown"
        
        // TODO: Реализовать реакцию на изменение времени
        // Например: изменить поведение, найти укрытие на ночь
        
        when (timeOfDay) {
            "night" -> log("Night is coming - time to be more careful", npc)
            "day" -> log("Day has arrived - time to be more active", npc)
            else -> log("Time of day changed to $timeOfDay", npc)
        }
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return false // Изменение времени обычно не требует сохранения состояния
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return false // Изменение времени обычно не прерывает действия
    }
    
    override fun getPriority(): Int = 5
}

/**
 * Обработчик изменения погоды
 */
class WeatherHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Weather change: ${interruptData.reason}", npc)
        
        val weather = interruptData.data["weather"] as? String ?: "unknown"
        val isThundering = interruptData.data["isThundering"] as? Boolean ?: false
        
        // TODO: Реализовать реакцию на изменение погоды
        // Например: найти укрытие от дождя, испугаться грозы
        
        when {
            weather == "rain" && isThundering -> {
                log("Thunderstorm! Need to find shelter", npc)
            }
            weather == "rain" -> {
                log("It's raining - might want to find cover", npc)
            }
            else -> {
                log("Weather changed to $weather", npc)
            }
        }
    }
    
    override fun shouldSaveState(npc: SmartNPC, interruptData: InterruptData): Boolean {
        return false
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        val isThundering = interruptData.data["isThundering"] as? Boolean ?: false
        return isThundering // Прерываем только при грозе
    }
    
    override fun getPriority(): Int = 20
}

/**
 * Обработчик толпы
 */
class CrowdHandler : BaseInterruptHandler() {
    override suspend fun handle(npc: SmartNPC, interruptData: InterruptData) {
        log("Crowd detected: ${interruptData.reason}", npc)
        
        val playerCount = interruptData.data["playerCount"] as? Int ?: 0
        val playerNames = interruptData.data["playerNames"] as? List<*> ?: emptyList<String>()
        
        // TODO: Реализовать реакцию на толпу
        // Например: стать более осторожным, отойти, привлечь внимание
        
        when {
            playerCount >= 5 -> {
                log("Large crowd of $playerCount players - feeling overwhelmed", npc)
            }
            playerCount >= 3 -> {
                log("Group of $playerCount players nearby - staying alert", npc)
            }
            else -> {
                log("Small group detected", npc)
            }
        }
    }
    
    override fun shouldInterruptCurrent(npc: SmartNPC, interruptData: InterruptData): Boolean {
        val playerCount = interruptData.data["playerCount"] as? Int ?: 0
        return playerCount >= 5 // Прерываем только при большой толпе
    }
    
    override fun getPriority(): Int = 25
}
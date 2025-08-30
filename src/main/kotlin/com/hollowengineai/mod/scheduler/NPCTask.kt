package com.hollowengineai.mod.scheduler

import com.hollowengineai.mod.actions.ActionTypes.ActionType
import java.util.*

/**
 * Задача планировщика для НПС
 * 
 * Представляет запланированное действие, которое должно быть выполнено НПС
 * в определенное время или при определенных условиях.
 */
data class NPCTask(
    /** Уникальный идентификатор задачи */
    val id: UUID = UUID.randomUUID(),
    
    /** Идентификатор НПС, который должен выполнить задачу */
    val npcId: UUID,
    
    /** Действие, которое нужно выполнить */
    val action: String,
    
    /** Параметры действия */
    val parameters: Map<String, Any> = emptyMap(),
    
    /** Время, когда задача должна быть выполнена (timestamp) */
    val scheduledTime: Long,
    
    /** Приоритет задачи (0 = низкий, 10 = высокий) */
    val priority: Int = 5,
    
    /** Статус выполнения задачи */
    val status: TaskStatus = TaskStatus.PENDING,
    
    /** Время создания задачи */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Время последнего обновления */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** Количество попыток выполнения */
    val attemptCount: Int = 0,
    
    /** Максимальное количество попыток */
    val maxAttempts: Int = 3,
    
    /** Время истечения задачи */
    val expiresAt: Long? = null,
    
    /** Условия для выполнения задачи */
    val conditions: List<TaskCondition> = emptyList(),
    
    /** Повторяющаяся ли задача */
    val isRecurring: Boolean = false,
    
    /** Интервал повторения (только для повторяющихся задач) */
    val recurringInterval: Long? = null,
    
    /** Результат выполнения задачи */
    val result: TaskResult? = null,
    
    /** Дополнительные метаданные */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Проверить, готова ли задача к выполнению
     */
    fun isReadyToExecute(): Boolean {
        if (status != TaskStatus.PENDING) return false
        if (System.currentTimeMillis() < scheduledTime) return false
        if (isExpired()) return false
        
        return conditions.all { it.isMet() }
    }
    
    /**
     * Проверить, истекла ли задача
     */
    fun isExpired(): Boolean {
        return expiresAt != null && System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Проверить, можно ли повторить задачу
     */
    fun canRetry(): Boolean {
        return attemptCount < maxAttempts && status == TaskStatus.FAILED
    }
    
    /**
     * Создать копию задачи для повторного выполнения
     */
    fun retry(): NPCTask {
        return copy(
            id = UUID.randomUUID(),
            status = TaskStatus.PENDING,
            attemptCount = attemptCount + 1,
            updatedAt = System.currentTimeMillis(),
            result = null
        )
    }
    
    /**
     * Создать следующую повторяющуюся задачу
     */
    fun createNextRecurrence(): NPCTask? {
        if (!isRecurring || recurringInterval == null) return null
        
        return copy(
            id = UUID.randomUUID(),
            scheduledTime = scheduledTime + recurringInterval,
            status = TaskStatus.PENDING,
            attemptCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            result = null
        )
    }
    
    /**
     * Обновить статус задачи
     */
    fun updateStatus(newStatus: TaskStatus, result: TaskResult? = null): NPCTask {
        return copy(
            status = newStatus,
            result = result,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Получить вес задачи для приоритизации
     */
    fun getWeight(): Float {
        var weight = priority.toFloat()
        
        // Учитываем время ожидания
        val waitTime = System.currentTimeMillis() - scheduledTime
        if (waitTime > 0) {
            weight += (waitTime / 1000.0f) * 0.1f
        }
        
        // Учитываем количество попыток
        weight += attemptCount * 0.5f
        
        return weight
    }
    
    /**
     * Получить описание задачи
     */
    fun getDescription(): String {
        return buildString {
            append("Task: $action")
            if (parameters.isNotEmpty()) {
                append(" (")
                append(parameters.entries.joinToString(", ") { "${it.key}=${it.value}" })
                append(")")
            }
            append(" for NPC $npcId")
        }
    }
    
    companion object {
        /**
         * Создать простую задачу
         */
        fun simple(
            npcId: UUID,
            action: String,
            scheduledTime: Long,
            vararg params: Pair<String, Any>
        ): NPCTask {
            return NPCTask(
                npcId = npcId,
                action = action,
                parameters = params.toMap(),
                scheduledTime = scheduledTime
            )
        }
        
        /**
         * Создать задачу с приоритетом
         */
        fun withPriority(
            npcId: UUID,
            action: String,
            scheduledTime: Long,
            priority: Int,
            vararg params: Pair<String, Any>
        ): NPCTask {
            return NPCTask(
                npcId = npcId,
                action = action,
                parameters = params.toMap(),
                scheduledTime = scheduledTime,
                priority = priority
            )
        }
        
        /**
         * Создать повторяющуюся задачу
         */
        fun recurring(
            npcId: UUID,
            action: String,
            startTime: Long,
            interval: Long,
            vararg params: Pair<String, Any>
        ): NPCTask {
            return NPCTask(
                npcId = npcId,
                action = action,
                parameters = params.toMap(),
                scheduledTime = startTime,
                isRecurring = true,
                recurringInterval = interval
            )
        }
    }
}

/**
 * Статус выполнения задачи
 */
enum class TaskStatus {
    /** Задача ожидает выполнения */
    PENDING,
    
    /** Задача выполняется */
    RUNNING,
    
    /** Задача успешно выполнена */
    COMPLETED,
    
    /** Задача провалена */
    FAILED,
    
    /** Задача отменена */
    CANCELLED,
    
    /** Задача истекла */
    EXPIRED,
    
    /** Задача приостановлена */
    PAUSED
}

/**
 * Условие выполнения задачи
 */
abstract class TaskCondition {
    /** Описание условия */
    abstract val description: String
    
    /** Проверить, выполнено ли условие */
    abstract fun isMet(): Boolean
}

/**
 * Условие по времени
 */
class TimeCondition(
    private val targetTime: Long,
    override val description: String = "Wait until $targetTime"
) : TaskCondition() {
    override fun isMet(): Boolean = System.currentTimeMillis() >= targetTime
}

/**
 * Результат выполнения задачи
 */
data class TaskResult(
    /** Успешно ли выполнена задача */
    val success: Boolean,
    
    /** Сообщение о результате */
    val message: String = "",
    
    /** Данные результата */
    val data: Map<String, Any> = emptyMap(),
    
    /** Время выполнения в миллисекундах */
    val executionTime: Long = 0L,
    
    /** Ошибка (если была) */
    val error: String? = null
) {
    companion object {
        fun success(message: String = "Task completed successfully", data: Map<String, Any> = emptyMap()): TaskResult {
            return TaskResult(success = true, message = message, data = data)
        }
        
        fun failure(error: String, data: Map<String, Any> = emptyMap()): TaskResult {
            return TaskResult(success = false, message = "Task failed", error = error, data = data)
        }
    }
}
package com.hollowengineai.mod.attention

import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Цель внимания НПС
 */
data class AttentionTarget(
    val id: String,
    val type: AttentionTargetType,
    val position: Vec3,
    val description: String
)

/**
 * Текущий фокус внимания НПС
 */
data class AttentionFocus(
    val target: AttentionTarget,
    val startTime: Long,
    val reason: String,
    val strength: Double, // 0.0 - 1.0
    val isForced: Boolean = false,
    val forcedDuration: Long? = null // Длительность принудительного фокуса в мс
)

/**
 * Кандидат на внимание
 */
data class AttentionCandidate(
    val target: AttentionTarget,
    val attentionValue: Double, // 0.0 - 1.0
    val factors: Map<String, Double>, // Факторы влияющие на внимание
    val lastUpdate: Long,
    val source: AttentionSource
)

/**
 * Событие внимания
 */
data class AttentionEvent(
    val eventType: AttentionEventType,
    val targetId: String,
    val targetType: AttentionTargetType,
    val description: String,
    val timestamp: Long
)

/**
 * Модификатор внимания
 */
data class AttentionModifier(
    val type: AttentionModifierType,
    val value: Double, // Может быть положительным или отрицательным
    val reason: String
)

/**
 * Состояние модуляторов внимания
 */
data class AttentionModulators(
    val arousal: Double, // Уровень возбуждения/активности (0.0 - 1.0)
    val stress: Double,  // Уровень стресса (0.0 - 1.0)
    val fatigue: Double  // Уровень усталости (0.0 - 1.0)
)

/**
 * Статистика системы внимания
 */
data class AttentionStats(
    val focusChanges: Long,
    val attentionUpdates: Long,
    val currentCandidates: Int,
    val hasFocus: Boolean,
    val focusDuration: Long?,
    val modulators: AttentionModulators
)

/**
 * Конфигурация системы внимания
 */
data class AttentionConfig(
    val minAttentionThreshold: Double = 0.3, // Минимальный порог для получения внимания
    val switchThreshold: Double = 0.2, // Разница для переключения фокуса
    val maxAttentionDistance: Double = 32.0, // Максимальная дистанция внимания
    val focusDecayRate: Double = 0.01, // Скорость затухания фокуса
    val updateInterval: Long = 500L, // Интервал обновления в мс
    val maxHistorySize: Int = 100 // Максимальный размер истории событий
)

// Енумы

/**
 * Типы целей внимания
 */
enum class AttentionTargetType {
    PLAYER,           // Игрок
    NPC,              // Другой НПС
    ENTITY,           // Сущность (моб, животное)
    BLOCK,            // Блок или структура
    ITEM,             // Предмет
    SOUND,            // Звук
    INTERRUPT,        // Прерывание
    LOCATION,         // Место/область
    EVENT,            // Событие
    ABSTRACT          // Абстрактная цель (мысль, цель)
}

/**
 * Источник кандидата внимания
 */
enum class AttentionSource {
    PERCEPTION,       // Из системы восприятия
    INTERRUPTION,     // Из системы прерываний
    PLANNING,         // Из системы планирования
    MEMORY,           // Из памяти
    AI,               // Из ИИ системы
    MANUAL,           // Ручное добавление
    ENVIRONMENT,      // Из окружающей среды
    SOCIAL            // Из социальных взаимодействий
}

/**
 * Типы событий внимания
 */
enum class AttentionEventType {
    FOCUS_GAINED,     // Получен фокус внимания
    FOCUS_LOST,       // Потерян фокус внимания
    FOCUS_SHIFTED,    // Фокус переключен на другую цель
    CANDIDATE_ADDED,  // Добавлен кандидат на внимание
    CANDIDATE_REMOVED,// Удален кандидат
    THRESHOLD_CHANGED,// Изменен порог внимания
    FORCED_FOCUS,     // Принудительный фокус установлен
    FOCUS_EXPIRED     // Фокус истек по времени
}

/**
 * Типы модификаторов внимания
 */
enum class AttentionModifierType {
    AROUSAL,          // Возбуждение/активность
    STRESS,           // Стресс
    FATIGUE,          // Усталость
    FOCUS_BOOST,      // Усиление фокуса
    DISTRACTION,      // Отвлечение
    INTEREST,         // Интерес
    FEAR,             // Страх
    CURIOSITY         // Любопытство
}

/**
 * Приоритеты внимания
 */
enum class AttentionPriority {
    VERY_LOW,
    LOW,
    NORMAL,
    HIGH,
    VERY_HIGH,
    CRITICAL
}

/**
 * Паттерны внимания
 */
enum class AttentionPattern {
    FOCUSED,          // Сфокусированное внимание на одной цели
    SCANNING,         // Сканирование окружения
    VIGILANT,         // Бдительное наблюдение
    DISTRACTED,       // Рассеянное внимание
    HYPER_FOCUSED,    // Гиперфокус (туннельное зрение)
    MULTI_TASKING,    // Многозадачность
    OVERWHELMED       // Перегрузка вниманием
}

/**
 * Утилиты для работы с вниманием
 */
object AttentionUtils {
    
    /**
     * Вычислить расстояние между позициями
     */
    fun calculateDistance(pos1: Vec3, pos2: Vec3): Double {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dz = pos1.z - pos2.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Нормализовать значение внимания
     */
    fun normalizeAttention(value: Double): Double {
        return value.coerceIn(0.0, 1.0)
    }
    
    /**
     * Вычислить затухание внимания со временем
     */
    fun calculateDecay(initialValue: Double, elapsedTime: Long, decayRate: Double): Double {
        val decayFactor = kotlin.math.exp(-decayRate * elapsedTime / 1000.0)
        return initialValue * decayFactor
    }
    
    /**
     * Объединить несколько факторов внимания
     */
    fun combineFators(factors: Map<String, Double>, weights: Map<String, Double>): Double {
        var totalValue = 0.0
        var totalWeight = 0.0
        
        factors.forEach { (factor, value) ->
            val weight = weights[factor] ?: 1.0
            totalValue += value * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0) totalValue / totalWeight else 0.0
    }
    
    /**
     * Определить паттерн внимания на основе истории
     */
    fun analyzeAttentionPattern(events: List<AttentionEvent>): AttentionPattern {
        if (events.size < 3) return AttentionPattern.FOCUSED
        
        val recentEvents = events.takeLast(10)
        val focusChanges = recentEvents.count { it.eventType == AttentionEventType.FOCUS_SHIFTED }
        val timeSpan = if (recentEvents.isNotEmpty()) {
            recentEvents.last().timestamp - recentEvents.first().timestamp
        } else {
            0L
        }
        
        return when {
            focusChanges == 0 -> AttentionPattern.FOCUSED
            focusChanges > 7 && timeSpan < 30000 -> AttentionPattern.DISTRACTED
            focusChanges > 5 && timeSpan < 15000 -> AttentionPattern.OVERWHELMED
            focusChanges in 2..4 -> AttentionPattern.SCANNING
            focusChanges == 1 -> AttentionPattern.VIGILANT
            else -> AttentionPattern.FOCUSED
        }
    }
    
    /**
     * Вычислить рекомендуемый порог внимания на основе контекста
     */
    fun calculateAdaptiveThreshold(
        baseThreshold: Double,
        arousal: Double,
        stress: Double,
        fatigue: Double,
        candidateCount: Int
    ): Double {
        var adaptedThreshold = baseThreshold
        
        // Высокое возбуждение снижает порог (более чувствительное внимание)
        adaptedThreshold -= arousal * 0.1
        
        // Стресс может как повышать, так и понижать порог в зависимости от уровня
        if (stress < 0.5) {
            adaptedThreshold -= stress * 0.05 // Небольшой стресс делает более внимательным
        } else {
            adaptedThreshold += (stress - 0.5) * 0.2 // Высокий стресс снижает внимательность
        }
        
        // Усталость повышает порог (менее чувствительное внимание)
        adaptedThreshold += fatigue * 0.15
        
        // Много кандидатов повышает порог (избирательность)
        if (candidateCount > 5) {
            adaptedThreshold += (candidateCount - 5) * 0.02
        }
        
        return normalizeAttention(adaptedThreshold)
    }
    
    /**
     * Создать модификатор внимания
     */
    fun createModifier(
        type: AttentionModifierType,
        intensity: Double, // -1.0 до 1.0
        reason: String
    ): AttentionModifier {
        val normalizedIntensity = intensity.coerceIn(-1.0, 1.0)
        return AttentionModifier(type, normalizedIntensity, reason)
    }
    
    /**
     * Проверить совместимость целей внимания
     */
    fun areTargetsCompatible(target1: AttentionTarget, target2: AttentionTarget): Boolean {
        // Некоторые типы целей не совместимы для одновременного внимания
        return when {
            target1.type == AttentionTargetType.INTERRUPT && target2.type == AttentionTargetType.INTERRUPT -> false
            target1.id == target2.id -> true
            calculateDistance(target1.position, target2.position) > 10.0 -> false
            else -> true
        }
    }
    
    /**
     * Вычислить приоритет цели внимания
     */
    fun calculateTargetPriority(target: AttentionTarget, context: Map<String, Any>): AttentionPriority {
        return when (target.type) {
            AttentionTargetType.INTERRUPT -> {
                val interruptPriority = context["interruptPriority"] as? String
                when (interruptPriority) {
                    "CRITICAL" -> AttentionPriority.CRITICAL
                    "HIGH" -> AttentionPriority.VERY_HIGH
                    "NORMAL" -> AttentionPriority.HIGH
                    else -> AttentionPriority.NORMAL
                }
            }
            AttentionTargetType.PLAYER -> {
                val playerIntent = context["playerIntent"] as? String
                when (playerIntent) {
                    "AGGRESSIVE", "THREATENING" -> AttentionPriority.VERY_HIGH
                    "WANTS_TO_INTERACT" -> AttentionPriority.HIGH
                    "CURIOUS" -> AttentionPriority.NORMAL
                    else -> AttentionPriority.LOW
                }
            }
            AttentionTargetType.ENTITY -> AttentionPriority.NORMAL
            AttentionTargetType.SOUND -> AttentionPriority.LOW
            else -> AttentionPriority.LOW
        }
    }
}
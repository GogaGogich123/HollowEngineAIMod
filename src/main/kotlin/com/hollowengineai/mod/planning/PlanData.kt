package com.hollowengineai.mod.planning

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.async.NPCAction
import com.hollowengineai.mod.async.WaitAction
import com.hollowengineai.mod.async.ThinkAction
import com.hollowengineai.mod.async.ObserveAction
import java.util.*

/**
 * План НПС
 */
data class Plan(
    val id: UUID,
    val goal: PlanGoal,
    val actions: List<NPCAction>,
    val type: PlanType,
    val priority: PlanPriority,
    val preconditions: List<(SmartNPC) -> Boolean> = emptyList(),
    val createdTime: Long,
    val estimatedDuration: Long = actions.sumOf { it.estimatedDurationMs },
    val tags: Set<String> = emptySet()
)

/**
 * Цель плана
 */
data class PlanGoal(
    val type: GoalType,
    val description: String,
    val targetId: String? = null,
    val priority: Int = 5, // 1-10
    val successCondition: (SmartNPC) -> Boolean = { true },
    val timeoutMs: Long? = null,
    val parameters: Map<String, Any> = emptyMap()
)

/**
 * Активный план
 */
data class ActivePlan(
    val plan: Plan,
    val status: PlanStatus,
    val currentStep: Int = 0,
    val startTime: Long?,
    val actionIds: List<UUID> = emptyList(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * Завершенный план
 */
data class CompletedPlan(
    val plan: Plan,
    val status: PlanStatus,
    val startTime: Long?,
    val endTime: Long?,
    val completedSteps: Int,
    val failureReason: String? = null
)

/**
 * Информация о плане
 */
data class PlanInfo(
    val id: UUID,
    val goalDescription: String,
    val goalType: GoalType,
    val priority: PlanPriority,
    val status: PlanStatus,
    val currentStep: Int,
    val totalSteps: Int,
    val startTime: Long?
)

/**
 * Событие плана
 */
data class PlanEvent(
    val planId: UUID,
    val eventType: PlanEventType,
    val description: String,
    val timestamp: Long
)

/**
 * Статистика системы планирования
 */
data class PlanSystemStats(
    val activePlans: Int,
    val executingPlans: Int,
    val createdPlans: Int,
    val pausedPlans: Int,
    val blockedPlans: Int,
    val completedPlans: Int
)

// Енумы

/**
 * Типы планов
 */
enum class PlanType {
    SEQUENTIAL,   // Последовательное выполнение действий
    PARALLEL,     // Параллельное выполнение действий  
    CONDITIONAL,  // Условное выполнение на основе проверок
    REACTIVE      // Реактивное выполнение по событиям
}

/**
 * Приоритет планов
 */
enum class PlanPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * Статус плана
 */
enum class PlanStatus {
    CREATED,    // Создан, но не запущен
    EXECUTING,  // Выполняется
    PAUSED,     // Приостановлен
    BLOCKED,    // Заблокирован (не может выполняться)
    COMPLETED,  // Успешно завершен
    FAILED,     // Провален
    CANCELLED   // Отменен
}

/**
 * Типы целей
 */
enum class GoalType {
    SURVIVAL,        // Выживание
    SOCIAL,          // Социальное взаимодействие
    EXPLORATION,     // Исследование
    MAINTENANCE,     // Поддержание состояния
    COMMUNICATION,   // Общение
    TRADE,           // Торговля
    LEARNING,        // Обучение
    ENTERTAINMENT,   // Развлечение
    WORK,            // Работа
    REST,            // Отдых
    CUSTOM           // Кастомная цель
}

/**
 * Типы событий плана
 */
enum class PlanEventType {
    PLAN_CREATED,
    PLAN_STARTED,
    PLAN_COMPLETED,
    PLAN_FAILED,
    PLAN_CANCELLED,
    PLAN_PAUSED,
    PLAN_RESUMED,
    PLAN_BLOCKED,
    PLAN_UNBLOCKED,
    STEP_COMPLETED,
    STEP_FAILED
}

/**
 * Интерфейс генератора планов
 */
interface PlanGenerator {
    fun generateActions(npc: SmartNPC, goal: PlanGoal): List<NPCAction>
    fun canHandleGoal(goal: PlanGoal): Boolean
    fun getPriority(): Int // Приоритет генератора (выше = важнее)
}

/**
 * Интерфейс оценщика планов
 */
interface PlanEvaluator {
    fun evaluatePlan(npc: SmartNPC, plan: Plan): PlanEvaluation
}

/**
 * Результат оценки плана
 */
data class PlanEvaluation(
    val score: Double, // 0.0 - 1.0
    val feasibility: Double, // Можно ли выполнить
    val efficiency: Double, // Насколько эффективно
    val risk: Double, // Уровень риска
    val comments: List<String> = emptyList()
)

/**
 * Базовый генератор планов
 */
class BasicPlanGenerator : PlanGenerator {
    
    override fun generateActions(npc: SmartNPC, goal: PlanGoal): List<NPCAction> {
        return when (goal.type) {
            GoalType.REST -> generateRestActions(goal)
            GoalType.EXPLORATION -> generateExplorationActions(goal)
            GoalType.SOCIAL -> generateSocialActions(goal)
            GoalType.COMMUNICATION -> generateCommunicationActions(goal)
            GoalType.LEARNING -> generateLearningActions(goal)
            GoalType.ENTERTAINMENT -> generateEntertainmentActions(goal)
            GoalType.MAINTENANCE -> generateMaintenanceActions(goal)
            else -> generateDefaultActions(goal)
        }
    }
    
    override fun canHandleGoal(goal: PlanGoal): Boolean {
        return true // Базовый генератор может обработать любую цель
    }
    
    override fun getPriority(): Int = 1 // Низкий приоритет
    
    private fun generateRestActions(goal: PlanGoal): List<NPCAction> {
        val duration = goal.parameters["duration"] as? Long ?: 10000L
        return listOf(
            ThinkAction("Time to rest", 1000L),
            WaitAction(duration, "Resting")
        )
    }
    
    private fun generateExplorationActions(goal: PlanGoal): List<NPCAction> {
        return listOf(
            ThinkAction("Planning exploration route", 2000L),
            ObserveAction("surroundings", 5000L),
            ThinkAction("Analyzing what I discovered", 1000L)
        )
    }
    
    private fun generateSocialActions(goal: PlanGoal): List<NPCAction> {
        val targetId = goal.targetId ?: "nearby_players"
        return listOf(
            ObserveAction(targetId, 3000L),
            ThinkAction("How should I approach them?", 2000L),
            WaitAction(1000L, "Preparing for social interaction")
        )
    }
    
    private fun generateCommunicationActions(goal: PlanGoal): List<NPCAction> {
        val targetId = goal.targetId ?: "player"
        return listOf(
            ThinkAction("What should I say to $targetId?", 2000L),
            ObserveAction(targetId, 1000L),
            ThinkAction("Preparing response", 1000L)
        )
    }
    
    private fun generateLearningActions(goal: PlanGoal): List<NPCAction> {
        val topic = goal.parameters["topic"] as? String ?: "general knowledge"
        return listOf(
            ThinkAction("Starting to learn about $topic", 1000L),
            ObserveAction("environment", 5000L),
            ThinkAction("Processing new information about $topic", 3000L),
            ThinkAction("Storing knowledge about $topic", 1000L)
        )
    }
    
    private fun generateEntertainmentActions(goal: PlanGoal): List<NPCAction> {
        return listOf(
            ThinkAction("What would be fun to do?", 2000L),
            ObserveAction("surroundings", 3000L),
            WaitAction(5000L, "Enjoying the moment"),
            ThinkAction("That was enjoyable", 1000L)
        )
    }
    
    private fun generateMaintenanceActions(goal: PlanGoal): List<NPCAction> {
        val task = goal.parameters["task"] as? String ?: "general maintenance"
        return listOf(
            ThinkAction("Time for maintenance: $task", 1000L),
            ObserveAction("current_status", 2000L),
            ThinkAction("Checking if maintenance is needed", 2000L),
            WaitAction(3000L, "Performing maintenance: $task"),
            ThinkAction("Maintenance completed", 1000L)
        )
    }
    
    private fun generateDefaultActions(goal: PlanGoal): List<NPCAction> {
        return listOf(
            ThinkAction("Considering goal: ${goal.description}", 2000L),
            WaitAction(3000L, "Working on: ${goal.description}"),
            ThinkAction("Completed goal: ${goal.description}", 1000L)
        )
    }
}

/**
 * Фабрика для создания стандартных целей
 */
object PlanGoals {
    
    fun rest(durationMs: Long = 10000L): PlanGoal {
        return PlanGoal(
            type = GoalType.REST,
            description = "Take a rest for ${durationMs}ms",
            priority = 3,
            parameters = mapOf("duration" to durationMs)
        )
    }
    
    fun exploreSurroundings(): PlanGoal {
        return PlanGoal(
            type = GoalType.EXPLORATION,
            description = "Explore the surroundings",
            priority = 5
        )
    }
    
    fun socialInteraction(targetId: String): PlanGoal {
        return PlanGoal(
            type = GoalType.SOCIAL,
            description = "Have social interaction with $targetId",
            targetId = targetId,
            priority = 7
        )
    }
    
    fun communicateWith(targetId: String, topic: String = "general"): PlanGoal {
        return PlanGoal(
            type = GoalType.COMMUNICATION,
            description = "Communicate with $targetId about $topic",
            targetId = targetId,
            priority = 6,
            parameters = mapOf("topic" to topic)
        )
    }
    
    fun learnAbout(topic: String): PlanGoal {
        return PlanGoal(
            type = GoalType.LEARNING,
            description = "Learn about $topic",
            priority = 4,
            parameters = mapOf("topic" to topic)
        )
    }
    
    fun maintenance(task: String): PlanGoal {
        return PlanGoal(
            type = GoalType.MAINTENANCE,
            description = "Perform maintenance: $task",
            priority = 6,
            parameters = mapOf("task" to task)
        )
    }
    
    fun entertainment(): PlanGoal {
        return PlanGoal(
            type = GoalType.ENTERTAINMENT,
            description = "Find something entertaining to do",
            priority = 3
        )
    }
    
    fun custom(description: String, priority: Int = 5, parameters: Map<String, Any> = emptyMap()): PlanGoal {
        return PlanGoal(
            type = GoalType.CUSTOM,
            description = description,
            priority = priority,
            parameters = parameters
        )
    }
}

/**
 * Утилиты для работы с планами
 */
object PlanUtils {
    
    fun estimatePlanDuration(plan: Plan): Long {
        return when (plan.type) {
            PlanType.SEQUENTIAL -> plan.actions.sumOf { it.estimatedDurationMs }
            PlanType.PARALLEL -> plan.actions.maxOfOrNull { it.estimatedDurationMs } ?: 0L
            PlanType.CONDITIONAL -> plan.actions.sumOf { it.estimatedDurationMs } / 2 // Примерная оценка
            PlanType.REACTIVE -> Long.MAX_VALUE // Неопределенная длительность
        }
    }
    
    fun canPlansRunConcurrently(plan1: Plan, plan2: Plan): Boolean {
        // Простая проверка - планы с высоким приоритетом не могут выполняться одновременно
        if (plan1.priority == PlanPriority.CRITICAL || plan2.priority == PlanPriority.CRITICAL) {
            return false
        }
        
        // Планы с одинаковыми тегами могут конфликтовать
        val commonTags = plan1.tags.intersect(plan2.tags)
        if (commonTags.contains("exclusive")) {
            return false
        }
        
        return true
    }
    
    fun calculatePlanComplexity(plan: Plan): Int {
        var complexity = plan.actions.size
        
        when (plan.type) {
            PlanType.PARALLEL -> complexity += 2
            PlanType.CONDITIONAL -> complexity += 3
            PlanType.REACTIVE -> complexity += 4
            else -> { /* Sequential - no additional complexity */ }
        }
        
        complexity += plan.preconditions.size
        
        return complexity
    }
    
    fun isUrgentPlan(plan: Plan): Boolean {
        return plan.priority == PlanPriority.CRITICAL || 
               plan.tags.contains("urgent") ||
               plan.goal.timeoutMs?.let { 
                   System.currentTimeMillis() + 10000 > plan.createdTime + it 
               } == true
    }
}
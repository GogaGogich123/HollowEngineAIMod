package com.hollowengineai.mod.async

import com.hollowengineai.mod.core.SmartNPC
import kotlinx.coroutines.delay
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import kotlin.random.Random

/**
 * Базовый интерфейс для действий НПС
 */
interface NPCAction {
    val name: String
    val type: NPCActionType
    val estimatedDurationMs: Long
    val description: String
    
    /**
     * Проверить, можно ли выполнить действие
     */
    suspend fun canExecute(npc: SmartNPC): Boolean
    
    /**
     * Выполнить действие
     */
    suspend fun execute(npc: SmartNPC): ActionExecutionResult
    
    /**
     * Прервать действие (опционально)
     */
    suspend fun cancel(npc: SmartNPC) {
        // По умолчанию ничего не делаем
    }
}

/**
 * Абстрактный базовый класс для действий НПС
 */
abstract class BaseNPCAction(
    override val name: String,
    override val type: NPCActionType,
    override val estimatedDurationMs: Long = 1000L,
    override val description: String = ""
) : NPCAction {
    
    companion object {
        private val LOGGER = LogManager.getLogger(BaseNPCAction::class.java)
    }
    
    override suspend fun canExecute(npc: SmartNPC): Boolean = true
    
    protected fun log(message: String, npc: SmartNPC) {
        LOGGER.debug("[$name] ${npc.name}: $message")
    }
    
    protected fun logError(message: String, npc: SmartNPC, throwable: Throwable? = null) {
        LOGGER.error("[$name] ${npc.name}: $message", throwable)
    }
}

/**
 * Типы действий НПС
 */
enum class NPCActionType {
    MOVEMENT,      // Перемещение
    COMMUNICATION, // Общение
    INTERACTION,   // Взаимодействие с объектами/игроками
    OBSERVATION,   // Наблюдение
    THINKING,      // Размышление/анализ
    EMOTION,       // Эмоциональное действие
    TRADE,         // Торговля
    COMBAT,        // Боевые действия
    IDLE,          // Простой/ожидание
    CUSTOM         // Кастомное действие
}

/**
 * Статус выполнения действия
 */
enum class ActionStatus {
    RUNNING,    // Выполняется
    COMPLETED,  // Успешно завершено
    FAILED,     // Провалено
    CANCELLED   // Отменено
}

/**
 * Результат выполнения действия
 */
data class ActionExecutionResult(
    val status: ActionStatus,
    val message: String? = null,
    val data: Map<String, Any> = emptyMap()
) {
    companion object {
        fun completed(message: String? = null, data: Map<String, Any> = emptyMap()) = 
            ActionExecutionResult(ActionStatus.COMPLETED, message, data)
            
        fun failed(message: String, data: Map<String, Any> = emptyMap()) = 
            ActionExecutionResult(ActionStatus.FAILED, message, data)
            
        fun cancelled(message: String? = null, data: Map<String, Any> = emptyMap()) = 
            ActionExecutionResult(ActionStatus.CANCELLED, message, data)
    }
}

// Базовые действия НПС

/**
 * Действие ожидания/простоя
 */
class WaitAction(
    private val durationMs: Long,
    private val reason: String = "Waiting"
) : BaseNPCAction(
    name = "Wait",
    type = NPCActionType.IDLE,
    estimatedDurationMs = durationMs,
    description = "Wait for $durationMs ms - $reason"
) {
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Starting wait for ${durationMs}ms - $reason", npc)
        
        try {
            delay(durationMs)
            log("Wait completed", npc)
            return ActionExecutionResult.completed("Wait completed after ${durationMs}ms")
        } catch (e: Exception) {
            logError("Wait interrupted", npc, e)
            return ActionExecutionResult.failed("Wait was interrupted: ${e.message}")
        }
    }
}

/**
 * Действие перемещения к точке
 */
class MoveToAction(
    private val targetPosition: BlockPos,
    private val speed: Double = 1.0
) : BaseNPCAction(
    name = "MoveTo",
    type = NPCActionType.MOVEMENT,
    estimatedDurationMs = 5000L, // Примерная оценка
    description = "Move to $targetPosition"
) {
    override suspend fun canExecute(npc: SmartNPC): Boolean {
        val currentPos = npc.getEntity().blockPosition()
        val distance = currentPos.distSqr(targetPosition)
        
        // Проверяем что цель не слишком далеко
        return distance <= 64 * 64 // Максимум 64 блока
    }
    
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        val startPos = npc.getEntity().blockPosition()
        log("Moving from $startPos to $targetPosition", npc)
        
        try {
            // Симуляция движения (в реальной реализации здесь будет код навигации)
            val distance = startPos.distSqr(targetPosition)
            val estimatedTime = (distance * 100 / speed).toLong() // Примерный расчет
            
            var progress = 0.0
            val steps = 10
            val stepTime = estimatedTime / steps
            
            repeat(steps) { step ->
                delay(stepTime)
                progress = (step + 1).toDouble() / steps
                
                // Логируем прогресс каждые 25%
                if ((progress * 100).toInt() % 25 == 0) {
                    log("Movement progress: ${(progress * 100).toInt()}%", npc)
                }
            }
            
            log("Reached target position $targetPosition", npc)
            return ActionExecutionResult.completed(
                "Successfully moved to $targetPosition",
                mapOf("finalPosition" to targetPosition, "travelTime" to estimatedTime)
            )
            
        } catch (e: Exception) {
            logError("Movement failed", npc, e)
            return ActionExecutionResult.failed("Movement failed: ${e.message}")
        }
    }
}

/**
 * Действие наблюдения за целью
 */
class ObserveAction(
    private val targetId: String,
    private val durationMs: Long = 3000L
) : BaseNPCAction(
    name = "Observe",
    type = NPCActionType.OBSERVATION,
    estimatedDurationMs = durationMs,
    description = "Observe target $targetId for ${durationMs}ms"
) {
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Starting observation of $targetId", npc)
        
        try {
            val observations = mutableListOf<String>()
            val observationInterval = durationMs / 5 // 5 наблюдений за время действия
            
            repeat(5) { observation ->
                delay(observationInterval)
                
                // Симулируем наблюдение
                val observationText = generateObservation(targetId, observation)
                observations.add(observationText)
                log("Observation ${observation + 1}: $observationText", npc)
            }
            
            log("Observation completed", npc)
            return ActionExecutionResult.completed(
                "Completed observation of $targetId",
                mapOf("observations" to observations, "targetId" to targetId)
            )
            
        } catch (e: Exception) {
            logError("Observation failed", npc, e)
            return ActionExecutionResult.failed("Observation failed: ${e.message}")
        }
    }
    
    private fun generateObservation(targetId: String, step: Int): String {
        val observations = listOf(
            "$targetId appears to be stationary",
            "$targetId is looking around",
            "$targetId seems to be holding something",
            "$targetId is moving slowly",
            "$targetId appears calm"
        )
        
        return observations.getOrElse(step) { "$targetId continues normal behavior" }
    }
}

/**
 * Действие размышления/анализа
 */
class ThinkAction(
    private val topic: String,
    private val durationMs: Long = 2000L
) : BaseNPCAction(
    name = "Think",
    type = NPCActionType.THINKING,
    estimatedDurationMs = durationMs,
    description = "Think about: $topic"
) {
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Starting to think about: $topic", npc)
        
        try {
            // Симулируем процесс размышления
            val thinkingStages = listOf(
                "Analyzing the situation",
                "Considering options",
                "Evaluating consequences",
                "Reaching conclusion"
            )
            
            val thoughts = mutableListOf<String>()
            val stageTime = durationMs / thinkingStages.size
            
            thinkingStages.forEach { stage ->
                delay(stageTime)
                val thought = "$stage about $topic"
                thoughts.add(thought)
                log(thought, npc)
            }
            
            // Генерируем случайный "вывод"
            val conclusions = listOf(
                "This seems interesting",
                "I need more information",
                "This could be important",
                "I should pay attention to this",
                "This reminds me of something"
            )
            
            val conclusion = conclusions.random()
            thoughts.add(conclusion)
            log("Conclusion: $conclusion", npc)
            
            return ActionExecutionResult.completed(
                "Finished thinking about $topic",
                mapOf("topic" to topic, "thoughts" to thoughts, "conclusion" to conclusion)
            )
            
        } catch (e: Exception) {
            logError("Thinking was interrupted", npc, e)
            return ActionExecutionResult.failed("Thinking was interrupted: ${e.message}")
        }
    }
}

/**
 * Композитное действие - выполняет несколько действий последовательно
 */
class SequentialAction(
    private val actions: List<NPCAction>,
    name: String = "Sequential"
) : BaseNPCAction(
    name = name,
    type = NPCActionType.CUSTOM,
    estimatedDurationMs = actions.sumOf { it.estimatedDurationMs },
    description = "Execute ${actions.size} actions sequentially: ${actions.joinToString(", ") { it.name }}"
) {
    override suspend fun canExecute(npc: SmartNPC): Boolean {
        // Проверяем все действия
        return actions.all { it.canExecute(npc) }
    }
    
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Starting sequential execution of ${actions.size} actions", npc)
        
        val results = mutableListOf<ActionExecutionResult>()
        
        try {
            actions.forEachIndexed { index, action ->
                log("Executing step ${index + 1}/${actions.size}: ${action.name}", npc)
                
                val result = action.execute(npc)
                results.add(result)
                
                if (result.status == ActionStatus.FAILED) {
                    log("Sequential action failed at step ${index + 1}: ${result.message}", npc)
                    return ActionExecutionResult.failed(
                        "Failed at step ${index + 1} (${action.name}): ${result.message}",
                        mapOf("stepResults" to results, "failedStep" to index)
                    )
                }
                
                if (result.status == ActionStatus.CANCELLED) {
                    log("Sequential action cancelled at step ${index + 1}", npc)
                    return ActionExecutionResult.cancelled(
                        "Cancelled at step ${index + 1} (${action.name})",
                        mapOf("stepResults" to results, "cancelledStep" to index)
                    )
                }
            }
            
            log("All ${actions.size} actions completed successfully", npc)
            return ActionExecutionResult.completed(
                "All ${actions.size} actions completed",
                mapOf("stepResults" to results)
            )
            
        } catch (e: Exception) {
            logError("Sequential action interrupted", npc, e)
            return ActionExecutionResult.failed(
                "Sequential action interrupted: ${e.message}",
                mapOf("stepResults" to results)
            )
        }
    }
}

/**
 * Условное действие - выполняется только если условие выполнено
 */
class ConditionalAction(
    private val condition: suspend (SmartNPC) -> Boolean,
    private val action: NPCAction,
    private val conditionDescription: String = "custom condition"
) : BaseNPCAction(
    name = "Conditional(${action.name})",
    type = action.type,
    estimatedDurationMs = action.estimatedDurationMs,
    description = "Execute ${action.name} if $conditionDescription"
) {
    override suspend fun canExecute(npc: SmartNPC): Boolean {
        return condition(npc) && action.canExecute(npc)
    }
    
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Checking condition: $conditionDescription", npc)
        
        if (!condition(npc)) {
            log("Condition not met, skipping action", npc)
            return ActionExecutionResult.completed(
                "Condition not met, action skipped",
                mapOf("conditionMet" to false, "conditionDescription" to conditionDescription)
            )
        }
        
        log("Condition met, executing ${action.name}", npc)
        val result = action.execute(npc)
        
        return result.copy(
            data = result.data + mapOf("conditionMet" to true, "conditionDescription" to conditionDescription)
        )
    }
}

/**
 * Повторяющееся действие
 */
class RepeatAction(
    private val action: NPCAction,
    private val repeatCount: Int,
    private val delayBetweenMs: Long = 0L
) : BaseNPCAction(
    name = "Repeat(${action.name})",
    type = action.type,
    estimatedDurationMs = (action.estimatedDurationMs + delayBetweenMs) * repeatCount,
    description = "Repeat ${action.name} $repeatCount times"
) {
    override suspend fun canExecute(npc: SmartNPC): Boolean {
        return action.canExecute(npc)
    }
    
    override suspend fun execute(npc: SmartNPC): ActionExecutionResult {
        log("Starting to repeat ${action.name} $repeatCount times", npc)
        
        val results = mutableListOf<ActionExecutionResult>()
        
        try {
            repeat(repeatCount) { iteration ->
                log("Iteration ${iteration + 1}/$repeatCount", npc)
                
                val result = action.execute(npc)
                results.add(result)
                
                if (result.status == ActionStatus.FAILED) {
                    log("Repeat action failed at iteration ${iteration + 1}", npc)
                    return ActionExecutionResult.failed(
                        "Failed at iteration ${iteration + 1}: ${result.message}",
                        mapOf("results" to results, "completedIterations" to iteration)
                    )
                }
                
                if (result.status == ActionStatus.CANCELLED) {
                    log("Repeat action cancelled at iteration ${iteration + 1}", npc)
                    return ActionExecutionResult.cancelled(
                        "Cancelled at iteration ${iteration + 1}",
                        mapOf("results" to results, "completedIterations" to iteration)
                    )
                }
                
                // Задержка между повторениями
                if (delayBetweenMs > 0 && iteration < repeatCount - 1) {
                    delay(delayBetweenMs)
                }
            }
            
            log("Completed all $repeatCount iterations", npc)
            return ActionExecutionResult.completed(
                "Completed $repeatCount iterations of ${action.name}",
                mapOf("results" to results, "completedIterations" to repeatCount)
            )
            
        } catch (e: Exception) {
            logError("Repeat action interrupted", npc, e)
            return ActionExecutionResult.failed(
                "Repeat action interrupted: ${e.message}",
                mapOf("results" to results)
            )
        }
    }
}
package com.hollowengineai.mod.actions

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.states.EmotionalState
import kotlinx.coroutines.Deferred
import net.minecraft.world.entity.Entity

/**
 * Базовый интерфейс для исполнителей действий NPC
 */
interface ActionExecutor {
    /**
     * Типы действий, которые может обрабатывать этот исполнитель
     */
    val supportedActions: Set<String>
    
    /**
     * Приоритет исполнителя (больше = выше приоритет)
     */
    val priority: Int
    
    /**
     * Проверить, может ли данный исполнитель обработать действие
     */
    fun canHandle(action: String, npc: SmartNPC, target: Entity? = null): Boolean
    
    /**
     * Асинхронно выполнить действие
     * @return Deferred с результатом выполнения
     */
    suspend fun executeAction(
        action: String,
        npc: SmartNPC,
        target: Entity? = null,
        parameters: Map<String, Any> = emptyMap()
    ): ActionResult
    
    /**
     * Оценить стоимость/сложность выполнения действия
     * Используется для выбора оптимального исполнителя
     */
    fun estimateCost(action: String, npc: SmartNPC, target: Entity? = null): ActionCost
    
    /**
     * Получить список предварительных условий для действия
     */
    fun getPrerequisites(action: String, npc: SmartNPC, target: Entity? = null): List<ActionPrerequisite>
    
    /**
     * Проверить, выполнены ли все предварительные условия
     */
    fun checkPrerequisites(action: String, npc: SmartNPC, target: Entity? = null): Boolean {
        return getPrerequisites(action, npc, target).all { it.isSatisfied(npc, target) }
    }
    
    /**
     * Подготовить NPC к выполнению действия (опционально)
     */
    suspend fun prepareForAction(action: String, npc: SmartNPC, target: Entity? = null): Boolean = true
    
    /**
     * Очистить состояние после выполнения действия (опционально)
     */
    suspend fun cleanupAfterAction(action: String, npc: SmartNPC, result: ActionResult) {}
}

/**
 * Результат выполнения действия
 */
data class ActionResult(
    val success: Boolean,
    val message: String = "",
    val data: Map<String, Any> = emptyMap(),
    val executionTime: Long = 0L,
    val energyCost: Float = 0f,
    val emotionalImpact: EmotionalImpact? = null
)

/**
 * Стоимость выполнения действия
 */
data class ActionCost(
    val energyCost: Float,
    val timeCost: Long, // в миллисекундах
    val riskLevel: Float, // 0.0 - безопасно, 1.0 - очень рискованно
    val socialCost: Float = 0f, // влияние на репутацию
    val resourceCost: Map<String, Int> = emptyMap() // требуемые ресурсы
)

/**
 * Эмоциональное воздействие действия
 */
data class EmotionalImpact(
    val valenceChange: Float, // изменение валентности (-1.0 до 1.0)
    val arousalChange: Float,  // изменение возбуждения (-1.0 до 1.0)
    val dominanceChange: Float = 0f // изменение доминантности (-1.0 до 1.0)
)

/**
 * Предварительное условие для выполнения действия
 */
abstract class ActionPrerequisite {
    abstract val description: String
    abstract fun isSatisfied(npc: SmartNPC, target: Entity? = null): Boolean
}

/**
 * Условие наличия предмета
 */
class ItemPrerequisite(
    private val itemType: String,
    private val minQuantity: Int = 1,
    override val description: String = "Requires $minQuantity x $itemType"
) : ActionPrerequisite() {
    
    override fun isSatisfied(npc: SmartNPC, target: Entity?): Boolean {
        // Здесь нужно проверить инвентарь NPC
        // Пока возвращаем true для совместимости
        return true
    }
}

/**
 * Условие уровня здоровья
 */
class HealthPrerequisite(
    private val minHealthPercent: Float,
    override val description: String = "Requires at least ${(minHealthPercent * 100).toInt()}% health"
) : ActionPrerequisite() {
    
    override fun isSatisfied(npc: SmartNPC, target: Entity?): Boolean {
        val entity = npc.getEntity()
        val health = entity.health / entity.maxHealth
        return health >= minHealthPercent
    }
}

/**
 * Условие дистанции до цели
 */
class DistancePrerequisite(
    private val maxDistance: Double,
    override val description: String = "Target must be within $maxDistance blocks"
) : ActionPrerequisite() {
    
    override fun isSatisfied(npc: SmartNPC, target: Entity?): Boolean {
        return target?.let { npc.getEntity().distanceTo(it) <= maxDistance } ?: true
    }
}

/**
 * Условие эмоционального состояния
 */
class EmotionalPrerequisite(
    private val requiredEmotion: EmotionalState? = null,
    private val forbiddenEmotions: Set<EmotionalState> = emptySet(),
    private val minArousal: Float? = null,
    private val maxArousal: Float? = null,
    private val minValence: Float? = null,
    private val maxValence: Float? = null,
    override val description: String = "Requires specific emotional state"
) : ActionPrerequisite() {
    
    override fun isSatisfied(npc: SmartNPC, target: Entity?): Boolean {
        val emotional = npc.emotionalState
        
        // Проверяем наличие необходимой эмоции
        if (requiredEmotion != null && emotional != requiredEmotion) return false
        
        // Проверяем отсутствие запрещенных эмоций
        if (forbiddenEmotions.contains(emotional)) return false
        
        // Получаем текущие значения arousal и valence
        // Пока используем упрощенное преобразование эмоций в числовые значения
        val currentArousal = getEmotionalArousal(emotional)
        val currentValence = getEmotionalValence(emotional)
        
        // Проверяем ограничения arousal
        if (minArousal != null && currentArousal < minArousal) return false
        if (maxArousal != null && currentArousal > maxArousal) return false
        
        // Проверяем ограничения valence
        if (minValence != null && currentValence < minValence) return false
        if (maxValence != null && currentValence > maxValence) return false
        
        return true
    }
    
    /**
     * Упрощенное преобразование эмоции в arousal (возбуждение)
     */
    private fun getEmotionalArousal(emotion: EmotionalState): Float {
        return when (emotion) {
            EmotionalState.ANGRY -> 0.9f
            EmotionalState.EXCITED -> 0.8f
            EmotionalState.AFRAID, EmotionalState.SCARED, EmotionalState.FEARFUL -> 0.7f
            EmotionalState.SURPRISED -> 0.6f
            EmotionalState.HAPPY -> 0.5f
            EmotionalState.CURIOUS -> 0.4f
            EmotionalState.FOCUSED -> 0.4f
            EmotionalState.NEUTRAL -> 0.3f
            EmotionalState.CONTENT -> 0.3f
            EmotionalState.TIRED -> 0.2f
            EmotionalState.SAD -> 0.2f
            EmotionalState.BORED -> 0.1f
            else -> 0.3f // default для всех остальных
        }
    }
    
    /**
     * Упрощенное преобразование эмоции в valence (валентность)
     */
    private fun getEmotionalValence(emotion: EmotionalState): Float {
        return when (emotion) {
            EmotionalState.HAPPY -> 0.8f
            EmotionalState.EXCITED -> 0.6f
            EmotionalState.CONTENT -> 0.4f
            EmotionalState.FRIENDLY -> 0.5f
            EmotionalState.TRUSTING -> 0.3f
            EmotionalState.CURIOUS -> 0.2f
            EmotionalState.SURPRISED -> 0.3f
            EmotionalState.CALM -> 0.1f
            EmotionalState.FOCUSED -> 0.0f
            EmotionalState.NEUTRAL -> 0.0f
            EmotionalState.CAUTIOUS -> -0.1f
            EmotionalState.BORED -> -0.2f
            EmotionalState.TIRED -> -0.3f
            EmotionalState.SUSPICIOUS -> -0.4f
            EmotionalState.SAD -> -0.5f
            EmotionalState.AFRAID, EmotionalState.SCARED, EmotionalState.FEARFUL -> -0.6f
            EmotionalState.ANGRY -> -0.7f
            EmotionalState.HOSTILE -> -0.8f
            else -> 0.0f // default для всех остальных
        }
    }
}

/**
 * Исключение для ошибок выполнения действий
 */
class ActionExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
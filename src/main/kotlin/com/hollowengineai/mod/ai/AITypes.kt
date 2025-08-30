package com.hollowengineai.mod.ai

import com.hollowengineai.mod.actions.Action
import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.core.SmartNPC
import java.time.Instant
import java.util.*

/**
 * Результат принятия решения ИИ
 */
data class AIDecision(
    val action: Action,
    val confidence: Float,
    val reasoning: String,
    val priority: Int = 50,
    val estimatedDuration: Long = 5000L, // миллисекунды
    val metadata: Map<String, Any> = emptyMap()
) {
    fun isHighConfidence(): Boolean = confidence > 0.7f
    fun isLowConfidence(): Boolean = confidence < 0.3f
}

// DecisionContext перенесен в DecisionEngine.kt чтобы избежать дублирования

/**
 * Эпизод памяти НПС
 */
data class MemoryEpisode(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Instant,
    val type: MemoryType,
    val content: String,
    val participants: List<String> = emptyList(),
    val location: String? = null,
    val importance: Float = 0.5f,
    val emotion: EmotionalState = EmotionalState.NEUTRAL,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class MemoryType {
        INTERACTION,    // Взаимодействие с игроком
        CONVERSATION,   // Разговор
        EVENT,         // Событие в мире
        ACHIEVEMENT,   // Достижение цели
        CONFLICT,      // Конфликт
        TRADE,         // Торговля
        OBSERVATION,   // Наблюдение
        LEARNING,      // Обучение/получение знаний
        EMOTIONAL,     // Эмоциональный момент
        OTHER          // Прочее
    }
    
    fun isRecent(): Boolean {
        return timestamp.isAfter(Instant.now().minusSeconds(3600)) // 1 час
    }
    
    fun isImportant(): Boolean {
        return importance > 0.7f
    }
}

/**
 * Простая заглушка для DecisionEngine до полной реализации ИИ
 */
interface DecisionEngine {
    fun makeDecision(context: DecisionContext): AIDecision?
    fun updateContext(context: DecisionContext)
    fun isAvailable(): Boolean
}

/**
 * Базовая реализация DecisionEngine для совместимости
 */
class BasicDecisionEngine : DecisionEngine {
    override fun makeDecision(context: DecisionContext): AIDecision? {
        // Простая логика принятия решений без LLM
        val availableActions = context.availableActions
        if (availableActions.isEmpty()) return null
        
        // Выбираем случайное действие с низкой уверенностью
        val randomAction = availableActions.random()
        return AIDecision(
            action = randomAction,
            confidence = 0.3f,
            reasoning = "Basic random decision making",
            priority = 25
        )
    }
    
    override fun updateContext(context: DecisionContext) {
        // Базовая реализация - ничего не делаем
    }
    
    override fun isAvailable(): Boolean = true
}

/**
 * Заглушка для продвинутого DecisionEngine с LLM
 */
class LLMDecisionEngine : DecisionEngine {
    private var available = false
    
    override fun makeDecision(context: DecisionContext): AIDecision? {
        // TODO: Интеграция с LLM (Ollama)
        return BasicDecisionEngine().makeDecision(context)
    }
    
    override fun updateContext(context: DecisionContext) {
        // TODO: Обновление контекста для LLM
    }
    
    override fun isAvailable(): Boolean = available
    
    fun setAvailable(available: Boolean) {
        this.available = available
    }
}
package com.hollowengineai.mod.core

import com.hollowengineai.mod.actions.Action
import com.hollowengineai.mod.actions.ActionType
import com.hollowengineai.mod.llm.OllamaClient
import com.hollowengineai.mod.memory.MemoryEpisode
import kotlinx.coroutines.withTimeout
import net.minecraft.core.BlockPos
import org.apache.logging.log4j.LogManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.*

/**
 * Движок принятия решений для AI НПС
 * 
 * Интегрируется с Ollama LLM для:
 * - Анализа текущей ситуации
 * - Принятия решений на основе личности и памяти
 * - Генерации последовательности действий
 * - Эмоциональных реакций
 */
class DecisionEngine(
    private val ollamaClient: OllamaClient
) {
    companion object {
        private val LOGGER = LogManager.getLogger(DecisionEngine::class.java)
        private val gson = Gson()
        private const val DECISION_TIMEOUT_MS = 5000L // 5 секунд на принятие решения
        private const val MAX_ACTIONS_PER_DECISION = 3
    }
    
    // Кэш для часто используемых решений
    private val decisionCache = mutableMapOf<String, AIDecision>()
    private val fallbackDecisions = FallbackDecisionProvider()
    
    /**
     * Принять решение на основе текущего контекста
     */
    suspend fun makeDecision(npc: SmartNPC, context: DecisionContext): AIDecision? {
        return try {
            // Проверяем кэш для похожих ситуаций
            val cacheKey = generateCacheKey(context)
            decisionCache[cacheKey]?.let { cachedDecision ->
                LOGGER.debug("Using cached decision for ${npc.name}")
                return@makeDecision cachedDecision
            }
            
            // Делаем запрос к LLM с таймаутом
            val decision = withTimeout(DECISION_TIMEOUT_MS) {
                queryLLMForDecision(npc, context)
            }
            
            // Кэшируем решение если оно валидное
            decision?.let {
                decisionCache[cacheKey] = it
                // Ограничиваем размер кэша
                if (decisionCache.size > 100) {
                    val oldestKey = decisionCache.keys.first()
                    decisionCache.remove(oldestKey)
                }
            }
            
            decision
            
        } catch (e: Exception) {
            LOGGER.error("Failed to make decision for ${npc.name}", e)
            
            // Возвращаем fallback решение
            fallbackDecisions.getDecision(context)
        }
    }
    
    /**
     * Запросить решение от LLM
     */
    private suspend fun queryLLMForDecision(npc: SmartNPC, context: DecisionContext): AIDecision? {
        val prompt = buildDecisionPrompt(npc, context)
        
        LOGGER.debug("Querying LLM for decision: ${npc.name}")
        
        val response = ollamaClient.generateResponse(prompt, model = "llama2")
        
        return if (response.isSuccess) {
            parseDecisionResponse(response.text)
        } else {
            LOGGER.warn("LLM request failed for ${npc.name}: ${response.error}")
            null
        }
    }
    
    /**
     * Построить промпт для LLM
     */
    private fun buildDecisionPrompt(npc: SmartNPC, context: DecisionContext): String {
        return buildString {
            appendLine("You are an AI NPC named '${npc.name}' in a Minecraft world.")
            appendLine("Your personality: ${npc.personalityType.name} - ${npc.personalityType.description}")
            appendLine()
            
            // Текущая ситуация
            appendLine("CURRENT SITUATION:")
            appendLine("- Position: ${context.position}")
            appendLine("- Current emotion: ${context.currentEmotion}")
            appendLine("- Current goal: ${context.currentGoal ?: "No specific goal"}")
            appendLine("- Time of day: ${getTimeDescription(context.timeOfDay)}")
            appendLine("- Weather: ${if (context.isRaining) "Raining" else "Clear"}")
            appendLine()
            
            // Ближайшие игроки
            if (context.nearbyPlayers.isNotEmpty()) {
                appendLine("NEARBY PLAYERS:")
                context.nearbyPlayers.forEach { player ->
                    appendLine("- $player")
                }
                appendLine()
            }
            
            // Недавняя память
            if (context.recentMemories.isNotEmpty()) {
                appendLine("RECENT MEMORIES:")
                context.recentMemories.take(5).forEach { memory ->
                    appendLine("- ${memory.description}")
                }
                appendLine()
            }
            
            // Черты личности
            appendLine("PERSONALITY TRAITS:")
            val traits = context.personalityTraits
            appendLine("- Friendliness: ${traits.friendliness}")
            appendLine("- Curiosity: ${traits.curiosity}")
            appendLine("- Aggressiveness: ${traits.aggressiveness}")
            appendLine("- Intelligence: ${traits.intelligence}")
            appendLine("- Creativity: ${traits.creativity}")
            appendLine()
            
            // Доступные действия
            appendLine("AVAILABLE ACTIONS:")
            ActionType.values().forEach { action ->
                appendLine("- ${action.name}: ${action.description}")
            }
            appendLine()
            
            // Инструкции для ответа
            appendLine("Based on your personality, current situation, and memories, decide what to do next.")
            appendLine("Respond ONLY in valid JSON format:")
            appendLine("""
            {
              "goal": "your current goal or objective",
              "emotionalResponse": "HAPPY|SAD|ANGRY|EXCITED|NEUTRAL|CURIOUS|CONTENT",
              "reasoning": "brief explanation of your decision",
              "actions": [
                {
                  "type": "ACTION_TYPE",
                  "target": "target_name_or_null",
                  "parameters": {
                    "param1": "value1"
                  }
                }
              ]
            }
            """.trimIndent())
        }
    }
    
    /**
     * Парсить ответ от LLM в структуру решения
     */
    private fun parseDecisionResponse(response: String): AIDecision? {
        try {
            // Извлекаем JSON из ответа (на случай если есть дополнительный текст)
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')
            
            if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
                LOGGER.warn("Invalid JSON format in LLM response")
                return null
            }
            
            val jsonText = response.substring(jsonStart, jsonEnd + 1)
            val jsonResponse = gson.fromJson(jsonText, LLMDecisionResponse::class.java)
            
            // Валидация ответа
            if (jsonResponse.actions.isEmpty()) {
                LOGGER.warn("LLM response contains no actions")
                return null
            }
            
            if (jsonResponse.actions.size > MAX_ACTIONS_PER_DECISION) {
                LOGGER.warn("Too many actions in LLM response, truncating to $MAX_ACTIONS_PER_DECISION")
            }
            
            // Преобразуем в внутренний формат
            val actions = jsonResponse.actions
                .take(MAX_ACTIONS_PER_DECISION)
                .mapNotNull { actionData ->
                    try {
                        Action(
                            type = ActionType.valueOf(actionData.type),
                            target = actionData.target,
                            parameters = actionData.parameters ?: emptyMap()
                        )
                    } catch (e: IllegalArgumentException) {
                        LOGGER.warn("Unknown action type: ${actionData.type}")
                        null
                    }
                }
            
            if (actions.isEmpty()) {
                LOGGER.warn("No valid actions found in LLM response")
                return null
            }
            
            val emotion = try {
                EmotionalState.valueOf(jsonResponse.emotionalResponse)
            } catch (e: IllegalArgumentException) {
                LOGGER.warn("Unknown emotional state: ${jsonResponse.emotionalResponse}")
                EmotionalState.NEUTRAL
            }
            
            return AIDecision(
                goal = jsonResponse.goal,
                emotionalResponse = emotion,
                reasoning = jsonResponse.reasoning,
                actions = actions
            )
            
        } catch (e: JsonSyntaxException) {
            LOGGER.error("Failed to parse LLM JSON response", e)
            return null
        } catch (e: Exception) {
            LOGGER.error("Unexpected error parsing LLM response", e)
            return null
        }
    }
    
    /**
     * Генерировать ключ кэша для контекста
     */
    private fun generateCacheKey(context: DecisionContext): String {
        return buildString {
            append(context.currentState)
            append(":")
            append(context.currentEmotion)
            append(":")
            append(context.nearbyPlayers.sorted().joinToString(","))
            append(":")
            append(context.isRaining)
        }
    }
    
    /**
     * Получить описание времени дня
     */
    private fun getTimeDescription(timeOfDay: Long): String {
        val time = timeOfDay % 24000L
        return when {
            time < 6000L -> "Morning"
            time < 12000L -> "Day"
            time < 18000L -> "Evening"
            else -> "Night"
        }
    }
}

/**
 * Контекст для принятия решений
 */
data class DecisionContext(
    val npcId: UUID,
    val npcName: String,
    val position: BlockPos,
    val currentState: NPCState,
    val currentEmotion: EmotionalState,
    val currentGoal: String?,
    val nearbyPlayers: List<String>,
    val recentMemories: List<MemoryEpisode>,
    val timeOfDay: Long,
    val isRaining: Boolean,
    val personalityTraits: PersonalityTraits
)

/**
 * Решение принятое AI системой
 */
data class AIDecision(
    val goal: String,
    val emotionalResponse: EmotionalState,
    val reasoning: String,
    val actions: List<Action>
)

/**
 * Ответ от LLM в JSON формате
 */
private data class LLMDecisionResponse(
    val goal: String,
    val emotionalResponse: String,
    val reasoning: String,
    val actions: List<LLMActionData>
)

private data class LLMActionData(
    val type: String,
    val target: String?,
    val parameters: Map<String, String>?
)

/**
 * Провайдер fallback решений на случай недоступности LLM
 */
private class FallbackDecisionProvider {
    private val LOGGER = LogManager.getLogger(FallbackDecisionProvider::class.java)
    
    fun getDecision(context: DecisionContext): AIDecision {
        LOGGER.debug("Using fallback decision for ${context.npcName}")
        
        return when {
            context.nearbyPlayers.isNotEmpty() -> {
                // Если рядом игроки - поприветствовать
                AIDecision(
                    goal = "Greet nearby players",
                    emotionalResponse = EmotionalState.NEUTRAL,
                    reasoning = "Fallback: greeting players",
                    actions = listOf(
                        Action(
                            type = ActionType.SPEAK,
                            target = context.nearbyPlayers.first(),
                            parameters = mapOf("message" to "Hello there!")
                        ),
                        Action(
                            type = ActionType.LOOK_AT,
                            target = context.nearbyPlayers.first(),
                            parameters = emptyMap()
                        )
                    )
                )
            }
            
            context.currentEmotion == EmotionalState.NEUTRAL && Math.random() > 0.7 -> {
                // Случайное исследование
                AIDecision(
                    goal = "Explore surroundings",
                    emotionalResponse = EmotionalState.CURIOUS,
                    reasoning = "Fallback: random exploration",
                    actions = listOf(
                        Action(
                            type = ActionType.MOVE,
                            target = null,
                            parameters = mapOf(
                                "direction" to listOf("north", "south", "east", "west").random()
                            )
                        )
                    )
                )
            }
            
            else -> {
                // Ничего не делать - простое ожидание
                AIDecision(
                    goal = "Wait and observe",
                    emotionalResponse = EmotionalState.CONTENT,
                    reasoning = "Fallback: idle state",
                    actions = listOf(
                        Action(
                            type = ActionType.WAIT,
                            target = null,
                            parameters = mapOf("duration" to "5000")
                        )
                    )
                )
            }
        }
    }
}
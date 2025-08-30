package com.hollowengineai.mod.integration

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.core.NPCManager
import com.hollowengineai.mod.core.PersonalityType
import com.hollowengineai.mod.core.DecisionEngine
import com.hollowengineai.mod.memory.NPCMemory
import com.hollowengineai.mod.actions.ActionExecutor
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.HollowEngineAIMod
import net.minecraft.world.entity.LivingEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Система-мост для интеграции HollowEngineAI с обычными НПС HollowEngine Legacy
 * 
 * Позволяет:
 * - Привязывать AI к существующим НПС
 * - Синхронизировать состояния между системами
 * - Управлять жизненным циклом AI НПС
 */
object HollowEngineBridge {
    private val LOGGER = LogManager.getLogger(HollowEngineBridge::class.java)
    
    // Карта привязки: ID HollowEngine НПС -> SmartNPC
    private val npcBindings = ConcurrentHashMap<String, SmartNPC>()
    
    // Карта обратной привязки: SmartNPC -> HollowEngine НПС ID
    private val reverseBindings = ConcurrentHashMap<UUID, String>()
    
    /**
     * Привязать AI к существующему НПС HollowEngine Legacy
     */
    fun enhanceNPC(
        npcId: String,
        npcEntity: LivingEntity,
        personality: NPCPersonality
    ): SmartNPC? {
        try {
            LOGGER.info("Enhancing HollowEngine NPC $npcId with AI")
            
            // Проверяем, не привязан ли уже этот НПС
            if (npcBindings.containsKey(npcId)) {
                LOGGER.warn("NPC $npcId is already enhanced with AI")
                return npcBindings[npcId]
            }
            
            // Создаем компоненты AI
            val aiId = UUID.randomUUID()
            val memory = NPCMemory(aiId, personality.name)
            val actionExecutor = ActionExecutor()
            val decisionEngine = DecisionEngine(
                HollowEngineAIMod.ollamaClient,
                memory
            )
            
            // Создаем SmartNPC
            val smartNPC = SmartNPC(
                id = aiId,
                name = personality.name,
                personalityType = personality.personalityType,
                entity = npcEntity,
                memory = memory,
                actionExecutor = actionExecutor,
                decisionEngine = decisionEngine
            )
            
            // Настраиваем личность
            memory.setPersonalityTraits(personality.traits)
            memory.setBiography(personality.biography)
            memory.setGoals(personality.goals)
            
            // Сохраняем привязки
            npcBindings[npcId] = smartNPC
            reverseBindings[aiId] = npcId
            
            // Запускаем AI
            smartNPC.start()
            
            // Публикуем событие
            NPCEventBus.publishEvent(
                com.hollowengineai.mod.events.NPCEvent.System(
                    "npc_enhanced: $npcId"
                )
            )
            
            LOGGER.info("Successfully enhanced NPC $npcId with AI")
            return smartNPC
            
        } catch (e: Exception) {
            LOGGER.error("Failed to enhance NPC $npcId", e)
            return null
        }
    }
    
    /**
     * Отвязать AI от НПС
     */
    fun removeAI(npcId: String): Boolean {
        val smartNPC = npcBindings.remove(npcId) ?: return false
        
        try {
            // Останавливаем AI
            smartNPC.stop()
            
            // Удаляем обратную привязку
            reverseBindings.remove(smartNPC.id)
            
            LOGGER.info("Removed AI from NPC $npcId")
            return true
            
        } catch (e: Exception) {
            LOGGER.error("Error removing AI from NPC $npcId", e)
            return false
        }
    }
    
    /**
     * Получить SmartNPC по ID HollowEngine НПС
     */
    fun getSmartNPC(npcId: String): SmartNPC? = npcBindings[npcId]
    
    /**
     * Получить ID HollowEngine НПС по SmartNPC
     */
    fun getHollowEngineId(smartNPC: SmartNPC): String? = reverseBindings[smartNPC.id]
    
    /**
     * Проверить, привязан ли НПС к AI
     */
    fun isEnhanced(npcId: String): Boolean = npcBindings.containsKey(npcId)
    
    /**
     * Получить все активные AI НПС
     */
    fun getAllEnhancedNPCs(): Collection<SmartNPC> = npcBindings.values
    
    /**
     * Синхронизировать действие HollowEngine НПС с AI
     */
    fun syncHollowEngineAction(npcId: String, action: String, data: Map<String, Any> = emptyMap()) {
        val smartNPC = npcBindings[npcId] ?: return
        
        try {
            // Уведомляем AI о действии из HollowEngine
            smartNPC.memory.addEpisode(
                "hollow_engine_action", 
                "Performed action: $action with data: $data"
            )
            
            // Логируем для отладки
            LOGGER.debug("Synced HollowEngine action $action for NPC $npcId")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to sync action $action for NPC $npcId", e)
        }
    }
    
    /**
     * Отправить прямой промт AI НПС
     */
    fun sendDirectPrompt(npcId: String, prompt: String, context: Map<String, Any> = emptyMap()): Boolean {
        val smartNPC = npcBindings[npcId] ?: return false
        
        try {
            LOGGER.info("Sending direct AI prompt to NPC $npcId: $prompt")
            
            // Добавляем промт как высокоприоритетную цель
            smartNPC.memory.addEpisode(
                "direct_instruction",
                "Received direct instruction: $prompt"
            )
            
            // Добавляем как временную цель с высоким приоритетом
            smartNPC.memory.addGoal("Execute instruction: $prompt")
            
            // Добавляем контекстную информацию если есть
            if (context.isNotEmpty()) {
                val contextStr = context.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                smartNPC.memory.addEpisode(
                    "instruction_context",
                    "Context for instruction: $contextStr"
                )
            }
            
            // Уведомляем AI о получении прямой инструкции
            NPCEventBus.publishEvent(
                com.hollowengineai.mod.events.NPCEvent.GoalUpdated(
                    smartNPC.id.toString(),
                    "Execute instruction: $prompt"
                )
            )
            
            // Запускаем немедленное принятие решения
            smartNPC.decisionEngine.makeDecision(smartNPC, "direct_instruction")
            
            LOGGER.info("Direct AI prompt sent successfully to NPC $npcId")
            return true
            
        } catch (e: Exception) {
            LOGGER.error("Failed to send direct prompt to NPC $npcId", e)
            return false
        }
    }
    
    /**
     * Отправить групповой промт нескольким AI НПС
     */
    fun sendGroupPrompt(
        npcIds: List<String>, 
        prompt: String, 
        context: Map<String, Any> = emptyMap()
    ): Int {
        var successCount = 0
        
        LOGGER.info("Sending group AI prompt to ${npcIds.size} NPCs: $prompt")
        
        npcIds.forEach { npcId ->
            if (sendDirectPrompt(npcId, prompt, context)) {
                successCount++
                
                // Добавляем информацию о групповой инструкции
                val smartNPC = npcBindings[npcId]
                smartNPC?.memory?.addEpisode(
                    "group_instruction",
                    "Part of group instruction with: ${npcIds.joinToString(", ")}"
                )
            }
        }
        
        LOGGER.info("Group AI prompt sent to $successCount/${npcIds.size} NPCs")
        return successCount
    }
    
    /**
     * Отправить промт всем AI НПС в радиусе от позиции
     */
    fun sendAreaPrompt(
        centerX: Double, centerY: Double, centerZ: Double,
        radius: Double,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): Int {
        val npcIdsInArea = npcBindings.filter { (npcId, smartNPC) ->
            val entity = smartNPC.entity
            val distance = kotlin.math.sqrt(
                (entity.x - centerX).let { it * it } +
                (entity.y - centerY).let { it * it } +
                (entity.z - centerZ).let { it * it }
            )
            distance <= radius
        }.keys.toList()
        
        if (npcIdsInArea.isEmpty()) {
            LOGGER.info("No AI NPCs found in radius $radius around ($centerX, $centerY, $centerZ)")
            return 0
        }
        
        val enhancedContext = context.toMutableMap().apply {
            put("area_center", "$centerX, $centerY, $centerZ")
            put("area_radius", radius)
            put("affected_npcs", npcIdsInArea.size)
        }
        
        return sendGroupPrompt(npcIdsInArea, prompt, enhancedContext)
    }
    
    /**
     * Получить статус выполнения промта для НПС
     */
    fun getPromptStatus(npcId: String): String? {
        val smartNPC = npcBindings[npcId] ?: return null
        
        // Получаем последние цели НПС
        val activeGoals = smartNPC.memory.getActiveGoals()
        val instructionGoals = activeGoals.filter { it.startsWith("Execute instruction:") }
        
        return when {
            instructionGoals.isEmpty() -> "No active instructions"
            instructionGoals.size == 1 -> "Executing: ${instructionGoals.first()}"
            else -> "Executing ${instructionGoals.size} instructions"
        }
    }
    
    /**
     * Отменить активные промты для НПС
     */
    fun cancelPrompts(npcId: String): Boolean {
        val smartNPC = npcBindings[npcId] ?: return false
        
        try {
            // Удаляем все цели-инструкции
            val activeGoals = smartNPC.memory.getActiveGoals()
            val instructionGoals = activeGoals.filter { it.startsWith("Execute instruction:") }
            
            instructionGoals.forEach { goal ->
                smartNPC.memory.removeGoal(goal)
            }
            
            // Добавляем запись об отмене
            smartNPC.memory.addEpisode(
                "instruction_cancelled",
                "Previous instructions were cancelled"
            )
            
            LOGGER.info("Cancelled ${instructionGoals.size} active prompts for NPC $npcId")
            return true
            
        } catch (e: Exception) {
            LOGGER.error("Failed to cancel prompts for NPC $npcId", e)
            return false
        }
    }
    
    /**
     * Очистить все привязки (при выгрузке мода)
     */
    fun shutdown() {
        LOGGER.info("Shutting down HollowEngine bridge")
        
        // Останавливаем все AI НПС
        npcBindings.values.forEach { smartNPC ->
            try {
                smartNPC.stop()
            } catch (e: Exception) {
                LOGGER.error("Error stopping SmartNPC ${smartNPC.id}", e)
            }
        }
        
        npcBindings.clear()
        reverseBindings.clear()
    }
}
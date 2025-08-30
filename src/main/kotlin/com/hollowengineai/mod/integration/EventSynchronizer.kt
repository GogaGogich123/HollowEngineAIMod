package com.hollowengineai.mod.integration

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEvent
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Система синхронизации событий между HollowEngine Legacy и HollowEngineAI
 * 
 * Обеспечивает:
 * - Передачу событий из HollowEngine в AI систему
 * - Передачу AI событий обратно в HollowEngine
 * - Синхронизацию состояний НПС
 */
object EventSynchronizer {
    private val LOGGER = LogManager.getLogger(EventSynchronizer::class.java)
    
    // Регистрация обработчиков событий HollowEngine
    private val hollowEngineEventHandlers = ConcurrentHashMap<String, (String, Map<String, Any>) -> Unit>()
    
    // Регистрация обработчиков AI событий
    private val aiEventHandlers = ConcurrentHashMap<String, (SmartNPC, NPCEvent) -> Unit>()
    
    init {
        // Инициализируем базовые обработчики
        setupDefaultHandlers()
        LOGGER.info("EventSynchronizer initialized")
    }
    
    /**
     * Событие из HollowEngine Legacy направляется в AI систему
     */
    fun onHollowEngineEvent(npcId: String, eventType: String, data: Map<String, Any> = emptyMap()) {
        val smartNPC = HollowEngineBridge.getSmartNPC(npcId) ?: return
        
        try {
            LOGGER.debug("Processing HollowEngine event: $eventType for NPC $npcId")
            
            // Обрабатываем стандартные события
            when (eventType) {
                "player_interaction" -> handlePlayerInteraction(smartNPC, data)
                "movement" -> handleMovement(smartNPC, data)
                "dialogue_started" -> handleDialogueStarted(smartNPC, data)
                "dialogue_ended" -> handleDialogueEnded(smartNPC, data)
                "item_given" -> handleItemGiven(smartNPC, data)
                "item_taken" -> handleItemTaken(smartNPC, data)
                "combat_started" -> handleCombatStarted(smartNPC, data)
                "combat_ended" -> handleCombatEnded(smartNPC, data)
                "task_assigned" -> handleTaskAssigned(smartNPC, data)
                "task_completed" -> handleTaskCompleted(smartNPC, data)
                "environment_change" -> handleEnvironmentChange(smartNPC, data)
                else -> handleCustomEvent(smartNPC, eventType, data)
            }
            
            // Вызываем пользовательские обработчики
            hollowEngineEventHandlers[eventType]?.invoke(npcId, data)
            
        } catch (e: Exception) {
            LOGGER.error("Error processing HollowEngine event $eventType for NPC $npcId", e)
        }
    }
    
    /**
     * Событие из AI системы направляется в HollowEngine Legacy
     */
    fun onAIEvent(smartNPC: SmartNPC, event: NPCEvent) {
        val npcId = HollowEngineBridge.getHollowEngineId(smartNPC) ?: return
        
        try {
            LOGGER.debug("Processing AI event: ${event.type} for NPC $npcId")
            
            // Обрабатываем AI события
            when (event) {
                is NPCEvent.ActionDecision -> handleActionDecision(npcId, smartNPC, event)
                is NPCEvent.EmotionChange -> handleEmotionChange(npcId, smartNPC, event)
                is NPCEvent.GoalUpdated -> handleGoalUpdated(npcId, smartNPC, event)
                is NPCEvent.MemoryAdded -> handleMemoryAdded(npcId, smartNPC, event)
                is NPCEvent.SocialInteraction -> handleSocialInteraction(npcId, smartNPC, event)
                is NPCEvent.System -> handleSystemEvent(npcId, smartNPC, event)
                else -> handleCustomAIEvent(npcId, smartNPC, event)
            }
            
            // Вызываем пользовательские обработчики
            aiEventHandlers[event.type]?.invoke(smartNPC, event)
            
        } catch (e: Exception) {
            LOGGER.error("Error processing AI event ${event.type} for NPC $npcId", e)
        }
    }
    
    /**
     * Регистрация пользовательского обработчика событий HollowEngine
     */
    fun registerHollowEngineEventHandler(
        eventType: String,
        handler: (npcId: String, data: Map<String, Any>) -> Unit
    ) {
        hollowEngineEventHandlers[eventType] = handler
        LOGGER.info("Registered HollowEngine event handler for: $eventType")
    }
    
    /**
     * Регистрация пользовательского обработчика AI событий
     */
    fun registerAIEventHandler(
        eventType: String,
        handler: (smartNPC: SmartNPC, event: NPCEvent) -> Unit
    ) {
        aiEventHandlers[eventType] = handler
        LOGGER.info("Registered AI event handler for: $eventType")
    }
    
    // === Обработчики событий HollowEngine ===
    
    private fun handlePlayerInteraction(smartNPC: SmartNPC, data: Map<String, Any>) {
        val playerName = data["player_name"] as? String ?: "Unknown Player"
        val interactionType = data["interaction_type"] as? String ?: "general"
        
        smartNPC.memory.addEpisode(
            "player_interaction",
            "Player $playerName interacted with me (type: $interactionType)"
        )
        
        // Обновляем отношения с игроком
        val currentRelation = smartNPC.memory.getRelationship(playerName)
        smartNPC.memory.setRelationship(playerName, currentRelation + 0.1f)
    }
    
    private fun handleMovement(smartNPC: SmartNPC, data: Map<String, Any>) {
        val fromPos = data["from"] as? String
        val toPos = data["to"] as? String
        
        smartNPC.memory.addEpisode(
            "movement",
            "Moved from $fromPos to $toPos"
        )
    }
    
    private fun handleDialogueStarted(smartNPC: SmartNPC, data: Map<String, Any>) {
        val participant = data["participant"] as? String ?: "Unknown"
        
        smartNPC.memory.addEpisode(
            "dialogue",
            "Started dialogue with $participant"
        )
        
        // Уведомляем AI о начале диалога
        NPCEventBus.publishEvent(
            NPCEvent.SocialInteraction(
                smartNPC.id.toString(),
                "dialogue_started",
                participant
            )
        )
    }
    
    private fun handleDialogueEnded(smartNPC: SmartNPC, data: Map<String, Any>) {
        val participant = data["participant"] as? String ?: "Unknown"
        val outcome = data["outcome"] as? String ?: "neutral"
        
        smartNPC.memory.addEpisode(
            "dialogue",
            "Ended dialogue with $participant (outcome: $outcome)"
        )
    }
    
    private fun handleItemGiven(smartNPC: SmartNPC, data: Map<String, Any>) {
        val item = data["item"] as? String ?: "unknown item"
        val giver = data["giver"] as? String ?: "someone"
        
        smartNPC.memory.addEpisode(
            "item_exchange",
            "Received $item from $giver"
        )
        
        // Улучшаем отношения с дающим
        val currentRelation = smartNPC.memory.getRelationship(giver)
        smartNPC.memory.setRelationship(giver, currentRelation + 0.2f)
    }
    
    private fun handleItemTaken(smartNPC: SmartNPC, data: Map<String, Any>) {
        val item = data["item"] as? String ?: "unknown item"
        val taker = data["taker"] as? String ?: "someone"
        
        smartNPC.memory.addEpisode(
            "item_exchange",
            "Gave $item to $taker"
        )
    }
    
    private fun handleCombatStarted(smartNPC: SmartNPC, data: Map<String, Any>) {
        val opponent = data["opponent"] as? String ?: "unknown enemy"
        
        smartNPC.memory.addEpisode(
            "combat",
            "Combat started with $opponent"
        )
        
        // Ухудшаем отношения с противником
        smartNPC.memory.setRelationship(opponent, -0.8f)
    }
    
    private fun handleCombatEnded(smartNPC: SmartNPC, data: Map<String, Any>) {
        val opponent = data["opponent"] as? String ?: "unknown enemy"
        val result = data["result"] as? String ?: "unknown"
        
        smartNPC.memory.addEpisode(
            "combat",
            "Combat ended with $opponent (result: $result)"
        )
    }
    
    private fun handleTaskAssigned(smartNPC: SmartNPC, data: Map<String, Any>) {
        val task = data["task"] as? String ?: "unknown task"
        val assigner = data["assigner"] as? String ?: "someone"
        
        smartNPC.memory.addGoal("Complete task: $task")
        smartNPC.memory.addEpisode(
            "task",
            "Assigned task '$task' by $assigner"
        )
    }
    
    private fun handleTaskCompleted(smartNPC: SmartNPC, data: Map<String, Any>) {
        val task = data["task"] as? String ?: "unknown task"
        
        smartNPC.memory.addEpisode(
            "task",
            "Completed task: $task"
        )
    }
    
    private fun handleEnvironmentChange(smartNPC: SmartNPC, data: Map<String, Any>) {
        val change = data["change"] as? String ?: "unknown change"
        
        smartNPC.memory.addEpisode(
            "environment",
            "Environment changed: $change"
        )
    }
    
    private fun handleCustomEvent(smartNPC: SmartNPC, eventType: String, data: Map<String, Any>) {
        val details = data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        
        smartNPC.memory.addEpisode(
            "custom_event",
            "Custom event '$eventType' with data: $details"
        )
    }
    
    // === Обработчики AI событий ===
    
    private fun handleActionDecision(npcId: String, smartNPC: SmartNPC, event: NPCEvent.ActionDecision) {
        // Уведомляем HollowEngine о решении AI
        LOGGER.debug("AI decided action for NPC $npcId: ${event.action}")
        
        // Здесь может быть интеграция с системой действий HollowEngine
        // Например, отправка команды выполнить определенное действие
    }
    
    private fun handleEmotionChange(npcId: String, smartNPC: SmartNPC, event: NPCEvent.EmotionChange) {
        LOGGER.debug("Emotion changed for NPC $npcId: ${event.emotion} = ${event.intensity}")
        
        // Можно синхронизировать эмоции с визуальными эффектами в HollowEngine
    }
    
    private fun handleGoalUpdated(npcId: String, smartNPC: SmartNPC, event: NPCEvent.GoalUpdated) {
        LOGGER.debug("Goal updated for NPC $npcId: ${event.goal}")
        
        // Можно обновить отображение целей в HollowEngine UI
    }
    
    private fun handleMemoryAdded(npcId: String, smartNPC: SmartNPC, event: NPCEvent.MemoryAdded) {
        LOGGER.debug("Memory added for NPC $npcId: ${event.memory}")
        
        // Можно логировать важные воспоминания в HollowEngine
    }
    
    private fun handleSocialInteraction(npcId: String, smartNPC: SmartNPC, event: NPCEvent.SocialInteraction) {
        LOGGER.debug("Social interaction for NPC $npcId: ${event.interactionType} with ${event.target}")
        
        // Можно синхронизировать социальные взаимодействия
    }
    
    private fun handleSystemEvent(npcId: String, smartNPC: SmartNPC, event: NPCEvent.System) {
        LOGGER.debug("System event for NPC $npcId: ${event.message}")
    }
    
    private fun handleCustomAIEvent(npcId: String, smartNPC: SmartNPC, event: NPCEvent) {
        LOGGER.debug("Custom AI event for NPC $npcId: ${event.type}")
    }
    
    /**
     * Настройка обработчиков по умолчанию
     */
    private fun setupDefaultHandlers() {
        // Подписываемся на AI события
        NPCEventBus.subscribe { event ->
            // Находим SmartNPC по событию и обрабатываем
            when (event) {
                is NPCEvent.ActionDecision -> {
                    val smartNPC = findSmartNPCById(event.npcId)
                    if (smartNPC != null) {
                        onAIEvent(smartNPC, event)
                    }
                }
                is NPCEvent.EmotionChange -> {
                    val smartNPC = findSmartNPCById(event.npcId)
                    if (smartNPC != null) {
                        onAIEvent(smartNPC, event)
                    }
                }
                // Добавить остальные типы событий по необходимости
                else -> {
                    // Обработка других событий
                }
            }
        }
    }
    
    /**
     * Найти SmartNPC по ID
     */
    private fun findSmartNPCById(npcId: String): SmartNPC? {
        return HollowEngineBridge.getAllEnhancedNPCs().find { it.id.toString() == npcId }
    }
    
    /**
     * Очистка при выключении
     */
    fun shutdown() {
        LOGGER.info("Shutting down EventSynchronizer")
        hollowEngineEventHandlers.clear()
        aiEventHandlers.clear()
    }
}
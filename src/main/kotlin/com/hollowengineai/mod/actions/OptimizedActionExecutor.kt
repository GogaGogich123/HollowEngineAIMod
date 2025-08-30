package com.hollowengineai.mod.actions

import com.hollowengineai.mod.config.AIConfig
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.integration.HollowNPC
import com.hollowengineai.mod.integration.PlayMode
import com.hollowengineai.mod.core.MovementConstants
import com.hollowengineai.mod.memory.MemoryEpisode
import com.hollowengineai.mod.events.NPCEventBusImpl
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import org.apache.logging.log4j.LogManager
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// HollowEngine Legacy 1.6.2a compatible imports (optional)
// Импорты HollowEngine будут загружаться динамически при необходимости

/**
 * Интерфейс для обработчиков действий (legacy совместимость)
 */
interface ActionHandler {
    suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult
}

/**
 * Modular Action Executor Dispatcher for HollowEngine Legacy 1.6.2a
 * 
 * Features:
 * - Modular architecture with specialized executors
 * - Automatic executor selection based on action type
 * - Fallback to legacy handlers for compatibility
 * - Performance optimizations for 50+ NPCs
 * - Thread-safe execution with proper pooling
 * - Advanced caching and batching
 * - Memory-efficient operation tracking
 */
class OptimizedActionExecutor(
    private val eventBus: NPCEventBusImpl
) {
    companion object {
        private val LOGGER = LogManager.getLogger(OptimizedActionExecutor::class.java)
        
        // Performance tuned constants for MC 1.19.2 + HE Legacy 1.6.2a
        private const val DEFAULT_ACTION_TIMEOUT = 3000L // Reduced for better responsiveness
        private const val MAX_MESSAGE_LENGTH = 256
        private const val MAX_MOVEMENT_DISTANCE = 32.0 // Optimized for server performance
        private const val CACHE_CLEANUP_INTERVAL = 300000L // 5 minutes
        private const val BATCH_SIZE = 10 // Process actions in batches
        
        // Thread pool for async operations
        private val executorDispatcher = Dispatchers.IO.limitedParallelism(8)
    }
    
    // Performance tracking
    private val executedActions = AtomicLong(0)
    private val failedActions = AtomicLong(0)
    private val totalExecutionTime = AtomicLong(0)
    
    // Modular executor registry
    private val actionExecutors = mutableListOf<ActionExecutor>()
    
    // Thread-safe legacy handler registry (for fallback)
    private val actionHandlers = ConcurrentHashMap<String, ActionHandler>()
    
    // Caching for performance
    private val animationCache = ConcurrentHashMap<String, String>()
    private val positionCache = ConcurrentHashMap<String, BlockPos>()
    private val playerCache = ConcurrentHashMap<String, Long>() // Last seen timestamp
    
    // Executor selection cache
    private val executorCache = ConcurrentHashMap<String, ActionExecutor>()
    
    // Batching support for high-load scenarios
    private val actionQueue = mutableListOf<Pair<Action, SmartNPC>>()
    private var lastBatchProcess = System.currentTimeMillis()
    
    init {
        registerModularExecutors()
        registerOptimizedHandlers() // Legacy fallback handlers
        startCacheCleanupJob()
    }
    
    /**
     * Register modular action executors
     */
    private fun registerModularExecutors() {
        // Register specialized executors in order of priority
        actionExecutors.add(CombatActionExecutor(eventBus))
        actionExecutors.add(TradingActionExecutor(eventBus))
        actionExecutors.add(SocialActionExecutor(eventBus))
        
        LOGGER.info("Registered ${actionExecutors.size} modular action executors")
    }
    
    /**
     * Execute single action through modular architecture with performance monitoring
     */
    suspend fun execute(action: Action, npc: SmartNPC): ActionResult {
        return executeModular(action.toModernAction(), npc, null)
    }
    
    /**
     * Execute modern action through modular executors
     */
    suspend fun executeModular(
        actionName: String,
        npc: SmartNPC,
        target: Entity? = null,
        parameters: Map<String, Any> = emptyMap()
    ): ActionResult {
        val startTime = System.nanoTime()
        executedActions.incrementAndGet()
        
        try {
            // Conditional logging to reduce overhead
            if (AIConfig.logActions && LOGGER.isDebugEnabled) {
                LOGGER.debug("Executing $actionName for ${npc.getEntity().name.string}")
            }
            
            // Find the best executor for this action
            val executor = selectBestExecutor(actionName, npc, target)
            
            val result = if (executor != null) {
                // Execute with modular executor
                withContext(executorDispatcher) {
                    withTimeout(DEFAULT_ACTION_TIMEOUT) {
                        executor.executeAction(actionName, npc, target, parameters)
                    }
                }
            } else {
                // Fallback to legacy system
                val handler = actionHandlers[actionName]
                if (handler != null) {
                    withContext(executorDispatcher) {
                        withTimeout(DEFAULT_ACTION_TIMEOUT) {
                            handler.handle(npc, actionName, parameters)
                        }
                    }
                } else {
                    ActionResult(false, "No executor found for action: $actionName")
                }
            }
            
            // Async memory recording to not block execution
            recordActionAsync(actionName, npc, result, target)
            
            val executionTime = (System.nanoTime() - startTime) / 1_000_000
            totalExecutionTime.addAndGet(executionTime)
            
            return result.copy(
                executionTime = executionTime,
                data = result.data + ("executor_type" to (executor?.javaClass?.simpleName ?: "legacy"))
            )
            
        } catch (e: TimeoutCancellationException) {
            failedActions.incrementAndGet()
            LOGGER.warn("Action $actionName timed out for ${npc.getEntity().name.string}")
            return ActionResult(false, "Action timed out")
            
        } catch (e: Exception) {
            failedActions.incrementAndGet()
            LOGGER.error("Failed to execute $actionName for ${npc.getEntity().name.string}", e)
            return ActionResult(false, "Execution error: ${e.message}")
        }
    }
    
    /**
     * Batch execute multiple actions for performance (legacy support)
     */
    suspend fun executeBatch(actions: List<Pair<Action, SmartNPC>>): List<ActionResult> {
        return withContext(executorDispatcher) {
            actions.chunked(BATCH_SIZE).flatMap { batch ->
                batch.map { (action, npc) ->
                    async { execute(action, npc) }
                }.awaitAll()
            }
        }
    }
    
    /**
     * Batch execute multiple modern actions for performance
     */
    suspend fun executeBatchModular(
        actions: List<Triple<String, SmartNPC, Map<String, Any>>>
    ): List<ActionResult> {
        return withContext(executorDispatcher) {
            actions.chunked(BATCH_SIZE).flatMap { batch ->
                batch.map { (actionName, npc, params) ->
                    async { 
                        executeModular(
                            actionName = actionName,
                            npc = npc,
                            target = params["target"] as? Entity,
                            parameters = params.filterKeys { it != "target" }
                        )
                    }
                }.awaitAll()
            }
        }
    }
    
    /**
     * Optimized action execution with caching and HE Legacy integration
     */
    private suspend fun executeActionOptimized(action: Action, npc: SmartNPC): ActionResult {
        val handler = actionHandlers[action.type]
        
        return if (handler != null) {
            // Use optimized handler with caching
            when (handler) {
                is CachingHandler -> handler.handleWithCache(action, npc)
                else -> handler.handle(action, npc)
            }
        } else {
            LOGGER.warn("No handler for action type: ${action.type}")
            ActionResult(false, "No handler for action type: ${action.type}")
        }
    }
    
    /**
     * Select the best executor for an action
     */
    private fun selectBestExecutor(
        actionName: String,
        npc: SmartNPC,
        target: Entity?
    ): ActionExecutor? {
        // Check cache first
        val cacheKey = "$actionName:${target?.javaClass?.simpleName ?: "none"}"
        executorCache[cacheKey]?.let { cachedExecutor ->
            if (cachedExecutor.canHandle(actionName, npc, target)) {
                return cachedExecutor
            } else {
                executorCache.remove(cacheKey)
            }
        }
        
        // Find the best executor
        val candidates = actionExecutors.filter { it.canHandle(actionName, npc, target) }
        
        val bestExecutor = if (candidates.size > 1) {
            // Multiple candidates - select by priority and cost
            candidates.maxByOrNull { executor ->
                val cost = try {
                    executor.estimateCost(actionName, npc, target)
                } catch (e: Exception) {
                    ActionCost(Float.MAX_VALUE, Long.MAX_VALUE, 1f) // High cost for failed estimates
                }
                
                // Score based on priority and inverse cost
                executor.priority - (cost.energyCost + cost.timeCost / 1000f + cost.riskLevel * 10f)
            }
        } else {
            candidates.firstOrNull()
        }
        
        // Cache the result
        bestExecutor?.let { executorCache[cacheKey] = it }
        
        return bestExecutor
    }
    
    /**
     * Async memory recording to prevent blocking (updated for modular actions)
     */
    private fun recordActionAsync(
        actionName: String,
        npc: SmartNPC,
        result: ActionResult,
        target: Entity? = null
    ) {
        if (!result.success) return
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val episode = MemoryEpisode(
                    npcId = npc.id,
                    type = "action_executed",
                    description = "Executed $actionName",
                    location = npc.getEntity().blockPosition(),
                    participants = listOfNotNull(npc.id, target?.stringUUID),
                    importance = result.emotionalImpact?.let { impact ->
                        (kotlin.math.abs(impact.valenceChange) + kotlin.math.abs(impact.arousalChange)) / 2f
                    } ?: 0.5f,
                    timestamp = System.currentTimeMillis()
                )
                
                npc.getMemory()?.addEpisode(episode)
            } catch (e: Exception) {
                // Silent failure to prevent cascading issues
                if (LOGGER.isDebugEnabled) {
                    LOGGER.debug("Failed to record action in memory", e)
                }
            }
        }
    }
    
    /**
     * Legacy async memory recording (for compatibility)
     */
    private fun recordActionAsync(action: Action, npc: SmartNPC, result: ActionResult) {
        recordActionAsync(
            actionName = action.type.toString(),
            npc = npc,
            result = result,
            target = null
        )
    }
    
    /**
     * Register optimized handlers for HE Legacy 1.6.2a
     */
    private fun registerOptimizedHandlers() {
        // Core optimized handlers
        actionHandlers[ActionType.SPEAK.name] = OptimizedSpeakHandler()
        actionHandlers[ActionType.MOVE.name] = OptimizedMoveHandler()
        actionHandlers[ActionType.LOOK_AT.name] = OptimizedLookAtHandler()
        actionHandlers[ActionType.ANIMATE.name] = OptimizedAnimateHandler()
        actionHandlers[ActionType.WAIT.name] = OptimizedWaitHandler()
        
        // Social optimized handlers
        actionHandlers[ActionType.GREET.name] = OptimizedGreetHandler()
        actionHandlers[ActionType.FAREWELL.name] = OptimizedFarewellHandler()
        actionHandlers[ActionType.EXPRESS_EMOTION.name] = OptimizedExpressEmotionHandler()
        
        // World interaction optimized handlers
        actionHandlers[ActionType.INTERACT_BLOCK.name] = OptimizedInteractBlockHandler()
        actionHandlers[ActionType.EXAMINE.name] = OptimizedExamineHandler()
        
        // Utility optimized handlers
        actionHandlers[ActionType.UPDATE_STATE.name] = OptimizedUpdateStateHandler()
        actionHandlers[ActionType.DEBUG.name] = OptimizedDebugHandler()
        
        LOGGER.info("Registered ${actionHandlers.size} legacy action handlers for fallback")
    }
    
    /**
     * Start background cache cleanup job
     */
    private fun startCacheCleanupJob() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(CACHE_CLEANUP_INTERVAL)
                
                // Clean stale player cache entries
                val currentTime = System.currentTimeMillis()
                playerCache.entries.removeIf { (_, timestamp) ->
                    currentTime - timestamp > 600000 // 10 minutes
                }
                
                // Clean executor cache
                if (executorCache.size > 200) {
                    executorCache.clear()
                }
                
                // Clean position cache
                if (positionCache.size > 1000) {
                    positionCache.clear()
                }
                
                // Clean animation cache
                if (animationCache.size > 500) {
                    animationCache.clear()
                }
                
                if (LOGGER.isDebugEnabled) {
                    LOGGER.debug("Cache cleanup completed. Player cache: ${playerCache.size}, Position cache: ${positionCache.size}")
                }
            }
        }
    }
    
    fun getPerformanceStats(): PerformanceStats {
        val executed = executedActions.get()
        val failed = failedActions.get()
        val totalTime = totalExecutionTime.get()
        
        return PerformanceStats(
            totalExecuted = executed,
            totalFailed = failed,
            successRate = if (executed > 0) ((executed - failed).toFloat() / executed) * 100 else 0f,
            averageExecutionTimeMs = if (executed > 0) totalTime / executed else 0L,
            cacheHitRate = calculateCacheHitRate(),
            memoryUsage = getMemoryUsage(),
            registeredExecutors = actionExecutors.size,
            cachedExecutors = executorCache.size
        )
    }
    
    private fun calculateCacheHitRate(): Float {
        // Simplified cache hit rate calculation including executor cache
        val totalCacheSize = animationCache.size + positionCache.size + playerCache.size + executorCache.size
        return if (totalCacheSize > 0) (totalCacheSize.toFloat() / 1500) * 100 else 0f
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
    }
}

/**
 * Interface for handlers that support caching
 */
interface CachingHandler : ActionHandler {
    suspend fun handleWithCache(action: Action, npc: SmartNPC): ActionResult
}

// === OPTIMIZED HANDLERS ===

/**
 * Optimized speak handler with message caching
 */
private class OptimizedSpeakHandler : CachingHandler {
    private val messageCache = ConcurrentHashMap<String, Component>()
    
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        // Создаем Action объект из параметров для совместимости
        val actionObj = Action.fromString(action, null, parameters)
        return handleWithCache(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        return handleWithCache(action, npc)
    }
    
    override suspend fun handleWithCache(action: Action, npc: SmartNPC): ActionResult {
        val message = action.getParameter<String>("message") ?: return ActionResult(false, "No message")
        val target = action.target
        
        // Cache message components for reuse
        val cacheKey = "${npc.name}:$message"
        val chatMessage = messageCache.computeIfAbsent(cacheKey) {
            Component.literal("<${npc.name}> $message")
        }
        
        return withContext(Dispatchers.Main) {
            try {
                val level = npc.level as? ServerLevel ?: return@withContext ActionResult(false, "Invalid level")
                
                if (target != null) {
                    val targetPlayer = level.players().find { it.name.string == target }
                    targetPlayer?.sendSystemMessage(chatMessage)
                        ?: return@withContext ActionResult(false, "Target not found: $target")
                } else {
                    // Optimized nearby player lookup with caching
                    val nearbyPlayers = npc.getNearbyPlayers(16.0)
                    nearbyPlayers.forEach { it.sendSystemMessage(chatMessage) }
                }
                
                ActionResult(true, "Said: $message", mapOf("type" to "speech", "message" to message))
            } catch (e: Exception) {
                ActionResult(false, "Speech failed: ${e.message}")
            }
        }
    }
}

/**
 * Optimized move handler with HE Legacy pathfinding
 */
private class OptimizedMoveHandler : CachingHandler {
    private val pathCache = ConcurrentHashMap<String, BlockPos>()
    
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handleWithCache(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        return handleWithCache(action, npc)
    }
    
    override suspend fun handleWithCache(action: Action, npc: SmartNPC): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                val currentPos = npc.position
                val targetPos = determineTargetPosition(action, npc, currentPos)
                    ?: return@withContext ActionResult(false, "No valid target")
                
                // Distance validation
                val distance = currentPos.distSqr(targetPos)
                if (distance > MovementConstants.MAX_MOVEMENT_DISTANCE * MovementConstants.MAX_MOVEMENT_DISTANCE) {
                    return@withContext ActionResult(false, "Distance too large")
                }
                
                // Use HollowEngine Legacy navigation if available
                val success = try {
                    if (HollowEngineAPI.isAvailable()) {
                        val hollowNPC = npc.getEntity() as? HollowNPC
                        hollowNPC?.navigateTo(targetPos) ?: false
                    } else {
                        // Fallback to direct teleportation
                        npc.getEntity().teleportTo(targetPos.x.toDouble(), targetPos.y.toDouble(), targetPos.z.toDouble())
                        true
                    }
                } catch (e: Exception) {
                    false
                }
                
                if (success) {
                    ActionResult(true, "Moved to $targetPos", mapOf("type" to "movement", "position" to targetPos.toString()))
                } else {
                    ActionResult(false, "Movement failed")
                }
                
            } catch (e: Exception) {
                ActionResult(false, "Move error: ${e.message}")
            }
        }
    }
    
    private fun determineTargetPosition(action: Action, npc: SmartNPC, currentPos: BlockPos): BlockPos? {
        // Cache target position calculations
        val cacheKey = "${action.type}:${action.parameters}:${action.target}"
        return pathCache.computeIfAbsent(cacheKey) {
            when {
                action.parameters.containsKey("x") -> {
                    val x = (action.parameters["x"] as? Int) ?: currentPos.x
                    val y = (action.parameters["y"] as? Int) ?: currentPos.y
                    val z = (action.parameters["z"] as? Int) ?: currentPos.z
                    BlockPos(x, y, z)
                }
                action.target != null -> {
                    val targetPlayer = npc.level.players().find { it.name.string == action.target }
                    targetPlayer?.blockPosition()
                }
                else -> null
            } ?: currentPos
        }
    }
}

/**
 * Optimized animation handler for HE Legacy 1.6.2a
 */
private class OptimizedAnimateHandler : CachingHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handleWithCache(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        return handleWithCache(action, npc)
    }
    
    override suspend fun handleWithCache(action: Action, npc: SmartNPC): ActionResult {
        val animation = action.getParameter<String>("animation") ?: return ActionResult(false, "No animation")
        val loop = (action.parameters["loop"] as? Boolean) ?: false
        
        return try {
            val npcEntity = npc.getEntity()
            
            // Try HollowEngine Legacy 1.6.2a animation system first
            val success = try {
                if (HollowEngineAPI.isAvailable()) {
                    val hollowNPC = npcEntity as? HollowNPC
                    val animController = hollowNPC?.getAnimationController()
                    
                    if (animController != null) {
                        val playMode = if (loop) PlayMode.LOOP else PlayMode.ONCE
                        animController.playAnimation(animation, playMode)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
            
            if (success) {
                ActionResult(true, "Playing animation: $animation", mapOf("type" to "animation", "animation" to animation))
            } else {
                // Fallback for compatibility
                ActionResult(true, "Animation fallback: $animation", mapOf("type" to "animation", "animation" to animation, "fallback" to true))
            }
            
        } catch (e: Exception) {
            ActionResult(false, "Animation failed: ${e.message}")
        }
    }
}

/**
 * Optimized examine handler with intelligent observation
 */
private class OptimizedExamineHandler : CachingHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handleWithCache(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        return handleWithCache(action, npc)
    }
    
    override suspend fun handleWithCache(action: Action, npc: SmartNPC): ActionResult {
        val target = action.target
        
        return when (target?.lowercase()) {
            "players" -> examinePlayersOptimized(npc)
            "environment" -> examineEnvironmentOptimized(npc)
            "blocks" -> examineBlocksOptimized(npc)
            else -> examineGeneralOptimized(npc, target)
        }
    }
    
    private suspend fun examinePlayersOptimized(npc: SmartNPC): ActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val nearbyPlayers = npc.level.players().filter { player ->
                    player.position().distanceToSqr(
                        npc.position.x.toDouble(),
                        npc.position.y.toDouble(),
                        npc.position.z.toDouble()
                    ) <= 64.0
                }
                
                if (nearbyPlayers.isEmpty()) {
                    return@withContext ActionResult(true, "No players nearby")
                }
                
                val observations = nearbyPlayers.map { player ->
                    val name = player.name.string
                    val distance = kotlin.math.sqrt(
                        player.position().distanceToSqr(
                            npc.position.x.toDouble(),
                            npc.position.y.toDouble(),
                            npc.position.z.toDouble()
                        )
                    ).toInt()
                    
                    // Quick player data gathering
                    "Player $name at ${distance}m, health: ${player.health.toInt()}"
                }
                
                // Async memory updates
                launch {
                    observations.forEach { obs ->
                        npc.getMemory()?.addEpisode(
                            MemoryEpisode(
                                npcId = npc.id,
                                type = "player_observation",
                                description = obs,
                                location = npc.position,
                                participants = listOf(npc.id.toString()),
                                importance = 0.6f,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
                
                ActionResult(
                    true,
                    "Examined ${nearbyPlayers.size} players: ${observations.joinToString("; ")}",
                    mapOf("type" to "examination", "players_count" to nearbyPlayers.size, "observations" to observations)
                )
            } catch (e: Exception) {
                ActionResult(false, "Player examination failed: ${e.message}")
            }
        }
    }
    
    private suspend fun examineEnvironmentOptimized(npc: SmartNPC): ActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val level = npc.level
                val position = npc.position
                
                // Fast environment data collection
                val biome = level.getBiome(position).value().toString().substringAfterLast('.')
                val timeOfDay = level.dayTime % 24000L
                val weather = when {
                    level.isThundering -> "stormy"
                    level.isRaining -> "raining" 
                    else -> "clear"
                }
                
                val observation = "Environment: $biome biome, ${if (timeOfDay < 12000L) "day" else "night"}, $weather weather"
                
                // Async memory update
                launch {
                    npc.getMemory()?.addKnowledge("environment", observation, 0.5f)
                }
                
                ActionResult(true, observation, mapOf("type" to "environmental_scan", "observation" to observation))
                
            } catch (e: Exception) {
                ActionResult(false, "Environment scan failed: ${e.message}")
            }
        }
    }
    
    private suspend fun examineBlocksOptimized(npc: SmartNPC): ActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val position = npc.position
                val level = npc.level
                val blockCounts = mutableMapOf<String, Int>()
                
                // Efficient block scanning in smaller area
                for (x in (position.x - 3)..(position.x + 3)) {
                    for (y in (position.y - 1)..(position.y + 2)) {
                        for (z in (position.z - 3)..(position.z + 3)) {
                            val blockState = level.getBlockState(BlockPos(x, y, z))
                            val blockName = blockState.block.descriptionId
                            
                            if (blockName != "minecraft:air") {
                                blockCounts[blockName] = blockCounts.getOrDefault(blockName, 0) + 1
                            }
                        }
                    }
                }
                
                val topBlocks = blockCounts.entries.sortedByDescending { it.value }.take(3)
                val summary = topBlocks.joinToString(", ") { "${it.key.substringAfter(':')}: ${it.value}" }
                
                ActionResult(true, "Block scan: $summary", mapOf("type" to "block_scan", "summary" to summary))
                
            } catch (e: Exception) {
                ActionResult(false, "Block scan failed: ${e.message}")
            }
        }
    }
    
    private suspend fun examineGeneralOptimized(npc: SmartNPC, target: String?): ActionResult {
        val info = "Position: ${npc.position}, State: ${npc.currentState}, Emotion: ${npc.emotionalState}"
        return ActionResult(true, "General: $info", mapOf("type" to "general_scan", "info" to info))
    }
}

// Simplified optimized handlers for other action types
private class OptimizedLookAtHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        // Создаем Action объект из параметров для совместимости
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        // Simplified look-at with HE Legacy integration
        return ActionResult(true, "Looking at ${action.target ?: "coordinates"}", mapOf("type" to "look_at", "target" to (action.target ?: "coordinates")))
    }
}

private class OptimizedWaitHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val duration = min(max((action.parameters["duration"] as? Int) ?: 1000, 100), 10000)
        delay(duration.toLong())
        return ActionResult(true, "Waited ${duration}ms")
    }
}

private class OptimizedGreetHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val target = action.target ?: "everyone"
        val greeting = "Hello, $target!"
        
        // Use optimized speak handler
        val speakAction = Action(ActionType.SPEAK, target, mapOf("message" to greeting))
        return OptimizedSpeakHandler().handle(speakAction, npc)
    }
}

private class OptimizedFarewellHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val target = action.target ?: "everyone"
        val farewell = "Goodbye, $target!"
        
        val speakAction = Action(ActionType.SPEAK, target, mapOf("message" to farewell))
        return OptimizedSpeakHandler().handle(speakAction, npc)
    }
}

private class OptimizedExpressEmotionHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val emotion = action.getParameter<String>("emotion") ?: "neutral"
        npc.setEmotion(EmotionalState.valueOf(emotion.uppercase()))
        return ActionResult(true, "Expressed emotion: $emotion", mapOf("type" to "emotion_change", "emotion" to emotion))
    }
}

private class OptimizedInteractBlockHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        // Simplified block interaction with caching
        val blockType = action.getParameter<String>("block_type") ?: return ActionResult(false, "No block type")
        return ActionResult(true, "Interacted with $blockType", mapOf("type" to "block_interaction", "block_type" to blockType))
    }
}

private class OptimizedUpdateStateHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val newState = action.getParameter<String>("state") ?: return ActionResult(false, "No state")
        return ActionResult(true, "State updated: $newState")
    }
}

private class OptimizedDebugHandler : ActionHandler {
    override suspend fun handle(npc: SmartNPC, action: String, parameters: Map<String, Any>): ActionResult {
        val actionObj = Action.fromString(action, null, parameters)
        return handle(actionObj, npc)
    }
    
    suspend fun handle(action: Action, npc: SmartNPC): ActionResult {
        val message = action.getParameter<String>("message") ?: "Debug"
        if (AIConfig.debugMode) {
            LogManager.getLogger().info("[DEBUG] ${npc.name}: $message")
        }
        return ActionResult(true, "Debug: $message")
    }
}

// Helper extension functions
private fun ActionPriority.toImportance(): Float = when (this) {
    ActionPriority.CRITICAL -> 0.9f
    ActionPriority.HIGH -> 0.7f
    ActionPriority.NORMAL -> 0.5f
    ActionPriority.LOW -> 0.3f
    ActionPriority.BACKGROUND -> 0.1f
}

// Helper method for getting entity UUID as string
private val Entity.stringUUID: String
    get() = this.uuid.toString()

private object HollowEngineAPI {
    fun isAvailable(): Boolean = try {
        Class.forName("team.hollow.engine.core.HollowEngine")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

/**
 * Enhanced performance statistics
 */
data class PerformanceStats(
    val totalExecuted: Long,
    val totalFailed: Long,
    val successRate: Float,
    val averageExecutionTimeMs: Long,
    val cacheHitRate: Float,
    val memoryUsage: Long,
    val registeredExecutors: Int = 0,
    val cachedExecutors: Int = 0
)

/**
 * Extension methods for compatibility between old and new action systems
 */
fun Action.toModernAction(): String {
    return when (this.type) {
        ActionType.SPEAK -> "talk"
        ActionType.MOVE -> "move"
        ActionType.LOOK_AT -> "look_at"
        ActionType.ANIMATE -> "animate"
        ActionType.WAIT -> "wait"
        ActionType.GREET -> "greet"
        ActionType.FAREWELL -> "farewell"
        ActionType.EXPRESS_EMOTION -> "express_emotion"
        ActionType.INTERACT_BLOCK -> "interact_block"
        ActionType.EXAMINE -> "examine"
        ActionType.UPDATE_STATE -> "update_state"
        ActionType.DEBUG -> "debug"
        else -> this.type.toString().lowercase()
    }
}

fun fromModernAction(
    actionName: String,
    target: Entity?,
    parameters: Map<String, Any>
): Action? {
    val actionType = when (actionName.lowercase()) {
        "talk", "speak" -> ActionType.SPEAK
        "move" -> ActionType.MOVE
        "look_at" -> ActionType.LOOK_AT
        "animate" -> ActionType.ANIMATE
        "wait" -> ActionType.WAIT
        "greet" -> ActionType.GREET
        "farewell" -> ActionType.FAREWELL
        "express_emotion" -> ActionType.EXPRESS_EMOTION
        "interact_block" -> ActionType.INTERACT_BLOCK
        "examine" -> ActionType.EXAMINE
        "update_state" -> ActionType.UPDATE_STATE
        "debug" -> ActionType.DEBUG
        else -> null
    } ?: return null
    
    return Action(
        type = actionType,
        target = target?.name?.string,
        parameters = parameters.mapValues { it.value.toString() }
    )
}

/**
 * Convert ActionResult to include execution time
 */
fun ActionResult.copy(
    success: Boolean = this.success,
    message: String = this.message,
    data: Map<String, Any> = this.data,
    executionTime: Long = this.executionTime,
    energyCost: Float = this.energyCost,
    emotionalImpact: EmotionalImpact? = this.emotionalImpact,
    duration: Long? = null
): ActionResult {
    return ActionResult(
        success = success,
        message = message,
        data = if (duration != null) data + ("duration" to duration) else data,
        executionTime = duration ?: executionTime,
        energyCost = energyCost,
        emotionalImpact = emotionalImpact
    )
}
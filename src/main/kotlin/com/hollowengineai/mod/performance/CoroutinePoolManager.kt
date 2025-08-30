package com.hollowengineai.mod.performance

import com.hollowengineai.mod.HollowEngineAIMod
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Менеджер пулов корутин для оптимизации производительности
 * 
 * Управляет различными пулами корутин для разных типов задач:
 * - AI вычисления
 * - Действия NPC  
 * - Операции ввода-вывода
 * - Фоновые задачи
 */
object CoroutinePoolManager {
    
    // Статистика использования
    private val executedTasks = AtomicLong(0)
    private val activeTasks = AtomicInteger(0)
    private val taskExecutionTimes = ConcurrentHashMap<String, Long>()
    
    // Пулы потоков
    private val aiThreadPool = Executors.newFixedThreadPool(4, createThreadFactory("AI-Thread"))
    private val actionThreadPool = Executors.newFixedThreadPool(6, createThreadFactory("Action-Thread"))
    private val ioThreadPool = Executors.newCachedThreadPool(createThreadFactory("IO-Thread"))
    private val backgroundThreadPool = Executors.newFixedThreadPool(2, createThreadFactory("Background-Thread"))
    
    // Диспетчеры корутин
    val aiDispatcher: CoroutineDispatcher = aiThreadPool.asCoroutineDispatcher()
    val actionDispatcher: CoroutineDispatcher = actionThreadPool.asCoroutineDispatcher()
    val ioDispatcher: CoroutineDispatcher = ioThreadPool.asCoroutineDispatcher()
    val backgroundDispatcher: CoroutineDispatcher = backgroundThreadPool.asCoroutineDispatcher()
    
    // Супервайзеры для разных типов задач
    val aiScope = CoroutineScope(aiDispatcher + SupervisorJob())
    val actionScope = CoroutineScope(actionDispatcher + SupervisorJob()) 
    val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())
    val backgroundScope = CoroutineScope(backgroundDispatcher + SupervisorJob())
    
    init {
        HollowEngineAIMod.LOGGER.info("CoroutinePoolManager initialized with optimized thread pools")
    }
    
    /**
     * Выполнить AI задачу с мониторингом производительности
     */
    suspend fun <T> executeAITask(
        taskName: String,
        block: suspend CoroutineScope.() -> T
    ): T {
        return executeWithMonitoring(taskName, aiScope, block)
    }
    
    /**
     * Выполнить действие NPC
     */
    suspend fun <T> executeAction(
        taskName: String,
        block: suspend CoroutineScope.() -> T
    ): T {
        return executeWithMonitoring(taskName, actionScope, block)
    }
    
    /**
     * Выполнить IO операцию
     */
    suspend fun <T> executeIO(
        taskName: String,
        block: suspend CoroutineScope.() -> T
    ): T {
        return executeWithMonitoring(taskName, ioScope, block)
    }
    
    /**
     * Выполнить фоновую задачу
     */
    fun executeBackground(
        taskName: String,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return backgroundScope.launch {
            executeWithMonitoring(taskName, this) { block() }
        }
    }
    
    /**
     * Получить статистику производительности
     */
    fun getPerformanceStats(): PoolStats {
        return PoolStats(
            totalExecutedTasks = executedTasks.get(),
            currentActiveTasks = activeTasks.get(),
            averageExecutionTimes = taskExecutionTimes.toMap(),
            aiPoolSize = 4,
            actionPoolSize = 6,
            ioPoolActive = true,
            backgroundPoolSize = 2
        )
    }
    
    /**
     * Получить полную отладочную информацию
     */
    fun getDebugInfo(): String {
        val stats = getPerformanceStats()
        val sb = StringBuilder()
        
        sb.appendLine("CoroutinePoolManager Debug Info:")
        sb.appendLine("================================")
        sb.appendLine("Total Executed Tasks: ${stats.totalExecutedTasks}")
        sb.appendLine("Current Active Tasks: ${stats.currentActiveTasks}")
        sb.appendLine()
        
        sb.appendLine("Thread Pool Configuration:")
        sb.appendLine("--------------------------")
        sb.appendLine("AI Pool Size: ${stats.aiPoolSize}")
        sb.appendLine("Action Pool Size: ${stats.actionPoolSize}")
        sb.appendLine("IO Pool: ${if (stats.ioPoolActive) "Active (Cached)" else "Inactive"}")
        sb.appendLine("Background Pool Size: ${stats.backgroundPoolSize}")
        sb.appendLine()
        
        sb.appendLine("Task Execution Times (Recent):")
        sb.appendLine("-------------------------------")
        if (stats.averageExecutionTimes.isNotEmpty()) {
            stats.averageExecutionTimes.entries
                .sortedByDescending { it.value }
                .take(10)
                .forEach { (taskName, time) ->
                    sb.appendLine("  $taskName: ${time}ms")
                }
        } else {
            sb.appendLine("  No task execution data available")
        }
        
        sb.appendLine()
        sb.appendLine("Thread Pool States:")
        sb.appendLine("-------------------")
        sb.appendLine("AI Dispatcher: ${if (!aiDispatcher.isClosedForDispatch) "Active" else "Closed"}")
        sb.appendLine("Action Dispatcher: ${if (!actionDispatcher.isClosedForDispatch) "Active" else "Closed"}")
        sb.appendLine("IO Dispatcher: ${if (!ioDispatcher.isClosedForDispatch) "Active" else "Closed"}")
        sb.appendLine("Background Dispatcher: ${if (!backgroundDispatcher.isClosedForDispatch) "Active" else "Closed"}")
        
        sb.appendLine()
        sb.appendLine("Scope States:")
        sb.appendLine("-------------")
        sb.appendLine("AI Scope: ${if (aiScope.isActive) "Active" else "Cancelled"}")
        sb.appendLine("Action Scope: ${if (actionScope.isActive) "Active" else "Cancelled"}")
        sb.appendLine("IO Scope: ${if (ioScope.isActive) "Active" else "Cancelled"}")
        sb.appendLine("Background Scope: ${if (backgroundScope.isActive) "Active" else "Cancelled"}")
        
        return sb.toString()
    }
    
    /**
     * Получить статистику пулов в виде Map для системного API
     */
    fun getPoolStats(): Map<String, Any> {
        val stats = getPerformanceStats()
        return mapOf(
            "totalExecutedTasks" to stats.totalExecutedTasks,
            "currentActiveTasks" to stats.currentActiveTasks,
            "aiPoolSize" to stats.aiPoolSize,
            "actionPoolSize" to stats.actionPoolSize,
            "backgroundPoolSize" to stats.backgroundPoolSize,
            "ioPoolActive" to stats.ioPoolActive,
            "taskCount" to stats.averageExecutionTimes.size,
            "avgTaskTime" to if (stats.averageExecutionTimes.isNotEmpty()) {
                stats.averageExecutionTimes.values.average().toLong()
            } else 0L,
            "maxTaskTime" to (stats.averageExecutionTimes.values.maxOrNull() ?: 0L),
            "minTaskTime" to (stats.averageExecutionTimes.values.minOrNull() ?: 0L),
            "aiScopeActive" to aiScope.isActive,
            "actionScopeActive" to actionScope.isActive,
            "ioScopeActive" to ioScope.isActive,
            "backgroundScopeActive" to backgroundScope.isActive
        )
    }
    
    /**
     * Сбросить статистику выполнения задач
     */
    fun resetStatistics() {
        executedTasks.set(0)
        taskExecutionTimes.clear()
        HollowEngineAIMod.LOGGER.info("CoroutinePoolManager statistics reset")
    }
    
    /**
     * Проверить состояние здоровья всех пулов
     */
    fun isHealthy(): Boolean {
        return aiScope.isActive && 
               actionScope.isActive && 
               ioScope.isActive && 
               backgroundScope.isActive &&
               !aiDispatcher.isClosedForDispatch &&
               !actionDispatcher.isClosedForDispatch &&
               !ioDispatcher.isClosedForDispatch &&
               !backgroundDispatcher.isClosedForDispatch
    }
    
    /**
     * Получить количество активных задач по типам
     */
    fun getActiveTasksByType(): Map<String, Int> {
        // Простое приближение, в реальности можно имплементировать более детально
        val total = activeTasks.get()
        val perType = if (total > 0) total / 4 else 0
        
        return mapOf(
            "ai" to perType,
            "action" to perType,
            "io" to perType,
            "background" to perType
        )
    }
    
    /**
     * Принудительно отменить все активные задачи (для отладки)
     */
    fun cancelAllTasks() {
        aiScope.coroutineContext[Job]?.cancelChildren()
        actionScope.coroutineContext[Job]?.cancelChildren()
        ioScope.coroutineContext[Job]?.cancelChildren()
        backgroundScope.coroutineContext[Job]?.cancelChildren()
        
        HollowEngineAIMod.LOGGER.info("All active tasks cancelled")
    }
    
    private suspend fun <T> executeWithMonitoring(
        taskName: String,
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> T
    ): T {
        val startTime = System.currentTimeMillis()
        activeTasks.incrementAndGet()
        executedTasks.incrementAndGet()
        
        try {
            return withContext(scope.coroutineContext) {
                block()
            }
        } finally {
            val executionTime = System.currentTimeMillis() - startTime
            taskExecutionTimes[taskName] = executionTime
            activeTasks.decrementAndGet()
        }
    }
    
    private fun createThreadFactory(namePrefix: String): ThreadFactory {
        return ThreadFactory { runnable ->
            Thread(runnable, "$namePrefix-${Thread.activeCount()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
    }
    
    /**
     * Проверить, инициализирован ли менеджер
     */
    fun isInitialized(): Boolean {
        // Object всегда инициализирован при первом обращении
        return true
    }
    
    /**
     * Инициализация менеджера (для API совместимости)
     */
    fun initialize() {
        // Менеджер уже инициализирован как object, но этот метод полезен для API
        HollowEngineAIMod.LOGGER.debug("CoroutinePoolManager.initialize() called")
    }
    
    fun shutdown() {
        aiScope.cancel()
        actionScope.cancel() 
        ioScope.cancel()
        backgroundScope.cancel()
        
        aiThreadPool.shutdown()
        actionThreadPool.shutdown()
        ioThreadPool.shutdown()
        backgroundThreadPool.shutdown()
        
        HollowEngineAIMod.LOGGER.info("CoroutinePoolManager shut down")
    }
}

data class PoolStats(
    val totalExecutedTasks: Long,
    val currentActiveTasks: Int,
    val averageExecutionTimes: Map<String, Long>,
    val aiPoolSize: Int,
    val actionPoolSize: Int,
    val ioPoolActive: Boolean,
    val backgroundPoolSize: Int
)
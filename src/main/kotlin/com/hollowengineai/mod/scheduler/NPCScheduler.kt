package com.hollowengineai.mod.scheduler

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.states.NPCState
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import net.minecraft.world.level.Level
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Система расписания для NPC с поддержкой временных задач и повседневных активностей
 * 
 * Функции:
 * - Ежедневные расписания для NPC
 * - Разовые и повторяющиеся задачи
 * - Сезонные и событийные активности
 * - Приоритизация задач
 * - Адаптивное планирование
 * - Групповые расписания
 */
@Serializable
object NPCScheduler {
    
    // Основные данные планировщика
    private val npcSchedules = ConcurrentHashMap<String, NPCSchedule>()
    private val globalTasks = PriorityQueue<ScheduledTask>(compareBy { it.scheduledTime })
    private val recurringTasks = ConcurrentHashMap<String, RecurringTask>()
    
    // Активные задачи
    private val activeTasks = ConcurrentHashMap<String, ScheduledTask>()
    private val taskHistory = ConcurrentHashMap<String, MutableList<TaskExecution>>()
    
    // События и система
    private lateinit var eventBus: NPCEventBus
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Время и состояние
    private var gameStartTime = System.currentTimeMillis()
    private var lastCleanupTime = System.currentTimeMillis()
    
    // JSON для сериализации
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Файл сохранения
    private val scheduleFile = File("config/hollowengineai/npc_schedules.json")
    
    /**
     * Инициализация планировщика
     */
    fun initialize(eventBus: NPCEventBus) {
        this.eventBus = eventBus
        loadScheduleData()
        startSchedulerEngine()
        
        HollowEngineAIMod.LOGGER.info("NPCScheduler initialized with ${npcSchedules.size} NPC schedules")
    }
    
    /**
     * Создать ежедневное расписание для NPC
     */
    fun createDailySchedule(npcId: String, routines: List<DailyRoutine>): NPCSchedule {
        val schedule = NPCSchedule(
            npcId = npcId,
            dailyRoutines = routines.toMutableList(),
            createdAt = System.currentTimeMillis()
        )
        
        npcSchedules[npcId] = schedule
        
        HollowEngineAIMod.LOGGER.debug("Created daily schedule for NPC $npcId with ${routines.size} routines")
        
        return schedule
    }
    
    /**
     * Добавить задачу в расписание NPC
     */
    fun scheduleTask(
        npcId: String,
        action: String,
        scheduledTime: Long,
        priority: TaskPriority = TaskPriority.NORMAL,
        parameters: Map<String, Any> = emptyMap(),
        conditions: List<TaskCondition> = emptyList()
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        val task = ScheduledTask(
            id = taskId,
            npcId = npcId,
            action = action,
            scheduledTime = scheduledTime,
            priority = priority,
            parameters = parameters,
            conditions = conditions,
            createdAt = System.currentTimeMillis()
        )
        
        globalTasks.offer(task)
        
        HollowEngineAIMod.LOGGER.debug("Scheduled task $action for NPC $npcId at ${formatTime(scheduledTime)}")
        
        return taskId
    }
    
    /**
     * Создать повторяющуюся задачу
     */
    fun createRecurringTask(
        npcId: String,
        action: String,
        interval: Long, // мс между выполнениями
        startTime: Long = System.currentTimeMillis(),
        endTime: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        parameters: Map<String, Any> = emptyMap(),
        conditions: List<TaskCondition> = emptyList()
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        val recurringTask = RecurringTask(
            id = taskId,
            npcId = npcId,
            action = action,
            interval = interval,
            startTime = startTime,
            endTime = endTime,
            priority = priority,
            parameters = parameters,
            conditions = conditions,
            nextExecution = startTime
        )
        
        recurringTasks[taskId] = recurringTask
        
        // Планируем первое выполнение
        scheduleTask(npcId, action, startTime, priority, parameters + ("recurring_id" to taskId), conditions)
        
        HollowEngineAIMod.LOGGER.debug("Created recurring task $action for NPC $npcId every ${interval}ms")
        
        return taskId
    }
    
    /**
     * Получить текущую активность NPC согласно расписанию
     */
    fun getCurrentActivity(npcId: String, gameTime: Long): DailyRoutine? {
        val schedule = npcSchedules[npcId] ?: return null
        val timeOfDay = gameTime % 24000L // Игровые тики в дне
        
        return schedule.dailyRoutines.find { routine ->
            val startTicks = routine.startTime * 1000L / 60L // Минуты в тики
            val endTicks = routine.endTime * 1000L / 60L
            
            if (startTicks <= endTicks) {
                timeOfDay in startTicks..endTicks
            } else {
                // Активность переходит через полночь
                timeOfDay >= startTicks || timeOfDay <= endTicks
            }
        }
    }
    
    /**
     * Получить следующую запланированную задачу для NPC
     */
    fun getNextTask(npcId: String): ScheduledTask? {
        return globalTasks.peek()?.takeIf { 
            it.npcId == npcId && it.scheduledTime <= System.currentTimeMillis() + 60000L 
        }
    }
    
    /**
     * Отменить задачу
     */
    fun cancelTask(taskId: String): Boolean {
        // Ищем в глобальных задачах
        val iterator = globalTasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.id == taskId) {
                iterator.remove()
                HollowEngineAIMod.LOGGER.debug("Cancelled task $taskId")
                return true
            }
        }
        
        // Ищем в повторяющихся задачах
        recurringTasks.remove(taskId)?.let {
            HollowEngineAIMod.LOGGER.debug("Cancelled recurring task $taskId")
            return true
        }
        
        return false
    }
    
    /**
     * Выполнить задачу
     */
    suspend fun executeTask(task: ScheduledTask, npc: SmartNPC): TaskResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Проверяем условия выполнения
            if (!checkTaskConditions(task, npc)) {
                return TaskResult(
                    success = false,
                    message = "Task conditions not met",
                    executionTime = 0L
                )
            }
            
            // Добавляем в активные задачи
            activeTasks[task.id] = task
            
            // Выполняем задачу через систему действий
            // Здесь может быть интеграция с OptimizedActionExecutor
            val result = when (task.action) {
                "sleep" -> executeSleepTask(npc, task.parameters)
                "work" -> executeWorkTask(npc, task.parameters)
                "patrol" -> executePatrolTask(npc, task.parameters)
                "socialize" -> executeSocializeTask(npc, task.parameters)
                "eat" -> executeEatTask(npc, task.parameters)
                "rest" -> executeRestTask(npc, task.parameters)
                else -> executeCustomTask(npc, task)
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // Записываем в историю
            recordTaskExecution(task, result, executionTime)
            
            // Обрабатываем повторяющиеся задачи
            handleRecurringTask(task)
            
            return TaskResult(
                success = result.success,
                message = result.message,
                executionTime = executionTime
            )
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to execute task ${task.action} for NPC ${task.npcId}", e)
            return TaskResult(
                success = false,
                message = "Execution failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        } finally {
            activeTasks.remove(task.id)
        }
    }
    
    /**
     * Получить статистику планировщика
     */
    fun getSchedulerStats(): SchedulerStats {
        val totalScheduledTasks = globalTasks.size
        val totalRecurringTasks = recurringTasks.size
        val totalActiveTasks = activeTasks.size
        val totalNPCsWithSchedules = npcSchedules.size
        
        val completedTasks = taskHistory.values.flatten().count { it.success }
        val failedTasks = taskHistory.values.flatten().count { !it.success }
        
        val averageExecutionTime = taskHistory.values.flatten()
            .map { it.executionTime }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toLong() ?: 0L
        
        return SchedulerStats(
            scheduledTasks = totalScheduledTasks,
            recurringTasks = totalRecurringTasks,
            activeTasks = totalActiveTasks,
            npcsWithSchedules = totalNPCsWithSchedules,
            completedTasks = completedTasks,
            failedTasks = failedTasks,
            averageExecutionTimeMs = averageExecutionTime,
            uptime = System.currentTimeMillis() - gameStartTime
        )
    }
    
    /**
     * Создать стандартное расписание для типа NPC
     */
    fun createStandardSchedule(npcId: String, npcType: String): NPCSchedule {
        val routines = when (npcType.lowercase()) {
            "guard" -> listOf(
                DailyRoutine("patrol", 6*60, 18*60, TaskPriority.HIGH),
                DailyRoutine("rest", 18*60, 22*60, TaskPriority.NORMAL),
                DailyRoutine("sleep", 22*60, 6*60, TaskPriority.LOW)
            )
            "merchant" -> listOf(
                DailyRoutine("work", 8*60, 18*60, TaskPriority.HIGH),
                DailyRoutine("socialize", 18*60, 20*60, TaskPriority.NORMAL),
                DailyRoutine("eat", 20*60, 21*60, TaskPriority.NORMAL),
                DailyRoutine("sleep", 21*60, 8*60, TaskPriority.LOW)
            )
            "farmer" -> listOf(
                DailyRoutine("work", 5*60, 12*60, TaskPriority.HIGH),
                DailyRoutine("eat", 12*60, 13*60, TaskPriority.HIGH),
                DailyRoutine("work", 13*60, 19*60, TaskPriority.HIGH),
                DailyRoutine("rest", 19*60, 21*60, TaskPriority.NORMAL),
                DailyRoutine("sleep", 21*60, 5*60, TaskPriority.LOW)
            )
            else -> listOf(
                DailyRoutine("work", 9*60, 17*60, TaskPriority.NORMAL),
                DailyRoutine("socialize", 17*60, 19*60, TaskPriority.NORMAL),
                DailyRoutine("eat", 19*60, 20*60, TaskPriority.NORMAL),
                DailyRoutine("rest", 20*60, 22*60, TaskPriority.LOW),
                DailyRoutine("sleep", 22*60, 9*60, TaskPriority.LOW)
            )
        }
        
        return createDailySchedule(npcId, routines)
    }
    
    // Приватные методы
    
    /**
     * Запуск основного двигателя планировщика
     */
    private fun startSchedulerEngine() {
        scope.launch {
            while (true) {
                try {
                    processScheduledTasks()
                    updateRecurringTasks()
                    cleanupOldTasks()
                    delay(1000L) // Проверяем каждую секунду
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in scheduler engine", e)
                }
            }
        }
        
        // Автосохранение каждые 5 минут
        scope.launch {
            while (true) {
                delay(300000L)
                try {
                    saveScheduleData()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error saving schedule data", e)
                }
            }
        }
    }
    
    /**
     * Обработка запланированных задач
     */
    private suspend fun processScheduledTasks() {
        val currentTime = System.currentTimeMillis()
        val tasksToExecute = mutableListOf<ScheduledTask>()
        
        // Собираем задачи для выполнения
        while (globalTasks.peek()?.scheduledTime ?: Long.MAX_VALUE <= currentTime) {
            globalTasks.poll()?.let { tasksToExecute.add(it) }
        }
        
        // Выполняем задачи асинхронно
        tasksToExecute.forEach { task ->
            scope.launch {
                // Здесь можно добавить получение NPC по ID и выполнение задачи
                // Пока используем заглушку
                val mockResult = TaskExecutionResult(
                    success = Random.nextBoolean(),
                    message = "Task executed",
                    data = emptyMap()
                )
                recordTaskExecution(task, mockResult, Random.nextLong(100, 1000))
            }
        }
    }
    
    /**
     * Обновление повторяющихся задач
     */
    private fun updateRecurringTasks() {
        val currentTime = System.currentTimeMillis()
        
        recurringTasks.values.forEach { recurringTask ->
            if (recurringTask.nextExecution <= currentTime) {
                // Планируем следующее выполнение
                val nextTime = recurringTask.nextExecution + recurringTask.interval
                
                // Проверяем, не истёк ли срок действия
                if (recurringTask.endTime == null || nextTime <= recurringTask.endTime) {
                    scheduleTask(
                        npcId = recurringTask.npcId,
                        action = recurringTask.action,
                        scheduledTime = nextTime,
                        priority = recurringTask.priority,
                        parameters = recurringTask.parameters + ("recurring_id" to recurringTask.id),
                        conditions = recurringTask.conditions
                    )
                    
                    recurringTask.nextExecution = nextTime
                } else {
                    // Удаляем истёкшую повторяющуюся задачу
                    recurringTasks.remove(recurringTask.id)
                }
            }
        }
    }
    
    /**
     * Очистка старых задач
     */
    private fun cleanupOldTasks() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastCleanupTime > 3600000L) { // Каждый час
            // Очищаем старую историю выполнения (старше 24 часов)
            val dayAgo = currentTime - 86400000L
            
            taskHistory.values.forEach { executions ->
                executions.removeIf { it.executedAt < dayAgo }
            }
            
            // Удаляем пустые записи
            taskHistory.entries.removeIf { it.value.isEmpty() }
            
            lastCleanupTime = currentTime
        }
    }
    
    /**
     * Проверка условий выполнения задачи
     */
    private fun checkTaskConditions(task: ScheduledTask, npc: SmartNPC): Boolean {
        return task.conditions.all { condition ->
            when (condition) {
                is StateCondition -> npc.stateMachine.getCurrentState() == condition.requiredState
                is TimeCondition -> {
                    val currentTime = System.currentTimeMillis()
                    currentTime in condition.startTime..condition.endTime
                }
                is WeatherCondition -> {
                    // Здесь можно добавить проверку погоды
                    true // Заглушка
                }
                is HealthCondition -> npc.getEntity().health >= condition.minHealth
                is CustomCondition -> condition.check(npc)
            }
        }
    }
    
    /**
     * Выполнение задач по типам
     */
    private suspend fun executeSleepTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        npc.stateMachine.transitionTo(NPCState.SLEEPING, "Scheduled sleep")
        delay(1000L)
        return TaskExecutionResult(true, "NPC is now sleeping")
    }
    
    private suspend fun executeWorkTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        npc.stateMachine.transitionTo(NPCState.ACTIVE, "Starting work")
        delay(500L)
        return TaskExecutionResult(true, "NPC started working")
    }
    
    private suspend fun executePatrolTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        npc.stateMachine.transitionTo(NPCState.PATROLLING, "Starting patrol")
        delay(500L)
        return TaskExecutionResult(true, "NPC started patrolling")
    }
    
    private suspend fun executeSocializeTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        npc.stateMachine.transitionTo(NPCState.TALKING, "Socializing")
        delay(800L)
        return TaskExecutionResult(true, "NPC is socializing")
    }
    
    private suspend fun executeEatTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        delay(600L)
        return TaskExecutionResult(true, "NPC finished eating")
    }
    
    private suspend fun executeRestTask(npc: SmartNPC, parameters: Map<String, Any>): TaskExecutionResult {
        npc.stateMachine.transitionTo(NPCState.IDLE, "Resting")
        delay(300L)
        return TaskExecutionResult(true, "NPC is resting")
    }
    
    private suspend fun executeCustomTask(npc: SmartNPC, task: ScheduledTask): TaskExecutionResult {
        // Здесь может быть интеграция с OptimizedActionExecutor
        delay(500L)
        return TaskExecutionResult(true, "Custom task executed: ${task.action}")
    }
    
    /**
     * Запись выполнения задачи в историю
     */
    private fun recordTaskExecution(
        task: ScheduledTask,
        result: TaskExecutionResult,
        executionTime: Long
    ) {
        val execution = TaskExecution(
            taskId = task.id,
            npcId = task.npcId,
            action = task.action,
            success = result.success,
            message = result.message,
            executionTime = executionTime,
            executedAt = System.currentTimeMillis()
        )
        
        taskHistory.computeIfAbsent(task.npcId) { mutableListOf() }.add(execution)
    }
    
    /**
     * Обработка повторяющейся задачи
     */
    private fun handleRecurringTask(task: ScheduledTask) {
        val recurringId = task.parameters["recurring_id"] as? String ?: return
        val recurringTask = recurringTasks[recurringId] ?: return
        
        // Логика обновления повторяющейся задачи обрабатывается в updateRecurringTasks()
    }
    
    /**
     * Сохранение данных планировщика
     */
    private suspend fun saveScheduleData() {
        try {
            withContext(Dispatchers.IO) {
                scheduleFile.parentFile?.mkdirs()
                
                val saveData = SchedulerSaveData(
                    npcSchedules = npcSchedules.toMap(),
                    recurringTasks = recurringTasks.toMap(),
                    gameStartTime = gameStartTime,
                    version = 1
                )
                
                val jsonString = json.encodeToString(SchedulerSaveData.serializer(), saveData)
                scheduleFile.writeText(jsonString)
            }
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to save scheduler data", e)
        }
    }
    
    /**
     * Загрузка данных планировщика
     */
    private fun loadScheduleData() {
        try {
            if (!scheduleFile.exists()) {
                HollowEngineAIMod.LOGGER.info("No existing scheduler data found")
                return
            }
            
            val jsonString = scheduleFile.readText()
            val saveData = json.decodeFromString(SchedulerSaveData.serializer(), jsonString)
            
            npcSchedules.clear()
            npcSchedules.putAll(saveData.npcSchedules)
            
            recurringTasks.clear()
            recurringTasks.putAll(saveData.recurringTasks)
            
            gameStartTime = saveData.gameStartTime
            
            HollowEngineAIMod.LOGGER.info(
                "Loaded scheduler data: ${npcSchedules.size} NPC schedules, " +
                "${recurringTasks.size} recurring tasks"
            )
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to load scheduler data", e)
        }
    }
    
    /**
     * Форматирование времени для логов
     */
    private fun formatTime(timestamp: Long): String {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
    
    /**
     * Завершение работы планировщика
     */
    fun shutdown() {
        scope.cancel()
        runBlocking {
            saveScheduleData()
        }
        HollowEngineAIMod.LOGGER.info("NPCScheduler shut down")
    }
}

// Классы данных

@Serializable
data class NPCSchedule(
    val npcId: String,
    val dailyRoutines: MutableList<DailyRoutine> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class DailyRoutine(
    val activity: String,
    val startTime: Int, // минуты от полуночи
    val endTime: Int,   // минуты от полуночи
    val priority: TaskPriority = TaskPriority.NORMAL,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class ScheduledTask(
    val id: String,
    val npcId: String,
    val action: String,
    val scheduledTime: Long,
    val priority: TaskPriority,
    val parameters: Map<String, Any> = emptyMap(),
    @Transient val conditions: List<TaskCondition> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class RecurringTask(
    val id: String,
    val npcId: String,
    val action: String,
    val interval: Long, // мс
    val startTime: Long,
    val endTime: Long? = null,
    val priority: TaskPriority,
    val parameters: Map<String, Any> = emptyMap(),
    @Transient val conditions: List<TaskCondition> = emptyList(),
    var nextExecution: Long = startTime,
    var executionCount: Int = 0
)

data class TaskExecution(
    val taskId: String,
    val npcId: String,
    val action: String,
    val success: Boolean,
    val message: String,
    val executionTime: Long,
    val executedAt: Long
)

data class TaskResult(
    val success: Boolean,
    val message: String,
    val executionTime: Long
)

data class TaskExecutionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)

data class SchedulerStats(
    val scheduledTasks: Int,
    val recurringTasks: Int,
    val activeTasks: Int,
    val npcsWithSchedules: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val averageExecutionTimeMs: Long,
    val uptime: Long
)

@Serializable
enum class TaskPriority(val value: Int) {
    CRITICAL(100),
    HIGH(75),
    NORMAL(50),
    LOW(25),
    BACKGROUND(10)
}

// Условия выполнения задач
sealed class TaskCondition {
    abstract fun isValid(): Boolean
}

data class StateCondition(val requiredState: NPCState) : TaskCondition() {
    override fun isValid() = true
}

data class TimeCondition(val startTime: Long, val endTime: Long) : TaskCondition() {
    override fun isValid() = startTime <= endTime
}

data class WeatherCondition(val requiredWeather: String) : TaskCondition() {
    override fun isValid() = requiredWeather.isNotEmpty()
}

data class HealthCondition(val minHealth: Float) : TaskCondition() {
    override fun isValid() = minHealth >= 0f
}

class CustomCondition(val check: (SmartNPC) -> Boolean) : TaskCondition() {
    override fun isValid() = true
}

@Serializable
private data class SchedulerSaveData(
    val npcSchedules: Map<String, NPCSchedule>,
    val recurringTasks: Map<String, RecurringTask>,
    val gameStartTime: Long,
    val version: Int
)
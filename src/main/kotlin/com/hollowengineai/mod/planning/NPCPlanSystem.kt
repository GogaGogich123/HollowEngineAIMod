package com.hollowengineai.mod.planning

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.async.AsyncActionSystem
import com.hollowengineai.mod.async.NPCAction
import com.hollowengineai.mod.async.ActionPriority
import com.hollowengineai.mod.async.SequentialAction
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Система планирования НПС
 * 
 * Возможности:
 * - Долгосрочное планирование целей
 * - Адаптивное планирование на основе изменений
 * - Приоритизация и управление множественными планами
 * - Интеграция с системой действий
 * - Реакция на события восприятия
 * - Создание динамических планов
 */
class NPCPlanSystem(
    private val npc: SmartNPC,
    private val actionSystem: AsyncActionSystem,
    private val eventBus: NPCEventBus
) {
    companion object {
        private val LOGGER = LogManager.getLogger(NPCPlanSystem::class.java)
        private const val PLAN_UPDATE_INTERVAL = 2000L // 2 секунды
    }
    
    // Система корутин
    private val planningScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Планы и цели
    private val activePlans = ConcurrentHashMap<UUID, ActivePlan>()
    private val completedPlans = mutableListOf<CompletedPlan>()
    private val planHistory = mutableListOf<PlanEvent>()
    
    // Состояние системы
    private var isRunning = false
    private var lastPlanUpdate = 0L
    
    // Планировщики
    private val planGenerators = mutableListOf<PlanGenerator>()
    private val planEvaluators = mutableListOf<PlanEvaluator>()
    
    /**
     * Запустить систему планирования
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        
        // Регистрируем базовые планировщики
        registerBasePlanGenerators()
        
        // Запускаем цикл планирования
        planningScope.launch {
            while (isRunning) {
                try {
                    updatePlans()
                    delay(PLAN_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    LOGGER.error("Error in planning cycle for NPC ${npc.name}", e)
                    delay(PLAN_UPDATE_INTERVAL * 2)
                }
            }
        }
        
        LOGGER.debug("NPCPlanSystem started for NPC ${npc.name}")
    }
    
    /**
     * Остановить систему планирования
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        
        // Отменяем все активные планы
        cancelAllPlans("System shutdown")
        
        planningScope.cancel()
        LOGGER.debug("NPCPlanSystem stopped for NPC ${npc.name}")
    }
    
    /**
     * Создать и добавить новый план
     */
    suspend fun createPlan(
        goal: PlanGoal,
        priority: PlanPriority = PlanPriority.NORMAL,
        planType: PlanType = PlanType.SEQUENTIAL
    ): UUID {
        val planId = UUID.randomUUID()
        
        // Генерируем действия для достижения цели
        val actions = generateActionsForGoal(goal)
        
        if (actions.isEmpty()) {
            LOGGER.warn("No actions generated for goal ${goal.description} for NPC ${npc.name}")
            throw PlanGenerationException("Cannot create plan: no actions generated for goal")
        }
        
        val plan = Plan(
            id = planId,
            goal = goal,
            actions = actions,
            type = planType,
            priority = priority,
            createdTime = System.currentTimeMillis()
        )
        
        val activePlan = ActivePlan(
            plan = plan,
            status = PlanStatus.CREATED,
            currentStep = 0,
            startTime = null,
            actionIds = emptyList()
        )
        
        activePlans[planId] = activePlan
        
        logPlanEvent(planId, PlanEventType.PLAN_CREATED, "Plan created for goal: ${goal.description}")
        
        LOGGER.debug("Created plan $planId for goal '${goal.description}' with ${actions.size} actions for NPC ${npc.name}")
        
        return planId
    }
    
    /**
     * Запустить выполнение плана
     */
    suspend fun executePlan(planId: UUID): Boolean {
        val activePlan = activePlans[planId] ?: return false
        
        if (activePlan.status != PlanStatus.CREATED && activePlan.status != PlanStatus.PAUSED) {
            LOGGER.warn("Cannot execute plan $planId in status ${activePlan.status} for NPC ${npc.name}")
            return false
        }
        
        // Проверяем можно ли выполнить план
        if (!canExecutePlan(activePlan.plan)) {
            LOGGER.debug("Cannot execute plan $planId - preconditions not met for NPC ${npc.name}")
            activePlans[planId] = activePlan.copy(status = PlanStatus.BLOCKED)
            logPlanEvent(planId, PlanEventType.PLAN_BLOCKED, "Plan blocked - preconditions not met")
            return false
        }
        
        // Отменяем планы с более низким приоритетом если необходимо
        val currentPlan = activePlan.plan
        if (shouldInterruptLowerPriorityPlans(currentPlan.priority)) {
            interruptLowerPriorityPlans(currentPlan.priority)
        }
        
        // Запускаем план
        return when (currentPlan.type) {
            PlanType.SEQUENTIAL -> executeSequentialPlan(planId, activePlan)
            PlanType.PARALLEL -> executeParallelPlan(planId, activePlan)
            PlanType.CONDITIONAL -> executeConditionalPlan(planId, activePlan)
            PlanType.REACTIVE -> executeReactivePlan(planId, activePlan)
        }
    }
    
    /**
     * Отменить план
     */
    fun cancelPlan(planId: UUID, reason: String = "Cancelled by request"): Boolean {
        val activePlan = activePlans[planId] ?: return false
        
        // Отменяем все связанные действия
        activePlan.actionIds.forEach { actionId ->
            actionSystem.cancelAction(actionId, "Plan cancelled: $reason")
        }
        
        // Перемещаем план в завершенные
        val completedPlan = CompletedPlan(
            plan = activePlan.plan,
            status = PlanStatus.CANCELLED,
            startTime = activePlan.startTime,
            endTime = System.currentTimeMillis(),
            completedSteps = activePlan.currentStep,
            failureReason = reason
        )
        
        synchronized(completedPlans) {
            completedPlans.add(completedPlan)
            if (completedPlans.size > 50) {
                completedPlans.removeAt(0)
            }
        }
        
        activePlans.remove(planId)
        logPlanEvent(planId, PlanEventType.PLAN_CANCELLED, reason)
        
        LOGGER.debug("Cancelled plan $planId for NPC ${npc.name}: $reason")
        return true
    }
    
    /**
     * Отменить все планы
     */
    fun cancelAllPlans(reason: String = "Cancelled all plans") {
        val planIds = activePlans.keys.toList()
        planIds.forEach { planId ->
            cancelPlan(planId, reason)
        }
    }
    
    /**
     * Приостановить план
     */
    fun pausePlan(planId: UUID): Boolean {
        val activePlan = activePlans[planId] ?: return false
        
        if (activePlan.status != PlanStatus.EXECUTING) {
            return false
        }
        
        // Отменяем текущие действия
        activePlan.actionIds.forEach { actionId ->
            actionSystem.cancelAction(actionId, "Plan paused")
        }
        
        activePlans[planId] = activePlan.copy(
            status = PlanStatus.PAUSED,
            actionIds = emptyList()
        )
        
        logPlanEvent(planId, PlanEventType.PLAN_PAUSED, "Plan paused")
        LOGGER.debug("Paused plan $planId for NPC ${npc.name}")
        return true
    }
    
    /**
     * Возобновить план
     */
    suspend fun resumePlan(planId: UUID): Boolean {
        val activePlan = activePlans[planId] ?: return false
        
        if (activePlan.status != PlanStatus.PAUSED) {
            return false
        }
        
        activePlans[planId] = activePlan.copy(status = PlanStatus.CREATED)
        logPlanEvent(planId, PlanEventType.PLAN_RESUMED, "Plan resumed")
        
        return executePlan(planId)
    }
    
    /**
     * Цикл обновления планов
     */
    private suspend fun updatePlans() {
        val currentTime = System.currentTimeMillis()
        lastPlanUpdate = currentTime
        
        // Обновляем статусы планов
        updatePlanStatuses()
        
        // Генерируем новые планы на основе изменений
        generateNewPlansIfNeeded()
        
        // Очищаем завершенные планы
        cleanupCompletedPlans(currentTime)
        
        // Автоматически запускаем планы с высоким приоритетом
        startHighPriorityPlans()
    }
    
    /**
     * Обновить статусы планов
     */
    private suspend fun updatePlanStatuses() {
        val toUpdate = mutableListOf<Pair<UUID, ActivePlan>>()
        
        activePlans.forEach { (planId, activePlan) ->
            when (activePlan.status) {
                PlanStatus.EXECUTING -> {
                    // Проверяем завершились ли действия
                    val completedActions = activePlan.actionIds.count { actionId ->
                        !actionSystem.isActionRunning(actionId)
                    }
                    
                    if (completedActions == activePlan.actionIds.size && activePlan.actionIds.isNotEmpty()) {
                        // Все действия завершены
                        if (activePlan.currentStep >= activePlan.plan.actions.size) {
                            // План полностью завершен
                            toUpdate.add(planId to activePlan.copy(status = PlanStatus.COMPLETED))
                        } else {
                            // Переходим к следующему шагу
                            continueSequentialPlan(planId, activePlan)
                        }
                    }
                }
                PlanStatus.BLOCKED -> {
                    // Проверяем можно ли разблокировать план
                    if (canExecutePlan(activePlan.plan)) {
                        toUpdate.add(planId to activePlan.copy(status = PlanStatus.CREATED))
                        logPlanEvent(planId, PlanEventType.PLAN_UNBLOCKED, "Plan unblocked - conditions now met")
                    }
                }
                else -> { /* Ничего не делаем */ }
            }
        }
        
        // Применяем обновления
        toUpdate.forEach { (planId, updatedPlan) ->
            activePlans[planId] = updatedPlan
            
            if (updatedPlan.status == PlanStatus.COMPLETED) {
                completePlan(planId, updatedPlan)
            }
        }
    }
    
    /**
     * Завершить план
     */
    private fun completePlan(planId: UUID, activePlan: ActivePlan) {
        val completedPlan = CompletedPlan(
            plan = activePlan.plan,
            status = PlanStatus.COMPLETED,
            startTime = activePlan.startTime,
            endTime = System.currentTimeMillis(),
            completedSteps = activePlan.plan.actions.size
        )
        
        synchronized(completedPlans) {
            completedPlans.add(completedPlan)
            if (completedPlans.size > 50) {
                completedPlans.removeAt(0)
            }
        }
        
        activePlans.remove(planId)
        logPlanEvent(planId, PlanEventType.PLAN_COMPLETED, "Plan completed successfully")
        
        publishPlanEvent("plan_completed", activePlan.plan)
        
        LOGGER.debug("Completed plan $planId (${activePlan.plan.goal.description}) for NPC ${npc.name}")
    }
    
    /**
     * Выполнить последовательный план
     */
    private suspend fun executeSequentialPlan(planId: UUID, activePlan: ActivePlan): Boolean {
        val plan = activePlan.plan
        
        if (plan.actions.isEmpty()) {
            LOGGER.warn("Cannot execute empty sequential plan $planId for NPC ${npc.name}")
            return false
        }
        
        // Начинаем выполнение первого действия
        val firstAction = plan.actions[0]
        val actionId = actionSystem.queueAction(firstAction, plan.priority.toActionPriority())
        
        activePlans[planId] = activePlan.copy(
            status = PlanStatus.EXECUTING,
            currentStep = 0,
            startTime = System.currentTimeMillis(),
            actionIds = listOf(actionId)
        )
        
        logPlanEvent(planId, PlanEventType.PLAN_STARTED, "Sequential plan execution started")
        publishPlanEvent("plan_started", plan)
        
        LOGGER.debug("Started sequential plan $planId with first action: ${firstAction.name} for NPC ${npc.name}")
        return true
    }
    
    /**
     * Продолжить выполнение последовательного плана
     */
    private suspend fun continueSequentialPlan(planId: UUID, activePlan: ActivePlan) {
        val plan = activePlan.plan
        val nextStep = activePlan.currentStep + 1
        
        if (nextStep >= plan.actions.size) {
            // План завершен
            activePlans[planId] = activePlan.copy(status = PlanStatus.COMPLETED)
            return
        }
        
        val nextAction = plan.actions[nextStep]
        val actionId = actionSystem.queueAction(nextAction, plan.priority.toActionPriority())
        
        activePlans[planId] = activePlan.copy(
            currentStep = nextStep,
            actionIds = listOf(actionId)
        )
        
        LOGGER.debug("Continuing sequential plan $planId with step $nextStep: ${nextAction.name} for NPC ${npc.name}")
    }
    
    /**
     * Выполнить параллельный план
     */
    private suspend fun executeParallelPlan(planId: UUID, activePlan: ActivePlan): Boolean {
        val plan = activePlan.plan
        
        if (plan.actions.isEmpty()) {
            LOGGER.warn("Cannot execute empty parallel plan $planId for NPC ${npc.name}")
            return false
        }
        
        // Запускаем все действия параллельно
        val actionIds = mutableListOf<UUID>()
        plan.actions.forEach { action ->
            val actionId = actionSystem.queueAction(action, plan.priority.toActionPriority())
            actionIds.add(actionId)
        }
        
        activePlans[planId] = activePlan.copy(
            status = PlanStatus.EXECUTING,
            startTime = System.currentTimeMillis(),
            actionIds = actionIds
        )
        
        logPlanEvent(planId, PlanEventType.PLAN_STARTED, "Parallel plan execution started")
        publishPlanEvent("plan_started", plan)
        
        LOGGER.debug("Started parallel plan $planId with ${actionIds.size} actions for NPC ${npc.name}")
        return true
    }
    
    /**
     * Выполнить условный план
     */
    private suspend fun executeConditionalPlan(planId: UUID, activePlan: ActivePlan): Boolean {
        // Для условных планов нужна более сложная логика
        // Пока используем последовательное выполнение
        return executeSequentialPlan(planId, activePlan)
    }
    
    /**
     * Выполнить реактивный план
     */
    private suspend fun executeReactivePlan(planId: UUID, activePlan: ActivePlan): Boolean {
        // Реактивные планы выполняются по триггерам
        // Пока помечаем как выполняющийся и ждем событий
        activePlans[planId] = activePlan.copy(
            status = PlanStatus.EXECUTING,
            startTime = System.currentTimeMillis()
        )
        
        logPlanEvent(planId, PlanEventType.PLAN_STARTED, "Reactive plan activated")
        return true
    }
    
    // Вспомогательные методы
    
    private fun canExecutePlan(plan: Plan): Boolean {
        // Проверяем можно ли выполнить план
        return plan.preconditions.all { it.invoke(npc) }
    }
    
    private fun shouldInterruptLowerPriorityPlans(priority: PlanPriority): Boolean {
        return activePlans.values.any { 
            it.status == PlanStatus.EXECUTING && 
            it.plan.priority.ordinal < priority.ordinal 
        }
    }
    
    private fun interruptLowerPriorityPlans(higherPriority: PlanPriority) {
        val toInterrupt = activePlans.entries.filter { (_, activePlan) ->
            activePlan.status == PlanStatus.EXECUTING && 
            activePlan.plan.priority.ordinal < higherPriority.ordinal
        }
        
        toInterrupt.forEach { (planId, _) ->
            pausePlan(planId)
        }
        
        if (toInterrupt.isNotEmpty()) {
            LOGGER.debug("Interrupted ${toInterrupt.size} lower priority plans for NPC ${npc.name}")
        }
    }
    
    private suspend fun generateActionsForGoal(goal: PlanGoal): List<NPCAction> {
        val actions = mutableListOf<NPCAction>()
        
        planGenerators.forEach { generator ->
            val generatedActions = generator.generateActions(npc, goal)
            actions.addAll(generatedActions)
        }
        
        return actions
    }
    
    private fun generateNewPlansIfNeeded() {
        // Генерация новых планов на основе текущего состояния
        // Пока не реализуем
    }
    
    private fun cleanupCompletedPlans(currentTime: Long) {
        synchronized(completedPlans) {
            completedPlans.removeIf { 
                currentTime - (it.endTime ?: 0) > 300000 // 5 минут 
            }
        }
        
        synchronized(planHistory) {
            planHistory.removeIf { 
                currentTime - it.timestamp > 600000 // 10 минут 
            }
        }
    }
    
    private suspend fun startHighPriorityPlans() {
        activePlans.values
            .filter { it.status == PlanStatus.CREATED && it.plan.priority == PlanPriority.CRITICAL }
            .forEach { activePlan ->
                executePlan(activePlan.plan.id)
            }
    }
    
    private fun logPlanEvent(planId: UUID, eventType: PlanEventType, description: String) {
        val event = PlanEvent(
            planId = planId,
            eventType = eventType,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(planHistory) {
            planHistory.add(event)
            if (planHistory.size > 200) {
                planHistory.removeAt(0)
            }
        }
    }
    
    private fun publishPlanEvent(eventType: String, plan: Plan) {
        try {
            val event = NPCEvents.customEvent(
                npcId = npc.id,
                npcName = npc.name,
                eventData = mapOf(
                    "eventType" to eventType,
                    "planId" to plan.id.toString(),
                    "goalType" to plan.goal.type.name,
                    "goalDescription" to plan.goal.description,
                    "priority" to plan.priority.name,
                    "actionCount" to plan.actions.size
                ),
                position = npc.getEntity().blockPosition()
            )
            
            eventBus.sendEventSync(event)
        } catch (e: Exception) {
            LOGGER.warn("Failed to publish plan event for NPC ${npc.name}", e)
        }
    }
    
    private fun registerBasePlanGenerators() {
        planGenerators.add(BasicPlanGenerator())
        // Можно добавить больше генераторов планов
    }
    
    // Геттеры
    
    fun getActivePlans(): List<PlanInfo> {
        return activePlans.values.map { activePlan ->
            PlanInfo(
                id = activePlan.plan.id,
                goalDescription = activePlan.plan.goal.description,
                goalType = activePlan.plan.goal.type,
                priority = activePlan.plan.priority,
                status = activePlan.status,
                currentStep = activePlan.currentStep,
                totalSteps = activePlan.plan.actions.size,
                startTime = activePlan.startTime
            )
        }
    }
    
    fun getPlanInfo(planId: UUID): PlanInfo? {
        val activePlan = activePlans[planId] ?: return null
        return PlanInfo(
            id = activePlan.plan.id,
            goalDescription = activePlan.plan.goal.description,
            goalType = activePlan.plan.goal.type,
            priority = activePlan.plan.priority,
            status = activePlan.status,
            currentStep = activePlan.currentStep,
            totalSteps = activePlan.plan.actions.size,
            startTime = activePlan.startTime
        )
    }
    
    fun getStats(): PlanSystemStats {
        val executing = activePlans.values.count { it.status == PlanStatus.EXECUTING }
        val created = activePlans.values.count { it.status == PlanStatus.CREATED }
        val paused = activePlans.values.count { it.status == PlanStatus.PAUSED }
        val blocked = activePlans.values.count { it.status == PlanStatus.BLOCKED }
        
        return PlanSystemStats(
            activePlans = activePlans.size,
            executingPlans = executing,
            createdPlans = created,
            pausedPlans = paused,
            blockedPlans = blocked,
            completedPlans = synchronized(completedPlans) { completedPlans.size }
        )
    }
}

// Расширения
private fun PlanPriority.toActionPriority(): ActionPriority {
    return when (this) {
        PlanPriority.LOW -> ActionPriority.LOW
        PlanPriority.NORMAL -> ActionPriority.NORMAL
        PlanPriority.HIGH -> ActionPriority.HIGH
        PlanPriority.CRITICAL -> ActionPriority.CRITICAL
    }
}

/**
 * Исключение генерации плана
 */
class PlanGenerationException(message: String) : Exception(message)
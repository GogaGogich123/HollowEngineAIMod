package com.hollowengineai.mod.core

import com.hollowengineai.mod.actions.ActionExecutor
import com.hollowengineai.mod.actions.OptimizedActionExecutor
import com.hollowengineai.mod.config.AIConfig
import com.hollowengineai.mod.llm.OllamaClient
import com.hollowengineai.mod.memory.DatabaseManager
import com.hollowengineai.mod.memory.NPCMemory
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.performance.CoroutinePoolManager
import com.hollowengineai.mod.performance.CacheManager
import com.hollowengineai.mod.reputation.ReputationSystem
import com.hollowengineai.mod.social.SocialGroupManager
import com.hollowengineai.mod.scheduler.NPCScheduler
import kotlinx.coroutines.*
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер AI НПС
 * 
 * Отвечает за:
 * - Создание и управление AI НПС
 * - LOD систему (Level of Detail) для производительности
 * - Интеграцию с HollowEngine Legacy
 * - Мониторинг производительности
 */
class NPCManager(
    private val ollamaClient: OllamaClient,
    private val databaseManager: DatabaseManager
) {
    companion object {
        private val LOGGER = LogManager.getLogger(NPCManager::class.java)
        private const val LOD_UPDATE_INTERVAL_MS = 5000L // 5 секунд
        private const val PERFORMANCE_LOG_INTERVAL_MS = 30000L // 30 секунд
    }
    
    // Реестр всех AI НПС
    private val aiNPCs = ConcurrentHashMap<UUID, SmartNPC>()
    
    // Компоненты системы
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var optimizedActionExecutor: OptimizedActionExecutor
    
    // Новые системы
    private lateinit var eventBus: NPCEventBusImpl
    private var reputationSystem: ReputationSystem? = null
    private var socialGroupManager: SocialGroupManager? = null
    private var npcScheduler: NPCScheduler? = null
    
    // Система управления производительностью
    private val lodManager = LODManager()
    private val performanceMonitor = PerformanceMonitor()
    
    // Используем оптимизированные пулы корутин
    private var lodUpdateJob: Job? = null
    private var performanceJob: Job? = null
    private var systemMaintenanceJob: Job? = null
    
    // Статистика
    @Volatile
    private var isRunning = false
    
    /**
     * Запустить менеджер НПС
     */
    fun start() {
        if (isRunning) {
            LOGGER.warn("NPCManager is already running")
            return
        }
        
        LOGGER.info("Starting NPC Manager...")
        
        try {
            // Инициализируем компоненты
            initializeComponents()
            
            // Загружаем существующих НПС из базы данных
            loadExistingNPCs()
            
            // Запускаем фоновые задачи
            startBackgroundJobs()
            
            isRunning = true
            LOGGER.info("NPC Manager started successfully. Active NPCs: ${aiNPCs.size}")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to start NPC Manager", e)
            stop()
            throw e
        }
    }
    
    /**
     * Остановить менеджер НПС
     */
    fun stop() {
        if (!isRunning) {
            LOGGER.warn("NPCManager is not running")
            return
        }
        
        LOGGER.info("Stopping NPC Manager...")
        isRunning = false
        
        try {
            // Останавливаем фоновые задачи
            lodUpdateJob?.cancel()
            performanceJob?.cancel()
            systemMaintenanceJob?.cancel()
            
            // Останавливаем всех НПС
            aiNPCs.values.forEach { npc ->
                try {
                    npc.stop()
                } catch (e: Exception) {
                    LOGGER.error("Error stopping NPC ${npc.name}", e)
                }
            }
            
            // Останавливаем новые системы
            try {
                npcScheduler?.shutdown()
                socialGroupManager?.shutdown()
                reputationSystem?.shutdown()
                eventBus.shutdown()
                CacheManager.shutdown()
                CoroutinePoolManager.shutdown()
                LOGGER.info("Новые системы остановлены")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при остановке новых систем", e)
            }
            
            // Очищаем реестр
            aiNPCs.clear()
            
            LOGGER.info("NPC Manager stopped successfully")
            
        } catch (e: Exception) {
            LOGGER.error("Error during NPC Manager shutdown", e)
        }
    }
    
    /**
     * Найти НПС entity по ID (для загрузки из базы данных)
     */
    private fun findNPCEntityById(npcId: String): LivingEntity? {
        return try {
            // В реальности здесь нужно будет связаться с HollowEngine Legacy
            // для поиска сущности НПС по ID
            
            val serverInstance = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
            if (serverInstance == null) {
                LOGGER.warn("Server instance not available for NPC search")
                return null
            }
            
            // Проходим по всем измерениям и ищем сущность
            for (level in serverInstance.allLevels) {
                val entities = level.allEntities
                for (entity in entities) {
                    if (entity is LivingEntity) {
                        // Проверяем UUID или кастомные теги HollowEngine
                        val entityId = entity.uuid.toString()
                        if (entityId == npcId) {
                            return entity
                        }
                        
                        // Проверяем кастомные теги для HollowEngine Legacy
                        val tags = entity.tags
                        if (tags.contains("hollowengine:npc_id:$npcId")) {
                            return entity
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            LOGGER.error("Error searching for NPC entity with ID: $npcId", e)
            null
        }
    }
    
    /**
     * Создать нового AI НПС
     */
    fun createSmartNPC(
        entity: LivingEntity,
        name: String,
        personalityType: PersonalityType,
        traits: PersonalityTraits? = null
    ): SmartNPC? {
        
        if (!isRunning) {
            LOGGER.error("Cannot create NPC - manager is not running")
            return null
        }
        
        if (aiNPCs.size >= AIConfig.maxAINPCs) {
            LOGGER.warn("Cannot create NPC - maximum limit reached (${AIConfig.maxAINPCs})")
            return null
        }
        
        try {
            val npcId = UUID.randomUUID()
            
            LOGGER.info("Creating smart NPC: $name ($npcId)")
            
            // Создаем память НПС
            val memory = NPCMemory(npcId, databaseManager)
            memory.initialize()
            
            // Применяем кастомные черты личности если указаны
            val finalPersonalityType = if (traits != null) {
                personalityType.copy(traits = traits)
            } else {
                personalityType
            }
            
            // Создаем НПС с новыми системами
            val smartNPC = SmartNPC(
                id = npcId,
                name = name,
                personalityType = finalPersonalityType,
                entity = entity,
                memory = memory,
                actionExecutor = actionExecutor,
                decisionEngine = decisionEngine,
                eventBus = eventBus
            )
            
            // Регистрируем в системе
            aiNPCs[npcId] = smartNPC
            
            // Запускаем AI системы
            smartNPC.start()
            
            // Обновляем LOD систему
            lodManager.addNPC(smartNPC)
            
            LOGGER.info("Smart NPC created successfully: $name")
            return smartNPC
            
        } catch (e: Exception) {
            LOGGER.error("Failed to create smart NPC: $name", e)
            return null
        }
    }
    
    /**
     * Удалить AI НПС
     */
    fun removeSmartNPC(npcId: UUID): Boolean {
        val npc = aiNPCs.remove(npcId) ?: return false
        
        try {
            LOGGER.info("Removing smart NPC: ${npc.name} ($npcId)")
            
            // Останавливаем AI системы
            npc.stop()
            
            // Убираем из LOD системы
            lodManager.removeNPC(npc)
            
            LOGGER.info("Smart NPC removed: ${npc.name}")
            return true
            
        } catch (e: Exception) {
            LOGGER.error("Error removing smart NPC ${npc.name}", e)
            return false
        }
    }
    
    /**
     * Получить AI НПС по ID
     */
    fun getSmartNPC(npcId: UUID): SmartNPC? {
        return aiNPCs[npcId]
    }
    
    /**
     * Получить всех AI НПС
     */
    fun getAllSmartNPCs(): Collection<SmartNPC> {
        return aiNPCs.values.toList()
    }
    
    /**
     * Получить AI НПС по строковому ID
     * Поддерживает как UUID, так и entity ID из HollowEngine
     */
    fun getNPCById(id: String): SmartNPC? {
        // Сначала пытаемся найти по UUID
        try {
            val uuid = UUID.fromString(id)
            return aiNPCs[uuid]
        } catch (e: IllegalArgumentException) {
            // Если не UUID, ищем по всем NPC с совпадающим строковым представлением ID
            return aiNPCs.values.find { npc ->
                npc.id.toString() == id || 
                npc.entity.uuid.toString() == id ||
                npc.entity.stringUUID == id
            }
        }
    }
    
    /**
     * Получить AI НПС по имени
     * Возвращает первого найденного НПС с указанным именем
     */
    fun getNPCByName(name: String): SmartNPC? {
        return aiNPCs.values.find { npc ->
            npc.name.equals(name, ignoreCase = true) ||
            npc.entity.name?.string?.equals(name, ignoreCase = true) == true
        }
    }
    
    /**
     * Получить всех AI НПС с указанным именем
     * Полезно когда несколько НПС имеют одинаковые имена
     */
    fun getAllNPCsByName(name: String): List<SmartNPC> {
        return aiNPCs.values.filter { npc ->
            npc.name.equals(name, ignoreCase = true) ||
            npc.entity.name?.string?.equals(name, ignoreCase = true) == true
        }
    }
    
    /**
     * Получить AI НПС в определенном радиусе
     */
    fun getSmartNPCsNearby(level: Level, x: Double, y: Double, z: Double, radius: Double): List<SmartNPC> {
        return aiNPCs.values.filter { npc ->
            npc.level == level && 
            npc.isActive &&
            npc.position.distSqr(x.toInt(), y.toInt(), z.toInt()) <= radius * radius
        }
    }
    
    /**
     * Получить статистику менеджера
     */
    fun getManagerStats(): ManagerStats {
        val activeNPCs = aiNPCs.values.count { it.isActive }
        val inactiveNPCs = aiNPCs.size - activeNPCs
        
        return ManagerStats(
            totalNPCs = aiNPCs.size,
            activeNPCs = activeNPCs,
            inactiveNPCs = inactiveNPCs,
            maxNPCs = AIConfig.maxAINPCs,
            isRunning = isRunning,
            memoryUsageMB = performanceMonitor.getMemoryUsageMB(),
            averageDecisionTimeMs = performanceMonitor.getAverageDecisionTime()
        )
    }
    
    /**
     * Инициализировать компоненты системы
     */
    private fun initializeComponents() {
        LOGGER.debug("Инициализация компонентов NPC Manager...")
        
        try {
            // 1. Инициализируем оптимизации производительности (первыми!)
            CoroutinePoolManager.initialize()
            CacheManager.initialize()
            LOGGER.info("Оптимизации производительности инициализированы")
            
            // 2. Инициализируем EventBus (вторым!)
            eventBus = NPCEventBus.instance
            eventBus.initialize()
            LOGGER.info("EventBus инициализирован")
            
            // 3. Инициализируем системы репутации и групп
            try {
                ReputationSystem.initialize()
                reputationSystem = ReputationSystem
                LOGGER.info("Система репутации инициализирована")
                
                socialGroupManager = SocialGroupManager
                socialGroupManager?.initialize()
                LOGGER.info("Менеджер социальных групп инициализирован")
            } catch (e: Exception) {
                LOGGER.warn("Не удалось инициализировать системы репутации/групп", e)
            }
            
            // 4. Инициализируем планировщик
            try {
                npcScheduler = NPCScheduler
                npcScheduler?.initialize()
                LOGGER.info("Планировщик NPC инициализирован")
            } catch (e: Exception) {
                LOGGER.warn("Не удалось инициализировать планировщик NPC", e)
            }
            
            // 5. Создаем основные компоненты
            decisionEngine = DecisionEngine(ollamaClient)
            actionExecutor = ActionExecutor()
            optimizedActionExecutor = OptimizedActionExecutor()
            
            LOGGER.info("Все компоненты успешно инициализированы")
            
        } catch (e: Exception) {
            LOGGER.error("Ошибка при инициализации компонентов", e)
            throw e
        }
    }
    
    /**
     * Загрузить существующих НПС из базы данных
     */
    private fun loadExistingNPCs() {
        LOGGER.debug("Loading existing NPCs from database...")
        
        try {
            // Загружаем сохраненных НПС из базы данных
            val savedNPCs = databaseManager.loadAllNPCs()
            var loadedCount = 0
            
            savedNPCs.forEach { npcData ->
                try {
                    // Проверяем что НПС еще не загружен
                    if (!aiNPCs.containsKey(npcData.id)) {
                        // Получаем ссылку на entity из HollowEngine Legacy
                        val entity = findNPCEntityById(npcData.id)
                        
                        if (entity != null && entity.isAlive) {
                            // Создаем SmartNPC с сохраненными данными и новыми системами
                            val smartNPC = SmartNPC(
                                id = npcData.id,
                                name = npcData.name,
                                entity = entity,
                                personalityType = npcData.personalityType,
                                decisionEngine = decisionEngine,
                                actionExecutor = actionExecutor,
                                memory = NPCMemory(npcData.id, databaseManager),
                                eventBus = eventBus
                            )
                            
                            // Загружаем состояние из базы данных
                            smartNPC.loadState(npcData)
                            
                            // Добавляем в список активных НПС
                            aiNPCs[npcData.id] = smartNPC
                            loadedCount++
                            
                            // Запускаем AI системы
                            smartNPC.start()
                            
                            LOGGER.debug("Loaded AI NPC: ${npcData.name} (${npcData.id})")
                        } else {
                            // Entity не найдена или мертва - отмечаем как inactive
                            databaseManager.markNPCInactive(npcData.id)
                            LOGGER.warn("NPC entity not found for saved NPC: ${npcData.name} (${npcData.id})")
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.error("Failed to load individual NPC: ${npcData.id}", e)
                }
            }
            
            LOGGER.info("Successfully loaded $loadedCount AI NPCs from database")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to load NPCs from database", e)
        }
    }
    
    /**
     * Запустить фоновые задачи
     */
    private fun startBackgroundJobs() {
        // LOD система обновляется каждые 5 секунд (с оптимизированным пулом)
        lodUpdateJob = CoroutineScope(CoroutinePoolManager.backgroundContext).launch {
            while (isRunning && currentCoroutineContext().isActive) {
                try {
                    lodManager.updateLOD(aiNPCs.values)
                    delay(LOD_UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    LOGGER.error("Error in LOD update job", e)
                    delay(LOD_UPDATE_INTERVAL_MS * 2)
                }
            }
        }
        
        // Мониторинг производительности каждые 30 секунд
        performanceJob = CoroutineScope(CoroutinePoolManager.backgroundContext).launch {
            while (isRunning && currentCoroutineContext().isActive) {
                try {
                    performanceMonitor.collectMetrics(aiNPCs.values)
                    
                    if (performanceMonitor.shouldLogStats()) {
                        logPerformanceStats()
                    }
                    
                    delay(PERFORMANCE_LOG_INTERVAL_MS)
                } catch (e: Exception) {
                    LOGGER.error("Error in performance monitoring job", e)
                    delay(PERFORMANCE_LOG_INTERVAL_MS * 2)
                }
            }
        }
        
        // Обслуживание новых систем (каждые 60 секунд)
        systemMaintenanceJob = CoroutineScope(CoroutinePoolManager.backgroundContext).launch {
            while (isRunning && currentCoroutineContext().isActive) {
                try {
                    // Очистка кэшей
                    CacheManager.cleanupExpiredEntries()
                    
                    // Обновление репутации (затухание)
                    reputationSystem?.performMaintenanceTasks()
                    
                    // Обновление групповой динамики
                    socialGroupManager?.updateGroupDynamics()
                    
                    // Очистка планировщика
                    npcScheduler?.cleanupCompletedTasks()
                    
                    delay(60000L) // 60 секунд
                    
                } catch (e: Exception) {
                    LOGGER.error("Error in system maintenance job", e)
                    delay(120000L) // 2 минуты при ошибке
                }
            }
        }
    }
    
    /**
     * Логировать статистику производительности
     */
    private fun logPerformanceStats() {
        val stats = getManagerStats()
        
        LOGGER.info(
            "Performance Stats - NPCs: ${stats.activeNPCs}/${stats.totalNPCs} active, " +
            "Memory: ${stats.memoryUsageMB}MB, " +
            "Avg Decision Time: ${stats.averageDecisionTimeMs}ms"
        )
        
        // Предупреждения о производительности
        if (stats.memoryUsageMB > 1024) { // > 1GB
            LOGGER.warn("High memory usage detected: ${stats.memoryUsageMB}MB")
        }
        
        if (stats.averageDecisionTimeMs > 2000) { // > 2 секунд
            LOGGER.warn("Slow decision making detected: ${stats.averageDecisionTimeMs}ms")
        }
    }
    
    // === НОВЫЕ МЕТОДЫ ДЛЯ ИНТЕГРАЦИИ ===
    
    /**
     * Получить ссылку на EventBus
     */
    fun getEventBus(): NPCEventBusImpl = eventBus
    
    /**
     * Получить ссылку на систему репутации
     */
    fun getReputationSystem(): ReputationSystem? = reputationSystem
    
    /**
     * Получить ссылку на менеджер социальных групп
     */
    fun getSocialGroupManager(): SocialGroupManager? = socialGroupManager
    
    /**
     * Получить ссылку на планировщик NPC
     */
    fun getNPCScheduler(): NPCScheduler? = npcScheduler
    
    /**
     * Создать социальную группу и добавить NPC
     */
    fun createSocialGroup(
        name: String, 
        type: com.hollowengineai.mod.social.GroupType, 
        leaderNPCId: UUID,
        memberIds: List<UUID> = emptyList()
    ): UUID? {
        return try {
            socialGroupManager?.createGroup(name, type, leaderNPCId)?.also { groupId ->
                // Добавляем членов
                memberIds.forEach { memberId ->
                    val npc = getSmartNPC(memberId)
                    if (npc != null) {
                        socialGroupManager?.addGroupMember(groupId, memberId, com.hollowengineai.mod.social.GroupRole.MEMBER)
                        npc.joinGroup(groupId)
                    }
                }
                
                // Лидер тоже присоединяется
                val leaderNPC = getSmartNPC(leaderNPCId)
                leaderNPC?.joinGroup(groupId)
                
                LOGGER.info("Создана социальная группа: $name ($groupId)")
            }
        } catch (e: Exception) {
            LOGGER.error("Ошибка при создании социальной группы", e)
            null
        }
    }
    
    /**
     * Добавить NPC в существующую группу
     */
    fun addNPCToGroup(npcId: UUID, groupId: UUID, role: com.hollowengineai.mod.social.GroupRole = com.hollowengineai.mod.social.GroupRole.MEMBER): Boolean {
        return try {
            val npc = getSmartNPC(npcId) ?: return false
            val success = socialGroupManager?.addGroupMember(groupId, npcId, role) == true
            
            if (success) {
                npc.joinGroup(groupId)
                LOGGER.info("NPC ${npc.name} добавлен в группу $groupId")
            }
            
            success
        } catch (e: Exception) {
            LOGGER.error("Ошибка при добавлении NPC в группу", e)
            false
        }
    }
    
    /**
     * Убрать NPC из группы
     */
    fun removeNPCFromGroup(npcId: UUID): Boolean {
        return try {
            val npc = getSmartNPC(npcId) ?: return false
            val groupId = npc.getCurrentGroup() ?: return false
            
            val success = socialGroupManager?.removeGroupMember(groupId, npcId) == true
            
            if (success) {
                npc.leaveGroup()
                LOGGER.info("NPC ${npc.name} удален из группы $groupId")
            }
            
            success
        } catch (e: Exception) {
            LOGGER.error("Ошибка при удалении NPC из группы", e)
            false
        }
    }
    
    /**
     * Запланировать задачу для NPC
     */
    fun scheduleTaskForNPC(
        npcId: UUID,
        taskName: String,
        actionType: String,
        scheduledTime: Long,
        priority: com.hollowengineai.mod.scheduler.TaskPriority = com.hollowengineai.mod.scheduler.TaskPriority.NORMAL,
        parameters: Map<String, Any> = emptyMap()
    ): Boolean {
        return try {
            val task = com.hollowengineai.mod.scheduler.NPCTask(
                id = UUID.randomUUID(),
                npcId = npcId,
                name = taskName,
                actionType = actionType,
                scheduledTime = scheduledTime,
                priority = priority,
                parameters = parameters
            )
            
            npcScheduler?.scheduleTask(task) == true
        } catch (e: Exception) {
            LOGGER.error("Ошибка при планировании задачи для NPC", e)
            false
        }
    }
    
    /**
     * Получить расширенную статистику менеджера
     */
    fun getExtendedManagerStats(): ExtendedManagerStats {
        val basicStats = getManagerStats()
        
        return ExtendedManagerStats(
            basicStats = basicStats,
            totalGroups = socialGroupManager?.getAllGroups()?.size ?: 0,
            totalReputationEntries = reputationSystem?.getTotalReputationEntries() ?: 0,
            scheduledTasks = npcScheduler?.getTotalScheduledTasks() ?: 0,
            cacheHitRate = CacheManager.getGlobalHitRate(),
            coroutinePoolStats = CoroutinePoolManager.getPoolStats()
        )
    }
}

/**
 * Статистика менеджера НПС
 */
data class ManagerStats(
    val totalNPCs: Int,
    val activeNPCs: Int,
    val inactiveNPCs: Int,
    val maxNPCs: Int,
    val isRunning: Boolean,
    val memoryUsageMB: Long,
    val averageDecisionTimeMs: Long
)

/**
 * Расширенная статистика менеджера
 */
data class ExtendedManagerStats(
    val basicStats: ManagerStats,
    val totalGroups: Int,
    val totalReputationEntries: Int,
    val scheduledTasks: Int,
    val cacheHitRate: Double,
    val coroutinePoolStats: Map<String, Any>
)

/**
 * Менеджер Level of Detail для оптимизации производительности
 */
private class LODManager {
    private val LOGGER = LogManager.getLogger(LODManager::class.java)
    private val npcLODLevels = ConcurrentHashMap<String, LODLevel>()
    
    companion object {
        private const val ACTIVE_DISTANCE = 32.0 // Полная активность
        private const val NEARBY_DISTANCE = 64.0 // Уменьшенная активность  
        private const val BACKGROUND_DISTANCE = 128.0 // Минимальная активность
    }
    
    fun addNPC(npc: SmartNPC) {
        try {
            npcLODLevels[npc.id] = LODLevel.ACTIVE
            LOGGER.debug("Added NPC ${npc.name} to LOD system")
        } catch (e: Exception) {
            LOGGER.error("Failed to add NPC ${npc.name} to LOD system", e)
        }
    }
    
    fun removeNPC(npc: SmartNPC) {
        try {
            npcLODLevels.remove(npc.id)
            LOGGER.debug("Removed NPC ${npc.name} from LOD system")
        } catch (e: Exception) {
            LOGGER.error("Failed to remove NPC ${npc.name} from LOD system", e)
        }
    }
    
    fun updateLOD(npcs: Collection<SmartNPC>) {
        try {
            val serverInstance = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
            if (serverInstance == null) return
            
            val players = serverInstance.playerList.players
            if (players.isEmpty()) {
                // Нет игроков - все НПС в background режим
                npcs.forEach { npc -> updateNPCLOD(npc, LODLevel.BACKGROUND) }
                return
            }
            
            // Получаем текущую загрузку сервера
            val serverLoad = getServerLoad()
            val lodMultiplier = when {
                serverLoad > 0.8 -> 0.5 // При высокой загрузке уменьшаем дальность
                serverLoad > 0.6 -> 0.8
                else -> 1.0
            }
            
            val activeDistance = ACTIVE_DISTANCE * lodMultiplier
            val nearbyDistance = NEARBY_DISTANCE * lodMultiplier
            val backgroundDistance = BACKGROUND_DISTANCE * lodMultiplier
            
            var activeCount = 0
            var nearbyCount = 0
            var backgroundCount = 0
            
            npcs.forEach { npc ->
                if (!npc.isActive) return@forEach
                
                val minDistanceToPlayer = players.minOfOrNull { player ->
                    val playerPos = player.position()
                    val npcPos = npc.position
                    
                    if (player.level != npc.level) Double.MAX_VALUE
                    else playerPos.distanceToSqr(
                        npcPos.x.toDouble(), 
                        npcPos.y.toDouble(), 
                        npcPos.z.toDouble()
                    )
                } ?: Double.MAX_VALUE
                
                val distance = kotlin.math.sqrt(minDistanceToPlayer)
                
                val newLOD = when {
                    distance <= activeDistance -> {
                        activeCount++
                        LODLevel.ACTIVE
                    }
                    distance <= nearbyDistance -> {
                        nearbyCount++
                        LODLevel.NEARBY
                    }
                    distance <= backgroundDistance -> {
                        backgroundCount++
                        LODLevel.BACKGROUND
                    }
                    else -> LODLevel.INACTIVE
                }
                
                updateNPCLOD(npc, newLOD)
            }
            
        } catch (e: Exception) {
            LOGGER.error("Failed to update LOD levels", e)
        }
    }
    
    private fun updateNPCLOD(npc: SmartNPC, newLOD: LODLevel) {
        val currentLOD = npcLODLevels[npc.id]
        if (currentLOD != newLOD) {
            npcLODLevels[npc.id] = newLOD
            // В будущем здесь можно настроить частоту обновлений AI на основе LOD
        }
    }
    
    private fun getServerLoad(): Double {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            (usedMemory.toDouble() / maxMemory.toDouble()).coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            0.5 // По умолчанию средняя загрузка
        }
    }
    
    fun getNPCLOD(npcId: String): LODLevel {
        return npcLODLevels[npcId] ?: LODLevel.ACTIVE
    }
}

/**
 * Монитор производительности
 */
private class PerformanceMonitor {
    private val runtime = Runtime.getRuntime()
    private var lastLogTime = 0L
    private var decisionTimes = mutableListOf<Long>()
    
    fun getMemoryUsageMB(): Long {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    fun getAverageDecisionTime(): Long {
        return if (decisionTimes.isNotEmpty()) {
            decisionTimes.average().toLong()
        } else {
            0L
        }
    }
    
    fun collectMetrics(npcs: Collection<SmartNPC>) {
        // TODO: Собрать метрики производительности
    }
    
    fun shouldLogStats(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastLogTime > 30000L // Логировать раз в 30 секунд
    }
}
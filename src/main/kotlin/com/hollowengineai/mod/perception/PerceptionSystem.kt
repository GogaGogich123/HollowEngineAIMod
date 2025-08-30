package com.hollowengineai.mod.perception

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEventType
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Система восприятия НПС - анализирует окружающую среду и игроков
 * 
 * Возможности:
 * - Обнаружение игроков в радиусе действия
 * - Анализ поведения и намерений игроков
 * - Детекция взгляда и внимания
 * - Оценка социальных сигналов
 * - Понимание контекста ситуации
 */
class PerceptionSystem(
    private val npc: SmartNPC,
    private val eventBus: NPCEventBus
) {
    companion object {
        private val LOGGER = LogManager.getLogger(PerceptionSystem::class.java)
        
        // Константы восприятия
        private const val MAX_PERCEPTION_DISTANCE = 32.0
        private const val ATTENTION_DISTANCE = 16.0
        private const val PERSONAL_SPACE_DISTANCE = 3.0
        private const val FIELD_OF_VIEW_ANGLE = 120.0 // градусы
        private const val PERCEPTION_UPDATE_INTERVAL = 500L // мс
    }
    
    // Обнаруженные объекты
    private val perceivedPlayers = ConcurrentHashMap<UUID, PerceivedPlayer>()
    private val perceivedEntities = ConcurrentHashMap<UUID, PerceivedEntity>()
    private val recentEvents = mutableListOf<PerceptionEvent>()
    
    // Система внимания
    private var currentFocus: PerceptionTarget? = null
    private var lastPerceptionUpdate = 0L
    
    // Корутинная область для фоновых задач
    private val perceptionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var perceptionJob: Job? = null
    
    /**
     * Запустить систему восприятия
     */
    fun start() {
        perceptionJob = perceptionScope.launch {
            while (isActive) {
                try {
                    updatePerception()
                    delay(PERCEPTION_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    LOGGER.error("Error in perception update for NPC ${npc.name}", e)
                    delay(PERCEPTION_UPDATE_INTERVAL * 2)
                }
            }
        }
        
        LOGGER.debug("Perception system started for NPC ${npc.name}")
    }
    
    /**
     * Остановить систему восприятия
     */
    fun stop() {
        perceptionJob?.cancel()
        perceptionScope.cancel()
        LOGGER.debug("Perception system stopped for NPC ${npc.name}")
    }
    
    /**
     * Обновить данные восприятия
     */
    private suspend fun updatePerception() {
        val currentTime = System.currentTimeMillis()
        lastPerceptionUpdate = currentTime
        
        // Очищаем устаревшие данные
        cleanupOldPerceptions(currentTime)
        
        // Обновляем восприятие игроков
        updatePlayerPerception()
        
        // Обновляем восприятие сущностей
        updateEntityPerception()
        
        // Анализируем текущую ситуацию
        analyzeCurrentSituation()
        
        // Обновляем фокус внимания
        updateAttentionFocus()
    }
    
    /**
     * Обновить восприятие игроков
     */
    private fun updatePlayerPerception() {
        val nearbyPlayers = getNearbyPlayers()
        
        nearbyPlayers.forEach { player ->
            val playerId = player.uuid
            val existingPerception = perceivedPlayers[playerId]
            
            val updatedPerception = if (existingPerception != null) {
                updateExistingPlayerPerception(existingPerception, player)
            } else {
                createNewPlayerPerception(player)
            }
            
            perceivedPlayers[playerId] = updatedPerception
        }
    }
    
    /**
     * Создать новое восприятие игрока
     */
    private fun createNewPlayerPerception(player: Player): PerceivedPlayer {
        val distance = npc.position.distanceTo(player.position())
        val isInFieldOfView = isInFieldOfView(player.position())
        
        return PerceivedPlayer(
            playerId = player.uuid,
            playerName = player.gameProfile.name,
            position = player.position(),
            lastSeen = System.currentTimeMillis(),
            distance = distance,
            isVisible = isInFieldOfView,
            gazeData = GazeData(),
            behaviorAnalysis = BehaviorAnalysis(),
            socialSignals = SocialSignals(),
            firstEncounterTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Обновить существующее восприятие игрока
     */
    private fun updateExistingPlayerPerception(
        existing: PerceivedPlayer, 
        player: Player
    ): PerceivedPlayer {
        val currentTime = System.currentTimeMillis()
        val newDistance = npc.position.distanceTo(player.position())
        val isVisible = isInFieldOfView(player.position())
        
        // Обновляем данные о взгляде
        val updatedGazeData = updateGazeData(existing.gazeData, player)
        
        // Анализируем поведение
        val updatedBehavior = analyzeBehavior(existing, player)
        
        // Определяем социальные сигналы
        val updatedSocialSignals = analyzeSocialSignals(existing, player)
        
        return existing.copy(
            position = player.position(),
            lastSeen = currentTime,
            distance = newDistance,
            isVisible = isVisible,
            gazeData = updatedGazeData,
            behaviorAnalysis = updatedBehavior,
            socialSignals = updatedSocialSignals
        )
    }
    
    /**
     * Обновить данные о взгляде игрока
     */
    private fun updateGazeData(existing: GazeData, player: Player): GazeData {
        val isLookingAtNPC = isPlayerLookingAtNPC(player)
        val currentTime = System.currentTimeMillis()
        
        val gazeStartTime = if (isLookingAtNPC && !existing.isLookingAtNPC) {
            currentTime // Начало взгляда
        } else {
            existing.gazeStartTime
        }
        
        val gazeDuration = if (isLookingAtNPC && gazeStartTime != null) {
            currentTime - gazeStartTime
        } else {
            0L
        }
        
        // Обновляем историю взглядов
        val updatedGazeHistory = existing.gazeHistory.toMutableList()
        if (isLookingAtNPC != existing.isLookingAtNPC) {
            updatedGazeHistory.add(
                GazeEvent(
                    timestamp = currentTime,
                    startedLooking = isLookingAtNPC,
                    duration = if (!isLookingAtNPC) gazeDuration else 0L
                )
            )
            
            // Ограничиваем размер истории
            if (updatedGazeHistory.size > 20) {
                updatedGazeHistory.removeAt(0)
            }
        }
        
        return existing.copy(
            isLookingAtNPC = isLookingAtNPC,
            gazeStartTime = gazeStartTime,
            gazeDuration = gazeDuration,
            gazeHistory = updatedGazeHistory,
            lastGazeUpdate = currentTime
        )
    }
    
    /**
     * Проверить, смотрит ли игрок на НПС
     */
    private fun isPlayerLookingAtNPC(player: Player): Boolean {
        val playerPos = player.position()
        val npcPos = npc.position
        val distance = playerPos.distanceTo(npcPos)
        
        // Слишком далеко для "взгляда"
        if (distance > ATTENTION_DISTANCE) return false
        
        // Получаем направление взгляда игрока
        val yaw = Math.toRadians(player.yRot.toDouble())
        val pitch = Math.toRadians(player.xRot.toDouble())
        
        val lookDirection = Vec3(
            -sin(yaw) * cos(pitch),
            -sin(pitch),
            cos(yaw) * cos(pitch)
        ).normalize()
        
        // Направление от игрока к НПС
        val directionToNPC = npcPos.subtract(playerPos).normalize()
        
        // Вычисляем угол между направлением взгляда и направлением к НПС
        val dotProduct = lookDirection.dot(directionToNPC)
        val angle = Math.toDegrees(acos(dotProduct.coerceIn(-1.0, 1.0)))
        
        // Игрок смотрит на НПС если угол меньше 30 градусов
        return angle < 30.0
    }
    
    /**
     * Анализировать поведение игрока
     */
    private fun analyzeBehavior(existing: PerceivedPlayer, player: Player): BehaviorAnalysis {
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - existing.lastSeen
        
        // Анализ движения
        val movement = if (timeDelta > 0) {
            val previousPos = existing.position
            val currentPos = player.position()
            val moveDistance = previousPos.distanceTo(currentPos)
            val speed = (moveDistance / (timeDelta / 1000.0)).coerceAtMost(20.0) // Ограничиваем максимальную скорость
            
            MovementAnalysis(
                speed = speed,
                isMoving = speed > 0.1,
                isRunning = speed > 5.0,
                isSneaking = player.isCrouching,
                direction = if (moveDistance > 0.1) {
                    currentPos.subtract(previousPos).normalize()
                } else {
                    existing.behaviorAnalysis.movement.direction
                }
            )
        } else {
            existing.behaviorAnalysis.movement
        }
        
        // Анализ действий
        val actions = ActionAnalysis(
            heldItem = player.mainHandItem?.item?.toString() ?: "empty",
            isHoldingWeapon = isHoldingWeapon(player),
            isHoldingTool = isHoldingTool(player),
            recentActions = existing.behaviorAnalysis.actions.recentActions // TODO: обновлять из событий
        )
        
        // Анализ намерений
        val intent = analyzePlayerIntent(existing, player, movement, actions)
        
        return BehaviorAnalysis(
            movement = movement,
            actions = actions,
            inferredIntent = intent,
            lastAnalysisTime = currentTime
        )
    }
    
    /**
     * Определить намерение игрока
     */
    private fun analyzePlayerIntent(
        existing: PerceivedPlayer,
        player: Player,
        movement: MovementAnalysis,
        actions: ActionAnalysis
    ): PlayerIntent {
        val distance = existing.distance
        val isLooking = existing.gazeData.isLookingAtNPC
        val gazeDuration = existing.gazeData.gazeDuration
        
        return when {
            // Агрессивное намерение
            actions.isHoldingWeapon && distance < PERSONAL_SPACE_DISTANCE -> PlayerIntent.AGGRESSIVE
            
            // Желание торговать
            actions.heldItem.contains("emerald") || actions.heldItem.contains("gold") -> PlayerIntent.WANTS_TO_TRADE
            
            // Желание общаться
            isLooking && gazeDuration > 2000 && distance < ATTENTION_DISTANCE -> PlayerIntent.WANTS_TO_INTERACT
            
            // Приближается для разговора
            movement.isMoving && !movement.isRunning && isLooking -> PlayerIntent.APPROACHING_TO_TALK
            
            // Любопытство
            isLooking && distance > PERSONAL_SPACE_DISTANCE -> PlayerIntent.CURIOUS
            
            // Просто проходит мимо
            movement.isMoving && !isLooking -> PlayerIntent.PASSING_BY
            
            // Неопределенное поведение
            else -> PlayerIntent.NEUTRAL
        }
    }
    
    // Вспомогательные методы...
    
    private fun getNearbyPlayers(): List<Player> {
        return npc.level.getEntitiesOfClass(
            Player::class.java,
            npc.entity.boundingBox.inflate(MAX_PERCEPTION_DISTANCE)
        )
    }
    
    private fun getNearbyEntities(): List<LivingEntity> {
        return npc.level.getEntitiesOfClass(
            LivingEntity::class.java,
            npc.entity.boundingBox.inflate(MAX_PERCEPTION_DISTANCE)
        ).filter { it != npc.entity && it !is Player }
    }
    
    private fun addPerceptionEvent(
        eventType: PerceptionEventType,
        sourceId: UUID?,
        description: String,
        importance: Int
    ) {
        val event = PerceptionEvent(
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            sourceId = sourceId,
            description = description,
            importance = importance
        )
        
        recentEvents.add(event)
        
        // Ограничиваем размер истории событий
        if (recentEvents.size > 50) {
            recentEvents.removeAt(0)
        }
        
        // Отправляем событие через EventBus для ИИ
        try {
            publishPerceptionEvent(event)
        } catch (e: Exception) {
            LOGGER.warn("Failed to publish perception event for NPC ${npc.name}", e)
        }
    }
    
    private fun updateAISituationContext(
        threatLevel: ThreatLevel,
        socialSituation: SocialSituation,
        playersCount: Int
    ) {
        // Формируем контекст для ИИ
        val context = buildString {
            appendLine("Current situation analysis:")
            appendLine("- Threat level: $threatLevel")
            appendLine("- Social situation: $socialSituation")
            appendLine("- Players nearby: $playersCount")
            
            if (currentFocus != null) {
                appendLine("- Current focus: ${currentFocus!!.targetType} (${currentFocus!!.reason})")
            }
            
            val playersLooking = perceivedPlayers.values.filter { it.gazeData.isLookingAtNPC }
            if (playersLooking.isNotEmpty()) {
                appendLine("- Players looking at me: ${playersLooking.map { it.playerName }}")
            }
        }
        
        // TODO: Отправить контекст в ИИ систему
        LOGGER.debug("Situation context for NPC ${npc.name}: $context")
    }
    
    private fun calculatePlayerAttentionPriority(player: PerceivedPlayer): Int {
        var priority = 0
        
        // Базовый приоритет по намерению
        priority += when (player.behaviorAnalysis.inferredIntent) {
            PlayerIntent.AGGRESSIVE, PlayerIntent.THREATENING -> 10
            PlayerIntent.WANTS_TO_INTERACT -> 8
            PlayerIntent.WANTS_TO_TRADE -> 7
            PlayerIntent.APPROACHING_TO_TALK -> 6
            PlayerIntent.CURIOUS -> 4
            PlayerIntent.FRIENDLY -> 5
            PlayerIntent.STALKING -> 9
            PlayerIntent.AVOIDING -> 2
            PlayerIntent.PASSING_BY -> 1
            PlayerIntent.NEUTRAL -> 3
        }
        
        // Бонус за взгляд
        if (player.gazeData.isLookingAtNPC) {
            priority += 3
            if (player.gazeData.gazeDuration > 3000) {
                priority += 2 // Долгий взгляд = больше внимания
            }
        }
        
        // Бонус за близость
        when {
            player.distance < PERSONAL_SPACE_DISTANCE -> priority += 5
            player.distance < ATTENTION_DISTANCE -> priority += 3
            player.distance < MAX_PERCEPTION_DISTANCE / 2 -> priority += 1
        }
        
        // Штраф за время с последнего обновления
        val timeSinceUpdate = System.currentTimeMillis() - player.lastSeen
        if (timeSinceUpdate > 10000) { // 10 секунд
            priority -= 2
        }
        
        return priority.coerceAtLeast(0)
    }
    
    private fun getAttentionReason(player: PerceivedPlayer): String {
        return when {
            player.behaviorAnalysis.inferredIntent == PlayerIntent.AGGRESSIVE -> "Player showing aggressive behavior"
            player.behaviorAnalysis.inferredIntent == PlayerIntent.THREATENING -> "Player appears threatening"
            player.behaviorAnalysis.inferredIntent == PlayerIntent.WANTS_TO_INTERACT -> "Player wants to interact"
            player.gazeData.isLookingAtNPC && player.gazeData.gazeDuration > 3000 -> "Player staring for extended time"
            player.distance < PERSONAL_SPACE_DISTANCE -> "Player in personal space"
            player.socialSignals.attentionSeeking -> "Player seeking attention"
            else -> "General player observation"
        }
    }
    
    private fun isInFieldOfView(targetPos: Vec3): Boolean {
        val npcPos = npc.position
        val npcRotation = npc.entity.yRot
        
        val directionToTarget = targetPos.subtract(npcPos).normalize()
        val npcFacing = Vec3(
            -sin(Math.toRadians(npcRotation.toDouble())),
            0.0,
            cos(Math.toRadians(npcRotation.toDouble()))
        )
        
        val dotProduct = npcFacing.dot(directionToTarget)
        val angle = Math.toDegrees(acos(dotProduct.coerceIn(-1.0, 1.0)))
        
        return angle <= FIELD_OF_VIEW_ANGLE / 2
    }
    
    private fun isHoldingWeapon(player: Player): Boolean {
        val item = player.mainHandItem.item.toString()
        return item.contains("sword") || item.contains("axe") || item.contains("bow")
    }
    
    private fun isHoldingTool(player: Player): Boolean {
        val item = player.mainHandItem.item.toString()
        return item.contains("pickaxe") || item.contains("shovel") || item.contains("hoe")
    }
    
    private fun cleanupOldPerceptions(currentTime: Long) {
        val maxAge = 30000L // 30 секунд
        
        perceivedPlayers.entries.removeIf { (_, perception) ->
            currentTime - perception.lastSeen > maxAge
        }
        
        perceivedEntities.entries.removeIf { (_, perception) ->
            currentTime - perception.lastSeen > maxAge
        }
        
        recentEvents.removeIf { event ->
            currentTime - event.timestamp > maxAge
        }
    }
    
    private fun updateEntityPerception() {
        val nearbyEntities = getNearbyEntities()
        
        nearbyEntities.forEach { entity ->
            val entityId = entity.uuid
            val distance = npc.position.distanceTo(entity.position())
            val isVisible = isInFieldOfView(entity.position())
            
            val perceivedEntity = PerceivedEntity(
                entityId = entityId,
                entityType = entity.type.toString(),
                position = entity.position(),
                lastSeen = System.currentTimeMillis(),
                distance = distance,
                isHostile = entity.type.category.isFriendly.not(),
                isPassive = entity.type.category.isPeaceful
            )
            
            perceivedEntities[entityId] = perceivedEntity
            
            // Генерируем события если нужно
            if (distance < PERSONAL_SPACE_DISTANCE && perceivedEntity.isHostile) {
                addPerceptionEvent(
                    PerceptionEventType.HOSTILE_ACTION,
                    entityId,
                    "Hostile entity ${entity.type} entered personal space",
                    8
                )
            }
        }
    }
    
    private fun analyzeCurrentSituation() {
        val currentTime = System.currentTimeMillis()
        
        // Анализируем количество игроков поблизости
        val playersCount = perceivedPlayers.size
        val playersLooking = perceivedPlayers.values.count { it.gazeData.isLookingAtNPC }
        val hostilePlayers = perceivedPlayers.values.count { 
            it.behaviorAnalysis.inferredIntent in listOf(PlayerIntent.AGGRESSIVE, PlayerIntent.THREATENING)
        }
        
        // Оцениваем уровень опасности ситуации
        val threatLevel = when {
            hostilePlayers > 0 -> ThreatLevel.HIGH
            perceivedEntities.values.any { it.isHostile && it.distance < ATTENTION_DISTANCE } -> ThreatLevel.MEDIUM
            playersCount > 3 -> ThreatLevel.MEDIUM // Много людей = потенциальная опасность
            else -> ThreatLevel.LOW
        }
        
        // Определяем социальную ситуацию
        val socialSituation = when {
            playersLooking > 0 -> SocialSituation.BEING_OBSERVED
            playersCount == 1 && perceivedPlayers.values.first().distance < PERSONAL_SPACE_DISTANCE -> SocialSituation.INTIMATE_CONVERSATION
            playersCount > 1 -> SocialSituation.GROUP_INTERACTION
            else -> SocialSituation.ALONE
        }
        
        // Генерируем события если ситуация изменилась
        if (threatLevel == ThreatLevel.HIGH) {
            addPerceptionEvent(
                PerceptionEventType.HOSTILE_ACTION,
                null,
                "High threat situation detected: $hostilePlayers hostile players nearby",
                9
            )
        }
        
        // Обновляем контекст для ИИ
        updateAISituationContext(threatLevel, socialSituation, playersCount)
    }
    
    private fun updateAttentionFocus() {
        val currentTime = System.currentTimeMillis()
        
        // Собираем все потенциальные цели внимания
        val potentialTargets = mutableListOf<PerceptionTarget>()
        
        // Добавляем игроков как потенциальные цели
        perceivedPlayers.values.forEach { player ->
            val priority = calculatePlayerAttentionPriority(player)
            if (priority > 0) {
                potentialTargets.add(
                    PerceptionTarget(
                        targetId = player.playerId,
                        targetType = TargetType.PLAYER,
                        attentionStartTime = currentTime,
                        priority = priority,
                        reason = getAttentionReason(player)
                    )
                )
            }
        }
        
        // Добавляем враждебных сущностей
        perceivedEntities.values.filter { it.isHostile }.forEach { entity ->
            potentialTargets.add(
                PerceptionTarget(
                    targetId = entity.entityId,
                    targetType = TargetType.MONSTER,
                    attentionStartTime = currentTime,
                    priority = if (entity.distance < PERSONAL_SPACE_DISTANCE) 10 else 7,
                    reason = "Hostile entity nearby"
                )
            )
        }
        
        // Выбираем цель с наивысшим приоритетом
        val newFocus = potentialTargets.maxByOrNull { it.priority }
        
        // Обновляем фокус если нужно
        if (newFocus != null && 
            (currentFocus == null || newFocus.priority > currentFocus!!.priority || 
             currentTime - currentFocus!!.attentionStartTime > 30000)) { // 30 секунд максимум на одну цель
            
            val oldFocus = currentFocus
            currentFocus = newFocus
            
            // Уведомляем о смене фокуса
            if (oldFocus?.targetId != newFocus.targetId) {
                LOGGER.debug("NPC ${npc.name} attention shifted to ${newFocus.targetType} (priority: ${newFocus.priority})")
                
                // Генерируем событие для ИИ
                addPerceptionEvent(
                    PerceptionEventType.INTERACTION_ATTEMPT,
                    newFocus.targetId,
                    "Attention focused on ${newFocus.targetType}: ${newFocus.reason}",
                    newFocus.priority
                )
            }
        }
    }
    
    private fun analyzeSocialSignals(existing: PerceivedPlayer, player: Player): SocialSignals {
        val distance = existing.distance
        val isLooking = existing.gazeData.isLookingAtNPC
        val gazeDuration = existing.gazeData.gazeDuration
        val movement = existing.behaviorAnalysis.movement
        
        // Анализируем нарушение личного пространства
        val personalSpaceViolation = distance < PERSONAL_SPACE_DISTANCE && movement.isMoving
        
        // Анализируем дружественные жесты
        val friendlyGestures = existing.socialSignals.friendlyGestures + when {
            // Медленное приближение с взглядом = дружественное намерение
            isLooking && movement.isMoving && !movement.isRunning && distance > PERSONAL_SPACE_DISTANCE -> 1
            // Уважительная дистанция при разговоре
            isLooking && !movement.isMoving && distance in PERSONAL_SPACE_DISTANCE..ATTENTION_DISTANCE -> 1
            else -> 0
        }
        
        // Анализируем враждебные жесты
        val hostileGestures = existing.socialSignals.hostileGestures + when {
            // Быстрое приближение с оружием
            movement.isRunning && existing.behaviorAnalysis.actions.isHoldingWeapon -> 2
            // Нарушение личного пространства без взгляда
            personalSpaceViolation && !isLooking -> 1
            else -> 0
        }
        
        // Определяем попытку привлечь внимание
        val attentionSeeking = when {
            gazeDuration > 5000 -> true // Долгий взгляд
            existing.gazeData.gazeHistory.size > 3 -> true // Частые взгляды
            distance < ATTENTION_DISTANCE && movement.movementPattern == MovementPattern.CIRCLING -> true
            else -> false
        }
        
        // Оцениваем соблюдение уважительной дистанции
        val respectfulDistance = distance >= PERSONAL_SPACE_DISTANCE || 
            (distance < PERSONAL_SPACE_DISTANCE && existing.behaviorAnalysis.inferredIntent in listOf(
                PlayerIntent.WANTS_TO_INTERACT, PlayerIntent.WANTS_TO_TRADE, PlayerIntent.FRIENDLY
            ))
        
        // Анализируем язык тела
        val bodyLanguage = when {
            movement.isSneaking && existing.behaviorAnalysis.actions.isHoldingWeapon -> BodyLanguage.THREATENING
            movement.isSneaking && !existing.behaviorAnalysis.actions.isHoldingWeapon -> BodyLanguage.SUBMISSIVE
            existing.behaviorAnalysis.actions.isHoldingWeapon && distance < PERSONAL_SPACE_DISTANCE -> BodyLanguage.AGGRESSIVE
            movement.isRunning && distance > ATTENTION_DISTANCE -> BodyLanguage.NERVOUS
            isLooking && !movement.isMoving -> BodyLanguage.CONFIDENT
            gazeDuration > 3000 && distance < ATTENTION_DISTANCE -> BodyLanguage.FRIENDLY
            else -> BodyLanguage.NEUTRAL
        }
        
        return SocialSignals(
            personalSpaceViolation = personalSpaceViolation,
            friendlyGestures = friendlyGestures.coerceAtMost(10), // Ограничиваем максимальное значение
            hostileGestures = hostileGestures.coerceAtMost(10),
            attentionSeeking = attentionSeeking,
            respectfulDistance = respectfulDistance,
            bodyLanguage = bodyLanguage
        )
    }
    
    // Геттеры для доступа к данным восприятия
    
    fun getPerceivedPlayers(): Map<UUID, PerceivedPlayer> = perceivedPlayers.toMap()
    fun getPerceivedPlayer(playerId: UUID): PerceivedPlayer? = perceivedPlayers[playerId]
    fun getCurrentFocus(): PerceptionTarget? = currentFocus
    fun getPlayersLookingAtNPC(): List<PerceivedPlayer> {
        return perceivedPlayers.values.filter { it.gazeData.isLookingAtNPC }
    }
}

// Расширения для Vec3
private fun Vec3.distanceTo(other: Vec3): Double {
    val dx = this.x - other.x
    val dy = this.y - other.y
    val dz = this.z - other.z
    return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
}

private fun BlockPos.distanceTo(vec: Vec3): Double {
    return Vec3.atCenterOf(this).distanceTo(vec)
}

// Дополнительные енумы для системы восприятия
enum class ThreatLevel {
    LOW, MEDIUM, HIGH
}

enum class SocialSituation {
    ALONE,
    BEING_OBSERVED,
    INTIMATE_CONVERSATION,
    GROUP_INTERACTION,
    PUBLIC_GATHERING
}

/**
 * Расширение для публикации событий восприятия
 */
private fun PerceptionSystem.publishPerceptionEvent(event: PerceptionEvent) {
    val npcEvent = NPCEvents.customEvent(
        npcId = npc.id,
        npcName = npc.name,
        eventData = mapOf(
            "perceptionEventType" to event.eventType.name,
            "description" to event.description,
            "importance" to event.importance,
            "sourceId" to event.sourceId?.toString()
        ),
        position = npc.entity.blockPosition()
    )
    
    eventBus.sendEventSync(npcEvent)
}
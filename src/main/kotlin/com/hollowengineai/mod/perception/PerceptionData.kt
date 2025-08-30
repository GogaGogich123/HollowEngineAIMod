package com.hollowengineai.mod.perception

import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Данные восприятия игрока НПС'ом
 */
data class PerceivedPlayer(
    val playerId: UUID,
    val playerName: String,
    val position: Vec3,
    val lastSeen: Long,
    val distance: Double,
    val isVisible: Boolean,
    val gazeData: GazeData,
    val behaviorAnalysis: BehaviorAnalysis,
    val socialSignals: SocialSignals,
    val firstEncounterTime: Long,
    val relationshipLevel: RelationshipLevel = RelationshipLevel.STRANGER
)

/**
 * Данные о взгляде игрока
 */
data class GazeData(
    val isLookingAtNPC: Boolean = false,
    val gazeStartTime: Long? = null,
    val gazeDuration: Long = 0L,
    val gazeIntensity: Float = 0f, // 0.0 - 1.0
    val gazeHistory: List<GazeEvent> = emptyList(),
    val lastGazeUpdate: Long = System.currentTimeMillis()
)

/**
 * Событие взгляда
 */
data class GazeEvent(
    val timestamp: Long,
    val startedLooking: Boolean,
    val duration: Long = 0L
)

/**
 * Анализ поведения игрока
 */
data class BehaviorAnalysis(
    val movement: MovementAnalysis = MovementAnalysis(),
    val actions: ActionAnalysis = ActionAnalysis(),
    val inferredIntent: PlayerIntent = PlayerIntent.NEUTRAL,
    val lastAnalysisTime: Long = System.currentTimeMillis()
)

/**
 * Анализ движения
 */
data class MovementAnalysis(
    val speed: Double = 0.0,
    val isMoving: Boolean = false,
    val isRunning: Boolean = false,
    val isSneaking: Boolean = false,
    val direction: Vec3? = null,
    val movementPattern: MovementPattern = MovementPattern.RANDOM
)

/**
 * Анализ действий
 */
data class ActionAnalysis(
    val heldItem: String = "empty",
    val isHoldingWeapon: Boolean = false,
    val isHoldingTool: Boolean = false,
    val recentActions: List<String> = emptyList(),
    val interactionAttempts: Int = 0
)

/**
 * Социальные сигналы
 */
data class SocialSignals(
    val personalSpaceViolation: Boolean = false,
    val friendlyGestures: Int = 0,
    val hostileGestures: Int = 0,
    val attentionSeeking: Boolean = false,
    val respectfulDistance: Boolean = true,
    val bodyLanguage: BodyLanguage = BodyLanguage.NEUTRAL
)

/**
 * Восприятие других сущностей
 */
data class PerceivedEntity(
    val entityId: UUID,
    val entityType: String,
    val position: Vec3,
    val lastSeen: Long,
    val distance: Double,
    val isHostile: Boolean = false,
    val isPassive: Boolean = true
)

/**
 * Событие восприятия
 */
data class PerceptionEvent(
    val timestamp: Long,
    val eventType: PerceptionEventType,
    val sourceId: UUID?,
    val description: String,
    val importance: Int = 1 // 1-10
)

/**
 * Цель восприятия (на что НПС обращает внимание)
 */
data class PerceptionTarget(
    val targetId: UUID,
    val targetType: TargetType,
    val attentionStartTime: Long,
    val priority: Int,
    val reason: String
)

// Енумы

enum class PlayerIntent {
    NEUTRAL,
    CURIOUS,
    WANTS_TO_INTERACT,
    WANTS_TO_TRADE,
    APPROACHING_TO_TALK,
    AGGRESSIVE,
    THREATENING,
    FRIENDLY,
    PASSING_BY,
    AVOIDING,
    STALKING
}

enum class MovementPattern {
    RANDOM,
    DIRECT_APPROACH,
    CIRCLING,
    RETREATING,
    FOLLOWING,
    PATROLLING,
    STATIONARY
}

enum class BodyLanguage {
    NEUTRAL,
    AGGRESSIVE,
    DEFENSIVE,
    FRIENDLY,
    SUBMISSIVE,
    CONFIDENT,
    NERVOUS,
    THREATENING
}

enum class RelationshipLevel {
    STRANGER,
    ACQUAINTANCE,
    FRIEND,
    ENEMY,
    ALLY,
    NEUTRAL
}

enum class PerceptionEventType {
    PLAYER_ENTERED_RANGE,
    PLAYER_LEFT_RANGE,
    STARTED_LOOKING,
    STOPPED_LOOKING,
    WEAPON_DRAWN,
    WEAPON_SHEATHED,
    APPROACHED_CLOSE,
    MOVED_AWAY,
    INTERACTION_ATTEMPT,
    HOSTILE_ACTION,
    FRIENDLY_GESTURE
}

enum class TargetType {
    PLAYER,
    NPC,
    MONSTER,
    ANIMAL,
    OBJECT,
    AREA
}

/**
 * Конфигурация восприятия НПС
 */
data class PerceptionConfig(
    val maxPerceptionDistance: Double = 32.0,
    val attentionDistance: Double = 16.0,
    val personalSpaceDistance: Double = 3.0,
    val fieldOfViewAngle: Double = 120.0,
    val gazeDetectionSensitivity: Float = 0.8f,
    val updateInterval: Long = 500L,
    val memoryDuration: Long = 300000L, // 5 минут
    val enableGazeDetection: Boolean = true,
    val enableBehaviorAnalysis: Boolean = true,
    val enableSocialSignals: Boolean = true
)
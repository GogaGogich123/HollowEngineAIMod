package com.hollowengineai.mod.perception

import com.hollowengineai.mod.core.SmartNPC
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import kotlin.math.*

/**
 * Продвинутый детектор взгляда игрока на НПС
 * 
 * Возможности:
 * - Raycast для проверки прямой видимости
 * - Анализ точности направления взгляда
 * - Учет препятствий и блоков
 * - Различные режимы детекции
 * - Калибровка чувствительности
 * - История взглядов и паттерны
 */
class GazeDetector(
    private val npc: SmartNPC,
    private val config: GazeDetectionConfig = GazeDetectionConfig()
) {
    companion object {
        private val LOGGER = LogManager.getLogger(GazeDetector::class.java)
    }
    
    // Кэш результатов для оптимизации
    private val gazeCache = mutableMapOf<String, CachedGazeResult>()
    private var lastCacheCleanup = 0L
    
    /**
     * Основной метод определения взгляда игрока на НПС
     */
    fun isPlayerLookingAtNPC(
        player: Player,
        mode: GazeDetectionMode = config.defaultMode
    ): GazeResult {
        val playerPos = player.position()
        val npcPos = npc.position
        val distance = playerPos.distanceTo(npcPos)
        
        // Базовые проверки
        if (distance > config.maxDetectionDistance) {
            return GazeResult.notLooking("Too far away")
        }
        
        if (distance < config.minDetectionDistance) {
            return GazeResult.notLooking("Too close for accurate detection")
        }
        
        // Проверяем кэш
        val cacheKey = "${player.uuid}-${mode.name}"
        val cached = gazeCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < config.cacheValidityMs) {
            return cached.result
        }
        
        val result = when (mode) {
            GazeDetectionMode.SIMPLE -> simpleGazeDetection(player, playerPos, npcPos, distance)
            GazeDetectionMode.RAYCAST -> raycastGazeDetection(player, playerPos, npcPos, distance)
            GazeDetectionMode.PRECISE -> preciseGazeDetection(player, playerPos, npcPos, distance)
            GazeDetectionMode.CONTEXTUAL -> contextualGazeDetection(player, playerPos, npcPos, distance)
        }
        
        // Сохраняем в кэш
        gazeCache[cacheKey] = CachedGazeResult(result, System.currentTimeMillis())
        
        // Периодически очищаем кэш
        cleanupCacheIfNeeded()
        
        return result
    }
    
    /**
     * Простая детекция по углу взгляда
     */
    private fun simpleGazeDetection(
        player: Player,
        playerPos: Vec3,
        npcPos: Vec3,
        distance: Double
    ): GazeResult {
        val lookDirection = getPlayerLookDirection(player)
        val directionToNPC = npcPos.subtract(playerPos).normalize()
        
        val dotProduct = lookDirection.dot(directionToNPC)
        val angle = Math.toDegrees(acos(dotProduct.coerceIn(-1.0, 1.0)))
        
        val maxAngle = config.simpleDetectionAngle
        val isLooking = angle <= maxAngle
        
        val confidence = if (isLooking) {
            ((maxAngle - angle) / maxAngle).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        
        return GazeResult(
            isLooking = isLooking,
            confidence = confidence,
            angle = angle,
            distance = distance,
            method = GazeDetectionMode.SIMPLE,
            details = "Simple angle check: ${String.format("%.1f", angle)}° (max: ${maxAngle}°)"
        )
    }
    
    /**
     * Детекция с использованием raycast для проверки препятствий
     */
    private fun raycastGazeDetection(
        player: Player,
        playerPos: Vec3,
        npcPos: Vec3,
        distance: Double
    ): GazeResult {
        // Сначала проверяем угол
        val simpleResult = simpleGazeDetection(player, playerPos, npcPos, distance)
        if (!simpleResult.isLooking) {
            return simpleResult.copy(method = GazeDetectionMode.RAYCAST)
        }
        
        // Проверяем прямую видимость с помощью raycast
        val eyePosition = player.getEyePosition(1.0f)
        val npcEyePosition = npc.entity.getEyePosition(1.0f)
        
        val clipContext = ClipContext(
            eyePosition,
            npcEyePosition,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        )
        
        val hitResult = player.level.clip(clipContext)
        val hasDirectLineOfSight = hitResult.type == HitResult.Type.MISS
        
        if (!hasDirectLineOfSight) {
            return GazeResult.notLooking("Blocked by ${hitResult.type}")
        }
        
        // Если видимость есть, используем результат простой детекции с поправкой на raycast
        return simpleResult.copy(
            method = GazeDetectionMode.RAYCAST,
            confidence = simpleResult.confidence * 0.95, // Небольшое снижение уверенности из-за приблизительности raycast
            details = "${simpleResult.details} + Line of sight confirmed"
        )
    }
    
    /**
     * Точная детекция с учетом размеров НПС и точки взгляда
     */
    private fun preciseGazeDetection(
        player: Player,
        playerPos: Vec3,
        npcPos: Vec3,
        distance: Double
    ): GazeResult {
        val eyePosition = player.getEyePosition(1.0f)
        val lookDirection = getPlayerLookDirection(player)
        
        // Получаем bounding box НПС
        val npcBoundingBox = npc.entity.boundingBox
        val npcCenter = npcBoundingBox.center
        val npcHeight = npcBoundingBox.maxY - npcBoundingBox.minY
        val npcWidth = (npcBoundingBox.maxX - npcBoundingBox.minX + npcBoundingBox.maxZ - npcBoundingBox.minZ) / 2.0
        
        // Проверяем попадание луча взгляда в bounding box НПС
        val rayHitsNPC = checkRayIntersectsBox(eyePosition, lookDirection, npcBoundingBox)
        
        if (!rayHitsNPC) {
            // Вычисляем ближайшую точку на НПС и угол отклонения
            val closestPoint = getClosestPointOnNPC(eyePosition, npcBoundingBox)
            val directionToClosest = closestPoint.subtract(eyePosition).normalize()
            val angle = Math.toDegrees(acos(lookDirection.dot(directionToClosest).coerceIn(-1.0, 1.0)))
            
            return GazeResult.notLooking(
                "Ray misses NPC (closest angle: ${String.format("%.1f", angle)}°)"
            )
        }
        
        // Определяем точность попадания
        val directionToCenter = npcCenter.subtract(eyePosition).normalize()
        val centerAngle = Math.toDegrees(acos(lookDirection.dot(directionToCenter).coerceIn(-1.0, 1.0)))
        
        // Вычисляем уверенность на основе того, куда именно смотрит игрок
        val confidence = calculatePreciseConfidence(
            eyePosition, lookDirection, npcBoundingBox, distance, centerAngle
        )
        
        return GazeResult(
            isLooking = true,
            confidence = confidence,
            angle = centerAngle,
            distance = distance,
            method = GazeDetectionMode.PRECISE,
            details = "Precise raycast hit NPC bounds (center angle: ${String.format("%.1f", centerAngle)}°)"
        )
    }
    
    /**
     * Контекстная детекция с учетом поведения игрока
     */
    private fun contextualGazeDetection(
        player: Player,
        playerPos: Vec3,
        npcPos: Vec3,
        distance: Double
    ): GazeResult {
        // Начинаем с точной детекции
        val preciseResult = preciseGazeDetection(player, playerPos, npcPos, distance)
        
        // Анализируем контекст
        val context = analyzeGazeContext(player, distance)
        
        // Корректируем результат на основе контекста
        val adjustedConfidence = adjustConfidenceByContext(preciseResult.confidence, context)
        val adjustedThreshold = adjustThresholdByContext(config.minConfidenceThreshold, context)
        
        val isLooking = adjustedConfidence >= adjustedThreshold
        
        return GazeResult(
            isLooking = isLooking,
            confidence = adjustedConfidence,
            angle = preciseResult.angle,
            distance = distance,
            method = GazeDetectionMode.CONTEXTUAL,
            details = "${preciseResult.details} + Context: ${context.description}"
        )
    }
    
    /**
     * Анализ контекста для корректировки детекции
     */
    private fun analyzeGazeContext(player: Player, distance: Double): GazeContext {
        var score = 0.0
        val factors = mutableListOf<String>()
        
        // Фактор расстояния - чем ближе, тем более вероятно что смотрит
        val distanceFactor = when {
            distance < 2.0 -> { factors.add("very close"); 0.3 }
            distance < 4.0 -> { factors.add("close"); 0.2 }
            distance < 8.0 -> { factors.add("medium"); 0.1 }
            else -> { factors.add("far"); -0.1 }
        }
        score += distanceFactor
        
        // Фактор движения - если игрок не двигается, более вероятно что он смотрит
        val velocity = player.deltaMovement.length()
        val movementFactor = when {
            velocity < 0.01 -> { factors.add("stationary"); 0.2 }
            velocity < 0.1 -> { factors.add("slow movement"); 0.1 }
            velocity > 0.3 -> { factors.add("fast movement"); -0.1 }
            else -> 0.0
        }
        score += movementFactor
        
        // Фактор предмета в руках
        val heldItem = player.mainHandItem.item.toString()
        val itemFactor = when {
            heldItem.contains("sword") || heldItem.contains("axe") -> { 
                factors.add("weapon held"); -0.2 
            }
            heldItem.contains("emerald") || heldItem.contains("gold") -> { 
                factors.add("trade item"); 0.3 
            }
            heldItem == "minecraft:air" -> { 
                factors.add("empty hands"); 0.1 
            }
            else -> 0.0
        }
        score += itemFactor
        
        // Фактор sneaking - приседание может означать осторожный подход
        if (player.isCrouching) {
            factors.add("sneaking")
            score += 0.1
        }
        
        return GazeContext(
            score = score.coerceIn(-1.0, 1.0),
            description = factors.joinToString(", ")
        )
    }
    
    /**
     * Корректировка уверенности на основе контекста
     */
    private fun adjustConfidenceByContext(baseConfidence: Double, context: GazeContext): Double {
        val adjustment = context.score * 0.2 // Максимальная корректировка ±20%
        return (baseConfidence + adjustment).coerceIn(0.0, 1.0)
    }
    
    /**
     * Корректировка порога уверенности на основе контекста
     */
    private fun adjustThresholdByContext(baseThreshold: Double, context: GazeContext): Double {
        val adjustment = -context.score * 0.1 // Положительный контекст снижает порог
        return (baseThreshold + adjustment).coerceIn(0.1, 0.9)
    }
    
    // Вспомогательные методы
    
    private fun getPlayerLookDirection(player: Player): Vec3 {
        val yaw = Math.toRadians(player.yRot.toDouble())
        val pitch = Math.toRadians(player.xRot.toDouble())
        
        return Vec3(
            -sin(yaw) * cos(pitch),
            -sin(pitch),
            cos(yaw) * cos(pitch)
        ).normalize()
    }
    
    private fun checkRayIntersectsBox(
        rayStart: Vec3,
        rayDirection: Vec3,
        boundingBox: net.minecraft.world.phys.AABB
    ): Boolean {
        // Простая реализация ray-box intersection
        val dirfrac = Vec3(
            1.0 / rayDirection.x,
            1.0 / rayDirection.y,
            1.0 / rayDirection.z
        )
        
        val t1 = (boundingBox.minX - rayStart.x) * dirfrac.x
        val t2 = (boundingBox.maxX - rayStart.x) * dirfrac.x
        val t3 = (boundingBox.minY - rayStart.y) * dirfrac.y
        val t4 = (boundingBox.maxY - rayStart.y) * dirfrac.y
        val t5 = (boundingBox.minZ - rayStart.z) * dirfrac.z
        val t6 = (boundingBox.maxZ - rayStart.z) * dirfrac.z
        
        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))
        
        return tmax >= 0 && tmin <= tmax
    }
    
    private fun getClosestPointOnNPC(
        point: Vec3,
        boundingBox: net.minecraft.world.phys.AABB
    ): Vec3 {
        val x = point.x.coerceIn(boundingBox.minX, boundingBox.maxX)
        val y = point.y.coerceIn(boundingBox.minY, boundingBox.maxY)
        val z = point.z.coerceIn(boundingBox.minZ, boundingBox.maxZ)
        
        return Vec3(x, y, z)
    }
    
    private fun calculatePreciseConfidence(
        eyePosition: Vec3,
        lookDirection: Vec3,
        npcBoundingBox: net.minecraft.world.phys.AABB,
        distance: Double,
        centerAngle: Double
    ): Double {
        // Базовая уверенность на основе точности попадания в центр
        val angleConfidence = (1.0 - (centerAngle / 30.0)).coerceIn(0.0, 1.0)
        
        // Корректировка на расстояние - чем дальше, тем менее точно
        val distanceConfidence = when {
            distance < 2.0 -> 1.0
            distance < 5.0 -> 0.9
            distance < 10.0 -> 0.8
            distance < 20.0 -> 0.7
            else -> 0.6
        }
        
        // Размер НПС влияет на легкость попадания
        val npcSize = (npcBoundingBox.maxX - npcBoundingBox.minX) * 
                      (npcBoundingBox.maxY - npcBoundingBox.minY)
        val sizeBonus = min(0.1, npcSize / 10.0) // Максимальный бонус 10%
        
        return (angleConfidence * distanceConfidence + sizeBonus).coerceIn(0.0, 1.0)
    }
    
    private fun cleanupCacheIfNeeded() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCacheCleanup > config.cacheCleanupInterval) {
            gazeCache.entries.removeIf { 
                currentTime - it.value.timestamp > config.cacheValidityMs 
            }
            lastCacheCleanup = currentTime
        }
    }
    
    /**
     * Получить детальную информацию о последнем анализе взгляда
     */
    fun getLastGazeAnalysis(player: Player): String? {
        val cacheKey = "${player.uuid}-${config.defaultMode.name}"
        val cached = gazeCache[cacheKey]
        return cached?.result?.details
    }
    
    /**
     * Очистить кэш для определенного игрока
     */
    fun clearPlayerCache(player: Player) {
        gazeCache.keys.removeIf { it.startsWith(player.uuid.toString()) }
    }
    
    /**
     * Очистить весь кэш
     */
    fun clearCache() {
        gazeCache.clear()
    }
}

/**
 * Режимы детекции взгляда
 */
enum class GazeDetectionMode {
    SIMPLE,     // Простая проверка по углу
    RAYCAST,    // С проверкой препятствий
    PRECISE,    // Точная с учетом размеров НПС
    CONTEXTUAL  // С учетом контекста поведения
}

/**
 * Результат детекции взгляда
 */
data class GazeResult(
    val isLooking: Boolean,
    val confidence: Double,
    val angle: Double,
    val distance: Double,
    val method: GazeDetectionMode,
    val details: String
) {
    companion object {
        fun notLooking(reason: String) = GazeResult(
            isLooking = false,
            confidence = 0.0,
            angle = Double.MAX_VALUE,
            distance = Double.MAX_VALUE,
            method = GazeDetectionMode.SIMPLE,
            details = reason
        )
    }
}

/**
 * Контекст для анализа взгляда
 */
data class GazeContext(
    val score: Double, // -1.0 до 1.0
    val description: String
)

/**
 * Кэшированный результат детекции
 */
private data class CachedGazeResult(
    val result: GazeResult,
    val timestamp: Long
)

/**
 * Конфигурация детектора взгляда
 */
data class GazeDetectionConfig(
    val defaultMode: GazeDetectionMode = GazeDetectionMode.CONTEXTUAL,
    val maxDetectionDistance: Double = 20.0,
    val minDetectionDistance: Double = 0.5,
    val simpleDetectionAngle: Double = 30.0, // градусы
    val minConfidenceThreshold: Double = 0.6,
    val cacheValidityMs: Long = 100L, // 100ms кэш
    val cacheCleanupInterval: Long = 5000L // очистка каждые 5 секунд
)

// Расширения
private fun Vec3.distanceTo(other: Vec3): Double {
    val dx = this.x - other.x
    val dy = this.y - other.y
    val dz = this.z - other.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}
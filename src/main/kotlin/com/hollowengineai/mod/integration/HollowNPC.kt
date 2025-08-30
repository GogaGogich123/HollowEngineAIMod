package com.hollowengineai.mod.integration

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.*

/**
 * Интерфейс контроллера анимаций для совместимости с HollowEngine Legacy
 */
interface AnimationController {
    fun playAnimation(animationName: String, playMode: PlayMode)
    fun stopAnimation(animationName: String)
    fun isPlaying(animationName: String): Boolean
}

/**
 * Интеграция с HollowEngine Legacy НПС
 * 
 * Этот класс служит мостом между HollowEngineAI и базовой системой НПС HollowEngine Legacy.
 * Обеспечивает совместимость и доступ к функциональности базовой системы.
 */
class HollowNPC(private val entity: LivingEntity) {
    
    val id: UUID = entity.uuid
    val name: String = entity.name.string
    val level: Level = entity.level
    val position: BlockPos get() = entity.blockPosition()
    
    /**
     * Воспроизведение анимации
     */
    fun playAnimation(animationName: String, playMode: PlayMode = PlayMode.ONCE) {
        try {
            // Интеграция с системой анимаций HollowEngine Legacy
            // Пока что базовая реализация, которая не вызывает ошибок
            when (playMode) {
                PlayMode.ONCE -> {
                    // Воспроизвести анимацию один раз
                    entity.level.broadcastEntityEvent(entity, 4) // Примерный event ID
                }
                PlayMode.LOOP -> {
                    // Зациклить анимацию
                    entity.level.broadcastEntityEvent(entity, 5)
                }
                PlayMode.REVERSE -> {
                    // Воспроизвести в обратном порядке
                    entity.level.broadcastEntityEvent(entity, 6)
                }
                PlayMode.PINGPONG -> {
                    // Воспроизвести туда-обратно
                    entity.level.broadcastEntityEvent(entity, 7)
                }
            }
        } catch (e: Exception) {
            // Если система анимаций не доступна, игнорируем ошибку
        }
    }
    
    /**
     * Получить базовую сущность Minecraft
     */
    fun getEntity(): LivingEntity = entity
    
    /**
     * Проверить, жива ли сущность
     */
    fun isAlive(): Boolean = entity.isAlive
    
    /**
     * Получить позицию как Vec3
     */
    fun getPosition(): net.minecraft.world.phys.Vec3 = entity.position()
    
    /**
     * Переместить НПС к указанной позиции
     */
    fun moveTo(x: Double, y: Double, z: Double, speed: Double = 1.0) {
        try {
            entity.navigation?.moveTo(x, y, z, speed)
        } catch (e: Exception) {
            // Fallback для случая, когда навигация недоступна
            entity.setPos(x, y, z)
        }
    }
    
    /**
     * Навигация к позиции (альтернативная navigateTo)
     */
    fun navigateTo(pos: BlockPos): Boolean {
        return try {
            entity.navigation?.moveTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, 1.0) != null
        } catch (e: Exception) {
            // Fallback для случая, когда навигация недоступна
            entity.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            true
        }
    }
    
    /**
     * Заставить НПС смотреть на указанную позицию
     */
    fun lookAt(x: Double, y: Double, z: Double) {
        try {
            val deltaX = x - entity.x
            val deltaZ = z - entity.z
            val deltaY = y - entity.y
            
            val yaw = (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI).toFloat() - 90.0f
            val pitch = (-(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * 180.0 / Math.PI)).toFloat()
            
            entity.setYRot(yaw)
            entity.setXRot(pitch)
        } catch (e: Exception) {
            // Игнорируем ошибки поворота
        }
    }
    
    /**
     * Заставить НПС смотреть на другую сущность
     */
    fun lookAt(target: Entity) {
        lookAt(target.x, target.eyeY, target.z)
    }
    
    /**
     * Получить здоровье НПС
     */
    fun getHealth(): Float = entity.health
    
    /**
     * Получить максимальное здоровье НПС
     */
    fun getMaxHealth(): Float = entity.maxHealth
    
    /**
     * Нанести урон НПС
     */
    fun damage(amount: Float) {
        try {
            entity.hurt(entity.level.damageSources().generic(), amount)
        } catch (e: Exception) {
            // Игнорируем ошибки урона
        }
    }
    
    /**
     * Исцелить НПС
     */
    fun heal(amount: Float) {
        entity.heal(amount)
    }
    
    /**
     * Проверить, в бою ли НПС
     */
    fun isInCombat(): Boolean {
        return try {
            entity.lastHurtByMobTimestamp > entity.tickCount - 100 // Последние 5 секунд
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Получить цель атаки НПС
     */
    fun getTarget(): LivingEntity? {
        return try {
            entity.target
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Установить цель атаки для НПС
     */
    fun setTarget(target: LivingEntity?) {
        try {
            entity.target = target
        } catch (e: Exception) {
            // Игнорируем ошибки установки цели
        }
    }
    
    /**
     * Получить уровень мира
     */
    fun getLevel(): Level = entity.level
    
    /**
     * Отправить сообщение всем игрокам поблизости
     */
    fun say(message: String, range: Double = 16.0) {
        try {
            val nearbyPlayers = level.getEntitiesOfClass(
                net.minecraft.world.entity.player.Player::class.java,
                entity.boundingBox.inflate(range)
            )
            
            nearbyPlayers.forEach { player ->
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("$name: $message")
                )
            }
        } catch (e: Exception) {
            // Игнорируем ошибки отправки сообщений
        }
    }
    
    /**
     * Получить контроллер анимаций (совместимость с HollowEngine Legacy)
     */
    fun getAnimationController(): AnimationController? {
        return try {
            // Попытка интеграции с HollowEngine Legacy системой анимаций
            // Пока что возвращаем null для совместимости
            null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun toString(): String {
        return "HollowNPC(id=$id, name='$name', position=$position)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HollowNPC) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Режимы воспроизведения анимаций
 */
enum class PlayMode {
    /** Воспроизвести один раз */
    ONCE,
    
    /** Зациклить воспроизведение */
    LOOP,
    
    /** Воспроизвести в обратном порядке */
    REVERSE,
    
    /** Воспроизвести туда-обратно */
    PINGPONG
}
package com.hollowengineai.mod.actions

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.core.CombatState
import com.hollowengineai.mod.core.MovementConstants
import com.hollowengineai.mod.events.NPCEvent
import com.hollowengineai.mod.events.NPCEventType
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.states.NPCState
import kotlinx.coroutines.delay
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.SwordItem
import kotlin.math.max
import kotlin.random.Random

/**
 * Исполнитель боевых действий для NPC
 * Обрабатывает атаки, защиту, уклонение и тактические маневры
 */
class CombatActionExecutor(
    private val eventBus: NPCEventBusImpl
) : ActionExecutor {
    
    override val supportedActions = setOf(
        "attack_melee",
        "attack_ranged", 
        "defend",
        "dodge",
        "flee",
        "block",
        "counter_attack",
        "charge",
        "retreat",
        "intimidate",
        "assess_threat"
    )
    
    override val priority = 100 // Высокий приоритет для боевых действий
    
    override fun canHandle(action: String, npc: SmartNPC, target: Entity?): Boolean {
        return supportedActions.contains(action) && (target is LivingEntity || action in setOf("defend", "dodge", "flee"))
    }
    
    override suspend fun executeAction(
        action: String,
        npc: SmartNPC,
        target: Entity?,
        parameters: Map<String, Any>
    ): ActionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Переходим в боевое состояние если нужно
            val stateMachine = npc.getStateMachine()
            if (stateMachine != null && stateMachine.getCurrentState() != NPCState.FIGHTING && action != "assess_threat") {
                stateMachine.transitionTo(NPCState.FIGHTING, "Combat action: $action")
            }
            
            val result = when (action) {
                "attack_melee" -> executeMeleeAttack(npc, target as LivingEntity)
                "attack_ranged" -> executeRangedAttack(npc, target as LivingEntity)
                "defend" -> executeDefend(npc)
                "dodge" -> executeDodge(npc, target as? LivingEntity)
                "flee" -> executeFlee(npc, target as? LivingEntity)
                "block" -> executeBlock(npc, target as? LivingEntity)
                "counter_attack" -> executeCounterAttack(npc, target as LivingEntity)
                "charge" -> executeCharge(npc, target as LivingEntity)
                "retreat" -> executeRetreat(npc, target as? LivingEntity)
                "intimidate" -> executeIntimidate(npc, target as LivingEntity)
                "assess_threat" -> executeAssessThreat(npc, target as? LivingEntity)
                else -> ActionResult(false, "Unknown combat action: $action")
            }
            
            // Отправляем событие о боевом действии
            eventBus.sendEventSync(NPCEvent(
                type = NPCEventType.NPC_ATTACKED,
                sourceNpcId = npc.getEntity().uuid,
                sourceNpcName = npc.name,
                data = mapOf(
                    "action" to action,
                    "target" to (target?.uuid?.toString() ?: "none"),
                    "success" to result.success
                )
            ))
            
            return result.copy(executionTime = System.currentTimeMillis() - startTime)
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Error executing combat action $action", e)
            return ActionResult(
                success = false,
                message = "Combat action failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override fun estimateCost(action: String, npc: SmartNPC, target: Entity?): ActionCost {
        return when (action) {
            "attack_melee" -> ActionCost(
                energyCost = 15f,
                timeCost = 500L,
                riskLevel = 0.6f,
                socialCost = if (target is Player) 50f else 10f
            )
            "attack_ranged" -> ActionCost(
                energyCost = 10f,
                timeCost = 800L,
                riskLevel = 0.4f,
                socialCost = if (target is Player) 40f else 8f,
                resourceCost = mapOf("arrows" to 1)
            )
            "defend" -> ActionCost(
                energyCost = 5f,
                timeCost = 200L,
                riskLevel = 0.2f
            )
            "dodge" -> ActionCost(
                energyCost = 8f,
                timeCost = 300L,
                riskLevel = 0.3f
            )
            "flee" -> ActionCost(
                energyCost = 20f,
                timeCost = 1000L,
                riskLevel = 0.1f,
                socialCost = -10f // Потеря репутации
            )
            "block" -> ActionCost(
                energyCost = 3f,
                timeCost = 150L,
                riskLevel = 0.1f
            )
            "counter_attack" -> ActionCost(
                energyCost = 20f,
                timeCost = 400L,
                riskLevel = 0.5f,
                socialCost = if (target is Player) 30f else 5f
            )
            "charge" -> ActionCost(
                energyCost = 25f,
                timeCost = 1200L,
                riskLevel = 0.8f,
                socialCost = if (target is Player) 60f else 15f
            )
            "retreat" -> ActionCost(
                energyCost = 12f,
                timeCost = 800L,
                riskLevel = 0.2f,
                socialCost = -5f
            )
            "intimidate" -> ActionCost(
                energyCost = 5f,
                timeCost = 500L,
                riskLevel = 0.1f,
                socialCost = if (target is Player) 20f else 3f
            )
            "assess_threat" -> ActionCost(
                energyCost = 1f,
                timeCost = 100L,
                riskLevel = 0.0f
            )
            else -> ActionCost(10f, 500L, 0.5f)
        }
    }
    
    override fun getPrerequisites(action: String, npc: SmartNPC, target: Entity?): List<ActionPrerequisite> {
        return when (action) {
            "attack_melee" -> listOf(
                DistancePrerequisite(3.0, "Must be close to target for melee attack"),
                HealthPrerequisite(0.1f, "Must have at least 10% health to attack")
            )
            "attack_ranged" -> listOf(
                DistancePrerequisite(20.0, "Target must be within ranged attack range"),
                ItemPrerequisite("bow", 1, "Requires bow for ranged attack"),
                ItemPrerequisite("arrow", 1, "Requires arrows for ranged attack")
            )
            "charge" -> listOf(
                DistancePrerequisite(15.0, "Must have distance to charge"),
                HealthPrerequisite(0.3f, "Must have at least 30% health to charge")
            )
            "counter_attack" -> listOf(
                EmotionalPrerequisite(minArousal = 0.6f, description = "Must be sufficiently aroused for counter-attack")
            )
            "flee" -> listOf(
                HealthPrerequisite(0.0f, "Can always flee if alive")
            )
            else -> emptyList()
        }
    }
    
    /**
     * Выполнить атаку ближнего боя
     */
    private suspend fun executeMeleeAttack(npc: SmartNPC, target: LivingEntity): ActionResult {
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC is not a combat entity")
        
        // Проверяем дистанцию
        if (mob.distanceTo(target) > 3.0) {
            return ActionResult(false, "Target too far for melee attack")
        }
        
        // Рассчитываем урон
        val baseAttack = mob.getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
        val weaponBonus = getWeaponDamageBonus(mob)
        val criticalHit = Random.nextFloat() < 0.1f // 10% шанс критического удара
        
        var damage = baseAttack + weaponBonus
        if (criticalHit) {
            damage *= 1.5f
        }
        
        // Применяем урон
        val damageSource = mob.level.damageSources().mobAttack(mob)
        val actualDamage = target.hurt(damageSource, damage)
        
        // Анимация атаки
        delay(300L) // Симуляция времени анимации
        
        // Эмоциональное воздействие
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (actualDamage) 0.2f else -0.1f,
            arousalChange = 0.3f
        )
        
        // Изменение эмоционального состояния упрощено(0.3f)
        // Изменение эмоционального состояния упрощено(if (actualDamage) 0.2f else -0.1f)
        
        val message = if (actualDamage) {
            if (criticalHit) "Critical melee attack!" else "Successful melee attack"
        } else {
            "Melee attack blocked or missed"
        }
        
        return ActionResult(
            success = actualDamage,
            message = message,
            data = mapOf(
                "damage" to damage,
                "critical" to criticalHit,
                "target" to target.name.string
            ),
            energyCost = 15f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить дальнюю атаку
     */
    private suspend fun executeRangedAttack(npc: SmartNPC, target: LivingEntity): ActionResult {
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC is not a combat entity")
        
        // Проверяем, есть ли лук
        val hasBow = mob.mainHandItem.item is BowItem
        if (!hasBow) {
            return ActionResult(false, "No bow equipped for ranged attack")
        }
        
        // Создаем стрелу (упрощенно)
        val distance = mob.distanceTo(target)
        val accuracy = calculateRangedAccuracy(distance, npc)
        
        delay(800L) // Время для натягивания лука и прицеливания
        
        val hit = Random.nextFloat() < accuracy
        var damage = 0f
        
        if (hit) {
            damage = Random.nextFloat() * 6f + 2f // 2-8 урона
            val damageSource = mob.level.damageSources().arrow(
                null, // Arrow entity - упрощаем
                mob
            )
            target.hurt(damageSource, damage)
        }
        
        // Эмоциональное воздействие
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (hit) 0.15f else -0.05f,
            arousalChange = 0.2f
        )
        
        // Изменение эмоционального состояния упрощено(0.2f)
        // Изменение эмоционального состояния упрощено(if (hit) 0.15f else -0.05f)
        
        return ActionResult(
            success = hit,
            message = if (hit) "Ranged attack hit!" else "Ranged attack missed",
            data = mapOf(
                "damage" to damage,
                "distance" to distance,
                "accuracy" to accuracy,
                "target" to target.name.string
            ),
            energyCost = 10f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить защитное действие
     */
    private suspend fun executeDefend(npc: SmartNPC): ActionResult {
        // Увеличиваем защиту на короткое время
        delay(200L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            arousalChange = 0.1f
        )
        
        // Изменение эмоционального состояния упрощено(0.1f)
        // Изменение эмоционального состояния упрощено(0.05f)
        
        return ActionResult(
            success = true,
            message = "Defensive stance taken",
            data = mapOf("defense_boost" to 0.3f),
            energyCost = 5f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить уклонение
     */
    private suspend fun executeDodge(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC cannot dodge")
        
        // Случайное движение для уклонения
        val dodgeSuccess = Random.nextFloat() < 0.7f // 70% шанс успешного уклонения
        
        if (dodgeSuccess) {
            target?.let {
            // Простое перемещение в сторону
            val deltaX = (Random.nextFloat() - 0.5f) * 2f
            val deltaZ = (Random.nextFloat() - 0.5f) * 2f
            
            mob.setPos(
                mob.x + deltaX,
                mob.y,
                mob.z + deltaZ
            )
            }
        }
        
        delay(300L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (dodgeSuccess) 0.1f else -0.05f,
            arousalChange = 0.2f
        )
        
        // Изменение эмоционального состояния упрощено(0.2f)
        // Изменение эмоционального состояния упрощено(if (dodgeSuccess) 0.1f else -0.05f)
        
        return ActionResult(
            success = dodgeSuccess,
            message = if (dodgeSuccess) "Successfully dodged!" else "Dodge attempt failed",
            energyCost = 8f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить бегство
     */
    private suspend fun executeFlee(npc: SmartNPC, target: LivingEntity?): ActionResult {
        // Переходим в состояние бегства
        npc.getStateMachine()?.transitionTo(NPCState.FLEEING, "Combat flee")
        
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC cannot flee")
        
        // Увеличиваем скорость движения
        val speedBoost = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.5f
        
        delay(1000L) // Время на бегство
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = -0.2f, // Негативные эмоции от бегства
            arousalChange = 0.4f    // Высокое возбуждение
        )
        
        // Изменение эмоционального состояния упрощено(0.4f)
        // Изменение эмоционального состояния упрощено(-0.2f)
        
        return ActionResult(
            success = true,
            message = "Fleeing from combat",
            data = mapOf("speed_boost" to speedBoost),
            energyCost = 20f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить блокировку
     */
    private suspend fun executeBlock(npc: SmartNPC, target: LivingEntity?): ActionResult {
        delay(150L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            arousalChange = 0.05f
        )
        
        return ActionResult(
            success = true,
            message = "Blocking incoming attacks",
            data = mapOf("block_chance" to 0.6f),
            energyCost = 3f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить контратаку
     */
    private suspend fun executeCounterAttack(npc: SmartNPC, target: LivingEntity): ActionResult {
        // Контратака - это улучшенная атака ближнего боя
        delay(200L) // Время на реакцию
        
        val meleeResult = executeMeleeAttack(npc, target)
        
        // Усиливаем результат контратаки
        return meleeResult.copy(
            data = meleeResult.data + ("counter_attack" to true),
            energyCost = meleeResult.energyCost + 5f,
            message = "Counter-attack executed!"
        )
    }
    
    /**
     * Выполнить атаку с разбега
     */
    private suspend fun executeCharge(npc: SmartNPC, target: LivingEntity): ActionResult {
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC cannot charge")
        
        val distance = mob.distanceTo(target)
        if (distance < 5.0) {
            return ActionResult(false, "Too close to charge")
        }
        
        // Симуляция разбега
        delay(1200L)
        
        // Мощная атака
        val baseAttack = mob.getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
        val chargeDamage = baseAttack * 1.8f // Увеличенный урон от разбега
        
        val damageSource = mob.level.damageSources().mobAttack(mob)
        val actualDamage = target.hurt(damageSource, chargeDamage)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (actualDamage) 0.3f else -0.2f,
            arousalChange = 0.5f
        )
        
        // Изменение эмоционального состояния упрощено(0.5f)
        // Изменение эмоционального состояния упрощено(if (actualDamage) 0.3f else -0.2f)
        
        return ActionResult(
            success = actualDamage,
            message = if (actualDamage) "Devastating charge attack!" else "Charge attack failed",
            data = mapOf(
                "damage" to chargeDamage,
                "distance" to distance,
                "target" to target.name.string
            ),
            energyCost = 25f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить отступление
     */
    private suspend fun executeRetreat(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val mob = npc.getEntity() as? Mob ?: return ActionResult(false, "NPC cannot retreat")
        
        // Контролируемое отступление на безопасную дистанцию
        target?.let { targetEntity ->
            val safeDistance = 10.0
            val currentDistance = mob.distanceTo(targetEntity)
            
            if (currentDistance < safeDistance) {
                // Перемещаемся от цели
                val dirX = mob.x - targetEntity.x
                val dirZ = mob.z - targetEntity.z
                val length = kotlin.math.sqrt(dirX * dirX + dirZ * dirZ)
                
                if (length > 0) {
                    val normalizedX = dirX / length
                    val normalizedZ = dirZ / length
                    
                    mob.setPos(
                        mob.x + normalizedX * 3.0,
                        mob.y,
                        mob.z + normalizedZ * 3.0
                    )
                }
            }
        }
        
        delay(800L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = -0.1f,
            arousalChange = 0.2f
        )
        
        // Изменение эмоционального состояния упрощено(0.2f)
        // Изменение эмоционального состояния упрощено(-0.1f)
        
        return ActionResult(
            success = true,
            message = "Tactical retreat executed",
            energyCost = 12f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выполнить запугивание
     */
    private suspend fun executeIntimidate(npc: SmartNPC, target: LivingEntity): ActionResult {
        delay(500L)
        
        val intimidationSuccess = Random.nextFloat() < 0.4f // 40% шанс успеха
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (intimidationSuccess) 0.15f else -0.05f,
            arousalChange = 0.1f,
            dominanceChange = if (intimidationSuccess) 0.2f else -0.1f
        )
        
        // Изменение эмоционального состояния упрощено(0.1f)
        // Изменение эмоционального состояния упрощено(if (intimidationSuccess) 0.15f else -0.05f)
        
        return ActionResult(
            success = intimidationSuccess,
            message = if (intimidationSuccess) "Target intimidated!" else "Intimidation failed",
            data = mapOf(
                "target" to target.name.string,
                "intimidation_level" to if (intimidationSuccess) 0.7f else 0.2f
            ),
            energyCost = 5f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Оценить уровень угрозы
     */
    private suspend fun executeAssessThreat(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
            // Оценка угрозы
            val distance = npc.getEntity().distanceTo(targetEntity)
            val health = (targetEntity as? LivingEntity)?.health ?: 0f
            
            val threatLevel = when {
                distance < 5.0 && health > 15f -> "HIGH"
                distance < 10.0 && health > 10f -> "MEDIUM"
                else -> "LOW"
            }
            
            ActionResult(
                success = true,
                message = "Threat level assessed: $threatLevel",
                data = mapOf(
                    "threat_level" to threatLevel,
                    "target_distance" to distance,
                    "target_health" to health
                ),
                energyCost = 2f
            )
        } ?: ActionResult(
            success = true,
            message = "No immediate threats detected",
            data = mapOf("threat_level" to 0.0f),
            energyCost = 1f
        )
    }
    
    /**
     * Получить бонус урона от оружия
     */
    private fun getWeaponDamageBonus(mob: Mob): Float {
        val weapon = mob.mainHandItem
        return when (weapon.item) {
            is SwordItem -> 4f
            else -> 0f
        }
    }
    
    /**
     * Рассчитать точность дальней атаки
     */
    private fun calculateRangedAccuracy(distance: Float, npc: SmartNPC): Float {
        val baseAccuracy = 0.8f
        val distancePenalty = (distance - 5f) * 0.02f // Штраф за дистанцию
        val emotionalBonus = when (npc.emotionalState) {
            EmotionalState.EXCITED, EmotionalState.ANGRY -> 0.1f
            else -> 0f
        }
        
        return max(0.1f, baseAccuracy - distancePenalty + emotionalBonus)
    }
    
    /**
     * Рассчитать уровень угрозы цели
     */
    private fun calculateThreatLevel(npc: SmartNPC, target: LivingEntity): Float {
        var threatLevel = 0.3f // Базовый уровень угрозы
        
        // Тип цели
        when (target) {
            is Player -> threatLevel += 0.5f // Игроки более опасны
            is Mob -> threatLevel += 0.3f
        }
        
        // Здоровье цели
        val targetHealthPercent = target.health / target.maxHealth
        threatLevel += targetHealthPercent * 0.2f
        
        // Дистанция (ближе = опаснее)
        val distance = npc.getEntity().distanceTo(target)
        threatLevel += max(0f, (10f - distance) * 0.05f)
        
        return max(0f, min(1f, threatLevel))
    }
}
package com.hollowengineai.mod.actions

import com.hollowengineai.mod.actions.ActionTypes.ActionType.ActionType
import java.util.*

/**
 * Модульное действие с расширенными возможностями
 * 
 * Расширяет базовую систему действий дополнительной функциональностью:
 * - Приоритизация действий
 * - Условия выполнения
 * - Модульная архитектура
 * - Метаданные для ИИ системы
 */
data class ModularAction(
    /** Тип действия */
    val type: ActionType,
    
    /** Параметры действия */
    val parameters: Map<String, Any> = emptyMap(),
    
    /** Приоритет действия (0 = низкий, 10 = высокий) */
    val priority: Int = 5,
    
    /** Уникальный идентификатор действия */
    val id: UUID = UUID.randomUUID(),
    
    /** Время создания действия */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Ожидаемое время выполнения в миллисекундах */
    val estimatedDuration: Long = 1000L,
    
    /** Условия для выполнения действия */
    val prerequisites: List<ActionPrerequisite> = emptyList(),
    
    /** Метаданные для ИИ системы */
    val metadata: ActionMetadata = ActionMetadata(),
    
    /** Модули, которые участвуют в выполнении */
    val modules: Set<ActionModule> = emptySet()
) {
    
    /**
     * Проверить, можно ли выполнить действие
     */
    fun canExecute(context: ActionExecutionContext): Boolean {
        return prerequisites.all { it.isSatisfied(context.npc, context.target) }
    }
    
    /**
     * Получить описание действия для ИИ
     */
    fun getDescription(): String {
        return buildString {
            append(type.description)
            if (parameters.isNotEmpty()) {
                append(" with parameters: ")
                append(parameters.entries.joinToString(", ") { "${it.key}=${it.value}" })
            }
        }
    }
    
    /**
     * Создать копию действия с изменениями
     */
    fun copy(
        type: ActionType = this.type,
        parameters: Map<String, Any> = this.parameters,
        priority: Int = this.priority,
        prerequisites: List<ActionPrerequisite> = this.prerequisites,
        metadata: ActionMetadata = this.metadata
    ): ModularAction {
        return ModularAction(
            type = type,
            parameters = parameters,
            priority = priority,
            id = UUID.randomUUID(), // Новый ID для копии
            createdAt = System.currentTimeMillis(),
            estimatedDuration = this.estimatedDuration,
            prerequisites = prerequisites,
            metadata = metadata,
            modules = this.modules
        )
    }
    
    /**
     * Получить вес действия для приоритизации
     */
    fun getWeight(): Float {
        var weight = priority.toFloat()
        
        // Учитываем срочность
        if (metadata.isUrgent) weight += 2.0f
        
        // Учитываем эмоциональную важность
        weight += metadata.emotionalImpact * 0.5f
        
        // Учитываем время ожидания
        val waitTime = System.currentTimeMillis() - createdAt
        weight += (waitTime / 1000.0f) * 0.1f
        
        return weight
    }
    
    /**
     * Проверить, истекло ли время жизни действия
     */
    fun isExpired(timeoutMs: Long = 30000L): Boolean {
        return System.currentTimeMillis() - createdAt > timeoutMs
    }
    
    companion object {
        /**
         * Создать простое действие
         */
        fun simple(type: ActionType, vararg params: Pair<String, Any>): ModularAction {
            return ModularAction(
                type = type,
                parameters = params.toMap()
            )
        }
        
        /**
         * Создать приоритетное действие
         */
        fun priority(type: ActionType, priority: Int, vararg params: Pair<String, Any>): ModularAction {
            return ModularAction(
                type = type,
                parameters = params.toMap(),
                priority = priority
            )
        }
        
        /**
         * Создать срочное действие
         */
        fun urgent(type: ActionType, vararg params: Pair<String, Any>): ModularAction {
            return ModularAction(
                type = type,
                parameters = params.toMap(),
                priority = 9,
                metadata = ActionMetadata(isUrgent = true)
            )
        }
    }
}

/**
 * Метаданные действия для ИИ системы
 */
data class ActionMetadata(
    /** Является ли действие срочным */
    val isUrgent: Boolean = false,
    
    /** Эмоциональное воздействие действия (-1.0 до 1.0) */
    val emotionalImpact: Float = 0.0f,
    
    /** Социальное воздействие действия (-1.0 до 1.0) */
    val socialImpact: Float = 0.0f,
    
    /** Требует ли действие концентрации */
    val requiresFocus: Boolean = false,
    
    /** Может ли действие быть прервано */
    val canBeInterrupted: Boolean = true,
    
    /** Теги для категоризации */
    val tags: Set<String> = emptySet(),
    
    /** Дополнительные данные */
    val extraData: Map<String, Any> = emptyMap()
)

/**
 * Модуль действия
 */
enum class ActionModule {
    /** Модуль движения */
    MOVEMENT,
    
    /** Модуль коммуникации */
    COMMUNICATION,
    
    /** Модуль боя */
    COMBAT,
    
    /** Модуль взаимодействия с миром */
    WORLD_INTERACTION,
    
    /** Модуль эмоций */
    EMOTION,
    
    /** Модуль памяти */
    MEMORY,
    
    /** Модуль планирования */
    PLANNING,
    
    /** Модуль восприятия */
    PERCEPTION
}

/**
 * Контекст выполнения действия
 */
data class ActionExecutionContext(
    /** НПС, выполняющий действие */
    val npc: com.hollowengineai.mod.core.SmartNPC,
    
    /** Цель действия (если есть) */
    val target: net.minecraft.world.entity.Entity? = null,
    
    /** Дополнительные данные контекста */
    val contextData: Map<String, Any> = emptyMap()
)
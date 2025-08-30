package com.hollowengineai.mod.factory

import com.hollowengineai.mod.core.*
import com.hollowengineai.mod.memory.NPCMemory
import com.hollowengineai.mod.actions.OptimizedActionExecutor
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import org.apache.logging.log4j.LogManager
import java.util.*

/**
 * Фабрика для создания AI NPC
 * Централизованное создание с валидацией и настройкой
 */
class NPCFactory(
    private val decisionEngine: DecisionEngine,
    private val actionExecutor: OptimizedActionExecutor
) {
    companion object {
        private val LOGGER = LogManager.getLogger(NPCFactory::class.java)
    }
    
    /**
     * Создать Smart NPC с базовыми настройками
     */
    fun createSmartNPC(
        entity: LivingEntity,
        name: String,
        personalityType: PersonalityType,
        customTraits: PersonalityTraits? = null
    ): SmartNPC {
        val npcId = UUID.randomUUID()
        
        // Создаем память для NPC
        val memory = NPCMemory(npcId)
        
        // Применяем кастомные черты или используем базовые
        val finalTraits = customTraits ?: personalityType.traits
        val finalPersonalityType = if (customTraits != null) {
            personalityType.copy(traits = finalTraits)
        } else {
            personalityType
        }
        
        val smartNPC = SmartNPC(
            id = npcId,
            name = name,
            personalityType = finalPersonalityType,
            entity = entity,
            memory = memory,
            actionExecutor = actionExecutor,
            decisionEngine = decisionEngine
        )
        
        // Инициализируем базовые знания
        initializeBasicKnowledge(memory, personalityType)
        
        LOGGER.info("Created Smart NPC: $name ($npcId) with personality: ${personalityType.name}")
        
        return smartNPC
    }
    
    /**
     * Создать Smart NPC с расширенной конфигурацией
     */
    fun createAdvancedSmartNPC(config: NPCCreationConfig): SmartNPC {
        val smartNPC = createSmartNPC(
            entity = config.entity,
            name = config.name,
            personalityType = config.personalityType,
            customTraits = config.customTraits
        )
        
        // Применяем дополнительные настройки
        config.initialGoal?.let { goal ->
            smartNPC.setInitialGoal(goal)
        }
        
        config.initialEmotion?.let { emotion ->
            smartNPC.setEmotion(emotion)
        }
        
        config.initialKnowledge?.forEach { (topic, info) ->
            smartNPC.memory.addKnowledge(topic, info, 0.8f)
        }
        
        config.schedule?.let { schedule ->
            // TODO: Интеграция с системой расписания
        }
        
        config.socialGroup?.let { group ->
            // TODO: Добавить в социальную группу
        }
        
        return smartNPC
    }
    
    /**
     * Создать группу связанных NPC
     */
    fun createNPCGroup(groupConfig: NPCGroupConfig): List<SmartNPC> {
        val npcs = mutableListOf<SmartNPC>()
        
        groupConfig.members.forEach { memberConfig ->
            val npc = createAdvancedSmartNPC(memberConfig)
            npcs.add(npc)
        }
        
        // Устанавливаем связи между членами группы
        if (groupConfig.establishRelationships) {
            establishGroupRelationships(npcs, groupConfig.groupType)
        }
        
        LOGGER.info("Created NPC group '${groupConfig.name}' with ${npcs.size} members")
        
        return npcs
    }
    
    /**
     * Инициализировать базовые знания для NPC
     */
    private fun initializeBasicKnowledge(memory: NPCMemory, personalityType: PersonalityType) {
        when (personalityType) {
            PersonalityType.MERCHANT -> {
                memory.addKnowledge("trading", "Skilled in commerce and negotiations", 0.9f)
                memory.addKnowledge("economy", "Understands market dynamics", 0.7f)
                memory.addKnowledge("social", "Good at reading people", 0.6f)
            }
            PersonalityType.GUARD -> {
                memory.addKnowledge("combat", "Trained in combat techniques", 0.9f)
                memory.addKnowledge("security", "Knows how to protect areas", 0.8f)
                memory.addKnowledge("law", "Understands rules and order", 0.7f)
            }
            PersonalityType.SCHOLAR -> {
                memory.addKnowledge("research", "Skilled in gathering information", 0.9f)
                memory.addKnowledge("books", "Loves reading and learning", 0.8f)
                memory.addKnowledge("history", "Knows about past events", 0.7f)
            }
            PersonalityType.EXPLORER -> {
                memory.addKnowledge("navigation", "Good at finding paths", 0.8f)
                memory.addKnowledge("survival", "Knows how to survive outdoors", 0.7f)
                memory.addKnowledge("adventure", "Seeks new experiences", 0.9f)
            }
            PersonalityType.ARTISAN -> {
                memory.addKnowledge("crafting", "Skilled at making things", 0.9f)
                memory.addKnowledge("materials", "Knows about different materials", 0.8f)
                memory.addKnowledge("tools", "Expert with various tools", 0.7f)
            }
            else -> {
                memory.addKnowledge("general", "Basic world knowledge", 0.5f)
            }
        }
    }
    
    /**
     * Установить отношения между членами группы
     */
    private fun establishGroupRelationships(npcs: List<SmartNPC>, groupType: SocialGroupType) {
        // Базовое отношение в зависимости от типа группы
        val baseRelationship = when (groupType) {
            SocialGroupType.FAMILY -> 0.8f
            SocialGroupType.GUILD -> 0.6f
            SocialGroupType.CLAN -> 0.7f
            SocialGroupType.TRADING_COMPANY -> 0.5f
            SocialGroupType.MILITARY_UNIT -> 0.6f
            SocialGroupType.RELIGIOUS_ORDER -> 0.7f
            SocialGroupType.RESEARCH_TEAM -> 0.5f
            SocialGroupType.CUSTOM -> 0.4f
        }
        
        // Устанавливаем отношения между всеми парами
        for (i in npcs.indices) {
            for (j in i + 1 until npcs.size) {
                val npc1 = npcs[i]
                val npc2 = npcs[j]
                
                // TODO: Интеграция с системой отношений
                // npc1.relationships.setRelationship(npc2.id, baseRelationship)
                // npc2.relationships.setRelationship(npc1.id, baseRelationship)
            }
        }
    }
    
    /**
     * Валидировать конфигурацию NPC
     */
    fun validateConfig(config: NPCCreationConfig): List<String> {
        val errors = mutableListOf<String>()
        
        if (config.name.isBlank()) {
            errors.add("NPC name cannot be blank")
        }
        
        if (config.name.length > 50) {
            errors.add("NPC name too long (max 50 characters)")
        }
        
        config.customTraits?.let { traits ->
            if (!traits.isValid()) {
                errors.add("Invalid personality traits (values must be between 0.0 and 1.0)")
            }
        }
        
        return errors
    }
}

/**
 * Конфигурация для создания NPC
 */
data class NPCCreationConfig(
    val entity: LivingEntity,
    val name: String,
    val personalityType: PersonalityType,
    val customTraits: PersonalityTraits? = null,
    val initialGoal: String? = null,
    val initialEmotion: EmotionalState? = null,
    val initialKnowledge: Map<String, String>? = null,
    val schedule: NPCSchedule? = null,
    val socialGroup: String? = null
)

/**
 * Конфигурация группы NPC
 */
data class NPCGroupConfig(
    val name: String,
    val groupType: SocialGroupType,
    val members: List<NPCCreationConfig>,
    val establishRelationships: Boolean = true,
    val groupGoals: List<String> = emptyList()
)

/**
 * Типы социальных групп
 */
enum class SocialGroupType {
    FAMILY,
    GUILD, 
    CLAN,
    TRADING_COMPANY,
    MILITARY_UNIT,
    RELIGIOUS_ORDER,
    RESEARCH_TEAM,
    CUSTOM
}

/**
 * Расширение для PersonalityTraits с валидацией
 */
fun PersonalityTraits.isValid(): Boolean {
    return friendliness in 0.0f..1.0f &&
           curiosity in 0.0f..1.0f &&
           aggressiveness in 0.0f..1.0f &&
           intelligence in 0.0f..1.0f &&
           creativity in 0.0f..1.0f &&
           patience in 0.0f..1.0f &&
           boldness in 0.0f..1.0f &&
           empathy in 0.0f..1.0f
}

/**
 * Расширение для SmartNPC
 */
fun SmartNPC.setInitialGoal(goal: String) {
    // TODO: Реализовать установку начальной цели
}

// TODO: Определить NPCSchedule когда будет создана система расписания
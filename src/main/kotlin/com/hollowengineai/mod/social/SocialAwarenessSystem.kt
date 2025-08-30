package com.hollowengineai.mod.social

import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEvents
import kotlinx.coroutines.*
import net.minecraft.world.entity.player.Player
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SocialAwarenessSystem(
    private val npc: SmartNPC,
    private val eventBus: NPCEventBus,
    private val config: SocialConfig = SocialConfig()
) {
    private val logger = LoggerFactory.getLogger(SocialAwarenessSystem::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Основные данные
    private val relationships = ConcurrentHashMap<UUID, SocialRelationship>()
    private val socialGroups = ConcurrentHashMap<UUID, SocialGroup>()
    private val socialRules = ConcurrentHashMap<UUID, SocialRule>()
    private val socialMemories = ConcurrentHashMap<UUID, MutableList<SocialMemory>>()
    
    // Кэш и статистика
    private val relationshipCache = ConcurrentHashMap<UUID, RelationshipType>()
    private val socialAdviceCache = ConcurrentHashMap<UUID, List<String>>()
    private var totalInteractions = 0
    private var ruleViolations = 0
    
    // Активность системы
    private var isActive = false
    private var lastUpdate = LocalDateTime.now()
    
    fun start() {
        if (isActive) {
            logger.warn("Social awareness system already active for NPC ${npc.id}")
            return
        }
        
        isActive = true
        logger.info("Starting social awareness system for NPC ${npc.id}")
        
        // Инициализируем базовые социальные правила
        initializeDefaultRules()
        
        // Запускаем периодические обновления
        startPeriodicUpdates()
        
        logger.info("Social awareness system started for NPC ${npc.id}")
    }
    
    fun stop() {
        if (!isActive) return
        
        isActive = false
        coroutineScope.cancel()
        logger.info("Social awareness system stopped for NPC ${npc.id}")
    }
    
    // === ВЗАИМОДЕЙСТВИЕ С ИГРОКАМИ ===
    
    fun recordInteraction(player: Player, interactionType: InteractionType, context: String = "", 
                         importance: InteractionImportance = InteractionImportance.NORMAL,
                         witnesses: Set<UUID> = emptySet(),
                         customChanges: Map<String, Double> = emptyMap()) {
        
        val playerId = player.uuid
        val relationship = getOrCreateRelationship(playerId, player.scoreboardName)
        
        // Создаем взаимодействие с изменениями
        val interaction = SocialInteraction(
            playerId = playerId,
            interactionType = interactionType,
            context = context,
            trustChange = customChanges["trust"] ?: (interactionType.defaultTrustChange * importance.multiplier),
            respectChange = customChanges["respect"] ?: (interactionType.defaultRespectChange * importance.multiplier),
            friendshipChange = customChanges["friendship"] ?: (interactionType.defaultFriendshipChange * importance.multiplier),
            fearChange = customChanges["fear"] ?: (interactionType.defaultFearChange * importance.multiplier),
            detectedTraits = analyzeTraitsFromInteraction(interactionType, context),
            importance = importance,
            witnesses = witnesses
        )
        
        // Обновляем отношения
        relationship.updateRelationship(interaction)
        totalInteractions++
        
        // Очищаем кэш
        relationshipCache.remove(playerId)
        socialAdviceCache.remove(playerId)
        
        // Создаем память об этом взаимодействии
        if (importance != InteractionImportance.TRIVIAL) {
            addMemory(playerId, 
                "Взаимодействие: ${interactionType.displayName} - $context",
                MemoryType.SIGNIFICANT_EVENT,
                calculateEmotionalWeight(interaction)
            )
        }
        
        // Уведомляем свидетелей
        notifyWitnesses(interaction, witnesses)
        
        // Отправляем событие
        publishSocialEvent("interaction_recorded", mapOf(
            "playerId" to playerId.toString(),
            "interactionType" to interactionType.name,
            "newRelationship" to relationship.getOverallRelation().name,
            "importance" to importance.name
        ))
        
        logger.debug("Recorded interaction ${interactionType.name} with player ${player.scoreboardName}")
    }
    
    fun getRelationship(playerId: UUID): SocialRelationship? {
        return relationships[playerId]
    }
    
    fun getRelationshipType(playerId: UUID): RelationshipType {
        return relationshipCache.computeIfAbsent(playerId) {
            relationships[playerId]?.getOverallRelation() ?: RelationshipType.NEUTRAL
        }
    }
    
    fun getAllRelationships(): Map<UUID, SocialRelationship> {
        return relationships.toMap()
    }
    
    // === СОЦИАЛЬНЫЕ ГРУППЫ ===
    
    fun createGroup(name: String, groupType: GroupType, initialMembers: Set<UUID> = emptySet()): UUID {
        val group = SocialGroup(
            name = name,
            groupType = groupType,
            members = initialMembers.toMutableSet()
        )
        
        socialGroups[group.id] = group
        
        publishSocialEvent("group_created", mapOf(
            "groupId" to group.id.toString(),
            "groupName" to name,
            "groupType" to groupType.name,
            "memberCount" to initialMembers.size.toString()
        ))
        
        logger.info("Created social group: $name (${groupType.displayName})")
        return group.id
    }
    
    fun addToGroup(groupId: UUID, playerId: UUID): Boolean {
        val group = socialGroups[groupId] ?: return false
        
        group.addMember(playerId)
        
        publishSocialEvent("group_member_added", mapOf(
            "groupId" to groupId.toString(),
            "playerId" to playerId.toString(),
            "groupName" to group.name
        ))
        
        return true
    }
    
    fun removeFromGroup(groupId: UUID, playerId: UUID): Boolean {
        val group = socialGroups[groupId] ?: return false
        
        group.removeMember(playerId)
        
        publishSocialEvent("group_member_removed", mapOf(
            "groupId" to groupId.toString(),
            "playerId" to playerId.toString(),
            "groupName" to group.name
        ))
        
        return true
    }
    
    fun getPlayerGroups(playerId: UUID): List<SocialGroup> {
        return socialGroups.values.filter { playerId in it.members }
    }
    
    fun areInSameGroup(playerId1: UUID, playerId2: UUID): Boolean {
        return socialGroups.values.any { 
            playerId1 in it.members && playerId2 in it.members 
        }
    }
    
    // === СОЦИАЛЬНЫЕ ПРАВИЛА ===
    
    fun addRule(name: String, description: String, ruleType: RuleType, 
               severity: RuleSeverity, affectedGroups: Set<UUID> = emptySet()): UUID {
        val rule = SocialRule(
            name = name,
            description = description,
            ruleType = ruleType,
            severity = severity,
            affectedGroups = affectedGroups
        )
        
        socialRules[rule.id] = rule
        
        logger.info("Added social rule: $name (${severity.displayName})")
        return rule.id
    }
    
    fun violateRule(ruleId: UUID, violatorId: UUID, violatorName: String, 
                   context: String, witnesses: Set<UUID> = emptySet()): Boolean {
        val rule = socialRules[ruleId] ?: return false
        
        val violation = RuleViolation(
            violatorId = violatorId,
            violatorName = violatorName,
            ruleId = ruleId,
            context = context,
            witnesses = witnesses
        )
        
        rule.violations.add(violation)
        ruleViolations++
        
        // Применяем штрафы к отношениям
        applyRulePenalty(violatorId, rule, violation)
        
        // Создаем память о нарушении
        addMemory(violatorId,
            "Нарушил правило: ${rule.name} - $context",
            MemoryType.SIGNIFICANT_EVENT,
            -rule.severity.penaltyMultiplier * 2
        )
        
        publishSocialEvent("rule_violated", mapOf(
            "ruleId" to ruleId.toString(),
            "violatorId" to violatorId.toString(),
            "ruleName" to rule.name,
            "severity" to rule.severity.name,
            "context" to context
        ))
        
        logger.warn("Rule violation: ${rule.name} by $violatorName - $context")
        return true
    }
    
    fun forgivevViolation(ruleId: UUID, violationIndex: Int): Boolean {
        val rule = socialRules[ruleId] ?: return false
        if (violationIndex < 0 || violationIndex >= rule.violations.size) return false
        
        val violation = rule.violations[violationIndex]
        violation.forgiven = true
        
        // Частично восстанавливаем отношения
        val relationship = relationships[violation.violatorId]
        if (relationship != null) {
            val restoreAmount = rule.severity.penaltyMultiplier * 0.5
            relationship.trust = minOf(100.0, relationship.trust + restoreAmount * 5)
            relationship.respect = minOf(100.0, relationship.respect + restoreAmount * 3)
            relationship.friendship = minOf(100.0, relationship.friendship + restoreAmount * 4)
        }
        
        addMemory(violation.violatorId,
            "Простил нарушение: ${rule.name}",
            MemoryType.EMOTIONAL_MOMENT,
            rule.severity.penaltyMultiplier
        )
        
        logger.info("Forgave rule violation: ${rule.name} by ${violation.violatorName}")
        return true
    }
    
    // === СОЦИАЛЬНАЯ ПАМЯТЬ ===
    
    fun addMemory(playerId: UUID, memory: String, memoryType: MemoryType, 
                 emotionalWeight: Double, relatedPlayers: Set<UUID> = emptySet(),
                 location: String? = null) {
        val memories = socialMemories.computeIfAbsent(playerId) { mutableListOf() }
        
        val socialMemory = SocialMemory(
            playerId = playerId,
            memory = memory,
            memoryType = memoryType,
            emotionalWeight = emotionalWeight,
            relatedPlayers = relatedPlayers,
            location = location
        )
        
        memories.add(socialMemory)
        
        // Ограничиваем количество воспоминаний
        if (memories.size > config.maxMemoriesPerPlayer) {
            // Удаляем наименее эмоционально значимые воспоминания
            memories.sortBy { kotlin.math.abs(it.emotionalWeight) }
            memories.removeAt(0)
        }
        
        logger.debug("Added memory for player $playerId: $memory")
    }
    
    fun getMemories(playerId: UUID, memoryType: MemoryType? = null): List<SocialMemory> {
        val memories = socialMemories[playerId] ?: return emptyList()
        return if (memoryType != null) {
            memories.filter { it.memoryType == memoryType }
        } else {
            memories.toList()
        }
    }
    
    fun recallMemory(playerId: UUID, memoryIndex: Int): SocialMemory? {
        val memories = socialMemories[playerId] ?: return null
        if (memoryIndex < 0 || memoryIndex >= memories.size) return null
        
        val memory = memories[memoryIndex]
        memory.recall()
        
        return memory
    }
    
    // === АНАЛИЗ И РЕКОМЕНДАЦИИ ===
    
    fun getSocialAdvice(playerId: UUID): List<String> {
        return socialAdviceCache.computeIfAbsent(playerId) {
            val relationship = relationships[playerId] ?: return@computeIfAbsent emptyList()
            SocialUtils.generateSocialAdvice(relationship)
        }
    }
    
    fun analyzeSocialSituation(playerId: UUID): SocialSituationAnalysis {
        val relationship = getRelationship(playerId)
        val groups = getPlayerGroups(playerId)
        val memories = getMemories(playerId)
        val recentInteractions = relationship?.interactionHistory?.takeLast(5) ?: emptyList()
        
        return SocialSituationAnalysis(
            playerId = playerId,
            relationshipType = getRelationshipType(playerId),
            relationship = relationship,
            groups = groups,
            recentMemories = memories.sortedByDescending { it.timestamp }.take(10),
            recentInteractions = recentInteractions,
            socialAdvice = getSocialAdvice(playerId),
            overallSentiment = calculateOverallSentiment(relationship, memories),
            trustLevel = relationship?.trust ?: 0.0,
            compatibilityScore = calculateCompatibility(playerId)
        )
    }
    
    fun getCompatibilityScore(playerId1: UUID, playerId2: UUID): Double {
        val rel1 = relationships[playerId1]
        val rel2 = relationships[playerId2]
        
        if (rel1 == null || rel2 == null) return 0.0
        
        return SocialUtils.getCompatibilityScore(rel1.traits, rel2.traits)
    }
    
    // === ПРИВАТНЫЕ МЕТОДЫ ===
    
    private fun getOrCreateRelationship(playerId: UUID, playerName: String): SocialRelationship {
        return relationships.computeIfAbsent(playerId) {
            val newRelationship = SocialRelationship(playerId, playerName)
            
            // Первая встреча
            addMemory(playerId,
                "Первая встреча с $playerName",
                MemoryType.FIRST_IMPRESSION,
                2.0
            )
            
            logger.info("Created new relationship with player: $playerName")
            newRelationship
        }
    }
    
    private fun initializeDefaultRules() {
        // Базовые моральные правила
        addRule("Не нападать без причины", "Запрет на неспровоцированное нападение", 
               RuleType.MORAL, RuleSeverity.SEVERE)
        
        addRule("Держать обещания", "Обязательство выполнять данные обещания",
               RuleType.MORAL, RuleSeverity.MODERATE)
        
        addRule("Не красть", "Запрет на воровство предметов",
               RuleType.MORAL, RuleSeverity.SERIOUS)
        
        addRule("Быть вежливым", "Требование вежливого общения",
               RuleType.SOCIAL, RuleSeverity.MINOR)
        
        addRule("Помогать друзьям", "Обязательство помогать друзьям в беде",
               RuleType.SOCIAL, RuleSeverity.MODERATE)
        
        addRule("Честная торговля", "Требование справедливых цен и качественных товаров",
               RuleType.TRADE, RuleSeverity.MODERATE)
               
        logger.info("Initialized ${socialRules.size} default social rules")
    }
    
    private fun analyzeTraitsFromInteraction(interactionType: InteractionType, context: String): Set<SocialTrait> {
        val traits = mutableSetOf<SocialTrait>()
        
        when (interactionType) {
            InteractionType.GIFT_RECEIVED -> traits.add(SocialTrait.GENEROUS)
            InteractionType.HELP_RECEIVED -> traits.addAll(setOf(SocialTrait.HELPFUL, SocialTrait.GENEROUS))
            InteractionType.BETRAYAL -> traits.addAll(setOf(SocialTrait.TREACHEROUS, SocialTrait.UNRELIABLE))
            InteractionType.ATTACK -> traits.addAll(setOf(SocialTrait.AGGRESSIVE, SocialTrait.UNRELIABLE))
            InteractionType.DEFENSE -> traits.addAll(setOf(SocialTrait.BRAVE, SocialTrait.PROTECTIVE))
            InteractionType.PROMISE_KEPT -> traits.addAll(setOf(SocialTrait.HONEST, SocialTrait.RELIABLE))
            InteractionType.PROMISE_BROKEN -> traits.addAll(setOf(SocialTrait.LIAR, SocialTrait.UNRELIABLE))
            InteractionType.INSULT -> traits.addAll(setOf(SocialTrait.RUDE, SocialTrait.AGGRESSIVE))
            InteractionType.COMPLIMENT -> traits.addAll(setOf(SocialTrait.RESPECTFUL, SocialTrait.HELPFUL))
            InteractionType.PROTECTED -> traits.addAll(setOf(SocialTrait.BRAVE, SocialTrait.PROTECTIVE, SocialTrait.LOYAL))
            InteractionType.ABANDONED -> traits.addAll(setOf(SocialTrait.COWARD, SocialTrait.SELFISH, SocialTrait.UNRELIABLE))
            else -> {}
        }
        
        return traits
    }
    
    private fun calculateEmotionalWeight(interaction: SocialInteraction): Double {
        val baseWeight = (interaction.trustChange + interaction.respectChange + 
                         interaction.friendshipChange - interaction.fearChange) / 4.0
        return baseWeight * interaction.importance.multiplier
    }
    
    private fun notifyWitnesses(interaction: SocialInteraction, witnesses: Set<UUID>) {
        for (witnessId in witnesses) {
            val witnessRel = relationships[witnessId] ?: continue
            
            // Свидетели получают небольшое изменение отношения
            val witnessInteraction = SocialInteraction(
                playerId = witnessId,
                interactionType = if (interaction.trustChange + interaction.friendshipChange > 0) 
                    InteractionType.WITNESSED_KINDNESS else InteractionType.WITNESSED_CRIME,
                context = "Стал свидетелем: ${interaction.interactionType.displayName}",
                trustChange = interaction.trustChange * 0.2,
                respectChange = interaction.respectChange * 0.2,
                friendshipChange = interaction.friendshipChange * 0.2,
                fearChange = interaction.fearChange * 0.3,
                importance = InteractionImportance.NORMAL
            )
            
            witnessRel.updateRelationship(witnessInteraction)
            
            addMemory(witnessId,
                "Видел как ${interaction.playerId} ${interaction.interactionType.displayName}",
                MemoryType.OBSERVATION,
                calculateEmotionalWeight(witnessInteraction)
            )
        }
    }
    
    private fun applyRulePenalty(violatorId: UUID, rule: SocialRule, violation: RuleViolation) {
        val relationship = getOrCreateRelationship(violatorId, violation.violatorName)
        
        val penalty = rule.severity.penaltyMultiplier
        relationship.trust = maxOf(-100.0, relationship.trust - penalty * 10)
        relationship.respect = maxOf(-100.0, relationship.respect - penalty * 6)
        relationship.friendship = maxOf(-100.0, relationship.friendship - penalty * 8)
        relationship.fear = minOf(100.0, relationship.fear + penalty * 5)
        
        // Добавляем негативные черты
        when (rule.ruleType) {
            RuleType.MORAL -> relationship.traits.addAll(setOf(SocialTrait.UNRELIABLE, SocialTrait.TREACHEROUS))
            RuleType.LEGAL -> relationship.traits.add(SocialTrait.UNRELIABLE)
            RuleType.SOCIAL -> relationship.traits.add(SocialTrait.RUDE)
            else -> {}
        }
        
        relationshipCache.remove(violatorId)
        socialAdviceCache.remove(violatorId)
    }
    
    private fun calculateOverallSentiment(relationship: SocialRelationship?, memories: List<SocialMemory>): Double {
        if (relationship == null) return 0.0
        
        val relationshipSentiment = (relationship.trust + relationship.respect + 
                                   relationship.friendship - relationship.fear) / 4.0
        
        val memorySentiment = if (memories.isNotEmpty()) {
            memories.map { it.emotionalWeight }.average()
        } else 0.0
        
        return (relationshipSentiment + memorySentiment * 10) / 2.0
    }
    
    private fun calculateCompatibility(playerId: UUID): Double {
        val relationship = relationships[playerId] ?: return 0.0
        
        // Совместимость на основе черт характера НПС и игрока
        val npcTraits = setOf(SocialTrait.HONEST, SocialTrait.HELPFUL, SocialTrait.RELIABLE) // Пример
        return SocialUtils.getCompatibilityScore(npcTraits, relationship.traits)
    }
    
    private fun startPeriodicUpdates() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    updateSystem()
                    delay(config.updateIntervalMs)
                } catch (e: Exception) {
                    logger.error("Error in social awareness system update", e)
                }
            }
        }
    }
    
    private suspend fun updateSystem() {
        val currentTime = LocalDateTime.now()
        
        // Применяем затухание отношений со временем
        for (relationship in relationships.values) {
            val decay = SocialUtils.calculateRelationshipDecay(relationship.lastInteraction, currentTime)
            if (decay > 0) {
                relationship.trust *= (1.0 - decay * 0.1)
                relationship.friendship *= (1.0 - decay * 0.15)
                relationship.respect *= (1.0 - decay * 0.05)
                relationship.fear *= (1.0 - decay * 0.2)
            }
        }
        
        // Очищаем кэши
        if (currentTime.minusMinutes(5).isAfter(lastUpdate)) {
            relationshipCache.clear()
            socialAdviceCache.clear()
            lastUpdate = currentTime
        }
        
        // Очищаем старые воспоминания
        cleanupOldMemories(currentTime)
    }
    
    private fun cleanupOldMemories(currentTime: LocalDateTime) {
        for ((playerId, memories) in socialMemories) {
            memories.removeIf { memory ->
                val daysSince = java.time.Duration.between(memory.timestamp, currentTime).toDays()
                val shouldRemove = when (memory.memoryType) {
                    MemoryType.OBSERVATION -> daysSince > 30
                    MemoryType.CONVERSATION -> daysSince > 60
                    MemoryType.SIGNIFICANT_EVENT -> daysSince > 180
                    MemoryType.FIRST_IMPRESSION -> false // Никогда не удаляем
                    MemoryType.EMOTIONAL_MOMENT -> daysSince > 90
                    else -> daysSince > 120
                }
                
                if (shouldRemove) {
                    logger.debug("Removing old memory for player $playerId: ${memory.memory}")
                }
                
                shouldRemove
            }
        }
    }
    
    private fun publishSocialEvent(eventType: String, data: Map<String, String>) {
        val event = NPCEvents.customEvent(
            npcId = npc.id,
            npcName = npc.name,
            eventData = data + mapOf("socialEventType" to eventType)
        )
        eventBus.sendEventSync(event)
    }
    
    // === ГЕТТЕРЫ ДЛЯ СТАТИСТИКИ ===
    
    fun getSystemStats(): SocialSystemStats {
        return SocialSystemStats(
            totalRelationships = relationships.size,
            totalInteractions = totalInteractions,
            totalGroups = socialGroups.size,
            totalRules = socialRules.size,
            totalRuleViolations = ruleViolations,
            totalMemories = socialMemories.values.sumOf { it.size },
            isActive = isActive
        )
    }
}

data class SocialSituationAnalysis(
    val playerId: UUID,
    val relationshipType: RelationshipType,
    val relationship: SocialRelationship?,
    val groups: List<SocialGroup>,
    val recentMemories: List<SocialMemory>,
    val recentInteractions: List<SocialInteraction>,
    val socialAdvice: List<String>,
    val overallSentiment: Double,
    val trustLevel: Double,
    val compatibilityScore: Double
)

data class SocialSystemStats(
    val totalRelationships: Int,
    val totalInteractions: Int,
    val totalGroups: Int,
    val totalRules: Int,
    val totalRuleViolations: Int,
    val totalMemories: Int,
    val isActive: Boolean
)

data class SocialConfig(
    val updateIntervalMs: Long = 30000, // 30 секунд
    val maxMemoriesPerPlayer: Int = 100,
    val maxInteractionHistory: Int = 50,
    val relationshipDecayEnabled: Boolean = true,
    val autoForgiveMinorViolations: Boolean = false,
    val memoryCleanupEnabled: Boolean = true
)
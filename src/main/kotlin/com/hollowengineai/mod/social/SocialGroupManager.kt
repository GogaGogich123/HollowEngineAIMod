package com.hollowengineai.mod.social

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.actions.ActionResult
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.events.NPCEvent
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.reputation.ReputationSystem
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Менеджер социальных групп и кланов NPC
 * 
 * Управляет:
 * - Создание и управление группами NPC
 * - Отношения между группами
 * - Иерархии и роли внутри групп
 * - Территориальное поведение групп
 * - Коллективные действия и решения
 * - Конфликты и альянсы между группами
 */
@Serializable
object SocialGroupManager {
    
    // Основные данные групп
    private val groups = ConcurrentHashMap<String, SocialGroup>()
    private val npcGroupMembership = ConcurrentHashMap<String, String>() // NPCId -> GroupId
    private val groupRelations = ConcurrentHashMap<Pair<String, String>, GroupRelation>()
    
    // Территориальные зоны групп
    private val groupTerritories = ConcurrentHashMap<String, Territory>()
    
    // Кэши для производительности
    private val nearbyGroupsCache = ConcurrentHashMap<String, List<String>>()
    private val allianceCache = ConcurrentHashMap<String, Set<String>>()
    
    // Событийная система
    private lateinit var eventBus: NPCEventBus
    
    // Корутинная область для фоновых задач
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // JSON для сериализации
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Файл сохранения
    private val saveFile = File("config/hollowengineai/social_groups.json")
    
    private var isInitialized = false
    
    /**
     * Инициализация менеджера групп
     */
    fun initialize(eventBus: NPCEventBus) {
        this.eventBus = eventBus
        loadGroupData()
        startBackgroundTasks()
        isInitialized = true
        
        HollowEngineAIMod.LOGGER.info("SocialGroupManager initialized with ${groups.size} groups")
    }
    
    /**
     * Создать новую социальную группу
     */
    fun createGroup(
        groupId: String,
        name: String,
        type: GroupType,
        leader: SmartNPC? = null,
        description: String = ""
    ): SocialGroup {
        val group = SocialGroup(
            id = groupId,
            name = name,
            type = type,
            description = description,
            leaderId = leader?.id,
            createdAt = System.currentTimeMillis()
        )
        
        groups[groupId] = group
        
        // Добавляем лидера в группу если указан
        leader?.let { addNPCToGroup(it.id, groupId, GroupRole.LEADER) }
        
        // Устанавливаем базовые отношения с системой репутации
        ReputationSystem.setGlobalModifier("group_$groupId", 1.0f)
        
        HollowEngineAIMod.LOGGER.info("Created social group: $name ($groupId) of type $type")
        
        // Отправляем событие создания группы
        eventBus.publishEvent(NPCEvent.createGroupEvent(
            group = group,
            action = "group_created",
            details = mapOf(
                "group_name" to name,
                "group_type" to type.name,
                "leader" to (leader?.getEntity()?.name?.string ?: "none")
            )
        ))
        
        return group
    }
    
    /**
     * Добавить NPC в группу
     */
    fun addNPCToGroup(npcId: String, groupId: String, role: GroupRole = GroupRole.MEMBER): Boolean {
        val group = groups[groupId] ?: return false
        
        // Удаляем из старой группы если была
        val oldGroupId = npcGroupMembership[npcId]
        oldGroupId?.let { removeNPCFromGroup(npcId) }
        
        // Добавляем в новую группу
        val member = GroupMember(
            npcId = npcId,
            role = role,
            joinedAt = System.currentTimeMillis(),
            loyalty = 0.5f,
            influence = when (role) {
                GroupRole.LEADER -> 1.0f
                GroupRole.OFFICER -> 0.7f
                GroupRole.VETERAN -> 0.5f
                GroupRole.MEMBER -> 0.3f
                GroupRole.RECRUIT -> 0.1f
            }
        )
        
        group.members[npcId] = member
        npcGroupMembership[npcId] = groupId
        
        // Обновляем лидера если нужно
        if (role == GroupRole.LEADER) {
            group.leaderId = npcId
        }
        
        // Очищаем кэши
        clearCaches()
        
        HollowEngineAIMod.LOGGER.debug("Added NPC $npcId to group $groupId as $role")
        
        // Отправляем событие
        eventBus.publishEvent(NPCEvent.createGroupEvent(
            group = group,
            action = "member_joined",
            npcId = npcId,
            details = mapOf("role" to role.name)
        ))
        
        return true
    }
    
    /**
     * Удалить NPC из группы
     */
    fun removeNPCFromGroup(npcId: String): Boolean {
        val groupId = npcGroupMembership[npcId] ?: return false
        val group = groups[groupId] ?: return false
        
        val member = group.members.remove(npcId) ?: return false
        npcGroupMembership.remove(npcId)
        
        // Если это был лидер, выбираем нового
        if (group.leaderId == npcId) {
            val newLeader = group.members.values
                .filter { it.role == GroupRole.OFFICER }
                .maxByOrNull { it.influence + it.loyalty }
            
            group.leaderId = newLeader?.npcId
            newLeader?.let {
                it.role = GroupRole.LEADER
                it.influence = 1.0f
            }
        }
        
        // Если группа пуста, удаляем её
        if (group.members.isEmpty() && group.type != GroupType.PERSISTENT) {
            groups.remove(groupId)
        }
        
        // Очищаем кэши
        clearCaches()
        
        HollowEngineAIMod.LOGGER.debug("Removed NPC $npcId from group $groupId")
        
        // Отправляем событие
        eventBus.publishEvent(NPCEvent.createGroupEvent(
            group = group,
            action = "member_left",
            npcId = npcId,
            details = mapOf("old_role" to member.role.name)
        ))
        
        return true
    }
    
    /**
     * Получить группу NPC
     */
    fun getNPCGroup(npcId: String): SocialGroup? {
        val groupId = npcGroupMembership[npcId] ?: return null
        return groups[groupId]
    }
    
    /**
     * Получить роль NPC в группе
     */
    fun getNPCRole(npcId: String): GroupRole? {
        val group = getNPCGroup(npcId) ?: return null
        return group.members[npcId]?.role
    }
    
    /**
     * Проверить, находятся ли два NPC в одной группе
     */
    fun areInSameGroup(npcId1: String, npcId2: String): Boolean {
        val group1 = npcGroupMembership[npcId1]
        val group2 = npcGroupMembership[npcId2]
        return group1 != null && group1 == group2
    }
    
    /**
     * Установить отношения между группами
     */
    fun setGroupRelation(groupId1: String, groupId2: String, relationType: RelationType, strength: Float = 0.5f) {
        val group1 = groups[groupId1] ?: return
        val group2 = groups[groupId2] ?: return
        
        val relationKey = Pair(groupId1, groupId2)
        val reverseKey = Pair(groupId2, groupId1)
        
        val relation = GroupRelation(
            fromGroup = groupId1,
            toGroup = groupId2,
            type = relationType,
            strength = strength.coerceIn(0f, 1f),
            establishedAt = System.currentTimeMillis()
        )
        
        groupRelations[relationKey] = relation
        
        // Устанавливаем обратное отношение
        val reverseRelation = relation.copy(
            fromGroup = groupId2,
            toGroup = groupId1
        )
        groupRelations[reverseKey] = reverseRelation
        
        // Обновляем репутационные модификаторы
        when (relationType) {
            RelationType.ALLIED -> {
                ReputationSystem.setGlobalModifier("alliance_${groupId1}_$groupId2", 1.5f)
                ReputationSystem.setGlobalModifier("alliance_${groupId2}_$groupId1", 1.5f)
            }
            RelationType.HOSTILE -> {
                ReputationSystem.setGlobalModifier("hostility_${groupId1}_$groupId2", 2.0f)
                ReputationSystem.setGlobalModifier("hostility_${groupId2}_$groupId1", 2.0f)
            }
            else -> {
                // Нейтральные отношения не изменяют модификаторы
            }
        }
        
        // Очищаем кэши
        clearCaches()
        
        HollowEngineAIMod.LOGGER.info("Set relation between ${group1.name} and ${group2.name}: $relationType")
        
        // Отправляем событие
        eventBus.publishEvent(NPCEvent.createDiplomacyEvent(
            group1 = group1,
            group2 = group2,
            action = "relation_changed",
            relationType = relationType
        ))
    }
    
    /**
     * Получить отношение между группами
     */
    fun getGroupRelation(groupId1: String, groupId2: String): GroupRelation? {
        return groupRelations[Pair(groupId1, groupId2)]
    }
    
    /**
     * Получить всех союзников группы
     */
    fun getGroupAllies(groupId: String): Set<String> {
        return allianceCache.getOrPut(groupId) {
            groupRelations.entries
                .filter { it.key.first == groupId && it.value.type == RelationType.ALLIED }
                .map { it.key.second }
                .toSet()
        }
    }
    
    /**
     * Получить всех врагов группы
     */
    fun getGroupEnemies(groupId: String): Set<String> {
        return groupRelations.entries
            .filter { it.key.first == groupId && it.value.type == RelationType.HOSTILE }
            .map { it.key.second }
            .toSet()
    }
    
    /**
     * Создать территорию для группы
     */
    fun createTerritory(
        groupId: String,
        center: BlockPos,
        radius: Int,
        level: Level,
        name: String = "",
        type: TerritoryType = TerritoryType.SETTLEMENT
    ): Territory {
        val group = groups[groupId] ?: throw IllegalArgumentException("Group $groupId not found")
        
        val territory = Territory(
            groupId = groupId,
            name = name.ifEmpty { "${group.name} Territory" },
            center = center,
            radius = radius,
            level = level.dimension().location().toString(),
            type = type,
            establishedAt = System.currentTimeMillis()
        )
        
        groupTerritories[groupId] = territory
        
        HollowEngineAIMod.LOGGER.info("Created territory '${territory.name}' for group ${group.name}")
        
        return territory
    }
    
    /**
     * Проверить, находится ли позиция на территории группы
     */
    fun isInTerritory(groupId: String, position: BlockPos): Boolean {
        val territory = groupTerritories[groupId] ?: return false
        val distance = sqrt(
            (position.x - territory.center.x).toDouble().let { it * it } +
            (position.z - territory.center.z).toDouble().let { it * it }
        )
        return distance <= territory.radius
    }
    
    /**
     * Получить группы поблизости от позиции
     */
    fun getNearbyGroups(position: BlockPos, radius: Double): List<SocialGroup> {
        return groupTerritories.values
            .filter { territory ->
                val distance = sqrt(
                    (position.x - territory.center.x).toDouble().let { it * it } +
                    (position.z - territory.center.z).toDouble().let { it * it }
                )
                distance <= radius + territory.radius
            }
            .mapNotNull { groups[it.groupId] }
    }
    
    /**
     * Выполнить коллективное действие группы
     */
    suspend fun executeGroupAction(
        groupId: String,
        action: GroupAction,
        target: Any? = null,
        parameters: Map<String, Any> = emptyMap()
    ): GroupActionResult {
        val group = groups[groupId] ?: return GroupActionResult(
            false, "Group not found", emptyList()
        )
        
        val participants = selectParticipants(group, action)
        if (participants.isEmpty()) {
            return GroupActionResult(false, "No available participants", emptyList())
        }
        
        // Выполняем действие в зависимости от типа
        val results = when (action) {
            is CollectiveMovement -> executeCollectiveMovement(participants, action.destination)
            is DefendTerritory -> executeDefendTerritory(participants, groupId)
            is AttackGroup -> executeAttackGroup(participants, action.targetGroupId)
            is GatherResources -> executeGatherResources(participants, action.resourceType)
            is GroupPatrol -> executeGroupPatrol(participants, groupId)
            is GroupMeeting -> executeGroupMeeting(participants, action.agenda)
        }
        
        // Обновляем лояльность участников
        updateParticipantLoyalty(participants, results.any { it.success })
        
        // Отправляем событие
        eventBus.publishEvent(NPCEvent.createGroupEvent(
            group = group,
            action = "collective_action",
            details = mapOf(
                "action_type" to action.javaClass.simpleName,
                "participants" to participants.size,
                "success" to results.any { it.success }
            )
        ))
        
        val success = results.count { it.success } > results.size / 2
        return GroupActionResult(success, "Group action completed", results)
    }
    
    /**
     * Обновить лояльность и влияние в группе
     */
    fun updateMemberStats(npcId: String, loyaltyChange: Float = 0f, influenceChange: Float = 0f) {
        val groupId = npcGroupMembership[npcId] ?: return
        val group = groups[groupId] ?: return
        val member = group.members[npcId] ?: return
        
        member.loyalty = (member.loyalty + loyaltyChange).coerceIn(0f, 1f)
        member.influence = (member.influence + influenceChange).coerceIn(0f, 1f)
        
        // Возможное продвижение по службе при высокой лояльности и влиянии
        if (member.loyalty > 0.8f && member.influence > 0.6f) {
            val newRole = when (member.role) {
                GroupRole.RECRUIT -> GroupRole.MEMBER
                GroupRole.MEMBER -> GroupRole.VETERAN
                GroupRole.VETERAN -> if (group.members.values.count { it.role == GroupRole.OFFICER } < 3) 
                    GroupRole.OFFICER else member.role
                else -> member.role
            }
            
            if (newRole != member.role) {
                member.role = newRole
                HollowEngineAIMod.LOGGER.info("NPC $npcId promoted to $newRole in group $groupId")
            }
        }
    }
    
    /**
     * Получить статистику групп
     */
    fun getGroupStats(): GroupSystemStats {
        val totalMembers = groups.values.sumOf { it.members.size }
        val averageGroupSize = if (groups.isNotEmpty()) totalMembers.toFloat() / groups.size else 0f
        val activeConflicts = groupRelations.values.count { it.type == RelationType.HOSTILE }
        val activeAlliances = groupRelations.values.count { it.type == RelationType.ALLIED } / 2 // Делим на 2 так как отношения двусторонние
        
        return GroupSystemStats(
            totalGroups = groups.size,
            totalMembers = totalMembers,
            averageGroupSize = averageGroupSize,
            activeConflicts = activeConflicts,
            activeAlliances = activeAlliances,
            territories = groupTerritories.size,
            relationshipCount = groupRelations.size / 2
        )
    }
    
    /**
     * Получить все социальные группы
     */
    fun getAllGroups(): Collection<SocialGroup> {
        return groups.values.toList()
    }
    
    /**
     * Получить группу по имени
     * Возвращает первую найденную группу с указанным именем
     */
    fun getGroupByName(name: String): SocialGroup? {
        return groups.values.find { group ->
            group.name.equals(name, ignoreCase = true)
        }
    }
    
    /**
     * Получить все группы с указанным именем
     * Полезно когда несколько групп имеют одинаковые имена
     */
    fun getAllGroupsByName(name: String): List<SocialGroup> {
        return groups.values.filter { group ->
            group.name.equals(name, ignoreCase = true)
        }
    }
    
    /**
     * Получить группу по ID
     */
    fun getGroupById(groupId: String): SocialGroup? {
        return groups[groupId]
    }
    
    /**
     * Публичный метод для роспуска группы
     * @param groupId ID группы для роспуска
     * @param reason Причина роспуска
     * @return true если группа успешно роспущена
     */
    fun disbandGroup(groupId: String, reason: String = "Admin action"): Boolean {
        val group = groups[groupId] ?: return false
        
        try {
            // Удаляем всех членов из группы
            group.members.keys.toList().forEach { npcId ->
                removeNPCFromGroup(npcId)
            }
            
            // Удаляем группу и связанные данные
            groups.remove(groupId)
            groupTerritories.remove(groupId)
            
            // Удаляем все отношения с этой группой
            groupRelations.entries.removeIf { 
                it.key.first == groupId || it.key.second == groupId 
            }
            
            clearCaches()
            
            HollowEngineAIMod.LOGGER.info("Disbanded group ${group.name}: $reason")
            
            // Отправляем событие
            eventBus.publishEvent(NPCEvent.createGroupEvent(
                group = group,
                action = "group_disbanded",
                details = mapOf("reason" to reason)
            ))
            
            return true
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to disband group ${group.name}", e)
            return false
        }
    }
    
    // Приватные методы
    
    /**
     * Выбрать участников для коллективного действия
     */
    private fun selectParticipants(group: SocialGroup, action: GroupAction): List<String> {
        val maxParticipants = when (action) {
            is AttackGroup -> min(group.members.size, 10)
            is DefendTerritory -> min(group.members.size, 15)
            is CollectiveMovement -> group.members.size
            is GatherResources -> min(group.members.size, 5)
            is GroupPatrol -> min(group.members.size, 4)
            is GroupMeeting -> min(group.members.size, 8)
        }
        
        return group.members.values
            .filter { it.loyalty > 0.3f } // Только лояльные члены участвуют
            .sortedByDescending { it.influence + it.loyalty }
            .take(maxParticipants)
            .map { it.npcId }
    }
    
    /**
     * Выполнить коллективное перемещение
     */
    private suspend fun executeCollectiveMovement(
        participants: List<String>, 
        destination: BlockPos
    ): List<ActionResult> {
        // Заглушка для коллективного движения
        return participants.map { npcId ->
            ActionResult(
                success = Random.nextFloat() < 0.8f,
                message = "Moving to destination",
                data = mapOf("npc" to npcId, "destination" to destination.toString())
            )
        }
    }
    
    /**
     * Выполнить защиту территории
     */
    private suspend fun executeDefendTerritory(
        participants: List<String>,
        groupId: String
    ): List<ActionResult> {
        val territory = groupTerritories[groupId]
        
        return participants.map { npcId ->
            ActionResult(
                success = Random.nextFloat() < 0.9f, // Защита на своей территории более успешна
                message = "Defending territory",
                data = mapOf(
                    "npc" to npcId,
                    "territory" to (territory?.name ?: "Unknown")
                )
            )
        }
    }
    
    /**
     * Выполнить атаку на группу
     */
    private suspend fun executeAttackGroup(
        participants: List<String>,
        targetGroupId: String
    ): List<ActionResult> {
        val targetGroup = groups[targetGroupId]
        
        return participants.map { npcId ->
            val success = Random.nextFloat() < 0.6f // Атака менее вероятна для успеха
            ActionResult(
                success = success,
                message = if (success) "Attack successful" else "Attack failed",
                data = mapOf(
                    "attacker" to npcId,
                    "target_group" to (targetGroup?.name ?: "Unknown")
                )
            )
        }
    }
    
    /**
     * Выполнить сбор ресурсов
     */
    private suspend fun executeGatherResources(
        participants: List<String>,
        resourceType: String
    ): List<ActionResult> {
        return participants.map { npcId ->
            ActionResult(
                success = Random.nextFloat() < 0.7f,
                message = "Gathering $resourceType",
                data = mapOf("npc" to npcId, "resource" to resourceType)
            )
        }
    }
    
    /**
     * Выполнить групповое патрулирование
     */
    private suspend fun executeGroupPatrol(
        participants: List<String>,
        groupId: String
    ): List<ActionResult> {
        return participants.map { npcId ->
            ActionResult(
                success = Random.nextFloat() < 0.85f,
                message = "Patrolling area",
                data = mapOf("npc" to npcId, "group" to groupId)
            )
        }
    }
    
    /**
     * Выполнить групповое собрание
     */
    private suspend fun executeGroupMeeting(
        participants: List<String>,
        agenda: String
    ): List<ActionResult> {
        return participants.map { npcId ->
            ActionResult(
                success = true, // Собрания всегда успешны
                message = "Participating in meeting",
                data = mapOf("npc" to npcId, "agenda" to agenda)
            )
        }
    }
    
    /**
     * Обновить лояльность участников после действия
     */
    private fun updateParticipantLoyalty(participants: List<String>, success: Boolean) {
        val loyaltyChange = if (success) 0.05f else -0.03f
        
        participants.forEach { npcId ->
            updateMemberStats(npcId, loyaltyChange)
        }
    }
    
    /**
     * Запуск фоновых задач
     */
    private fun startBackgroundTasks() {
        // Задача обновления групповой динамики
        scope.launch {
            while (true) {
                delay(60000L) // Каждую минуту
                try {
                    updateGroupDynamics()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in group dynamics update", e)
                }
            }
        }
        
        // Задача автосохранения
        scope.launch {
            while (true) {
                delay(300000L) // Каждые 5 минут
                try {
                    saveGroupData()
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in group data save", e)
                }
            }
        }
    }
    
    /**
     * Обновление групповой динамики
     */
    private fun updateGroupDynamics() {
        // Проверяем стабильность групп
        groups.values.forEach { group ->
            val averageLoyalty = group.members.values.map { it.loyalty }.average().toFloat()
            
            // Если средняя лояльность очень низкая, группа может распасться
            if (averageLoyalty < 0.2f && group.type != GroupType.PERSISTENT) {
                HollowEngineAIMod.LOGGER.info("Group ${group.name} is unstable (low loyalty: $averageLoyalty)")
                
                // Возможный распад группы
                if (Random.nextFloat() < 0.1f) {
                    disbandGroup(group.id, "Low loyalty")
                }
            }
            
            // Естественные изменения лояльности
            group.members.values.forEach { member ->
                val naturalChange = (Random.nextFloat() - 0.5f) * 0.02f
                member.loyalty = (member.loyalty + naturalChange).coerceIn(0f, 1f)
            }
        }
        
        // Обновляем отношения между группами
        updateGroupRelations()
    }
    
    /**
     * Обновление отношений между группами
     */
    private fun updateGroupRelations() {
        groupRelations.values.forEach { relation ->
            // Отношения могут естественным образом изменяться
            val change = (Random.nextFloat() - 0.5f) * 0.01f
            relation.strength = (relation.strength + change).coerceIn(0f, 1f)
            
            // Очень слабые враждебные отношения могут стать нейтральными
            if (relation.type == RelationType.HOSTILE && relation.strength < 0.1f) {
                relation.type = RelationType.NEUTRAL
                relation.strength = 0.3f
            }
        }
    }
    

    
    /**
     * Очистить кэши
     */
    private fun clearCaches() {
        nearbyGroupsCache.clear()
        allianceCache.clear()
    }
    
    /**
     * Сохранение данных групп
     */
    private suspend fun saveGroupData() {
        try {
            withContext(Dispatchers.IO) {
                saveFile.parentFile?.mkdirs()
                
                val saveData = GroupSaveData(
                    groups = groups.toMap(),
                    npcGroupMembership = npcGroupMembership.toMap(),
                    groupRelations = groupRelations.mapKeys { "${it.key.first}:${it.key.second}" },
                    groupTerritories = groupTerritories.toMap(),
                    version = 1
                )
                
                val jsonString = json.encodeToString(GroupSaveData.serializer(), saveData)
                saveFile.writeText(jsonString)
            }
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to save group data", e)
        }
    }
    
    /**
     * Загрузка данных групп
     */
    private fun loadGroupData() {
        try {
            if (!saveFile.exists()) {
                HollowEngineAIMod.LOGGER.info("No existing group data found, starting fresh")
                return
            }
            
            val jsonString = saveFile.readText()
            val saveData = json.decodeFromString(GroupSaveData.serializer(), jsonString)
            
            groups.clear()
            groups.putAll(saveData.groups)
            
            npcGroupMembership.clear()
            npcGroupMembership.putAll(saveData.npcGroupMembership)
            
            groupRelations.clear()
            saveData.groupRelations.forEach { (keyString, relation) ->
                val parts = keyString.split(":")
                if (parts.size == 2) {
                    groupRelations[Pair(parts[0], parts[1])] = relation
                }
            }
            
            groupTerritories.clear()
            groupTerritories.putAll(saveData.groupTerritories)
            
            HollowEngineAIMod.LOGGER.info(
                "Loaded group data: ${groups.size} groups, ${npcGroupMembership.size} memberships, " +
                "${groupRelations.size} relations, ${groupTerritories.size} territories"
            )
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Failed to load group data", e)
        }
    }
    
    /**
     * Завершение работы
     */
    fun shutdown() {
        scope.cancel()
        runBlocking {
            saveGroupData()
        }
        HollowEngineAIMod.LOGGER.info("SocialGroupManager shut down")
    }
}

// Данные классы и енумы

/**
 * Социальная группа NPC
 */
@Serializable
data class SocialGroup(
    val id: String,
    val name: String,
    val type: GroupType,
    val description: String = "",
    var leaderId: String? = null,
    val members: MutableMap<String, GroupMember> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var disbanded: Boolean = false
)

/**
 * Член группы
 */
@Serializable
data class GroupMember(
    val npcId: String,
    var role: GroupRole,
    val joinedAt: Long,
    var loyalty: Float, // 0.0 - 1.0
    var influence: Float, // 0.0 - 1.0
    var lastActive: Long = System.currentTimeMillis()
)

/**
 * Отношение между группами
 */
@Serializable
data class GroupRelation(
    val fromGroup: String,
    val toGroup: String,
    var type: RelationType,
    var strength: Float, // 0.0 - 1.0
    val establishedAt: Long,
    var lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Территория группы
 */
@Serializable
data class Territory(
    val groupId: String,
    val name: String,
    @Serializable(with = BlockPosSerializer::class)
    val center: BlockPos,
    val radius: Int,
    val level: String, // Dimension
    val type: TerritoryType,
    val establishedAt: Long
)

/**
 * Результат группового действия
 */
data class GroupActionResult(
    val success: Boolean,
    val message: String,
    val individualResults: List<ActionResult>
)

/**
 * Статистика системы групп
 */
data class GroupSystemStats(
    val totalGroups: Int,
    val totalMembers: Int,
    val averageGroupSize: Float,
    val activeConflicts: Int,
    val activeAlliances: Int,
    val territories: Int,
    val relationshipCount: Int
)

/**
 * Типы групп
 */
enum class GroupType(val displayName: String) {
    CLAN("Клан"),           // Постоянные кланы
    GUILD("Гильдия"),          // Профессиональные гильдии
    MILITARY("Воинство"),       // Военные отряды
    TRADING("Торговцы"),        // Торговые компании
    RELIGIOUS("Орден"),      // Религиозные ордена
    BANDITS("Бандиты"),        // Банды разбойников
    TEMPORARY("Временная"),      // Временные группы
    PERSISTENT("Постоянная")      // Системные группы (не удаляются)
}

/**
 * Роли в группе
 */
enum class GroupRole(val level: Int) {
    LEADER(5),
    OFFICER(4),
    VETERAN(3),
    MEMBER(2),
    RECRUIT(1)
}

/**
 * Типы отношений между группами
 */
enum class RelationType {
    ALLIED,         // Союзники
    FRIENDLY,       // Дружественные
    NEUTRAL,        // Нейтральные
    UNFRIENDLY,     // Недружественные
    HOSTILE,        // Враги
    AT_WAR          // В состоянии войны
}

/**
 * Типы территорий
 */
enum class TerritoryType {
    SETTLEMENT,     // Поселение
    FORTRESS,       // Крепость
    CAMP,           // Лагерь
    HUNTING_GROUND, // Охотничьи угодья
    SACRED_PLACE,   // Священное место
    RESOURCE_AREA   // Ресурсная зона
}

/**
 * Базовый класс для групповых действий
 */
sealed class GroupAction

data class CollectiveMovement(val destination: BlockPos) : GroupAction()
data class DefendTerritory(val territoryId: String) : GroupAction()
data class AttackGroup(val targetGroupId: String) : GroupAction()
data class GatherResources(val resourceType: String) : GroupAction()
data class GroupPatrol(val patrolRoute: List<BlockPos> = emptyList()) : GroupAction()
data class GroupMeeting(val agenda: String) : GroupAction()

/**
 * Данные для сохранения
 */
@Serializable
private data class GroupSaveData(
    val groups: Map<String, SocialGroup>,
    val npcGroupMembership: Map<String, String>,
    val groupRelations: Map<String, GroupRelation>,
    val groupTerritories: Map<String, Territory>,
    val version: Int
)

// ActionResult перенесен в ActionExecutor.kt чтобы избежать конфликтов имен

/**
 * Сериализатор для BlockPos
 */
@Serializer(forClass = BlockPos::class)
object BlockPosSerializer : KSerializer<BlockPos> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("BlockPos") {
        element<Int>("x")
        element<Int>("y") 
        element<Int>("z")
    }
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: BlockPos) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.x)
            encodeIntElement(descriptor, 1, value.y)
            encodeIntElement(descriptor, 2, value.z)
        }
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): BlockPos {
        return decoder.decodeStructure(descriptor) {
            var x = 0
            var y = 0
            var z = 0
            
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeIntElement(descriptor, 0)
                    1 -> y = decodeIntElement(descriptor, 1)
                    2 -> z = decodeIntElement(descriptor, 2)
                    kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            
            BlockPos(x, y, z)
        }
    }
}
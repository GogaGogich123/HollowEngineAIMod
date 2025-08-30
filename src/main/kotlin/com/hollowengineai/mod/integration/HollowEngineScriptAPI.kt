package com.hollowengineai.mod.integration

import com.hollowengineai.mod.core.SmartNPC
import net.minecraft.world.entity.LivingEntity
import org.apache.logging.log4j.LogManager

/**
 * Простой API для использования в скриптах HollowEngine Legacy
 * 
 * Предоставляет удобные функции для:
 * - Привязки AI к НПС
 * - Управления умными НПС
 * - Настройки поведения
 */
object HollowEngineScriptAPI {
    private val LOGGER = LogManager.getLogger(HollowEngineScriptAPI::class.java)
    
    /**
     * Сделать НПС умным - привязать AI к существующему НПС
     * 
     * @param npcEntity Сущность НПС из HollowEngine Legacy
     * @param personality Конфигурация личности НПС
     * @param npcId Уникальный ID НПС (по умолчанию генерируется автоматически)
     * @return SmartNPC или null в случае ошибки
     */
    fun makeNPCSmart(
        npcEntity: LivingEntity,
        personality: NPCPersonality,
        npcId: String = generateNPCId(npcEntity, personality.name)
    ): SmartNPC? {
        LOGGER.info("Making NPC smart: ${personality.name} (ID: $npcId)")
        
        return HollowEngineBridge.enhanceNPC(
            npcId = npcId,
            npcEntity = npcEntity,
            personality = personality
        )
    }
    
    /**
     * Убрать AI у НПС - вернуть к обычному состоянию
     * 
     * @param npcId ID НПС или сущность
     * @return true если AI был успешно удален
     */
    fun removeNPCAI(npcId: String): Boolean {
        LOGGER.info("Removing AI from NPC: $npcId")
        return HollowEngineBridge.removeAI(npcId)
    }
    
    /**
     * Убрать AI у НПС по сущности
     */
    fun removeNPCAI(npcEntity: LivingEntity): Boolean {
        val npcId = findNPCId(npcEntity) ?: return false
        return removeNPCAI(npcId)
    }
    
    /**
     * Проверить, умный ли НПС (есть ли у него AI)
     */
    fun isNPCSmart(npcId: String): Boolean {
        return HollowEngineBridge.isEnhanced(npcId)
    }
    
    /**
     * Проверить по сущности
     */
    fun isNPCSmart(npcEntity: LivingEntity): Boolean {
        val npcId = findNPCId(npcEntity) ?: return false
        return isNPCSmart(npcId)
    }
    
    /**
     * Получить умного НПС по ID
     */
    fun getSmartNPC(npcId: String): SmartNPC? {
        return HollowEngineBridge.getSmartNPC(npcId)
    }
    
    /**
     * Получить умного НПС по сущности
     */
    fun getSmartNPC(npcEntity: LivingEntity): SmartNPC? {
        val npcId = findNPCId(npcEntity) ?: return null
        return getSmartNPC(npcId)
    }
    
    /**
     * Настроить дополнительные параметры умного НПС
     */
    fun configureSmartNPC(npcId: String, config: NPCConfiguration.() -> Unit): Boolean {
        val smartNPC = getSmartNPC(npcId) ?: return false
        
        try {
            val configuration = NPCConfiguration(smartNPC)
            configuration.apply(config)
            LOGGER.info("Configured smart NPC: $npcId")
            return true
        } catch (e: Exception) {
            LOGGER.error("Error configuring smart NPC: $npcId", e)
            return false
        }
    }
    
    /**
     * Уведомить AI НПС о событии из HollowEngine Legacy
     */
    fun notifyNPCAction(npcId: String, action: String, details: String = "") {
        HollowEngineBridge.syncHollowEngineAction(
            npcId = npcId,
            action = action,
            data = if (details.isNotEmpty()) mapOf("details" to details) else emptyMap()
        )
    }
    
    /**
     * Уведомить по сущности
     */
    fun notifyNPCAction(npcEntity: LivingEntity, action: String, details: String = "") {
        val npcId = findNPCId(npcEntity) ?: return
        notifyNPCAction(npcId, action, details)
    }
    
    /**
     * Получить всех умных НПС в мире
     */
    fun getAllSmartNPCs(): Collection<SmartNPC> {
        return HollowEngineBridge.getAllEnhancedNPCs()
    }
    
    /**
     * Отправить прямой промт AI НПС - дать ему конкретное задание
     * 
     * @param npcId ID НПС
     * @param prompt Инструкция для AI (например, "Подойди к воротам и встреть игрока")
     * @param context Дополнительная контекстная информация
     * @return true если промт отправлен успешно
     */
    fun sendAIPrompt(
        npcId: String, 
        prompt: String, 
        context: Map<String, Any> = emptyMap()
    ): Boolean {
        LOGGER.info("Sending AI prompt to NPC $npcId: $prompt")
        return HollowEngineBridge.sendDirectPrompt(npcId, prompt, context)
    }
    
    /**
     * Отправить промт AI НПС по сущности
     */
    fun sendAIPrompt(
        npcEntity: LivingEntity,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): Boolean {
        val npcId = findNPCId(npcEntity) ?: return false
        return sendAIPrompt(npcId, prompt, context)
    }
    
    /**
     * Отправить групповой промт нескольким AI НПС одновременно
     * 
     * @param npcIds Список ID НПС
     * @param prompt Общая инструкция для всех
     * @param context Контекст (автоматически дополняется информацией о группе)
     * @return количество НПС, которым успешно отправлен промт
     */
    fun sendGroupAIPrompt(
        npcIds: List<String>,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): Int {
        LOGGER.info("Sending group AI prompt to ${npcIds.size} NPCs: $prompt")
        return HollowEngineBridge.sendGroupPrompt(npcIds, prompt, context)
    }
    
    /**
     * Отправить промт всем AI НПС в определенной области
     * 
     * @param centerX, centerY, centerZ Центр области
     * @param radius Радиус области
     * @param prompt Инструкция для всех НПС в области
     * @return количество НПС в области, получивших промт
     */
    fun sendAreaAIPrompt(
        centerX: Double, centerY: Double, centerZ: Double,
        radius: Double,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): Int {
        LOGGER.info("Sending area AI prompt in radius $radius: $prompt")
        return HollowEngineBridge.sendAreaPrompt(centerX, centerY, centerZ, radius, prompt, context)
    }
    
    /**
     * Отправить промт всем AI НПС вокруг определенной позиции
     */
    fun sendAreaAIPrompt(
        centerPos: Any, // BlockPos или аналогичный объект позиции
        radius: Double,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): Int {
        // Пытаемся извлечь координаты из объекта позиции
        val (x, y, z) = when {
            centerPos.toString().contains("(") -> {
                // Парсим строковое представление позиции типа "(100, 64, 200)"
                val coords = centerPos.toString()
                    .removePrefix("(")
                    .removeSuffix(")")
                    .split(",")
                    .map { it.trim().toDoubleOrNull() ?: 0.0 }
                Triple(coords.getOrElse(0) { 0.0 }, coords.getOrElse(1) { 0.0 }, coords.getOrElse(2) { 0.0 })
            }
            else -> Triple(0.0, 0.0, 0.0)
        }
        
        return sendAreaAIPrompt(x, y, z, radius, prompt, context)
    }
    
    /**
     * Получить статус выполнения промта для НПС
     */
    fun getAIPromptStatus(npcId: String): String? {
        return HollowEngineBridge.getPromptStatus(npcId)
    }
    
    /**
     * Получить статус по сущности
     */
    fun getAIPromptStatus(npcEntity: LivingEntity): String? {
        val npcId = findNPCId(npcEntity) ?: return null
        return getAIPromptStatus(npcId)
    }
    
    /**
     * Отменить активные промты для НПС
     */
    fun cancelAIPrompts(npcId: String): Boolean {
        LOGGER.info("Cancelling AI prompts for NPC: $npcId")
        return HollowEngineBridge.cancelPrompts(npcId)
    }
    
    /**
     * Отменить промты по сущности
     */
    fun cancelAIPrompts(npcEntity: LivingEntity): Boolean {
        val npcId = findNPCId(npcEntity) ?: return false
        return cancelAIPrompts(npcId)
    }
    
    /**
     * Быстрые команды для типичных сценариев
     */
    object QuickCommands {
        
        /**
         * Организовать встречу игрока у ворот
         */
        fun organizeMeeting(
            mayorId: String,
            guardIds: List<String>,
            playerName: String,
            meetingPoint: String = "главные ворота"
        ): Int {
            var successCount = 0
            
            // Мэр - главный организатор
            if (sendAIPrompt(
                mayorId,
                "Организуй торжественную встречу игрока $playerName у $meetingPoint. " +
                "Подойди туда, подготовь речь, координируйся со стражами.",
                mapOf(
                    "event_type" to "player_meeting",
                    "player_name" to playerName,
                    "location" to meetingPoint
                )
            )) {
                successCount++
            }
            
            // Стражи - сопровождающие
            guardIds.forEach { guardId ->
                if (sendAIPrompt(
                    guardId,
                    "Сопроводи мэра к $meetingPoint для встречи игрока $playerName. " +
                    "Следуй указаниям мэра, обеспечь безопасность встречи.",
                    mapOf(
                        "event_type" to "escort_duty",
                        "player_name" to playerName,
                        "mayor_id" to mayorId
                    )
                )) {
                    successCount++
                }
            }
            
            return successCount
        }
        
        /**
         * Организовать праздник в деревне
         */
        fun organizeCelebration(
            organizerIds: List<String>,
            reason: String,
            location: String = "центр деревни"
        ): Int {
            return sendGroupAIPrompt(
                organizerIds,
                "Организуйте праздник в честь: $reason. Место проведения: $location. " +
                "Каждый должен внести свой вклад согласно своей роли.",
                mapOf(
                    "event_type" to "celebration",
                    "reason" to reason,
                    "location" to location
                )
            )
        }
        
        /**
         * Объявить тревогу в поселении
         */
        fun declareAlert(
            npcIds: List<String>,
            threat: String,
            severity: String = "medium"
        ): Int {
            return sendGroupAIPrompt(
                npcIds,
                "ТРЕВОГА! Обнаружена угроза: $threat. Уровень опасности: $severity. " +
                "Действуйте согласно своей роли и полномочиям.",
                mapOf(
                    "event_type" to "alert",
                    "threat" to threat,
                    "severity" to severity
                )
            )
        }
        
        /**
         * Собрать совет деревни
         */
        fun callCouncilMeeting(
            councilIds: List<String>,
            topic: String,
            meetingPlace: String = "ратуша"
        ): Int {
            return sendGroupAIPrompt(
                councilIds,
                "Созывается совет деревни. Тема: $topic. Место: $meetingPlace. " +
                "Все члены совета должны прибыть для обсуждения.",
                mapOf(
                    "event_type" to "council_meeting",
                    "topic" to topic,
                    "location" to meetingPlace
                )
            )
        }
    }
    
    /**
     * Генерировать ID НПС на основе сущности и имени
     */
    private fun generateNPCId(npcEntity: LivingEntity, name: String): String {
        return "${name}_${npcEntity.uuid}_${System.currentTimeMillis()}"
    }
    
    /**
     * Найти ID НПС по сущности (упрощенная версия)
     */
    private fun findNPCId(npcEntity: LivingEntity): String? {
        // Попробуем найти среди всех умных НПС
        for (smartNPC in getAllSmartNPCs()) {
            if (smartNPC.entity == npcEntity) {
                return HollowEngineBridge.getHollowEngineId(smartNPC)
            }
        }
        return null
    }
}

/**
 * Класс для дополнительной настройки умного НПС
 */
class NPCConfiguration(private val smartNPC: SmartNPC) {
    
    /**
     * Установить дополнительные цели
     */
    fun addGoals(vararg goals: String) {
        goals.forEach { goal ->
            smartNPC.memory.addGoal(goal)
        }
    }
    
    /**
     * Добавить воспоминание
     */
    fun addMemory(category: String, content: String) {
        smartNPC.memory.addEpisode(category, content)
    }
    
    /**
     * Настроить отношения с другими НПС или игроками
     */
    fun setRelationship(target: String, level: Float) {
        smartNPC.memory.setRelationship(target, level)
    }
    
    /**
     * Установить навык
     */
    fun setSkill(skillName: String, level: Float) {
        smartNPC.memory.setSkillLevel(skillName, level)
    }
    
    /**
     * Настроить приоритет определенных действий
     */
    fun setActionPriority(actionType: String, priority: Float) {
        // Реализация зависит от системы приоритетов в ActionExecutor
        smartNPC.memory.addEpisode(
            "action_priority", 
            "Set priority for $actionType to $priority"
        )
    }
}

    /**
     * Генерировать ID НПС на основе сущности и имени
     */
    private fun generateNPCId(npcEntity: LivingEntity, name: String): String {
        return "${name}_${npcEntity.uuid}_${System.currentTimeMillis()}"
    }
    
    /**
     * Найти ID НПС по сущности (упрощенная версия)
     */
    private fun findNPCId(npcEntity: LivingEntity): String? {
        // Попробуем найти среди всех умных НПС
        for (smartNPC in getAllSmartNPCs()) {
            if (smartNPC.entity == npcEntity) {
                return HollowEngineBridge.getHollowEngineId(smartNPC)
            }
        }
        return null
    }
}

// Удобные функции для быстрого создания умных НПС с готовыми личностями

/**
 * Создать умного торговца
 */
fun LivingEntity.makeTrader(
    name: String, 
    biography: String = ""
): SmartNPC? {
    return HollowEngineScriptAPI.makeNPCSmart(
        npcEntity = this,
        personality = NPCPersonality.friendlyTrader(name, biography)
    )
}

/**
 * Создать умного стража  
 */
fun LivingEntity.makeGuard(
    name: String,
    biography: String = ""
): SmartNPC? {
    return HollowEngineScriptAPI.makeNPCSmart(
        npcEntity = this,
        personality = NPCPersonality.cautiousGuard(name, biography)
    )
}

/**
 * Создать умного ученого
 */
fun LivingEntity.makeScholar(
    name: String,
    biography: String = ""
): SmartNPC? {
    return HollowEngineScriptAPI.makeNPCSmart(
        npcEntity = this,
        personality = NPCPersonality.curiousScholar(name, biography)
    )
}

/**
 * Создать умного бандита
 */
fun LivingEntity.makeBandit(
    name: String,
    biography: String = ""
): SmartNPC? {
    return HollowEngineScriptAPI.makeNPCSmart(
        npcEntity = this,
        personality = NPCPersonality.aggressiveBandit(name, biography)
    )
}

/**
 * Создать умного крестьянина
 */
fun LivingEntity.makePeasant(
    name: String,
    biography: String = ""
): SmartNPC? {
    return HollowEngineScriptAPI.makeNPCSmart(
        npcEntity = this,
        personality = NPCPersonality.neutralPeasant(name, biography)
    )
}

// Extension функции для отправки промтов

/**
 * Отправить прямой AI промт НПС
 */
fun SmartNPC.sendPrompt(
    prompt: String,
    context: Map<String, Any> = emptyMap()
): Boolean {
    val npcId = HollowEngineBridge.getHollowEngineId(this) ?: return false
    return HollowEngineScriptAPI.sendAIPrompt(npcId, prompt, context)
}

/**
 * Отправить прямой AI промт по сущности
 */
fun LivingEntity.sendAIPrompt(
    prompt: String,
    context: Map<String, Any> = emptyMap()
): Boolean {
    return HollowEngineScriptAPI.sendAIPrompt(this, prompt, context)
}

/**
 * Получить статус AI промта
 */
fun LivingEntity.getAIStatus(): String? {
    return HollowEngineScriptAPI.getAIPromptStatus(this)
}

/**
 * Отменить активные AI промты
 */
fun LivingEntity.cancelAIPrompts(): Boolean {
    return HollowEngineScriptAPI.cancelAIPrompts(this)
}

/**
 * Проверить является ли НПС умным
 */
fun LivingEntity.isAISmart(): Boolean {
    return HollowEngineScriptAPI.isNPCSmart(this)
}

// Сокращенные функции для типичных сценариев

/**
 * Попросить AI НПС подойти к определенному месту
 */
fun LivingEntity.goTo(location: String, purpose: String = ""): Boolean {
    val fullPrompt = if (purpose.isNotEmpty()) {
        "Подойди к: $location. Цель: $purpose"
    } else {
        "Подойди к: $location"
    }
    return sendAIPrompt(fullPrompt)
}

/**
 * Попросить AI НПС пообщаться с кем-то
 */
fun LivingEntity.talkTo(target: String, topic: String = ""): Boolean {
    val fullPrompt = if (topic.isNotEmpty()) {
        "Пообщайся с $target на тему: $topic"
    } else {
        "Пообщайся с $target"
    }
    return sendAIPrompt(fullPrompt)
}

/**
 * Попросить AI НПС выполнить определенное действие
 */
fun LivingEntity.performAction(action: String, details: String = ""): Boolean {
    val fullPrompt = if (details.isNotEmpty()) {
        "$action. Детали: $details"
    } else {
        action
    }
    return sendAIPrompt(fullPrompt)
}

/**
 * Попросить AI НПС принять участие в событии
 */
fun LivingEntity.participateIn(eventName: String, role: String = ""): Boolean {
    val fullPrompt = if (role.isNotEmpty()) {
        "Прими участие в событии: $eventName. Твоя роль: $role"
    } else {
        "Прими участие в событии: $eventName"
    }
    return sendAIPrompt(fullPrompt)
}
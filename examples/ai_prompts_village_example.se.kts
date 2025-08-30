// Пример использования прямых AI промтов - сценарий встречи игрока у ворот деревни
// Демонстрирует как давать AI НПС прямые инструкции для выполнения сложных сценариев

import com.hollowengineai.mod.integration.HollowEngineScriptAPI
import com.hollowengineai.mod.integration.NPCPersonality
import com.hollowengineai.mod.core.PersonalityType

// === Создаем жителей деревни ===

// Мэр - главный организатор
val mayor by NPCEntity.creating {
    name = "Мэр Эдвард"
    pos = pos(0, 64, 0)
    equipment {
        head = "golden_helmet"
        chest = "leather_chestplate"
    }
}

val smartMayor = HollowEngineScriptAPI.makeNPCSmart(
    mayor.entity,
    NPCPersonality.custom(
        name = "Эдвард",
        personalityType = PersonalityType.DIPLOMATIC,
        traits = mapOf(
            "leadership" to 0.9f,
            "diplomacy" to 0.9f,
            "wisdom" to 0.8f
        ),
        biography = "Мудрый мэр, умеющий организовать любое мероприятие",
        goals = listOf("Обеспечить процветание деревни", "Встречать важных гостей")
    )
)

// Стражи
val guard1 by NPCEntity.creating {
    name = "Страж Маркус"
    pos = pos(-5, 64, 5)
    equipment {
        mainHand = "iron_sword"
        chest = "iron_chestplate"
    }
}

val guard2 by NPCEntity.creating {
    name = "Страж Лена"
    pos = pos(5, 64, 5)
    equipment {
        mainHand = "iron_sword"
        chest = "iron_chestplate"
    }
}

val smartGuard1 = guard1.entity.makeGuard("Маркус", "Опытный страж восточных ворот")
val smartGuard2 = guard2.entity.makeGuard("Лена", "Бдительный страж западных ворот")

// Глашатай
val herald by NPCEntity.creating {
    name = "Глашатай Томас"
    pos = pos(0, 64, 10)
    equipment {
        mainHand = "bell"
        chest = "leather_chestplate"
    }
}

val smartHerald = herald.entity.makeTrader(
    "Томас", 
    "Деревенский глашатай, объявляющий важные новости"
)

// === Функции для управления сценариями ===

/**
 * Организовать торжественную встречу игрока
 */
fun organizeFormalWelcome(playerName: String) {
    println("🎭 Начинается сценарий торжественной встречи игрока $playerName")
    
    // 1. Мэр организует встречу
    val mayorSuccess = HollowEngineScriptAPI.sendAIPrompt(
        mayor.npcId,
        "ВАЖНОЕ ЗАДАНИЕ: Организуй торжественную встречу игрока $playerName у главных ворот деревни. " +
        "Подойди к воротам, подготовь приветственную речь, координируй действия с стражами и глашатаем. " +
        "Веди себя как достойный лидер общины.",
        mapOf(
            "event_type" to "formal_welcome",
            "player_name" to playerName,
            "location" to "main_gates",
            "importance" to "high"
        )
    )
    
    // 2. Стражи получают инструкции по сопровождению
    val guardIds = listOf(guard1.npcId, guard2.npcId)
    val guardsSuccess = HollowEngineScriptAPI.sendGroupAIPrompt(
        guardIds,
        "ПРИКАЗ: Сопроводите мэра к главным воротам для торжественной встречи игрока $playerName. " +
        "Обеспечьте безопасность мероприятия, встаньте в почетный караул, следуйте указаниям мэра. " +
        "Ведите себя дисциплинированно и торжественно.",
        mapOf(
            "event_type" to "escort_duty",
            "player_name" to playerName,
            "mayor_id" to mayor.npcId
        )
    )
    
    // 3. Глашатай объявляет о событии
    val heraldSuccess = HollowEngineScriptAPI.sendAIPrompt(
        herald.npcId,
        "ОБЪЯВЛЕНИЕ: Возвести прибытие почетного гостя $playerName! " +
        "Подойди к воротам, громко объяви о прибытии важного гостя, " +
        "создай торжественную атмосферу своими объявлениями.",
        mapOf(
            "event_type" to "announcement",
            "player_name" to playerName
        )
    )
    
    println("📊 Результаты отправки инструкций:")
    println("   Мэр: ${if (mayorSuccess) "✅ Получил задание" else "❌ Ошибка"}")
    println("   Стражи: ✅ $guardsSuccess из ${guardIds.size} получили приказ")
    println("   Глашатай: ${if (heraldSuccess) "✅ Готов к объявлению" else "❌ Ошибка"}")
}

/**
 * Объявить тревогу в деревне
 */
fun declareEmergency(threat: String) {
    println("🚨 ТРЕВОГА! Объявляется угроза: $threat")
    
    val allNPCIds = listOf(mayor.npcId, guard1.npcId, guard2.npcId, herald.npcId)
    
    val affectedCount = HollowEngineScriptAPI.sendAreaAIPrompt(
        centerX = 0.0, centerY = 64.0, centerZ = 0.0,
        radius = 50.0,
        prompt = "🚨 ТРЕВОГА! Обнаружена угроза: $threat. " +
        "Все жители деревни должны действовать согласно своей роли: " +
        "мэр - организует оборону, стражи - готовятся к бою, " +
        "остальные - укрываются в безопасных местах.",
        context = mapOf(
            "alert_level" to "high",
            "threat_type" to threat,
            "response_required" to true
        )
    )
    
    println("📢 Тревога объявлена $affectedCount НПС в радиусе деревни")
}

/**
 * Устроить праздник в честь игрока
 */
fun celebratePlayer(playerName: String, reason: String) {
    println("🎉 Организуется празднование в честь $playerName по поводу: $reason")
    
    val organizerIds = listOf(mayor.npcId, herald.npcId)
    val celebrationCount = HollowEngineScriptAPI.QuickCommands.organizeCelebration(
        organizerIds,
        "прибытие героя $playerName - $reason",
        "центральная площадь деревни"
    )
    
    // Отдельные инструкции стражам
    HollowEngineScriptAPI.sendGroupAIPrompt(
        listOf(guard1.npcId, guard2.npcId),
        "Примите участие в празднике в честь $playerName. " +
        "Отложите оружие, присоединитесь к празднованию, но сохраняйте бдительность.",
        mapOf("event_type" to "celebration_guard_duty")
    )
    
    println("🎊 Праздник организован! Участвуют $celebrationCount основных организаторов")
}

// === События игры ===

// Игрок подходит к деревне
events.onPlayerEnterArea(pos(0, 64, 0), radius = 30) { player ->
    println("👤 Игрок ${player.name} приближается к деревне")
    
    // Автоматически уведомляем всех AI НПС о приближении игрока
    val allIds = listOf(mayor.npcId, guard1.npcId, guard2.npcId, herald.npcId)
    allIds.forEach { npcId ->
        HollowEngineScriptAPI.notifyNPCAction(
            npcId, 
            "player_approach", 
            "игрок ${player.name} приближается к деревне"
        )
    }
    
    // Если игрок VIP - организуем встречу
    if (player.hasTag("VIP") || player.hasPermission("admin")) {
        delay(2.seconds) {
            organizeFormalWelcome(player.name)
        }
    }
}

// Команды для демонстрации
commands {
    
    command("/village_welcome") { player, args ->
        val targetPlayer = args.getOrElse(0) { player.name }
        organizeFormalWelcome(targetPlayer)
        player.sendMessage("Организована торжественная встреча для $targetPlayer")
    }
    
    command("/village_alert") { player, args ->
        val threat = args.joinToString(" ").ifEmpty { "неизвестная угроза" }
        declareEmergency(threat)
        player.sendMessage("Объявлена тревога: $threat")
    }
    
    command("/village_celebrate") { player, args ->
        val reason = args.joinToString(" ").ifEmpty { "великие подвиги" }
        celebratePlayer(player.name, reason)
        player.sendMessage("Организовано празднование в вашу честь!")
    }
    
    command("/npc_status") { player ->
        val allIds = listOf(mayor.npcId, guard1.npcId, guard2.npcId, herald.npcId)
        val allNames = listOf("Мэр Эдвард", "Страж Маркус", "Страж Лена", "Глашатай Томас")
        
        player.sendMessage("=== Статус AI НПС ===")
        allIds.zip(allNames).forEach { (id, name) ->
            val status = HollowEngineScriptAPI.getAIPromptStatus(id) ?: "Неизвестно"
            player.sendMessage("$name: $status")
        }
    }
    
    command("/cancel_all_prompts") { player ->
        val allIds = listOf(mayor.npcId, guard1.npcId, guard2.npcId, herald.npcId)
        var cancelledCount = 0
        
        allIds.forEach { id ->
            if (HollowEngineScriptAPI.cancelAIPrompts(id)) {
                cancelledCount++
            }
        }
        
        player.sendMessage("Отменено активных промтов: $cancelledCount из ${allIds.size}")
    }
}

// Примеры использования extension функций
schedule {
    // Каждые 10 минут мэр патрулирует деревню
    every(10.minutes) {
        mayor.entity.goTo("центр деревни", "патрулирование и проверка порядка")
    }
    
    // Стражи периодически общаются между собой
    every(15.minutes) {
        guard1.entity.talkTo("Страж Лена", "обсуждение безопасности деревни")
    }
    
    // Глашатай делает объявления
    every(30.minutes) {
        herald.entity.performAction(
            "Сделать объявление для жителей деревни",
            "новости, события, важная информация"
        )
    }
}

println("🏘️ Деревня с AI НПС и системой прямых промтов создана!")
println("📝 Доступные команды:")
println("   /village_welcome [игрок] - организовать встречу")
println("   /village_alert [угроза] - объявить тревогу") 
println("   /village_celebrate [причина] - устроить праздник")
println("   /npc_status - статус всех AI НПС")
println("   /cancel_all_prompts - отменить все активные инструкции")
# Примеры интеграции HollowEngineAI с HollowEngine Legacy

Эта папка содержит примеры скриптов `.se.kts` для HollowEngine Legacy, демонстрирующие интеграцию с HollowEngineAI.

## Как использовать

1. Убедитесь, что установлены оба мода:
   - HollowEngine Legacy
   - HollowEngineAI

2. Скопируйте нужный `.se.kts` файл в папку скриптов вашего мира

3. Перезагрузите скрипты командой `/hollowengine reload` или перезапустите сервер

## Примеры скриптов

### 1. `smart_trader_example.se.kts`
**Полный пример умного торговца**

Демонстрирует:
- Создание НПС с комплексной AI личностью
- Настройку диалогов с AI генерацией
- Торговую систему с AI памятью
- Обработку событий и взаимодействий
- Персонализированные ответы на основе отношений
- Расширенную настройку целей, навыков и воспоминаний

```kotlin
// Основная структура
val trader by NPCEntity.creating { /* настройки НПС */ }
val smartTrader = HollowEngineScriptAPI.makeNPCSmart(trader.entity, personality)
```

### 2. `smart_guard_simple.se.kts`
**Простой пример умного стража**

Демонстрирует:
- Базовое использование готовых личностей
- Простую настройку AI параметров
- Патрулирование с AI влиянием
- Быстрое создание через extension функции

```kotlin
// Упрощенное создание
val smartGuard = guard.entity.makeGuard(name, biography)
```

### 3. `smart_village_community.se.kts`
**Умная деревенская община**

Демонстрирует:
- Создание нескольких AI НПС с разными личностями
- Настройку взаимоотношений между НПС
- Кастомные личности с PersonalityType
- Общие события для группы НПС
- Социальные взаимодействия между AI НПС

### 4. `ai_prompts_village_example.se.kts`
**Деревня с системой прямых AI промтов**

Демонстрирует:
- Отправку прямых инструкций AI НПС ("организуй встречу игрока")
- Групповые промты для координации действий
- Промты по областям для массовых событий
- Extension функции для удобного управления AI
- Готовые сценарии (встреча VIP, тревога, празднование)
- Команды для тестирования AI промтов
- Мониторинг статуса выполнения инструкций

```kotlin
// Отправка прямой инструкции AI
HollowEngineScriptAPI.sendAIPrompt(
    mayorId,
    "Организуй торжественную встречу игрока у ворот"
)

// Групповая инструкция
HollowEngineScriptAPI.sendGroupAIPrompt(
    listOf(guard1Id, guard2Id),
    "Сопроводите мэра и обеспечьте безопасность"
)

// Extension функции
mayor.entity.goTo("главные ворота", "встреча гостя")
guard.entity.talkTo("другой страж", "обсуждение безопасности")
```

## Основные API функции

### Создание умных НПС

### HollowEngineScriptAPI

```kotlin
// Основная функция для добавления AI
makeNPCSmart(npcEntity, personality, npcId?)

// Удаление AI
removeNPCAI(npcId)
removeNPCAI(npcEntity)

// Проверка наличия AI
isNPCSmart(npcId)
isNPCSmart(npcEntity) 

// Получение SmartNPC
getSmartNPC(npcId)
getSmartNPC(npcEntity)

// Настройка AI
configureSmartNPC(npcId) { /* конфигурация */ }

// Уведомление о событиях
notifyNPCAction(npcId, action, details)

// === НОВЫЕ ФУНКЦИИ: Прямые AI промты ===

// Отправить прямую инструкцию одному AI НПС
sendAIPrompt(npcId, "Подойди к воротам и встреть игрока")
sendAIPrompt(npcEntity, "Организуй праздник в деревне")

// Групповые инструкции
sendGroupAIPrompt(listOf(npc1Id, npc2Id), "Соберитесь на площади")

// Инструкции по областям
sendAreaAIPrompt(centerX, centerY, centerZ, radius, "Все укрыться!")
sendAreaAIPrompt(centerPos, radius, "Начать эвакуацию")

// Проверка статуса
getAIPromptStatus(npcId) // "Executing: подойти к воротам"
cancelAIPrompts(npcId)   // отменить активные инструкции
```

### Extension функции для создания

```kotlin
// Быстрое создание с готовыми личностями
npcEntity.makeTrader(name, biography)
npcEntity.makeGuard(name, biography)  
npcEntity.makeScholar(name, biography)
npcEntity.makeBandit(name, biography)
npcEntity.makePeasant(name, biography)

### Extension функции для управления AI

```kotlin
// Прямые промты
npcEntity.sendAIPrompt("Подойди к воротам")
smartNPC.sendPrompt("Начни патрулирование")

// Проверка статуса
npcEntity.getAIStatus()     // получить статус AI
npcEntity.isAISmart()       // проверить наличие AI
npcEntity.cancelAIPrompts() // отменить активные промты

// Удобные команды
npcEntity.goTo("рынок", "купить товары")
npcEntity.talkTo("другой НПС", "обсудить дела")
npcEntity.performAction("приготовить еду")
npcEntity.participateIn("праздник", "музыкант")
```
```

### Готовые личности

```kotlin
// Предустановленные типы
NPCPersonality.friendlyTrader(name, biography)
NPCPersonality.cautiousGuard(name, biography)
NPCPersonality.curiousScholar(name, biography)
NPCPersonality.aggressiveBandit(name, biography)
NPCPersonality.neutralPeasant(name, biography)

// Кастомная личность
NPCPersonality.custom(
    name, personalityType, traits, biography, goals, skills, preferences
)
```

### Конфигурация AI

```kotlin
configureSmartNPC(npcId) {
    addGoals("цель1", "цель2")
    addMemory("категория", "содержание")
    setRelationship("имя", уровень_0_to_1)
    setSkill("навык", уровень_0_to_1)
    setActionPriority("тип_действия", приоритет)
}

### Быстрые команды для типичных сценариев

```kotlin
// Организовать встречу VIP гостя
HollowEngineScriptAPI.QuickCommands.organizeMeeting(
    mayorId = "mayor_robert",
    guardIds = listOf("guard1", "guard2"),
    playerName = "PlayerName",
    meetingPoint = "главные ворота"
)

// Организовать празднование
HollowEngineScriptAPI.QuickCommands.organizeCelebration(
    organizerIds = listOf("mayor", "herald"),
    reason = "победа над драконом",
    location = "центральная площадь"
)

// Объявить тревогу
HollowEngineScriptAPI.QuickCommands.declareAlert(
    npcIds = listOf("all", "village", "npcs"),
    threat = "нападение монстров",
    severity = "high"
)

// Созвать совет
HollowEngineScriptAPI.QuickCommands.callCouncilMeeting(
    councilIds = listOf("mayor", "elder", "captain"),
    topic = "оборона деревни",
    meetingPlace = "ратуша"
)
```
```

## AI генерация в диалогах

Используйте специальные маркеры для AI генерации:

```kotlin
dialogues {
    greeting {
        text = "AI_GENERATED"  // AI сгенерирует на основе личности
        fallbackText = "Резервный текст если AI недоступен"
    }
    
    option("Вопрос") {
        text = "AI_GENERATED_STORY"  // AI сгенерирует историю
        text = "AI_GENERATED_RECOMMENDATION"  // AI даст рекомендацию
    }
}
```

## Синхронизация событий

AI автоматически реагирует на события HollowEngine Legacy:

```kotlin
// Уведомление AI о событиях
onPlayerApproach { 
    HollowEngineScriptAPI.notifyNPCAction(npc, "player_approach", "details")
}

onDamage { damager, damage ->
    HollowEngineScriptAPI.notifyNPCAction(npc, "received_damage", "от: $damager")
}

onItemReceived { item, giver ->
    HollowEngineScriptAPI.notifyNPCAction(npc, "item_received", "предмет: $item")
}

// === НОВОЕ: Автоматические AI реакции ===

// AI может сам принимать решения на основе событий
onPlayerApproach(radius = 10) {
    // AI автоматически решит как реагировать на основе личности
    // Дополнительно можно дать прямую инструкцию:
    if (player.hasTag("VIP")) {
        npc.sendAIPrompt("Организуй торжественную встречу VIP гостя ${player.name}")
    }
}

// Групповые реакции на события
onMobSpawn { mob ->
    if (mob.isHostile) {
        HollowEngineScriptAPI.sendAreaAIPrompt(
            mob.x, mob.y, mob.z, radius = 20.0,
            "Внимание! Замечен враждебный ${mob.type}. Действуйте согласно своим ролям!"
        )
    }
}
```

## Требования

- Minecraft 1.20.1+
- Forge 47.2.0+
- HollowEngine Legacy 2.0+
- HollowEngineAI 1.0+
- Ollama сервер для AI функций (опционально - без него будут использоваться fallback тексты)

## Отладка

Используйте команды для проверки статуса AI:

```kotlin
trader.debug {
    command("/npc_ai_status") { player ->
        val status = smartNPC?.let { ai ->
            "AI активен: ${ai.isActive}, Эмоция: ${ai.currentEmotion}"
        } ?: "AI неактивен"
        player.sendMessage(status)
    }
}
```

## Производительность

- AI НПС потребляют больше ресурсов чем обычные
- Рекомендуется не более 10-20 AI НПС на сервере одновременно
- AI генерация требует подключения к Ollama серверу
- При недоступности AI используются fallback тексты

## Дополнительные возможности

- Персонализированные диалоги на основе истории взаимодействий
- Динамическое изменение поведения на основе эмоций
- Социальные взаимодействия между AI НПС
- Память о событиях и действиях игроков
- Планирование долгосрочных целей
- Адаптация к игровой среде

## Прямые AI промты - новая возможность!

**Самая мощная функция интеграции** - возможность давать AI НПС прямые инструкции:

```kotlin
// Простая инструкция
npc.sendAIPrompt("Подойди к игроку и поприветствуй его")

// Сложный сценарий
mayor.sendAIPrompt(
    "Организуй встречу важного гостя: подойди к воротам, " +
    "подготовь речь, скоординируйся со стражами, " +
    "создай торжественную атмосферу"
)

// Групповая координация
HollowEngineScriptAPI.sendGroupAIPrompt(
    listOf(guard1Id, guard2Id, herald1d),
    "Все соберитесь у фонтана для важного объявления"
)

// Массовые события
HollowEngineScriptAPI.sendAreaAIPrompt(
    villageCenter, radius = 50.0,
    "Объявляется праздник! Все жители присоединяйтесь к празднованию!"
)
```

**AI сам решит КАК выполнить инструкцию** на основе:
- Своей личности и характера
- Текущей ситуации в мире  
- Отношений с другими НПС
- Накопленного опыта и памяти

Это позволяет создавать динамические, непредсказуемые сценарии где AI НПС действуют живо и естественно!

---

Больше примеров и документации доступно в [документации HollowEngineAI](../docs/).
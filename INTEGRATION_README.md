# Интеграция HollowEngineAI с HollowEngine Legacy

## Обзор

Этот документ описывает полную интеграцию мода **HollowEngineAI** с **HollowEngine Legacy**, позволяющую создавать умных НПС с искусственным интеллектом прямо в скриптах HollowEngine Legacy.

## Архитектура интеграции

### Основные компоненты

1. **HollowEngineBridge** - Система-мост для связи AI и HollowEngine НПС
2. **NPCPersonality** - Расширенная система конфигурации личности НПС  
3. **HollowEngineScriptAPI** - Простой API для использования в скриптах
4. **EventSynchronizer** - Система синхронизации событий между системами

### Файловая структура

```
src/main/kotlin/com/hollowengineai/mod/integration/
├── HollowEngineBridge.kt      # Основная система-мост
├── NPCPersonality.kt          # Конфигурация личностей НПС
├── HollowEngineScriptAPI.kt   # API для скриптов
└── EventSynchronizer.kt       # Синхронизация событий

examples/
├── smart_trader_example.se.kts       # Полный пример умного торговца
├── smart_guard_simple.se.kts         # Простой пример стража
├── smart_village_community.se.kts    # Деревенская община с AI
└── README.md                         # Руководство по примерам

src/test/kotlin/com/hollowengineai/mod/integration/
└── IntegrationTest.kt                # Тесты интеграции
```

## Как это работает

### 1. Создание умного НПС

В скрипте HollowEngine Legacy (.se.kts):

```kotlin
import com.hollowengineai.mod.integration.HollowEngineScriptAPI
import com.hollowengineai.mod.integration.NPCPersonality

// 1. Создаем обычного НПС через HollowEngine Legacy
val trader by NPCEntity.creating {
    name = "Торговец Алекс"
    pos = pos(100, 64, 200)
    // ... другие параметры
}

// 2. Делаем НПС умным - добавляем AI
val smartTrader = HollowEngineScriptAPI.makeNPCSmart(
    npcEntity = trader.entity,
    personality = NPCPersonality.friendlyTrader(
        name = "Алекс",
        biography = "Опытный торговец, который много путешествовал..."
    )
)
```

### 2. Настройка AI поведения

```kotlin
// Дополнительная настройка AI
HollowEngineScriptAPI.configureSmartNPC(trader.npcId) {
    addGoals("Найти редкие товары", "Завести друзей среди клиентов")
    addMemory("past_adventures", "Помню, как в пещерах драконов...")
    setRelationship("Гильдия Торговцев", 0.8f)
    setSkill("magical_appraisal", 0.9f)
}
```

### 3. AI генерация в диалогах

```kotlin
trader.dialogues {
    greeting {
        text = "AI_GENERATED"  // AI сгенерирует на основе личности
        fallbackText = "Резервный текст если AI недоступен"
        
        onStart {
            HollowEngineScriptAPI.notifyNPCAction(trader.entity, "dialogue_started")
        }
    }
}
```

### 4. Синхронизация событий

```kotlin
trader.events {
    onPlayerApproach(radius = 5) {
        HollowEngineScriptAPI.notifyNPCAction(
            trader.entity,
            "player_approach", 
            "player_nearby"
        )
    }
    
    onItemReceived { item, giver ->
        HollowEngineScriptAPI.notifyNPCAction(
            trader.entity,
            "item_received",
            "получил $item от ${giver.name}"
        )
    }
}
```

## Готовые типы личностей

### Предустановленные личности

1. **NPCPersonality.friendlyTrader()** - Дружелюбный торговец
2. **NPCPersonality.cautiousGuard()** - Осторожный страж  
3. **NPCPersonality.curiousScholar()** - Любопытный ученый
4. **NPCPersonality.aggressiveBandit()** - Агрессивный бандит
5. **NPCPersonality.neutralPeasant()** - Нейтральный крестьянин

### Быстрое создание через extension функции

```kotlin
// Создание умного торговца
val smartTrader = npcEntity.makeTrader("Имя", "Биография")

// Создание умного стража
val smartGuard = npcEntity.makeGuard("Имя", "Биография")

// И так далее...
val smartScholar = npcEntity.makeScholar("Имя", "Биография")
val smartBandit = npcEntity.makeBandit("Имя", "Биография") 
val smartPeasant = npcEntity.makePeasant("Имя", "Биография")
```

### Кастомные личности

```kotlin
val personality = NPCPersonality.custom(
    name = "Уникальный НПС",
    personalityType = PersonalityType.DIPLOMATIC,
    traits = mapOf(
        "intelligence" to 0.9f,
        "charisma" to 0.8f,
        "patience" to 0.7f
    ),
    biography = "Детальная биография...",
    goals = listOf("Цель 1", "Цель 2"),
    skills = mapOf("diplomacy" to 0.9f),
    preferences = mapOf("style" to "formal")
)
```

## AI возможности

### Что AI может делать:

1. **Генерировать персонализированные диалоги** на основе:
   - Личности НПС
   - Истории взаимодействий с игроком
   - Текущих эмоций и целей
   - Отношений с игроком

2. **Запоминать и анализировать**:
   - Все взаимодействия с игроками
   - События в игровом мире
   - Выполненные действия
   - Социальные связи

3. **Принимать решения** на основе:
   - Черт характера
   - Текущих целей
   - Эмоционального состояния
   - Накопленного опыта

4. **Адаптироваться** к игровой среде:
   - Менять поведение в зависимости от ситуации
   - Развивать отношения с игроками
   - Обучаться на основе опыта

### Типы AI событий:

- **player_interaction** - Взаимодействие с игроком
- **dialogue_started/ended** - Начало/конец диалога
- **item_given/taken** - Получение/отдача предметов
- **combat_started/ended** - Начало/конец боя
- **movement** - Перемещение НПС
- **task_assigned/completed** - Назначение/выполнение задач
- **environment_change** - Изменения в окружении

## Требования и совместимость

### Системные требования:

- Minecraft 1.20.1+
- Forge 47.2.0+
- HollowEngine Legacy 2.0+
- HollowEngineAI 1.0+
- Java 17+
- Kotlin 1.9+

### Опциональные компоненты:

- **Ollama сервер** - Для полноценного AI (без него используются fallback тексты)
- **База данных** - Для сохранения памяти НПС между сессиями

### Производительность:

- Рекомендуется не более 10-20 AI НПС одновременно
- AI генерация требует подключения к Ollama
- Память НПС сохраняется в базе данных

## Инициализация и настройка

### Автоматическая инициализация

Интеграция инициализируется автоматически при загрузке мода:

```kotlin
// В HollowEngineAIMod.kt
// 7. Инициализируем интеграцию с HollowEngine Legacy
try {
    LOGGER.info("Инициализация интеграции с HollowEngine Legacy...")
    // EventSynchronizer и HollowEngineBridge готовы к работе
    LOGGER.info("Интеграция с HollowEngine Legacy инициализирована успешно")
} catch (e: Exception) {
    LOGGER.warn("Не удалось инициализировать интеграцию с HollowEngine Legacy", e)
}
```

### Проверка статуса

```kotlin
// Проверка доступности интеграции
if (HollowEngineScriptAPI.getAllSmartNPCs().isEmpty()) {
    println("Нет активных AI НПС")
} else {
    println("Активных AI НПС: ${HollowEngineScriptAPI.getAllSmartNPCs().size}")
}
```

## Отладка и диагностика

### Логирование

Все компоненты интеграции ведут подробные логи:

```
[HollowEngineBridge] Enhancing HollowEngine NPC trader_alex with AI
[EventSynchronizer] Processing HollowEngine event: player_interaction for NPC trader_alex
[HollowEngineScriptAPI] Making NPC smart: Алекс (ID: trader_alex)
```

### Команды отладки

```kotlin
trader.debug {
    command("/npc_ai_status") { player ->
        if (player.hasPermission("admin")) {
            val status = smartTrader?.let { ai ->
                """
                AI Status для ${trader.name}:
                - Активен: ${ai.isActive}
                - Текущая эмоция: ${ai.currentEmotion}  
                - Отношение к ${player.name}: ${ai.memory.getRelationship(player.name)}
                """.trimIndent()
            } ?: "AI не активен"
            
            player.sendMessage(status)
        }
    }
}
```

## Примеры использования

Полные рабочие примеры доступны в папке `examples/`:

1. **smart_trader_example.se.kts** - Комплексный торговец с AI
2. **smart_guard_simple.se.kts** - Простой страж с базовым AI
3. **smart_village_community.se.kts** - Целая деревня с AI жителями

## Расширение функциональности

### Добавление новых типов личностей

```kotlin
// В NPCPersonality.kt можно добавить новые функции
fun mysteriosWizard(name: String, biography: String = ""): NPCPersonality {
    return NPCPersonality(
        name = name,
        personalityType = PersonalityType.MYSTERIOUS,
        traits = mapOf(
            "wisdom" to 0.95f,
            "secrecy" to 0.8f,
            "magical_power" to 0.9f
        ),
        // ... остальные параметры
    )
}
```

### Регистрация пользовательских обработчиков событий

```kotlin
EventSynchronizer.registerHollowEngineEventHandler("custom_event") { npcId, data ->
    // Ваша логика обработки
}

EventSynchronizer.registerAIEventHandler("custom_ai_event") { smartNPC, event ->
    // Обработка AI событий
}
```

## Техническая реализация

### Архитектурные решения:

1. **Система-мост** - Не заменяет функциональность HollowEngine Legacy, а дополняет ее
2. **Двухэтапное создание** - Сначала HollowEngine НПС, затем привязка AI
3. **Система привязок** - Отслеживание связи между системами через ID
4. **Синхронизация событий** - Обмен информацией между AI и HollowEngine
5. **Graceful degradation** - Работа без AI при недоступности Ollama

### Потоки выполнения:

1. **Инициализация**: HollowEngine Legacy → HollowEngineAI → Интеграция
2. **Создание НПС**: HollowEngine создание → AI привязка → Конфигурация
3. **Событие**: HollowEngine → EventSynchronizer → AI обработка
4. **AI решение**: AI → EventSynchronizer → HollowEngine действие

## Поддержка и развитие

Интеграция предоставляет полную совместимость между HollowEngine Legacy и HollowEngineAI, позволяя создавать по-настоящему умных НПС с минимальными изменениями в существующих скриптах.

Для получения дополнительной поддержки обращайтесь к документации каждого из модов или создавайте issues в соответствующих репозиториях.
# Руководство пользователя HollowEngineAI

Простое руководство по созданию умных НПС с искусственным интеллектом.

## Быстрый старт

### 1. Создать обычного НПС
```kotlin
val npc by NPCEntity.creating {
    name = "Торговец Боб"
    pos = pos(100, 64, 200)
}
```

### 2. Сделать его умным
```kotlin
import com.hollowengineai.mod.integration.HollowEngineScriptAPI
import com.hollowengineai.mod.integration.NPCPersonality

val smartNPC = HollowEngineScriptAPI.makeNPCSmart(
    npc.entity,
    NPCPersonality.friendlyTrader("Боб", "Опытный торговец")
)
```

### 3. Готово!
Теперь НПС имеет AI и будет:
- Генерировать персональные диалоги
- Запоминать взаимодействия с игроками
- Принимать решения на основе характера
- Реагировать на события в мире

## Готовые типы личностей

```kotlin
// Дружелюбный торговец
NPCPersonality.friendlyTrader(name, biography)

// Осторожный страж  
NPCPersonality.cautiousGuard(name, biography)

// Любопытный ученый
NPCPersonality.curiousScholar(name, biography)

// Агрессивный бандит
NPCPersonality.aggressiveBandit(name, biography)

// Обычный крестьянин
NPCPersonality.neutralPeasant(name, biography)
```

## Быстрое создание через extension функции

```kotlin
val smartTrader = npc.entity.makeTrader("Имя", "Биография")
val smartGuard = npc.entity.makeGuard("Имя", "Биография") 
val smartScholar = npc.entity.makeScholar("Имя", "Биография")
```

## Прямые AI инструкции

**Главная фишка интеграции** - можете давать AI НПС прямые команды:

```kotlin
// Простая команда
npc.sendAIPrompt("Подойди к игроку и поприветствуй его")

// Сложный сценарий
mayor.sendAIPrompt("Организуй встречу VIP гостя у ворот - позови стражей, подготовь речь")

// Групповая команда
HollowEngineScriptAPI.sendGroupAIPrompt(
    listOf(guard1Id, guard2Id),
    "Все стражи соберитесь у ворот для важного объявления"
)

// Массовое событие
HollowEngineScriptAPI.sendAreaAIPrompt(
    centerX, centerY, centerZ, radius = 30.0,
    "Объявляется праздник! Все жители присоединяйтесь к празднованию!"
)
```

## Удобные команды

```kotlin
// Движение
npc.goTo("рынок", "купить продукты")

// Общение  
npc.talkTo("другой НПС", "обсудить торговлю")

// Действие
npc.performAction("приготовить ужин")

// Участие в событии
npc.participateIn("деревенский праздник", "музыкант")
```

## Настройка AI поведения

```kotlin
HollowEngineScriptAPI.configureSmartNPC(npcId) {
    // Добавить цели
    addGoals("Найти редкие товары", "Завести друзей")
    
    // Добавить воспоминания
    addMemory("adventures", "Помню свое путешествие в горы...")
    
    // Настроить отношения
    setRelationship("Гильдия Торговцев", 0.8f)
    setRelationship("Воры", -0.6f)
    
    // Установить навыки  
    setSkill("торговля", 0.9f)
    setSkill("убеждение", 0.7f)
}
```

## AI в диалогах

```kotlin
trader.dialogues {
    greeting {
        text = "AI_GENERATED"  // AI сгенерирует на основе личности
        fallbackText = "Резервный текст если AI недоступен"
    }
    
    option("Расскажи историю") {
        text = "AI_GENERATED_STORY"  // AI расскажет историю
        fallbackText = "У меня много историй..."
    }
}
```

## Мониторинг AI

```kotlin
// Проверить статус
val status = npc.getAIStatus()  // "Executing: подойти к игроку"

// Проверить есть ли AI
if (npc.isAISmart()) {
    println("У НПС есть AI")
}

// Отменить активные команды
npc.cancelAIPrompts()

// Убрать AI вообще
HollowEngineScriptAPI.removeNPCAI(npcId)
```

## Готовые сценарии

```kotlin
// Встреча VIP гостя
HollowEngineScriptAPI.QuickCommands.organizeMeeting(
    mayorId, guardIds, playerName, "главные ворота"
)

// Праздник
HollowEngineScriptAPI.QuickCommands.organizeCelebration(
    organizerIds, "победа над драконом", "центральная площадь"
)

// Тревога
HollowEngineScriptAPI.QuickCommands.declareAlert(
    allNPCIds, "нападение монстров", "high"
)
```

## Автоматические реакции

AI НПС автоматически реагируют на события:
- Приближение игроков
- Получение предметов  
- Начало боя
- Изменения в окружении

Дополнительно можно уведомлять:
```kotlin
trader.events {
    onPlayerApproach {
        HollowEngineScriptAPI.notifyNPCAction(trader.entity, "player_approach", "игрок подошел")
    }
}
```

## Советы

1. **Начните с готовых личностей** - они уже настроены
2. **Используйте прямые промты** для сложных сценариев  
3. **AI сам решает КАК** выполнить инструкцию
4. **Не более 20 AI НПС** одновременно для производительности
5. **Проверяйте логи** при проблемах с AI

## Полный пример

Смотрите `examples/ai_prompts_village_example.se.kts` - там показано как создать целую деревню с умными жителями и сложными сценариями!
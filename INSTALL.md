# Установка HollowEngineAI интеграции с HollowEngine Legacy

## Требования

- **Minecraft** 1.20.1+
- **Forge** 47.2.0+
- **HollowEngine Legacy** 2.0+ (должен быть установлен и работает)
- **Java** 17+

## Шаг 1: Сборка мода

**На компьютере (рекомендуется):**
```bash
git clone <ваш-репозиторий>
cd HollowEngineAI
./gradlew build
```

**В Termux (Android):**
```bash
pkg install openjdk-17 git
git clone <ваш-репозиторий>
cd HollowEngineAI
./gradlew build --no-daemon
```

Готовый мод будет в `build/libs/HollowEngineAI-<версия>.jar`

## Шаг 2: Установка HollowEngineAI мода

1. Скачайте файл `HollowEngineAI-1.0.0.jar`
2. Поместите его в папку `mods/` вашего сервера/клиента
3. Убедитесь что HollowEngine Legacy также установлен

## Шаг 2: Установка Ollama (опционально)

Для полного AI функционала нужен Ollama сервер:

1. Скачайте Ollama с https://ollama.ai
2. Установите модель: `ollama pull llama3.1`
3. Запустите сервер: `ollama serve`

**Без Ollama** мод будет работать с заглушками вместо AI генерации.

## Шаг 3: Настройка конфигурации

Отредактируйте файл `config/hollowengineai-common.toml`:

```toml
[ai]
    # URL Ollama сервера  
    ollama_url = "http://localhost:11434"
    # Модель для использования
    default_model = "llama3.1"
    # Максимум AI НПС одновременно
    max_ai_npcs = 20

[performance]
    # Включить кеширование
    enable_caching = true
    # Размер пула потоков
    thread_pool_size = 4
```

## Шаг 4: Проверка работы

1. Запустите сервер/клиент
2. Создайте тестовый скрипт в папке скриптов мира
3. Проверьте логи на наличие ошибок

## Шаг 5: Создание первого умного НПС

Создайте файл `test.se.kts` в папке скриптов:

```kotlin
import com.hollowengineai.mod.integration.HollowEngineScriptAPI
import com.hollowengineai.mod.integration.NPCPersonality

val trader by NPCEntity.creating {
    name = "Умный Торговец"
    pos = pos(0, 64, 0)
}

val smartTrader = HollowEngineScriptAPI.makeNPCSmart(
    trader.entity,
    NPCPersonality.friendlyTrader("Алекс", "Дружелюбный торговец")
)

println("Умный НПС создан: ${smartTrader != null}")
```

Перезагрузите скрипты командой `/hollowengine reload`

## Устранение проблем

**НПС не становится умным:**
- Проверьте что HollowEngine Legacy установлен
- Проверьте логи на ошибки интеграции

**AI не работает:**  
- Убедитесь что Ollama запущен на правильном порту
- Проверьте что модель загружена: `ollama list`

**Низкая производительность:**
- Уменьшите `max_ai_npcs` в конфиге
- Увеличьте `thread_pool_size`

## Готово!

Интеграция установлена. Теперь можно создавать умных НПС в ваших скриптах HollowEngine Legacy!

Подробное руководство по использованию смотрите в `USER_GUIDE.md`
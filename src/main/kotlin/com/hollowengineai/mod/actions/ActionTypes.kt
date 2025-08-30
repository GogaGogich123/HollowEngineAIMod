package com.hollowengineai.mod.actions

/**
 * Типы действий которые может выполнять AI НПС
 * 
 * Каждое действие имеет описание для LLM и набор параметров
 */
enum class ActionType(
    val description: String,
    val requiredParams: List<String> = emptyList(),
    val optionalParams: List<String> = emptyList()
) {
    // === БАЗОВЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Сказать что-то в чат
     * Параметры: message (обязательно), target (опционально)
     */
    SPEAK(
        description = "Speak a message to nearby players or a specific target",
        requiredParams = listOf("message"),
        optionalParams = listOf("target", "volume")
    ),
    
    /**
     * Двигаться к цели или в направлении
     * Параметры: target/direction/coordinates
     */
    MOVE(
        description = "Move towards a target, in a direction, or to specific coordinates",
        optionalParams = listOf("target", "direction", "x", "y", "z", "speed")
    ),
    
    /**
     * Посмотреть на цель
     * Параметры: target (обязательно)
     */
    LOOK_AT(
        description = "Look at a specific target (player, entity, or coordinates)",
        requiredParams = listOf("target"),
        optionalParams = listOf("x", "y", "z")
    ),
    
    /**
     * Подождать определенное время
     * Параметры: duration (в миллисекундах)
     */
    WAIT(
        description = "Wait for a specified duration",
        requiredParams = listOf("duration")
    ),
    
    /**
     * Воспроизвести анимацию
     * Параметры: animation (название анимации)
     */
    ANIMATE(
        description = "Play a specific animation",
        requiredParams = listOf("animation"),
        optionalParams = listOf("duration", "loop")
    ),
    
    // === ВЗАИМОДЕЙСТВИЕ С МИРОМ ===
    
    /**
     * Взаимодействовать с блоком
     * Параметры: x, y, z, action
     */
    INTERACT_BLOCK(
        description = "Interact with a block at specific coordinates",
        requiredParams = listOf("x", "y", "z"),
        optionalParams = listOf("action", "item")
    ),
    
    /**
     * Взаимодействовать с предметом
     * Параметры: item, action
     */
    INTERACT_ITEM(
        description = "Interact with an item in inventory or world",
        requiredParams = listOf("item"),
        optionalParams = listOf("action", "quantity")
    ),
    
    /**
     * Построить что-то
     * Параметры: block, x, y, z
     */
    BUILD(
        description = "Place a block at specific coordinates",
        requiredParams = listOf("block", "x", "y", "z"),
        optionalParams = listOf("direction")
    ),
    
    /**
     * Сломать блок
     * Параметры: x, y, z
     */
    BREAK(
        description = "Break a block at specific coordinates",
        requiredParams = listOf("x", "y", "z"),
        optionalParams = listOf("tool")
    ),
    
    // === СОЦИАЛЬНЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Поприветствовать игрока
     * Параметры: target
     */
    GREET(
        description = "Greet a specific player or nearby players",
        optionalParams = listOf("target", "greeting_type")
    ),
    
    /**
     * Попрощаться с игроком
     * Параметры: target
     */
    FAREWELL(
        description = "Say goodbye to a specific player or nearby players",
        optionalParams = listOf("target", "farewell_type")
    ),
    
    /**
     * Предложить торговлю
     * Параметры: target, item, price
     */
    OFFER_TRADE(
        description = "Offer to trade with a player",
        requiredParams = listOf("target"),
        optionalParams = listOf("item", "price", "quantity")
    ),
    
    /**
     * Попросить о помощи
     * Параметры: target, request
     */
    REQUEST_HELP(
        description = "Ask a player for help with something",
        requiredParams = listOf("target", "request")
    ),
    
    /**
     * Предложить помощь
     * Параметры: target, offer
     */
    OFFER_HELP(
        description = "Offer help to a player",
        requiredParams = listOf("target"),
        optionalParams = listOf("offer")
    ),
    
    // === ЭМОЦИОНАЛЬНЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Выразить эмоцию
     * Параметры: emotion, intensity
     */
    EXPRESS_EMOTION(
        description = "Express a specific emotion through speech and animation",
        requiredParams = listOf("emotion"),
        optionalParams = listOf("intensity", "target")
    ),
    
    /**
     * Отреагировать на событие
     * Параметры: reaction, target
     */
    REACT(
        description = "React to something that happened",
        requiredParams = listOf("reaction"),
        optionalParams = listOf("target", "intensity")
    ),
    
    // === ИССЛЕДОВАНИЕ ===
    
    /**
     * Исследовать область
     * Параметры: radius, duration
     */
    EXPLORE(
        description = "Explore the surrounding area",
        optionalParams = listOf("radius", "duration", "direction")
    ),
    
    /**
     * Найти что-то
     * Параметры: target_type
     */
    SEARCH(
        description = "Search for something specific in the area",
        requiredParams = listOf("target_type"),
        optionalParams = listOf("radius", "duration")
    ),
    
    /**
     * Изучить объект
     * Параметры: target
     */
    EXAMINE(
        description = "Carefully examine an object or entity",
        requiredParams = listOf("target"),
        optionalParams = listOf("duration")
    ),
    
    // === ЦЕЛЕВЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Установить цель
     * Параметры: goal, priority
     */
    SET_GOAL(
        description = "Set a new goal or objective",
        requiredParams = listOf("goal"),
        optionalParams = listOf("priority", "deadline")
    ),
    
    /**
     * Следовать за игроком
     * Параметры: target, distance
     */
    FOLLOW(
        description = "Follow a specific player",
        requiredParams = listOf("target"),
        optionalParams = listOf("distance", "duration")
    ),
    
    /**
     * Охранять область
     * Параметры: x, y, z, radius
     */
    GUARD(
        description = "Guard a specific area or location",
        requiredParams = listOf("x", "y", "z"),
        optionalParams = listOf("radius", "duration")
    ),
    
    // === ИНФОРМАЦИОННЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Поделиться информацией
     * Параметры: target, info
     */
    SHARE_INFO(
        description = "Share information with a player",
        requiredParams = listOf("target", "info")
    ),
    
    /**
     * Запросить информацию
     * Параметры: target, question
     */
    ASK_INFO(
        description = "Ask a player for information",
        requiredParams = listOf("target", "question")
    ),
    
    /**
     * Запомнить что-то
     * Параметры: information, importance
     */
    REMEMBER(
        description = "Store something in memory for later",
        requiredParams = listOf("information"),
        optionalParams = listOf("importance", "category")
    ),
    
    // === СЛУЖЕБНЫЕ ДЕЙСТВИЯ ===
    
    /**
     * Обновить состояние
     * Параметры: state
     */
    UPDATE_STATE(
        description = "Update internal state",
        requiredParams = listOf("state")
    ),
    
    /**
     * Отладочное сообщение
     * Параметры: message
     */
    DEBUG(
        description = "Output debug information",
        requiredParams = listOf("message")
    ),
    
    /**
     * Пользовательское действие
     * Параметры: любые
     */
    CUSTOM(
        description = "Custom user-defined action",
        optionalParams = listOf("action", "parameters", "target")
    );
    
    /**
     * Проверить валидность параметров действия
     */
    fun validateParameters(parameters: Map<String, String>): ValidationResult {
        val missingRequired = requiredParams.filter { param ->
            !parameters.containsKey(param) || parameters[param].isNullOrBlank()
        }
        
        if (missingRequired.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                error = "Missing required parameters: ${missingRequired.joinToString(", ")}"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Получить список всех поддерживаемых параметров
     */
    fun getAllParameters(): List<String> {
        return requiredParams + optionalParams
    }
}

// Класс Action перенесен в Action.kt чтобы избежать конфликтов имен

/**
 * Приоритет действия
 */
enum class ActionPriority(val value: Int) {
    CRITICAL(100),
    HIGH(75),
    NORMAL(50),
    LOW(25),
    BACKGROUND(1);
    
    companion object {
        fun fromValue(value: Int): ActionPriority {
            return values().minByOrNull { kotlin.math.abs(it.value - value) } ?: NORMAL
        }
    }
}

/**
 * Результат валидации действия
 */
data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
)

/**
 * Контекст выполнения действия
 */
data class ActionContext(
    val executor: Any, // SmartNPC
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

// Класс ActionResult перенесен в ActionExecutor.kt чтобы избежать конфликтов имен
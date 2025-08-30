package com.hollowengineai.mod.core

/**
 * Состояния НПС в системе
 */
enum class NPCState {
    /** НПС создан но не активен */
    IDLE,
    
    /** НПС активен и обрабатывает AI */
    ACTIVE,
    
    /** НПС временно неактивен (LOD оптимизация) */
    INACTIVE,
    
    /** НПС в процессе выгрузки */
    STOPPING,
    
    /** НПС выполняет важное действие */
    BUSY,
    
    /** НПС в режиме диалога с игроком */
    INTERACTING,
    
    /** НПС в боевом режиме */
    COMBAT,
    
    /** НПС торгует */
    TRADING,
    
    /** НПС исследует территорию */
    EXPLORING,
    
    // Дополнительные состояния для продвинутых ИИ систем
    /** НПС в бою/сражении */
    FIGHTING,
    
    /** НПС разговаривает */
    TALKING,
    
    /** НПС что-то создает/крафтит */
    CRAFTING,
    
    /** НПС патрулирует */
    PATROLLING,
    
    /** НПС следует за кем-то */
    FOLLOWING,
    
    /** НПС убегает/спасается */
    FLEEING,
    
    /** НПС спит или отдыхает */
    SLEEPING
}

/**
 * Эмоциональные состояния НПС
 */
enum class EmotionalState {
    /** Нейтральное состояние */
    NEUTRAL,
    
    /** Счастливый */
    HAPPY,
    
    /** Грустный */
    SAD,
    
    /** Злой */
    ANGRY,
    
    /** Возбужденный/воодушевленный */
    EXCITED,
    
    /** Любопытный */
    CURIOUS,
    
    /** Довольный */
    CONTENT,
    
    /** Испуганный */
    SCARED,
    
    /** Удивленный */
    SURPRISED,
    
    /** Скучающий */
    BORED,
    
    /** Сосредоточенный */
    FOCUSED,
    
    /** Усталый */
    TIRED
}

/**
 * Типы личности НПС
 */
enum class PersonalityType(
    val description: String,
    val traits: PersonalityTraits
) {
    MERCHANT(
        "Friendly trader focused on commerce and profit",
        PersonalityTraits(
            friendliness = 0.8f,
            curiosity = 0.6f,
            aggressiveness = 0.2f,
            intelligence = 0.7f,
            creativity = 0.5f,
            patience = 0.8f,
            boldness = 0.6f,
            empathy = 0.7f
        )
    ),
    
    GUARD(
        "Protective warrior focused on security and order",
        PersonalityTraits(
            friendliness = 0.5f,
            curiosity = 0.4f,
            aggressiveness = 0.7f,
            intelligence = 0.6f,
            creativity = 0.3f,
            patience = 0.6f,
            boldness = 0.9f,
            empathy = 0.4f
        )
    ),
    
    SCHOLAR(
        "Intellectual researcher focused on knowledge and learning",
        PersonalityTraits(
            friendliness = 0.6f,
            curiosity = 0.9f,
            aggressiveness = 0.1f,
            intelligence = 0.9f,
            creativity = 0.8f,
            patience = 0.8f,
            boldness = 0.3f,
            empathy = 0.6f
        )
    ),
    
    EXPLORER(
        "Adventurous wanderer focused on discovery and freedom",
        PersonalityTraits(
            friendliness = 0.7f,
            curiosity = 0.9f,
            aggressiveness = 0.4f,
            intelligence = 0.7f,
            creativity = 0.7f,
            patience = 0.4f,
            boldness = 0.8f,
            empathy = 0.5f
        )
    ),
    
    ARTISAN(
        "Creative craftsperson focused on creation and beauty",
        PersonalityTraits(
            friendliness = 0.7f,
            curiosity = 0.7f,
            aggressiveness = 0.3f,
            intelligence = 0.6f,
            creativity = 0.9f,
            patience = 0.9f,
            boldness = 0.4f,
            empathy = 0.6f
        )
    ),
    
    HERMIT(
        "Reclusive individual focused on solitude and reflection",
        PersonalityTraits(
            friendliness = 0.3f,
            curiosity = 0.6f,
            aggressiveness = 0.2f,
            intelligence = 0.8f,
            creativity = 0.7f,
            patience = 0.9f,
            boldness = 0.2f,
            empathy = 0.4f
        )
    ),
    
    ENTERTAINER(
        "Charismatic performer focused on joy and social interaction",
        PersonalityTraits(
            friendliness = 0.9f,
            curiosity = 0.6f,
            aggressiveness = 0.2f,
            intelligence = 0.6f,
            creativity = 0.8f,
            patience = 0.5f,
            boldness = 0.8f,
            empathy = 0.8f
        )
    ),
    
    NOBLE(
        "Aristocratic leader focused on honor and authority",
        PersonalityTraits(
            friendliness = 0.6f,
            curiosity = 0.5f,
            aggressiveness = 0.5f,
            intelligence = 0.7f,
            creativity = 0.6f,
            patience = 0.6f,
            boldness = 0.7f,
            empathy = 0.5f
        )
    ),
    
    FARMER(
        "Hardworking cultivator focused on growth and nurturing",
        PersonalityTraits(
            friendliness = 0.8f,
            curiosity = 0.4f,
            aggressiveness = 0.3f,
            intelligence = 0.5f,
            creativity = 0.4f,
            patience = 0.9f,
            boldness = 0.3f,
            empathy = 0.7f
        )
    ),
    
    MYSTIC(
        "Spiritual seeker focused on magic and the unknown",
        PersonalityTraits(
            friendliness = 0.5f,
            curiosity = 0.8f,
            aggressiveness = 0.2f,
            intelligence = 0.8f,
            creativity = 0.9f,
            patience = 0.7f,
            boldness = 0.5f,
            empathy = 0.6f
        )
    );
    
    /**
     * Создать копию с модифицированными чертами
     */
    fun copy(traits: PersonalityTraits): PersonalityType {
        return PersonalityType.valueOf(this.name).apply {
            // Нельзя изменить enum, но можно использовать в фабричном методе
        }
    }
}

/**
 * Черты личности НПС
 * Все значения от 0.0 до 1.0
 */
data class PersonalityTraits(
    /** Дружелюбность - склонность к позитивным социальным взаимодействиям */
    val friendliness: Float,
    
    /** Любопытство - склонность к исследованию и изучению */
    val curiosity: Float,
    
    /** Агрессивность - склонность к конфликтам и доминированию */
    val aggressiveness: Float,
    
    /** Интеллект - способность к сложному анализу и решению проблем */
    val intelligence: Float,
    
    /** Креативность - способность к нестандартным решениям и творчеству */
    val creativity: Float,
    
    /** Терпеливость - способность ждать и не спешить */
    val patience: Float,
    
    /** Смелость - готовность идти на риск */
    val boldness: Float,
    
    /** Эмпатия - способность понимать чувства других */
    val empathy: Float
) {
    init {
        // Валидация значений
        require(friendliness in 0f..1f) { "Friendliness must be between 0 and 1" }
        require(curiosity in 0f..1f) { "Curiosity must be between 0 and 1" }
        require(aggressiveness in 0f..1f) { "Aggressiveness must be between 0 and 1" }
        require(intelligence in 0f..1f) { "Intelligence must be between 0 and 1" }
        require(creativity in 0f..1f) { "Creativity must be between 0 and 1" }
        require(patience in 0f..1f) { "Patience must be between 0 and 1" }
        require(boldness in 0f..1f) { "Boldness must be between 0 and 1" }
        require(empathy in 0f..1f) { "Empathy must be between 0 and 1" }
    }
    
    /**
     * Получить доминирующую черту личности
     */
    fun getDominantTrait(): String {
        val traits = mapOf(
            "friendly" to friendliness,
            "curious" to curiosity,
            "aggressive" to aggressiveness,
            "intelligent" to intelligence,
            "creative" to creativity,
            "patient" to patience,
            "bold" to boldness,
            "empathetic" to empathy
        )
        
        return traits.maxByOrNull { it.value }?.key ?: "balanced"
    }
    
    /**
     * Вычислить совместимость с другим НПС
     */
    fun getCompatibilityWith(other: PersonalityTraits): Float {
        // Простая формула совместимости
        val friendlinessCompat = 1f - kotlin.math.abs(friendliness - other.friendliness)
        val aggressivenessCompat = 1f - kotlin.math.abs(aggressiveness - other.aggressiveness)
        val empathyCompat = 1f - kotlin.math.abs(empathy - other.empathy)
        
        return (friendlinessCompat + aggressivenessCompat + empathyCompat) / 3f
    }
    
    /**
     * Получить описание личности
     */
    fun getDescription(): String {
        val dominantTrait = getDominantTrait()
        val level = when {
            getTraitValue(dominantTrait) > 0.8f -> "very"
            getTraitValue(dominantTrait) > 0.6f -> "quite"
            else -> "somewhat"
        }
        
        return "A $level $dominantTrait individual"
    }
    
    private fun getTraitValue(trait: String): Float {
        return when (trait) {
            "friendly" -> friendliness
            "curious" -> curiosity
            "aggressive" -> aggressiveness
            "intelligent" -> intelligence
            "creative" -> creativity
            "patient" -> patience
            "bold" -> boldness
            "empathetic" -> empathy
            else -> 0.5f
        }
    }
}

/**
 * Уровни LOD (Level of Detail) для оптимизации производительности
 */
enum class LODLevel {
    /** Полная обработка AI - НПС рядом с игроками */
    HIGH,
    
    /** Средняя обработка - НПС в загруженных чанках */
    MEDIUM,
    
    /** Минимальная обработка - НПС далеко от игроков */
    LOW,
    
    /** Только базовое состояние - НПС в незагруженных чанках */
    MINIMAL
}

/**
 * Приоритеты действий НПС
 */
enum class ActionPriority {
    /** Критическая важность - безопасность, здоровье */
    CRITICAL,
    
    /** Высокая важность - основные цели */
    HIGH,
    
    /** Нормальная важность - обычные действия */
    NORMAL,
    
    /** Низкая важность - случайные действия */
    LOW,
    
    /** Фоновые задачи */
    BACKGROUND
}

/**
 * Типы отношений между НПС и игроками
 */
enum class RelationshipType {
    /** Незнаком */
    STRANGER,
    
    /** Знакомый */
    ACQUAINTANCE,
    
    /** Друг */
    FRIEND,
    
    /** Враг */
    ENEMY,
    
    /** Торговый партнер */
    TRADE_PARTNER,
    
    /** Союзник */
    ALLY,
    
    /** Нейтральные отношения */
    NEUTRAL,
    
    /** Романтический интерес */
    ROMANTIC,
    
    /** Соперник */
    RIVAL,
    
    /** Наставник */
    MENTOR,
    
    /** Ученик */
    STUDENT
}
package com.hollowengineai.mod.states

/**
 * Состояние НПС в системе ИИ
 */
enum class NPCState {
    IDLE,           // Ничего не делает
    THINKING,       // Думает/принимает решение
    MOVING,         // Движется
    INTERACTING,    // Взаимодействует с объектом/игроком
    WORKING,        // Выполняет задачу
    COMBAT,         // В бою
    TALKING,        // Разговаривает
    TRADING,        // Торгует
    EXPLORING,      // Исследует
    RESTING,        // Отдыхает
    PANICKING,      // Паникует
    DEAD;           // Мертв

    fun isActive(): Boolean {
        return this != IDLE && this != RESTING && this != DEAD
    }
    
    fun isInterruptible(): Boolean {
        return this != COMBAT && this != DEAD && this != PANICKING
    }
}

/**
 * Эмоциональное состояние НПС
 */
enum class EmotionalState(val displayName: String, val priority: Int) {
    // Базовые эмоции
    NEUTRAL("Нейтральное", 0),
    HAPPY("Радостное", 10),
    SAD("Грустное", 15),
    ANGRY("Злое", 20),
    FEARFUL("Боязливое", 25),
    SURPRISED("Удивленное", 5),
    DISGUSTED("Отвращение", 10),
    
    // Социальные эмоции
    FRIENDLY("Дружелюбное", 8),
    HOSTILE("Враждебное", 30),
    CAUTIOUS("Осторожное", 12),
    TRUSTING("Доверчивое", 8),
    SUSPICIOUS("Подозрительное", 15),
    
    // Рабочие эмоции
    FOCUSED("Сосредоточенное", 5),
    BORED("Скучающее", 3),
    EXCITED("Возбужденное", 12),
    STRESSED("Стрессовое", 18),
    CALM("Спокойное", 2),
    
    // Дополнительные состояния из ошибок
    CURIOUS("Любопытное", 7),
    CONTENT("Довольное", 4),
    SCARED("Испуганное", 22),
    TIRED("Усталое", 8),
    AFRAID("Боязливое", 25); // Дубликат для совместимости
    
    fun isNegative(): Boolean {
        return priority >= 15
    }
    
    fun isPositive(): Boolean {
        return this in listOf(HAPPY, FRIENDLY, TRUSTING, EXCITED, CONTENT, CURIOUS)
    }
}

/**
 * Тип личности НПС
 */
enum class PersonalityType(val displayName: String) {
    // Базовые типы личности
    FRIENDLY_TRADER("Дружелюбный торговец"),
    CAUTIOUS_GUARD("Осторожный стражник"),
    CURIOUS_SCHOLAR("Любознательный ученый"),
    AGGRESSIVE_BANDIT("Агрессивный бандит"),
    NEUTRAL_PEASANT("Нейтральный крестьянин"),
    
    // Дополнительные типы
    WISE_ELDER("Мудрый старейшина"),
    CHEERFUL_BARD("Веселый бард"),
    GRUMPY_BLACKSMITH("Угрюмый кузнец"),
    MYSTERIOUS_MAGE("Таинственный маг"),
    LOYAL_KNIGHT("Верный рыцарь"),
    
    // Специальные типы
    CUSTOM("Настраиваемый"),
    UNKNOWN("Неизвестный");
    
    fun getDefaultEmotionalState(): EmotionalState {
        return when (this) {
            FRIENDLY_TRADER -> EmotionalState.FRIENDLY
            CAUTIOUS_GUARD -> EmotionalState.CAUTIOUS
            CURIOUS_SCHOLAR -> EmotionalState.CURIOUS
            AGGRESSIVE_BANDIT -> EmotionalState.HOSTILE
            NEUTRAL_PEASANT -> EmotionalState.NEUTRAL
            WISE_ELDER -> EmotionalState.CALM
            CHEERFUL_BARD -> EmotionalState.HAPPY
            GRUMPY_BLACKSMITH -> EmotionalState.ANGRY
            MYSTERIOUS_MAGE -> EmotionalState.SUSPICIOUS
            LOYAL_KNIGHT -> EmotionalState.FOCUSED
            else -> EmotionalState.NEUTRAL
        }
    }
}

/**
 * Черты личности НПС
 */
data class PersonalityTraits(
    val friendliness: Float = 0.5f,    // Дружелюбность (0.0 - враждебный, 1.0 - очень дружелюбный)
    val curiosity: Float = 0.5f,       // Любознательность
    val courage: Float = 0.5f,         // Храбрость
    val honesty: Float = 0.5f,         // Честность
    val generosity: Float = 0.5f,      // Щедрость
    val patience: Float = 0.5f,        // Терпение
    val intelligence: Float = 0.5f,    // Интеллект
    val sociability: Float = 0.5f      // Общительность
) {
    companion object {
        fun forPersonalityType(type: PersonalityType): PersonalityTraits {
            return when (type) {
                PersonalityType.FRIENDLY_TRADER -> PersonalityTraits(
                    friendliness = 0.8f, curiosity = 0.6f, courage = 0.5f, 
                    honesty = 0.7f, generosity = 0.6f, sociability = 0.9f
                )
                PersonalityType.CAUTIOUS_GUARD -> PersonalityTraits(
                    friendliness = 0.4f, curiosity = 0.3f, courage = 0.8f, 
                    honesty = 0.8f, patience = 0.7f, sociability = 0.3f
                )
                PersonalityType.CURIOUS_SCHOLAR -> PersonalityTraits(
                    friendliness = 0.6f, curiosity = 0.9f, intelligence = 0.8f, 
                    honesty = 0.7f, patience = 0.6f, sociability = 0.5f
                )
                PersonalityType.AGGRESSIVE_BANDIT -> PersonalityTraits(
                    friendliness = 0.1f, curiosity = 0.4f, courage = 0.7f, 
                    honesty = 0.2f, generosity = 0.1f, patience = 0.2f
                )
                else -> PersonalityTraits() // Default values
            }
        }
    }
}

/**
 * Уровень детализации (LOD) для оптимизации производительности
 */
enum class LODLevel(val distance: Double, val updateFrequency: Int) {
    HIGHEST(16.0, 20),    // Каждый тик
    HIGH(32.0, 10),       // Каждые 2 тика
    MEDIUM(64.0, 5),      // Каждые 4 тика
    LOW(128.0, 2),        // Каждые 10 тиков
    LOWEST(256.0, 1);     // Каждые 20 тиков
    
    companion object {
        fun forDistance(distance: Double): LODLevel {
            return values().firstOrNull { distance <= it.distance } ?: LOWEST
        }
    }
}
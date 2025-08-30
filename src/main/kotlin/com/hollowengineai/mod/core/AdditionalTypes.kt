package com.hollowengineai.mod.core

import net.minecraft.ChatFormatting

/**
 * Дополнительные типы и константы для HollowEngineAI
 * Содержит цвета, константы движения и состояния боя
 */

/**
 * Цвета для форматирования текста в командах и интерфейсе
 */
object Colors {
    val CYAN = ChatFormatting.CYAN
    val RED = ChatFormatting.RED
    val GREEN = ChatFormatting.GREEN
    val YELLOW = ChatFormatting.YELLOW
    val BLUE = ChatFormatting.BLUE
    val WHITE = ChatFormatting.WHITE
    val GRAY = ChatFormatting.GRAY
    val DARK_GREEN = ChatFormatting.DARK_GREEN
    val DARK_RED = ChatFormatting.DARK_RED
    val GOLD = ChatFormatting.GOLD
    val AQUA = ChatFormatting.AQUA
    val LIGHT_PURPLE = ChatFormatting.LIGHT_PURPLE
    val BOLD = ChatFormatting.BOLD
    val ITALIC = ChatFormatting.ITALIC
    val UNDERLINE = ChatFormatting.UNDERLINE
}

/**
 * Константы для системы движения НПС
 */
object MovementConstants {
    /** Максимальное расстояние движения за одну операцию */
    const val MAX_MOVEMENT_DISTANCE = 64.0
    
    /** Минимальное расстояние до цели для остановки */
    const val MIN_DISTANCE_TO_TARGET = 1.5
    
    /** Скорость движения по умолчанию */
    const val DEFAULT_MOVEMENT_SPEED = 1.0
    
    /** Максимальная скорость движения */
    const val MAX_MOVEMENT_SPEED = 2.5
    
    /** Радиус поиска пути */
    const val PATHFINDING_RADIUS = 32.0
    
    /** Высота прыжка НПС */
    const val JUMP_HEIGHT = 1.2
    
    /** Максимальная высота падения без урона */
    const val MAX_SAFE_FALL_DISTANCE = 3.0
}

/**
 * Состояния боя для НПС
 */
enum class CombatState {
    /** НПС не в бою */
    PEACEFUL,
    
    /** НПС готовится к бою */
    READY,
    
    /** НПС активно сражается */
    COMBAT,
    
    /** НПС отступает */
    RETREATING,
    
    /** НПС парирует/блокирует */
    DEFENDING,
    
    /** НПС атакует */
    ATTACKING,
    
    /** НПС ранен и восстанавливается */
    WOUNDED,
    
    /** НПС побежден */
    DEFEATED
}

/**
 * Приоритеты действий НПС
 */
enum class ActionPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
    CRITICAL
}

/**
 * Типы взаимодействий НПС
 */
enum class InteractionType {
    FRIENDLY,
    NEUTRAL,
    HOSTILE,
    TRADE,
    QUEST,
    INFORMATION,
    HELP,
    WARNING
}
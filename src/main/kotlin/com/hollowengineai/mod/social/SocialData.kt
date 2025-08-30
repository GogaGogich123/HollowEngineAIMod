package com.hollowengineai.mod.social

import net.minecraft.world.entity.player.Player
import java.time.LocalDateTime
import java.util.*
import kotlin.math.max
import kotlin.math.min

data class SocialRelationship(
    val playerId: UUID,
    val playerName: String,
    var trust: Double = 0.0, // -100 to 100
    var respect: Double = 0.0, // -100 to 100 
    var friendship: Double = 0.0, // -100 to 100
    var fear: Double = 0.0, // 0 to 100
    val interactionHistory: MutableList<SocialInteraction> = mutableListOf(),
    val traits: MutableSet<SocialTrait> = mutableSetOf(),
    var firstMet: LocalDateTime = LocalDateTime.now(),
    var lastInteraction: LocalDateTime = LocalDateTime.now(),
    var totalInteractions: Int = 0
) {
    fun getOverallRelation(): RelationshipType {
        val avgPositive = (trust + respect + friendship) / 3.0
        return when {
            fear > 50 -> RelationshipType.ENEMY
            avgPositive > 60 -> RelationshipType.FRIEND
            avgPositive > 30 -> RelationshipType.ACQUAINTANCE  
            avgPositive > 0 -> RelationshipType.NEUTRAL_POSITIVE
            avgPositive > -30 -> RelationshipType.NEUTRAL
            avgPositive > -60 -> RelationshipType.DISLIKE
            else -> RelationshipType.ENEMY
        }
    }
    
    fun updateRelationship(interaction: SocialInteraction) {
        interactionHistory.add(interaction)
        lastInteraction = interaction.timestamp
        totalInteractions++
        
        // Применяем изменения отношений
        trust = clamp(trust + interaction.trustChange, -100.0, 100.0)
        respect = clamp(respect + interaction.respectChange, -100.0, 100.0)  
        friendship = clamp(friendship + interaction.friendshipChange, -100.0, 100.0)
        fear = clamp(fear + interaction.fearChange, 0.0, 100.0)
        
        // Добавляем черты на основе взаимодействия
        traits.addAll(interaction.detectedTraits)
        
        // Ограничиваем историю
        if (interactionHistory.size > 50) {
            interactionHistory.removeAt(0)
        }
    }
    
    private fun clamp(value: Double, min: Double, max: Double): Double {
        return max(min, min(max, value))
    }
}

data class SocialInteraction(
    val playerId: UUID,
    val interactionType: InteractionType,
    val context: String,
    val trustChange: Double = 0.0,
    val respectChange: Double = 0.0,
    val friendshipChange: Double = 0.0, 
    val fearChange: Double = 0.0,
    val detectedTraits: Set<SocialTrait> = emptySet(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val importance: InteractionImportance = InteractionImportance.NORMAL,
    val witnesses: Set<UUID> = emptySet() // Другие НПС или игроки которые видели взаимодействие
)

enum class RelationshipType(val displayName: String) {
    ENEMY("Враг"),
    DISLIKE("Неприязнь"), 
    NEUTRAL("Нейтральный"),
    NEUTRAL_POSITIVE("Слегка положительный"),
    ACQUAINTANCE("Знакомый"),
    FRIEND("Друг"),
    CLOSE_FRIEND("Близкий друг")
}

enum class InteractionType(
    val displayName: String,
    val defaultTrustChange: Double = 0.0,
    val defaultRespectChange: Double = 0.0,
    val defaultFriendshipChange: Double = 0.0,
    val defaultFearChange: Double = 0.0
) {
    FIRST_MEETING("Первая встреча", friendshipChange = 2.0),
    GREETING("Приветствие", trustChange = 1.0, friendshipChange = 0.5),
    CONVERSATION("Разговор", trustChange = 2.0, friendshipChange = 1.0),
    TRADE_SUCCESS("Успешная торговля", trustChange = 5.0, respectChange = 3.0),
    TRADE_FAILURE("Неудачная торговля", trustChange = -2.0),
    GIFT_RECEIVED("Получил подарок", trustChange = 8.0, friendshipChange = 10.0),
    GIFT_GIVEN("Дал подарок", respectChange = 5.0, friendshipChange = 5.0),
    HELP_RECEIVED("Получил помощь", trustChange = 10.0, respectChange = 8.0, friendshipChange = 5.0),
    HELP_GIVEN("Оказал помощь", respectChange = 3.0),
    BETRAYAL("Предательство", trustChange = -50.0, respectChange = -30.0, friendshipChange = -40.0, fearChange = 20.0),
    ATTACK("Нападение", trustChange = -30.0, respectChange = -20.0, friendshipChange = -50.0, fearChange = 40.0),
    DEFENSE("Защита", trustChange = 15.0, respectChange = 10.0, friendshipChange = 8.0),
    WITNESSED_CRIME("Видел преступление", trustChange = -10.0, fearChange = 5.0),
    WITNESSED_KINDNESS("Видел доброту", trustChange = 3.0, respectChange = 2.0),
    IGNORED("Проигнорирован", friendshipChange = -1.0),
    INTERRUPTED("Прерван", respectChange = -2.0),
    JOKE_SHARED("Поделился шуткой", friendshipChange = 3.0),
    INSULT("Оскорбление", respectChange = -15.0, friendshipChange = -10.0, fearChange = 5.0),
    COMPLIMENT("Комплимент", respectChange = 3.0, friendshipChange = 5.0),
    PROMISE_KEPT("Сдержал обещание", trustChange = 15.0, respectChange = 8.0),
    PROMISE_BROKEN("Нарушил обещание", trustChange = -20.0, respectChange = -10.0),
    SHARED_MEAL("Разделил еду", trustChange = 5.0, friendshipChange = 8.0),
    WORKED_TOGETHER("Работали вместе", trustChange = 5.0, respectChange = 5.0, friendshipChange = 3.0),
    COMPETITION_WON("Выиграл в соревновании", respectChange = 8.0),
    COMPETITION_LOST("Проиграл в соревновании", respectChange = -3.0),
    PROTECTED("Защитил", trustChange = 20.0, respectChange = 15.0, friendshipChange = 12.0),
    ABANDONED("Бросил в беде", trustChange = -25.0, friendshipChange = -30.0, fearChange = 10.0)
}

enum class InteractionImportance(val multiplier: Double) {
    TRIVIAL(0.5),
    NORMAL(1.0),
    IMPORTANT(1.5),
    CRITICAL(2.0),
    LIFE_CHANGING(3.0)
}

enum class SocialTrait(val displayName: String, val description: String) {
    GENEROUS("Щедрый", "Часто дарит подарки и помогает"),
    SELFISH("Эгоистичный", "Редко помогает, думает только о себе"),
    HONEST("Честный", "Всегда говорит правду, держит обещания"),
    LIAR("Лжец", "Часто обманывает, не держит слово"),
    BRAVE("Смелый", "Готов рисковать ради других"),
    COWARD("Трус", "Избегает опасности, бросает в беде"),
    AGGRESSIVE("Агрессивный", "Склонен к конфликтам и насилию"),
    PEACEFUL("Миролюбивый", "Избегает конфликтов, предпочитает мир"),
    INTELLIGENT("Умный", "Демонстрирует знания и мудрость"),
    FOOLISH("Глупый", "Принимает неразумные решения"),
    PATIENT("Терпеливый", "Спокойно ждет, не торопит"),
    IMPATIENT("Нетерпеливый", "Торопится, не любит ждать"),
    RELIABLE("Надежный", "На него можно положиться"),
    UNRELIABLE("Ненадежный", "Часто подводит, непредсказуем"),
    RESPECTFUL("Уважительный", "Вежлив и тактичен"),
    RUDE("Грубый", "Невежлив, оскорбляет"),
    LEADER("Лидер", "Принимает решения за группу"),
    FOLLOWER("Последователь", "Следует за другими"),
    CURIOUS("Любопытный", "Интересуется всем вокруг"),
    SECRETIVE("Скрытный", "Не любит делиться информацией"),
    HELPFUL("Полезный", "Всегда готов помочь"),
    LAZY("Ленивый", "Избегает работы и обязанностей"),
    PROTECTIVE("Защитник", "Заботится о безопасности других"),
    MANIPULATIVE("Манипулятор", "Использует других в своих целях"),
    LOYAL("Верный", "Остается предан друзьям"),
    TREACHEROUS("Предатель", "Готов предать ради выгоды")
}

data class SocialGroup(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val members: MutableSet<UUID> = mutableSetOf(),
    val leaders: MutableSet<UUID> = mutableSetOf(),
    val groupType: GroupType,
    val reputation: Double = 0.0, // -100 to 100
    val rules: MutableList<SocialRule> = mutableListOf(),
    val allies: MutableSet<UUID> = mutableSetOf(), // ID других групп
    val enemies: MutableSet<UUID> = mutableSetOf(), // ID других групп
    var created: LocalDateTime = LocalDateTime.now(),
    var lastActivity: LocalDateTime = LocalDateTime.now()
) {
    fun addMember(playerId: UUID) {
        members.add(playerId)
        lastActivity = LocalDateTime.now()
    }
    
    fun removeMember(playerId: UUID) {
        members.remove(playerId)
        leaders.remove(playerId)
        lastActivity = LocalDateTime.now()
    }
    
    fun isLeader(playerId: UUID): Boolean {
        return playerId in leaders
    }
    
    fun isMember(playerId: UUID): Boolean {
        return playerId in members
    }
}

// GroupType перенесен в SocialGroupManager.kt для избежания дублирования

data class SocialRule(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String,
    val ruleType: RuleType,
    val severity: RuleSeverity,
    val affectedGroups: Set<UUID> = emptySet(), // Пусто = применяется ко всем
    var violations: MutableList<RuleViolation> = mutableListOf()
)

data class RuleViolation(
    val violatorId: UUID,
    val violatorName: String,
    val ruleId: UUID,
    val context: String,
    val witnesses: Set<UUID> = emptySet(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    var punishment: String? = null,
    var forgiven: Boolean = false
)

enum class RuleType(val displayName: String) {
    MORAL("Моральное правило"),
    LEGAL("Правовое правило"), 
    SOCIAL("Социальное правило"),
    PERSONAL("Личное правило"),
    GROUP("Групповое правило"),
    TRADE("Торговое правило"),
    COMBAT("Правило боя")
}

enum class RuleSeverity(val displayName: String, val penaltyMultiplier: Double) {
    MINOR("Незначительное", 0.5),
    MODERATE("Умеренное", 1.0),
    SERIOUS("Серьезное", 2.0), 
    SEVERE("Тяжкое", 3.0),
    UNFORGIVABLE("Непростительное", 5.0)
}

data class SocialMemory(
    val playerId: UUID,
    val memory: String,
    val memoryType: MemoryType,
    val emotionalWeight: Double, // -10 to 10
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val relatedPlayers: Set<UUID> = emptySet(),
    val location: String? = null,
    var recalled: Int = 0
) {
    fun recall() {
        recalled++
    }
}

enum class MemoryType(val displayName: String) {
    FIRST_IMPRESSION("Первое впечатление"),
    SIGNIFICANT_EVENT("Значимое событие"),
    EMOTIONAL_MOMENT("Эмоциональный момент"),
    LESSON_LEARNED("Выученный урок"),
    PROMISE("Обещание"),
    SECRET("Секрет"),
    ACHIEVEMENT("Достижение"),
    FAILURE("Неудача"),
    CONVERSATION("Разговор"),
    OBSERVATION("Наблюдение")
}

object SocialUtils {
    fun calculateRelationshipDecay(lastInteraction: LocalDateTime, currentTime: LocalDateTime): Double {
        val daysSince = java.time.Duration.between(lastInteraction, currentTime).toDays()
        return when {
            daysSince < 1 -> 0.0
            daysSince < 7 -> 0.1
            daysSince < 30 -> 0.2
            daysSince < 90 -> 0.5
            else -> 1.0
        }
    }
    
    fun getCompatibilityScore(traits1: Set<SocialTrait>, traits2: Set<SocialTrait>): Double {
        val positiveTraits = setOf(
            SocialTrait.GENEROUS, SocialTrait.HONEST, SocialTrait.BRAVE,
            SocialTrait.PEACEFUL, SocialTrait.INTELLIGENT, SocialTrait.PATIENT,
            SocialTrait.RELIABLE, SocialTrait.RESPECTFUL, SocialTrait.HELPFUL,
            SocialTrait.PROTECTIVE, SocialTrait.LOYAL
        )
        
        val negativeTraits = setOf(
            SocialTrait.SELFISH, SocialTrait.LIAR, SocialTrait.COWARD,
            SocialTrait.AGGRESSIVE, SocialTrait.FOOLISH, SocialTrait.IMPATIENT,
            SocialTrait.UNRELIABLE, SocialTrait.RUDE, SocialTrait.LAZY,
            SocialTrait.MANIPULATIVE, SocialTrait.TREACHEROUS
        )
        
        val conflictingPairs = mapOf(
            SocialTrait.HONEST to SocialTrait.LIAR,
            SocialTrait.BRAVE to SocialTrait.COWARD,
            SocialTrait.PEACEFUL to SocialTrait.AGGRESSIVE,
            SocialTrait.PATIENT to SocialTrait.IMPATIENT,
            SocialTrait.GENEROUS to SocialTrait.SELFISH,
            SocialTrait.RELIABLE to SocialTrait.UNRELIABLE,
            SocialTrait.RESPECTFUL to SocialTrait.RUDE,
            SocialTrait.LOYAL to SocialTrait.TREACHEROUS
        )
        
        var compatibility = 0.0
        
        // Бонус за общие положительные черты
        val commonPositive = traits1.intersect(traits2).intersect(positiveTraits)
        compatibility += commonPositive.size * 10.0
        
        // Штраф за конфликтующие черты
        for ((trait1, trait2) in conflictingPairs) {
            if ((trait1 in traits1 && trait2 in traits2) || (trait2 in traits1 && trait1 in traits2)) {
                compatibility -= 20.0
            }
        }
        
        // Штраф за общие негативные черты
        val commonNegative = traits1.intersect(traits2).intersect(negativeTraits)
        compatibility -= commonNegative.size * 5.0
        
        return compatibility
    }
    
    fun generateSocialAdvice(relationship: SocialRelationship): List<String> {
        val advice = mutableListOf<String>()
        
        if (relationship.trust < 0) {
            advice.add("Необходимо восстановить доверие через честные действия")
        }
        
        if (relationship.fear > 30) {
            advice.add("Игрок боится - следует избегать агрессивного поведения")
        }
        
        if (relationship.friendship > 50 && relationship.trust > 30) {
            advice.add("Хорошие отношения - можно просить об услугах")
        }
        
        if (relationship.totalInteractions < 5) {
            advice.add("Мало взаимодействий - стоит больше общаться")
        }
        
        val recentInteractions = relationship.interactionHistory.takeLast(5)
        val negativeRecent = recentInteractions.count { 
            it.trustChange < 0 || it.respectChange < 0 || it.friendshipChange < 0 
        }
        
        if (negativeRecent >= 3) {
            advice.add("Последние взаимодействия негативные - нужно изменить подход")
        }
        
        return advice
    }
}
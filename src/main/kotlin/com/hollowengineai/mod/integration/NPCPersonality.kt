package com.hollowengineai.mod.integration

import com.hollowengineai.mod.core.PersonalityType

/**
 * Расширенная система личности НПС для интеграции с HollowEngine Legacy
 * 
 * Позволяет детально настроить:
 * - Характер и темперамент
 * - Биографию и историю
 * - Цели и мотивацию
 * - Навыки и предпочтения
 */
data class NPCPersonality(
    val name: String,
    val personalityType: PersonalityType,
    val traits: Map<String, Float> = emptyMap(),
    val biography: String = "",
    val goals: List<String> = emptyList(),
    val relationships: Map<String, Float> = emptyMap(),
    val skills: Map<String, Float> = emptyMap(),
    val preferences: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Создать дружелюбного торговца
         */
        fun friendlyTrader(name: String, biography: String = ""): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = PersonalityType.FRIENDLY,
                traits = mapOf(
                    "friendliness" to 0.9f,
                    "greed" to 0.3f,
                    "curiosity" to 0.7f,
                    "patience" to 0.8f,
                    "honesty" to 0.8f
                ),
                biography = biography.ifEmpty { 
                    "Опытный торговец, любящий общаться с путниками и предлагающий честные сделки." 
                },
                goals = listOf(
                    "Заработать золото честной торговлей",
                    "Узнать новости от путешественников",
                    "Найти редкие товары для продажи"
                ),
                skills = mapOf(
                    "trading" to 0.9f,
                    "persuasion" to 0.7f,
                    "appraisal" to 0.8f
                ),
                preferences = mapOf(
                    "favorite_items" to listOf("gold", "gems", "exotic_goods"),
                    "hated_items" to listOf("stolen_goods", "cursed_items"),
                    "greeting_style" to "enthusiastic"
                )
            )
        }
        
        /**
         * Создать осторожного стража
         */
        fun cautiousGuard(name: String, biography: String = ""): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = PersonalityType.CAUTIOUS,
                traits = mapOf(
                    "vigilance" to 0.9f,
                    "suspicion" to 0.7f,
                    "loyalty" to 0.8f,
                    "courage" to 0.7f,
                    "discipline" to 0.9f
                ),
                biography = biography.ifEmpty { 
                    "Верный страж, посвятивший жизнь защите от угроз. Не доверяет незнакомцам." 
                },
                goals = listOf(
                    "Защищать территорию от врагов",
                    "Выявлять подозрительных личностей",
                    "Поддерживать порядок"
                ),
                skills = mapOf(
                    "combat" to 0.8f,
                    "observation" to 0.9f,
                    "intimidation" to 0.6f
                ),
                preferences = mapOf(
                    "patrol_routes" to listOf("main_gate", "walls", "watchtower"),
                    "threat_level" to "medium",
                    "backup_call_threshold" to 0.7f
                )
            )
        }
        
        /**
         * Создать любопытного ученого
         */
        fun curiousScholar(name: String, biography: String = ""): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = PersonalityType.CURIOUS,
                traits = mapOf(
                    "curiosity" to 0.95f,
                    "intelligence" to 0.9f,
                    "patience" to 0.8f,
                    "social_skills" to 0.4f,
                    "absent_mindedness" to 0.7f
                ),
                biography = biography.ifEmpty { 
                    "Увлеченный исследователь, постоянно изучающий мир и собирающий знания." 
                },
                goals = listOf(
                    "Изучить магические артефакты",
                    "Записать истории путешественников",
                    "Открыть новые заклинания"
                ),
                skills = mapOf(
                    "research" to 0.9f,
                    "magic" to 0.8f,
                    "writing" to 0.8f,
                    "memory" to 0.9f
                ),
                preferences = mapOf(
                    "research_topics" to listOf("magic", "history", "artifacts"),
                    "interaction_style" to "questioning",
                    "attention_span" to "long"
                )
            )
        }
        
        /**
         * Создать агрессивного бандита
         */
        fun aggressiveBandit(name: String, biography: String = ""): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = PersonalityType.AGGRESSIVE,
                traits = mapOf(
                    "aggression" to 0.9f,
                    "greed" to 0.8f,
                    "impulsiveness" to 0.8f,
                    "cruelty" to 0.6f,
                    "cunning" to 0.7f
                ),
                biography = biography.ifEmpty { 
                    "Безжалостный разбойник, промышляющий грабежом на дорогах." 
                },
                goals = listOf(
                    "Ограбить богатых путников",
                    "Избежать правосудия",
                    "Найти укромное убежище"
                ),
                skills = mapOf(
                    "combat" to 0.8f,
                    "stealth" to 0.7f,
                    "intimidation" to 0.9f,
                    "lockpicking" to 0.6f
                ),
                preferences = mapOf(
                    "target_types" to listOf("wealthy_travelers", "merchants"),
                    "attack_style" to "ambush",
                    "retreat_threshold" to 0.3f
                )
            )
        }
        
        /**
         * Создать нейтрального крестьянина
         */
        fun neutralPeasant(name: String, biography: String = ""): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = PersonalityType.NEUTRAL,
                traits = mapOf(
                    "hardworking" to 0.8f,
                    "simple" to 0.9f,
                    "cautious" to 0.6f,
                    "kindness" to 0.7f,
                    "superstition" to 0.6f
                ),
                biography = biography.ifEmpty { 
                    "Простой труженик, живущий размеренной жизнью и заботящийся о семье." 
                },
                goals = listOf(
                    "Вырастить хороший урожай",
                    "Обеспечить семью",
                    "Жить в мире и спокойствии"
                ),
                skills = mapOf(
                    "farming" to 0.8f,
                    "crafting" to 0.6f,
                    "animal_care" to 0.7f
                ),
                preferences = mapOf(
                    "daily_routine" to listOf("dawn_work", "noon_rest", "evening_chores"),
                    "social_level" to "reserved",
                    "change_tolerance" to "low"
                )
            )
        }
        
        /**
         * Создать кастомную личность
         */
        fun custom(
            name: String,
            personalityType: PersonalityType,
            traits: Map<String, Float>,
            biography: String,
            goals: List<String>,
            skills: Map<String, Float> = emptyMap(),
            preferences: Map<String, Any> = emptyMap()
        ): NPCPersonality {
            return NPCPersonality(
                name = name,
                personalityType = personalityType,
                traits = traits,
                biography = biography,
                goals = goals,
                skills = skills,
                preferences = preferences
            )
        }
    }
    
    /**
     * Получить описание личности для LLM
     */
    fun getLLMDescription(): String {
        val traitsDesc = traits.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val goalsDesc = goals.joinToString("; ")
        
        return """
        Имя: $name
        Тип личности: $personalityType
        Черты характера: $traitsDesc
        Биография: $biography
        Цели: $goalsDesc
        """.trimIndent()
    }
}
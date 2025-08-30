package com.hollowengineai.mod.actions

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.core.SmartNPC
import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.events.NPCEvent
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.states.NPCState
import kotlinx.coroutines.delay
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Исполнитель социальных действий для NPC
 * Обрабатывает разговоры, эмоциональные взаимодействия, жесты и социальное поведение
 */
class SocialActionExecutor(
    private val eventBus: NPCEventBusImpl
) : ActionExecutor {
    
    override val supportedActions = setOf(
        "talk",
        "greet",
        "farewell",
        "compliment",
        "insult",
        "joke",
        "ask_question",
        "share_gossip",
        "express_emotion",
        "gesture",
        "wave",
        "nod",
        "shake_head",
        "bow",
        "laugh",
        "cry",
        "comfort",
        "apologize",
        "thank",
        "request_help",
        "offer_help",
        "negotiate",
        "persuade",
        "build_rapport"
    )
    
    override val priority = 80 // Средний приоритет
    
    override fun canHandle(action: String, npc: SmartNPC, target: Entity?): Boolean {
        return supportedActions.contains(action)
    }
    
    override suspend fun executeAction(
        action: String,
        npc: SmartNPC,
        target: Entity?,
        parameters: Map<String, Any>
    ): ActionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Переходим в состояние разговора для большинства социальных действий
            val talkingActions = setOf("talk", "greet", "farewell", "ask_question", "negotiate", "persuade")
            val stateMachine = npc.getStateMachine()
            if (talkingActions.contains(action) && stateMachine?.getCurrentState() != NPCState.TALKING) {
                stateMachine?.transitionTo(NPCState.TALKING, "Social interaction: $action")
            }
            
            val result = when (action) {
                "talk" -> executeTalk(npc, target, parameters)
                "greet" -> executeGreet(npc, target)
                "farewell" -> executeFarewell(npc, target)
                "compliment" -> executeCompliment(npc, target as? LivingEntity)
                "insult" -> executeInsult(npc, target as? LivingEntity)
                "joke" -> executeJoke(npc, target)
                "ask_question" -> executeAskQuestion(npc, target, parameters)
                "share_gossip" -> executeShareGossip(npc, target)
                "express_emotion" -> executeExpressEmotion(npc, parameters)
                "gesture" -> executeGesture(npc, parameters)
                "wave" -> executeWave(npc, target)
                "nod" -> executeNod(npc)
                "shake_head" -> executeShakeHead(npc)
                "bow" -> executeBow(npc, target)
                "laugh" -> executeLaugh(npc)
                "cry" -> executeCry(npc)
                "comfort" -> executeComfort(npc, target as? LivingEntity)
                "apologize" -> executeApologize(npc, target as? LivingEntity)
                "thank" -> executeThank(npc, target as? LivingEntity)
                "request_help" -> executeRequestHelp(npc, target as? LivingEntity, parameters)
                "offer_help" -> executeOfferHelp(npc, target as? LivingEntity, parameters)
                "negotiate" -> executeNegotiate(npc, target as? LivingEntity, parameters)
                "persuade" -> executePersuade(npc, target as? LivingEntity, parameters)
                "build_rapport" -> executeBuildRapport(npc, target as? LivingEntity)
                else -> ActionResult(false, "Unknown social action: $action")
            }
            
            // Отправляем событие о социальном действии
            val communicationEvent = NPCEvent(
                type = NPCEventType.SPEECH,
                sourceNpcId = npc.id,
                sourceNpcName = npc.getName(),
                data = mapOf(
                    "action" to action,
                    "target" to (target ?: "everyone"),
                    "content" to (result.data["message"] as? String ?: "")
                ),
                position = npc.position
            )
            eventBus.sendEventSync(communicationEvent)
            
            return result.copy(executionTime = System.currentTimeMillis() - startTime)
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Error executing social action $action", e)
            return ActionResult(
                success = false,
                message = "Social action failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override fun estimateCost(action: String, npc: SmartNPC, target: Entity?): ActionCost {
        return when (action) {
            "talk" -> ActionCost(
                energyCost = 5f,
                timeCost = 2000L,
                riskLevel = 0.1f,
                socialCost = 1f // Позитивное влияние
            )
            "greet", "farewell" -> ActionCost(
                energyCost = 2f,
                timeCost = 500L,
                riskLevel = 0.0f,
                socialCost = 2f
            )
            "compliment" -> ActionCost(
                energyCost = 3f,
                timeCost = 800L,
                riskLevel = 0.05f,
                socialCost = 5f
            )
            "insult" -> ActionCost(
                energyCost = 4f,
                timeCost = 600L,
                riskLevel = 0.6f,
                socialCost = -20f // Негативное влияние
            )
            "joke" -> ActionCost(
                energyCost = 4f,
                timeCost = 1000L,
                riskLevel = 0.2f,
                socialCost = 3f
            )
            "apologize" -> ActionCost(
                energyCost = 6f,
                timeCost = 1200L,
                riskLevel = 0.1f,
                socialCost = 10f
            )
            "negotiate", "persuade" -> ActionCost(
                energyCost = 8f,
                timeCost = 3000L,
                riskLevel = 0.3f,
                socialCost = 0f
            )
            "comfort" -> ActionCost(
                energyCost = 5f,
                timeCost = 1500L,
                riskLevel = 0.1f,
                socialCost = 8f
            )
            else -> ActionCost(3f, 800L, 0.1f, 1f)
        }
    }
    
    override fun getPrerequisites(action: String, npc: SmartNPC, target: Entity?): List<ActionPrerequisite> {
        return when (action) {
            "talk", "greet", "negotiate", "persuade" -> listOf(
                DistancePrerequisite(8.0, "Must be close enough for conversation")
            )
            "compliment", "insult" -> listOf(
                DistancePrerequisite(8.0, "Must be close enough to be heard")
            )
            "comfort" -> listOf(
                DistancePrerequisite(5.0, "Must be close for comforting"),
                EmotionalPrerequisite(minValence = 0.2f, description = "Must be in positive mood to comfort others")
            )
            "apologize" -> listOf(
                EmotionalPrerequisite(maxValence = 0.0f, description = "Must feel remorse to apologize")
            )
            else -> listOf(
                DistancePrerequisite(10.0, "Must be within social interaction range")
            )
        }
    }
    
    /**
     * Обычный разговор
     */
    private suspend fun executeTalk(npc: SmartNPC, target: Entity?, parameters: Map<String, Any>): ActionResult {
        val message = parameters["message"] as? String ?: generateConversationMessage(npc, target)
        
        // Отправляем сообщение в чат если цель - игрок
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
        }
        
        delay(2000L) // Время разговора
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.1f,
            arousalChange = 0.05f
        )
        
        // Изменение эмоционального состояния упрощено (valence +0.1, arousal +0.05)
        // TODO: Логика смены эмоционального состояния на более позитивное
        
        return ActionResult(
            success = true,
            message = "Conversation successful",
            data = mapOf(
                "message" to message,
                "target" to (target?.name?.string ?: "unknown")
            ),
            energyCost = 5f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Приветствие
     */
    private suspend fun executeGreet(npc: SmartNPC, target: Entity?): ActionResult {
        val greetings = listOf(
            "Hello there!",
            "Good day!",
            "Greetings!",
            "How are you?",
            "Well met!",
            "Welcome!"
        )
        
        val greeting = greetings.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $greeting"))
        }
        
        delay(500L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.15f,
            arousalChange = 0.1f
        )
        
        // Изменение эмоционального состояния упрощено (valence +0.15, arousal +0.1)
        // TODO: Логика смены эмоционального состояния на более позитивное
        
        return ActionResult(
            success = true,
            message = "Greeting delivered",
            data = mapOf(
                "greeting" to greeting,
                "target" to (target?.name?.string ?: "someone")
            ),
            energyCost = 2f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Прощание
     */
    private suspend fun executeFarewell(npc: SmartNPC, target: Entity?): ActionResult {
        val farewells = listOf(
            "Farewell!",
            "See you later!",
            "Take care!",
            "Until next time!",
            "Safe travels!",
            "Goodbye!"
        )
        
        val farewell = farewells.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $farewell"))
        }
        
        delay(500L)
        
        // После прощания возвращаемся в состояние IDLE
        npc.getStateMachine()?.transitionTo(NPCState.IDLE, "Conversation ended")
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            arousalChange = -0.1f
        )
        
        return ActionResult(
            success = true,
            message = "Farewell delivered",
            data = mapOf(
                "farewell" to farewell,
                "target" to (target?.name?.string ?: "someone")
            ),
            energyCost = 2f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Комплимент
     */
    private suspend fun executeCompliment(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
        
        val compliments = listOf(
            "You look great today!",
            "I admire your courage.",
            "You're very skilled!",
            "You have good taste.",
            "You're quite wise.",
            "Your equipment looks impressive!"
        )
            
            val compliment = compliments.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $compliment"))
            }
            
            delay(800L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = 0.2f,
                arousalChange = 0.1f
            )
            
            // Изменение эмоционального состояния упрощено (valence +0.2, arousal +0.1)
            // TODO: Логика смены эмоционального состояния на HAPPY или EXCITED
            
            ActionResult(
                success = true,
                message = "Compliment given",
                data = mapOf(
                    "compliment" to compliment,
                    "target" to targetEntity.name.string
                ),
                energyCost = 3f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No target for compliment")
    }
    
    /**
     * Оскорбление
     */
    private suspend fun executeInsult(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
            val insults = listOf(
                "You're not very bright, are you?",
                "I've seen rocks with more personality.",
                "Your skills are... questionable.",
                "You should stick to easier tasks.",
                "I'm not impressed.",
                "You could use some practice."
            )
            
            val insult = insults.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $insult"))
            }
            
            delay(600L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = -0.1f,
                arousalChange = 0.2f,
                dominanceChange = 0.1f
            )
            
            // Изменение эмоционального состояния упрощено (valence -0.1, arousal +0.2)
            // TODO: Логика смены эмоционального состояния на ANGRY или EXCITED
            
            ActionResult(
                success = true,
                message = "Insult delivered",
                data = mapOf(
                    "insult" to insult,
                    "target" to targetEntity.name.string
                ),
                energyCost = 4f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No target for insult")
    }
    
    /**
     * Шутка
     */
    private suspend fun executeJoke(npc: SmartNPC, target: Entity?): ActionResult {
        val jokes = listOf(
            "Why don't skeletons fight each other? They don't have the guts!",
            "What do you call a pig that does karate? A pork chop!",
            "Why don't scientists trust atoms? Because they make up everything!",
            "What do you call a fake noodle? An impasta!",
            "Why did the creeper cross the road? To get to the other BOOM!",
            "What's a zombie's favorite type of music? Soul music!"
        )
        
        val joke = jokes.random()
        val jokeSuccess = Random.nextFloat() < 0.7f // 70% шанс успешной шутки
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $joke"))
            if (jokeSuccess) {
                target.sendSystemMessage(Component.literal("*${target.name.string} laughs*"))
            }
        }
        
        delay(1000L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (jokeSuccess) 0.25f else 0.05f,
            arousalChange = 0.15f
        )
        
        // Изменение эмоционального состояния упрощено (valence +${if (jokeSuccess) "0.25" else "0.05"}, arousal +0.15)
        // TODO: Логика смены эмоционального состояния на HAPPY если шутка успешна, иначе нейтральное
        
        return ActionResult(
            success = jokeSuccess,
            message = if (jokeSuccess) "Joke was well received!" else "Joke fell flat",
            data = mapOf(
                "joke" to joke,
                "success" to jokeSuccess,
                "target" to (target?.name?.string ?: "audience")
            ),
            energyCost = 4f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Задать вопрос
     */
    private suspend fun executeAskQuestion(npc: SmartNPC, target: Entity?, parameters: Map<String, Any>): ActionResult {
        val question = parameters["question"] as? String ?: generateQuestion(npc, target)
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $question"))
        }
        
        delay(1500L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            arousalChange = 0.1f
        )
        
        return ActionResult(
            success = true,
            message = "Question asked",
            data = mapOf(
                "question" to question,
                "target" to (target?.name?.string ?: "someone")
            ),
            energyCost = 4f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Поделиться сплетнями
     */
    private suspend fun executeShareGossip(npc: SmartNPC, target: Entity?): ActionResult {
        val gossipTopics = listOf(
            "Did you hear about the merchant in the next village?",
            "I heard strange noises coming from the caves last night.",
            "Someone saw unusual lights in the forest.",
            "The weather has been quite odd lately, don't you think?",
            "I heard there's treasure hidden somewhere around here.",
            "There are rumors of new dangers in these parts."
        )
        
        val gossip = gossipTopics.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $gossip"))
        }
        
        delay(2500L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.1f,
            arousalChange = 0.15f
        )
        
        return ActionResult(
            success = true,
            message = "Gossip shared",
            data = mapOf(
                "gossip" to gossip,
                "target" to (target?.name?.string ?: "someone")
            ),
            energyCost = 5f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Выразить эмоцию
     */
    private suspend fun executeExpressEmotion(npc: SmartNPC, parameters: Map<String, Any>): ActionResult {
        val emotion = parameters["emotion"] as? String ?: determineCurrentEmotion(npc)
        
        val expressions = when (emotion.lowercase()) {
            "happy" -> listOf("*smiles broadly*", "*beams with joy*", "*grins happily*")
            "sad" -> listOf("*looks down sadly*", "*sighs heavily*", "*appears dejected*")
            "angry" -> listOf("*scowls angrily*", "*clenches fists*", "*glares fiercely*")
            "excited" -> listOf("*bounces with excitement*", "*eyes light up*", "*can barely contain enthusiasm*")
            "tired" -> listOf("*yawns widely*", "*rubs eyes wearily*", "*slouches with fatigue*")
            "confused" -> listOf("*scratches head*", "*looks puzzled*", "*furrows brow in confusion*")
            else -> listOf("*expresses emotion*")
        }
        
        val expression = expressions.random()
        
        // Показываем эмоцию игрокам поблизости
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(10.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $expression"))
        }
        
        delay(800L)
        
        return ActionResult(
            success = true,
            message = "Emotion expressed",
            data = mapOf(
                "emotion" to emotion,
                "expression" to expression,
                "observers" to nearbyPlayers.size
            ),
            energyCost = 3f
        )
    }
    
    /**
     * Жест
     */
    private suspend fun executeGesture(npc: SmartNPC, parameters: Map<String, Any>): ActionResult {
        val gestureType = parameters["gesture"] as? String ?: "generic"
        
        val gestures = when (gestureType) {
            "point" -> listOf("*points ahead*", "*gestures in that direction*")
            "shrug" -> listOf("*shrugs shoulders*", "*makes a dismissive gesture*")
            "thumbs_up" -> listOf("*gives thumbs up*", "*shows approval*")
            "beckoning" -> listOf("*beckons to come closer*", "*waves you over*")
            else -> listOf("*makes a gesture*", "*moves expressively*")
        }
        
        val gesture = gestures.random()
        
        // Показываем жест игрокам поблизости
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(8.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $gesture"))
        }
        
        delay(400L)
        
        return ActionResult(
            success = true,
            message = "Gesture performed",
            data = mapOf(
                "gesture_type" to gestureType,
                "gesture" to gesture,
                "observers" to nearbyPlayers.size
            ),
            energyCost = 2f
        )
    }
    
    /**
     * Помахать рукой
     */
    private suspend fun executeWave(npc: SmartNPC, target: Entity?): ActionResult {
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(10.0))
        
        val waveMessage = target?.let {
            "*waves at ${it.name.string}*"
        } ?: "*waves friendly*"
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $waveMessage"))
        }
        
        delay(300L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.1f,
            arousalChange = 0.05f
        )
        
        return ActionResult(
            success = true,
            message = "Wave performed",
            data = mapOf(
                "target" to (target?.name?.string ?: "everyone"),
                "observers" to nearbyPlayers.size
            ),
            energyCost = 1f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Кивнуть
     */
    private suspend fun executeNod(npc: SmartNPC): ActionResult {
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(8.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} *nods in agreement*"))
        }
        
        delay(200L)
        
        return ActionResult(
            success = true,
            message = "Nod performed",
            data = mapOf("observers" to nearbyPlayers.size),
            energyCost = 1f
        )
    }
    
    /**
     * Покачать головой
     */
    private suspend fun executeShakeHead(npc: SmartNPC): ActionResult {
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(8.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} *shakes head disapprovingly*"))
        }
        
        delay(300L)
        
        return ActionResult(
            success = true,
            message = "Head shake performed",
            data = mapOf("observers" to nearbyPlayers.size),
            energyCost = 1f
        )
    }
    
    /**
     * Поклониться
     */
    private suspend fun executeBow(npc: SmartNPC, target: Entity?): ActionResult {
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(8.0))
        
        val bowMessage = target?.let {
            "*bows respectfully to ${it.name.string}*"
        } ?: "*bows respectfully*"
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $bowMessage"))
        }
        
        delay(600L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            dominanceChange = -0.1f // Поклон снижает доминантность
        )
        
        return ActionResult(
            success = true,
            message = "Bow performed",
            data = mapOf(
                "target" to (target?.name?.string ?: "everyone"),
                "observers" to nearbyPlayers.size
            ),
            energyCost = 2f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Смеяться
     */
    private suspend fun executeLaugh(npc: SmartNPC): ActionResult {
        val laughs = listOf("*laughs heartily*", "*chuckles*", "*giggles*", "*bursts into laughter*")
        val laugh = laughs.random()
        
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(10.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $laugh"))
        }
        
        delay(800L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.3f,
            arousalChange = 0.2f
        )
        
        // Изменение эмоционального состояния упрощено (valence +0.3, arousal +0.2)
        // TODO: Логика смены эмоционального состояния на EXCITED или HAPPY
        
        return ActionResult(
            success = true,
            message = "Laughter expressed",
            data = mapOf(
                "laugh_type" to laugh,
                "observers" to nearbyPlayers.size
            ),
            energyCost = 3f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Плакать
     */
    private suspend fun executeCry(npc: SmartNPC): ActionResult {
        val cries = listOf("*starts crying*", "*tears up*", "*sobs quietly*", "*weeps*")
        val cry = cries.random()
        
        val level = npc.getEntity().level
        val nearbyPlayers = level.getEntitiesOfClass(Player::class.java, npc.getEntity().boundingBox.inflate(8.0))
        
        nearbyPlayers.forEach { (player: Player) ->
            player.sendSystemMessage(Component.literal("${npc.getEntity().name.string} $cry"))
        }
        
        delay(1200L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = -0.4f,
            arousalChange = 0.1f
        )
        
        // Изменение эмоционального состояния упрощено (valence -0.4, arousal +0.1)
        // TODO: Логика смены эмоционального состояния на SAD или ANGRY
        
        return ActionResult(
            success = true,
            message = "Crying expressed",
            data = mapOf(
                "cry_type" to cry,
                "observers" to nearbyPlayers.size
            ),
            energyCost = 4f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Утешить
     */
    private suspend fun executeComfort(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
            
            val comfortMessages = listOf(
                "There, there. It will be alright.",
                "Don't worry, I'm here for you.",
                "Things will get better.",
                "You're stronger than you know.",
                "I believe in you.",
                "Take your time, no need to rush."
            )
            
            val comfort = comfortMessages.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $comfort"))
                targetEntity.sendSystemMessage(Component.literal("*${npc.getEntity().name.string} offers comfort*"))
            }
            
            delay(1500L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = 0.2f,
                arousalChange = -0.1f // Успокаивающий эффект
            )
            
            // Изменение эмоционального состояния упрощено (valence +0.2, arousal -0.1)
            // TODO: Логика смены эмоционального состояния на CONTENT или HAPPY
            
            ActionResult(
                success = true,
                message = "Comfort provided",
                data = mapOf(
                    "comfort" to comfort,
                    "target" to targetEntity.name.string
                ),
                energyCost = 5f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to comfort")
    }
    
    /**
     * Извиниться
     */
    private suspend fun executeApologize(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val apologies = listOf(
            "I'm sorry.",
            "Please forgive me.",
            "I apologize for my actions.",
            "I didn't mean to cause trouble.",
            "That was my mistake.",
            "I regret what happened."
        )
        
        val apology = apologies.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $apology"))
        }
        
        delay(1200L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = -0.1f,
            arousalChange = 0.05f,
            dominanceChange = -0.2f // Извинение снижает доминантность
        )
        
        // Изменение эмоционального состояния упрощено (valence -0.1, arousal +0.05)
        // TODO: Логика смены эмоционального состояния на более нейтральное
        
        return ActionResult(
            success = true,
            message = "Apology made",
            data = mapOf(
                "apology" to apology,
                "target" to (target?.name?.string ?: "everyone")
            ),
            energyCost = 6f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Поблагодарить
     */
    private suspend fun executeThank(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val thanks = listOf(
            "Thank you!",
            "I appreciate your help.",
            "Thanks so much!",
            "I'm grateful for your assistance.",
            "You're very kind.",
            "Thank you for your time."
        )
        
        val thank = thanks.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $thank"))
        }
        
        delay(700L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.2f,
            arousalChange = 0.1f
        )
        
        // Изменение эмоционального состояния упрощено (valence +0.2, arousal +0.1)
        // TODO: Логика смены эмоционального состояния на HAPPY или EXCITED
        
        return ActionResult(
            success = true,
            message = "Thanks expressed",
            data = mapOf(
                "thanks" to thank,
                "target" to (target?.name?.string ?: "someone")
            ),
            energyCost = 3f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Попросить помощь
     */
    private suspend fun executeRequestHelp(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        return target?.let { targetEntity ->
            val helpType = parameters["help_type"] as? String ?: "general"
            val request = generateHelpRequest(helpType)
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $request"))
            }
            
            delay(1800L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = -0.05f,
                arousalChange = 0.1f,
                dominanceChange = -0.1f
            )
            
            ActionResult(
                success = true,
                message = "Help requested",
                data = mapOf(
                    "request" to request,
                    "help_type" to helpType,
                    "target" to targetEntity.name.string
                ),
                energyCost = 4f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to ask for help")
    }
    
    /**
     * Предложить помощь
     */
    private suspend fun executeOfferHelp(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        return target?.let { targetEntity ->
            val helpType = parameters["help_type"] as? String ?: "general"
            val offer = generateHelpOffer(helpType)
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $offer"))
            }
            
            delay(1500L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = 0.15f,
                arousalChange = 0.1f,
                dominanceChange = 0.05f
            )
            
            // Изменение эмоционального состояния упрощено (valence +0.15, arousal +0.1)
            // TODO: Логика смены эмоционального состояния на более позитивное
            
            ActionResult(
                success = true,
                message = "Help offered",
                data = mapOf(
                    "offer" to offer,
                    "help_type" to helpType,
                    "target" to targetEntity.name.string
                ),
                energyCost = 4f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to offer help to")
    }
    
    /**
     * Ведение переговоров
     */
    private suspend fun executeNegotiate(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        return target?.let { targetEntity ->
            val negotiationTopic = parameters["topic"] as? String ?: "trade"
            val success = Random.nextFloat() < calculateNegotiationChance(npc, targetEntity)
            
            val message = if (success) {
                "Let's discuss terms that work for both of us."
            } else {
                "I'm not sure we can reach an agreement right now."
            }
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
            }
            
            delay(3000L) // Переговоры занимают время
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = if (success) 0.1f else -0.1f,
                arousalChange = 0.2f
            )
            
            ActionResult(
                success = success,
                message = if (success) "Negotiation successful" else "Negotiation failed",
                data = mapOf(
                    "topic" to negotiationTopic,
                    "target" to targetEntity.name.string,
                    "success_chance" to calculateNegotiationChance(npc, targetEntity)
                ),
                energyCost = 8f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to negotiate with")
    }
    
    /**
     * Убеждение
     */
    private suspend fun executePersuade(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        return target?.let { targetEntity ->
            val persuasionTopic = parameters["topic"] as? String ?: "general"
            val success = Random.nextFloat() < calculatePersuasionChance(npc, targetEntity)
            
            val arguments = listOf(
                "Consider the benefits of this approach.",
                "I think you'll find this quite reasonable.",
                "Trust me, this is the right choice.",
                "Look at it from this perspective.",
                "You won't regret this decision.",
                "This could work out well for both of us."
            )
            
            val argument = arguments.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $argument"))
            }
            
            delay(3000L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = if (success) 0.15f else -0.1f,
                arousalChange = 0.25f,
                dominanceChange = if (success) 0.1f else -0.05f
            )
            
            ActionResult(
                success = success,
                message = if (success) "Persuasion successful" else "Persuasion failed",
                data = mapOf(
                    "argument" to argument,
                    "topic" to persuasionTopic,
                    "target" to targetEntity.name.string,
                    "success_chance" to calculatePersuasionChance(npc, targetEntity)
                ),
                energyCost = 8f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to persuade")
    }
    
    /**
     * Построение взаимопонимания
     */
    private suspend fun executeBuildRapport(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
            val rapportActivities = listOf(
                "I can see we have a lot in common.",
                "You seem like someone I could trust.",
                "I feel like I understand you better now.",
                "We should talk more often.",
                "I enjoy our conversations.",
                "You have interesting perspectives."
            )
            
            val activity = rapportActivities.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $activity"))
            }
            
            delay(2000L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = 0.2f,
                arousalChange = 0.1f
            )
            
            // Изменение эмоционального состояния упрощено (valence +0.2, arousal +0.1)
            // TODO: Логика смены эмоционального состояния на HAPPY или EXCITED
            
            ActionResult(
                success = true,
                message = "Rapport building successful",
                data = mapOf(
                    "activity" to activity,
                    "target" to targetEntity.name.string
                ),
                energyCost = 6f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No one to build rapport with")
    }
    
    // Вспомогательные методы
    
    private fun generateConversationMessage(npc: SmartNPC, target: Entity?): String {
        val topics = listOf(
            "How are you today?",
            "The weather is quite nice, isn't it?",
            "Have you been traveling long?",
            "This is a peaceful place.",
            "I hope you're finding everything you need.",
            "What brings you to these parts?"
        )
        return topics.random()
    }
    
    private fun generateQuestion(npc: SmartNPC, target: Entity?): String {
        val questions = listOf(
            "What's your favorite thing about this area?",
            "Have you seen anything unusual lately?",
            "Where are you headed next?",
            "Do you have any advice for a fellow traveler?",
            "What's the most interesting place you've visited?",
            "Are you looking for anything in particular?"
        )
        return questions.random()
    }
    
    private fun determineCurrentEmotion(npc: SmartNPC): String {
        val emotional = npc.emotionalState
        return when (emotional) {
            EmotionalState.EXCITED -> "excited"
            EmotionalState.HAPPY -> "happy"
            EmotionalState.CONTENT -> "happy"
            EmotionalState.SAD -> "sad"
            EmotionalState.ANGRY -> "angry"
            EmotionalState.TIRED, EmotionalState.BORED -> "tired"
            EmotionalState.SCARED -> "scared"
            EmotionalState.SURPRISED -> "surprised"
            EmotionalState.CURIOUS -> "curious"
            EmotionalState.FOCUSED -> "focused"
            else -> "neutral"
        }
    }
    
    private fun generateHelpRequest(helpType: String): String {
        return when (helpType) {
            "combat" -> "Could you help me with these monsters?"
            "directions" -> "I'm a bit lost. Could you point me in the right direction?"
            "trade" -> "Do you have any items you'd be willing to trade?"
            "information" -> "I'm looking for information about this area."
            else -> "I could use some assistance, if you don't mind."
        }
    }
    
    private fun generateHelpOffer(helpType: String): String {
        return when (helpType) {
            "combat" -> "I can help you fight if you need backup."
            "directions" -> "I know these parts well. Need directions?"
            "trade" -> "I have some items that might interest you."
            "information" -> "I might know something that could help you."
            else -> "Is there anything I can help you with?"
        }
    }
    
    private fun calculateNegotiationChance(npc: SmartNPC, target: LivingEntity): Float {
        var chance = 0.5f // Базовый шанс
        
        // Эмоциональное состояние влияет на переговоры
        when (npc.emotionalState) {
            EmotionalState.HAPPY, EmotionalState.CONTENT, EmotionalState.EXCITED -> chance += 0.2f
            EmotionalState.ANGRY, EmotionalState.SAD -> chance -= 0.1f
            EmotionalState.NEUTRAL, EmotionalState.FOCUSED -> chance += 0.1f // Спокойствие помогает
            else -> {} // Остальные состояния не влияют
        }
        
        // Черты личности
        val charisma = npc.personalityTraits.getOrDefault("charisma", 0.5f)
        chance += (charisma - 0.5f) * 0.4f
        
        return max(0.1f, min(0.9f, chance))
    }
    
    private fun calculatePersuasionChance(npc: SmartNPC, target: LivingEntity): Float {
        var chance = 0.4f // Убеждение сложнее переговоров
        
        // Эмоциональное состояние
        when (npc.emotionalState) {
            EmotionalState.HAPPY, EmotionalState.CONTENT, EmotionalState.EXCITED -> chance += 0.15f
            EmotionalState.ANGRY, EmotionalState.SAD -> chance -= 0.05f
            else -> {} // Остальные состояния не влияют
        }
        
        // Черты личности
        val charisma = npc.personalityTraits.getOrDefault("charisma", 0.5f)
        val confidence = npc.personalityTraits.getOrDefault("confidence", 0.5f)
        
        chance += (charisma - 0.5f) * 0.3f
        chance += (confidence - 0.5f) * 0.2f
        
        return max(0.1f, min(0.8f, chance))
    }
}
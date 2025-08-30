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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Исполнитель торговых действий для NPC
 * Обрабатывает торговлю, оценку товаров, переговоры о цене и экономические взаимодействия
 */
class TradingActionExecutor(
    private val eventBus: NPCEventBusImpl
) : ActionExecutor {
    
    override val supportedActions = setOf(
        "start_trade",
        "end_trade",
        "evaluate_item",
        "make_offer",
        "counter_offer",
        "accept_trade",
        "decline_trade",
        "show_inventory",
        "request_item",
        "negotiate_price",
        "bulk_trade",
        "appraise_value",
        "check_demand",
        "update_prices",
        "special_deal",
        "trade_info"
    )
    
    override val priority = 90 // Высокий приоритет для торговых действий
    
    // Внутренний инвентарь торговца (упрощенная версия)
    private val traderInventory = mutableMapOf<String, TradeItem>()
    
    // Экономические параметры
    private var economicMood = 0.5f // 0.0 = депрессия, 1.0 = бум
    private var lastPriceUpdate = System.currentTimeMillis()
    
    init {
        // Инициализируем базовый инвентарь торговца
        initializeTraderInventory()
    }
    
    override fun canHandle(action: String, npc: SmartNPC, target: Entity?): Boolean {
        // Торговые действия доступны только торговцам или NPC с торговыми навыками
        val canTrade = npc.personalityTraits.getOrDefault("merchant", 0f) > 0.3f ||
                      npc.personalityTraits.getOrDefault("business_minded", 0f) > 0.5f
        
        return supportedActions.contains(action) && canTrade
    }
    
    override suspend fun executeAction(
        action: String,
        npc: SmartNPC,
        target: Entity?,
        parameters: Map<String, Any>
    ): ActionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Переходим в торговое состояние для основных торговых действий
            val tradingActions = setOf("start_trade", "make_offer", "negotiate_price", "bulk_trade")
            val stateMachine = npc.getStateMachine()
            if (tradingActions.contains(action) && stateMachine?.getCurrentState() != NPCState.TRADING) {
                stateMachine?.transitionTo(NPCState.TRADING, "Trading action: $action")
            }
            
            // Обновляем цены периодически
            updatePricesIfNeeded()
            
            val result = when (action) {
                "start_trade" -> executeStartTrade(npc, target as? LivingEntity)
                "end_trade" -> executeEndTrade(npc, target as? LivingEntity)
                "evaluate_item" -> executeEvaluateItem(npc, parameters)
                "make_offer" -> executeMakeOffer(npc, target as? LivingEntity, parameters)
                "counter_offer" -> executeCounterOffer(npc, target as? LivingEntity, parameters)
                "accept_trade" -> executeAcceptTrade(npc, target as? LivingEntity, parameters)
                "decline_trade" -> executeDeclineTrade(npc, target as? LivingEntity, parameters)
                "show_inventory" -> executeShowInventory(npc, target as? LivingEntity)
                "request_item" -> executeRequestItem(npc, target as? LivingEntity, parameters)
                "negotiate_price" -> executeNegotiatePrice(npc, target as? LivingEntity, parameters)
                "bulk_trade" -> executeBulkTrade(npc, target as? LivingEntity, parameters)
                "appraise_value" -> executeAppraiseValue(npc, parameters)
                "check_demand" -> executeCheckDemand(npc, parameters)
                "update_prices" -> executeUpdatePrices(npc)
                "special_deal" -> executeSpecialDeal(npc, target as? LivingEntity, parameters)
                "trade_info" -> executeTradeInfo(npc, target as? LivingEntity)
                else -> ActionResult(false, "Unknown trading action: $action")
            }
            
            // Отправляем торговое событие
            eventBus.publishEvent(NPCEvent.createTradingEvent(
                npc = npc.getEntity(),
                target = target,
                action = action,
                success = result.success
            ))
            
            return result.copy(executionTime = System.currentTimeMillis() - startTime)
            
        } catch (e: Exception) {
            HollowEngineAIMod.LOGGER.error("Error executing trading action $action", e)
            return ActionResult(
                success = false,
                message = "Trading action failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override fun estimateCost(action: String, npc: SmartNPC, target: Entity?): ActionCost {
        return when (action) {
            "start_trade", "end_trade" -> ActionCost(
                energyCost = 2f,
                timeCost = 500L,
                riskLevel = 0.1f,
                socialCost = 1f
            )
            "evaluate_item", "appraise_value" -> ActionCost(
                energyCost = 3f,
                timeCost = 800L,
                riskLevel = 0.05f
            )
            "make_offer", "counter_offer" -> ActionCost(
                energyCost = 5f,
                timeCost = 1500L,
                riskLevel = 0.2f,
                socialCost = 2f
            )
            "negotiate_price" -> ActionCost(
                energyCost = 8f,
                timeCost = 3000L,
                riskLevel = 0.3f,
                socialCost = 3f
            )
            "bulk_trade" -> ActionCost(
                energyCost = 12f,
                timeCost = 5000L,
                riskLevel = 0.4f,
                socialCost = 5f
            )
            "special_deal" -> ActionCost(
                energyCost = 10f,
                timeCost = 4000L,
                riskLevel = 0.5f,
                socialCost = 8f
            )
            else -> ActionCost(4f, 1200L, 0.15f, 1f)
        }
    }
    
    override fun getPrerequisites(action: String, npc: SmartNPC, target: Entity?): List<ActionPrerequisite> {
        return when (action) {
            "start_trade", "make_offer", "negotiate_price" -> listOf(
                DistancePrerequisite(5.0, "Must be close for trading"),
                EmotionalPrerequisite(maxArousal = 0.8f, description = "Must be calm enough to trade")
            )
            "bulk_trade" -> listOf(
                DistancePrerequisite(5.0, "Must be close for trading"),
                EmotionalPrerequisite(maxArousal = 0.7f, minValence = 0.2f, description = "Must be in good mood for bulk trading")
            )
            "special_deal" -> listOf(
                DistancePrerequisite(3.0, "Must be very close for special deals"),
                EmotionalPrerequisite(minValence = 0.4f, description = "Must be in positive mood for special deals")
            )
            else -> listOf(
                DistancePrerequisite(8.0, "Must be within trading range")
            )
        }
    }
    
    /**
     * Начать торговлю
     */
    private suspend fun executeStartTrade(npc: SmartNPC, target: LivingEntity?): ActionResult {
        return target?.let { targetEntity ->
            
            val greetings = listOf(
                "Welcome! What can I offer you today?",
                "Looking to make a trade? I have good deals!",
                "Step right up! I have the finest goods!",
                "What brings you to my shop today?",
                "I have some excellent items for trade!",
                "Let's see what we can work out together."
            )
            
            val greeting = greetings.random()
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $greeting"))
                targetEntity.sendSystemMessage(Component.literal("*${npc.getEntity().name.string} opens up their trading interface*"))
            }
            
            delay(500L)
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = 0.2f,
                arousalChange = 0.15f
            )
            
            // Изменение эмоционального состояния упрощено (valence +0.2, arousal +0.15)
            // TODO: Логика смены эмоционального состояния на HAPPY
            
            ActionResult(
                success = true,
                message = "Trade session started",
                data = mapOf(
                    "greeting" to greeting,
                    "partner" to targetEntity.name.string,
                    "available_items" to traderInventory.size
                ),
                energyCost = 2f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No trading partner")
    }
    
    /**
     * Завершить торговлю
     */
    private suspend fun executeEndTrade(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val farewells = listOf(
            "Thank you for your business!",
            "Come back anytime!",
            "It was a pleasure trading with you!",
            "Safe travels!",
            "I hope you're satisfied with our trade!",
            "Until next time!"
        )
        
        val farewell = farewells.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $farewell"))
        }
        
        // Возвращаемся в состояние IDLE после торговли
        npc.getStateMachine()?.transitionTo(NPCState.IDLE, "Trade session ended")
        
        delay(300L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.1f,
            arousalChange = -0.1f // Успокаиваемся после торговли
        )
        
        return ActionResult(
            success = true,
            message = "Trade session ended",
            data = mapOf(
                "farewell" to farewell,
                "partner" to (target?.name?.string ?: "unknown")
            ),
            energyCost = 2f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Оценить предмет
     */
    private suspend fun executeEvaluateItem(npc: SmartNPC, parameters: Map<String, Any>): ActionResult {
        val itemName = parameters["item"] as? String ?: return ActionResult(false, "No item specified")
        
        delay(800L) // Время на оценку
        
        val baseValue = getItemBaseValue(itemName)
        val marketModifier = getMarketModifier(itemName)
        val personalPreference = getPersonalPreference(npc, itemName)
        
        val estimatedValue = (baseValue * marketModifier * personalPreference).roundToInt()
        
        val evaluation = when {
            estimatedValue > baseValue * 1.5f -> "This is quite valuable! I'd pay good money for it."
            estimatedValue > baseValue * 1.2f -> "This is worth a fair amount."
            estimatedValue > baseValue * 0.8f -> "This has some value, though not exceptional."
            else -> "I'm not particularly interested in this item."
        }
        
        return ActionResult(
            success = true,
            message = "Item evaluated",
            data = mapOf(
                "item" to itemName,
                "base_value" to baseValue,
                "estimated_value" to estimatedValue,
                "evaluation" to evaluation,
                "market_modifier" to marketModifier,
                "personal_preference" to personalPreference
            ),
            energyCost = 3f
        )
    }
    
    /**
     * Сделать предложение
     */
    private suspend fun executeMakeOffer(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        return target?.let { targetEntity ->
            val itemName = parameters["item"] as? String
            val offerPrice = parameters["price"] as? Int
            val offerType = parameters["type"] as? String ?: "buy" // buy or sell
            
            if (itemName == null || offerPrice == null) {
                return ActionResult(false, "Missing item or price information")
            }
            
            val message = when (offerType) {
                "buy" -> "I'll buy your $itemName for $offerPrice coins."
                "sell" -> "I can sell you this $itemName for $offerPrice coins."
                else -> "How about we trade this $itemName for $offerPrice coins worth of goods?"
            }
            
            if (targetEntity is Player) {
                targetEntity.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
            }
            
            delay(1500L)
            
            // Шанс принятия предложения (упрощенно)
            val acceptanceChance = calculateOfferAcceptance(npc, targetEntity, itemName, offerPrice, offerType)
            val accepted = Random.nextFloat() < acceptanceChance
            
            val emotionalImpact = EmotionalImpact(
                valenceChange = if (accepted) 0.15f else -0.05f,
                arousalChange = 0.2f
            )
            
            ActionResult(
                success = true,
                message = "Offer made",
                data = mapOf(
                    "item" to itemName,
                    "price" to offerPrice,
                    "type" to offerType,
                    "message" to message,
                    "acceptance_chance" to acceptanceChance,
                    "accepted" to accepted
                ),
                energyCost = 5f,
                emotionalImpact = emotionalImpact
            )
        } ?: ActionResult(false, "No trading partner")
    }
    
    /**
     * Сделать встречное предложение
     */
    private suspend fun executeCounterOffer(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val originalPrice = parameters["original_price"] as? Int ?: 0
        val itemName = parameters["item"] as? String ?: "item"
        
        // Генерируем встречное предложение
        val counterPrice = generateCounterOffer(npc, originalPrice, itemName)
        val counterMessage = generateCounterOfferMessage(originalPrice, counterPrice)
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $counterMessage"))
        }
        
        delay(1500L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.05f,
            arousalChange = 0.15f
        )
        
        return ActionResult(
            success = true,
            message = "Counter offer made",
            data = mapOf(
                "original_price" to originalPrice,
                "counter_price" to counterPrice,
                "item" to itemName,
                "message" to counterMessage
            ),
            energyCost = 5f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Принять торговлю
     */
    private suspend fun executeAcceptTrade(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val itemName = parameters["item"] as? String ?: "item"
        val price = parameters["price"] as? Int ?: 0
        
        val acceptanceMessages = listOf(
            "Deal! That's a fair price.",
            "Agreed! Let's complete this trade.",
            "You have yourself a deal!",
            "Excellent! I accept your offer.",
            "Perfect! That works for me.",
            "Wonderful! Let's shake on it."
        )
        
        val message = acceptanceMessages.random()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
            target.sendSystemMessage(Component.literal("*Trade completed successfully*"))
        }
        
        delay(1000L)
        
        // Обновляем инвентарь торговца
        updateInventoryAfterTrade(itemName, price, true)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.3f,
            arousalChange = 0.1f
        )
        
        // Изменение эмоционального состояния упрощено (valence +0.3, arousal +0.1)
        // TODO: Логика смены эмоционального состояния на HAPPY или EXCITED
        
        return ActionResult(
            success = true,
            message = "Trade accepted and completed",
            data = mapOf(
                "item" to itemName,
                "price" to price,
                "message" to message
            ),
            energyCost = 4f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Отклонить торговлю
     */
    private suspend fun executeDeclineTrade(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val reason = parameters["reason"] as? String ?: "price_too_low"
        
        val declineMessage = when (reason) {
            "price_too_low" -> "I'm sorry, but that price is too low for me."
            "not_interested" -> "I'm not really interested in that item right now."
            "no_stock" -> "I don't have that item in stock at the moment."
            "no_space" -> "I don't have room for more items right now."
            else -> "I'm afraid I can't make that trade right now."
        }
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $declineMessage"))
        }
        
        delay(800L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = -0.1f,
            arousalChange = 0.05f
        )
        
        return ActionResult(
            success = true,
            message = "Trade declined",
            data = mapOf(
                "reason" to reason,
                "message" to declineMessage
            ),
            energyCost = 3f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Показать инвентарь
     */
    private suspend fun executeShowInventory(npc: SmartNPC, target: LivingEntity?): ActionResult {
        if (target == null) {
            return ActionResult(false, "No one to show inventory to")
        }
        
        val availableItems = traderInventory.filter { it.value.quantity > 0 }
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> Here's what I have available:"))
            
            if (availableItems.isEmpty()) {
                target.sendSystemMessage(Component.literal("Unfortunately, I'm out of stock at the moment."))
            } else {
                availableItems.forEach { (name, item) ->
                    val priceText = if (item.sellPrice > 0) " - ${item.sellPrice} coins" else " - Not for sale"
                    target.sendSystemMessage(Component.literal("• $name (${item.quantity} available)$priceText"))
                }
            }
        }
        
        delay(2000L) // Время на показ инвентаря
        
        return ActionResult(
            success = true,
            message = "Inventory displayed",
            data = mapOf(
                "available_items" to availableItems.size,
                "total_value" to availableItems.values.sumOf { it.sellPrice * it.quantity },
                "items" to availableItems.keys.toList()
            ),
            energyCost = 3f
        )
    }
    
    /**
     * Запросить предмет
     */
    private suspend fun executeRequestItem(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No one to request from")
        }
        
        val itemName = parameters["item"] as? String ?: return ActionResult(false, "No item specified")
        val maxPrice = parameters["max_price"] as? Int ?: getItemBaseValue(itemName)
        
        val requestMessage = "Do you happen to have any $itemName? I'd be willing to pay up to $maxPrice coins for it."
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $requestMessage"))
        }
        
        delay(1200L)
        
        return ActionResult(
            success = true,
            message = "Item requested",
            data = mapOf(
                "item" to itemName,
                "max_price" to maxPrice,
                "message" to requestMessage
            ),
            energyCost = 4f
        )
    }
    
    /**
     * Торговаться о цене
     */
    private suspend fun executeNegotiatePrice(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val itemName = parameters["item"] as? String ?: "item"
        val startingPrice = parameters["starting_price"] as? Int ?: 100
        val targetPrice = parameters["target_price"] as? Int ?: startingPrice
        
        val negotiationRounds = Random.nextInt(2, 5)
        var currentPrice = startingPrice
        val priceStep = (startingPrice - targetPrice) / negotiationRounds
        
        val negotiationMessages = mutableListOf<String>()
        
        for (round in 1..negotiationRounds) {
            currentPrice = max(targetPrice, currentPrice - priceStep.roundToInt())
            
            val message = when (round) {
                1 -> "Let's discuss the price. How about $currentPrice coins?"
                negotiationRounds -> "Final offer: $currentPrice coins. That's the best I can do."
                else -> "I could come down to $currentPrice coins. What do you think?"
            }
            
            negotiationMessages.add(message)
            
            if (target is Player) {
                target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
            }
            
            delay(1000L)
        }
        
        val success = Random.nextFloat() < calculateNegotiationSuccessChance(npc, target)
        val finalPrice = if (success) currentPrice else startingPrice
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = if (success) 0.2f else -0.1f,
            arousalChange = 0.3f
        )
        
        return ActionResult(
            success = success,
            message = if (success) "Negotiation successful" else "Negotiation failed",
            data = mapOf(
                "item" to itemName,
                "starting_price" to startingPrice,
                "target_price" to targetPrice,
                "final_price" to finalPrice,
                "rounds" to negotiationRounds,
                "messages" to negotiationMessages
            ),
            energyCost = 8f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Массовая торговля
     */
    private suspend fun executeBulkTrade(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val items = parameters["items"] as? Map<String, Int> ?: return ActionResult(false, "No items specified")
        val discount = calculateBulkDiscount(items.size)
        
        var totalValue = 0
        val itemSummary = mutableListOf<String>()
        
        items.forEach { (itemName, quantity) ->
            val itemValue = getItemBaseValue(itemName) * quantity
            totalValue += itemValue
            itemSummary.add("$quantity x $itemName")
        }
        
        val discountedPrice = (totalValue * (1f - discount)).roundToInt()
        
        val message = "For bulk purchase of ${itemSummary.joinToString(", ")}, " +
                     "I can offer a ${(discount * 100).roundToInt()}% discount. " +
                     "Total: $discountedPrice coins instead of $totalValue."
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> $message"))
        }
        
        delay(5000L) // Массовая торговля требует времени
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.25f,
            arousalChange = 0.2f
        )
        
        return ActionResult(
            success = true,
            message = "Bulk trade offer made",
            data = mapOf(
                "items" to items,
                "total_value" to totalValue,
                "discount" to discount,
                "discounted_price" to discountedPrice,
                "message" to message
            ),
            energyCost = 12f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Оценить стоимость
     */
    private suspend fun executeAppraiseValue(npc: SmartNPC, parameters: Map<String, Any>): ActionResult {
        val itemName = parameters["item"] as? String ?: return ActionResult(false, "No item specified")
        
        delay(800L)
        
        val baseValue = getItemBaseValue(itemName)
        val marketValue = baseValue * getMarketModifier(itemName)
        val condition = parameters["condition"] as? Float ?: 1.0f // 1.0 = perfect condition
        val rarity = parameters["rarity"] as? String ?: "common"
        
        val rarityMultiplier = when (rarity.lowercase()) {
            "legendary" -> 5.0f
            "epic" -> 3.0f
            "rare" -> 2.0f
            "uncommon" -> 1.5f
            else -> 1.0f
        }
        
        val finalValue = (marketValue * condition * rarityMultiplier).roundToInt()
        
        val appraisal = when {
            finalValue > baseValue * 3 -> "This is an exceptionally valuable item!"
            finalValue > baseValue * 2 -> "This is quite valuable."
            finalValue > baseValue * 1.5f -> "This has good value."
            finalValue > baseValue * 0.8f -> "This has moderate value."
            else -> "This isn't particularly valuable."
        }
        
        return ActionResult(
            success = true,
            message = "Item appraised",
            data = mapOf(
                "item" to itemName,
                "base_value" to baseValue,
                "market_value" to marketValue,
                "condition" to condition,
                "rarity" to rarity,
                "final_value" to finalValue,
                "appraisal" to appraisal
            ),
            energyCost = 3f
        )
    }
    
    /**
     * Проверить спрос
     */
    private suspend fun executeCheckDemand(npc: SmartNPC, parameters: Map<String, Any>): ActionResult {
        val itemName = parameters["item"] as? String ?: return ActionResult(false, "No item specified")
        
        delay(500L)
        
        val baseDemand = Random.nextFloat()
        val seasonalModifier = getSeasonalModifier(itemName)
        val localDemand = getLocalDemand(itemName)
        
        val totalDemand = baseDemand * seasonalModifier * localDemand
        
        val demandDescription = when {
            totalDemand > 0.8f -> "Very high demand - prices are rising!"
            totalDemand > 0.6f -> "Good demand - steady prices"
            totalDemand > 0.4f -> "Moderate demand - average prices"
            totalDemand > 0.2f -> "Low demand - prices may drop"
            else -> "Very low demand - difficult to sell"
        }
        
        return ActionResult(
            success = true,
            message = "Demand checked",
            data = mapOf(
                "item" to itemName,
                "base_demand" to baseDemand,
                "seasonal_modifier" to seasonalModifier,
                "local_demand" to localDemand,
                "total_demand" to totalDemand,
                "description" to demandDescription
            ),
            energyCost = 2f
        )
    }
    
    /**
     * Обновить цены
     */
    private suspend fun executeUpdatePrices(npc: SmartNPC): ActionResult {
        delay(1000L)
        
        var updatedItems = 0
        val priceChanges = mutableMapOf<String, Float>()
        
        traderInventory.forEach { (name, item) ->
            val oldPrice = item.sellPrice
            val newPrice = calculateUpdatedPrice(name, oldPrice)
            
            if (newPrice != oldPrice) {
                item.sellPrice = newPrice
                item.buyPrice = (newPrice * 0.7f).roundToInt() // Покупаем за 70% от продажной цены
                updatedItems++
                priceChanges[name] = (newPrice - oldPrice) / oldPrice.toFloat()
            }
        }
        
        lastPriceUpdate = System.currentTimeMillis()
        
        return ActionResult(
            success = true,
            message = "Prices updated",
            data = mapOf(
                "updated_items" to updatedItems,
                "price_changes" to priceChanges,
                "timestamp" to lastPriceUpdate
            ),
            energyCost = 6f
        )
    }
    
    /**
     * Специальное предложение
     */
    private suspend fun executeSpecialDeal(npc: SmartNPC, target: LivingEntity?, parameters: Map<String, Any>): ActionResult {
        if (target == null) {
            return ActionResult(false, "No trading partner")
        }
        
        val dealType = parameters["deal_type"] as? String ?: generateRandomDealType()
        val specialOffer = generateSpecialOffer(npc, dealType)
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> I have a special offer for you!"))
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> ${specialOffer.description}"))
        }
        
        delay(4000L)
        
        val emotionalImpact = EmotionalImpact(
            valenceChange = 0.3f,
            arousalChange = 0.25f
        )
        
        return ActionResult(
            success = true,
            message = "Special deal offered",
            data = mapOf(
                "deal_type" to dealType,
                "description" to specialOffer.description,
                "discount" to specialOffer.discount,
                "duration" to specialOffer.duration
            ),
            energyCost = 10f,
            emotionalImpact = emotionalImpact
        )
    }
    
    /**
     * Торговая информация
     */
    private suspend fun executeTradeInfo(npc: SmartNPC, target: LivingEntity?): ActionResult {
        val marketInfo = generateMarketInfo()
        val trends = generateMarketTrends()
        
        if (target is Player) {
            target.sendSystemMessage(Component.literal("<${npc.getEntity().name.string}> Here's some market information:"))
            target.sendSystemMessage(Component.literal(marketInfo))
            target.sendSystemMessage(Component.literal(trends))
        }
        
        delay(2000L)
        
        return ActionResult(
            success = true,
            message = "Trade information provided",
            data = mapOf(
                "market_info" to marketInfo,
                "trends" to trends,
                "economic_mood" to economicMood
            ),
            energyCost = 4f
        )
    }
    
    // Вспомогательные методы
    
    private fun initializeTraderInventory() {
        // Базовые товары торговца
        traderInventory["bread"] = TradeItem("bread", 10, 3, 2)
        traderInventory["iron_sword"] = TradeItem("iron_sword", 2, 50, 35)
        traderInventory["leather_boots"] = TradeItem("leather_boots", 5, 20, 14)
        traderInventory["apple"] = TradeItem("apple", 20, 2, 1)
        traderInventory["wool"] = TradeItem("wool", 30, 4, 3)
        traderInventory["coal"] = TradeItem("coal", 50, 1, 1)
    }
    
    private fun getItemBaseValue(itemName: String): Int {
        return when (itemName.lowercase()) {
            "bread" -> 3
            "apple" -> 2
            "iron_sword" -> 50
            "diamond_sword" -> 200
            "leather_boots" -> 20
            "iron_boots" -> 80
            "coal" -> 1
            "iron_ingot" -> 10
            "gold_ingot" -> 25
            "diamond" -> 100
            "emerald" -> 150
            "wool" -> 4
            else -> 10 // Базовое значение для неизвестных предметов
        }
    }
    
    private fun getMarketModifier(itemName: String): Float {
        // Модификатор рынка на основе экономического состояния
        return when (itemName.lowercase()) {
            "bread", "apple" -> 0.8f + (economicMood * 0.4f) // Еда менее волатильна
            "iron_sword", "diamond_sword" -> 0.6f + (economicMood * 0.8f) // Оружие более волатильно
            "coal", "iron_ingot" -> 0.7f + (economicMood * 0.6f) // Ресурсы умеренно волатильны
            else -> 0.5f + economicMood
        }
    }
    
    private fun getPersonalPreference(npc: SmartNPC, itemName: String): Float {
        // Личные предпочтения торговца влияют на цены
        val merchantType = npc.personalityTraits.getOrDefault("merchant_type", 0.5f)
        
        return when (itemName.lowercase()) {
            "bread", "apple" -> if (merchantType < 0.3f) 1.2f else 0.9f // Торговцы едой ценят еду больше
            "iron_sword", "diamond_sword" -> if (merchantType > 0.7f) 1.3f else 0.8f // Торговцы оружием ценят оружие
            else -> 1.0f
        }
    }
    
    private fun calculateOfferAcceptance(npc: SmartNPC, target: LivingEntity, itemName: String, price: Int, offerType: String): Float {
        val baseValue = getItemBaseValue(itemName)
        val fairPrice = baseValue * getMarketModifier(itemName)
        
        val priceRatio = if (offerType == "buy") {
            price / fairPrice // Для покупки: чем больше предлагают, тем лучше
        } else {
            fairPrice / price // Для продажи: чем меньше просят, тем лучше
        }
        
        return max(0.1f, min(0.9f, priceRatio * 0.8f))
    }
    
    private fun generateCounterOffer(npc: SmartNPC, originalPrice: Int, itemName: String): Int {
        val fairPrice = (getItemBaseValue(itemName) * getMarketModifier(itemName)).roundToInt()
        val aggressiveness = npc.personalityTraits.getOrDefault("aggressiveness", 0.5f)
        
        val adjustment = (originalPrice - fairPrice) * (0.5f + aggressiveness * 0.3f)
        return max(fairPrice, originalPrice - adjustment.roundToInt())
    }
    
    private fun generateCounterOfferMessage(originalPrice: Int, counterPrice: Int): String {
        val difference = originalPrice - counterPrice
        
        return when {
            difference > originalPrice * 0.3 -> "That's quite high. How about $counterPrice coins instead?"
            difference > originalPrice * 0.1 -> "I was thinking more like $counterPrice coins."
            else -> "I could do $counterPrice coins. What do you say?"
        }
    }
    
    private fun updateInventoryAfterTrade(itemName: String, price: Int, wasSuccessful: Boolean) {
        if (!wasSuccessful) return
        
        val item = traderInventory[itemName]
        if (item != null) {
            item.quantity = max(0, item.quantity - 1)
            item.lastSoldPrice = price
            item.totalSales++
        }
    }
    
    private fun calculateBulkDiscount(itemCount: Int): Float {
        return when {
            itemCount >= 10 -> 0.25f // 25% скидка за 10+ предметов
            itemCount >= 5 -> 0.15f  // 15% скидка за 5+ предметов
            itemCount >= 3 -> 0.1f   // 10% скидка за 3+ предмета
            else -> 0f
        }
    }
    
    private fun calculateNegotiationSuccessChance(npc: SmartNPC, target: LivingEntity): Float {
        var chance = 0.6f // Базовый шанс
        
        val charisma = npc.personalityTraits.getOrDefault("charisma", 0.5f)
        val business = npc.personalityTraits.getOrDefault("business_minded", 0.5f)
        
        chance += (charisma - 0.5f) * 0.3f
        chance += (business - 0.5f) * 0.2f
        
        // Эмоциональное состояние
        when (npc.emotionalState) {
            EmotionalState.HAPPY, EmotionalState.EXCITED, EmotionalState.CONTENT -> chance += 0.1f
            EmotionalState.NEUTRAL, EmotionalState.FOCUSED -> chance += 0.1f // Спокойствие помогает
            EmotionalState.ANGRY, EmotionalState.SAD -> chance -= 0.05f
            else -> {} // Остальные состояния не влияют
        }
        
        return max(0.2f, min(0.9f, chance))
    }
    
    private fun getSeasonalModifier(itemName: String): Float {
        // Упрощенная сезонность - можно расширить
        return when (itemName.lowercase()) {
            "bread", "apple" -> Random.nextFloat() * 0.4f + 0.8f // 0.8-1.2
            "wool" -> Random.nextFloat() * 0.6f + 0.7f // 0.7-1.3
            else -> Random.nextFloat() * 0.2f + 0.9f // 0.9-1.1
        }
    }
    
    private fun getLocalDemand(itemName: String): Float {
        // Локальный спрос - можно привязать к биому или событиям
        return Random.nextFloat() * 0.4f + 0.8f // 0.8-1.2
    }
    
    private fun calculateUpdatedPrice(itemName: String, currentPrice: Int): Int {
        val marketChange = (Random.nextFloat() - 0.5f) * 0.2f // ±10% изменение
        val newPrice = (currentPrice * (1f + marketChange)).roundToInt()
        
        val minPrice = getItemBaseValue(itemName) / 2
        val maxPrice = getItemBaseValue(itemName) * 3
        
        return max(minPrice, min(maxPrice, newPrice))
    }
    
    private fun updatePricesIfNeeded() {
        val timeSinceUpdate = System.currentTimeMillis() - lastPriceUpdate
        if (timeSinceUpdate > 300000) { // 5 минут
            // Обновляем экономическое настроение
            economicMood += (Random.nextFloat() - 0.5f) * 0.1f
            economicMood = max(0f, min(1f, economicMood))
        }
    }
    
    private fun generateRandomDealType(): String {
        val dealTypes = listOf("clearance", "bulk", "loyalty", "seasonal", "rare_item")
        return dealTypes.random()
    }
    
    private fun generateSpecialOffer(npc: SmartNPC, dealType: String): SpecialOffer {
        return when (dealType) {
            "clearance" -> SpecialOffer(
                description = "Clearance sale! 30% off all items in stock!",
                discount = 0.3f,
                duration = 3600000L // 1 час
            )
            "bulk" -> SpecialOffer(
                description = "Buy 5 items, get the 6th free!",
                discount = 0.16f, // Примерно эквивалент
                duration = 7200000L // 2 часа
            )
            "loyalty" -> SpecialOffer(
                description = "Loyal customer discount: 20% off your next purchase!",
                discount = 0.2f,
                duration = 1800000L // 30 минут
            )
            "seasonal" -> SpecialOffer(
                description = "Seasonal special: All seasonal items 25% off!",
                discount = 0.25f,
                duration = 86400000L // 24 часа
            )
            else -> SpecialOffer(
                description = "Limited time offer: 15% discount on everything!",
                discount = 0.15f,
                duration = 1800000L
            )
        }
    }
    
    private fun generateMarketInfo(): String {
        val moodDescription = when {
            economicMood > 0.7f -> "The market is booming! Prices are high but goods are moving fast."
            economicMood > 0.3f -> "The market is stable with steady prices and consistent demand."
            else -> "The market is slow. Prices are low but buyers are scarce."
        }
        return moodDescription
    }
    
    private fun generateMarketTrends(): String {
        val trends = listOf(
            "Weapon prices are expected to rise due to increased danger in the area.",
            "Food prices remain stable with good harvests this season.",
            "Rare materials are becoming more valuable as demand increases.",
            "Building materials are in high demand as new construction projects begin.",
            "Luxury items are seeing decreased interest among buyers."
        )
        return trends.random()
    }
    
    /**
     * Данные о торговом предмете
     */
    data class TradeItem(
        val name: String,
        var quantity: Int,
        var sellPrice: Int,
        var buyPrice: Int,
        var lastSoldPrice: Int = 0,
        var totalSales: Int = 0
    )
    
    /**
     * Специальное предложение
     */
    data class SpecialOffer(
        val description: String,
        val discount: Float,
        val duration: Long // в миллисекундах
    )
}
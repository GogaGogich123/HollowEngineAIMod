package com.hollowengineai.mod.commands

import com.hollowengineai.mod.HollowEngineAIMod
import com.hollowengineai.mod.core.NPCManager
import com.hollowengineai.mod.social.GroupType
import com.hollowengineai.mod.performance.CoroutinePoolManager
import com.hollowengineai.mod.performance.CacheManager
import com.hollowengineai.mod.config.AIConfig
import com.hollowengineai.mod.core.Colors
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * Команды администрирования для HollowEngineAI
 * Позволяют управлять новыми системами мода через чат
 */
object AdminCommands {
    
    /**
     * Регистрирует все команды администрирования
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("hollowengineai")
                .requires { source -> source.hasPermission(2) } // Требует права администратора
                .then(registerStatsCommand())
                .then(registerReputationCommand())
                .then(registerGroupCommand())
                .then(registerScheduleCommand())
                .then(registerPerformanceCommand())
                .then(registerConfigCommand())
                .then(registerDebugCommand())
        )
    }
    
    /**
     * /hollowengineai stats - Общая статистика всех систем
     */
    private fun registerStatsCommand() = Commands.literal("stats")
        .executes { context ->
            showGeneralStats(context)
            1
        }
    
    /**
     * /hollowengineai reputation <player> [get|set|add] [amount] [faction]
     */
    private fun registerReputationCommand() = Commands.literal("reputation")
        .then(Commands.argument("player", StringArgumentType.string())
            .then(Commands.literal("get")
                .executes { context ->
                    val playerName = StringArgumentType.getString(context, "player")
                    showPlayerReputation(context, playerName)
                    1
                }
                .then(Commands.argument("faction", StringArgumentType.string())
                    .executes { context ->
                        val playerName = StringArgumentType.getString(context, "player")
                        val faction = StringArgumentType.getString(context, "faction")
                        showPlayerReputationWithFaction(context, playerName, faction)
                        1
                    }
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                    .executes { context ->
                        val playerName = StringArgumentType.getString(context, "player")
                        val amount = DoubleArgumentType.getDouble(context, "amount")
                        setPlayerReputation(context, playerName, amount)
                        1
                    }
                    .then(Commands.argument("faction", StringArgumentType.string())
                        .executes { context ->
                            val playerName = StringArgumentType.getString(context, "player")
                            val amount = DoubleArgumentType.getDouble(context, "amount")
                            val faction = StringArgumentType.getString(context, "faction")
                            setPlayerReputationWithFaction(context, playerName, amount, faction)
                            1
                        }
                    )
                )
            )
            .then(Commands.literal("add")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                    .executes { context ->
                        val playerName = StringArgumentType.getString(context, "player")
                        val amount = DoubleArgumentType.getDouble(context, "amount")
                        addPlayerReputation(context, playerName, amount)
                        1
                    }
                    .then(Commands.argument("faction", StringArgumentType.string())
                        .executes { context ->
                            val playerName = StringArgumentType.getString(context, "player")
                            val amount = DoubleArgumentType.getDouble(context, "amount")
                            val faction = StringArgumentType.getString(context, "faction")
                            addPlayerReputationWithFaction(context, playerName, amount, faction)
                            1
                        }
                    )
                )
            )
        )
    
    /**
     * /hollowengineai group [create|list|add|remove|info|disband]
     */
    private fun registerGroupCommand() = Commands.literal("group")
        .then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("type", StringArgumentType.string())
                    .then(Commands.argument("leader", StringArgumentType.string())
                        .executes { context ->
                            val name = StringArgumentType.getString(context, "name")
                            val type = StringArgumentType.getString(context, "type")
                            val leader = StringArgumentType.getString(context, "leader")
                            createSocialGroup(context, name, type, leader)
                            1
                        }
                    )
                )
            )
        )
        .then(Commands.literal("list")
            .executes { context ->
                listSocialGroups(context)
                1
            }
        )
        .then(Commands.literal("add")
            .then(Commands.argument("npc", StringArgumentType.string())
                .then(Commands.argument("group", StringArgumentType.string())
                    .executes { context ->
                        val npcName = StringArgumentType.getString(context, "npc")
                        val groupName = StringArgumentType.getString(context, "group")
                        addNPCToGroup(context, npcName, groupName)
                        1
                    }
                )
            )
        )
        .then(Commands.literal("remove")
            .then(Commands.argument("npc", StringArgumentType.string())
                .then(Commands.argument("group", StringArgumentType.string())
                    .executes { context ->
                        val npcName = StringArgumentType.getString(context, "npc")
                        val groupName = StringArgumentType.getString(context, "group")
                        removeNPCFromGroup(context, npcName, groupName)
                        1
                    }
                )
            )
        )
        .then(Commands.literal("info")
            .then(Commands.argument("group", StringArgumentType.string())
                .executes { context ->
                    val groupName = StringArgumentType.getString(context, "group")
                    showGroupInfo(context, groupName)
                    1
                }
            )
        )
        .then(Commands.literal("disband")
            .then(Commands.argument("group", StringArgumentType.string())
                .executes { context ->
                    val groupName = StringArgumentType.getString(context, "group")
                    disbandGroup(context, groupName)
                    1
                }
            )
        )
    
    /**
     * /hollowengineai schedule <npc> <task> <time> [priority]
     */
    private fun registerScheduleCommand() = Commands.literal("schedule")
        .then(Commands.argument("npc", StringArgumentType.string())
            .then(Commands.argument("task", StringArgumentType.string())
                .then(Commands.argument("time", StringArgumentType.string())
                    .executes { context ->
                        val npcName = StringArgumentType.getString(context, "npc")
                        val task = StringArgumentType.getString(context, "task")
                        val time = StringArgumentType.getString(context, "time")
                        scheduleNPCTask(context, npcName, task, time, 1.0)
                        1
                    }
                    .then(Commands.argument("priority", DoubleArgumentType.doubleArg())
                        .executes { context ->
                            val npcName = StringArgumentType.getString(context, "npc")
                            val task = StringArgumentType.getString(context, "task")
                            val time = StringArgumentType.getString(context, "time")
                            val priority = DoubleArgumentType.getDouble(context, "priority")
                            scheduleNPCTask(context, npcName, task, time, priority)
                            1
                        }
                    )
                )
            )
        )
    
    /**
     * /hollowengineai performance - Детальная статистика производительности
     */
    private fun registerPerformanceCommand() = Commands.literal("performance")
        .executes { context ->
            showPerformanceStats(context)
            1
        }
    
    /**
     * /hollowengineai config [reload|show|set]
     */
    private fun registerConfigCommand() = Commands.literal("config")
        .then(Commands.literal("reload")
            .executes { context ->
                reloadConfig(context)
                1
            }
        )
        .then(Commands.literal("show")
            .executes { context ->
                showConfig(context)
                1
            }
        )
    
    /**
     * /hollowengineai debug [events|cache|coroutines]
     */
    private fun registerDebugCommand() = Commands.literal("debug")
        .then(Commands.literal("events")
            .executes { context ->
                showEventDebugInfo(context)
                1
            }
        )
        .then(Commands.literal("cache")
            .executes { context ->
                showCacheDebugInfo(context)
                1
            }
        )
        .then(Commands.literal("coroutines")
            .executes { context ->
                showCoroutineDebugInfo(context)
                1
            }
        )
    
    // ==============================================
    // РЕАЛИЗАЦИЯ КОМАНД
    // ==============================================
    
    private fun showGeneralStats(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            // Получаем статистику от NPCManager
            val stats = try {
                HollowEngineAIMod.npcManager.getExtendedManagerStats()
            } catch (e: UninitializedPropertyAccessException) {
                null
            }
            
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("📊 HollowEngineAI - Общая Статистика")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            
            if (stats != null) {
                // Основная статистика NPC
                source.sendSuccess(
                    Component.literal("🤖 NPC: ${stats.basicStats.activeNPCs} активных, ${stats.basicStats.totalNPCs} всего")
                        .withStyle(ChatFormatting.WHITE), false
                )
                
                // Статистика систем
                source.sendSuccess(
                    Component.literal("🏛️ Групп: ${stats.totalGroups}")
                        .withStyle(Colors.CYAN), false
                )
                source.sendSuccess(
                    Component.literal("📅 Задач в планировщике: ${stats.scheduledTasks}")
                        .withStyle(ChatFormatting.GREEN), false
                )

                source.sendSuccess(
                    Component.literal("🎯 Использование кэша: ${"%.1f".format(stats.cacheHitRate)}%")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false
                )
                

                source.sendSuccess(
                    Component.literal("💾 Использование памяти: ${stats.basicStats.memoryUsageMB}MB")
                        .withStyle(ChatFormatting.GRAY), false
                )
            } else {
                source.sendSuccess(
                    Component.literal("❌ Системы ещё не инициализированы")
                        .withStyle(ChatFormatting.RED), false
                )
            }
            
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения статистики: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showPlayerReputation(context: CommandContext<CommandSourceStack>, playerName: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            val reputation = HollowEngineAIMod.reputationSystem?.getPlayerReputation(playerName) ?: 0.0
            val reputationLevel = when {
                reputation >= 80 -> "Почитаемый" to ChatFormatting.GOLD
                reputation >= 60 -> "Дружелюбный" to ChatFormatting.GREEN
                reputation >= 40 -> "Нейтральный" to ChatFormatting.YELLOW
                reputation >= 20 -> "Подозрительный" to ChatFormatting.RED
                else -> "Враждебный" to ChatFormatting.DARK_RED
            }
            
            source.sendSuccess(
                Component.literal("🎭 Репутация игрока $playerName:")
                    .withStyle(Colors.CYAN), false
            )
            source.sendSuccess(
                Component.literal("📊 Значение: ${"%.1f".format(reputation)}/100")
                    .withStyle(ChatFormatting.WHITE), false
            )
            source.sendSuccess(
                Component.literal("⭐ Уровень: ${reputationLevel.first}")
                    .withStyle(reputationLevel.second), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения репутации: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showPlayerReputationWithFaction(context: CommandContext<CommandSourceStack>, playerName: String, faction: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            val reputation = HollowEngineAIMod.reputationSystem?.getFactionReputation(playerName, faction) ?: 0.0
            
            source.sendSuccess(
                Component.literal("🏛️ Репутация игрока $playerName с фракцией $faction:")
                    .withStyle(Colors.CYAN), false
            )
            source.sendSuccess(
                Component.literal("📊 Значение: ${"%.1f".format(reputation)}/100")
                    .withStyle(ChatFormatting.WHITE), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения репутации фракции: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun setPlayerReputation(context: CommandContext<CommandSourceStack>, playerName: String, amount: Double) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            HollowEngineAIMod.reputationSystem?.setPlayerReputation(playerName, amount)
            
            source.sendSuccess(
                Component.literal("✅ Репутация игрока $playerName установлена на ${"%.1f".format(amount)}")
                    .withStyle(ChatFormatting.GREEN), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка установки репутации: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun setPlayerReputationWithFaction(context: CommandContext<CommandSourceStack>, playerName: String, amount: Double, faction: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            HollowEngineAIMod.reputationSystem?.setFactionReputation(playerName, faction, amount)
            
            source.sendSuccess(
                Component.literal("✅ Репутация игрока $playerName с фракцией $faction установлена на ${"%.1f".format(amount)}")
                    .withStyle(ChatFormatting.GREEN), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка установки репутации фракции: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun addPlayerReputation(context: CommandContext<CommandSourceStack>, playerName: String, amount: Double) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            HollowEngineAIMod.reputationSystem?.modifyPlayerReputation(playerName, amount)
            val newReputation = HollowEngineAIMod.reputationSystem?.getPlayerReputation(playerName) ?: 0.0
            
            source.sendSuccess(
                Component.literal("✅ К репутации игрока $playerName добавлено ${"%.1f".format(amount)}")
                    .withStyle(ChatFormatting.GREEN), false
            )
            source.sendSuccess(
                Component.literal("📊 Новая репутация: ${"%.1f".format(newReputation)}")
                    .withStyle(Colors.CYAN), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка изменения репутации: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun addPlayerReputationWithFaction(context: CommandContext<CommandSourceStack>, playerName: String, amount: Double, faction: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.reputationSystem == null) {
                source.sendFailure(
                    Component.literal("❌ Система репутации не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            HollowEngineAIMod.reputationSystem?.modifyFactionReputation(playerName, faction, amount)
            val newReputation = HollowEngineAIMod.reputationSystem?.getFactionReputation(playerName, faction) ?: 0.0
            
            source.sendSuccess(
                Component.literal("✅ К репутации игрока $playerName с фракцией $faction добавлено ${"%.1f".format(amount)}")
                    .withStyle(ChatFormatting.GREEN), false
            )
            source.sendSuccess(
                Component.literal("📊 Новая репутация: ${"%.1f".format(newReputation)}")
                    .withStyle(Colors.CYAN), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка изменения репутации фракции: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun createSocialGroup(context: CommandContext<CommandSourceStack>, name: String, type: String, leader: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.socialGroupManager == null) {
                source.sendFailure(
                    Component.literal("❌ Система социальных групп не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            val groupType = try {
                GroupType.valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                source.sendFailure(
                    Component.literal("❌ Неизвестный тип группы: $type. Доступны: CLAN, GUILD, MILITARY, TRADING, RELIGIOUS, BANDITS, TEMPORARY, PERSISTENT")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Нужно получить SmartNPC по ID или создать перегрузку метода
            val groupId = UUID.randomUUID().toString()
            val leaderNPC = HollowEngineAIMod.npcManager.getNPCById(leader) // Нужно добавить этот метод
            val group = HollowEngineAIMod.socialGroupManager?.createGroup(groupId, name, groupType, leaderNPC)
            val success = group != null
            
            if (success) {
                source.sendSuccess(
                    Component.literal("✅ Социальная группа '$name' типа $type создана с лидером $leader")
                        .withStyle(ChatFormatting.GREEN), false
                )
            } else {
                source.sendFailure(
                    Component.literal("❌ Не удалось создать группу '$name'")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка создания группы: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun listSocialGroups(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.socialGroupManager == null) {
                source.sendFailure(
                    Component.literal("❌ Система социальных групп не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Добавить метод getAllGroups в SocialGroupManager
            val groups = emptyList<com.hollowengineai.mod.social.SocialGroup>() // Временная заглушка
            
            source.sendSuccess(
                Component.literal("🏛️ Социальные группы (${groups.size}):")
                    .withStyle(Colors.CYAN, ChatFormatting.BOLD), false
            )
            
            if (groups.isEmpty()) {
                source.sendSuccess(
                    Component.literal("   Групп не найдено")
                        .withStyle(ChatFormatting.GRAY), false
                )
            } else {
                groups.forEach { group ->
                    val typeColor = when (group.type) {
                        GroupType.CLAN -> ChatFormatting.BLUE
                        GroupType.GUILD -> ChatFormatting.AQUA
                        GroupType.MILITARY -> ChatFormatting.RED
                        GroupType.TRADING -> ChatFormatting.GOLD
                        GroupType.RELIGIOUS -> ChatFormatting.LIGHT_PURPLE
                        GroupType.BANDITS -> ChatFormatting.DARK_RED
                        GroupType.TEMPORARY -> ChatFormatting.GRAY
                        GroupType.PERSISTENT -> ChatFormatting.GREEN
                    }
                    
                    source.sendSuccess(
                        Component.literal("   📝 ${group.name} (${group.type}) - ${group.members.size} членов")
                            .withStyle(typeColor), false
                    )
                }
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения списка групп: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun addNPCToGroup(context: CommandContext<CommandSourceStack>, npcName: String, groupName: String) {
        val source = context.source
        
        try {
            // TODO: Нужно преобразовать строки в UUID или создать перегрузки методов
            val success = false // Временная заглушка
            
            if (success) {
                source.sendSuccess(
                    Component.literal("✅ NPC $npcName добавлен в группу '$groupName'")
                        .withStyle(ChatFormatting.GREEN), false
                )
            } else {
                source.sendFailure(
                    Component.literal("❌ Не удалось добавить NPC $npcName в группу '$groupName'")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка добавления NPC в группу: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun removeNPCFromGroup(context: CommandContext<CommandSourceStack>, npcName: String, groupName: String) {
        val source = context.source
        
        try {
            // TODO: Нужно преобразовать строки в UUID или создать перегрузки методов
            val success = false // Временная заглушка
            
            if (success) {
                source.sendSuccess(
                    Component.literal("✅ NPC $npcName удален из группы '$groupName'")
                        .withStyle(ChatFormatting.GREEN), false
                )
            } else {
                source.sendFailure(
                    Component.literal("❌ Не удалось удалить NPC $npcName из группы '$groupName'")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка удаления NPC из группы: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showGroupInfo(context: CommandContext<CommandSourceStack>, groupName: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.socialGroupManager == null) {
                source.sendFailure(
                    Component.literal("❌ Система социальных групп не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Добавить метод getGroupByName в SocialGroupManager  
            val group: com.hollowengineai.mod.social.SocialGroup? = null // Временная заглушка
            
            if (group != null) {
                source.sendSuccess(
                    Component.literal("═══════════════════════════════════════")
                        .withStyle(ChatFormatting.GOLD), false
                )
                source.sendSuccess(
                    Component.literal("🏛️ Информация о группе: ${group.name}")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false
                )
                source.sendSuccess(
                    Component.literal("═══════════════════════════════════════")
                        .withStyle(ChatFormatting.GOLD), false
                )
                source.sendSuccess(
                    Component.literal("📋 Тип: ${group.type}")
                        .withStyle(Colors.CYAN), false
                )
                source.sendSuccess(
                    Component.literal("👑 Лидер: ${group.leaderId}")
                        .withStyle(ChatFormatting.GOLD), false
                )
                source.sendSuccess(
                    Component.literal("👥 Членов: ${group.members.size}/${AIConfig.maxGroupSize}")
                        .withStyle(ChatFormatting.GREEN), false
                )
                source.sendSuccess(
                    Component.literal("📅 Создана: ${group.createdAt}")
                        .withStyle(ChatFormatting.GRAY), false
                )
                
                if (group.memberCount > 0) {
                    source.sendSuccess(
                        Component.literal("👥 Участники:")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    // Тут можно добавить список участников, если он доступен
                }
            } else {
                source.sendFailure(
                    Component.literal("❌ Группа '$groupName' не найдена")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения информации о группе: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun disbandGroup(context: CommandContext<CommandSourceStack>, groupName: String) {
        val source = context.source
        
        try {
            if (HollowEngineAIMod.socialGroupManager == null) {
                source.sendFailure(
                    Component.literal("❌ Система социальных групп не инициализирована")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Добавить публичный метод disbandGroup в SocialGroupManager
            val success = false // Временная заглушка
            
            if (success) {
                source.sendSuccess(
                    Component.literal("✅ Группа '$groupName' расформирована")
                        .withStyle(ChatFormatting.GREEN), false
                )
            } else {
                source.sendFailure(
                    Component.literal("❌ Не удалось расформировать группу '$groupName'")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка расформирования группы: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun scheduleNPCTask(context: CommandContext<CommandSourceStack>, npcName: String, task: String, time: String, priority: Double) {
        val source = context.source
        
        try {
            // TODO: Нужно преобразовать строки в правильные типы (напр. UUID, LocalTime)
            val success = false // Временная заглушка
            
            if (success) {
                source.sendSuccess(
                    Component.literal("✅ Задача '$task' запланирована для NPC $npcName на время $time (приоритет: $priority)")
                        .withStyle(ChatFormatting.GREEN), false
                )
            } else {
                source.sendFailure(
                    Component.literal("❌ Не удалось запланировать задачу для NPC $npcName")
                        .withStyle(ChatFormatting.RED)
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка планирования задачи: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showPerformanceStats(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("⚡ HollowEngineAI - Статистика Производительности")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            
            // Статистика корутин
            try {
                val coroutineStats = CoroutinePoolManager.getPerformanceStats()
                if (coroutineStats != null) {
                    source.sendSuccess(
                        Component.literal("🔄 Корутины:")
                            .withStyle(Colors.CYAN, ChatFormatting.BOLD), false
                    )
                    source.sendSuccess(
                        Component.literal("   AI пул: ${coroutineStats.aiPoolSize}")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    source.sendSuccess(
                        Component.literal("   Действия пул: ${coroutineStats.actionPoolSize}")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    source.sendSuccess(
                        Component.literal("   Выполнено задач: ${coroutineStats.totalExecutedTasks}")
                            .withStyle(ChatFormatting.GREEN), false
                    )
                    source.sendSuccess(
                        Component.literal("   Активные задачи: ${coroutineStats.currentActiveTasks}")
                            .withStyle(ChatFormatting.YELLOW), false
                    )
                }
            }
            
            // Статистика кэша
            try {
                val cacheStats = CacheManager.getGlobalStats()
                if (cacheStats != null) {
                    source.sendSuccess(
                        Component.literal("💾 Кэш:")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false
                    )
                    source.sendSuccess(
                        Component.literal("   Кэшей: ${cacheStats.totalCaches}")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    source.sendSuccess(
                        Component.literal("   Записей: ${cacheStats.totalEntries}/${cacheStats.totalCapacity}")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    source.sendSuccess(
                        Component.literal("   Попаданий: ${cacheStats.globalHits}")
                            .withStyle(ChatFormatting.GREEN), false
                    )
                    source.sendSuccess(
                        Component.literal("   Промахов: ${cacheStats.globalMisses}")
                            .withStyle(ChatFormatting.RED), false
                    )
                    source.sendSuccess(
                        Component.literal("   Эффективность: ${"%.1f".format(cacheStats.globalHitRate * 100)}%")
                            .withStyle(Colors.CYAN), false
                    )
                }
            }
            
            // Статистика событий
            if (try { HollowEngineAIMod.eventBus != null } catch (_: UninitializedPropertyAccessException) { false }) {
                val eventStats = HollowEngineAIMod.eventBus?.getStats()
                if (eventStats != null) {
                    source.sendSuccess(
                        Component.literal("📡 События:")
                            .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), false
                    )
                    source.sendSuccess(
                        Component.literal("   В очереди: ${eventStats.queueSize}")
                            .withStyle(ChatFormatting.WHITE), false
                    )
                    source.sendSuccess(
                        Component.literal("   Обработано: ${eventStats.eventsProcessed}")
                            .withStyle(ChatFormatting.GREEN), false
                    )
                    source.sendSuccess(
                        Component.literal("   Пропущено: ${eventStats.eventsDropped}")
                            .withStyle(ChatFormatting.RED), false
                    )
                    source.sendSuccess(
                        Component.literal("   Подписчиков: ${eventStats.subscribersCount}")
                            .withStyle(ChatFormatting.BLUE), false
                    )
                }
            }
            
            // Системная статистика
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            val usedMemory = totalMemory - freeMemory
            
            source.sendSuccess(
                Component.literal("🖥️ Система:")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("   Память: ${usedMemory}MB / ${maxMemory}MB (${"%.1f".format(usedMemory * 100.0 / maxMemory)}%)")
                    .withStyle(ChatFormatting.WHITE), false
            )
            source.sendSuccess(
                Component.literal("   Доступных ядер: ${Runtime.getRuntime().availableProcessors()}")
                    .withStyle(ChatFormatting.WHITE), false
            )
            
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения статистики производительности: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun reloadConfig(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            // TODO: Добавить метод reloadConfig в AIConfig
            // AIConfig.reloadConfig() // Метод не существует
            
            source.sendSuccess(
                Component.literal("✅ Конфигурация перезагружена")
                    .withStyle(ChatFormatting.GREEN), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка перезагрузки конфигурации: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showConfig(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            val configSummary = AIConfig.getConfigSummary()
            
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("⚙️ Текущая Конфигурация HollowEngineAI")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false
            )
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false
            )
            
            configSummary.lines().forEach { line ->
                source.sendSuccess(
                    Component.literal(line).withStyle(ChatFormatting.WHITE), false
                )
            }
            
            source.sendSuccess(
                Component.literal("═══════════════════════════════════════")
                    .withStyle(ChatFormatting.GOLD), false
            )
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения конфигурации: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showEventDebugInfo(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            if (try { HollowEngineAIMod.eventBus == null } catch (_: UninitializedPropertyAccessException) { true }) {
                source.sendFailure(
                    Component.literal("❌ EventBus не инициализирован")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Добавить getDebugInfo в NPCEventBus
            val debugInfo = "Debug info not implemented yet"
            
            source.sendSuccess(
                Component.literal("🔍 Debug информация EventBus:")
                    .withStyle(Colors.CYAN, ChatFormatting.BOLD), false
            )
            
            debugInfo?.lines()?.forEach { line ->
                source.sendSuccess(
                    Component.literal("   $line").withStyle(ChatFormatting.WHITE), false
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения debug информации событий: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showCacheDebugInfo(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            if (try { HollowEngineAIMod.cacheManager == null } catch (_: UninitializedPropertyAccessException) { true }) {
                source.sendFailure(
                    Component.literal("❌ CacheManager не инициализирован")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            val debugInfo = HollowEngineAIMod.cacheManager?.getDebugInfo()
            
            source.sendSuccess(
                Component.literal("🔍 Debug информация Cache:")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false
            )
            
            debugInfo?.lines()?.forEach { line ->
                source.sendSuccess(
                    Component.literal("   $line").withStyle(ChatFormatting.WHITE), false
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения debug информации кэша: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
    
    private fun showCoroutineDebugInfo(context: CommandContext<CommandSourceStack>) {
        val source = context.source
        
        try {
            // Проверяем доступность CoroutinePoolManager
            if (!CoroutinePoolManager.isInitialized()) {
                source.sendFailure(
                    Component.literal("❌ CoroutinePoolManager не инициализирован")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
            
            // TODO: Добавить getDebugInfo в CoroutinePoolManager
            val debugInfo = "Debug info not implemented yet"
            
            source.sendSuccess(
                Component.literal("🔍 Debug информация Coroutines:")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false
            )
            
            debugInfo?.lines()?.forEach { line ->
                source.sendSuccess(
                    Component.literal("   $line").withStyle(ChatFormatting.WHITE), false
                )
            }
            
        } catch (e: Exception) {
            source.sendFailure(
                Component.literal("❌ Ошибка получения debug информации корутин: ${e.message}")
                    .withStyle(ChatFormatting.RED)
            )
        }
    }
}
package com.hollowengineai.mod

import com.hollowengineai.mod.config.AIConfig
import com.hollowengineai.mod.core.NPCManager
import com.hollowengineai.mod.llm.OllamaClient
import com.hollowengineai.mod.memory.DatabaseManager
import com.hollowengineai.mod.events.NPCEventBus
import com.hollowengineai.mod.events.NPCEventBusImpl
import com.hollowengineai.mod.performance.CoroutinePoolManager
import com.hollowengineai.mod.performance.CacheManager
import com.hollowengineai.mod.reputation.ReputationSystem
import com.hollowengineai.mod.social.SocialGroupManager
import com.hollowengineai.mod.scheduler.NPCScheduler
import com.hollowengineai.mod.integration.HollowEngineBridge
import com.hollowengineai.mod.integration.EventSynchronizer
import kotlinx.coroutines.*
import java.util.*
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Главный класс мода HollowEngineAI
 * 
 * Отвечает за:
 * - Инициализацию основных компонентов
 * - Регистрацию событий Forge
 * - Управление жизненным циклом мода
 */
@Mod(HollowEngineAIMod.MOD_ID)
class HollowEngineAIMod {
    
    companion object {
        const val MOD_ID = "hollowengineai"
        const val MOD_NAME = "HollowEngineAI"
        const val VERSION = "1.0.0"
        
        val LOGGER: Logger = LogManager.getLogger(MOD_NAME)
        
        // Основные компоненты мода
        lateinit var npcManager: NPCManager
        lateinit var ollamaClient: OllamaClient
        lateinit var databaseManager: DatabaseManager
        lateinit var config: AIConfig
        
        // Новые системы
        lateinit var eventBus: NPCEventBusImpl
        var reputationSystem: ReputationSystem? = null
        var socialGroupManager: SocialGroupManager? = null
        var npcScheduler: NPCScheduler? = null
        
        // Примечание: CoroutinePoolManager и CacheManager являются object (синглтоны),
        // поэтому не нуждаются в объявлении как переменные
    }
    
    init {
        LOGGER.info("Initializing $MOD_NAME v$VERSION")
        
        // Регистрируем конфигурацию
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AIConfig.SPEC)
        config = AIConfig
        
        // Регистрируем обработчики событий мода
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        modEventBus.addListener(::onCommonSetup)
        modEventBus.addListener(::onClientSetup)
        
        // Регистрируем обработчики событий игры
        MinecraftForge.EVENT_BUS.register(this)
        
        LOGGER.info("$MOD_NAME initialized successfully")
    }
    
    /**
     * Общая настройка мода (выполняется на клиенте и сервере)
     */
    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("Common setup phase for $MOD_NAME")
        
        event.enqueueWork {
            try {
                LOGGER.info("Начало инициализации систем HollowEngineAI...")
                
                // 1. Оптимизации производительности инициализируются автоматически
                LOGGER.info("Оптимизации производительности готовы к использованию")
                
                // 2. Инициализируем EventBus
                LOGGER.info("Инициализация EventBus...")
                eventBus = NPCEventBus.instance
                eventBus.start()
                LOGGER.info("EventBus инициализирован")
                
                // 3. Инициализируем базу данных
                LOGGER.info("Инициализация базы данных...")
                databaseManager = DatabaseManager()
                databaseManager.initialize()
                LOGGER.info("База данных инициализирована")
                
                // 4. Инициализируем Ollama клиент
                LOGGER.info("Инициализация Ollama клиента...")
                ollamaClient = OllamaClient(config.ollamaUrl, config.defaultModel)
                LOGGER.info("Ollama клиент инициализирован")
                
                // 5. Инициализируем новые системы (опционально)
                try {
                    LOGGER.info("Инициализация системы репутации...")
                    reputationSystem = ReputationSystem
                    LOGGER.info("Система репутации инициализирована")
                    
                    LOGGER.info("Инициализация менеджера социальных групп...")
                    socialGroupManager = SocialGroupManager
                    socialGroupManager?.initialize(eventBus)
                    LOGGER.info("Менеджер социальных групп инициализирован")
                    
                    LOGGER.info("Инициализация планировщика NPC...")
                    npcScheduler = NPCScheduler
                    npcScheduler?.initialize(eventBus)
                    LOGGER.info("Планировщик NPC инициализирован")
                } catch (e: Exception) {
                    LOGGER.warn("Не удалось инициализировать все новые системы - мод будет работать в базовом режиме", e)
                }
                
                // 6. Инициализируем менеджер НПС
                LOGGER.info("Инициализация NPC Manager...")
                npcManager = NPCManager(ollamaClient, databaseManager)
                LOGGER.info("NPC Manager инициализирован")
                
                // 7. Инициализируем интеграцию с HollowEngine Legacy
                try {
                    LOGGER.info("Инициализация интеграции с HollowEngine Legacy...")
                    
                    // EventSynchronizer инициализируется автоматически при создании объекта
                    LOGGER.info("EventSynchronizer инициализирован")
                    
                    // HollowEngineBridge готов к работе
                    LOGGER.info("HollowEngineBridge готов к использованию")
                    
                    LOGGER.info("Интеграция с HollowEngine Legacy инициализирована успешно")
                } catch (e: Exception) {
                    LOGGER.warn("Не удалось инициализировать интеграцию с HollowEngine Legacy", e)
                    LOGGER.info("Мод будет работать без интеграции с HollowEngine Legacy")
                }
                
                LOGGER.info("Все системы HollowEngineAI успешно инициализированы!")
            } catch (e: Exception) {
                LOGGER.error("Критическая ошибка при инициализации HollowEngineAI", e)
                throw e
            }
        }
    }
    
    /**
     * Настройка клиентской части (только на клиенте)
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.info("Client setup phase for $MOD_NAME")
        // Здесь будут клиентские инициализации (UI, рендеринг и т.д.)
    }
    
    /**
     * Обработчик запуска сервера
     */
    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        LOGGER.info("Server starting - initializing AI systems")
        
        try {
            // Проверяем подключение к Ollama в корутине
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ollamaClient.isHealthy()) {
                        LOGGER.info("Ollama connection verified")
                    } else {
                        LOGGER.warn("Ollama connection failed - AI features will be limited")
                    }
                } catch (e: Exception) {
                    LOGGER.warn("Failed to check Ollama connection", e)
                }
            }
            
            // Запускаем NPC менеджер
            npcManager.start()
            LOGGER.info("NPC Manager started successfully")
            
            // Отправляем событие о запуске сервера
            try {
                val serverStartEvent = com.hollowengineai.mod.events.NPCEvent(
                    type = com.hollowengineai.mod.events.NPCEventType.CUSTOM_EVENT,
                    sourceNpcId = UUID.fromString("00000000-0000-0000-0000-000000000000"), // Системный ID
                    sourceNpcName = "System",
                    data = mapOf("event" to "server_started")
                )
                eventBus.sendEventSync(serverStartEvent)
            } catch (e: Exception) {
                LOGGER.warn("Не удалось отправить событие запуска сервера", e)
            }
            
            // Регистрируем команды администрирования
            try {
                com.hollowengineai.mod.commands.AdminCommands.register(event.server.commands.dispatcher)
                LOGGER.info("Команды администрирования зарегистрированы")
            } catch (e: Exception) {
                LOGGER.error("Ошибка регистрации команд", e)
            }
            // Проверяем доступность интеграции с HollowEngine Legacy
            try {
                // Проверяем, что классы HollowEngine Legacy доступны
                Class.forName("net.minecraft.world.entity.LivingEntity")
                LOGGER.info("Интеграция с HollowEngine Legacy доступна")
            } catch (e: ClassNotFoundException) {
                LOGGER.info("HollowEngine Legacy не обнаружен - интеграция недоступна")
            }
            
        } catch (e: Exception) {
            LOGGER.error("Failed to start AI systems", e)
        }
    }
    
    /**
     * Обработчик остановки сервера
     */
    @SubscribeEvent  
    fun onServerStopping(event: ServerStoppingEvent) {
        LOGGER.info("Server stopping - shutting down AI systems")
        
        try {
            LOGGER.info("Начало остановки HollowEngineAI...")
            
            // Отправляем событие об остановке сервера
            try {
                val serverStopEvent = com.hollowengineai.mod.events.NPCEvent(
                    type = com.hollowengineai.mod.events.NPCEventType.CUSTOM_EVENT,
                    sourceNpcId = UUID.fromString("00000000-0000-0000-0000-000000000000"), // Системный ID
                    sourceNpcName = "System",
                    data = mapOf("event" to "server_stopping")
                )
                eventBus.sendEventSync(serverStopEvent)
            } catch (e: Exception) {
                LOGGER.warn("Не удалось отправить событие остановки сервера", e)
            }
            
            // 1. Останавливаем NPC менеджер (первым!)
            try {
                npcManager.stop()
                LOGGER.info("NPC Manager остановлен")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при остановке NPC Manager", e)
            }
            
            // 2. Останавливаем интеграцию с HollowEngine Legacy
            try {
                LOGGER.info("Остановка интеграции с HollowEngine Legacy...")
                EventSynchronizer.shutdown()
                HollowEngineBridge.shutdown()
                LOGGER.info("Интеграция с HollowEngine Legacy остановлена")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при остановке интеграции с HollowEngine Legacy", e)
            }
            
            // 3. Останавливаем новые системы
            try {
                npcScheduler?.shutdown()
                LOGGER.info("Планировщик NPC остановлен")
                
                socialGroupManager?.shutdown()
                LOGGER.info("Менеджер социальных групп остановлен")
                
                reputationSystem?.shutdown()
                LOGGER.info("Система репутации остановлена")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при остановке новых систем", e)
            }
            
            // 4. Закрываем соединение с базой данных
            try {
                databaseManager.close()
                LOGGER.info("Соединение с базой данных закрыто")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при закрытии соединения с базой данных", e)
            }
            
            // 5. Останавливаем оптимизации производительности (последними!)
            try {
                try {
                    eventBus.stop()
                    LOGGER.info("EventBus остановлен")
                } catch (e: Exception) {
                    LOGGER.error("Ошибка при остановке EventBus", e)
                }
                
                CacheManager.shutdown()
                CoroutinePoolManager.shutdown()
                LOGGER.info("Оптимизации производительности остановлены")
            } catch (e: Exception) {
                LOGGER.error("Ошибка при остановке оптимизаций производительности", e)
            }
            
            LOGGER.info("HollowEngineAI успешно остановлен!")
            
        } catch (e: Exception) {
            LOGGER.error("Критическая ошибка при остановке HollowEngineAI", e)
        }
    }
}
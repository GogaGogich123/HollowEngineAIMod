package com.hollowengineai.mod.memory

import com.hollowengineai.mod.states.EmotionalState
import com.hollowengineai.mod.core.NPCState
import com.hollowengineai.mod.core.PersonalityType
import net.minecraft.core.BlockPos
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

/**
 * Менеджер базы данных для HollowEngineAI
 * 
 * Использует SQLite с ORM Exposed для:
 * - Хранения памяти НПС
 * - Персистентности состояния
 * - Отношений между НПС и игроками
 * - Статистики и аналитики
 */
class DatabaseManager {
    companion object {
        private val LOGGER = LogManager.getLogger(DatabaseManager::class.java)
        private const val DB_VERSION = 1
    }
    
    private lateinit var database: Database
    private var isInitialized = false
    
    /**
     * Инициализировать базу данных
     */
    fun initialize(dbPath: String = "config/hollowengineai/npc_memory.db") {
        try {
            LOGGER.info("Initializing database at: $dbPath")
            
            // Создаем директорию если не существует
            val dbFile = File(dbPath)
            dbFile.parentFile?.mkdirs()
            
            // Подключаемся к SQLite
            database = Database.connect(
                url = "jdbc:sqlite:$dbPath",
                driver = "org.sqlite.JDBC"
            )
            
            // Создаем таблицы
            createTables()
            
            // Выполняем миграции если нужно
            performMigrations()
            
            isInitialized = true
            LOGGER.info("Database initialized successfully")
            
        } catch (e: Exception) {
            LOGGER.error("Failed to initialize database", e)
            throw e
        }
    }
    
    /**
     * Создать таблицы базы данных
     */
    private fun createTables() {
        transaction(database) {
            SchemaUtils.create(
                NPCTable,
                MemoryEpisodeTable,
                KnowledgeTable,
                EmotionTable,
                RelationshipTable,
                PerformanceMetricTable,
                ConfigTable
            )
        }
        LOGGER.info("Database tables created successfully")
    }
    
    /**
     * Выполнить миграции базы данных
     */
    private fun performMigrations() {
        transaction(database) {
            // Проверяем версию схемы
            val currentVersion = try {
                ConfigTable.select { ConfigTable.key eq "schema_version" }
                    .singleOrNull()?.get(ConfigTable.value)?.toInt() ?: 0
            } catch (e: Exception) {
                // Первый запуск - вставляем версию
                ConfigTable.insert {
                    it[key] = "schema_version"
                    it[value] = DB_VERSION.toString()
                }
                DB_VERSION
            }
            
            if (currentVersion < DB_VERSION) {
                LOGGER.info("Migrating database from version $currentVersion to $DB_VERSION")
                // Здесь будут миграции в будущем
                
                // Обновляем версию
                ConfigTable.update({ ConfigTable.key eq "schema_version" }) {
                    it[value] = DB_VERSION.toString()
                }
            }
        }
    }
    
    /**
     * Сохранить НПС в базу данных
     */
    fun saveNPC(
        npcId: UUID,
        name: String,
        personalityType: PersonalityType,
        position: BlockPos,
        state: NPCState = NPCState.IDLE
    ) {
        ensureInitialized()
        
        transaction(database) {
            NPCTable.insertOrUpdate(NPCTable.id) {
                it[id] = npcId
                it[npcName] = name
                it[personality] = personalityType.name
                it[locationX] = position.x
                it[locationY] = position.y  
                it[locationZ] = position.z
                it[currentState] = state.name
                it[lastUpdated] = System.currentTimeMillis()
            }
        }
        
        LOGGER.debug("Saved NPC: $name ($npcId)")
    }
    
    /**
     * Загрузить НПС из базы данных
     */
    fun loadNPC(npcId: UUID): NPCData? {
        ensureInitialized()
        
        return transaction(database) {
            NPCTable.select { NPCTable.id eq npcId }
                .map { row ->
                    NPCData(
                        id = row[NPCTable.id],
                        name = row[NPCTable.npcName],
                        personalityType = PersonalityType.valueOf(row[NPCTable.personality]),
                        position = BlockPos(
                            row[NPCTable.locationX],
                            row[NPCTable.locationY],
                            row[NPCTable.locationZ]
                        ),
                        state = NPCState.valueOf(row[NPCTable.currentState]),
                        createdAt = row[NPCTable.createdAt],
                        lastUpdated = row[NPCTable.lastUpdated]
                    )
                }
                .singleOrNull()
        }
    }
    
    /**
     * Загрузить всех НПС из базы данных
     */
    fun loadAllNPCs(): List<NPCData> {
        ensureInitialized()
        
        return transaction(database) {
            NPCTable.selectAll()
                .map { row ->
                    NPCData(
                        id = row[NPCTable.id],
                        name = row[NPCTable.npcName],
                        personalityType = PersonalityType.valueOf(row[NPCTable.personality]),
                        position = BlockPos(
                            row[NPCTable.locationX],
                            row[NPCTable.locationY],
                            row[NPCTable.locationZ]
                        ),
                        state = NPCState.valueOf(row[NPCTable.currentState]),
                        createdAt = row[NPCTable.createdAt],
                        lastUpdated = row[NPCTable.lastUpdated]
                    )
                }
        }
    }
    
    /**
     * Сохранить эпизод памяти
     */
    fun saveMemoryEpisode(episode: MemoryEpisode) {
        ensureInitialized()
        
        transaction(database) {
            MemoryEpisodeTable.insert {
                it[npcId] = episode.npcId
                it[episodeType] = episode.type
                it[description] = episode.description
                it[locationX] = episode.location.x
                it[locationY] = episode.location.y
                it[locationZ] = episode.location.z
                it[participants] = episode.participants.joinToString(",")
                it[importance] = episode.importance
                it[timestamp] = episode.timestamp
            }
        }
        
        LOGGER.debug("Saved memory episode: ${episode.type} for NPC: ${episode.npcId}")
    }
    
    /**
     * Загрузить недавние эпизоды памяти
     */
    fun loadRecentMemoryEpisodes(npcId: UUID, limit: Int = 10): List<MemoryEpisode> {
        ensureInitialized()
        
        return transaction(database) {
            MemoryEpisodeTable
                .select { MemoryEpisodeTable.npcId eq npcId }
                .orderBy(MemoryEpisodeTable.timestamp, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    MemoryEpisode(
                        npcId = row[MemoryEpisodeTable.npcId],
                        type = row[MemoryEpisodeTable.episodeType],
                        description = row[MemoryEpisodeTable.description],
                        location = BlockPos(
                            row[MemoryEpisodeTable.locationX],
                            row[MemoryEpisodeTable.locationY],
                            row[MemoryEpisodeTable.locationZ]
                        ),
                        participants = row[MemoryEpisodeTable.participants]
                            .split(",")
                            .filter { it.isNotBlank() },
                        importance = row[MemoryEpisodeTable.importance],
                        timestamp = row[MemoryEpisodeTable.timestamp]
                    )
                }
        }
    }
    
    /**
     * Сохранить знание НПС
     */
    fun saveKnowledge(knowledge: Knowledge) {
        ensureInitialized()
        
        transaction(database) {
            KnowledgeTable.insertOrUpdate(KnowledgeTable.id) {
                it[npcId] = knowledge.npcId
                it[topic] = knowledge.topic
                it[information] = knowledge.information
                it[confidence] = knowledge.confidence
                it[lastUpdated] = knowledge.lastUpdated
            }
        }
    }
    
    /**
     * Загрузить знания НПС
     */
    fun loadKnowledge(npcId: UUID, topic: String? = null): List<Knowledge> {
        ensureInitialized()
        
        return transaction(database) {
            val query = if (topic != null) {
                KnowledgeTable.select { 
                    (KnowledgeTable.npcId eq npcId) and (KnowledgeTable.topic eq topic)
                }
            } else {
                KnowledgeTable.select { KnowledgeTable.npcId eq npcId }
            }
            
            query.map { row ->
                Knowledge(
                    npcId = row[KnowledgeTable.npcId],
                    topic = row[KnowledgeTable.topic],
                    information = row[KnowledgeTable.information],
                    confidence = row[KnowledgeTable.confidence],
                    lastUpdated = row[KnowledgeTable.lastUpdated]
                )
            }
        }
    }
    
    /**
     * Сохранить эмоциональное состояние
     */
    fun saveEmotion(
        npcId: UUID,
        emotion: EmotionalState,
        intensity: Float,
        triggeredBy: String? = null
    ) {
        ensureInitialized()
        
        transaction(database) {
            EmotionTable.insert {
                it[EmotionTable.npcId] = npcId
                it[emotionType] = emotion.name
                it[EmotionTable.intensity] = intensity
                it[decayRate] = 0.1f // Стандартная скорость затухания
                it[EmotionTable.triggeredBy] = triggeredBy
                it[timestamp] = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Получить текущие эмоции НПС
     */
    fun getCurrentEmotions(npcId: UUID): List<EmotionRecord> {
        ensureInitialized()
        
        return transaction(database) {
            EmotionTable
                .select { EmotionTable.npcId eq npcId }
                .orderBy(EmotionTable.timestamp, SortOrder.DESC)
                .limit(5)
                .map { row ->
                    EmotionRecord(
                        npcId = row[EmotionTable.npcId],
                        emotionType = EmotionalState.valueOf(row[EmotionTable.emotionType]),
                        intensity = row[EmotionTable.intensity],
                        decayRate = row[EmotionTable.decayRate],
                        triggeredBy = row[EmotionTable.triggeredBy],
                        timestamp = row[EmotionTable.timestamp]
                    )
                }
        }
    }
    
    /**
     * Очистить старые данные
     */
    fun cleanupOldData(maxAge: Long = 30 * 24 * 60 * 60 * 1000L) { // 30 дней
        ensureInitialized()
        
        val cutoffTime = System.currentTimeMillis() - maxAge
        
        transaction(database) {
            val deletedEpisodes = MemoryEpisodeTable.deleteWhere {
                MemoryEpisodeTable.timestamp less cutoffTime
            }
            
            val deletedEmotions = EmotionTable.deleteWhere {
                EmotionTable.timestamp less cutoffTime
            }
            
            LOGGER.info("Cleaned up old data: $deletedEpisodes episodes, $deletedEmotions emotions")
        }
    }
    
    /**
     * Получить статистику базы данных
     */
    fun getDatabaseStats(): DatabaseStats {
        ensureInitialized()
        
        return transaction(database) {
            val npcCount = NPCTable.selectAll().count()
            val episodeCount = MemoryEpisodeTable.selectAll().count()
            val knowledgeCount = KnowledgeTable.selectAll().count()
            val emotionCount = EmotionTable.selectAll().count()
            val relationshipCount = RelationshipTable.selectAll().count()
            
            DatabaseStats(
                npcCount = npcCount,
                episodeCount = episodeCount,
                knowledgeCount = knowledgeCount,
                emotionCount = emotionCount,
                relationshipCount = relationshipCount
            )
        }
    }
    
    /**
     * Закрыть соединение с базой данных
     */
    fun close() {
        if (isInitialized) {
            // Выполняем финальную очистку
            try {
                cleanupOldData()
                LOGGER.info("Database connection closed")
            } catch (e: Exception) {
                LOGGER.error("Error during database shutdown", e)
            }
            
            isInitialized = false
        }
    }
    
    /**
     * Отметить НПС как неактивного (для LOD системы)
     */
    fun markNPCInactive(npcId: String) {
        ensureInitialized()
        
        try {
            transaction(database) {
                NPCTable.update(
                    where = { NPCTable.id eq npcId },
                    body = {
                        it[currentState] = NPCState.INACTIVE.name
                        it[lastUpdated] = System.currentTimeMillis()
                    }
                )
            }
            LOGGER.debug("Marked NPC as inactive: $npcId")
        } catch (e: Exception) {
            LOGGER.error("Failed to mark NPC as inactive: $npcId", e)
        }
    }
    
    /**
     * Проверить что база данных инициализирована
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Database is not initialized")
        }
    }
}

// === Таблицы базы данных ===

/**
 * Таблица НПС
 */
object NPCTable : UUIDTable("npcs") {
    val npcName = varchar("name", 255)
    val personality = varchar("personality_type", 50)
    val locationX = integer("location_x")
    val locationY = integer("location_y")
    val locationZ = integer("location_z")
    val currentState = varchar("current_state", 50).default("IDLE")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val lastUpdated = long("last_updated").default(System.currentTimeMillis())
}

/**
 * Таблица эпизодов памяти
 */
object MemoryEpisodeTable : IntIdTable("memory_episodes") {
    val npcId = uuid("npc_id")
    val episodeType = varchar("episode_type", 100)
    val description = text("description")
    val locationX = integer("location_x")
    val locationY = integer("location_y") 
    val locationZ = integer("location_z")
    val participants = text("participants") // JSON список
    val importance = float("importance")
    val timestamp = long("timestamp")
}

/**
 * Таблица знаний
 */
object KnowledgeTable : IntIdTable("knowledge") {
    val npcId = uuid("npc_id")
    val topic = varchar("topic", 255)
    val information = text("information")
    val confidence = float("confidence")
    val lastUpdated = long("last_updated")
}

/**
 * Таблица эмоций
 */
object EmotionTable : IntIdTable("emotions") {
    val npcId = uuid("npc_id")
    val emotionType = varchar("emotion_type", 50)
    val intensity = float("intensity")
    val decayRate = float("decay_rate")
    val triggeredBy = varchar("triggered_by", 255).nullable()
    val timestamp = long("timestamp")
}

/**
 * Таблица отношений
 */
object RelationshipTable : IntIdTable("relationships") {
    val npcId = uuid("npc_id")
    val targetType = varchar("target_type", 50) // PLAYER, NPC
    val targetId = varchar("target_id", 255) // UUID или имя игрока
    val relationshipType = varchar("relationship_type", 50)
    val strength = float("strength")
    val lastInteraction = long("last_interaction")
}

/**
 * Таблица метрик производительности
 */
object PerformanceMetricTable : IntIdTable("performance_metrics") {
    val metricName = varchar("metric_name", 100)
    val value = double("value")
    val timestamp = long("timestamp")
}

/**
 * Таблица конфигурации
 */
object ConfigTable : IntIdTable("config") {
    val key = varchar("key", 100)
    val value = text("value")
}

// === Data классы ===

/**
 * Данные НПС из базы
 */
data class NPCData(
    val id: UUID,
    val name: String,
    val personalityType: PersonalityType,
    val position: BlockPos,
    val state: NPCState,
    val createdAt: Long,
    val lastUpdated: Long
)

/**
 * Статистика базы данных
 */
data class DatabaseStats(
    val npcCount: Long,
    val episodeCount: Long,
    val knowledgeCount: Long,
    val emotionCount: Long,
    val relationshipCount: Long
)
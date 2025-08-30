package com.hollowengineai.mod.performance

import com.hollowengineai.mod.HollowEngineAIMod
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Централизованный менеджер кэширования для повышения производительности
 */
object CacheManager {
    
    private val caches = ConcurrentHashMap<String, Cache<*, *>>()
    private val hitCounts = AtomicLong(0)
    private val missCounts = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        startCleanupTask()
        HollowEngineAIMod.LOGGER.info("CacheManager initialized")
    }
    
    /**
     * Создать кэш с автоматической очисткой
     */
    fun <K, V> createCache(
        name: String,
        maxSize: Int = 1000,
        ttlMillis: Long = 300000L // 5 минут по умолчанию
    ): Cache<K, V> {
        val cache = Cache<K, V>(name, maxSize, ttlMillis)
        caches[name] = cache
        return cache
    }
    
    /**
     * Получить существующий кэш
     */
    @Suppress("UNCHECKED_CAST")
    fun <K, V> getCache(name: String): Cache<K, V>? {
        return caches[name] as? Cache<K, V>
    }
    
    /**
     * Получить статистику всех кэшей
     */
    fun getGlobalStats(): CacheStats {
        val totalSize = caches.values.sumOf { it.size() }
        val totalMaxSize = caches.values.sumOf { it.maxSize }
        val hitRate = if (hitCounts.get() + missCounts.get() > 0) {
            hitCounts.get().toFloat() / (hitCounts.get() + missCounts.get())
        } else 0f
        
        return CacheStats(
            totalCaches = caches.size,
            totalEntries = totalSize,
            totalCapacity = totalMaxSize,
            globalHitRate = hitRate,
            globalHits = hitCounts.get(),
            globalMisses = missCounts.get()
        )
    }
    
    /**
     * Получить отладочную информацию о всех кэшах
     */
    fun getDebugInfo(): String {
        val stats = getGlobalStats()
        val sb = StringBuilder()
        
        sb.appendLine("CacheManager Debug Info:")
        sb.appendLine("========================")
        sb.appendLine("Total Caches: ${stats.totalCaches}")
        sb.appendLine("Total Entries: ${stats.totalEntries}")
        sb.appendLine("Total Capacity: ${stats.totalCapacity}")
        sb.appendLine("Global Hit Rate: ${String.format("%.2f%%", stats.globalHitRate * 100)}")
        sb.appendLine("Global Hits: ${stats.globalHits}")
        sb.appendLine("Global Misses: ${stats.globalMisses}")
        sb.appendLine()
        
        sb.appendLine("Individual Cache Details:")
        sb.appendLine("-------------------------")
        
        caches.forEach { (name, cache) ->
            val utilization = if (cache.maxSize > 0) {
                (cache.size().toFloat() / cache.maxSize * 100)
            } else 0f
            
            sb.appendLine("Cache '$name':")
            sb.appendLine("  Size: ${cache.size()}/${cache.maxSize} (${String.format("%.1f%%", utilization)})")
            sb.appendLine("  TTL: ${cache.ttlMillis}ms")
            
            if (cache is Cache<*, *>) {
                val details = cache.getDetailedStats()
                sb.appendLine("  Expired entries: ${details.expiredEntries}")
                sb.appendLine("  Average age: ${details.averageAgeMs}ms")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Получить глобальный hit rate для быстрого доступа
     */
    fun getGlobalHitRate(): Double {
        return if (hitCounts.get() + missCounts.get() > 0) {
            hitCounts.get().toDouble() / (hitCounts.get() + missCounts.get())
        } else 0.0
    }
    
    /**
     * Очистить все кэши принудительно
     */
    fun clearAllCaches() {
        caches.values.forEach { it.clear() }
        HollowEngineAIMod.LOGGER.info("All caches cleared")
    }
    
    /**
     * Очистить конкретный кэш
     */
    fun clearCache(name: String): Boolean {
        val cache = caches[name]
        return if (cache != null) {
            cache.clear()
            HollowEngineAIMod.LOGGER.info("Cache '$name' cleared")
            true
        } else {
            false
        }
    }
    
    /**
     * Получить список всех существующих кэшей
     */
    fun getCacheNames(): Set<String> {
        return caches.keys.toSet()
    }
    
    /**
     * Проверить, существует ли кэш с указанным именем
     */
    fun hasCache(name: String): Boolean {
        return caches.containsKey(name)
    }
    
    /**
     * Принудительно очистить просроченные записи в всех кэшах
     */
    fun cleanupExpiredEntries() {
        var totalCleaned = 0
        caches.values.forEach { cache ->
            val sizeBefore = cache.size()
            cache.cleanup()
            totalCleaned += sizeBefore - cache.size()
        }
        if (totalCleaned > 0) {
            HollowEngineAIMod.LOGGER.debug("Cleaned up $totalCleaned expired cache entries")
        }
    }
    
    /**
     * Сбросить статистику hit/miss
     */
    fun resetStatistics() {
        hitCounts.set(0)
        missCounts.set(0)
        HollowEngineAIMod.LOGGER.info("Cache statistics reset")
    }
    
    private fun startCleanupTask() {
        scope.launch {
            while (true) {
                delay(60000L) // Каждую минуту
                try {
                    caches.values.forEach { it.cleanup() }
                } catch (e: Exception) {
                    HollowEngineAIMod.LOGGER.error("Error in cache cleanup", e)
                }
            }
        }
    }
    
    internal fun recordHit() { hitCounts.incrementAndGet() }
    internal fun recordMiss() { missCounts.incrementAndGet() }
    
    fun shutdown() {
        scope.cancel()
        caches.clear()
        HollowEngineAIMod.LOGGER.info("CacheManager shut down")
    }
}

/**
 * Потокобезопасный кэш с TTL
 */
class Cache<K, V>(
    val name: String,
    val maxSize: Int,
    private val ttlMillis: Long
) {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null && !entry.isExpired()) {
            CacheManager.recordHit()
            return entry.value
        } else {
            if (entry != null) cache.remove(key) // Удаляем истёкшие
            CacheManager.recordMiss()
            return null
        }
    }
    
    fun put(key: K, value: V) {
        if (cache.size >= maxSize) {
            evictOldest()
        }
        cache[key] = CacheEntry(value, System.currentTimeMillis() + ttlMillis)
    }
    
    fun size(): Int = cache.size
    
    fun cleanup() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { it.value.expiryTime <= now }
    }
    
    fun clear() {
        cache.clear()
    }
    
    /**
     * Получить детальную статистику кэша
     */
    fun getDetailedStats(): CacheDetailedStats {
        val now = System.currentTimeMillis()
        val entries = cache.values
        
        val expiredCount = entries.count { it.expiryTime <= now }
        val averageAge = if (entries.isNotEmpty()) {
            entries.map { now - (it.expiryTime - ttlMillis) }.average().toLong()
        } else 0L
        
        return CacheDetailedStats(
            expiredEntries = expiredCount,
            validEntries = entries.size - expiredCount,
            averageAgeMs = averageAge,
            oldestEntryAgeMs = if (entries.isNotEmpty()) {
                entries.maxOfOrNull { now - (it.expiryTime - ttlMillis) } ?: 0L
            } else 0L
        )
    }
    
    private fun evictOldest() {
        // Простая эвикция - удаляем 10% старых записей
        val toRemove = maxSize / 10
        cache.entries.sortedBy { it.value.expiryTime }
            .take(toRemove)
            .forEach { cache.remove(it.key) }
    }
}

private data class CacheEntry<V>(
    val value: V,
    val expiryTime: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiryTime
}

data class CacheStats(
    val totalCaches: Int,
    val totalEntries: Int,
    val totalCapacity: Int,
    val globalHitRate: Float,
    val globalHits: Long,
    val globalMisses: Long
)

data class CacheDetailedStats(
    val expiredEntries: Int,
    val validEntries: Int,
    val averageAgeMs: Long,
    val oldestEntryAgeMs: Long
)
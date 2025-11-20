package org.ntqqrev.cecilia.core

import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private data class CacheEntry(
    val bitmap: ImageBitmap,
    val timestamp: Long = System.currentTimeMillis()
)

object AvatarCache {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val maxAge: Duration = 1.hours

    fun get(uin: Long, isGroup: Boolean, quality: Int): ImageBitmap? {
        val key = cacheKey(uin, isGroup, quality)
        val entry = cache[key] ?: return null
        val age = System.currentTimeMillis() - entry.timestamp
        if (age > maxAge.inWholeMilliseconds) {
            cache.remove(key)
            return null
        }
        return entry.bitmap
    }

    fun put(uin: Long, isGroup: Boolean, quality: Int, bitmap: ImageBitmap) {
        val key = cacheKey(uin, isGroup, quality)
        cache[key] = CacheEntry(bitmap)
    }

    private fun cacheKey(uin: Long, isGroup: Boolean, quality: Int): String {
        val type = if (isGroup) "group" else "user"
        return "${type}_${uin}_${quality}"
    }
}
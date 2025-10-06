package org.ntqqrev.cecilia.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.ntqqrev.cecilia.utils.AvatarCache.maxAge
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * 头像缓存条目
 */
private data class CacheEntry(
    val bitmap: ImageBitmap,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 头像缓存，避免重复加载
 * @param maxAge 缓存最大有效期，默认 24 小时
 */
object AvatarCache {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private var maxAge: Duration = 1.hours

    /**
     * 设置缓存的最大有效期
     */
    fun setMaxAge(duration: Duration) {
        maxAge = duration
    }

    /**
     * 获取缓存的头像，如果过期则返回 null
     */
    fun get(uin: Long, isGroup: Boolean, quality: Int): ImageBitmap? {
        val key = cacheKey(uin, isGroup, quality)
        val entry = cache[key] ?: return null

        // 检查是否过期
        val age = System.currentTimeMillis() - entry.timestamp
        if (age > maxAge.inWholeMilliseconds) {
            // 已过期，移除并返回 null
            cache.remove(key)
            return null
        }

        return entry.bitmap
    }

    /**
     * 存入缓存
     */
    fun put(uin: Long, isGroup: Boolean, quality: Int, bitmap: ImageBitmap) {
        val key = cacheKey(uin, isGroup, quality)
        cache[key] = CacheEntry(bitmap)
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        cache.clear()
    }

    /**
     * 清理过期的缓存条目
     */
    fun cleanExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { (_, entry) ->
            (now - entry.timestamp) > maxAge.inWholeMilliseconds
        }
    }

    private fun cacheKey(uin: Long, isGroup: Boolean, quality: Int): String {
        val type = if (isGroup) "group" else "user"
        return "${type}_${uin}_${quality}"
    }
}


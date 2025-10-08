package org.ntqqrev.cecilia.utils

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object MediaCache {
    private const val URL_CONTENT_CACHE_SIZE = 300
    private const val FILE_ID_URL_CACHE_SIZE = 10000

    private val urlContentCache = LRUCache<String, ByteArray>(URL_CONTENT_CACHE_SIZE)
    private val fileIdUrlCache = FIFOCache<String, String>(FILE_ID_URL_CACHE_SIZE)

    fun getUrlByFileId(fileId: String): String? {
        return fileIdUrlCache.get(fileId)
    }

    fun putFileIdUrl(fileId: String, url: String) {
        fileIdUrlCache.put(fileId, url)
    }

    fun getContentByUrl(url: String): ByteArray? {
        return urlContentCache.get(url)
    }

    fun putUrlContent(url: String, content: ByteArray) {
        urlContentCache.put(url, content)
    }

    fun getContentByFileId(fileId: String): ByteArray? {
        val url = getUrlByFileId(fileId) ?: return null
        return getContentByUrl(url)
    }

    fun putFileIdAndContent(fileId: String, url: String, content: ByteArray) {
        putFileIdUrl(fileId, url)
        putUrlContent(url, content)
    }

    fun clear() {
        urlContentCache.clear()
        fileIdUrlCache.clear()
    }

    fun clearUrlContent() {
        urlContentCache.clear()
    }

    fun clearFileIdUrl() {
        fileIdUrlCache.clear()
    }

    fun getStats(): CacheStats {
        return CacheStats(
            urlContentCacheSize = urlContentCache.size(),
            urlContentCacheMaxSize = URL_CONTENT_CACHE_SIZE,
            fileIdUrlCacheSize = fileIdUrlCache.size(),
            fileIdUrlCacheMaxSize = FILE_ID_URL_CACHE_SIZE
        )
    }

    data class CacheStats(
        val urlContentCacheSize: Int,
        val urlContentCacheMaxSize: Int,
        val fileIdUrlCacheSize: Int,
        val fileIdUrlCacheMaxSize: Int
    )
}

private class LRUCache<K, V>(private val maxSize: Int) {
    private val lock = ReentrantReadWriteLock()
    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: K): V? = lock.read {
        cache[key]
    }

    fun put(key: K, value: V) = lock.write {
        cache[key] = value
    }

    fun clear() = lock.write {
        cache.clear()
    }

    fun size(): Int = lock.read {
        cache.size
    }
}

private class FIFOCache<K, V>(private val maxSize: Int) {
    private val lock = ReentrantReadWriteLock()
    private val cache = LinkedHashMap<K, V>()
    private val queue = ArrayDeque<K>()

    fun get(key: K): V? = lock.read {
        cache[key]
    }

    fun put(key: K, value: V) = lock.write {
        if (cache.containsKey(key)) {
            queue.remove(key)
        }

        cache[key] = value
        queue.addLast(key)

        while (queue.size > maxSize) {
            val oldestKey = queue.removeFirst()
            cache.remove(oldestKey)
        }
    }

    fun clear() = lock.write {
        cache.clear()
        queue.clear()
    }

    fun size(): Int = lock.read {
        cache.size
    }
}
package org.ntqqrev.cecilia.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.struct.BotFriendData
import org.ntqqrev.acidify.struct.BotGroupData
import org.ntqqrev.acidify.struct.BotGroupMemberData

/**
 * 通用缓存类
 */
class Cache<K, V>(
    private val scope: CoroutineScope,
    private val fetchData: suspend () -> Map<K, V>
) {
    private val updateMutex = Mutex()
    private var currentTask: Deferred<Unit>? = null
    private var currentCache = mutableMapOf<K, V>()

    /**
     * 获取单个数据
     * @param key 键
     * @param cacheFirst 是否优先使用缓存，false 则强制刷新
     */
    suspend operator fun get(key: K, cacheFirst: Boolean = true): V? {
        if (key !in currentCache || !cacheFirst) {
            update()
        }
        return currentCache[key]
    }

    /**
     * 获取所有数据
     * @param cacheFirst 是否优先使用缓存，false 则强制刷新
     */
    suspend fun getAll(cacheFirst: Boolean = true): Collection<V> {
        if (currentCache.isEmpty() || !cacheFirst) {
            update()
        }
        return currentCache.values
    }

    /**
     * 手动设置缓存项
     */
    suspend operator fun set(key: K, value: V) {
        updateMutex.withLock {
            currentCache[key] = value
        }
    }

    /**
     * 更新缓存
     */
    suspend fun update() {
        updateMutex.withLock {
            currentTask?.let {
                if (it.isActive) {
                    return@withLock it
                }
            }
            val newTask = scope.async {
                val data = fetchData()
                currentCache = data.toMutableMap()
            }
            currentTask = newTask
            newTask
        }.await()
    }

    /**
     * 清空缓存
     */
    suspend fun clear() {
        updateMutex.withLock {
            currentCache.clear()
        }
    }
}

/**
 * 缓存管理器，管理好友和群组缓存
 */
class CacheManager(private val bot: Bot, private val scope: CoroutineScope) {

    /**
     * 好友缓存
     */
    val friendCache = Cache<Long, BotFriendData>(scope) {
        bot.fetchFriends().associateBy { it.uin }
    }

    /**
     * 群组缓存
     */
    val groupCache = Cache<Long, BotGroupData>(scope) {
        bot.fetchGroups().associateBy { it.uin }
    }

    /**
     * 群成员缓存映射（群号 -> 群成员缓存）
     */
    private val groupMemberCaches = mutableMapOf<Long, Cache<Long, BotGroupMemberData>>()
    private val groupMemberMutex = Mutex()

    /**
     * 获取或创建群成员缓存
     */
    suspend fun getGroupMemberCache(groupUin: Long): Cache<Long, BotGroupMemberData>? {
        // 确保群存在
        groupCache[groupUin] ?: return null

        return groupMemberMutex.withLock {
            groupMemberCaches.getOrPut(groupUin) {
                Cache(scope) {
                    bot.fetchGroupMembers(groupUin).associateBy { it.uin }
                }
            }
        }
    }

    /**
     * 初始化缓存（可选，预加载数据）
     */
    suspend fun initialize() {
        coroutineScope {
            launch { friendCache.update() }
            launch { groupCache.update() }
        }
    }

    /**
     * 刷新所有缓存
     */
    suspend fun refreshAll() {
        coroutineScope {
            launch { friendCache.update() }
            launch { groupCache.update() }
            // 刷新已加载的群成员缓存
            groupMemberMutex.withLock {
                groupMemberCaches.values.forEach { cache ->
                    launch { cache.update() }
                }
            }
        }
    }

    /**
     * 清空所有缓存
     */
    suspend fun clearAll() {
        friendCache.clear()
        groupCache.clear()
        groupMemberMutex.withLock {
            groupMemberCaches.clear()
        }
    }
}


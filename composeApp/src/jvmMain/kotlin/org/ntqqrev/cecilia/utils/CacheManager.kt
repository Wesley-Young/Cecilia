package org.ntqqrev.cecilia.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.struct.BotFriendData
import org.ntqqrev.acidify.struct.BotGroupData
import org.ntqqrev.acidify.struct.BotGroupMemberData

class CacheManager(private val bot: Bot, private val scope: CoroutineScope) {
    val friendCache = Cache<Long, BotFriendData>(scope) {
        bot.fetchFriends().associateBy { it.uin }
    }

    val groupCache = Cache<Long, BotGroupData>(scope) {
        bot.fetchGroups().associateBy { it.uin }
    }

    private val groupMemberCaches = mutableMapOf<Long, Cache<Long, BotGroupMemberData>>()
    private val groupMemberMutex = Mutex()

    suspend fun getGroupMemberCache(groupUin: Long): Cache<Long, BotGroupMemberData>? {
        groupCache[groupUin] ?: return null

        return groupMemberMutex.withLock {
            groupMemberCaches.getOrPut(groupUin) {
                Cache(scope) {
                    bot.fetchGroupMembers(groupUin).associateBy { it.uin }
                }
            }
        }
    }

    suspend fun initialize() {
        coroutineScope {
            launch { friendCache.update() }
            launch { groupCache.update() }
        }
    }

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

    suspend fun clearAll() {
        friendCache.clear()
        groupCache.clear()
        groupMemberMutex.withLock {
            groupMemberCaches.clear()
        }
    }

    class Cache<K, V>(
        private val scope: CoroutineScope,
        private val fetchData: suspend () -> Map<K, V>
    ) {
        private val updateMutex = Mutex()
        private var currentTask: Deferred<Unit>? = null
        private var currentCache = mutableMapOf<K, V>()

        suspend operator fun get(key: K, cacheFirst: Boolean = true): V? {
            if (key !in currentCache || !cacheFirst) {
                update()
            }
            return currentCache[key]
        }

        suspend fun getAll(cacheFirst: Boolean = true): Collection<V> {
            if (currentCache.isEmpty() || !cacheFirst) {
                update()
            }
            return currentCache.values
        }

        suspend operator fun set(key: K, value: V) {
            updateMutex.withLock {
                currentCache[key] = value
            }
        }

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

        suspend fun clear() {
            updateMutex.withLock {
                currentCache.clear()
            }
        }
    }
}
package org.ntqqrev.cecilia.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroup
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.acidify.struct.BotFriendData
import org.ntqqrev.acidify.struct.BotGroupData

@Stable
class ContactsState(private val bot: Bot) {
    var friends by mutableStateOf<List<BotFriendData>>(emptyList())
        private set

    var groups by mutableStateOf<List<BotGroupData>>(emptyList())
        private set

    var isFriendsLoading by mutableStateOf(false)
        private set

    var isGroupsLoading by mutableStateOf(false)
        private set

    var pinnedFriendUins by mutableStateOf<List<Long>>(emptyList())
        private set

    var pinnedGroupUins by mutableStateOf<List<Long>>(emptyList())
        private set

    private var friendsInitialized = false
    private var groupsInitialized = false
    private var pinsInitialized = false

    suspend fun ensureInitialized() {
        if (!pinsInitialized) {
            runCatching { refreshPins() }
        }
        if (!friendsInitialized) {
            runCatching { refreshFriends() }
        }
        if (!groupsInitialized) {
            runCatching { refreshGroups() }
        }
    }

    suspend fun refreshPins() {
        val pins = bot.getPins()
        withContext(Dispatchers.Main) {
            pinnedFriendUins = pins.friendUins
            pinnedGroupUins = pins.groupUins
            pinsInitialized = true
        }
    }

    suspend fun refreshFriends() {
        setFriendsLoading(true)
        try {
            val refreshed = bot.getFriends(forceUpdate = true).map { it.toData() }
            withContext(Dispatchers.Main) {
                friends = refreshed
                friendsInitialized = true
            }
        } finally {
            setFriendsLoading(false)
        }
    }

    suspend fun refreshGroups() {
        setGroupsLoading(true)
        try {
            val refreshed = bot.getGroups(forceUpdate = true)
                .map { it.toData() }
            withContext(Dispatchers.Main) {
                groups = refreshed
                groupsInitialized = true
            }
        } finally {
            setGroupsLoading(false)
        }
    }

    suspend fun setFriendPinned(friendUin: Long, isPinned: Boolean) {
        bot.setFriendPin(friendUin, isPinned)
        refreshPins()
    }

    suspend fun setGroupPinned(groupUin: Long, isPinned: Boolean) {
        bot.setGroupPin(groupUin, isPinned)
        refreshPins()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun handlePinChanged(scene: MessageScene, peerUin: Long, isPinned: Boolean) {
        // 后端事件会附带最新的置顶状态，直接重新拉取一次以保持排序一致
        refreshPins()
    }

    fun clear() {
        friends = emptyList()
        groups = emptyList()
        isFriendsLoading = false
        isGroupsLoading = false
        friendsInitialized = false
        groupsInitialized = false
        pinnedFriendUins = emptyList()
        pinnedGroupUins = emptyList()
        pinsInitialized = false
    }

    private suspend fun setFriendsLoading(value: Boolean) {
        withContext(Dispatchers.Main) {
            isFriendsLoading = value
        }
    }

    private suspend fun setGroupsLoading(value: Boolean) {
        withContext(Dispatchers.Main) {
            isGroupsLoading = value
        }
    }
}

private fun BotFriend.toData() = BotFriendData(
    uin = uin,
    uid = uid,
    nickname = nickname,
    remark = remark,
    bio = bio,
    qid = qid,
    age = age,
    gender = gender,
    categoryId = categoryId,
    categoryName = categoryName
)

private fun BotGroup.toData() = BotGroupData(
    uin = uin,
    name = name,
    memberCount = memberCount,
    capacity = capacity
)

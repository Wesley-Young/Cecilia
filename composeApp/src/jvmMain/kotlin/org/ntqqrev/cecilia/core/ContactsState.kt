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

    private var friendsInitialized = false
    private var groupsInitialized = false

    suspend fun ensureInitialized() {
        if (!friendsInitialized) {
            runCatching { refreshFriends() }
        }
        if (!groupsInitialized) {
            runCatching { refreshGroups() }
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

    fun clear() {
        friends = emptyList()
        groups = emptyList()
        isFriendsLoading = false
        isGroupsLoading = false
        friendsInitialized = false
        groupsInitialized = false
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

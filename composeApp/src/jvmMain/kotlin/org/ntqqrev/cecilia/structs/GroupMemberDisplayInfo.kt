package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.struct.GroupMemberRole

data class GroupMemberDisplayInfo(
    val card: String,
    val nickname: String,
    val specialTitle: String,
    val level: Int,
    val role: GroupMemberRole
)

fun BotGroupMember.toDisplayInfo() = GroupMemberDisplayInfo(
    card = card,
    nickname = nickname,
    specialTitle = specialTitle,
    level = level,
    role = role
)

package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.entity.BotGroupMember

data class GroupMemberDisplayInfo(
    val card: String,
    val nickname: String,
    val specialTitle: String,
    val level: Int
)

fun BotGroupMember.toDisplayInfo() = GroupMemberDisplayInfo(
    card = card,
    nickname = nickname,
    specialTitle = specialTitle,
    level = level
)

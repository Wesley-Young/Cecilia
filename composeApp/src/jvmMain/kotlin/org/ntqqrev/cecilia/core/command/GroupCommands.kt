package org.ntqqrev.cecilia.core.command

private val positiveFlags = setOf("set", "enable", "on", "true", "1", "yes", "y")
private val negativeFlags = setOf("unset", "disable", "off", "false", "0", "no", "n")

private fun parseToggle(input: String, errorMessage: String): Boolean = when (input.lowercase()) {
    in positiveFlags -> true
    in negativeFlags -> false
    else -> error(errorMessage)
}

private fun CommandExecutionContext.requireGroupUin(): Long {
    val group = currentGroup ?: error("请在群聊会话中使用此命令")
    return group.uin
}

private fun String.toMemberUin(): Long =
    toLongOrNull() ?: error("成员 uin 必须是数字")

val RefreshCommand = Command(
    id = "refresh",
    title = "刷新群聊缓存",
    description = "在群聊会话中重新加载群列表并更新 UI",
) {
    val group = currentGroup ?: error("请在群聊会话中使用 /refresh")
    val contacts = contactsState ?: error("联系人状态不可用")
    contacts.refreshGroups()
    bot.getGroupMembers(group.uin, forceUpdate = true)
}

val NudgeCommand = Command(
    id = "nudge",
    title = "戳一戳",
    description = "向群成员发送戳一戳",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要戳一戳的成员",
            required = true
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    bot.sendGroupNudge(groupUin, targetUin.toMemberUin())
}

val CardCommand = Command(
    id = "card",
    title = "设置群名片",
    description = "修改群成员的群名片",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要修改名片的成员"
        ),
        CommandParameter(
            key = "card",
            name = "新名片",
            description = "新的群名片内容",
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    val card by args
    bot.setGroupMemberCard(groupUin, targetUin.toMemberUin(), card)
}

val AdminCommand = Command(
    id = "admin",
    title = "设置/取消管理员",
    description = "授予或取消群成员管理员权限",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要操作的成员"
        ),
        CommandParameter(
            key = "action",
            name = "操作",
            description = "set 表示设为管理员，unset 表示取消",
            suggestionsProvider = {
                listOf(
                    CommandSuggestion("set", "设为管理员"),
                    CommandSuggestion("unset", "取消管理员")
                )
            }
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    val action by args
    val isAdmin = parseToggle(action, "操作只能是 set 或 unset")
    bot.setGroupMemberAdmin(groupUin, targetUin.toMemberUin(), isAdmin)
}

val TitleCommand = Command(
    id = "title",
    title = "设置群头衔",
    description = "设置群成员的专属头衔 (<=18 字节)",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要设置头衔的成员"
        ),
        CommandParameter(
            key = "title",
            name = "头衔",
            description = "新的专属头衔（最长 18 字节）"
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    val title by args
    if (title.encodeToByteArray().size > 18) {
        error("头衔长度不能超过 18 字节")
    }
    bot.setGroupMemberSpecialTitle(groupUin, targetUin.toMemberUin(), title)
}

val KickCommand = Command(
    id = "kick",
    title = "移除群成员",
    description = "将成员移出群聊并设置是否允许再次加群",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要移除的成员"
        ),
        CommandParameter(
            key = "allowRejoin",
            name = "允许再次加群?",
            description = "yes 允许再次申请，no 表示拒绝",
            suggestionsProvider = {
                listOf(
                    CommandSuggestion("yes", "允许再次加群"),
                    CommandSuggestion("no", "拒绝再次加群")
                )
            }
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    val allowRejoin by args
    val allow = parseToggle(
        allowRejoin,
        "请输入 yes 或 no 表示是否允许再次加群"
    )
    bot.kickGroupMember(groupUin, targetUin.toMemberUin(), rejectAddRequest = !allow)
}

val MuteCommand = Command(
    id = "mute",
    title = "成员禁言",
    description = "设置群成员禁言时长（秒），0 取消禁言",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "成员 uin",
            description = "要禁言的成员"
        ),
        CommandParameter(
            key = "duration",
            name = "时长 (秒)",
            description = "禁言时长，0 表示解除",
        )
    )
) {
    val groupUin = requireGroupUin()
    val targetUin by args
    val duration by args
    val seconds = duration.toIntOrNull()?.takeIf { it >= 0 }
        ?: error("时长必须是非负整数")
    bot.setGroupMemberMute(groupUin, targetUin.toMemberUin(), seconds)
}

val MuteAllCommand = Command(
    id = "mute-all",
    title = "全员禁言",
    description = "开启或关闭群全员禁言",
    parameters = listOf(
        CommandParameter(
            key = "action",
            name = "操作",
            description = "set 开启禁言，unset 关闭禁言",
            suggestionsProvider = {
                listOf(
                    CommandSuggestion("set", "开启全员禁言"),
                    CommandSuggestion("unset", "关闭全员禁言")
                )
            }
        )
    )
) {
    val groupUin = requireGroupUin()
    val action by args
    val enable = parseToggle(action, "操作只能是 set 或 unset")
    bot.setGroupWholeMute(groupUin, enable)
}

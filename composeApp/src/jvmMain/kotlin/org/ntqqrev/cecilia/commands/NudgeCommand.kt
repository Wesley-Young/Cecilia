package org.ntqqrev.cecilia.commands

import org.ntqqrev.cecilia.Command
import org.ntqqrev.cecilia.utils.params.uinParameter

val NudgeCommand = Command(
    id = "nudge",
    title = "戳一戳",
    description = "向群成员发送戳一戳",
    parameters = listOf(
        uinParameter(
            key = "targetUin",
            name = "目标 uin",
            description = "要戳一戳的群成员的 QQ 号",
            required = true
        )
    ),
    execute = {
        val targetUin by args
        val uin = targetUin.toLongOrNull()
            ?: error("目标 uin 必须是数字")
        if (currentGroup != null) {
            bot.sendGroupNudge(currentGroup.uin, uin)
        }
    }
)
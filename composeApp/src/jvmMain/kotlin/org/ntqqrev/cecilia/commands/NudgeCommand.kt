package org.ntqqrev.cecilia.commands

val NudgeCommand = Command(
    id = "nudge",
    title = "戳一戳",
    description = "向群成员发送戳一戳",
    parameters = listOf(
        CommandParameter(
            key = "targetUin",
            name = "目标 uin",
            description = "要戳一戳的群成员的 QQ 号",
            required = true,
            suggestionsProvider = { arg ->
                currentGroup?.getMembers()
                    ?.filter {
                        // search across uin, nickname, card
                        it.uin.toString().contains(arg) ||
                        it.nickname.contains(arg, ignoreCase = true) ||
                        it.card.contains(arg, ignoreCase = true)
                    }
                    ?.map {
                        CommandSuggestion(
                            content = it.uin.toString(),
                            display = "${it.card.ifEmpty { it.nickname }} (${it.uin})"
                        )
                    }
                    ?: emptyList()
            }
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
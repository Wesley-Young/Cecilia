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
                val query = arg.trim()
                if (query.isEmpty()) {
                    return@CommandParameter emptyList()
                }

                currentGroup?.getMembers()
                    ?.mapNotNull { member ->
                        val cardPriority = member.card.matchType(query, ignoreCase = true)?.let { CARD_FIELD_PRIORITY to it }
                        val nicknamePriority = member.nickname.matchType(query, ignoreCase = true)?.let { NICKNAME_FIELD_PRIORITY to it }
                        val uinPriority = member.uin.toString().matchType(query)?.let { UIN_FIELD_PRIORITY to it }

                        val bestMatch = listOfNotNull(cardPriority, nicknamePriority, uinPriority)
                            .minWithOrNull(
                                compareBy<Pair<Int, Int>> { it.first }
                                    .thenBy { it.second }
                            )

                        bestMatch?.let { (fieldPriority, matchPriority) ->
                            val overallPriority = fieldPriority * MATCH_TYPE_COUNT + matchPriority
                            member to overallPriority
                        }
                    }
                    ?.sortedBy { it.second }
                    ?.map { (member, _) ->
                        CommandSuggestion(
                            content = member.uin.toString(),
                            display = member.card.ifEmpty { member.nickname }
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

private const val MATCH_TYPE_COUNT = 3
private const val CARD_FIELD_PRIORITY = 0
private const val NICKNAME_FIELD_PRIORITY = 1
private const val UIN_FIELD_PRIORITY = 2

private fun String.matchType(query: String, ignoreCase: Boolean = false): Int? {
    if (query.isEmpty() || isEmpty()) return null
    return when {
        startsWith(query, ignoreCase) -> 0
        endsWith(query, ignoreCase) -> 1
        contains(query, ignoreCase) -> 2
        else -> null
    }
}

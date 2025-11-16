package org.ntqqrev.cecilia.commands

import org.ntqqrev.cecilia.Command

val RefreshCommand = Command(
    id = "refresh",
    title = "刷新群聊缓存",
    description = "在群聊会话中重新加载群列表并更新 UI",
    execute = {
        val group = currentGroup ?: error("请在群聊会话中使用 /refresh")
        val contacts = contactsState ?: error("联系人状态不可用")
        contacts.refreshGroups()
        bot.getGroupMembers(group.uin, forceUpdate = true)
    }
)

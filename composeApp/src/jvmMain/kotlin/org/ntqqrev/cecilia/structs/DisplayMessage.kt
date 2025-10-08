package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.message.BotIncomingMessage

class DisplayMessage(
    val real: BotIncomingMessage? = null,
    val placeholder: PlaceholderMessage? = null,
)
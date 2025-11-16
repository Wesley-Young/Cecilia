package org.ntqqrev.cecilia.struct

import org.ntqqrev.acidify.message.BotIncomingMessage

class DisplayMessage(
    val real: BotIncomingMessage? = null,
    val placeholder: PlaceholderMessage? = null,
    val greyTip: String? = null,
)
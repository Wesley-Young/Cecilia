package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.message.BotIncomingMessage

fun BotIncomingMessage.toShortPreview() = buildString {
    extraInfo?.let {
        append(it.groupCard)
        append(": ")
    }
    segments.forEach { append(it.toString()) }
}
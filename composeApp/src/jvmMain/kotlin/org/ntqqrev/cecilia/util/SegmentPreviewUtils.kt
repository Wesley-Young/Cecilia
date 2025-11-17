package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment

/**
 * 将消息转换为预览字符串
 */
fun BotIncomingMessage.segmentsToPreviewString(): String {
    return segments.joinToString("") { segment ->
        when (segment) {
            is BotIncomingSegment.MarketFace
                if segment.url == "https://gxh.vip.qq.com/club/item/parcel/item/a3/a322b9f5bf8e2f0f2370d933f6fd4239/raw300.gif" -> "[逼？！]"
            // easter egg
            is BotIncomingSegment.Reply -> "[回复消息]" // hide replied seq
            else -> segment.toString()
        }
    }.take(100) // 限制预览长度
}
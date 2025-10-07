package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.message.BotIncomingSegment

/**
 * 消息显示元素
 *
 * 用于消息渲染的类型安全模型，将原始消息段转换为适合 UI 显示的元素
 */
sealed class DisplayElem {
    /**
     * 文本元素
     *
     * 合并连续的文本、提及等文本型段落
     *
     * @property text 显示的文本内容
     */
    data class Text(val text: String) : DisplayElem()

    /**
     * 回复元素
     *
     * 引用其他消息的回复段
     *
     * @property segment 原始回复段
     */
    data class Reply(val segment: BotIncomingSegment.Reply) : DisplayElem()

    /**
     * 图片元素
     *
     * 包括普通图片和表情贴纸
     *
     * @property segment 原始图片段
     */
    data class Image(val segment: BotIncomingSegment.Image) : DisplayElem()

    /**
     * 语音元素
     *
     * @property segment 原始语音段
     */
    data class Record(val segment: BotIncomingSegment.Record) : DisplayElem()

    /**
     * 视频元素
     *
     * @property segment 原始视频段
     */
    data class Video(val segment: BotIncomingSegment.Video) : DisplayElem()

    /**
     * 其他元素
     *
     * 其他类型的消息段，使用默认的 toString 显示
     *
     * @property segment 原始消息段
     */
    data class Other(val segment: BotIncomingSegment) : DisplayElem()
}


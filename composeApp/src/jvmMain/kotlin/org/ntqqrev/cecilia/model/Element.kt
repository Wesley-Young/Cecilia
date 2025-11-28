package org.ntqqrev.cecilia.model

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.AnnotatedString
import org.ntqqrev.acidify.message.ImageSubType

sealed class Element {
    data class RichText(
        val content: AnnotatedString,
        val inlines: Map<String, InlineTextContent>,
    ) : Element() {
        override fun toString() = content.text
    }

    data class LargeFace(
        val faceId: Int,
    ) : Element() {
        override fun toString() = "[表情]"
    }

    data class Image(
        val fileId: String,
        val width: Int,
        val height: Int,
        val subType: ImageSubType,
        val summary: String,
    ) : Element() {
        override fun toString() = summary
    }

    data class Reply(
        val sequence: Long,
        val senderName: String,
        val content: String,
    ) : Element() {
        override fun toString() = "[引用消息]"
    }
}
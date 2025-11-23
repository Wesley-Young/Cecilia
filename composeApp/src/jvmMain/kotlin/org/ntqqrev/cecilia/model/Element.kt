package org.ntqqrev.cecilia.model

import androidx.compose.ui.text.AnnotatedString
import org.ntqqrev.acidify.message.ImageSubType

sealed class Element {
    data class RichText(
        val content: AnnotatedString
    ) : Element()

    data class Image(
        val fileId: String,
        val width: Int,
        val height: Int,
        val subType: ImageSubType,
        val summary: String,
    ) : Element()

    data class Reply(
        val sequence: Long,
    ) : Element()
}
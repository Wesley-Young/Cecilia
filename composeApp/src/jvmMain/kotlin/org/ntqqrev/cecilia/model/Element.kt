package org.ntqqrev.cecilia.model

import org.ntqqrev.acidify.message.ImageSubType

sealed class Element {
    data class Text(
        val content: String
    ) : Element()

    data class Image(
        val fileId: String,
        val width: Int,
        val height: Int,
        val subType: ImageSubType,
        val summary: String,
    ) : Element()
}
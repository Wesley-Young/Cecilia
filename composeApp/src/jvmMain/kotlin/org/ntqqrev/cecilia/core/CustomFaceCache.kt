package org.ntqqrev.cecilia.core

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.cecilia.struct.OutgoingImageAttachment
import java.util.*

object CustomFaceCache {
    data class CachedCustomFace(val attachment: OutgoingImageAttachment)

    private val cache = mutableMapOf<String, CachedCustomFace>()
    private val mutex = Mutex()

    suspend fun getOrLoad(url: String, httpClient: HttpClient): CachedCustomFace? {
        cache[url]?.let { return it }
        val loaded = loadFromNetwork(url, httpClient) ?: return null
        return mutex.withLock {
            cache.getOrPut(url) { loaded }
        }
    }

    private suspend fun loadFromNetwork(
        url: String,
        httpClient: HttpClient
    ): CachedCustomFace? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = httpClient.get(url).body<ByteArray>()
            val attachment = OutgoingImageAttachment.fromBytes(
                raw = bytes,
                summary = "[动画表情]",
                subType = ImageSubType.STICKER
            ) ?: return@runCatching null
            CachedCustomFace(attachment)
        }.getOrNull()
    }

    fun CachedCustomFace.toAttachmentInstance(): OutgoingImageAttachment =
        attachment.copy(id = UUID.randomUUID().toString())
}

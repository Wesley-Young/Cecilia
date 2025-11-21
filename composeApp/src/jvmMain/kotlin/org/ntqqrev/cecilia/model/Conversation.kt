package org.ntqqrev.cecilia.model

import org.ntqqrev.acidify.message.MessageScene

data class Conversation(
    val scene: MessageScene,
    val peerUin: Long,
    val displayName: String,
    val isPinned: Boolean,
    val lastMessageTime: Long? = null,
    val unreadCount: Int = 0,
    val lastMessagePreview: String? = null,
) : Comparable<Conversation> {
    data class Key(
        val scene: MessageScene,
        val peerUin: Long,
    )

    val asKey: Key get() = Key(scene, peerUin)

    override fun compareTo(other: Conversation): Int {
        if (this == other) return 0

        // Pinned conversations come first
        if (this.isPinned && !other.isPinned) return -1
        if (!this.isPinned && other.isPinned) return 1

        // Then sort by last message time, most recent first
        val timeComparison = (other.lastMessageTime ?: 0L).compareTo(this.lastMessageTime ?: 0L)
        if (timeComparison != 0) return timeComparison

        // Then sort by scene, friend > group > temp
        val sceneComparison = this.scene.compareTo(other.scene)
        if (sceneComparison != 0) return sceneComparison

        // Then sort by peerUin as a tiebreaker
        return this.peerUin.compareTo(other.peerUin)
    }
}
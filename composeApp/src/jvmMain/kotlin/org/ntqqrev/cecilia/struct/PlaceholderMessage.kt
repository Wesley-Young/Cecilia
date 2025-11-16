package org.ntqqrev.cecilia.struct

class PlaceholderMessage(
    val clientSequence: Long,
    val random: Int,
    val timestamp: Long,
    val displaySegments: List<DisplaySegment>,
    val groupMemberInfo: GroupMemberDisplayInfo? = null,
)

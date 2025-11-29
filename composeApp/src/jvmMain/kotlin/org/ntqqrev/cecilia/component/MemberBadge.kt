package org.ntqqrev.cecilia.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.struct.GroupMemberRole

@Composable
fun MemberBadge(member: BotGroupMember) {
    Box(
        Modifier.background(
            color = when (member.role) {
                GroupMemberRole.OWNER -> Color(0xFFFFB74D)
                GroupMemberRole.ADMIN -> Color(0xFF26C6DA)
                GroupMemberRole.MEMBER -> {
                    if (member.specialTitle.isNotBlank()) {
                        Color(0xFFD269DA)
                    } else {
                        Color(0xFF90A4AE)
                    }
                }
            },
            shape = RoundedCornerShape(999.dp),
        )
    ) {
        Text(
            text = buildString {
                val titlePart = member.specialTitle.takeIf { it.isNotBlank() } ?: when (member.role) {
                    GroupMemberRole.OWNER -> "群主"
                    GroupMemberRole.ADMIN -> "管理员"
                    else -> null
                }
                append("Lv")
                append(member.level)
                if (titlePart != null) {
                    append(" ")
                    append(titlePart)
                }
            },
            style = FluentTheme.typography.caption.copy(
                fontSize = FluentTheme.typography.caption.fontSize * 0.9f,
                fontWeight = FontWeight.SemiBold,
            ),
            color = FluentTheme.colors.text.onAccent.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}
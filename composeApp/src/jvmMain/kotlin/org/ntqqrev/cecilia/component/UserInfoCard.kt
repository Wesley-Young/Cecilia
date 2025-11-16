package org.ntqqrev.cecilia.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ntqqrev.acidify.struct.BotUserInfo

@Composable
fun UserInfoCard(
    userInfo: BotUserInfo?,
    isLoading: Boolean,
    uin: Long
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 8.dp
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (userInfo != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像 - 固定72px直径，不可点击
                AvatarImage(
                    uin = uin,
                    size = 72.dp,
                    isGroup = false,
                    quality = 640
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 昵称
                Text(
                    text = userInfo.nickname,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // QQ号
                Text(
                    text = "QQ: $uin",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                // 备注（若有）
                if (userInfo.remark.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "备注: ${userInfo.remark}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                // 个性签名
                if (userInfo.bio.isNotEmpty()) {
                    Text(
                        text = userInfo.bio,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "这个人很懒，什么都没写",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败",
                    color = MaterialTheme.colors.error
                )
            }
        }
    }
}
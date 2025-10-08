package org.ntqqrev.cecilia

import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// 绿色主题
val GreenColors = lightColors(
    primary = Color(0xFF4CAF50),
    primaryVariant = Color(0xFF388E3C),
    secondary = Color(0xFF66BB6A),
    secondaryVariant = Color(0xFF43A047),
    background = Color(0xFFF1F8F4),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFF44336),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B),
    onError = Color.White
)

// 聊天区域背景色
val ChatBackgroundColor = Color(0xFFEDF3EE)

val NotoFontFamily = FontFamily(
    Font("fonts/NotoSansSC-Regular.ttf"),
    Font("fonts/NotoSansSC-Bold.ttf", FontWeight.Bold),
)
package org.ntqqrev.cecilia.component

import androidx.compose.material.Colors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

enum class ThemeType(
    val displayName: String, val colorScheme: Colors, val chatBackgroundColor: Color
) {
    GREEN(
        "圣女绿", lightColors(
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
        ), Color(0xFFEDF3EE)
    ),
    RED(
        "树莓红", lightColors(
            primary = Color(0xFFEC407A),
            primaryVariant = Color(0xFFD81B60),
            secondary = Color(0xFFF06292),
            secondaryVariant = Color(0xFFE91E63),
            background = Color(0xFFFFF0F5),
            surface = Color(0xFFFFFFFF),
            error = Color(0xFFF44336),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1B1B1B),
            onSurface = Color(0xFF1B1B1B),
            onError = Color.White
        ), Color(0xFFFFF0F6)
    ),
    YELLOW(
        "蛋糕黄", lightColors(
            primary = Color(0xFFFFA726),
            primaryVariant = Color(0xFFF57C00),
            secondary = Color(0xFFFFB74D),
            secondaryVariant = Color(0xFFFB8C00),
            background = Color(0xFFFFFBF0),
            surface = Color(0xFFFFFFFF),
            error = Color(0xFFF44336),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1B1B1B),
            onSurface = Color(0xFF1B1B1B),
            onError = Color.White
        ), Color(0xFFFFFAE6)
    ),
    BLUE(
        "海盐蓝", lightColors(
            primary = Color(0xFF42A5F5),
            primaryVariant = Color(0xFF1976D2),
            secondary = Color(0xFF64B5F6),
            secondaryVariant = Color(0xFF1E88E5),
            background = Color(0xFFF0F7FF),
            surface = Color(0xFFFFFFFF),
            error = Color(0xFFF44336),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1B1B1B),
            onSurface = Color(0xFF1B1B1B),
            onError = Color.White
        ), Color(0xFFE8F4FC)
    )
}

val NotoFontFamily = FontFamily(
    Font("fonts/NotoSansSC-Regular.ttf"),
    Font("fonts/NotoSansSC-Bold.ttf", FontWeight.Bold),
)
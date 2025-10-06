package org.ntqqrev.cecilia

import androidx.compose.runtime.compositionLocalOf
import org.ntqqrev.acidify.Bot

/**
 * 提供 Bot 实例的 CompositionLocal
 * 使组件可以直接访问 Bot，无需层层传递参数
 */
val LocalBot = compositionLocalOf<Bot> {
    error("Bot not provided")
}
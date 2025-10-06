package org.ntqqrev.cecilia.utils

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 提供 CacheManager 的 CompositionLocal
 */
val LocalCacheManager = staticCompositionLocalOf<CacheManager> {
    error("No CacheManager provided")
}


package org.ntqqrev.cecilia.core.command

import kotlin.reflect.KProperty

class CommandArguments(
    private val values: Map<String, String>
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return values[property.name]
            ?: error("缺少参数 \"${property.name}\"")
    }
}
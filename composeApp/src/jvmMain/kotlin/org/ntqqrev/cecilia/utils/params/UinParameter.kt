package org.ntqqrev.cecilia.utils.params

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import org.ntqqrev.cecilia.CommandParameter
import org.ntqqrev.cecilia.CommandSuggestion

private const val MATCH_TYPE_COUNT = 5
private const val MATCH_TYPE_STARTS_WITH = 0
private const val MATCH_TYPE_ENDS_WITH = 1
private const val MATCH_TYPE_CONTAINS_TEXT = 2
private const val MATCH_TYPE_CONTAINS_PINYIN_FULL = 3
private const val MATCH_TYPE_CONTAINS_PINYIN_INITIAL = 4
private const val CARD_FIELD_PRIORITY = 0
private const val NICKNAME_FIELD_PRIORITY = 1
private const val UIN_FIELD_PRIORITY = 2

private val PINYIN_OUTPUT_FORMAT = HanyuPinyinOutputFormat().apply {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
    vCharType = HanyuPinyinVCharType.WITH_V
}

private fun String.matchType(query: String, ignoreCase: Boolean = false): Int? {
    if (query.isEmpty() || isEmpty()) return null
    return when {
        startsWith(query, ignoreCase) -> MATCH_TYPE_STARTS_WITH
        endsWith(query, ignoreCase) -> MATCH_TYPE_ENDS_WITH
        contains(query, ignoreCase) -> MATCH_TYPE_CONTAINS_TEXT
        else -> null
    }
}

private fun String.matchTypeWithPinyin(query: String): Int? {
    val baseMatch = matchType(query, ignoreCase = true)
    if (baseMatch != null) return baseMatch

    val fullPinyin = toFullPinyinOrNull()
    if (fullPinyin != null && fullPinyin.contains(query, ignoreCase = true)) {
        return MATCH_TYPE_CONTAINS_PINYIN_FULL
    }

    val initials = toPinyinInitialsOrNull()
    if (initials != null && initials.contains(query, ignoreCase = true)) {
        return MATCH_TYPE_CONTAINS_PINYIN_INITIAL
    }

    return null
}

private fun String.toFullPinyinOrNull(): String? {
    if (isEmpty()) return null
    var hasPinyin = false
    val builder = StringBuilder(length * 2)
    for (char in this) {
        val pinyin = char.toPinyinOrNull()
        when {
            pinyin != null -> {
                builder.append(pinyin)
                hasPinyin = true
            }
            char.isLetterOrDigit() -> builder.append(char)
        }
    }
    return if (hasPinyin && builder.isNotEmpty()) builder.toString() else null
}

private fun String.toPinyinInitialsOrNull(): String? {
    if (isEmpty()) return null
    var hasPinyin = false
    val builder = StringBuilder(length)
    for (char in this) {
        val pinyin = char.toPinyinOrNull()
        when {
            pinyin != null -> {
                builder.append(pinyin[0])
                hasPinyin = true
            }
            char.isLetterOrDigit() -> builder.append(char)
        }
    }
    return if (hasPinyin && builder.isNotEmpty()) builder.toString() else null
}

private fun Char.toPinyinOrNull(): String? = try {
    PinyinHelper.toHanyuPinyinStringArray(this, PINYIN_OUTPUT_FORMAT)?.firstOrNull()
} catch (_: BadHanyuPinyinOutputFormatCombination) {
    null
}

fun uinParameter(
    key: String,
    name: String,
    description: String,
    required: Boolean = true,
) = CommandParameter(
    key = key,
    name = name,
    description = description,
    required = required,
) { arg ->
    val query = arg.trim()
    val members = currentGroup?.getMembers() ?: return@CommandParameter emptyList()

    if (query.isEmpty()) {
        return@CommandParameter members
            .sortedBy { it.card.ifEmpty { it.nickname } }
            .map {
                CommandSuggestion(
                    content = it.uin.toString(),
                    display = it.card.ifEmpty { it.nickname }.ifEmpty { "<无名氏>" }
                )
            }
    }

    return@CommandParameter members
        .mapNotNull { member ->
            val cardPriority =
                member.card.matchTypeWithPinyin(query)?.let { CARD_FIELD_PRIORITY to it }
            val nicknamePriority =
                member.nickname.matchTypeWithPinyin(query)?.let { NICKNAME_FIELD_PRIORITY to it }
            val uinPriority = member.uin.toString().matchType(query)?.let { UIN_FIELD_PRIORITY to it }

            val bestMatch = listOfNotNull(cardPriority, nicknamePriority, uinPriority)
                .minWithOrNull(
                    compareBy<Pair<Int, Int>> { it.first }
                        .thenBy { it.second }
                )

            bestMatch?.let { (fieldPriority, matchPriority) ->
                val overallPriority = fieldPriority * MATCH_TYPE_COUNT + matchPriority
                member to overallPriority
            }
        }
        .sortedBy { it.second }
        .map { (member, _) ->
            CommandSuggestion(
                content = member.uin.toString(),
                display = member.card.ifEmpty { member.nickname }.ifEmpty { "<无名氏>" }
            )
        }
}

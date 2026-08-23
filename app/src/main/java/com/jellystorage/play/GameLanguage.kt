package com.jellystorage.play

import java.util.Locale

enum class GameLanguage(val code: String, val nativeName: String) {
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語");

    fun toggled(): GameLanguage = if (this == CHINESE) JAPANESE else CHINESE

    companion object {
        fun fromCode(code: String?): GameLanguage = entries.firstOrNull { it.code == code }
            ?: if (Locale.getDefault().language == Locale.JAPANESE.language) JAPANESE else CHINESE
    }
}

/**
 * Runtime localization for the Canvas-heavy game UI.
 *
 * Most game text is generated dynamically (equipment stats, story beats, combat
 * banners), so all render paths pass through [tr]. Exact translations keep story
 * prose natural; ordered phrase replacements cover interpolated values.
 */
object GameI18n {
    @Volatile
    var language: GameLanguage = GameLanguage.CHINESE

    fun tr(source: String): String {
        if (language == GameLanguage.CHINESE || source.isBlank()) return source
        JapaneseTranslations.exact[source]?.let { return it }
        var result = source
        JapaneseTranslations.patterns.forEach { (pattern, replacement) ->
            result = pattern.replace(result, replacement)
        }
        JapaneseTranslations.contentFragments.forEach { (zh, ja) ->
            result = result.replace(zh, ja)
        }
        JapaneseTranslations.phrases.forEach { (zh, ja) ->
            result = result.replace(zh, ja)
        }
        return result
    }
}

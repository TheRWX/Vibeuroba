package com.github.k1rakishou.core_themes.themes

import android.content.Context
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

/**
 * Vibeuroba theme built from the wallpaper-based system palette (Material You, Android 12+).
 */
@RequiresApi(Build.VERSION_CODES.S)
object MaterialYouTheme {
  const val NAME_DARK = "Material You (dark)"
  const val NAME_LIGHT = "Material You (light)"

  fun dark(context: Context): ChanTheme {
    fun c(@ColorRes id: Int) = context.getColor(id)

    val accent = c(android.R.color.system_accent1_200)

    return Kuroneko(
      name = NAME_DARK,
      accentColor = accent,
      primaryColor = c(android.R.color.system_neutral2_800),
      backColor = c(android.R.color.system_neutral1_900),
      backColorSecondary = ThemeEngine.manipulateColor(c(android.R.color.system_neutral1_900), 0.8f),
      errorColor = DEFAULT_ERROR_DARK,
      textColorPrimary = c(android.R.color.system_neutral1_50),
      textColorSecondary = c(android.R.color.system_neutral2_200),
      textColorHint = c(android.R.color.system_neutral2_400),
      postHighlightedColor = ColorUtils.setAlphaComponent(accent, 0x40),
      postSavedReplyColor = c(android.R.color.system_accent3_300),
      postSubjectColor = c(android.R.color.system_accent3_200),
      postDetailsColor = c(android.R.color.system_neutral2_400),
      postNameColor = c(android.R.color.system_accent2_300),
      postInlineQuoteColor = c(android.R.color.system_accent3_300),
      postQuoteColor = c(android.R.color.system_accent1_300),
      postHighlightQuoteColor = c(android.R.color.system_accent1_700),
      postLinkColor = accent,
      postUnseenLabelColor = c(android.R.color.system_accent3_300),
      bookmarkCounterNormalColor = accent,
      scrollbarThumbColorDragged = accent,
    )
  }

  fun light(context: Context): ChanTheme {
    fun c(@ColorRes id: Int) = context.getColor(id)

    val accent = c(android.R.color.system_accent1_600)

    return Shironeko(
      name = NAME_LIGHT,
      accentColor = accent,
      primaryColor = c(android.R.color.system_accent1_600),
      backColor = c(android.R.color.system_neutral1_10),
      backColorSecondary = c(android.R.color.system_neutral2_100),
      errorColor = DEFAULT_ERROR_LIGHT,
      textColorPrimary = c(android.R.color.system_neutral1_900),
      textColorSecondary = c(android.R.color.system_neutral2_700),
      textColorHint = c(android.R.color.system_neutral2_500),
      postHighlightedColor = ColorUtils.setAlphaComponent(accent, 0x30),
      postSavedReplyColor = c(android.R.color.system_accent3_600),
      postSubjectColor = c(android.R.color.system_accent3_700),
      postDetailsColor = c(android.R.color.system_neutral2_600),
      postNameColor = c(android.R.color.system_accent2_700),
      postInlineQuoteColor = c(android.R.color.system_accent3_600),
      postQuoteColor = c(android.R.color.system_accent1_700),
      postHighlightQuoteColor = c(android.R.color.system_accent1_900),
      postLinkColor = accent,
      postUnseenLabelColor = c(android.R.color.system_accent3_600),
      bookmarkCounterNormalColor = accent,
      scrollbarThumbColorDragged = accent,
    )
  }

  private const val DEFAULT_ERROR_DARK = 0xFFFFB4AB.toInt()
  private const val DEFAULT_ERROR_LIGHT = 0xFFBA1A1A.toInt()
}

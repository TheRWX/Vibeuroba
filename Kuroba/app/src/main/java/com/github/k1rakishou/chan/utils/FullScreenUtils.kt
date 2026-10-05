package com.github.k1rakishou.chan.utils

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ChanTheme


object FullScreenUtils {

  fun Window.setupEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(this, false)

    val controller = WindowInsetsControllerCompat(this, this.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }

  fun Window.setupStatusAndNavBarColors(theme: ChanTheme) {
    // ChanTheme.lightStatusBar/lightNavBar mean "light icons" (dark bar content off), the opposite of
    // isAppearanceLight*Bars. Replaces the systemUiVisibility flags deprecated since API 30.
    val controller = WindowInsetsControllerCompat(this, decorView)
    controller.isAppearanceLightStatusBars = !theme.lightStatusBar

    if (AndroidUtils.isAndroidO) {
      controller.isAppearanceLightNavigationBars = !theme.lightNavBar
    }
  }

}
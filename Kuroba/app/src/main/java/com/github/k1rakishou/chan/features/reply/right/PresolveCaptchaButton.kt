package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import kotlinx.coroutines.delay

private const val ABOUT_TO_EXPIRE_SECONDS = 15L

@Composable
internal fun PresolveCaptchaButton(
  iconSize: Dp,
  padding: Dp,
  newReplyLayoutTutorialFinished: Boolean,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onPresolveCaptchaButtonClicked: () -> Unit
) {
  val captchaCounter by replyLayoutViewModel.captchaHolderCaptchaCounterUpdatesFlow.collectAsState(initial = 0)

  // Saved answers expire (usually after a couple of minutes), show how much time is left for the one that is going to
  // be used next so that it doesn't expire unnoticed.
  var nextCaptchaSecondsLeft by remember { mutableStateOf<Long?>(null) }

  LaunchedEffect(key1 = captchaCounter) {
    while (captchaCounter > 0) {
      nextCaptchaSecondsLeft = replyLayoutViewModel.nextSavedCaptchaRemainingMillis()?.div(1000L)
      delay(1000L)
    }

    nextCaptchaSecondsLeft = null
  }

  Box {
    KurobaComposeIcon(
      modifier = Modifier
        .size(iconSize)
        .padding(padding)
        .kurobaClickable(
          bounded = false,
          enabled = newReplyLayoutTutorialFinished,
          onClick = onPresolveCaptchaButtonClicked
        ),
      drawableId = R.drawable.ic_captcha_24dp,
      iconTint = IconTint.DoNotTint
    )

    if (captchaCounter > 0) {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.TopEnd)
          .drawBehind {
            drawRoundRect(
              color = Color.Black.copy(alpha = 0.8f),
              cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx())
            )
          }
          .padding(horizontal = 4.dp)
      ) {
        val secondsLeft = nextCaptchaSecondsLeft
        val isAboutToExpire = secondsLeft != null && secondsLeft <= ABOUT_TO_EXPIRE_SECONDS

        KurobaComposeText(
          text = if (secondsLeft != null) {
            "${captchaCounter} \u00b7 ${stringResource(id = R.string.captcha_layout_saved_answer_time_left, secondsLeft)}"
          } else {
            captchaCounter.toString()
          },
          color = if (isAboutToExpire) Color(0xFFFF6B6B) else Color.White,
          fontSize = 11.ktu.fixedSize()
        )
      }
    }
  }
}

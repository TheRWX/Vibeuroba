package com.github.k1rakishou.chan.ui.captcha.chan4

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeClickableIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.scaffold.FloatingListScaffoldBuilder
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.IHasViewModelScope
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.resolveTextColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class Chan4CaptchaLayout(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
  // True when the solved captcha is going to be used to post the reply right away, false when the answer is only
  // being saved for later (pre-solve).
  private val autoReply: Boolean = true,
  private val presentControllerFunc: (Controller) -> Unit
) : TouchBlockingFrameLayout(context),
  AuthenticationLayoutInterface,
  IHasViewModelScope {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appResources: AppResources

  private val viewModel by viewModelByKey<Chan4CaptchaLayoutViewModel>()
  private val scope = KurobaCoroutineScope()

  private var siteDescriptor: SiteDescriptor? = null
  private var siteAuthentication: SiteAuthentication? = null
  private var callback: AuthenticationLayoutCallback? = null

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ActivityScope(context.requireComponentActivity())

  init {
    AppModuleAndroidUtils.extractActivityComponent(getContext())
      .inject(this)
  }

  override fun initialize(
    siteDescriptor: SiteDescriptor,
    authentication: SiteAuthentication,
    callback: AuthenticationLayoutCallback
  ) {
    this.siteDescriptor = siteDescriptor
    this.siteAuthentication = authentication
    this.callback = callback

    val view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }

    view.layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )

    addView(view)

    viewModel.onCaptchaViewInitialized()
  }

  override fun reset() {
    hardReset()
  }

  override fun hardReset() {
    viewModel.requestCaptcha(
      chanDescriptor = chanDescriptor,
      mcl = "",
      forced = false
    )
  }

  override fun onDestroy() {
    this.siteAuthentication = null
    this.callback = null

    scope.cancelChildren()

    viewModel.resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor)
    viewModel.onCaptchaViewDestroyed()
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val scrollState = rememberScrollState()

    with(FloatingListScaffoldBuilder()) {
      Content(
        boxScope = this@BuildContent,
        scrollState = scrollState,
        header = {
          val chanTheme = LocalChanTheme.current
          val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()
          val captchaInfoAsync by viewModel.captchaInfoToShow
          val shownCaptchaInfo = (captchaInfoAsync as? AsyncUiData.UiData)?.data

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(
              modifier = Modifier
                .wrapContentWidth()
                .padding(vertical = 4.dp)
            ) {
              if (captchaTtlMillis >= 0L) {
                val ttlSeconds = captchaTtlMillis / 1000L
                val ttlIsLow = ttlSeconds <= LOW_TTL_SECONDS

                KurobaComposeText(
                  text = if (captchaTtlMillis > 0L) {
                    "Captcha TTL: ${ttlSeconds} sec"
                  } else {
                    stringResource(id = R.string.captcha_layout_expired)
                  },
                  color = if (ttlIsLow) chanTheme.errorColorCompose else null,
                  fontWeight = if (ttlIsLow) FontWeight.Bold else null,
                  fontSize = 14.ktu
                )
              }

              if (shownCaptchaInfo != null && !shownCaptchaInfo.isNoopChallenge() && shownCaptchaInfo.tasks.isNotEmpty()) {
                KurobaComposeText(
                  text = stringResource(
                    id = R.string.captcha_layout_progress,
                    shownCaptchaInfo.answeredTasksCount(),
                    shownCaptchaInfo.tasks.size
                  ),
                  color = chanTheme.textColorHintCompose,
                  fontSize = 14.ktu
                )
              }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (captchaInfoAsync is AsyncUiData.UiData) {
              KurobaComposeClickableIcon(
                drawableId = R.drawable.ic_help_outline_white_24dp,
                onClick = {
                  val title = appResources.string(R.string.captcha_4chan_updates_title)
                  val description = buildString {
                    appendLine(appResources.string(R.string.captcha_4chan_updates_new))
                    appendLine()
                    appendLine(appResources.string(R.string.captcha_4chan_update_06_03_2026))
                    appendLine()
                    appendLine(appResources.string(R.string.captcha_4chan_update_26_02_2026))
                  }

                  dialogFactory.createSimpleInformationDialog(
                    context = context,
                    titleText = title,
                    descriptionText = description
                  )
                }
              )
            }
          }
        },
        body = { paddingValues ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .verticalScroll(scrollState)
          ) {
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            BuildCaptchaWindow(scrollState)
            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
          }
        },
        footer = {
          BuildCaptchaWindowFooter()
        }
      )
    }
  }

  @Composable
  private fun BuildCaptchaWindow(scrollState: ScrollState) {
    Spacer(modifier = Modifier.height(8.dp))

    Box(
      modifier = Modifier
        .heightIn(min = 64.dp)
    ) {
      BuildCaptchaImageRows(scrollState)
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun BuildCaptchaImageRows(scrollState: ScrollState) {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val previousCaptchaInfo by viewModel.previousCaptchaInfo
    val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val taskTops = remember { mutableMapOf<Int, Float>() }
    var zoomedImage by remember { mutableStateOf<ImageBitmap?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val nextButtonTextLayoutResult = remember {
      textMeasurer.measure(
        text = "Next",
        style = TextStyle(
          color = chanTheme.textColorHintCompose,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      )
    }

    var isLoadingNewChallenge = false

    val captchaInfo = when (val captchaInfo = captchaInfoAsync) {
      is AsyncUiData.UiData<Chan4CaptchaLayoutViewModel.CaptchaInfo> -> captchaInfo.data
      is AsyncUiData.Error -> {
        KurobaComposeErrorMessage(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
          error = captchaInfo.throwable
        )
        return
      }
      AsyncUiData.Loading -> {
        val prevCaptchaInfo = previousCaptchaInfo
        if (prevCaptchaInfo == null) {
          KurobaComposeProgressIndicator(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 8.dp),
          )
          return
        }

        // Keep the old challenge on the screen (dimmed) while the new one is being loaded
        isLoadingNewChallenge = true
        prevCaptchaInfo
      }
      AsyncUiData.NotInitialized -> {
        return
      }
    }

    val isExpired = !isLoadingNewChallenge && captchaTtlMillis == 0L
    val tasks = captchaInfo.tasks

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (isLoadingNewChallenge) {
          KurobaComposeText(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            text = stringResource(id = R.string.captcha_layout_loading_new_challenge),
            color = chanTheme.accentColorCompose,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
          )
        }

        if (isExpired) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            KurobaComposeText(
              modifier = Modifier.weight(1f),
              text = stringResource(id = R.string.captcha_layout_expired_load_new),
              color = chanTheme.errorColorCompose,
              fontWeight = FontWeight.SemiBold
            )

            KurobaComposeTextBarButton(
              onClick = { reloadCaptcha() },
              text = stringResource(id = R.string.captcha_layout_load_new_challenge)
            )
          }
        }

        if (captchaInfo.isNoopChallenge()) {
          VerificationNotRequired()
        } else {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .alpha(if (isLoadingNewChallenge || isExpired) 0.4f else 1f)
          ) {
            for ((taskIndex, task) in tasks.withIndex()) {
              if (taskIndex > 0) {
                Spacer(modifier = Modifier.height(12.dp))
              }

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(chanTheme.backColorSecondaryCompose)
                  .onGloballyPositioned { coordinates -> taskTops[taskIndex] = coordinates.positionInRoot().y }
                  .padding(all = 8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  val taskAnswered = task.images.any { image -> image.isSelected }

                  KurobaComposeText(
                    text = if (taskAnswered) "#${taskIndex + 1} \u2713" else "#${taskIndex + 1}",
                    color = if (taskAnswered) chanTheme.accentColorCompose else chanTheme.textColorHintCompose,
                    fontWeight = if (taskAnswered) FontWeight.Bold else null
                  )

                  Spacer(modifier = Modifier.width(8.dp))

                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                  ) {
                    CaptchaTaskTitle(task.title)
                  }
                }

                Spacer(
                  modifier = Modifier
                    .wrapContentWidth()
                    .height(8.dp)
                )

                // Show at most 2-3 images in a row, otherwise they become too small to inspect on a phone screen
                val imagesPerRow = when {
                  task.hasWideImages -> 2
                  task.images.size == 4 -> 2
                  else -> task.images.size.coerceIn(1, 3)
                }

                FlowRow(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                  horizontalArrangement = Arrangement.spacedBy(
                    space = 4.dp,
                    alignment = Alignment.CenterHorizontally
                  ),
                  verticalArrangement = Arrangement.spacedBy(space = 10.dp),
                  maxItemsInEachRow = imagesPerRow
                ) {
                  for ((imageIndex, taskImage) in task.images.withIndex()) {
                    val aspectRatio = taskImage.imageBitmap.width.toFloat() / taskImage.imageBitmap.height.toFloat()
                    val isWideImage = aspectRatio > 1.5f

                    Image(
                      modifier = Modifier
                        .background(chanTheme.backColorCompose)
                        .weight(if (isWideImage) 1f else 0.5f)
                        .aspectRatio(aspectRatio)
                        .kurobaClickable(
                          enabled = !isLoadingNewChallenge && !isExpired,
                          bounded = true,
                          onLongClick = { zoomedImage = taskImage.imageBitmap },
                          onClick = {
                            val wasAnswered = tasks.getOrNull(taskIndex)
                              ?.images
                              ?.any { image -> image.isSelected } == true

                            viewModel.onCaptchaImageClicked(taskIndex, imageIndex)

                            val answeredNow = tasks.getOrNull(taskIndex)
                              ?.images
                              ?.getOrNull(imageIndex)
                              ?.isSelected == true

                            // Only jump ahead when this click has just answered the task. Changing an existing answer
                            // (or a click that was ignored) must not move the user away from where they are.
                            if (answeredNow && !wasAnswered) {
                              val nextTaskIndex = captchaInfo.nextUnansweredTaskIndex(taskIndex)
                              val firstTop = taskTops[0]
                              val nextTop = nextTaskIndex?.let { index -> taskTops[index] }

                              if (firstTop != null && nextTop != null) {
                                coroutineScope.launch {
                                  scrollState.animateScrollTo((nextTop - firstTop).toInt().coerceAtLeast(0))
                                }
                              }
                            }
                          }
                        )
                        .drawWithContent {
                          drawContent()

                          if (taskImage.isSelected) {
                            val accentColor = chanTheme.accentColorCompose
                            val borderWidth = 4.dp.toPx()

                            drawRect(color = accentColor.copy(alpha = 0.18f))
                            drawRect(
                              color = accentColor,
                              topLeft = Offset(borderWidth / 2f, borderWidth / 2f),
                              size = Size(size.width - borderWidth, size.height - borderWidth),
                              style = Stroke(width = borderWidth)
                            )

                            // Checkmark badge
                            val badgeRadius = 12.dp.toPx()
                            val badgeCenter = Offset(badgeRadius + 6.dp.toPx(), badgeRadius + 6.dp.toPx())
                            val checkColor = if (ThemeEngine.isDarkColor(accentColor)) Color.White else Color.Black
                            val checkStrokeWidth = 2.5.dp.toPx()
                            val checkBottom = Offset(
                              x = badgeCenter.x - badgeRadius * 0.1f,
                              y = badgeCenter.y + badgeRadius * 0.38f
                            )

                            drawCircle(color = accentColor, center = badgeCenter, radius = badgeRadius)
                            drawLine(
                              color = checkColor,
                              start = Offset(
                                x = badgeCenter.x - badgeRadius * 0.45f,
                                y = badgeCenter.y + badgeRadius * 0.02f
                              ),
                              end = checkBottom,
                              strokeWidth = checkStrokeWidth,
                              cap = StrokeCap.Round
                            )
                            drawLine(
                              color = checkColor,
                              start = checkBottom,
                              end = Offset(
                                x = badgeCenter.x + badgeRadius * 0.5f,
                                y = badgeCenter.y - badgeRadius * 0.35f
                              ),
                              strokeWidth = checkStrokeWidth,
                              cap = StrokeCap.Round
                            )
                          }

                          if (task.drawFakeSliderAndNextButton()) {
                            // Draw the "next" button (it is not clickable, it's here only to mimic the web captcha)
                            run {
                              val verticalPadding = 4.dp.toPx()
                              val horizontalPadding = 8.dp.toPx()
                              val buttonWidth =
                                (nextButtonTextLayoutResult.size.width + horizontalPadding.toInt()).toFloat()
                              val buttonHeight =
                                (nextButtonTextLayoutResult.size.height + verticalPadding.toInt()).toFloat()

                              translate(
                                left = size.width - buttonWidth,
                                top = 0f
                              ) {
                                drawRect(
                                  color = chanTheme.accentColorCompose,
                                  size = Size(width = buttonWidth, height = buttonHeight)
                                )

                                translate(left = horizontalPadding / 2f, top = verticalPadding / 2f) {
                                  drawText(
                                    textLayoutResult = nextButtonTextLayoutResult,
                                    color = chanTheme.accentColorCompose.resolveTextColor()
                                  )
                                }
                              }
                            }

                            // Draw the slider's thumb
                            run {
                              val sliderWidth = this.size.width
                              val sliderHeight = this.size.height
                              val sliderThumbSize = 8.dp.toPx()

                              val thumbOffsetStep = (sliderWidth - (sliderThumbSize * 2)) / (task.images.size).toFloat()
                              val thumbOffset = ((imageIndex + 1) * thumbOffsetStep) + sliderThumbSize

                              drawCircle(
                                color = if (ThemeEngine.isDarkColor(chanTheme.accentColorCompose)) {
                                  Color.White
                                } else {
                                  Color.Black
                                },
                                center = Offset(x = thumbOffset, y = sliderHeight),
                                radius = sliderThumbSize + 1.dp.toPx()
                              )

                              drawCircle(
                                color = chanTheme.accentColorCompose,
                                center = Offset(x = thumbOffset, y = sliderHeight),
                                radius = sliderThumbSize
                              )
                            }
                          }
                        },
                      bitmap = taskImage.imageBitmap,
                      contentDescription = "Captcha task image"
                    )
                  }

                  // Fill the last row with empty cells so that the images in it have the same size as in other rows
                  val emptyCells = (imagesPerRow - task.images.size % imagesPerRow) % imagesPerRow
                  repeat(emptyCells) {
                    Spacer(modifier = Modifier.weight(if (task.hasWideImages) 1f else 0.5f))
                  }
                }
              }
            }
          }
        }
      }
    }

    zoomedImage?.let { imageBitmap ->
      ZoomableImageDialog(
        imageBitmap = imageBitmap,
        onDismiss = { zoomedImage = null }
      )
    }
  }

  @Composable
  private fun ZoomableImageDialog(
    imageBitmap: ImageBitmap,
    onDismiss: () -> Unit
  ) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      var scale by remember { mutableStateOf(1f) }
      var offset by remember { mutableStateOf(Offset.Zero) }
      var containerSize by remember { mutableStateOf(IntSize.Zero) }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.92f))
          .onSizeChanged { size -> containerSize = size }
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { onDismiss() },
              onDoubleTap = {
                scale = 1f
                offset = Offset.Zero
              }
            )
          }
          .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
              scale = (scale * zoom).coerceIn(1f, 8f)

              // The image is fit to the width of the window, so it can be moved only as far as the scaled image
              // overflows the window. This way it can't be dragged completely out of the screen.
              val imageWidth = containerSize.width.toFloat()
              val imageHeight = imageWidth * imageBitmap.height.toFloat() / imageBitmap.width.toFloat()
              val maxX = (imageWidth * scale - containerSize.width).coerceAtLeast(0f) / 2f
              val maxY = (imageHeight * scale - containerSize.height).coerceAtLeast(0f) / 2f

              offset = if (scale <= 1f) {
                Offset.Zero
              } else {
                val newOffset = offset + pan
                Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
              }
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Image(
          modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
              scaleX = scale,
              scaleY = scale,
              translationX = offset.x,
              translationY = offset.y
            ),
          bitmap = imageBitmap,
          contentScale = ContentScale.Fit,
          contentDescription = null
        )

        KurobaComposeText(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp),
          text = stringResource(id = R.string.captcha_layout_zoom_hint),
          color = Color.White,
          fontSize = 14.ktu
        )
      }
    }
  }

  @Composable
  private fun VerificationNotRequired() {
    val text = stringResource(id = R.string.captcha_layout_verification_not_required)

    val annotatedText = remember {
      buildAnnotatedString {
        pushStyle(
          SpanStyle(
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline
          )
        )

        append(text)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(42.dp),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(
        text = annotatedText,
        fontSize = 18.ktu
      )
    }
  }

  @Composable
  private fun ColumnScope.CaptchaTaskTitle(title: Chan4CaptchaTitleFormatter.Title?) {
    val chanTheme = LocalChanTheme.current

    when (title) {
      is Chan4CaptchaTitleFormatter.Title.Image -> {
        val ratio = run {
          if (title.image.height == 0) {
            return@run 1f
          }

          return@run title.image.width.toFloat() / title.image.height.toFloat()
        }

        Image(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .heightIn(min = 64.dp, max = 160.dp)
            .aspectRatio(ratio = ratio),
          bitmap = title.image,
          contentDescription = null
        )

        Spacer(modifier = Modifier.height(8.dp))

        KurobaComposeText(text = "Ignore the \"scrollbar\"/\"click Next\" parts and just click " +
          "one of the images containing the element mentioned above")
      }
      is Chan4CaptchaTitleFormatter.Title.TextWithImage -> {
        if (title.images.isEmpty()) {
          KurobaComposeText(
            text = title.annotated,
            fontSize = 18.ktu,
            fontWeight = FontWeight.SemiBold
          )
          return
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          verticalAlignment = Alignment.CenterVertically
        ) {
          KurobaComposeText(
            modifier = Modifier.weight(1f),
            text = title.annotated,
            fontSize = 18.ktu,
            fontWeight = FontWeight.SemiBold
          )

          title.images.forEach { imageBitmap ->
            Spacer(modifier = Modifier.width(4.dp))

            val ratio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()

            Image(
              modifier = Modifier
                .aspectRatio(ratio = ratio)
                .widthIn(min = 72.dp)
                .border(width = 3.dp, color = chanTheme.accentColorCompose),
              bitmap = imageBitmap,
              contentDescription = null
            )
          }

          Spacer(modifier = Modifier.width(4.dp))
        }
      }
      null -> {
        KurobaComposeText(text = "Failed to parse captcha task title, time to guess ;)")
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by viewModel.captchaInfoToShow
    val captchaDataJson by viewModel.captchaDataJson
    val captchaTtlMillis by viewModel.captchaTtlMillisFlow.collectAsState()
    val captchaInfo = (captchaInfoAsync as? AsyncUiData.UiData)?.data
    val isExpired = captchaInfo != null && captchaTtlMillis == 0L

    Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(chanTheme.backColorCompose)
    ) {
      // 48dp is the recommended minimum size of a touch target
      KurobaComposeClickableIcon(
        modifier = Modifier
          .size(48.dp)
          .padding(10.dp),
        drawableId = R.drawable.ic_settings_white_24dp,
        onClick = { showChan4CaptchaSettings() }
      )

      KurobaComposeClickableIcon(
        modifier = Modifier
          .size(48.dp)
          .padding(10.dp),
        drawableId = R.drawable.ic_refresh_white_24dp,
        onClick = { reloadCaptcha() }
      )

      KurobaComposeClickableIcon(
        modifier = Modifier
          .size(48.dp)
          .padding(10.dp),
        drawableId = R.drawable.ic_baseline_content_copy_24,
        enabled = captchaDataJson != null,
        onClick = {
          captchaDataJson?.let { captchaInfoJson ->
            AndroidUtils.setClipboardContent("captcha_json", captchaInfoJson)
            showToast(context, "Captcha json copied to clipboard")
          }
        }
      )

      Spacer(modifier = Modifier.weight(1f))

      run {
        val buttonTextId = when {
          isExpired -> R.string.captcha_layout_load_new_challenge
          autoReply -> R.string.captcha_layout_submit_and_post
          else -> R.string.captcha_layout_save_answer
        }

        KurobaComposeTextBarButton(
          onClick = {
            if (isExpired) {
              reloadCaptcha()
            } else {
              verifyCaptcha(captchaInfo)
            }
          },
          enabled = isExpired || (captchaInfo != null && (captchaInfo.isFilledIn() || captchaInfo.isNoopChallenge())),
          text = stringResource(id = buttonTextId)
        )
      }

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  private fun reloadCaptcha() {
    viewModel.requestCaptcha(
      chanDescriptor = chanDescriptor,
      mcl = "",
      forced = true
    )
  }

  private fun verifyCaptcha(
    captchaInfo: Chan4CaptchaLayoutViewModel.CaptchaInfo?,
  ) {
    if (captchaInfo == null) {
      return
    }

    val challenge = captchaInfo.challenge
    val uuid = captchaHolder.generateCaptchaUuid()

    val solution = CaptchaSolution.ChallengeWithSolution(
      uuid = uuid,
      challenge = challenge,
      solution = captchaInfo.solution()
    )

    val ttl = captchaInfo.ttlMillis()
    if (ttl <= 0L) {
      showToast(context, R.string.captcha_layout_captcha_already_expired)
      return
    }

    viewModel.onCaptchaSubmitted()
    captchaHolder.addNewSolution(solution, ttl)
    callback?.onAuthenticationComplete()
    viewModel.resetCaptchaForced(chanDescriptor)
  }

  private fun showCaptchaHelp() {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = getString(R.string.captcha_layout_help_title),
      descriptionText = getString(R.string.captcha_layout_help_text)
    )
  }

  private fun showChan4CaptchaSettings() {
    val chan4CaptchaSettings = viewModel.chan4CaptchaSettingsJson.readBlocking()
    val items = mutableListOf<FloatingListMenuItem>()

    items += CheckableFloatingListMenuItem(
      key = ACTION_REMEMBER_CAPTCHA_COOKIES,
      name = getString(R.string.captcha_layout_remember_captcha_cookies),
      checked = chan4CaptchaSettings.rememberCaptchaCookies
    )

    items += FloatingListMenuItem(
      key = ACTION_SHOW_CAPTCHA_HELP,
      name = getString(R.string.captcha_layout_show_captcha_help)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      items = items,
      itemClickListener = { clickedMenuItem ->
        when (val itemId = clickedMenuItem.key as Int) {
          ACTION_SHOW_CAPTCHA_HELP -> {
            showCaptchaHelp()
          }
          ACTION_REMEMBER_CAPTCHA_COOKIES -> {
            val setting = viewModel.chan4CaptchaSettingsJson.readBlocking()
            val updatedSetting = setting.copy(rememberCaptchaCookies = setting.rememberCaptchaCookies.not())

            viewModel.chan4CaptchaSettingsJson.writeAsync(updatedSetting)
          }
        }
      }
    )

    presentControllerFunc(floatingListMenuController)
  }

  class Scale(
    private val scale: Float
  ) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
      return ScaleFactor(scale, scale)
    }
  }
  
  companion object {
    private const val LOW_TTL_SECONDS = 10L

    private const val ACTION_SHOW_CAPTCHA_HELP = 1
    private const val ACTION_REMEMBER_CAPTCHA_COOKIES = 2
  }

}
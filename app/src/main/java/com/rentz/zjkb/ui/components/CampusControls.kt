package com.rentz.zjkb.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rentz.zjkb.ui.theme.CampusTheme
import com.rentz.zjkb.ui.theme.LocalCampusContentColor

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CampusTheme.textStyles.body1,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    BasicText(text, modifier, style.merge(TextStyle(
        color = if (color != Color.Unspecified) color else LocalCampusContentColor.current,
        fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
        lineHeight = lineHeight, textAlign = textAlign ?: TextAlign.Unspecified,
    )), maxLines = maxLines, overflow = overflow, onTextLayout = onTextLayout)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalCampusContentColor.current) {
    Image(imageVector, contentDescription, modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
}

@Immutable
data class CampusButtonColors(val color: Color, val contentColor: Color)
object ButtonDefaults {
    @Composable fun buttonColors(color: Color = CampusTheme.colorScheme.surfaceVariant, contentColor: Color = CampusTheme.colorScheme.onSurface) = CampusButtonColors(color, contentColor)
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CampusButtonColors = ButtonDefaults.buttonColors(),
    cornerRadius: Dp = 8.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    minWidth: Dp = 0.dp,
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(150), label = "buttonPress")
    Row(
        modifier.defaultMinSize(minWidth, minHeight).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .45f }
            .clip(RoundedCornerShape(cornerRadius)).background(colors.color)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick).padding(insideMargin),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalCampusContentColor provides colors.contentColor) { content() }
    }
}

@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier, enabled, ButtonDefaults.buttonColors(Color.Transparent, CampusTheme.colorScheme.primary),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp), minHeight = 44.dp) {
        Text(text, style = CampusTheme.textStyles.footnote1, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.size(44.dp).clip(CircleShape).background(if (pressed) CampusTheme.colorScheme.surfaceVariant else Color.Transparent)
        .alpha(if (enabled) 1f else .4f).clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "switchPosition")
    val colors = CampusTheme.colorScheme
    Box(modifier.size(48.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(45.dp, 27.dp).clip(CircleShape).background(if (checked) colors.primary else colors.dividerLine)) {
            Box(Modifier.offset(x = 3.dp + 18.dp * position, y = 3.dp).size(21.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun TextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = CampusTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 54.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant)
            .border(1.dp, if (focused) colors.primary else Color.Transparent, RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
        enabled = enabled, singleLine = singleLine, textStyle = CampusTheme.textStyles.body1.copy(color = colors.onSurface),
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        interactionSource = source, cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
            val verticalPadding = if (trailingIcon == null) 10.dp else 5.dp
            Row(Modifier.padding(start = 15.dp, end = if (trailingIcon == null) 15.dp else 4.dp, top = verticalPadding, bottom = verticalPadding), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(label, color = colors.onSurfaceSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
                trailingIcon?.invoke()
            }
        },
    )
}

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier.fillMaxSize().background(CampusTheme.colorScheme.surface).windowInsetsPadding(contentWindowInsets)) {
        topBar?.invoke()
        Box(Modifier.weight(1f).fillMaxWidth().then(if (bottomBar != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            content(PaddingValues(0.dp))
        }
        bottomBar?.invoke()
    }
}

@Composable
fun TopAppBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = CampusTheme.textStyles.title1, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SmallTopAppBar(title: String, navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (title.isBlank()) 24.dp else 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        navigationIcon()
        Text(title, style = CampusTheme.textStyles.body2, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, cornerRadius: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(cornerRadius)).background(CampusTheme.colorScheme.surfaceVariant), content = content)
}

@Composable
fun OverlayDialog(show: Boolean, onDismissRequest: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(show) { visible.targetState = show }
    if (visible.currentState || visible.targetState) {
        BackHandler { onDismissRequest() }
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest))
                AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(240)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(180))) {
                    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * .88f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(CampusTheme.colorScheme.surface).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).clip(CircleShape).background(CampusTheme.colorScheme.dividerLine))
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = CampusTheme.textStyles.title3, modifier = Modifier.weight(1f))
                            IconButton(onDismissRequest) { Icon(CampusIcons.Close, "关闭") }
                        }
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp) {
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation")
    val color = CampusTheme.colorScheme.primary
    Canvas(modifier.size(22.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        drawArc(color, rotation, 270f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun LinearProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(CircleShape).background(CampusTheme.colorScheme.dividerLine).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(CampusTheme.colorScheme.primary))
    }
}

@Composable
fun ArrowPreference(title: String, summary: String? = null, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CampusTheme.textStyles.body2)
            summary?.let { Text(it, style = CampusTheme.textStyles.footnote1, color = CampusTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 5.dp)) }
        }
        if (onClick != null) Icon(CampusIcons.ChevronRight, null, Modifier.padding(start = 12.dp).size(16.dp), tint = CampusTheme.colorScheme.onSurfaceSecondary)
    }
}

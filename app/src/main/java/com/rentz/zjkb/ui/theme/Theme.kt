package com.rentz.zjkb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rentz.zjkb.R

val CampusSans = FontFamily(Font(R.font.dm_sans))
val CampusDisplay = FontFamily(Font(R.font.instrument_serif))

@Immutable
 data class CampusColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceSecondary: Color,
    val surfaceContainer: Color,
    val dividerLine: Color,
    val error: Color,
) {
    val secondaryVariant get() = surfaceVariant
    val onSecondaryVariant get() = onSurface
}

private val DayColors = CampusColors(
    primary = Color(0xFF147D60), onPrimary = Color(0xFFFCFFFD),
    primaryContainer = Color(0xFFE4F2DF), onPrimaryContainer = Color(0xFF147D60),
    surface = Color(0xFFFCFDFB), onSurface = Color(0xFF25372F),
    surfaceVariant = Color(0xFFF0F3EE), onSurfaceSecondary = Color(0xFF6E7A72),
    surfaceContainer = Color(0xFFFFFFFF), dividerLine = Color(0xFFE2E8DF), error = Color(0xFFB64F4D),
)
private val NightColors = CampusColors(
    primary = Color(0xFF87D9B7), onPrimary = Color(0xFF123A2C),
    primaryContainer = Color(0xFF214636), onPrimaryContainer = Color(0xFFB5E9CE),
    surface = Color(0xFF151C18), onSurface = Color(0xFFE2EAE0),
    surfaceVariant = Color(0xFF252E28), onSurfaceSecondary = Color(0xFFA7B4A8),
    surfaceContainer = Color(0xFF202923), dividerLine = Color(0xFF303B32), error = Color(0xFFF0A39B),
)

object CampusTextStyles {
    private val base = TextStyle(fontFamily = CampusSans, letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))
    val main = base.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val body1 = base.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val body2 = base.copy(fontSize = 14.sp, lineHeight = 21.sp)
    val button = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val footnote1 = base.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val footnote2 = base.copy(fontSize = 10.sp, lineHeight = 15.sp)
    val subtitle = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val title1 = base.copy(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
    val title2 = base.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val title3 = base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val title4 = base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
}
private val LocalCampusColors = staticCompositionLocalOf { DayColors }
val LocalCampusContentColor = staticCompositionLocalOf { Color.Unspecified }
object CampusTheme {
    val colorScheme: CampusColors @Composable get() = LocalCampusColors.current
    val textStyles get() = CampusTextStyles
}

@Composable
fun ZjkbTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) NightColors else DayColors
    CompositionLocalProvider(LocalCampusColors provides colors, LocalCampusContentColor provides colors.onSurface, content = content)
}

@Composable
fun GbuCaTheme(content: @Composable () -> Unit) = ZjkbTheme(content)

@Composable
fun courseTone(key: String): Pair<Color, Color> {
    val palette = if (isSystemInDarkTheme()) NightCourseColors else DayCourseColors
    return palette[Math.floorMod(key.hashCode(), palette.size)]
}
private val DayCourseColors = listOf(
    Color(0xFFE4F2DF) to Color(0xFF147D60),
    Color(0xFFEEE9F8) to Color(0xFF71608E),
    Color(0xFFFAE9DB) to Color(0xFF975E3A),
    Color(0xFFE5EFF3) to Color(0xFF507784),
    Color(0xFFF6E4E7) to Color(0xFF986170),
)
private val NightCourseColors = listOf(
    Color(0xFF243E2F) to Color(0xFFACE0BA),
    Color(0xFF363044) to Color(0xFFD3C2F0),
    Color(0xFF443428) to Color(0xFFF0C6A4),
    Color(0xFF283D45) to Color(0xFFB1D5E0),
    Color(0xFF442D36) to Color(0xFFF1BACB),
)

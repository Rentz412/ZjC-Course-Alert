package com.rentz.zjkb.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Lucide line geometry; license is retained in docs/licenses/Lucide-LICENSE.txt.
object CampusIcons {
    val Months = lineIcon("calendar", "M8 2v4 M16 2v4 M3 10h18", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2", "M8 14h2 M14 14h2 M8 18h2")
    val CalendarFold = lineIcon("calendar-fold", "M8 2v4 M16 2v4 M3 10h18", "M21 16V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10Z", "M15 22v-6h6")
    val Sun = lineIcon("sun", "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0", "M12 2v2 M12 20v2 M2 12h2 M20 12h2 M4.93 4.93l1.42 1.42 M17.65 17.65l1.42 1.42 M4.93 19.07l1.42-1.42 M17.65 6.35l1.42-1.42")
    val ContactsCircle = lineIcon("user-round", "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0", "M20 21a8 8 0 0 0-16 0")
    val Back = lineIcon("arrow-left", "M19 12H5 M12 19l-7-7 7-7")
    val ChevronLeft = lineIcon("chevron-left", "M15 18l-6-6 6-6")
    val ChevronRight = lineIcon("chevron-right", "M9 18l6-6-6-6")
    val ChevronDown = lineIcon("chevron-down", "M6 9l6 6 6-6")
    val ListView = lineIcon("list", "M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01")
    val Grid = lineIcon("layout-grid", "M3 3h7v7H3Z M14 3h7v7h-7Z M3 14h7v7H3Z M14 14h7v7h-7Z")
    val Tune = lineIcon("sliders-horizontal", "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-3 M14 20H3", "M14 4a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M12 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M18 20a2 2 0 1 1-4 0 2 2 0 0 1 4 0")
    val Refresh = lineIcon("refresh-cw", "M3 12a9 9 0 0 1 15.36-6.36L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-15.36 6.36L3 16 M8 16H3v5")
    val Alarm = lineIcon("bell", "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4")
    val Timer = lineIcon("timer", "M10 2h4 M12 14v-4 M19.1 4.9l-1.4 1.4", "M20 14a8 8 0 1 1-16 0 8 8 0 0 1 16 0")
    val VolumeOff = lineIcon("coffee", "M10 2v2 M14 2v2 M6 2v2 M4 7h12v9a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4Z M16 8h2a3 3 0 0 1 0 6h-2 M2 22h20")
    val Download = lineIcon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3")
    val Share = lineIcon("share", "M12 16V3 M7 8l5-5 5 5 M5 13H4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-6a1 1 0 0 0-1-1h-1")
    val Info = lineIcon("info", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M12 16v-4 M12 8h.01")
    val Location = lineIcon("map-pin", "M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0", "M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0")
    val Show = lineIcon("eye", "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0")
    val Hide = lineIcon("eye-off", "M2 2l20 20 M10.58 10.58a2 2 0 0 0 2.84 2.84 M9.9 5.24A11 11 0 0 1 12 5c7 0 10 7 10 7a15 15 0 0 1-3.16 4.19 M6.53 6.53A18 18 0 0 0 2 12s3 7 10 7a11 11 0 0 0 5.47-1.47")
    val Close = lineIcon("x", "M18 6 6 18 M6 6l12 12")
    val Shield = lineIcon("shield-check", "M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11", "m9 12 2 2 4-4")
    val Graduation = lineIcon("graduation-cap", "m2 10 10-5 10 5-10 5Z M6 12v5c3 3 9 3 12 0v-5 M22 10v6")
    val Book = lineIcon("book-open", "M12 7C9 4 5 4 2 5v15c3-1 7-1 10 2 3-3 7-3 10-2V5c-3-1-7-1-10 2Z M12 7v15")
    val Panels = lineIcon("panels", "M3 3h18v18H3Z M3 8h18 M9 8v13")
}

private fun lineIcon(name: String, vararg paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    paths.forEach { path ->
        addPath(PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
}.build()

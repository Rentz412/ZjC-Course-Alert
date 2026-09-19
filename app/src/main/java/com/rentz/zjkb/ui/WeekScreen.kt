package com.rentz.zjkb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentz.zjkb.data.remote.xq.XqSemesterRules
import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.domain.time.TimeGrid
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.theme.CampusTheme
import com.rentz.zjkb.ui.theme.courseTone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor

private val TimeColumnWidth = 36.dp

@Composable
fun WeekScreen(onOpenCourse: (String, String?) -> Unit, vm: AppViewModel) {
    val data by vm.termData.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val startMonday by vm.semesterStart.collectAsStateWithLifecycle()
    val selected by vm.selectedXnxq.collectAsStateWithLifecycle()
    val semesterOptions by vm.semesterOptions.collectAsStateWithLifecycle()
    val current = currentWeek(startMonday)
    var week by rememberSaveable(selected) { mutableIntStateOf(current) }
    var showWeekend by rememberSaveable { mutableStateOf(false) }
    var listView by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(startMonday, selected) { week = currentWeek(startMonday) }
    val monday = startMonday.plusWeeks((week - 1).toLong())
    val weekMeetings = data.meetings.filter { week in it.weeks }
    val colors = CampusTheme.colorScheme

    Scaffold(topBar = {
        TopAppBar("一周课表", actions = { CampusAction(CampusIcons.Tune, "课表显示设置") { dialog = "display" } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.padding(start = 8.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(4.dp)).clickable(enabled = !ui.syncing) { vm.loadSemesters(); dialog = "semester" }, verticalAlignment = Alignment.CenterVertically) {
                Text(XqSemesterRules.nameOf(vm.xnxq), style = CampusTheme.textStyles.footnote1, color = colors.onSurfaceSecondary)
                Icon(CampusIcons.ChevronDown, null, Modifier.padding(start = 4.dp).size(14.dp), tint = colors.onSurfaceSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CampusAction(CampusIcons.ChevronLeft, "上一周", week > 1) { week-- }
                Row(Modifier.heightIn(min = 44.dp).clickable { dialog = "week" }, verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${week.toString().padStart(2, '0')} 周", style = CampusTheme.textStyles.body2, fontWeight = FontWeight.SemiBold)
                    Icon(CampusIcons.ChevronDown, null, Modifier.padding(start = 5.dp).size(14.dp), tint = colors.onSurfaceSecondary)
                }
                CampusAction(CampusIcons.ChevronRight, "下一周", week < 30) { week++ }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).padding(4.dp)) {
                    listOf(false to CampusIcons.Grid, true to CampusIcons.ListView).forEach { (list, icon) ->
                        IconButton(onClick = { listView = list }, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(5.dp)).background(if (listView == list) colors.surfaceContainer else colors.surfaceVariant)) {
                            Icon(icon, if (list) "列表视图" else "网格视图", Modifier.size(16.dp), tint = if (listView == list) colors.onSurface else colors.onSurfaceSecondary)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${monday.format(DateTimeFormatter.ofPattern("M月d日"))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日"))}", style = CampusTheme.textStyles.footnote2.copy(fontSize = 11.sp), color = colors.onSurfaceSecondary, modifier = Modifier.weight(1f))
                if (week != current) TextButton("回到本周", { week = current })
                else Text("本周 · ${weekMeetings.size} 门次", style = CampusTheme.textStyles.footnote2, color = colors.primary)
            }
            if (ui.messageOk == false) ErrorMessage(ui.message, detail = ui.errorDetail, ok = false, modifier = Modifier.padding(bottom = 8.dp))
            if (listView) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (weekMeetings.isEmpty()) item { CampusEmpty("本周没有课程", "可以切换其他教学周查看") }
                    (1..7).forEach { day ->
                        val courses = weekMeetings.filter { it.weekday == day }.sortedBy { it.startTime }
                        if (courses.isNotEmpty()) {
                            item { Text("${weekdayName(day)} · ${monday.plusDays(day - 1L).dayOfMonth}日", style = CampusTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                            items(courses) { meeting ->
                                val tone = courseTone(meeting.rwh)
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenCourse(meeting.rwh, meeting.slotKey) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${meeting.startTime}", style = CampusTheme.textStyles.footnote1, modifier = Modifier.width(52.dp))
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(tone.second))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(data.courseByRwh[meeting.rwh]?.name ?: "未命名课程", style = CampusTheme.textStyles.subtitle)
                                        Text(listOfNotNull(meeting.room, meeting.teachers.joinToString("、").takeIf { it.isNotBlank() }).joinToString(" · "), style = CampusTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                WeekGrid(weekMeetings, monday, showWeekend, { data.courseByRwh[it]?.name ?: "未命名课程" }, onOpenCourse, Modifier.weight(1f))
            }
        }
        OverlayDialog(dialog == "display", { dialog = "" }, "课表显示") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("显示空白周末")
                    Text("周末有课时始终显示", style = CampusTheme.textStyles.footnote1, color = colors.onSurfaceSecondary)
                }
                Switch(showWeekend, { showWeekend = it }, Modifier.semantics { contentDescription = "显示空白周末" })
            }
            Text("长课程信息会自动撑高对应节次，所有日期保持对齐。", style = CampusTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(vertical = 20.dp))
            CampusPrimaryButton("完成") { dialog = "" }
        }
        OverlayDialog(dialog == "week", { dialog = "" }, "选择教学周") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..30).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { item ->
                            Button(onClick = { week = item; dialog = "" }, modifier = Modifier.weight(1f), insideMargin = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(if (week == item) colors.primary else colors.primaryContainer, if (week == item) colors.onPrimary else colors.onPrimaryContainer)) { Text("$item", style = CampusTheme.textStyles.body2) }
                        }
                    }
                }
            }
        }
        OverlayDialog(dialog == "semester", { dialog = "" }, "选择学期") {
            semesterOptions.forEach { dm ->
                TextButton(XqSemesterRules.nameOf(dm) + if (dm == vm.xnxq) " · 当前" else "", { vm.selectSemester(dm); dialog = "" }, Modifier.fillMaxWidth(), !ui.syncing)
            }
        }
    }
}

fun currentWeek(startMonday: LocalDate): Int = ScheduleLogic.weekOf(LocalDate.now(), startMonday) ?: 1

@Composable
internal fun WeekGrid(meetings: List<Meeting>, monday: LocalDate, showWeekend: Boolean, courseName: (String) -> String, onOpenCourse: (String, String?) -> Unit, modifier: Modifier = Modifier) {
    val periods = TimeGrid.periods
    val columns = remember(meetings, periods.size, showWeekend) {
        (1..7).mapNotNull { day ->
            val courses = meetings.filter { it.weekday == day && it.startPeriod in 1..periods.size && it.endPeriod >= it.startPeriod }
            if (day <= 5 || showWeekend || courses.isNotEmpty()) day to assignLanes(courses) else null
        }
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = CampusTheme.textStyles.footnote2.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val metaStyle = CampusTheme.textStyles.footnote2.copy(fontSize = 9.sp, lineHeight = 12.sp)
    val colors = CampusTheme.colorScheme
    val today = LocalDate.now()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val largestLaneCount = columns.maxOfOrNull { maxOf(1, it.second.size) } ?: 1
        val minimumLaneWidth = 44.dp * density.fontScale.coerceAtLeast(1f)
        val gridWidth = maxOf(maxWidth, TimeColumnWidth + (minimumLaneWidth * largestLaneCount) * columns.size)
        val dayWidth = (gridWidth - TimeColumnWidth) / columns.size
        val layoutItems = columns.flatMapIndexed { column, (_, lanes) ->
            val laneWidth = dayWidth / maxOf(1, lanes.size)
            lanes.flatMapIndexed { lane, values -> values.map { GridMeeting(it, courseName(it.rwh), column, lane, laneWidth) } }
        }
        val heights = remember(layoutItems, density.density, density.fontScale, measurer, nameStyle, metaStyle, periods) {
            with(density) {
                val requirements = layoutItems.map { item ->
                    val textWidth = (floor(item.laneWidth.toPx()).toInt() - 14.dp.roundToPx() - 2).coerceAtLeast(1)
                    fun measure(text: String, style: TextStyle) = if (text.isBlank()) 0 else measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = textWidth)).size.height
                    val room = item.meeting.room.orEmpty()
                    val teachers = item.meeting.teachers.joinToString("、")
                    val metadataHeight = measure(room, metaStyle) + measure(teachers, metaStyle) + if (room.isNotBlank() && teachers.isNotBlank()) 2.dp.roundToPx() else 0
                    val required = measure(item.name, nameStyle) + metadataHeight + 26.dp.roundToPx()
                    CourseSlotSize(item.meeting.startPeriod, item.meeting.endPeriod, required)
                }
                timetableRowHeights(periods.size, 42.dp.roundToPx(), requirements)
            }
        }
        val boundaries = heights.runningFold(0) { total, height -> total + height }
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(gridWidth).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${monday.monthValue}月", style = CampusTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.width(TimeColumnWidth), textAlign = TextAlign.Center)
                    columns.forEach { (day, _) ->
                        val date = monday.plusDays(day - 1L)
                        val selected = date == today
                        Column(Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(7.dp)).background(if (selected) colors.onSurface else colors.surface).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selected) "今天" else weekdayName(day), style = CampusTheme.textStyles.footnote2.copy(fontSize = 11.sp), color = if (selected) colors.surface else colors.onSurfaceSecondary)
                            Text("${date.dayOfMonth}", style = CampusTheme.textStyles.body1, color = if (selected) colors.surface else colors.onSurface, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("week-grid-scroll")) {
                    Box(Modifier.fillMaxWidth().height(with(density) { boundaries.last().toDp() })) {
                        periods.chunked(2).forEach { pair ->
                            val start = pair.first().index - 1
                            val end = pair.last().index
                            val top = boundaries[start]
                            Box(Modifier.offset { IntOffset(0, top) }.fillMaxWidth().height(.5.dp).background(colors.dividerLine))
                            Column(Modifier.offset { IntOffset(0, top) }.width(TimeColumnWidth).height(with(density) { (boundaries[end] - top).toDp() }).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${pair.first().index}–${pair.last().index}", style = CampusTheme.textStyles.footnote2, fontWeight = FontWeight.Medium)
                                Text("${pair.first().start}", style = metaStyle, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
                                Text("${pair.last().end}", style = metaStyle, color = colors.onSurfaceSecondary)
                            }
                        }
                        layoutItems.forEach { item ->
                            val meeting = item.meeting
                            val tone = courseTone(meeting.rwh)
                            val top = boundaries[meeting.startPeriod - 1]
                            val bottom = boundaries[meeting.endPeriod.coerceAtMost(periods.size)]
                            Column(Modifier.offset(x = TimeColumnWidth + dayWidth * item.column + item.laneWidth * item.lane + 2.dp, y = with(density) { top.toDp() } + 2.dp)
                                .width(item.laneWidth - 4.dp).height(with(density) { (bottom - top).toDp() } - 4.dp)
                                .testTag("course-${meeting.rwh}-${meeting.slotKey}")
                                .clip(RoundedCornerShape(7.dp)).background(tone.first).clickable { onOpenCourse(meeting.rwh, meeting.slotKey) }
                                .padding(horizontal = 5.dp, vertical = 8.dp)) {
                                Text(item.name, style = nameStyle, color = tone.second, modifier = Modifier.testTag("course-name-${meeting.rwh}"))
                                Spacer(Modifier.height(6.dp))
                                Spacer(Modifier.weight(1f))
                                meeting.room?.takeIf { it.isNotBlank() }?.let { Text(it, style = metaStyle, color = tone.second, modifier = Modifier.testTag("course-room-${meeting.rwh}")) }
                                if (meeting.room?.isNotBlank() == true && meeting.teachers.isNotEmpty()) Spacer(Modifier.height(2.dp))
                                if (meeting.teachers.isNotEmpty()) Text(meeting.teachers.joinToString("、"), style = metaStyle, color = tone.second, modifier = Modifier.testTag("course-teacher-${meeting.rwh}"))
                            }
                        }
                    }
                    Text("已缓存的课程可离线查看 · 华珠作息", style = CampusTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }
    }
}

private data class GridMeeting(val meeting: Meeting, val name: String, val column: Int, val lane: Int, val laneWidth: androidx.compose.ui.unit.Dp)

internal fun assignLanes(list: List<Meeting>): List<List<Meeting>> {
    val lanes = mutableListOf<MutableList<Meeting>>()
    for (meeting in list.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))) {
        val lane = lanes.firstOrNull { entries -> entries.all { it.endPeriod < meeting.startPeriod } }
        if (lane == null) lanes.add(mutableListOf(meeting)) else lane.add(meeting)
    }
    return lanes
}

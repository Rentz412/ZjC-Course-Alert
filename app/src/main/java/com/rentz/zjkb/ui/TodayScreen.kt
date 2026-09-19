package com.rentz.zjkb.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.domain.logic.ScheduleLogic.ClassStatus
import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.ui.components.CampusAction
import com.rentz.zjkb.ui.components.CampusBrand
import com.rentz.zjkb.ui.components.CampusEmpty
import com.rentz.zjkb.ui.components.CampusPrimaryButton
import com.rentz.zjkb.ui.components.CampusSectionTitle
import com.rentz.zjkb.ui.components.CampusTag
import com.rentz.zjkb.ui.components.ErrorMessage
import com.rentz.zjkb.ui.components.rememberCampusNow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.components.CampusIcons as MiuixIcons
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme
import com.rentz.zjkb.ui.theme.CampusDisplay

@Composable
fun TodayScreen(onOpenCourse: (String, String?) -> Unit, vm: AppViewModel) {
    val data by vm.termData.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val resting by vm.resting.collectAsStateWithLifecycle()
    val startMonday by vm.semesterStart.collectAsStateWithLifecycle()
    val now by rememberCampusNow()
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    var confirmRest by rememberSaveable { mutableStateOf(false) }
    val today = now.toLocalDate()
    val date = today.plusDays(dayOffset.toLong())
    val isToday = dayOffset == 0
    val week = ScheduleLogic.weekOf(date, startMonday)
    val dayList = ScheduleLogic.meetingsOn(date, week, data.meetings)
    val status = ScheduleLogic.statusAt(now.toLocalTime(), dayList)
    val activeMeeting = when (status) {
        is ClassStatus.InClass -> status.meeting
        is ClassStatus.Upcoming -> status.meeting
        else -> null
    }
    LaunchedEffect(today) { vm.refreshResting() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "",
                navigationIcon = { CampusBrand() },
                actions = {
                    if (ui.syncing) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    else CampusAction(MiuixIcons.Refresh, "同步课表") { vm.sync() }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${date.monthValue}月，新的节奏", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text(
                            if (!isToday) "${weekdayName(date.dayOfWeek.value)}的安排" else when (now.hour) {
                                in 5..11 -> "早上好，同学"
                                in 12..17 -> "下午好，同学"
                                else -> "晚上好，同学"
                            },
                            style = MiuixTheme.textStyles.title1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text("${date.dayOfMonth}", style = MiuixTheme.textStyles.title1.copy(fontFamily = CampusDisplay, fontSize = 44.sp, fontWeight = FontWeight.Normal))
                    Text(" / ${date.monthValue.toString().padStart(2, '0')}", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(bottom = 7.dp))
                }
                DateStrip(date, today) { dayOffset = ChronoUnit.DAYS.between(today, it).toInt() }
                Spacer(Modifier.height(14.dp))
            }
            if (isToday) {
                item {
                    TodayFocus(
                        status = status,
                        courseName = activeMeeting?.let { data.courseByRwh[it.rwh]?.name } ?: "课程",
                        resting = resting,
                        now = now.toLocalTime(),
                        onRest = { if (resting) vm.setResting(false) else confirmRest = true },
                        onOpen = { activeMeeting?.let { onOpenCourse(it.rwh, it.slotKey) } },
                    )
                }
            }
            if (ui.message != null) {
                item { ErrorMessage(ui.message, detail = ui.errorDetail, ok = ui.messageOk, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            }
            item {
                CampusSectionTitle(
                    if (isToday) "今天的安排" else "当日课程",
                    "${dayList.size} 门次" + (week?.let { " · 第 $it 周" } ?: ""),
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                )
            }
            if (dayList.isEmpty()) {
                item {
                    CampusEmpty(
                        if (ui.syncing) "正在整理你的课表" else if (week == null) "还没到上课的日子" else "课表留白，生活继续",
                        if (ui.syncing) "同步完成后自动显示" else "这一天没有课程安排",
                    )
                }
            } else {
                itemsIndexed(dayList, key = { index, meeting -> "${meeting.rwh}-${meeting.slotKey}-${meeting.weeks.hashCode()}-$index" }) { index, meeting ->
                    TimelineCourse(
                        meeting = meeting,
                        name = data.courseByRwh[meeting.rwh]?.name ?: "未命名课程",
                        past = isToday && now.toLocalTime() >= meeting.endTime,
                        active = isToday && meeting == activeMeeting,
                        inClass = isToday && status is ClassStatus.InClass && meeting == activeMeeting,
                        last = index == dayList.lastIndex,
                        onClick = { onOpenCourse(meeting.rwh, meeting.slotKey) },
                    )
                }
            }
        }
        OverlayDialog(show = confirmRest, onDismissRequest = { confirmRest = false }, title = "今天休息一下？") {
            Text("暂停今天的全部上课提醒，不会删除课程。明天将自动恢复。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            Spacer(Modifier.height(24.dp))
            CampusPrimaryButton("暂停今日提醒") { vm.setResting(true); confirmRest = false }
            TextButton("继续提醒我", onClick = { confirmRest = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DateStrip(date: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val monday = date.with(DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { index ->
            val day = monday.plusDays(index.toLong())
            val selected = day == date
            val background by animateColorAsState(if (selected) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.surface, label = "selectedDay")
            Column(
                Modifier.weight(1f).heightIn(min = 66.dp).clip(RoundedCornerShape(8.dp)).background(background)
                    .clickable { onSelect(day) }.padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val foreground = if (selected) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurfaceSecondary
                Text(if (day == today) "今天" else listOf("一", "二", "三", "四", "五", "六", "日")[index], style = MiuixTheme.textStyles.footnote2, color = foreground)
                Text("${day.dayOfMonth}", style = MiuixTheme.textStyles.title4, color = if (selected) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurface)
                if (day == today) Box(Modifier.size(3.dp).clip(CircleShape).background(foreground))
            }
        }
    }
}

@Composable
private fun TodayFocus(status: ClassStatus, courseName: String, resting: Boolean, now: LocalTime, onRest: () -> Unit, onOpen: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val meeting = when (status) {
        is ClassStatus.InClass -> status.meeting
        is ClassStatus.Upcoming -> status.meeting
        else -> null
    }
    Column(Modifier.fillMaxWidth().background(colors.primaryContainer).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (resting) "今日休息 · 仅今天生效" else when (status) {
                    is ClassStatus.InClass -> "正在上课 · 第 ${status.meeting.startPeriod}–${status.meeting.endPeriod} 节"
                    is ClassStatus.Upcoming -> "下一节 · 第 ${status.meeting.startPeriod}–${status.meeting.endPeriod} 节"
                    ClassStatus.Finished -> "今天的课程已结束"
                    ClassStatus.Free -> "今天，自由安排"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(if (resting) "恢复提醒" else "今日休息", onClick = onRest)
        }
        if (resting || meeting == null) {
            Text(if (resting) "今天，按自己的节奏" else "把时间留给生活", style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(top = 8.dp))
            Text(if (resting) "课程照常显示，提醒暂时安静。" else "愿你度过轻松而充实的一天。", style = MiuixTheme.textStyles.footnote1, color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(courseName, style = MiuixTheme.textStyles.title3.copy(fontSize = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${meeting.startTime} – ${meeting.endTime}" + meeting.teachers.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = " · ").orEmpty(), style = MiuixTheme.textStyles.footnote1, color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 7.dp), maxLines = 2)
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MiuixIcons.Location, null, Modifier.size(16.dp), tint = colors.onPrimaryContainer)
                        Spacer(Modifier.width(5.dp))
                        Text(meeting.room ?: "教室待定", style = MiuixTheme.textStyles.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                val remaining = if (status is ClassStatus.InClass) status.endsInMinutes else (status as ClassStatus.Upcoming).startsInMinutes
                val progress = if (status is ClassStatus.InClass) {
                    (ChronoUnit.SECONDS.between(now, meeting.endTime).toFloat() / maxOf(1, meeting.durationMinutes * 60)).coerceIn(0f, 1f)
                } else (1f - remaining / 60f).coerceIn(.03f, 1f)
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        drawCircle(colors.onPrimaryContainer.copy(alpha = .12f), style = Stroke(3.dp.toPx()))
                        drawArc(colors.onPrimaryContainer, -90f, 360 * progress, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (remaining == 0L) "<1" else "$remaining", style = MiuixTheme.textStyles.title1.copy(fontFamily = CampusDisplay, fontSize = if (remaining >= 100) 32.sp else 48.sp, fontWeight = FontWeight.Normal), color = colors.onPrimaryContainer)
                        Text(if (status is ClassStatus.InClass) "分钟后下课" else "分钟后上课", style = MiuixTheme.textStyles.footnote2, color = colors.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCourse(meeting: Meeting, name: String, past: Boolean, active: Boolean, inClass: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp).heightIn(min = 96.dp)) {
        Column(Modifier.width(46.dp).padding(top = 3.dp)) {
            Text(meeting.startTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = MiuixTheme.textStyles.footnote1, color = if (past) colors.onSurfaceSecondary else colors.onSurface)
            Text(meeting.endTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Canvas(Modifier.width(24.dp).height(96.dp)) {
            if (!last) drawLine(colors.dividerLine, Offset(size.width / 2, 16.dp.toPx()), Offset(size.width / 2, size.height), 1.dp.toPx())
            if (active) drawCircle(colors.primaryContainer, 7.dp.toPx(), Offset(size.width / 2, 10.dp.toPx()))
            drawCircle(if (active) colors.primary else colors.dividerLine, 3.5.dp.toPx(), Offset(size.width / 2, 10.dp.toPx()))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MiuixTheme.textStyles.subtitle, color = if (past) colors.onSurfaceSecondary else colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (active) { Spacer(Modifier.width(6.dp)); CampusTag(if (inClass) "上课中" else "即将开始") }
                else if (past) { Spacer(Modifier.width(6.dp)); Text("已下课", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary) }
            }
            Text(listOfNotNull(meeting.room, meeting.teachers.takeIf { it.isNotEmpty() }?.joinToString("、")).joinToString(" · "), style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

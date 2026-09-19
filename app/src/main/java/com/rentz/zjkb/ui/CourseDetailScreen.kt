package com.rentz.zjkb.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.ui.components.CampusAction
import com.rentz.zjkb.ui.components.CampusEmpty
import com.rentz.zjkb.ui.components.CampusPrimaryButton
import com.rentz.zjkb.ui.components.CampusSectionTitle
import com.rentz.zjkb.ui.components.CampusTag
import com.rentz.zjkb.ui.components.ReminderControls
import com.rentz.zjkb.ui.theme.courseTone
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.components.CampusIcons as MiuixIcons
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme

@Composable
fun CourseDetailScreen(rwh: String, highlightSlotKey: String? = null, vm: AppViewModel, onBack: () -> Unit) {
    val data by vm.termData.collectAsStateWithLifecycle()
    val startMonday by vm.semesterStart.collectAsStateWithLifecycle()
    val reminders by vm.remindersEnabled.collectAsStateWithLifecycle()
    val minutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val course = data.courseByRwh[rwh]
    val meetings = data.meetings.filter { it.rwh == rwh }.sortedWith(compareBy({ it.weekday }, { it.startTime }))
    val weeks = meetings.flatMap { it.weeks }.toSet()
    val currentWeek = currentWeek(startMonday)
    val tone = courseTone(rwh)
    val colors = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    var reminderDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(highlightSlotKey, meetings) {
        val index = meetings.indexOfFirst { it.slotKey == highlightSlotKey }
        if (index > 1) listState.animateScrollToItem(2 + index)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "课程详情",
                navigationIcon = { CampusAction(MiuixIcons.Back, "返回", onClick = onBack) },
                actions = { CampusAction(MiuixIcons.Alarm, "设置上课提醒") { reminderDialog = true } },
            )
        },
    ) { padding ->
        if (course == null) {
            CampusEmpty("暂时找不到这门课", "请返回课表，重新同步后再试", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().background(tone.first).padding(24.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(course.category, course.nature).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "课程信息" }, style = MiuixTheme.textStyles.footnote1, color = tone.second, modifier = Modifier.weight(1f))
                            Icon(MiuixIcons.Book, null, Modifier.size(24.dp), tint = tone.second)
                        }
                        Text(course.name, style = MiuixTheme.textStyles.title1, modifier = Modifier.padding(top = 20.dp))
                        course.nameEn?.takeIf { it.isNotBlank() }?.let { Text(it, style = MiuixTheme.textStyles.footnote1, color = tone.second, modifier = Modifier.padding(top = 6.dp)) }
                        course.code?.let { Text("课程编号 $it", style = MiuixTheme.textStyles.footnote1, color = tone.second, modifier = Modifier.padding(top = 8.dp)) }
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailFact("任课教师", meetings.flatMap { it.teachers }.distinct().joinToString("、").ifBlank { "待教务公布" }, Modifier.weight(1.4f))
                            if (course.credits > 0) DetailFact("学分", "${course.credits}", Modifier.weight(1f))
                            if (course.hours > 0) DetailFact("总学时", "${course.hours}", Modifier.weight(1f))
                        }
                    }
                }
                item { CampusSectionTitle("上课安排", modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) }
                items(meetings, key = { "${it.slotKey}-${it.endTime}-${it.weeks.hashCode()}" }) { meeting ->
                    val highlighted = meeting.slotKey == highlightSlotKey
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (highlighted) colors.primaryContainer else colors.surface).padding(vertical = 10.dp, horizontal = if (highlighted) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(44.dp).heightIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(if (highlighted) colors.surface else colors.surfaceVariant).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("星期", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary)
                            Text(weekdayName(meeting.weekday).removePrefix("周"), style = MiuixTheme.textStyles.title4)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${meeting.startTime} – ${meeting.endTime}", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (highlighted) CampusTag("已选课次")
                            }
                            Text("第 ${meeting.startPeriod}–${meeting.endPeriod} 节 · ${meeting.room ?: "教室待定"}", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 5.dp))
                            Text(ScheduleLogic.formatWeeks(meeting.weeks) + if (meeting.role.isNotBlank()) " · ${meeting.role}" else "", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        CampusSectionTitle("教学周历")
                        Spacer(Modifier.height(16.dp))
                        (1..maxOf(20, weeks.maxOrNull() ?: 20).coerceAtMost(30)).chunked(10).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                row.forEach { week ->
                                    val selected = week == currentWeek
                                    val scheduled = week in weeks
                                    Text(
                                        "$week",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = if (selected) colors.onPrimary else if (scheduled) tone.second else colors.onSurfaceSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                                            .background(if (selected) colors.primary else if (scheduled) tone.first else colors.surfaceVariant).padding(vertical = 8.dp),
                                    )
                                }
                                repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text("实色为本周，浅色为上课周", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 6.dp))
                        val extra = listOfNotNull(
                            course.className?.takeIf { it.isNotBlank() }?.let { "教学班" to it },
                            course.college?.takeIf { it.isNotBlank() }?.let { "开课学院" to it },
                            course.seq?.takeIf { it.isNotBlank() }?.let { "课序号" to it },
                            course.enrollTime?.takeIf { it.isNotBlank() }?.let { "选课时间" to it },
                            course.capacity?.let { "选课人数" to "${course.enrolled ?: "—"} / $it" },
                        )
                        if (extra.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Box(Modifier.fillMaxWidth().height(.5.dp).background(colors.dividerLine))
                            extra.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(label, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.width(62.dp))
                                    Text(value, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        CampusPrimaryButton(if (reminders) "上课前 $minutes 分钟提醒我" else "设置上课提醒", Modifier.padding(top = 24.dp)) { reminderDialog = true }
                        if (course.unparsed.isNotEmpty()) {
                            CampusSectionTitle("教务补充信息", modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                            course.unparsed.forEach { Text(it, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary) }
                        }
                    }
                }
            }
        }
        OverlayDialog(show = reminderDialog, onDismissRequest = { reminderDialog = false }, title = "提前一点，从容出发") {
            Text("所有课程统一使用此提醒设置。", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary)
            Spacer(Modifier.height(20.dp))
            ReminderControls(reminders, minutes, vm::setRemindersEnabled, vm::setReminderMinutes)
            CampusPrimaryButton("完成", Modifier.padding(top = 24.dp)) { reminderDialog = false }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Text(value, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

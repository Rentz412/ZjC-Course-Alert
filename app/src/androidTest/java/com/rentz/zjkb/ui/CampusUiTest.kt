package com.rentz.zjkb.ui

import android.content.res.Configuration
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rentz.zjkb.ZjkbApp
import com.rentz.zjkb.data.local.room.CourseEntity
import com.rentz.zjkb.data.local.room.MeetingEntity
import com.rentz.zjkb.domain.oobe.OobeStep
import com.rentz.zjkb.ui.theme.ZjkbTheme
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.ui.theme.CampusTheme

@RunWith(AndroidJUnit4::class)
class CampusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: ZjkbApp
    private lateinit var vm: AppViewModel
    private lateinit var store: ViewModelStore
    private val term = "20260"
    private val historicalTerm = "20251"

    @Before
    fun prepare() = runBlocking {
        app = ZjkbApp.instance
        app.settings.selectedXnxq = term
        app.settings.remindersEnabled = false
        app.settings.restDay = null
        app.settings.setSemesterStartMonday(term, LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2))
        app.settings.setSemesterStartMonday(historicalTerm, LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2))
        app.settings.lastSyncAt = System.currentTimeMillis()
        app.db.courseDao().deleteByXnxq(term)
        app.db.meetingDao().deleteByXnxq(term)
        app.db.courseDao().deleteByXnxq(historicalTerm)
        app.db.meetingDao().deleteByXnxq(historicalTerm)
        val names = listOf("数据结构与算法", "大学英语（三）", "交互设计基础", "线性代数", "计算机网络", "体育（三）")
        app.db.courseDao().insertAll(names.mapIndexed { index, name -> course("ui-$index", term, name) } + course("ui-history", historicalTerm, "历史学期课程"))
        val today = LocalDate.now().dayOfWeek.value
        val upcoming = LocalTime.now().plusMinutes(12).withSecond(0).withNano(0)
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        app.db.meetingDao().insertAll(listOf(
            meeting("ui-0", today, 3, upcoming.format(timeFormat), upcoming.plusMinutes(90).format(timeFormat)),
            meeting("ui-1", today, 1, "08:30", "10:00"),
            meeting("ui-2", today, 5, "14:30", "16:00"),
            meeting("ui-0", 1, 1, "08:30", "10:00"),
            meeting("ui-1", 2, 3, "10:20", "11:50"),
            meeting("ui-3", 3, 1, "08:30", "10:00"),
            meeting("ui-4", 4, 5, "14:30", "16:00"),
            meeting("ui-5", 5, 7, "16:20", "17:50"),
            meeting("ui-history", 1, 1, "08:30", "10:00").copy(xnxq = historicalTerm),
        ))
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            store = ViewModelStore()
            vm = ViewModelProvider(store, AppViewModel.Factory)[AppViewModel::class.java]
        }
    }

    @After
    fun finish() {
        compose.runOnUiThread { store.clear() }
        app.settings.restDay = null
        app.settings.selectedXnxq = term
        app.reminderScheduler.cancelAll()
    }

    @Test
    fun navigationRestAndCourseDetails() {
        render { AppNavHost(vm, {}, app.reminderScheduler) }
        waitFor("今天的安排")
        compose.waitUntil(5000) { vm.termData.value.courses.size == 6 }
        capture("today-light")
        compose.onNode(hasText("今日休息") and hasClickAction()).performClick()
        compose.onNodeWithText("暂停今日提醒").performClick()
        waitFor("今天，按自己的节奏")
        assertTrue(vm.resting.value)
        compose.onNode(hasText("我的") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("今日休息开关").assertIsOn()
        capture("settings-light")
        compose.onNodeWithContentDescription("今日休息开关").performClick()
        assertFalse(vm.resting.value)
        compose.onNode(hasText("课表") and isSelectable()).performClick()
        waitFor("一周课表")
        capture("week-light")
        compose.onNodeWithContentDescription("下一周").performClick()
        compose.onNodeWithText("第 04 周").assertExists()
        compose.onNodeWithText("回到本周").performClick()
        compose.onNodeWithText("第 03 周").assertExists()
        val courses = compose.onAllNodes(hasText("数据结构与算法") and hasClickAction())
        val visible = courses.fetchSemanticsNodes().indices.first { courses[it].isDisplayed() }
        courses[visible].performClick()
        waitFor("课程详情")
        capture("course-light")
        compose.onNodeWithContentDescription("设置上课提醒").performClick()
        compose.onNodeWithText("所有课程统一使用此提醒设置。").assertExists()
        capture("reminder-sheet", dialog = true)
    }

    @Test
    fun nightPaletteAndOfflineSemesterSwitch() {
        render(dark = true) { AppNavHost(vm, {}, app.reminderScheduler) }
        compose.waitUntil(5000) { vm.termData.value.courses.size == 6 }
        capture("today-dark")
        compose.onNode(hasText("课表") and isSelectable()).performClick()
        waitFor("一周课表")
        capture("week-dark")
        compose.onNode(hasText("我的") and isSelectable()).performClick()
        capture("settings-dark")
        compose.runOnUiThread { vm.selectSemester(historicalTerm) }
        compose.waitUntil(5000) { vm.termData.value.courses.singleOrNull()?.name == "历史学期课程" }
        assertEquals(historicalTerm, vm.xnxq)
        assertEquals(historicalTerm, app.settings.selectedXnxq)
    }

    @Test
    fun onboardingKeyboardAndReminderStep() {
        render { SetupFlowScreen(vm, app.reminderScheduler, OobeStep.Login, {}) }
        compose.onNodeWithText("连接并导入课表").assertIsNotEnabled()
        capture("onboarding-light")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("2024001234")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("demo-only")
        compose.onNodeWithText("连接并导入课表").assertIsEnabled()
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertExists()
    }

    @Test
    fun toolbarAndSystemBackSlideDetailToRight() {
        render { AppNavHost(vm, {}, app.reminderScheduler) }
        compose.waitUntil(5000) { vm.termData.value.courses.size == 6 }
        compose.onNode(hasText("课表") and isSelectable()).performClick()
        waitFor("一周课表")
        repeat(2) { attempt ->
            openVisibleCourse()
            val before = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            try {
                if (attempt == 0) compose.onNodeWithContentDescription("返回").performClick()
                else compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.mainClock.advanceTimeBy(128)
                assertDetailMovedRight(before)
                capture(if (attempt == 0) "return-toolbar-midway" else "return-system-midway")
            } finally {
                compose.mainClock.autoAdvance = true
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("返回").assertDoesNotExist()
            compose.onNode(hasText("课表") and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun predictiveBackUsesSameDirectionAndCancelsWithoutPopping() {
        render { AppNavHost(vm, {}, app.reminderScheduler) }
        compose.waitUntil(5000) { vm.termData.value.courses.size == 6 }
        compose.onNode(hasText("课表") and isSelectable()).performClick()
        waitFor("一周课表")
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            openVisibleCourse()
            val before = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
            val dispatcher = compose.activity.onBackPressedDispatcher
            compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, edge)) }
            compose.waitForIdle()
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 500f, .25f, edge)) }
            compose.waitForIdle()
            assertDetailMovedRight(before)
            capture("return-gesture-$edge-midway")
            compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
            compose.waitForIdle()
            val restored = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
            assertEquals(before.left, restored.left, 1f)
            assertEquals(before.top, restored.top, 1f)
            compose.onNodeWithText("课程详情").assertIsDisplayed()
            compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, edge)) }
            compose.waitForIdle()
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 500f, .25f, edge)) }
            compose.waitForIdle()
            compose.runOnUiThread { dispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("返回").assertDoesNotExist()
            compose.onNode(hasText("课表") and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun systemBackDismissesReminderBeforeLeavingCourse() {
        render { AppNavHost(vm, {}, app.reminderScheduler) }
        compose.waitUntil(5000) { vm.termData.value.courses.size == 6 }
        compose.onNode(hasText("课表") and isSelectable()).performClick()
        waitFor("一周课表")
        openVisibleCourse()
        compose.onNodeWithContentDescription("设置上课提醒").performClick()
        compose.onNodeWithText("所有课程统一使用此提醒设置。").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("所有课程统一使用此提醒设置。").assertDoesNotExist()
        compose.onNodeWithText("课程详情").assertIsDisplayed()
    }

    private fun openVisibleCourse() {
        val courses = compose.onAllNodes(hasText("数据结构与算法") and hasClickAction())
        val visible = courses.fetchSemanticsNodes().indices.first { courses[it].isDisplayed() }
        courses[visible].performClick()
        waitFor("课程详情")
    }

    private fun assertDetailMovedRight(before: Rect) {
        val during = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        assertTrue("子页面应向右退出", during.left > before.left + 12f)
        assertEquals("退出不应缩小页面", before.height, during.height, 1f)
        assertEquals("退出不应发生纵向偏移", before.top, during.top, 1f)
    }

    @Test
    fun todayMatchesCanvasWithoutExtraDateButtons() {
        render { TodayScreen({ _, _ -> }, vm) }
        waitFor("今天的安排")
        compose.onNodeWithText("上一周").assertDoesNotExist()
        compose.onNodeWithText("下一周").assertDoesNotExist()
        compose.onNodeWithText("回到今天").assertDoesNotExist()
        val otherDay = if (LocalDate.now().dayOfWeek.value == 1) "二" else "一"
        compose.onNode(hasText(otherDay) and hasClickAction()).performClick()
        compose.onNodeWithText("回到今天").assertDoesNotExist()
        compose.onNode(hasText("今天") and hasClickAction()).performClick()
        compose.onNodeWithText("今天的安排").assertExists()
    }

    @Test fun longCourseFieldsRemainFullyLaidOut() = verifyLongCourses(390, 1f, false)
    @Test fun narrowScreenAndLargeFontsDoNotClipCourseFields() = verifyLongCourses(320, 1.4f, false)
    @Test fun overlappingCoursesKeepEveryTeacherVisible() = verifyLongCourses(390, 1f, true)

    private fun verifyLongCourses(width: Int, fontScale: Float, overlap: Boolean) {
        val title = "数字人文理论与实践——跨学科研究方法"
        val room = "19栋人文综合教学楼［19-402］"
        val teachers = listOf("欧阳嘉宁", "司徒思远", "陈嘉宁")
        val longCourse = Meeting("long", "主讲", teachers, setOf(3), 1, 1, 2, LocalTime.of(8, 30), LocalTime.of(10, 0), room, "")
        val courses = listOf(longCourse, longCourse.copy(rwh = "peer", weekday = 2, room = "1栋［1-302］", teachers = listOf("刘舒"))) +
            if (overlap) listOf(longCourse.copy(rwh = "overlap", teachers = listOf("诸葛思远"))) else emptyList()
        render(dark = overlap) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.fillMaxSize().background(CampusTheme.colorScheme.surface)) {
                    WeekGrid(courses, LocalDate.now().with(DayOfWeek.MONDAY), true,
                        { if (it == "peer") "文学概论" else title }, { _, _ -> },
                        Modifier.width(width.dp).padding(16.dp).testTag("long-grid-container"))
                }
            }
        }
        compose.waitForIdle()
        for (id in if (overlap) listOf("long", "peer", "overlap") else listOf("long", "peer")) {
            for (field in listOf("name", "room", "teacher")) {
                val node = compose.onNodeWithTag("course-$field-$id", useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.size)
                assertFalse("长文本不应裁切或省略：$id/$field", layouts.single().hasVisualOverflow)
                val textBounds = node.getUnclippedBoundsInRoot()
                val day = if (id == "peer") 2 else 1
                val cardBounds = compose.onNodeWithTag("course-$id-$day-08:30-主讲").getUnclippedBoundsInRoot()
                assertTrue("文本底部必须在卡片内部：$id/$field", textBounds.bottom <= cardBounds.bottom)
            }
        }
        val first = compose.onNodeWithTag("course-long-1-08:30-主讲").getUnclippedBoundsInRoot()
        val peer = compose.onNodeWithTag("course-peer-2-08:30-主讲").getUnclippedBoundsInRoot()
        assertEquals(first.top, peer.top)
        assertEquals(first.bottom, peer.bottom)
        capture("long-courses-$width-$fontScale-$overlap")
    }

    private fun render(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) { ZjkbTheme(content) }
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = root.captureToImage().asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = compose.activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZjC-UI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            requireNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val file = File(compose.activity.getExternalFilesDir(null), "ui-verification/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun course(id: String, semester: String, name: String) = CourseEntity(
        rwh = id, xnxq = semester, name = name, nameEn = null, code = "0809203", seq = null,
        className = "计算机科学与技术 2 班", credits = 3.0, hours = 48.0, nature = "必修", category = "专业课程",
        college = "信息工程学院", enrollTime = null, capacity = null, enrolled = null, rawKcxx = "", unparsed = "[]", syncedAt = System.currentTimeMillis(),
    )

    private fun meeting(id: String, day: Int, period: Int, start: String, end: String) = MeetingEntity(
        rwh = id, xnxq = term, role = "主讲", teachers = "陈嘉宁", weeks = (1..16).joinToString(","),
        weekday = day, startPeriod = period, endPeriod = period + 1, startTime = start, endTime = end, room = "笃学楼 B305", rawText = "",
    )
}

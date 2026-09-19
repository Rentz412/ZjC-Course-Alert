package com.rentz.zjkb.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.rentz.zjkb.R
import com.rentz.zjkb.reminder.ReminderScheduler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.theme.CampusTheme

private const val PAGE_TRANSITION_MS = 360
private val PageEasing = CubicBezierEasing(0.22f, 0f, 0.12f, 1f)
private val DetailEnter = slideInHorizontally(tween(PAGE_TRANSITION_MS, easing = PageEasing)) { it } +
    fadeIn(tween(PAGE_TRANSITION_MS / 2, easing = PageEasing))
private val DetailExit = slideOutHorizontally(tween(PAGE_TRANSITION_MS, easing = PageEasing)) { it } +
    fadeOut(tween(PAGE_TRANSITION_MS / 2, delayMillis = PAGE_TRANSITION_MS / 2, easing = PageEasing))
private val ParentExit = slideOutHorizontally(tween(PAGE_TRANSITION_MS, easing = PageEasing)) { -it / 5 } +
    fadeOut(tween(PAGE_TRANSITION_MS, easing = PageEasing), targetAlpha = 0.92f)
private val ParentEnter = slideInHorizontally(tween(PAGE_TRANSITION_MS, easing = PageEasing)) { -it / 5 } +
    fadeIn(tween(PAGE_TRANSITION_MS, easing = PageEasing), initialAlpha = 0.92f)

@Composable
fun AppNavHost(
    vm: AppViewModel,
    onRerunOobe: () -> Unit,
    reminderScheduler: ReminderScheduler,
) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = "tabs",
        modifier = Modifier.fillMaxSize().background(CampusTheme.colorScheme.surface),
        enterTransition = { DetailEnter },
        exitTransition = { ParentExit },
        popEnterTransition = { ParentEnter },
        popExitTransition = { DetailExit },
        // Navigation 2.10 的手势返回独立于普通 pop，必须显式使用同一组动画。
        predictivePopEnterTransition = { _ -> ParentEnter },
        predictivePopExitTransition = { _ -> DetailExit },
    ) {
        composable("tabs") {
            TabsScreen(
                vm = vm,
                onOpenCourse = { rwh, slotKey ->
                    val route = if (slotKey != null) "course/$rwh?mk=${Uri.encode(slotKey)}" else "course/$rwh"
                    nav.navigate(route)
                },
                onRerunOobe = onRerunOobe,
                reminderScheduler = reminderScheduler,
            )
        }
        composable("course/{rwh}?mk={mk}") { entry ->
            val rwh = entry.arguments?.getString("rwh") ?: return@composable
            val slotKey = entry.arguments?.getString("mk")
            CourseDetailScreen(rwh = rwh, highlightSlotKey = slotKey, vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

/** 主页面保留跟手横滑，底部导航严格使用画布规格。 */
@Composable
private fun TabsScreen(
    vm: AppViewModel,
    onOpenCourse: (String, String?) -> Unit,
    onRerunOobe: () -> Unit,
    reminderScheduler: ReminderScheduler,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val colors = CampusTheme.colorScheme
            Row(
                Modifier.fillMaxWidth().background(colors.surfaceContainer).navigationBarsPadding()
                    .padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 16.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.targetPage == index
                    Box(Modifier.weight(1f).heightIn(min = 58.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
                        .selectable(selected, role = Role.Tab, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(tab.icon, null, Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurfaceSecondary)
                            Text(tab.label, style = CampusTheme.textStyles.footnote2.copy(fontSize = 11.sp), color = if (selected) colors.primary else colors.onSurfaceSecondary)
                        }
                        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 18.dp).size(4.dp).clip(CircleShape).background(colors.primary))
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            beyondViewportPageCount = tabs.size - 1,
        ) { page ->
            when (page) {
                0 -> TodayScreen(
                    onOpenCourse = onOpenCourse,
                    vm = vm,
                )
                1 -> WeekScreen(onOpenCourse = onOpenCourse, vm = vm)
                2 -> SettingsScreen(
                    reminderScheduler = reminderScheduler,
                    vm = vm,
                    onRerunOobe = onRerunOobe,
                )
            }
        }
    }
}

private val tabs = listOf(
    Tab("今日", CampusIcons.Sun),
    Tab("课表", CampusIcons.Months),
    Tab("我的", CampusIcons.ContactsCircle),
)

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

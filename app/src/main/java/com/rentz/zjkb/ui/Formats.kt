package com.rentz.zjkb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.rentz.zjkb.R

/** 星期文案（来自资源），weekday: 1=周一 … 7=周日。 */
@Composable
fun weekdayName(weekday: Int): String =
    stringArrayResource(R.array.weekdays).getOrElse(weekday - 1) { "?" }

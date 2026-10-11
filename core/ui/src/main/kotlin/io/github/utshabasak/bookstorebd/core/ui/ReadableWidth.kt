package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a column of text, lists and buttons grows. Lines longer than this are hard to read. */
val ReadableWidth: Dp = 720.dp

/** The widest a form grows: its fields read as one column on any screen. */
val FormWidth: Dp = 560.dp

/**
 * Centres a column at a readable width on tablets, foldables and phones held
 * sideways; on a phone held upright it changes nothing. On a scrolling column,
 * apply it after the scroll modifier, so the whole screen still scrolls.
 */
fun Modifier.readableWidth(max: Dp = ReadableWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()

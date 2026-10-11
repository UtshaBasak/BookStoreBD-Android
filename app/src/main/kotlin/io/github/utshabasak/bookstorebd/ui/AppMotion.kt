package io.github.utshabasak.bookstorebd.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * The app's screen transitions, after Material's motion guidance: opening a
 * screen slides it in a short way from the side as it fades in, going back
 * does the reverse, and switching tabs fades through, since tabs are siblings
 * rather than steps. Short durations keep navigation feeling immediate.
 */
internal class AppMotion(
    /** The content keys of the tabs' first screens. */
    private val tabRoots: Set<Any>,
) {
    private fun AnimatedContentTransitionScope<Scene<NavKey>>.isTabSwitch(): Boolean {
        val from = initialState.entries.map { it.contentKey }
        val to = targetState.entries.map { it.contentKey }
        // Opening or closing a screen keeps one stack as the start of the other.
        // Moving between two tabs' first screens is a switch too, though Home's stack sits under each tab's.
        val betweenRoots = from.lastOrNull() in tabRoots && to.lastOrNull() in tabRoots
        val opened = from.size <= to.size && to.take(from.size) == from
        val closed = to.size <= from.size && from.take(to.size) == to
        return betweenRoots || !(opened || closed)
    }

    private fun fadeThrough(): ContentTransform =
        (fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) + scaleIn(tween(DURATION_MS, easing = easing), initialScale = TAB_SCALE))
            .togetherWith(fadeOut(tween(FADE_OUT_MS)))

    private fun slide(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return (
            slideInHorizontally(tween(DURATION_MS, easing = easing)) { width -> direction * width / SLIDE_FRACTION } +
                fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS / 2))
        ).togetherWith(
            slideOutHorizontally(tween(DURATION_MS, easing = easing)) { width -> -direction * width / SLIDE_FRACTION } +
                fadeOut(tween(FADE_OUT_MS)),
        )
    }

    val forward: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (isTabSwitch()) fadeThrough() else slide(forward = true)
    }

    val back: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (isTabSwitch()) fadeThrough() else slide(forward = false)
    }

    val predictiveBack: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { _ -> slide(forward = false) }

    private companion object {
        const val DURATION_MS = 300
        const val FADE_IN_MS = 210
        const val FADE_OUT_MS = 90
        const val SLIDE_FRACTION = 10
        const val TAB_SCALE = 0.98f

        /** Material's "emphasized decelerate" curve. */
        val easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    }
}

package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Placeholders in the shape of the content on its way, so a screen keeps its
 * layout while it loads and the content settles into place without a jump.
 * A soft shimmer shows that something is happening; TalkBack hears one
 * "Loading" for the whole screen.
 */

private val LocalShimmer = compositionLocalOf<Brush?> { null }

/** Shares one shimmer across every placeholder on the screen, so they move together. */
@Composable
fun SkeletonScreen(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.core_ui_loading),
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    val start = -SHIMMER_WIDTH + progress * (SHIMMER_TRAVEL + SHIMMER_WIDTH)
    val brush =
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, start / 2),
            end = Offset(start + SHIMMER_WIDTH, (start + SHIMMER_WIDTH) / 2),
        )

    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_MS)
        slow = true
    }
    val slowMessage = stringResource(R.string.core_ui_loading_slow)
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = if (slow) "$message $slowMessage" else message
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        CompositionLocalProvider(LocalShimmer provides brush) { content() }
        if (slow) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
            ) {
                Text(
                    slowMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** One placeholder block; inside [SkeletonScreen] it shimmers with the rest. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val brush = LocalShimmer.current
    val fallback = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(fallback)),
    )
}

/** A book card's shape: cover, title, writer and price. */
@Composable
fun BookCardSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 156.dp,
) {
    Column(modifier.width(width).padding(bottom = 8.dp)) {
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO),
            RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(12.dp))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(16.dp))
    }
}

/** A section title above a row of book cards, as on the home screen. */
@Composable
fun ShelfSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        SkeletonBlock(Modifier.padding(horizontal = 16.dp).width(140.dp).height(22.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(SHELF_CARDS) { BookCardSkeleton() }
        }
    }
}

/** A grid of book cards, as in the catalogue and a seller's shop. */
@Composable
fun BookGridSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(GRID_CARDS) {
            BoxWithConstraints { BookCardSkeleton(width = maxWidth) }
        }
    }
}

/** A list row with a small cover, as in the cart, the wishlist and order lists. */
@Composable
fun BookRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SkeletonBlock(Modifier.width(72.dp).aspectRatio(COVER_RATIO), RoundedCornerShape(10.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(16.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(16.dp))
        }
    }
}

/** A book's page: the cover, then title, writer, price and the buy button. */
@Composable
fun BookDetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            SkeletonBlock(Modifier.width(220.dp).aspectRatio(COVER_RATIO), RoundedCornerShape(12.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.75f).height(28.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(18.dp))
            Spacer(Modifier.height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(30.dp))
            Spacer(Modifier.height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(28.dp))
        }
    }
}

private const val SHIMMER_MS = 1_200
private const val SHIMMER_WIDTH = 600f
private const val SHIMMER_TRAVEL = 2_000f
private const val SLOW_MS = 6_000L
private const val SHELF_CARDS = 3
private const val GRID_CARDS = 6

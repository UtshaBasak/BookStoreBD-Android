package io.github.utshabasak.bookstorebd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.ui.navigation.ChatRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrderRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SaleRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SalesRoute
import kotlin.reflect.KClass

/**
 * Lists whose items open beside them on wide screens (tablets, unfolded
 * foldables): conversations, orders and orders for a seller's books. On a
 * phone each opens on its own, as before.
 */
internal enum class AppPanes(
    val list: KClass<out NavKey>,
    val detail: KClass<out NavKey>,
    val placeholder: Int,
    val icon: ImageVector,
) {
    Messages(MessagesRoute::class, ChatRoute::class, R.string.pane_choose_conversation, Icons.Outlined.Forum),
    Orders(OrdersRoute::class, OrderRoute::class, R.string.pane_choose_order, Icons.AutoMirrored.Outlined.ReceiptLong),
    Sales(SalesRoute::class, SaleRoute::class, R.string.pane_choose_sale, Icons.Outlined.LocalShipping),
    ;

    companion object {
        fun of(route: NavKey): AppPanes? = entries.firstOrNull { it.list.isInstance(route) || it.detail.isInstance(route) }

        /** Whether [route] is an item of the same list as [shown], which it can replace beside the list. */
        fun sameDetail(
            shown: NavKey,
            route: NavKey,
        ): Boolean = entries.any { it.detail.isInstance(shown) && it.detail.isInstance(route) }

        fun isList(route: NavKey): Boolean = entries.any { it.list.isInstance(route) }
    }
}

/** [entry], marked as a list or the item beside it, for [ListDetailSceneStrategy]. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun withPane(entry: NavEntry<NavKey>, route: NavKey): NavEntry<NavKey> {
    val pane = AppPanes.of(route) ?: return entry
    val role =
        if (pane.list.isInstance(route)) {
            ListDetailSceneStrategy.listPane(sceneKey = pane.name, detailPlaceholder = { PanePlaceholder(pane) }) +
                ListDetailSceneStrategy.preferredPaneSize(width = LIST_WIDTH)
        } else {
            ListDetailSceneStrategy.detailPane(sceneKey = pane.name)
        }
    return NavEntry(route, contentKey = entry.contentKey, metadata = entry.metadata + role) { entry.Content() }
}

/** What the space beside a list shows before anything is chosen. */
@Composable
private fun PanePlaceholder(pane: AppPanes) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(pane.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(
                stringResource(pane.placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

private val LIST_WIDTH = 400.dp

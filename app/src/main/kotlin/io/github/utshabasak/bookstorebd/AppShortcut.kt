package io.github.utshabasak.bookstorebd

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.ui.navigation.CartRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute

/**
 * The shortcuts a long press on the app's icon shows. Published from the app
 * rather than declared in XML, so their labels follow the app's own language
 * and the debug build's shortcuts open the debug build.
 */
enum class AppShortcut(
    val id: String,
    @param:StringRes val label: Int,
    @param:StringRes val longLabel: Int,
    @param:DrawableRes val icon: Int,
    val route: NavKey,
) {
    Search(
        "search",
        R.string.shortcut_search,
        R.string.shortcut_search_long,
        R.drawable.ic_shortcut_search,
        CatalogueRoute(focusSearch = true),
    ),
    Scan("scan", R.string.shortcut_scan, R.string.shortcut_scan_long, R.drawable.ic_shortcut_scan, CatalogueRoute(scan = true)),
    Cart("cart", R.string.shortcut_cart, R.string.shortcut_cart_long, R.drawable.ic_shortcut_cart, CartRoute),
    Orders("orders", R.string.shortcut_orders, R.string.shortcut_orders_long, R.drawable.ic_shortcut_orders, OrdersRoute()),
    ;

    companion object {
        const val ACTION = "io.github.utshabasak.bookstorebd.action.SHORTCUT"
        const val EXTRA_ID = "shortcut"

        fun forId(id: String?): AppShortcut? = entries.firstOrNull { it.id == id }

        /** Publishes the shortcuts in the app's current language; scanning only where there is a camera. */
        fun publish(context: Context) {
            val camera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val shortcuts =
                entries.filter { it != Scan || camera }.mapIndexed { rank, shortcut ->
                    ShortcutInfoCompat
                        .Builder(context, shortcut.id)
                        .setShortLabel(context.getString(shortcut.label))
                        .setLongLabel(context.getString(shortcut.longLabel))
                        .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                        .setRank(rank)
                        .setIntent(
                            Intent(context, MainActivity::class.java)
                                .setAction(ACTION)
                                .putExtra(EXTRA_ID, shortcut.id),
                        ).build()
                }
            runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        }
    }
}

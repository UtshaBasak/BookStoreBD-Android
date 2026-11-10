package io.github.utshabasak.bookstorebd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.LocaleManagerCompat
import io.github.utshabasak.bookstorebd.AppShortcut
import io.github.utshabasak.bookstorebd.MainActivity
import io.github.utshabasak.bookstorebd.R

/**
 * The search widget: a search box that opens the app's search, with scanning
 * a barcode and the cart beside it. It shows nothing from the shop or from an
 * account, so it has nothing to fetch and looks the same signed in or out.
 */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val views = searchViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

internal fun searchViews(context: Context): RemoteViews {
    val words = context.inAppLanguage()
    val camera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    return RemoteViews(context.packageName, R.layout.widget_search).apply {
        setTextViewText(R.id.widget_search_box, words.getString(R.string.widget_search_hint))
        setOnClickPendingIntent(R.id.widget_search_box, shortcutIntent(context, AppShortcut.Search))
        // Scanning only where there is a camera, as with the home-screen shortcuts.
        setViewVisibility(R.id.widget_scan, if (camera) View.VISIBLE else View.GONE)
        setContentDescription(R.id.widget_scan, words.getString(R.string.shortcut_scan_long))
        setOnClickPendingIntent(R.id.widget_scan, shortcutIntent(context, AppShortcut.Scan))
        setContentDescription(R.id.widget_cart, words.getString(R.string.shortcut_cart_long))
        setOnClickPendingIntent(R.id.widget_cart, shortcutIntent(context, AppShortcut.Cart))
    }
}

/** Opens one of the app's shortcuts, the same way a long press on the icon does. */
internal fun shortcutIntent(
    context: Context,
    shortcut: AppShortcut,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        shortcut.id.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(AppShortcut.ACTION)
            .putExtra(AppShortcut.EXTRA_ID, shortcut.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * This context in the language chosen in the app. A widget is drawn by the
 * launcher, which knows only the phone's language, so its words are read
 * here and handed over as text.
 */
internal fun Context.inAppLanguage(): Context {
    val chosen = LocaleManagerCompat.getApplicationLocales(this)
    if (chosen.isEmpty) return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList.forLanguageTags(chosen.toLanguageTags()))
    return createConfigurationContext(configuration)
}

package io.github.utshabasak.bookstorebd.core.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a web page in a Custom Tab: the browser, styled for the app, with the
 * person's own cookies.
 *
 * The app opens the shop's links itself (App Links), so a page of the website
 * handed to the browser is sent to the browser by name; otherwise Android would
 * give it straight back to the app.
 */
fun openInBrowser(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
    val browser = CustomTabsClient.getPackageName(context, null)
    if (browser != null) {
        tab.intent.setPackage(browser)
    } else {
        // No browser with Custom Tabs: the phone's default browser, chosen by its category.
        tab.intent.selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    }
    runCatching { tab.launchUrl(context, url.toUri()) }
}

package io.github.utshabasak.bookstorebd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.MainActivity
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.core.ui.formatTaka
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The deals widget: up to three of the shop's discounted books, each opening
 * its page in the app. They are the Home screen's Quick deals, which anyone
 * can see, so the widget is the same signed in or out and holds nothing from
 * an account.
 *
 * It is drawn from what the app last saved, then again once the shop has
 * answered: when Android asks (four times a day) and after the app is used.
 */
@AndroidEntryPoint
class DealsWidget : AppWidgetProvider() {
    @Inject lateinit var catalogue: CatalogueRepository

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) = draw(context, manager, ids, fetch = true)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REDRAW) {
            val manager = AppWidgetManager.getInstance(context)
            draw(context, manager, manager.getAppWidgetIds(ComponentName(context, DealsWidget::class.java)), fetch = false)
        }
    }

    private fun draw(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        fetch: Boolean,
    ) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(WORK_MS) {
                    show(context, manager, ids)
                    if (fetch) {
                        catalogue.refreshHome()
                        show(context, manager, ids)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun show(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val deals =
            catalogue.homeSections
                .first()
                ?.deals
                .orEmpty()
        val views = dealsViews(context, deals)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private const val ACTION_REDRAW = "io.github.utshabasak.bookstorebd.action.REDRAW_DEALS"

        /** A broadcast has about ten seconds; the shop is given most of it. */
        private const val WORK_MS = 8_000L

        /** Draws the widgets again from what the app has saved, without asking the shop: after the app is used. */
        fun redraw(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DealsWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(Intent(context, DealsWidget::class.java).setAction(ACTION_REDRAW))
        }
    }
}

private val ROWS =
    listOf(
        DealRow(R.id.widget_deal_1, R.id.widget_deal_1_title, R.id.widget_deal_1_author, R.id.widget_deal_1_price),
        DealRow(R.id.widget_deal_2, R.id.widget_deal_2_title, R.id.widget_deal_2_author, R.id.widget_deal_2_price),
        DealRow(R.id.widget_deal_3, R.id.widget_deal_3_title, R.id.widget_deal_3_author, R.id.widget_deal_3_price),
    )

private data class DealRow(
    val row: Int,
    val title: Int,
    val author: Int,
    val price: Int,
)

/** The widget for [deals]: the first three that can be bought, or a line saying to open the app when there are none. */
internal fun dealsViews(
    context: Context,
    deals: List<Book>,
): RemoteViews {
    val words = context.inAppLanguage()
    val locale = words.resources.configuration.locales[0]
    val shown = deals.filter { it.inStock && it.hasDeal }.take(ROWS.size)
    return RemoteViews(context.packageName, R.layout.widget_deals).apply {
        setTextViewText(R.id.widget_deals_title, words.getString(R.string.widget_deals_title))
        setOnClickPendingIntent(R.id.widget_deals_title, siteIntent(context, "/filter?deals=1&sort=dealPercent"))
        ROWS.forEachIndexed { index, row ->
            val book = shown.getOrNull(index)
            setViewVisibility(row.row, if (book == null) View.GONE else View.VISIBLE)
            if (book != null) {
                val price = formatTaka(book.salePrice, locale)
                setTextViewText(row.title, book.title)
                setTextViewText(row.author, book.author)
                setTextViewText(row.price, price)
                setContentDescription(
                    row.row,
                    words.getString(
                        R.string.widget_deal_described,
                        book.title,
                        book.author,
                        price,
                        formatCount(book.discountPercent, locale),
                    ),
                )
                setOnClickPendingIntent(row.row, siteIntent(context, "/book/${book.id}"))
            }
        }
        setViewVisibility(R.id.widget_deals_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_deals_empty, words.getString(R.string.widget_deals_empty))
        setOnClickPendingIntent(R.id.widget_deals_empty, siteIntent(context, "/"))
    }
}

/** Opens a page of the shop in the app, by its path on the website, as a link to it does. */
internal fun siteIntent(
    context: Context,
    path: String,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        path.hashCode(),
        Intent(Intent.ACTION_VIEW, "https://${BuildConfig.LINK_HOST}$path".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

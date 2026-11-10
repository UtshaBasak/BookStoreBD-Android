package io.github.utshabasak.bookstorebd.widget

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.AppShortcut
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import io.github.utshabasak.bookstorebd.sitePath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun deal(
        id: String,
        title: String,
        stock: Int = 3,
        discount: Int = 20,
    ) = Book(
        id = id,
        title = title,
        author = "Humayun Ahmed",
        price = 300.0,
        salePrice = if (discount > 0) 240.0 else 300.0,
        discountPercent = discount,
        stock = stock,
    )

    private fun drawn(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.shown(id: Int): Boolean = findViewById<View>(id).visibility == View.VISIBLE

    @Test
    fun `the search widget opens search, scanning and the cart`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)

        val widget = drawn(searchViews(context))

        assertThat(widget.text(R.id.widget_search_box)).isEqualTo("Search books, writers and more")
        assertThat(widget.shown(R.id.widget_scan)).isTrue()
        assertThat(widget.findViewById<View>(R.id.widget_scan).contentDescription.toString()).isEqualTo("Scan a book")
        assertThat(widget.findViewById<View>(R.id.widget_cart).contentDescription.toString()).isEqualTo("Your cart")
        val opened = shadowOf(shortcutIntent(context, AppShortcut.Search)).savedIntent
        assertThat(opened.action).isEqualTo(AppShortcut.ACTION)
        assertThat(AppShortcut.forId(opened.getStringExtra(AppShortcut.EXTRA_ID))).isEqualTo(AppShortcut.Search)
    }

    @Test
    fun `a phone without a camera is not offered scanning`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)

        assertThat(drawn(searchViews(context)).shown(R.id.widget_scan)).isFalse()
    }

    @Test
    fun `the deals widget lists up to three discounted books that can be bought`() {
        val deals =
            listOf(
                deal("b1", "Himu"),
                deal("b2", "Sold out", stock = 0),
                deal("b3", "Full price", discount = 0),
                deal("b4", "Misir Ali"),
                deal("b5", "Debi"),
                deal("b6", "A fourth deal"),
            )

        val widget = drawn(dealsViews(context, deals))

        assertThat(widget.text(R.id.widget_deals_title)).isEqualTo("Deals at BookStoreBD")
        assertThat(widget.text(R.id.widget_deal_1_title)).isEqualTo("Himu")
        assertThat(widget.text(R.id.widget_deal_1_price)).isEqualTo("৳240")
        assertThat(widget.text(R.id.widget_deal_2_title)).isEqualTo("Misir Ali")
        assertThat(widget.text(R.id.widget_deal_3_title)).isEqualTo("Debi")
        assertThat(widget.findViewById<View>(R.id.widget_deal_1).contentDescription.toString())
            .isEqualTo("Himu by Humayun Ahmed, ৳240, 20% off")
        assertThat(widget.shown(R.id.widget_deals_empty)).isFalse()
    }

    @Test
    fun `with no deals saved yet the widget says to open the app`() {
        val widget = drawn(dealsViews(context, emptyList()))

        assertThat(widget.shown(R.id.widget_deal_1)).isFalse()
        assertThat(widget.shown(R.id.widget_deals_empty)).isTrue()
        assertThat(widget.text(R.id.widget_deals_empty)).isEqualTo("Open BookStoreBD to see today's deals")
    }

    @Test
    fun `a deal opens its book in the app`() {
        val opened: Intent = shadowOf(siteIntent(context, "/book/b1")).savedIntent

        assertThat(opened.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(opened.data!!.host).isEqualTo(BuildConfig.LINK_HOST)
        assertThat(routeForSiteLink(sitePath(opened.data!!))).isEqualTo(BookRoute("b1"))
    }
}

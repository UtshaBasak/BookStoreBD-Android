package io.github.utshabasak.bookstorebd

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppEntryPointsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `a link to the website keeps its path, search and fragment`() {
        val uri = Uri.parse("https://${BuildConfig.LINK_HOST}/filter?search=Aparajito%20Bibhutibhushan&deals=1#top")

        assertThat(sitePath(uri)).isEqualTo("/filter?search=Aparajito%20Bibhutibhushan&deals=1#top")
        assertThat(routeForSiteLink(sitePath(uri))).isEqualTo(CatalogueRoute(search = "Aparajito Bibhutibhushan", dealsOnly = true))
        assertThat(sitePath(Uri.parse("https://${BuildConfig.LINK_HOST}"))).isEqualTo("/")
    }

    @Test
    fun `the home-screen shortcuts include scanning only where there is a camera`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        AppShortcut.publish(context)
        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertThat(shortcuts.map { it.id }).containsExactly("search", "scan", "cart", "orders").inOrder()

        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        AppShortcut.publish(context)
        assertThat(ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }).doesNotContain("scan")
    }

    @Test
    fun `each shortcut opens its screen`() {
        assertThat(AppShortcut.forId("scan")?.route).isEqualTo(CatalogueRoute(scan = true))
        assertThat(AppShortcut.forId("search")?.route).isEqualTo(CatalogueRoute(focusSearch = true))
        assertThat(AppShortcut.forId("unknown")).isNull()
    }
}

package io.github.utshabasak.bookstorebd.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoresTest {
    @get:Rule val folder = TemporaryFolder()

    /** Stands in for the keystore: reversible, and visibly not the plain text. */
    private class FakeCipher : SecretCipher {
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()

        override fun decrypt(encoded: String) = encoded.removePrefix("sealed:").takeIf { it != encoded }?.reversed()
    }

    private fun TestScope.sessionStore(cipher: SecretCipher = FakeCipher()): Pair<SessionStore, File> {
        val file = File(folder.root, "session.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return SessionStore(store, cipher) to file
    }

    private fun TestScope.settingsStore() =
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(folder.root, "settings.preferences_pb")
            },
        )

    private val user = SessionUser(id = "u1", username = "nusrat", email = "nusrat@example.com", isAdmin = false)

    @Test
    fun `keeps the user and the refresh token, and never writes the token in the clear`() =
        runTest {
            val (store, file) = sessionStore()

            store.save(user, "refresh-token-value")

            assertThat(store.user.first()).isEqualTo(user)
            assertThat(store.refreshToken()).isEqualTo("refresh-token-value")
            assertThat(file.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("refresh-token-value")
        }

    @Test
    fun `forgets everything on sign-out`() =
        runTest {
            // DataStore cannot replace its file on Windows; this runs in CI (Linux) and on Android.
            assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
            val (store, _) = sessionStore()
            store.save(user, "refresh-token-value")

            store.clear()

            assertThat(store.user.first()).isNull()
            assertThat(store.refreshToken()).isNull()
        }

    @Test
    fun `treats a token it can no longer decrypt as no session`() =
        runTest {
            val unreadable =
                object : SecretCipher {
                    override fun encrypt(plain: String) = "sealed"

                    override fun decrypt(encoded: String): String? = null
                }
            val (store, _) = sessionStore(unreadable)
            store.save(user, "refresh-token-value")

            assertThat(store.refreshToken()).isNull()
        }

    @Test
    fun `remembers the theme, following the phone until one is chosen`() =
        runTest {
            val settings = settingsStore()
            assertThat(settings.theme.first()).isEqualTo(ThemePreference.System)

            settings.setTheme(ThemePreference.Dark)

            assertThat(settings.theme.first()).isEqualTo(ThemePreference.Dark)
        }

    @Test
    fun `makes one random installation id and keeps it`() =
        runTest {
            val settings = settingsStore()

            val first = settings.installId()
            val second = settings.installId()

            assertThat(first).matches("[A-Za-z0-9_-]{32}")
            assertThat(second).isEqualTo(first)
        }

    @Test
    fun `remembers a dismissed update`() =
        runTest {
            val settings = settingsStore()
            assertThat(settings.dismissedUpdate.first()).isEqualTo(0)
            settings.dismissUpdate(10300)
            assertThat(settings.dismissedUpdate.first()).isEqualTo(10300)
        }
}

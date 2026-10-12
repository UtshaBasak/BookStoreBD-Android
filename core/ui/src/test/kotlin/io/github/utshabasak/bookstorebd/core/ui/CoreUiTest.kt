package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class CoreUiTest {
    @get:Rule val compose = createComposeRule()

    private val book =
        Book(
            id = "b1",
            title = "Pather Panchali",
            author = "Bibhutibhushan Bandyopadhyay",
            type = BookType.Old,
            stock = 2,
            price = 500.0,
            salePrice = 400.0,
            discountPercent = 20,
            ratingAverage = 4.5,
            ratingCount = 12,
        )

    @Test
    fun `writes taka as the shop does, in English and in Bangla`() {
        assertThat(formatTaka(1200.0, Locale.ENGLISH)).isEqualTo("৳1,200")
        assertThat(formatTaka(150000.0, Locale.ENGLISH)).isEqualTo("৳1,50,000")
        assertThat(formatTaka(99.5, Locale.ENGLISH)).isEqualTo("৳99.50")
        assertThat(formatTaka(1200.0, Locale.forLanguageTag("bn"))).isEqualTo("৳১,২০০")
        assertThat(formatCount(12, Locale.forLanguageTag("bn"))).isEqualTo("১২")
    }

    @Test
    fun `a card's counts stay short, as the website shortens them`() {
        assertThat(formatCompact(950, Locale.ENGLISH)).isEqualTo("950")
        assertThat(formatCompact(1234, Locale.ENGLISH)).isEqualTo("1.2K")
    }

    @Test
    fun `a card reads its price as one sentence and offers the wishlist as a toggle`() {
        var toggled = false
        compose.setContent {
            BookStoreTheme {
                BookCard(book = book, onClick = {}, inWishlist = false, onToggleWishlist = { toggled = true })
            }
        }

        compose.onNodeWithContentDescription("Price ৳400, reduced from ৳500, 20 percent off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rated 4.5 out of 5 by 12 buyers").assertIsDisplayed()
        compose.onNodeWithText("Second-hand").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Pather Panchali to your wishlist").performClick()
        assertThat(toggled).isTrue()
    }

    @Test
    fun `a sold-out book says so on its cover`() {
        compose.setContent { BookStoreTheme { BookCard(book = book.copy(stock = 0), onClick = {}) } }
        compose.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    fun `an error explains itself and offers to try again`() {
        var retried = false
        compose.setContent { BookStoreTheme { ErrorState(AppError.Timeout, onRetry = { retried = true }) } }

        compose.onNodeWithText("The shop is waking up").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `a refusal shows the server's own reason`() {
        compose.setContent {
            BookStoreTheme {
                ErrorState(AppError.Rejected(400, "Validation failed", fieldErrors = mapOf("quantity" to "Only 2 left")), onRetry = null)
            }
        }
        compose.onNodeWithText("Only 2 left").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a physical keyboard's Enter key runs the field's action once`() {
        var submitted = 0
        compose.setContent {
            BookStoreTheme {
                var text by remember { mutableStateOf("") }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.testTag("field").onHardwareEnter { submitted++ },
                )
            }
        }

        compose.onNodeWithTag("field").performClick()
        compose.onNodeWithTag("field").performTextInput("putul")
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.Enter) }

        assertThat(submitted).isEqualTo(1)
        compose.onNodeWithTag("field").assertTextEquals("putul")
    }
}

package io.github.utshabasak.bookstorebd.feature.reviews

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Review
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.model.ReviewDraft
import io.github.utshabasak.bookstorebd.core.model.ReviewPage
import io.github.utshabasak.bookstorebd.core.model.ReviewSummary
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.WriteReviewRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewsTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val summary = ReviewSummary(0.0, 0, List(5) { 0 }, emptyList())

    @Test
    fun `editing fills in the person's review, and a rating is needed before posting`() =
        runTest {
            val mine = Review("r1", "nusrat", 4, "Lovely", "Read it twice", null, null)
            val community =
                mockk<CommunityRepository>(relaxed = true) {
                    coEvery { reviews(ReviewTarget.OfBook("b1")) } returns Outcome.Success(ReviewPage(summary, mine, true, null, false))
                }
            val vm = WriteReviewViewModel(WriteReviewRoute("book", "b1", "Pather Panchali"), community)

            assertThat(vm.uiState.value.editing).isTrue()
            assertThat(vm.uiState.value.rating).isEqualTo(4)

            vm.setRating(0)
            vm.submit()
            assertThat(vm.uiState.value.needsRating).isTrue()

            vm.setRating(5)
            vm.submit()
            coVerify { community.writeReview(ReviewTarget.OfBook("b1"), ReviewDraft(5, "Lovely", "Read it twice")) }
        }

    @Test
    fun `explains why someone cannot rate a seller they have not bought from`() {
        compose.setContent {
            BookStoreTheme {
                WriteReviewScreen(
                    name = "nusrat",
                    ofSeller = true,
                    state = WriteReviewUiState(loaded = true, canReview = false, block = ReviewBlock.NotPurchased),
                    actions = WriteReviewActions({}, {}, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Once an order from this seller has been delivered to you, you can rate them here.").assertExists()
    }

    @Test
    fun `an order still on its way says to wait for delivery`() {
        compose.setContent {
            BookStoreTheme {
                WriteReviewScreen(
                    name = "Pather Panchali",
                    ofSeller = false,
                    state = WriteReviewUiState(loaded = true, canReview = false, block = ReviewBlock.NotDelivered),
                    actions = WriteReviewActions({}, {}, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("You can review this book once it has been delivered.").assertExists()
    }

    @Test
    fun `the stars are a choice TalkBack can read`() {
        var chosen = 0
        compose.setContent {
            BookStoreTheme {
                WriteReviewScreen(
                    name = "Pather Panchali",
                    ofSeller = false,
                    state = WriteReviewUiState(loaded = true, canReview = true),
                    actions = WriteReviewActions({}, { chosen = it }, {}, {}, {}, {}, {}),
                )
            }
        }

        compose.onNodeWithContentDescription("3 stars").performClick()
        assertThat(chosen).isEqualTo(3)
    }
}

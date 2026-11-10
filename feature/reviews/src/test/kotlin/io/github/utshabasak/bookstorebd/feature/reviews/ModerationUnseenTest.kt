package io.github.utshabasak.bookstorebd.feature.reviews

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.model.Outcome
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

/**
 * The shop checks reviews automatically after they are posted, on the server,
 * and may flag one for an administrator. None of that reaches the writer: a
 * review the server accepts is simply posted, as it always was.
 */
@RunWith(RobolectricTestRunner::class)
class ModerationUnseenTest {
    @get:Rule val main = MainDispatcherRule()

    private val summary = ReviewSummary(0.0, 0, List(5) { 0 }, emptyList())

    private fun community(target: ReviewTarget) =
        mockk<CommunityRepository>(relaxed = true) {
            coEvery { reviews(target) } returns Outcome.Success(ReviewPage(summary, null, true, null, false))
            coEvery { writeReview(target, any()) } returns Outcome.Success(Unit)
        }

    @Test
    fun `a review the server accepts is posted, with nothing said about checks`() =
        runTest {
            val target = ReviewTarget.OfBook("b1")
            val community = community(target)
            val model = WriteReviewViewModel(WriteReviewRoute("book", "b1", "Pather Panchali"), community)

            model.eventFlow.test {
                model.setRating(5)
                model.submit()

                assertThat(awaitItem()).isEqualTo(WriteReviewEvent.Done)
                expectNoEvents()
            }
            assertThat(model.uiState.value.busy).isFalse()
            coVerify(exactly = 1) { community.writeReview(target, ReviewDraft(5, "", "")) }
        }

    @Test
    fun `a seller rating the server accepts is posted the same way`() =
        runTest {
            val target = ReviewTarget.OfSeller("rahim_books")
            val community = community(target)
            val model = WriteReviewViewModel(WriteReviewRoute("seller", "rahim_books", "rahim_books"), community)

            model.eventFlow.test {
                model.setRating(4)
                model.submit()

                assertThat(awaitItem()).isEqualTo(WriteReviewEvent.Done)
                expectNoEvents()
            }
        }
}

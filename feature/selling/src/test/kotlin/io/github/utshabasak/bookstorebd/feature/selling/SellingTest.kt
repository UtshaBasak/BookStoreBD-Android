package io.github.utshabasak.bookstorebd.feature.selling

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SellerRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.DiscountKind
import io.github.utshabasak.bookstorebd.core.model.DiscountProblem
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Listing
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderDelivery
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.Published
import io.github.utshabasak.bookstorebd.core.model.ReturnStatus
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SellingTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val sellers = mockk<SellerRepository>(relaxed = true)
    private val profiles = mockk<ProfileRepository>(relaxed = true) { every { profile } returns flowOf(null) }
    private val config = mockk<ConfigRepository> { every { config } returns flowOf(null) }
    private val images =
        mockk<ImagePreparer> {
            coEvery {
                prepare(
                    any(),
                    any(),
                )
            } returns ImageUpload(byteArrayOf(1), "image/jpeg", "p.jpg")
        }

    private val session = mockk<UserSession> { every { user } returns flowOf(SessionUser("u1", "boipoka", "seller@example.com", false)) }

    private fun form(route: ListBookRoute = ListBookRoute()) = ListBookViewModel(route, sellers, profiles, images, config, session)

    @Test
    fun `the form opens filled in from a Wanted request, and says what it still needs`() =
        runTest {
            val vm = form(ListBookRoute(title = "Aparajito", author = "Bibhutibhushan"))

            vm.publish()

            assertThat(vm.uiState.value.draft.title).isEqualTo("Aparajito")
            assertThat(vm.uiState.value.problems).containsExactly(ListingProblem.Photos, ListingProblem.Price, ListingProblem.Categories)
            coVerify(exactly = 0) { sellers.publish(any(), any()) }
        }

    @Test
    fun `Others stands alone among the categories`() =
        runTest {
            val vm = form()

            vm.toggleCategory("Novels")
            vm.toggleCategory("Classics")
            vm.toggleCategory("Others")
            assertThat(vm.uiState.value.draft.categories).containsExactly("Others")

            vm.toggleCategory("Poetry")
            assertThat(vm.uiState.value.draft.categories).containsExactly("Poetry")
        }

    @Test
    fun `a discount that does not fit the price is not sent`() =
        runTest {
            val vm = form()
            vm.addPhotos(listOf(Uri.parse("content://photos/1")))
            vm.edit { it.copy(title = "A", author = "B", price = "100", discount = Discount(DiscountKind.Amount, 100)) }
            vm.toggleCategory("Novels")

            vm.publish()

            assertThat(vm.uiState.value.discountProblem).isNotNull()
            coVerify(exactly = 0) { sellers.publish(any(), any()) }
        }

    @Test
    fun `a complete listing is published with its photos`() =
        runTest {
            coEvery { sellers.publish(any(), any()) } returns Outcome.Success(Published("Book added successfully!", "b1", 0))
            val vm = form()
            vm.addPhotos(listOf(Uri.parse("content://photos/1"), Uri.parse("content://photos/2")))
            vm.edit { it.copy(title = "Aparajito", author = "Bibhutibhushan", price = "300", type = BookType.Old, condition = "good") }
            vm.toggleCategory("Novels")

            vm.eventFlow.test {
                vm.publish()
                assertThat(awaitItem()).isInstanceOf(ListBookEvent.Listed::class.java)
            }
            coVerify { sellers.publish(match { it.title == "Aparajito" && it.condition == "good" }, match { it.size == 2 }) }
        }

    @Test
    fun `saving reports a refused change instead of calling it saved`() =
        runTest {
            val listing = Listing(Book(id = "b1", title = "Aparajito", author = "B", price = 300.0, stock = 1), Discount(), null, 0)
            coEvery { sellers.listings() } returns Outcome.Success(listOf(listing))
            coEvery { sellers.setPrice("b1", 250) } returns Outcome.Success("Price updated")
            coEvery { sellers.setStock("b1", -1) } returns Outcome.Failure(AppError.Rejected(400, "Stock must be 0 or more"))
            val vm = MyBooksViewModel(sellers, session)

            vm.eventFlow.test {
                vm.save(listing, ListingChange(price = 250, stock = -1))
                assertThat(awaitItem()).isEqualTo(MyBooksEvent.NotSaved(AppError.Rejected(400, "Stock must be 0 or more")))
            }
        }

    @Test
    fun `the listing form asks for a bKash number first when there is none`() {
        var asked = false
        compose.setContent {
            BookStoreTheme {
                ListBookScreen(
                    state = ListBookUiState(canSell = false),
                    actions = ListBookActions({}, {}, {}, {}, {}, {}, { asked = true }, {}),
                )
            }
        }

        compose.onNodeWithText("Add your bKash number").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun `the seller's slip shows only the area, the fee and what they receive`() {
        val line =
            OrderLine(
                id = "l1",
                orderNumber = "ORDER00000000001",
                status = OrderStatus.Confirmed,
                bookId = "b1",
                title = "Aparajito",
                author = "Bibhutibhushan",
                condition = null,
                type = BookType.New,
                price = 1000.0,
                listPrice = 1000.0,
                quantity = 1,
                sellerEmail = "seller@example.com",
                createdAt = null,
                deliveredAt = null,
                cancelledAt = null,
                cancelledBy = null,
                cancelReason = "",
                returnStatus = null,
                returnableUntil = null,
            )
        val order =
            Order(
                orderNumber = "ORDER00000000001",
                status = OrderStatus.Confirmed,
                placedAt = "2026-10-11T10:00:00Z",
                lines = listOf(line),
                booksTotal = 1000.0,
                shipping = 70.0,
                discount = 0.0,
                total = 1070.0,
                promo = null,
                delivery = OrderDelivery("Nusrat", "01712345678", "Dhaka", "Dhaka", "12 Road", "", "Cash on Delivery"),
                buyerEmail = "nusrat@example.com",
            )

        val slip = buildSellerSlip(order, RuntimeEnvironment.getApplication().resources, Locale.UK, 7, "11 Oct 2026")

        assertThat(slip.deliveryFacts.map { it.second }).containsExactly("Nusrat", "nusrat@example.com", "Dhaka, Dhaka").inOrder()
        assertThat(slip.totals.last()).isEqualTo("You receive" to "৳950")
        assertThat(slip.footer.first()).contains("7-day")

        // A book that came back is not paid for: the slip says so and leaves it out of what they receive.
        val back = line.copy(id = "l2", title = "Pather Panchali", status = OrderStatus.Delivered, returnStatus = ReturnStatus.Approved)
        val returned =
            buildSellerSlip(order.copy(lines = listOf(line, back)), RuntimeEnvironment.getApplication().resources, Locale.UK, 7, "")
        assertThat(returned.lines[1].detail).isEqualTo("Bibhutibhushan · returned")
        assertThat(returned.totals.first()).isEqualTo("Your books (not returned)" to "৳1,000")
        assertThat(returned.totals.last()).isEqualTo("You receive" to "৳950")
    }

    @Test
    fun `a second-hand copy must say its condition, and a new book is not asked for one`() =
        runTest {
            val vm = form()
            vm.addPhotos(listOf(Uri.parse("content://photos/1")))
            vm.edit { it.copy(title = "Aparajito", author = "Bibhutibhushan", price = "300", type = BookType.Old) }
            vm.toggleCategory("Novels")

            vm.publish()
            assertThat(vm.uiState.value.problems).containsExactly(ListingProblem.Condition)

            vm.edit { it.copy(type = BookType.New) }
            assertThat(vm.uiState.value.draft.condition).isEmpty()
            vm.publish()
            coVerify { sellers.publish(match { it.type == BookType.New }, any()) }
        }

    @Test
    fun `a percentage that saves less than 1 Tk is not offered as a discount`() =
        runTest {
            val vm = form()
            vm.addPhotos(listOf(Uri.parse("content://photos/1")))
            vm.edit { it.copy(title = "A", author = "B", price = "40", discount = Discount(DiscountKind.Percent, 1)) }
            vm.toggleCategory("Novels")

            vm.publish()

            assertThat(vm.uiState.value.discountProblem).isEqualTo(DiscountProblem.TooSmall)
            coVerify(exactly = 0) { sellers.publish(any(), any()) }
        }
}

package io.github.utshabasak.bookstorebd.feature.orders

import android.net.Uri
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.image.ImagePreparer
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Order
import io.github.utshabasak.bookstorebd.core.model.OrderDelivery
import io.github.utshabasak.bookstorebd.core.model.OrderLine
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.ReturnDraft
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.github.utshabasak.bookstorebd.core.ui.buildReceipt
import io.github.utshabasak.bookstorebd.core.ui.navigation.ReturnRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OrdersTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private fun line(
        id: String,
        status: OrderStatus,
        returnableUntil: String? = null,
    ) = OrderLine(
        id = id,
        orderNumber = "ORDER00000000001",
        status = status,
        bookId = "b-$id",
        title = "Book $id",
        author = "Author",
        condition = null,
        type = BookType.New,
        price = 300.0,
        listPrice = 300.0,
        quantity = 1,
        sellerEmail = "s@example.com",
        createdAt = "2026-10-01T10:00:00Z",
        deliveredAt = null,
        cancelledAt = null,
        cancelledBy = null,
        cancelReason = "",
        returnStatus = null,
        returnableUntil = returnableUntil,
    )

    private fun order(
        status: OrderStatus,
        lines: List<OrderLine>,
        canCancel: Boolean = false,
    ) = Order(
        orderNumber = "ORDER00000000001",
        status = status,
        placedAt = "2026-10-01T10:00:00Z",
        lines = lines,
        booksTotal = 300.0,
        shipping = 70.0,
        discount = 0.0,
        total = 370.0,
        promo = null,
        delivery = OrderDelivery("Nusrat", "01712345678", "Dhaka", "Dhaka", "12 Road", "", "Cash on Delivery"),
        canCancel = canCancel,
    )

    @Test
    fun `cancelling reports the server's answer`() =
        runTest {
            val orders =
                mockk<OrderRepository>(relaxed = true) {
                    every { order("ORDER00000000001") } returns MutableStateFlow(null)
                    coEvery { cancel("ORDER00000000001", "Changed my mind") } returns Outcome.Success("Order cancelled")
                }
            val vm = OrderViewModel("ORDER00000000001", orders)

            vm.eventFlow.test {
                vm.cancel("Changed my mind")
                assertThat(awaitItem()).isEqualTo(OrderEvent.Cancelled("Order cancelled"))
            }
            coVerify { orders.refreshOrder("ORDER00000000001") }
        }

    @Test
    fun `a return needs a description, a bKash number and a photo, and sends them`() =
        runTest {
            val draft = slot<ReturnDraft>()
            val orders =
                mockk<OrderRepository>(relaxed = true) {
                    coEvery { requestReturn(capture(draft)) } returns
                        Outcome.Success("Return requested")
                }
            val images =
                mockk<ImagePreparer> { coEvery { prepare(any(), any()) } returns ImageUpload(byteArrayOf(1), "image/jpeg", "p.jpg") }
            val vm = ReturnViewModel(ReturnRoute("ORDER00000000001", listOf("l1", "l2"), listOf("A", "B")), orders, images)

            vm.send()
            assertThat(vm.uiState.value.problems).containsExactly(ReturnProblem.Description, ReturnProblem.Bkash, ReturnProblem.Photos)

            vm.setDescription("Water damage on the cover")
            vm.setBkash("+880 1712-345678")
            vm.addPhotos(listOf(Uri.parse("content://photos/1")))
            vm.eventFlow.test {
                vm.send()
                assertThat(awaitItem()).isEqualTo(ReturnEvent.Sent("Return requested"))
            }
            assertThat(draft.captured.lineIds).containsExactly("l1", "l2")
            assertThat(draft.captured.refundBkash).isEqualTo("01712345678")
            assertThat(draft.captured.photos).hasSize(1)
        }

    @Test
    fun `the tracking page shows the steps, and offers cancelling and returning only when allowed`() {
        val shipped = order(OrderStatus.Confirmed, listOf(line("a", OrderStatus.Confirmed)), canCancel = true)
        compose.setContent {
            BookStoreTheme {
                OrderScreen("ORDER00000000001", OrderUiState(order = shipped), OrderActions({}, {}, {}, {}, { _, _ -> }, {}, {}))
            }
        }

        compose.onNodeWithContentDescription("Status: Order confirmed").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cancel this order"))
        compose.onNodeWithText("Cancel this order").assertExists()
        compose.onNodeWithText("Return a book").assertDoesNotExist()
    }

    @Test
    fun `a delivered order inside the return window offers a return`() {
        val delivered = order(OrderStatus.Delivered, listOf(line("a", OrderStatus.Delivered, returnableUntil = "2026-10-20T00:00:00Z")))
        compose.setContent {
            BookStoreTheme {
                OrderScreen("ORDER00000000001", OrderUiState(order = delivered), OrderActions({}, {}, {}, {}, { _, _ -> }, {}, {}))
            }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Return a book"))
        compose.onNodeWithText("Return a book").assertExists()
        compose.onNodeWithText("Cancel this order").assertDoesNotExist()
    }

    @Test
    fun `the receipt lists every book, strikes out cancelled ones, and says what is paid`() {
        val cancelled = line("b", OrderStatus.Cancelled)
        val placed = order(OrderStatus.Confirmed, listOf(line("a", OrderStatus.Confirmed), cancelled))
        val resources = RuntimeEnvironment.getApplication().resources

        val receipt = buildReceipt(placed, resources, Locale.UK, "11 Oct 2026", resources.getString(statusRes(placed.status)))

        assertThat(receipt.orderNumber).isEqualTo("#ORDER00000000001")
        assertThat(receipt.lines.map { it.cancelled }).containsExactly(false, true).inOrder()
        assertThat(receipt.lines[1].detail).isEqualTo("Author · cancelled")
        assertThat(receipt.totals.last()).isEqualTo("To pay on delivery" to "৳370")
        assertThat(receipt.deliveryFacts).contains("Address" to "12 Road, Dhaka, Dhaka")
        assertThat(receipt.footer.last()).endsWith("Downloaded 11 Oct 2026")
    }
}

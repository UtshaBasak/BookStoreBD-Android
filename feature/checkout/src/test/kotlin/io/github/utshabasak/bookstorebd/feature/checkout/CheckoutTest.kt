package io.github.utshabasak.bookstorebd.feature.checkout

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CheckoutRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.model.AppConfig
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.ContactDetails
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Division
import io.github.utshabasak.bookstorebd.core.model.OrderRequest
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PlacedOrder
import io.github.utshabasak.bookstorebd.core.model.Promo
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CheckoutTest {
    @get:Rule val main = MainDispatcherRule()

    private val book = Book(id = "b1", title = "দেয়াল", author = "হুমায়ূন আহমেদ", price = 450.0, salePrice = 400.0, stock = 5)
    private val config =
        AppConfig(
            minVersionCode = 0,
            latestRelease = null,
            captchaSiteKey = null,
            challengeUrl = null,
            googleClientId = null,
            delivery = DeliveryRules(insideDhaka = 70, outsideDhaka = 120, daysInsideDhaka = 3, daysOutsideDhaka = 5),
            returnWindowDays = 7,
            categoryGroups = emptyList(),
            divisions = listOf(Division("Dhaka", listOf("Dhaka", "Gazipur")), Division("Sylhet", listOf("Sylhet"))),
            links = SiteLinks("", "", "", "", ""),
        )
    private val cart = mockk<CartRepository> { every { cart } returns flowOf(listOf(CartItem(book, 2))) }
    private val configs = mockk<ConfigRepository> { every { config } returns flowOf(this@CheckoutTest.config) }
    private val checkout =
        mockk<CheckoutRepository> {
            coEvery { contactDetails() } returns
                Outcome.Success(ContactDetails("Nusrat Jahan", "01712345678", "Road 5"))
        }

    @Test
    fun `fills the form from the profile and prices delivery by district, as the server does`() =
        runTest {
            val vm = CheckoutViewModel(cart, configs, checkout)
            vm.uiState.test {
                vm.update { it.copy(division = "Dhaka", district = "Gazipur") }
                val outside = expectMostRecentItem()
                assertThat(outside.form.name).isEqualTo("Nusrat Jahan")
                assertThat(outside.deliveryCharge).isEqualTo(120.0)
                assertThat(outside.total).isEqualTo(920.0)

                vm.update { it.copy(district = "Dhaka") }
                assertThat(expectMostRecentItem().deliveryCharge).isEqualTo(70.0)
            }
        }

    @Test
    fun `a new division clears a district from the old one`() =
        runTest {
            val vm = CheckoutViewModel(cart, configs, checkout)
            vm.uiState.test {
                vm.update { it.copy(division = "Dhaka", district = "Gazipur") }
                vm.update { it.copy(division = "Sylhet") }
                val state = expectMostRecentItem()
                assertThat(state.form.district).isEmpty()
                assertThat(state.districts).containsExactly("Sylhet")
            }
        }

    @Test
    fun `will not place an order with the form incomplete, and says what is missing`() =
        runTest {
            coEvery { checkout.contactDetails() } returns Outcome.Success(ContactDetails())
            val vm = CheckoutViewModel(cart, configs, checkout)
            vm.uiState.test {
                vm.update { it.copy(phone = "12345") }
                vm.placeOrder()
                assertThat(expectMostRecentItem().problems).containsExactly(
                    FormProblem.Name,
                    FormProblem.Phone,
                    FormProblem.Division,
                    FormProblem.District,
                    FormProblem.Address,
                )
            }
            coVerify(exactly = 0) { checkout.placeOrder(any()) }
        }

    @Test
    fun `places the order with the number written the standard way and reports what was charged`() =
        runTest {
            val sent = slot<OrderRequest>()
            coEvery { checkout.checkPromo("BOI10", 800.0) } returns Outcome.Success(Promo("BOI10", "10% off", 80.0, false))
            coEvery { checkout.placeOrder(capture(sent)) } returns
                Outcome.Success(
                    PlacedOrder("ABC123", shippingCharge = 120.0, discount = 80.0, promoMessage = null, unavailable = emptyList()),
                )
            val vm = CheckoutViewModel(cart, configs, checkout)

            vm.eventFlow.test {
                vm.uiState.test {
                    vm.update { it.copy(phone = "+880 1712-345678", division = "Sylhet", district = "Sylhet", promoCode = "BOI10") }
                    vm.applyPromo()
                    cancelAndIgnoreRemainingEvents()
                }
                vm.placeOrder()
                assertThat(awaitItem()).isEqualTo(
                    CheckoutEvent.Placed(PlacedOrder("ABC123", 120.0, 80.0, null, emptyList()), total = 840.0),
                )
            }
            assertThat(sent.captured.contactPhone).isEqualTo("01712345678")
            assertThat(sent.captured.items).containsExactly("b1" to 2)
            assertThat(sent.captured.promo).isEqualTo("BOI10")
        }

    @Test
    fun `knows a Bangladeshi mobile number however it is written`() {
        assertThat(isBangladeshMobile("01712345678")).isTrue()
        assertThat(isBangladeshMobile("+8801712345678")).isTrue()
        assertThat(isBangladeshMobile("017 1234 5678")).isTrue()
        assertThat(isBangladeshMobile("01212345678")).isFalse()
        assertThat(isBangladeshMobile("0171234567")).isFalse()
    }
}

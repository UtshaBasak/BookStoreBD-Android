package io.github.utshabasak.bookstorebd.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShopRulesTest {
    @Test
    fun `numbers typed on a Bangla keyboard are read as the shop reads them`() {
        assertThat("০১৭১২-৩৪৫৬৭৮".digitsOnly()).isEqualTo("01712345678")
        assertThat("+৮৮০ 1712".asciiDigits()).isEqualTo("+880 1712")
        assertThat("৳ 450".digitsOnly()).isEqualTo("450")
    }

    @Test
    fun `a book nearly gone is news about deals, as on the website`() {
        assertThat(NotificationCategory.ofType("selling-out")).isEqualTo(NotificationCategory.Deals)
        assertThat(NotificationCategory.ofType("stock")).isEqualTo(NotificationCategory.Stock)
    }

    @Test
    fun `an order stands where its least advanced live book does`() {
        fun line(status: OrderStatus) =
            OrderLine(
                id = status.name,
                orderNumber = "BS-1",
                status = status,
                bookId = "b",
                title = "t",
                author = "a",
                condition = null,
                type = BookType.New,
                price = 1.0,
                listPrice = 1.0,
                quantity = 1,
                sellerEmail = "s@example.com",
                createdAt = null,
                deliveredAt = null,
                cancelledAt = null,
                cancelledBy = null,
                cancelReason = "",
                returnStatus = null,
                returnableUntil = null,
            )
        assertThat(orderStatusOf(listOf(line(OrderStatus.Shipped), line(OrderStatus.Processing)))).isEqualTo(OrderStatus.Processing)
        assertThat(orderStatusOf(listOf(line(OrderStatus.Cancelled), line(OrderStatus.Delivered)))).isEqualTo(OrderStatus.Delivered)
        assertThat(orderStatusOf(listOf(line(OrderStatus.Cancelled)))).isEqualTo(OrderStatus.Cancelled)
    }

    @Test
    fun `a change is confirmed with the password, or a 6-digit code when the account has none`() {
        assertThat(IdentityCheck(password = "secret").given(passwordSet = true)).isTrue()
        assertThat(IdentityCheck(code = "123456").given(passwordSet = true)).isFalse()
        assertThat(IdentityCheck(code = " 123456 ").given(passwordSet = false)).isTrue()
        assertThat(IdentityCheck(code = "12345").given(passwordSet = false)).isFalse()
    }

    @Test
    fun `the set-up steps no longer ask for a date of birth`() {
        val profile =
            OwnProfile(
                username = "n",
                email = "n@example.com",
                pictureUrl = null,
                buyerBannerUrl = null,
                sellerBannerUrl = null,
                sellerRating = Rating(),
                address = "",
                phone = "",
                bkashMerchant = "",
                dateOfBirth = "",
                gender = null,
                twoFactor = false,
                passwordSet = true,
            )
        assertThat(profile.setupSteps(ProfileMode.Buyer).map { it.item })
            .containsExactly(SetupItem.Photo, SetupItem.Phone, SetupItem.Address, SetupItem.Banner, SetupItem.TwoStep)
            .inOrder()
    }
}

package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CheckoutRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Division
import io.github.utshabasak.bookstorebd.core.model.OrderRequest
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PlacedOrder
import io.github.utshabasak.bookstorebd.core.model.Promo
import io.github.utshabasak.bookstorebd.core.model.Promotions
import io.github.utshabasak.bookstorebd.core.model.UnavailableItem
import io.github.utshabasak.bookstorebd.core.model.UnavailableReason
import io.github.utshabasak.bookstorebd.core.model.asciiDigits
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The contact and delivery form, as typed. */
data class DeliveryForm(
    val name: String = "",
    /** Where this order's e-mails go: the account's address unless changed here. */
    val email: String = "",
    val phone: String = "",
    val division: String = "",
    val district: String = "",
    val address: String = "",
    val note: String = "",
    val promoCode: String = "",
)

/** Which fields need attention before the order can go in. */
enum class FormProblem { Name, Email, Phone, Division, District, Address }

data class CheckoutUiState(
    /** The whole cart; a sold-out book stays in it but is left out of the order. */
    val cart: List<CartItem> = emptyList(),
    val form: DeliveryForm = DeliveryForm(),
    val divisions: List<Division> = emptyList(),
    val delivery: DeliveryRules? = null,
    val returnWindowDays: Int = 0,
    val promotions: Promotions = Promotions(),
    val promo: Promo? = null,
    /** The books total the code was checked against: a code belongs to the basket it was applied to. */
    val promoFor: Double? = null,
    val promoError: AppError? = null,
    val checkingPromo: Boolean = false,
    val problems: Set<FormProblem> = emptySet(),
    /** A book whose quantity is being changed, so its controls wait. */
    val busyBookId: String? = null,
    val placing: Boolean = false,
    val error: AppError? = null,
    /** Why the order could not be placed, beside the button. */
    val blocked: CheckoutBlock? = null,
    /** Books the shop could not order at all, when it refused the whole order: named beside the button. */
    val refused: List<UnavailableItem> = emptyList(),
) {
    /** What is ordered: the books in stock. */
    val items: List<CartItem> get() = cart.filter { it.book.inStock }
    val soldOut: List<CartItem> get() = cart.filterNot { it.book.inStock }
    val booksTotal: Double get() = items.sumOf { it.lineTotal }

    /**
     * The code counts only for the basket it was checked against: once a
     * quantity changes it may no longer match what the server will charge.
     */
    val promoCurrent: Boolean get() = promo != null && promoFor == booksTotal
    val promoStale: Boolean get() = promo != null && !promoCurrent

    /** A free-delivery code counts for this basket. */
    val freeDelivery: Boolean get() = promoCurrent && promo?.freeDelivery == true

    /** A preview: the server prices delivery from the district, and charges what it works out. */
    val deliveryCharge: Double
        get() = if (freeDelivery || form.district.isBlank()) 0.0 else delivery?.chargeFor(form.district)?.toDouble() ?: 0.0

    val discount: Double get() = if (promoCurrent) (promo?.discount ?: 0.0).coerceAtMost(booksTotal) else 0.0
    val total: Double get() = booksTotal - discount + deliveryCharge

    /** Until a district is chosen the delivery charge is not known, so the total says it is before delivery. */
    val deliveryKnown: Boolean get() = freeDelivery || form.district.isNotBlank()
    val districts: List<String> get() = divisions.firstOrNull { it.name == form.division }?.districts.orEmpty()

    /** Books worth enough for free delivery, and that code not in use: checkout says so, as the website does. */
    val couldHaveFreeDelivery: Boolean
        get() = promotions.freeDelivery?.let { booksTotal >= it.minBooksTotal } == true && !freeDelivery
}

/** Why "Place order" did not go ahead. */
enum class CheckoutBlock { PromoStale, NothingInStock }

sealed interface CheckoutEvent {
    data class Placed(
        val order: PlacedOrder,
        /** What the order comes to as placed: books left out or short are not in it. */
        val total: Double,
        /** The code the shopper asked for, named when the shop did not apply it. */
        val promoCode: String?,
    ) : CheckoutEvent
}

/**
 * Checkout, cash on delivery: the books being bought, changed in place, who
 * and where they go to, an optional promo code, and placing the order.
 */
@HiltViewModel
class CheckoutViewModel
    @Inject
    constructor(
        private val cart: CartRepository,
        config: ConfigRepository,
        private val checkout: CheckoutRepository,
        private val session: UserSession,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val local = MutableStateFlow(CheckoutUiState())

        /**
         * Made once per visit to checkout, kept across process death, and sent
         * with every try: a retry after a lost answer gets the same order back
         * rather than placing a second one.
         */
        private val checkoutId: String =
            savedState.get<String>(CHECKOUT_ID) ?: UUID.randomUUID().toString().also { savedState[CHECKOUT_ID] = it }
        private val events = Channel<CheckoutEvent>(Channel.BUFFERED)
        val eventFlow: Flow<CheckoutEvent> = events.receiveAsFlow()

        val uiState: StateFlow<CheckoutUiState> =
            combine(local, cart.cart, config.config) { state, items, settings ->
                state.copy(
                    cart = items,
                    divisions = settings?.divisions.orEmpty(),
                    delivery = settings?.delivery,
                    returnWindowDays = settings?.returnWindowDays ?: 0,
                    promotions = settings?.promotions ?: Promotions(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), CheckoutUiState())

        init {
            // Filled in from the account and the profile, where the person has given their details before.
            viewModelScope.launch {
                session.currentUser()?.email?.let { email ->
                    local.update { it.copy(form = it.form.copy(email = it.form.email.ifBlank { email })) }
                }
                checkout.contactDetails().getOrNull()?.let { saved ->
                    local.update { state ->
                        state.copy(
                            form =
                                state.form.copy(
                                    name = state.form.name.ifBlank { saved.name },
                                    phone = state.form.phone.ifBlank { saved.phone },
                                    address = state.form.address.ifBlank { saved.address },
                                ),
                        )
                    }
                }
            }
        }

        fun update(transform: (DeliveryForm) -> DeliveryForm) {
            local.update { state ->
                val next = transform(state.form)
                // A new division empties a district left over from the old one.
                val divisionChanged = next.division != state.form.division
                val form = if (divisionChanged && next.district == state.form.district) next.copy(district = "") else next
                state.copy(form = form, problems = state.problems - problemsFixedBy(form))
            }
        }

        /** How many copies, saved to the cart as it changes, as the website does. */
        fun setQuantity(
            item: CartItem,
            quantity: Int,
        ) = changeCart(item) { cart.setQuantity(item.book.id, quantity) }

        fun remove(item: CartItem) = changeCart(item) { cart.remove(item.book.id) }

        private fun changeCart(
            item: CartItem,
            call: suspend () -> Outcome<Unit>,
        ) {
            viewModelScope.launch {
                local.update { it.copy(busyBookId = item.book.id, blocked = null, refused = emptyList()) }
                val result = call()
                local.update { it.copy(busyBookId = null, error = (result as? Outcome.Failure)?.error ?: it.error) }
            }
        }

        fun applyPromo() {
            val code = local.value.form.promoCode.trim()
            if (code.isEmpty() || uiState.value.promoCurrent) return
            viewModelScope.launch {
                val total = uiState.value.booksTotal
                local.update { it.copy(checkingPromo = true, promoError = null, blocked = null) }
                val result = checkout.checkPromo(code, total)
                local.update {
                    val promo = result.getOrNull()
                    it.copy(
                        checkingPromo = false,
                        promo = promo,
                        promoFor = total.takeIf { promo != null },
                        form = promo?.let { found -> it.form.copy(promoCode = found.code) } ?: it.form,
                        promoError = (result as? Outcome.Failure)?.error,
                    )
                }
            }
        }

        fun removePromo() {
            local.update { it.copy(promo = null, promoFor = null, promoError = null, blocked = null, form = it.form.copy(promoCode = "")) }
        }

        fun placeOrder() {
            val state = uiState.value
            // One order at a time: a second tap while the first is on its way does nothing.
            if (state.placing) return
            val problems = problemsIn(state.form)
            val blocked =
                when {
                    problems.isNotEmpty() -> null

                    state.items.isEmpty() -> CheckoutBlock.NothingInStock

                    // Not dropped quietly: the shopper saw a price with the code in it.
                    state.promoStale -> CheckoutBlock.PromoStale

                    else -> null
                }
            if (problems.isNotEmpty() || blocked != null) {
                local.update { if (blocked != null) it.copy(blocked = blocked) else it.copy(problems = problems) }
                return
            }
            viewModelScope.launch {
                local.update { it.copy(placing = true, error = null, blocked = null, refused = emptyList()) }
                val form = state.form
                val promoCode = state.promo?.code?.takeIf { state.promoCurrent }
                val result =
                    checkout.placeOrder(
                        OrderRequest(
                            items = state.items.map { it.book.id to it.quantity },
                            checkoutId = checkoutId,
                            contactName = form.name,
                            contactEmail = form.email,
                            contactPhone = normalisePhone(form.phone),
                            division = form.division,
                            district = form.district,
                            address = form.address,
                            note = form.note,
                            promo = promoCode,
                        ),
                    )
                val error = (result as? Outcome.Failure)?.error
                // Nothing could be ordered: the books and their reasons go beside the button, not in a passing message.
                val refused = (error as? AppError.Rejected)?.unavailable.orEmpty()
                local.update { it.copy(placing = false, error = error.takeIf { refused.isEmpty() }, refused = refused) }
                if (result is Outcome.Success) {
                    val order = result.value
                    val charged = placedBooksTotal(state.items, order.unavailable) - order.discount + order.shippingCharge
                    events.send(CheckoutEvent.Placed(order, charged.coerceAtLeast(0.0), promoCode))
                }
            }
        }

        fun errorShown() = local.update { it.copy(error = null) }

        private fun problemsFixedBy(form: DeliveryForm): Set<FormProblem> = FormProblem.entries.toSet() - problemsIn(form)

        private companion object {
            const val STOP_MS = 5_000L
            const val CHECKOUT_ID = "checkoutId"
        }
    }

/**
 * What the books in a placed order come to: a book left out is not in it,
 * and a short one is there with the copies the shop had.
 */
internal fun placedBooksTotal(
    sent: List<CartItem>,
    unavailable: List<UnavailableItem>,
): Double {
    val byBook = unavailable.associateBy { it.bookId }
    return sent.sumOf { item ->
        val missing = byBook[item.book.id]
        when {
            missing == null -> item.lineTotal
            missing.reason == UnavailableReason.Short -> item.book.salePrice * missing.reserved
            else -> 0.0
        }
    }
}

/** What is missing or wrong in the form. */
internal fun problemsIn(form: DeliveryForm): Set<FormProblem> =
    buildSet {
        if (form.name.isBlank()) add(FormProblem.Name)
        if (!EMAIL.matches(form.email.trim())) add(FormProblem.Email)
        if (!isBangladeshMobile(form.phone)) add(FormProblem.Phone)
        if (form.division.isBlank()) add(FormProblem.Division)
        if (form.district.isBlank()) add(FormProblem.District)
        // The house, road and area: the shop takes no address shorter than this.
        if (form.address.trim().length < MIN_ADDRESS) add(FormProblem.Address)
    }

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private const val MIN_ADDRESS = 5

/** 01XXXXXXXXX, written however people write it: with spaces, dashes, or +880. */
internal fun normalisePhone(phone: String): String =
    phone.trim().filterNot { it == ' ' || it == '-' }.asciiDigits().replace(Regex("^\\+?880"), "0")

internal fun isBangladeshMobile(phone: String): Boolean = Regex("^01[3-9]\\d{8}$").matches(normalisePhone(phone))

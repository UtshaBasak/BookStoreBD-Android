package io.github.utshabasak.bookstorebd.feature.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CheckoutRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CartItem
import io.github.utshabasak.bookstorebd.core.model.DeliveryRules
import io.github.utshabasak.bookstorebd.core.model.Division
import io.github.utshabasak.bookstorebd.core.model.OrderRequest
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PlacedOrder
import io.github.utshabasak.bookstorebd.core.model.Promo
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
import javax.inject.Inject

/** The delivery form, as typed. */
data class DeliveryForm(
    val name: String = "",
    val phone: String = "",
    val division: String = "",
    val district: String = "",
    val address: String = "",
    val note: String = "",
    val promoCode: String = "",
)

/** Which fields need attention before the order can go in. */
enum class FormProblem { Name, Phone, Division, District, Address }

data class CheckoutUiState(
    val items: List<CartItem> = emptyList(),
    val form: DeliveryForm = DeliveryForm(),
    val divisions: List<Division> = emptyList(),
    val delivery: DeliveryRules? = null,
    val promo: Promo? = null,
    val promoError: AppError? = null,
    val checkingPromo: Boolean = false,
    val problems: Set<FormProblem> = emptySet(),
    val placing: Boolean = false,
    val error: AppError? = null,
) {
    val booksTotal: Double get() = items.sumOf { it.lineTotal }

    /** A preview: the server prices delivery from the district, and charges what it works out. */
    val deliveryCharge: Double
        get() = if (promo?.freeDelivery == true || form.district.isBlank()) 0.0 else delivery?.chargeFor(form.district)?.toDouble() ?: 0.0

    val discount: Double get() = (promo?.discount ?: 0.0).coerceAtMost(booksTotal)
    val total: Double get() = booksTotal - discount + deliveryCharge
    val districts: List<String> get() = divisions.firstOrNull { it.name == form.division }?.districts.orEmpty()
}

sealed interface CheckoutEvent {
    data class Placed(
        val order: PlacedOrder,
        val total: Double,
    ) : CheckoutEvent
}

/** Checkout, cash on delivery: the cart, where it goes, an optional promo code, and placing the order. */
@HiltViewModel
class CheckoutViewModel
    @Inject
    constructor(
        cart: CartRepository,
        config: ConfigRepository,
        private val checkout: CheckoutRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(CheckoutUiState())
        private val events = Channel<CheckoutEvent>(Channel.BUFFERED)
        val eventFlow: Flow<CheckoutEvent> = events.receiveAsFlow()

        val uiState: StateFlow<CheckoutUiState> =
            combine(local, cart.cart, config.config) { state, items, settings ->
                state.copy(items = items, divisions = settings?.divisions.orEmpty(), delivery = settings?.delivery)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), CheckoutUiState())

        init {
            // Filled in from the profile, where the person has given their details before.
            viewModelScope.launch {
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

        fun applyPromo() {
            val code = local.value.form.promoCode.trim()
            if (code.isEmpty()) return
            viewModelScope.launch {
                local.update { it.copy(checkingPromo = true, promoError = null) }
                val result = checkout.checkPromo(code, uiState.value.booksTotal)
                local.update {
                    it.copy(checkingPromo = false, promo = result.getOrNull(), promoError = (result as? Outcome.Failure)?.error)
                }
            }
        }

        fun removePromo() {
            local.update { it.copy(promo = null, promoError = null, form = it.form.copy(promoCode = "")) }
        }

        fun placeOrder() {
            val state = uiState.value
            val problems = problemsIn(state.form)
            if (problems.isNotEmpty()) {
                local.update { it.copy(problems = problems) }
                return
            }
            viewModelScope.launch {
                local.update { it.copy(placing = true, error = null) }
                val form = state.form
                val result =
                    checkout.placeOrder(
                        OrderRequest(
                            items = state.items.map { it.book.id to it.quantity },
                            contactName = form.name,
                            contactPhone = normalisePhone(form.phone),
                            division = form.division,
                            district = form.district,
                            address = form.address,
                            note = form.note,
                            promo = state.promo?.code,
                        ),
                    )
                local.update { it.copy(placing = false, error = (result as? Outcome.Failure)?.error) }
                if (result is Outcome.Success) {
                    val order = result.value
                    val charged = state.booksTotal - order.discount + order.shippingCharge
                    events.send(CheckoutEvent.Placed(order, charged))
                }
            }
        }

        fun errorShown() = local.update { it.copy(error = null) }

        private fun problemsFixedBy(form: DeliveryForm): Set<FormProblem> = FormProblem.entries.toSet() - problemsIn(form)

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

/** What is missing or wrong in the form. */
internal fun problemsIn(form: DeliveryForm): Set<FormProblem> =
    buildSet {
        if (form.name.isBlank()) add(FormProblem.Name)
        if (!isBangladeshMobile(form.phone)) add(FormProblem.Phone)
        if (form.division.isBlank()) add(FormProblem.Division)
        if (form.district.isBlank()) add(FormProblem.District)
        if (form.address.isBlank()) add(FormProblem.Address)
    }

/** 01XXXXXXXXX, written however people write it: with spaces, dashes, or +880. */
internal fun normalisePhone(phone: String): String = phone.filterNot { it == ' ' || it == '-' }.replace(Regex("^\\+?880"), "0")

internal fun isBangladeshMobile(phone: String): Boolean = Regex("^01[3-9]\\d{8}$").matches(normalisePhone(phone))

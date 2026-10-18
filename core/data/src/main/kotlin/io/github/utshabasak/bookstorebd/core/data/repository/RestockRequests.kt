package io.github.utshabasak.bookstorebd.core.data.repository

import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Notify me when it is back" for sold-out books - in the cart, the wishlist
 * and on the book page alike: whether the signed-in person asked, by book id,
 * and asking or withdrawing. Forgotten when someone else signs in.
 */
@Singleton
class RestockRequests
    @Inject
    constructor(
        private val books: BookRepository,
        private val session: UserSession,
    ) {
        private val states = MutableStateFlow<Map<String, Boolean>>(emptyMap())
        private var owner: String? = null

        /** Whether each book was asked for; a book not yet checked is missing. */
        val requested: StateFlow<Map<String, Boolean>> = states.asStateFlow()

        /**
         * Checks the books not yet known, or every one of them when [fresh]. A
         * book whose check fails stays unknown, and is checked again next time.
         */
        suspend fun load(
            ids: Collection<String>,
            fresh: Boolean = false,
        ) {
            if (!sameOwner()) return
            ids.filter { fresh || it !in states.value }.forEach { id ->
                books.isRequested(id).getOrNull()?.let { asked -> states.update { it + (id to asked) } }
            }
        }

        /** Asks to hear when the book is back, or withdraws that; the result is whether it is asked for now. */
        suspend fun toggle(id: String): Outcome<Boolean> {
            if (!sameOwner()) return Outcome.Failure(AppError.SignInRequired)
            val result = books.setRequested(id, states.value[id] != true)
            if (result is Outcome.Success) states.update { it + (id to result.value) }
            return result
        }

        private suspend fun sameOwner(): Boolean {
            val user = session.currentUser()?.id ?: return false
            if (user != owner) {
                owner = user
                states.value = emptyMap()
            }
            return true
        }
    }

package io.github.utshabasak.bookstorebd.core.data.session

import io.github.utshabasak.bookstorebd.core.model.SessionUser
import kotlinx.coroutines.flow.Flow

/** Who is signed in: what screens need to know about the session, and nothing more. */
interface UserSession {
    val user: Flow<SessionUser?>

    /** Who is signed in now, read from storage, so it is right even straight after launch. */
    suspend fun currentUser(): SessionUser?
}

package io.github.utshabasak.bookstorebd.core.data.inbox

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The conversation on screen right now, if any. A message in it is already in
 * front of the person, so it is not also shown as a phone notification.
 */
@Singleton
class ActiveConversation
    @Inject
    constructor() {
        private val current = AtomicReference<String?>(null)

        fun enter(email: String) = current.set(email.lowercase())

        fun leave(email: String) {
            current.compareAndSet(email.lowercase(), null)
        }

        fun isOpen(email: String): Boolean = current.get() == email.lowercase()
    }

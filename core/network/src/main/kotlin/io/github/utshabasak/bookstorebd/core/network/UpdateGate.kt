package io.github.utshabasak.bookstorebd.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notices when the API says this build is too old (426), from whichever
 * request first hears it, so the app can ask to be updated at once.
 */
@Singleton
class UpdateGate
    @Inject
    constructor() {
        private val required = MutableStateFlow(false)
        val updateRequired: StateFlow<Boolean> = required.asStateFlow()

        fun markRequired() {
            required.value = true
        }
    }

class UpdateRequiredInterceptor(
    private val gate: UpdateGate,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request()).also { response ->
            if (response.code == HTTP_UPGRADE_REQUIRED) gate.markRequired()
        }

    private companion object {
        const val HTTP_UPGRADE_REQUIRED = 426
    }
}

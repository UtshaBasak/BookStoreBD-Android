package io.github.utshabasak.bookstorebd.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Test

class DispatchersModuleTest {
    @Test
    fun `each qualifier gets the dispatcher it names`() {
        assertThat(DispatchersModule.providesIoDispatcher()).isSameInstanceAs(Dispatchers.IO)
        assertThat(DispatchersModule.providesDefaultDispatcher()).isSameInstanceAs(Dispatchers.Default)
    }

    @Test
    fun `the application scope survives a failed child`() {
        val scope = DispatchersModule.providesApplicationScope(Dispatchers.Unconfined)

        scope.launch(CoroutineExceptionHandler { _, _ -> }) { error("a child failed") }

        assertThat(scope.isActive).isTrue()
        scope.cancel()
    }
}

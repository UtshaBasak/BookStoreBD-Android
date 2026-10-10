package io.github.utshabasak.bookstorebd.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `system follows the phone and the others name a language`() {
        assertThat(AppLanguage.System.tag).isNull()
        assertThat(AppLanguage.English.tag).isEqualTo("en")
        assertThat(AppLanguage.Bangla.tag).isEqualTo("bn")
    }
}

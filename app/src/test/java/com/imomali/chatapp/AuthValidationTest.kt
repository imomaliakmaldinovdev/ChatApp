package com.imomali.chatapp
import com.imomali.chatapp.domain.AuthValidation
import org.junit.Assert.*
import org.junit.Test
class AuthValidationTest {
    @Test fun rejectsBlankAndOversizedNames() {
        assertFalse(AuthValidation.name("  "))
        assertFalse(AuthValidation.name("a".repeat(61)))
        assertTrue(AuthValidation.name("  Ada  "))
    }
    @Test fun validatesEmailWithoutChangingPassword() {
        assertTrue(AuthValidation.email(" ada@example.com "))
        assertFalse(AuthValidation.email("ada @example.com"))
        assertFalse(AuthValidation.email("ada@example"))
        assertFalse(AuthValidation.password("12345"))
        assertTrue(AuthValidation.password("abcdef"))
    }
}

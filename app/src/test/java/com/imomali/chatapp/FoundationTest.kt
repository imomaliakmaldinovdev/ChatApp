package com.imomali.chatapp
import com.imomali.chatapp.navigation.*
import com.imomali.chatapp.domain.MessagePolicy
import org.junit.Assert.*
import org.junit.Test
class FoundationTest {
    @Test fun signedOutCannotEnterProtectedRoutes() {
        Route.entries.forEach { assertEquals(Route.WELCOME, SessionRouter.resolve(it, null)) }
        assertEquals(Route.WELCOME, SessionRouter.resolve(Route.PROFILE, ""))
    }
    @Test fun signedInWelcomeRedirectsToChats() { assertEquals(Route.CHATS, SessionRouter.resolve(Route.WELCOME, "user")) }
    @Test fun signedInPreservesRequestedRoute() { assertEquals(Route.CONVERSATION, SessionRouter.resolve(Route.CONVERSATION, "user")) }
    @Test fun messageBoundaries() {
        assertFalse(MessagePolicy.valid("  ")); assertFalse(MessagePolicy.valid("a".repeat(4001)))
        assertTrue(MessagePolicy.valid("a".repeat(4000))); assertTrue(MessagePolicy.valid("Hello"))
    }
}

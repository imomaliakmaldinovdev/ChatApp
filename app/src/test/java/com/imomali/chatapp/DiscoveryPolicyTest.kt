package com.imomali.chatapp

import com.imomali.chatapp.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DiscoveryPolicyTest {
    @Test fun canonicalIdIsIndependentOfSelectionOrder() {
        assertEquals("a:b", DiscoveryPolicy.conversationId("b", "a"))
        assertEquals(DiscoveryPolicy.conversationId("a", "b"), DiscoveryPolicy.conversationId("b", "a"))
        for (pair in listOf("a" to "a", "" to "b", "a:b" to "c")) {
            assertThrows(IllegalArgumentException::class.java) { DiscoveryPolicy.conversationId(pair.first, pair.second) }
        }
    }
    @Test fun searchUsesStableLocaleAndAvatarHasFallback() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("imomali", DiscoveryPolicy.query("  IMOMALI  "))
            assertEquals("RA", DiscoveryPolicy.initials(" Rustam   Akbaraliyev "))
            assertEquals("?", DiscoveryPolicy.initials("  "))
        } finally { Locale.setDefault(before) }
    }
    @Test fun recentMessageMovesAnOlderConversationAheadOfANewEmptyOne() {
        val old = ChatSummary(Conversation("old", listOf("a", "b"), 10), null, Message("m", "b", "Hello", 30))
        val new = ChatSummary(Conversation("new", listOf("a", "c"), 20), null, null)
        assertEquals(listOf(old, new), DiscoveryPolicy.sorted(listOf(new, old)))
    }
}

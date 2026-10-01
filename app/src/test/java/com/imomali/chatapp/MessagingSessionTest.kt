package com.imomali.chatapp

import com.imomali.chatapp.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class MessagingSessionTest {
    private class Repository : MessageRepository {
        data class Send(val id: String, val text: String, val complete: (Result<Unit>) -> Unit)
        val sends = mutableListOf<Send>()
        val listeners = mutableListOf<(Result<MessageHistory>) -> Unit>()
        var closed = 0
        override fun observeRecent(conversationId: String, result: (Result<MessageHistory>) -> Unit): Subscription {
            listeners += result; return Subscription { closed++ }
        }
        override fun send(conversationId: String, clientMessageId: String, text: String, result: (Result<Unit>) -> Unit) {
            sends += Send(clientMessageId, text, result)
        }
    }
    private class Fixture {
        val repository = Repository()
        val timers = mutableListOf<() -> Unit>()
        var sequence = 0
        val session = MessagingSession(repository, { _, action ->
            var canceled = false
            timers += { if (!canceled) action() }
            Subscription { canceled = true }
        }, {}, { "message-${++sequence}" })
        init { session.open("alice", "alice:bob") }
    }
    @Test fun invalidInputDoesNotSendAndFailuresKeepDraftAndReuseId() {
        val f = Fixture(); val session = f.session
        session.edit(" \n "); session.send()
        assertTrue(f.repository.sends.isEmpty())
        session.edit("hello"); session.send(); session.send()
        assertEquals(1, f.repository.sends.size)
        f.repository.sends[0].complete(Result.failure(IllegalStateException()))
        assertEquals("hello", session.state.draft)
        assertEquals(SendStatus.FAILED, session.state.outgoing?.status)
        session.send()
        assertEquals(f.repository.sends[0].id, f.repository.sends[1].id)
        f.repository.sends[1].complete(Result.success(Unit))
        assertEquals("", session.state.draft)
        assertNull(session.state.outgoing)
        session.edit("x".repeat(4001))
        assertEquals(4000, session.state.draft.length)
    }
    @Test fun timeoutDoesNotClaimFailureOrAllowAnUnrelatedReplacement() {
        val f = Fixture(); val s = f.session
        s.edit("hello"); s.send(); f.timers.last()()
        assertEquals(SendStatus.WAITING, s.state.outgoing?.status)
        s.edit("replacement")
        assertEquals("hello", s.state.draft)
        s.send()
        assertEquals(f.repository.sends[0].id, f.repository.sends[1].id)
        f.repository.sends[0].complete(Result.success(Unit))
        assertNotNull(s.state.outgoing)
        f.repository.sends[1].complete(Result.success(Unit))
        assertNull(s.state.outgoing)
    }
    @Test fun acknowledgedSnapshotsReconcileOnceAndStaleCallbacksCannotRestorePrivateData() {
        val f = Fixture(); val s = f.session
        s.edit("hello"); s.send()
        val message = Message(f.repository.sends[0].id, "alice", "hello", 123)
        val oldListener = f.repository.listeners[0]
        oldListener(Result.success(MessageHistory(listOf(message, message), false)))
        assertEquals(1, s.state.messages.size)
        assertNull(s.state.outgoing); assertEquals("", s.state.draft)
        s.open("alice", "alice:eve")
        oldListener(Result.success(MessageHistory(listOf(message), false)))
        assertTrue(s.state.messages.isEmpty()); assertEquals(1, f.repository.closed)
        s.edit("other"); s.send(); s.clear()
        f.repository.sends.last().complete(Result.success(Unit))
        f.repository.listeners.last()(Result.success(MessageHistory(listOf(message), false)))
        assertNull(s.state.uid); assertTrue(s.state.messages.isEmpty()); assertEquals("", s.state.draft)
    }
    @Test fun pauseStopsListenerWithoutLosingDraftAndReopenResubscribes() {
        val f = Fixture(); val s = f.session
        s.edit("draft"); s.pause()
        f.repository.listeners[0](Result.success(MessageHistory(listOf(Message("m", "bob", "old", 1)), false)))
        assertTrue(s.state.messages.isEmpty()); assertEquals("draft", s.state.draft)
        s.open("alice", "alice:bob")
        assertEquals(2, f.repository.listeners.size); assertEquals("draft", s.state.draft)
    }
    @Test fun timestampsRespectLocalMidnightAndHaveAnUnknownFallback() {
        val now = 1704150600000L // 2024-01-01 23:10 UTC
        val earlier = now - 60 * 60 * 1000
        assertNull(MessageTime.format(0))
        val utc = TimeZone.getTimeZone("UTC")
        val east = TimeZone.getTimeZone("GMT+01:00")
        val sameDay = MessageTime.format(earlier, now, Locale.US, utc)!!
        val previousDay = MessageTime.format(earlier, now, Locale.US, east)!!
        assertFalse(sameDay.contains("/"))
        assertTrue(previousDay.contains("/"))
        assertTrue(MessageTime.format(earlier, now, Locale.US, east, full = true)!!.contains("2024"))
    }
}

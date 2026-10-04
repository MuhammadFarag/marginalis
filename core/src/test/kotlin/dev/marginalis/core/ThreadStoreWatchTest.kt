package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadStoreWatchTest {

    private val agent = Author.Agent("Claude")

    private fun thread() = CommentThread(file = "a.py", line = 3, anchorText = "def f():")

    @Test
    fun `a watcher hears its own thread change`() {
        val store = ThreadStore()
        val watched = thread().also(store::add)
        val heard = mutableListOf<CommentThread?>()
        store.watch(watched.id) { heard += it }

        watched.addMessage(Message(agent, "reply"))
        store.notifyChanged(watched)

        assertEquals(1, heard.size)
        assertSame(watched, heard.single())
    }

    @Test
    fun `a watcher ignores other threads`() {
        val store = ThreadStore()
        val watched = thread().also(store::add)
        val heard = mutableListOf<CommentThread?>()
        store.watch(watched.id) { heard += it }

        store.add(thread())

        assertTrue(heard.isEmpty())
    }

    @Test
    fun `a watcher hears its thread deleted as absence`() {
        val store = ThreadStore()
        val watched = thread().also(store::add)
        val heard = mutableListOf<CommentThread?>()
        store.watch(watched.id) { heard += it }

        store.remove(watched.id)

        assertEquals(1, heard.size)
        assertNull(heard.single())
    }

    @Test
    fun `a watcher hears its thread cleared as absence`() {
        val store = ThreadStore()
        val watched = thread().also(store::add)
        store.add(thread())
        val heard = mutableListOf<CommentThread?>()
        store.watch(watched.id) { heard += it }

        store.clear()

        assertEquals(listOf<CommentThread?>(null), heard)
    }

    @Test
    fun `a closed watch hears nothing more`() {
        val store = ThreadStore()
        val watched = thread().also(store::add)
        val heard = mutableListOf<CommentThread?>()
        val watch = store.watch(watched.id) { heard += it }

        watch.close()
        store.notifyChanged(watched)

        assertTrue(heard.isEmpty())
    }

    @Test
    fun `a draft's watcher hears it stored`() {
        val store = ThreadStore()
        val draft = thread()
        val heard = mutableListOf<CommentThread?>()
        store.watch(draft.id) { heard += it }

        store.add(draft)

        assertSame(draft, heard.single())
    }

    @Test
    fun `an observer hears every thread's change`() {
        val store = ThreadStore()
        val heard = mutableListOf<CommentThread>()
        store.observe { heard += it }

        val first = thread().also(store::add)
        val second = thread().also(store::add)
        store.notifyChanged(first)

        assertEquals(listOf(first, second, first), heard)
    }

    @Test
    fun `a closed observer hears nothing more`() {
        val store = ThreadStore()
        val heard = mutableListOf<CommentThread>()
        val observer = store.observe { heard += it }

        observer.close()
        store.add(thread())

        assertTrue(heard.isEmpty())
    }
}

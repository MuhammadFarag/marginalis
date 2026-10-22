package dev.marginalis.plugin.ui

import dev.marginalis.plugin.transport.HttpContractTestCase

class ShortenLongHistoryTest : HttpContractTestCase() {

    private lateinit var thread: String

    override fun setUp() {
        super.setUp()
        sample()
        thread = agent.addOnLine(1, "body" to BODIES.first())
        BODIES.drop(1).forEach { agent.reply(thread, it) }
    }

    fun testAnUnreadLongThreadShowsEveryMessage() {
        val panel = user.open(thread)

        assertEquals(BODIES, shown(panel))
    }

    fun testAnUnreadLongThreadHidesNothing() {
        val panel = user.open(thread)

        assertEquals(emptyList<String>(), historyLinks(panel))
    }

    fun testReadingKeepsEveryMessageInThePanelBeingRead() {
        val panel = user.open(thread)

        userReads()
        panel.refresh()

        assertEquals(BODIES, shown(panel))
    }

    fun testAReadThreadThatIsStillOpenOffersToShorten() {
        val panel = user.open(thread)

        userReads()
        panel.refresh()

        assertEquals(SHORTEN, historyOffer(panel))
    }

    fun testAReadLongThreadReopensWithTheFirstAndTheLastTwoMessages() {
        userReads()

        val panel = user.open(thread)

        assertEquals(SHORTENED, shown(panel))
    }

    fun testTheFourMessagesBetweenHideBehindOneLink() {
        userReads()

        val panel = user.open(thread)

        assertEquals(listOf("Show 4 more messages"), historyLinks(panel))
    }

    fun testTheLinkShowsTheFullHistory() {
        userReads()
        val panel = user.open(thread)

        user.clickLink(panel, "Show 4 more messages")

        assertEquals(BODIES, shown(panel))
    }

    fun testTheFullHistoryOffersToShorten() {
        userReads()
        val panel = user.open(thread)

        user.clickLink(panel, "Show 4 more messages")

        assertEquals(SHORTEN, historyOffer(panel))
    }

    fun testTheFullHistoryStaysOnTheNextVisit() {
        userReads()
        user.clickLink(user.open(thread), "Show 4 more messages")

        val reopened = user.open(thread)

        assertEquals(BODIES, shown(reopened))
    }

    fun testShortenHistoryHidesTheMiddleAgain() {
        val panel = fullHistoryOnTheNextVisit()

        user.press(panel, SHORTEN)

        assertEquals(SHORTENED, shown(panel))
    }

    fun testAShortenedThreadOffersTheFullHistoryAgain() {
        val panel = fullHistoryOnTheNextVisit()

        user.press(panel, SHORTEN)

        assertEquals(FULL, historyOffer(panel))
    }

    fun testFollowingALinkToAHiddenMessageBringsOutOnlyThatMessage() {
        userReads()

        val panel = afterFollowingALinkTo(message = 3)

        assertEquals(listOf(BODIES[0], BODIES[3], BODIES[5], BODIES[6]), shown(panel))
        assertEquals(listOf("Show 2 more messages", "Show 1 more message"), historyLinks(panel))
    }

    fun testFollowingALinkToAHiddenMessageKeepsTheHistoryShort() {
        userReads()
        afterFollowingALinkTo(message = 3)

        val reopened = user.open(thread)

        assertEquals(SHORTENED, shown(reopened))
    }

    fun testAfterFollowingALinkTheRestOfTheHistoryIsStillOnOffer() {
        userReads()

        val panel = afterFollowingALinkTo(message = 3)

        assertEquals(FULL, historyOffer(panel))
    }

    private fun afterFollowingALinkTo(message: Int): ThreadPanel {
        val panel = user.open(thread)
        val reference = "mg:${store.threads.byId(thread)!!.messages[message].id.take(8)}"
        val citing = agent.addOnLine(9, "body" to "As $reference says.")
        ThreadInlayManager.open(project, user.editor(), user.thread(citing))
        user.followLink(user.panels().single { "As mg:" in user.shownText(it) }, reference)
        return panel
    }

    private fun fullHistoryOnTheNextVisit(): ThreadPanel {
        userReads()
        user.clickLink(user.open(thread), "Show 4 more messages")
        return user.open(thread)
    }

    // How a look at the thread reads it; the panel itself marks read only while it is on screen.
    private fun userReads() {
        val read = store.threads.byId(thread)!!
        read.markReadByUser(read.messages)
        store.threads.notifyChanged(read)
    }

    private fun shown(panel: ThreadPanel): List<String> = user.shownText(panel).let { text -> BODIES.filter { it in text } }

    private fun historyLinks(panel: ThreadPanel): List<String> = user.visibleLinks(panel).filter { Regex("more messages?$").containsMatchIn(it) }

    private fun historyOffer(panel: ThreadPanel): String? =
        user.toolbar(panel).entries.singleOrNull { (text, shown) -> text.endsWith("History") && shown.isVisible }?.key

    private companion object {
        val BODIES = (1..7).map { "Message $it of the long thread." }
        val SHORTENED = listOf(BODIES[0], BODIES[5], BODIES[6])
        const val SHORTEN = "Shorten History"
        const val FULL = "Show Full History"
    }
}

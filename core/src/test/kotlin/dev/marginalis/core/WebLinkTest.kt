package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebLinkTest {

    @Test
    fun `only http and https links open in the browser`() {
        assertTrue(WebLink.isBrowsable("https://github.com/o/r/pull/1"))
        assertTrue(WebLink.isBrowsable("HTTP://example.com"))
        assertTrue(WebLink.isBrowsable("http://my_host.corp/x"))
        assertTrue(WebLink.isBrowsable("https://example.com/s?q=\"x\""))
        assertTrue(WebLink.isBrowsable("https://example.com/p#a#b"))
        for (url in listOf("file:///etc/passwd", "javascript:alert(1)", "mailto:a@b", "src/A.kt", "https://", "ht tp://x", "")) {
            assertFalse(WebLink.isBrowsable(url), url)
        }
    }
}

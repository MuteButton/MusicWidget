package io.github.mutebutton.musicwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlTest {
    @Test fun bareDomainUsesHttps() = assertEquals("https://music.youtube.com", BrowserUrl.normalize(" music.youtube.com "))
    @Test fun preservesPathQueryAndFragment() = assertEquals("https://example.com/play?v=123#time", BrowserUrl.normalize("example.com/play?v=123#time"))
    @Test fun acceptsExplicitHttpAndPort() = assertEquals("http://localhost:8080/music", BrowserUrl.normalize("http://localhost:8080/music"))
    @Test fun blankClearsDestination() = assertEquals("", BrowserUrl.normalize("  "))
    @Test fun rejectsInvalidAndNonWebDestinations() {
        listOf("javascript:alert(1)", "file:///tmp/music", "intent://music", "https://", "not a url", "https://user:pass@example.com", "https://example.com:99999").forEach {
            assertNull(it, BrowserUrl.normalize(it))
        }
    }
}

package io.github.mutebutton.oncue

import java.net.URI

internal object BrowserUrl {
    fun key(packageName: String) = "browser_url:$packageName"

    /** Empty input clears the destination; null means invalid input. */
    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return ""
        val candidate = if (value.contains("://")) value else "https://$value"
        return try {
            val uri = URI(candidate)
            if (uri.scheme.lowercase() !in setOf("http", "https") ||
                uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.port !in -1..65535) null else uri.toASCIIString()
        } catch (_: Exception) { null }
    }
}

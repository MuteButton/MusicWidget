package io.github.mutebutton.musicwidget

/** Presentation-only grace period. Playback state and position never pass through this class. */
internal class TransitionTitle {
    private var source: Any? = null
    private var lastTitle: String? = null
    private var deadline: Long? = null

    data class Result(val title: String, val refreshAt: Long?)

    fun reset() { source = null; lastTitle = null; deadline = null }

    fun resolve(token: Any, raw: String?, fallback: String, now: Long): Result {
        if (source != token) { reset(); source = token }
        val value = raw?.trim().orEmpty()
        val host = value.lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val youtube = host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
        if (value.isNotEmpty() && !youtube) {
            lastTitle = value
            deadline = null
            return Result(value, null)
        }
        val until = deadline ?: (now + 1_000).also { deadline = it }
        if (lastTitle != null && now < until) return Result(lastTitle!!, until)
        // Expired placeholders cannot keep reviving the previous song.
        lastTitle = null
        return Result(if (youtube) "YouTube" else fallback, null)
    }
}

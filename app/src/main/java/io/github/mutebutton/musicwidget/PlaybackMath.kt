package io.github.mutebutton.musicwidget

/** Independent of Android so timing edge cases can be covered by local tests. */
internal object PlaybackMath {
    /** No scheduled work when extrapolation cannot change the displayed position. */
    fun needsTick(base: Long, updatedAt: Long, now: Long, speed: Float, advancing: Boolean, duration: Long): Boolean {
        if (!advancing || base < 0 || updatedAt <= 0 || duration <= 0 || !speed.isFinite() || speed == 0f) return false
        val current = position(base, updatedAt, now, speed, true, duration)
        return if (speed > 0) current < duration else current > 0
    }

    fun position(base: Long, updatedAt: Long, now: Long, speed: Float, playing: Boolean, duration: Long): Long {
        if (base < 0) return 0
        val elapsed = if (playing && updatedAt > 0) (now - updatedAt).coerceAtLeast(0) else 0
        val result = (base + elapsed * speed.toDouble()).toLong().coerceAtLeast(0)
        return if (duration > 0) result.coerceAtMost(duration) else result
    }
    fun time(milliseconds: Long, roundUp: Boolean = false): String {
        val positive = milliseconds.coerceAtLeast(0)
        val seconds = positive / 1000 + if (roundUp && positive % 1000 != 0L) 1 else 0
        return if (seconds >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    }
}

package io.github.mutebutton.oncue.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Controls an already-running fixture without moving the launcher to the background. */
class DebugMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accepted = DebugMediaService.acceptCommand(intent)
        resultCode = if (accepted) 0 else 1
        resultData = if (accepted) "Fixture command accepted" else
            "Start DebugMediaActivity with command reset first; fixture is not running"
    }
}

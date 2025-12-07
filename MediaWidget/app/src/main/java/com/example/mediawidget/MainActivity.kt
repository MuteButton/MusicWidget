package com.example.mediawidget

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable StrictMode in debug builds to detect potential leaks
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val grantButton = findViewById<Button>(R.id.grant_button)

        updatePermissionUi(statusText, grantButton)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val listeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )

        return listeners
            ?.split(":")
            ?.any { ComponentName.unflattenFromString(it)?.packageName == pkgName }
            ?: false
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionUi(
            findViewById(R.id.status_text),
            findViewById(R.id.grant_button)
        )
    }

    private fun updatePermissionUi(statusText: TextView, grantButton: Button) {
        if (isNotificationServiceEnabled()) {
            statusText.text = "Permission Granted. You can add the widget to your home screen."
            grantButton.isEnabled = false
        } else {
            statusText.text = getString(R.string.permission_instruction)
            grantButton.isEnabled = true
            grantButton.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
}

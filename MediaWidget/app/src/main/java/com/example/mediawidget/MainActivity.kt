package com.example.mediawidget

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val grantButton = findViewById<Button>(R.id.grant_button)

        if (!isNotificationServiceEnabled()) {
            statusText.text = getString(R.string.permission_instruction)
            grantButton.isEnabled = true
            grantButton.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else {
            statusText.text = "Permission Granted. You can add the widget to your home screen."
            grantButton.isEnabled = false
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (pkgName == cn.packageName) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh status when returning from settings
        val statusText = findViewById<TextView>(R.id.status_text)
        val grantButton = findViewById<Button>(R.id.grant_button)
        
        if (isNotificationServiceEnabled()) {
            statusText.text = "Permission Granted. You can add the widget to your home screen."
            grantButton.isEnabled = false
        }
    }
}

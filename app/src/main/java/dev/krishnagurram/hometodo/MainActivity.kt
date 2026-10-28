package dev.krishnagurram.hometodo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.krishnagurram.hometodo.reminders.Reminders
import dev.krishnagurram.hometodo.ui.AppNav
import dev.krishnagurram.hometodo.ui.theme.HomeTodoTheme

/**
 * The single Activity. It hands the app to Compose, and it's the front door for
 * text shared from other apps (WhatsApp -> Share -> Home Todo).
 * Which screen shows is decided by AppNav, not by separate Activities.
 */
class MainActivity : ComponentActivity() {

    private val app: HomeTodoApp
        get() = application as HomeTodoApp

    // Must be registered before the Activity is fully started, hence a property, not inside onCreate logic.
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* yes or no, both fine */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only handle the share on a fresh start, not when the phone unfolds and the Activity
        // is recreated with the same old intent (that would import the list twice).
        if (savedInstanceState == null) handleShare(intent)
        askForNotificationsOnce()
        setContent {
            HomeTodoTheme {
                AppNav(app)
            }
        }
    }

    /** singleTop: if the app is already open and you share into it, the new intent arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        app.incomingShare.value = text // AppNav sees this and opens the import screen
    }

    /** Android 13+ needs a yes before posting the daily reminder. Android shows the prompt at most twice. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canPost(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

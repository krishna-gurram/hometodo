package dev.krishnagurram.hometodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.krishnagurram.hometodo.ui.AppNav
import dev.krishnagurram.hometodo.ui.theme.HomeTodoTheme

/**
 * The single Activity. Its whole job is to hand the app to Compose.
 * Which screen shows is decided by AppNav, not by separate Activities.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = (application as HomeTodoApp).db
        setContent {
            HomeTodoTheme {
                AppNav(db)
            }
        }
    }
}

package dev.krishnagurram.hometodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.krishnagurram.hometodo.ui.snapshot.SnapshotRoute
import dev.krishnagurram.hometodo.ui.theme.HomeTodoTheme

/**
 * The single Activity. Its whole job is to hand the screen to Compose.
 * Navigation between screens arrives in milestone 3.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = (application as HomeTodoApp).db
        setContent {
            HomeTodoTheme {
                SnapshotRoute(db)
            }
        }
    }
}

package dev.krishnagurram.hometodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Item
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = (application as HomeTodoApp).db
        setContent {
            MaterialTheme {
                DataCheckScreen(db)
            }
        }
    }
}

/**
 * Milestone 1 throwaway screen. Its only job is to prove the database works:
 * the three seeded stores appear, and tapping the button adds an item to the
 * Indian list and the count goes up WITHOUT us telling the UI to refresh.
 *
 * In React terms: a @Composable is a function component. collectAsStateWithLifecycle()
 * is like a useState that a subscription keeps updated. When the Flow emits, Compose
 * re-runs ("recomposes") only the functions that read the changed value.
 */
@Composable
private fun DataCheckScreen(db: AppDatabase) {
    val stores by db.storeDao().observeWithCounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val catalogSize by db.itemDao().observeCatalogSize().collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Database check", style = MaterialTheme.typography.headlineMedium)
        Text("$catalogSize items in catalog")

        stores.forEach { store ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(Color(android.graphics.Color.parseColor(store.colorHex)), CircleShape)
                )
                Text(store.name, modifier = Modifier.weight(1f))
                Text("${store.neededCount} to get")
            }
        }

        Button(
            onClick = {
                val indian = stores.firstOrNull { it.name == "Indian" }
                if (indian != null) {
                    // Database writes are suspend functions, so they need a coroutine.
                    // launch {} starts one without blocking the UI thread.
                    scope.launch {
                        db.itemDao().addToLists(
                            Item(name = "Test item ${catalogSize + 1}"),
                            listOf(indian.id),
                        )
                    }
                }
            },
        ) {
            Text("Add test item to Indian")
        }
    }
}

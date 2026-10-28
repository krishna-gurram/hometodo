package dev.krishnagurram.hometodo.ui.importlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.Choice
import dev.krishnagurram.hometodo.ui.theme.HomeColors

/** Review screen for a list shared in from WhatsApp. Nothing is added until you tap Add. */
@Composable
fun ImportRoute(app: HomeTodoApp, onClose: () -> Unit) {
    val vm: ImportViewModel = viewModel { ImportViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) { if (finished) onClose() }

    Scaffold(containerColor = HomeColors.Ground) { insets ->
        val s = state ?: return@Scaffold
        val count = s.rows.count { it.include }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BackBar(onClose) }
            item {
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        if (s.rows.isEmpty()) "No items found" else "${s.rows.size} items found",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.Ink,
                    )
                    Text("Shared text", fontSize = 15.sp, color = HomeColors.InkMuted)
                }
            }
            item {
                Text(
                    s.originalText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HomeColors.Hairline, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    fontSize = 15.sp,
                    color = HomeColors.Ink,
                )
            }
            items(s.rows, key = { it.key }) { row ->
                ImportRowCard(
                    row = row,
                    stores = s.stores,
                    onToggleInclude = { vm.toggleInclude(row.key) },
                    onToggleStore = { storeId -> vm.toggleStore(row.key, storeId) },
                )
            }
            error?.let { message ->
                item { Text(message, color = HomeColors.Urgent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
            }
            if (s.rows.isNotEmpty()) {
                item {
                    Button(
                        onClick = vm::apply,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeColors.Accent),
                    ) { Text(if (count == 1) "Add 1 item" else "Add $count items", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun ImportRowCard(
    row: ImportRow,
    stores: List<Store>,
    onToggleInclude: () -> Unit,
    onToggleStore: (String) -> Unit,
) {
    CardSurface {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = row.include, role = Role.Checkbox) { onToggleInclude() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = row.include, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    val title = if (row.quantity != null) "${row.name} × ${row.quantity}" else row.name
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink)
                    val subtitle = when {
                        row.matchedId == null -> "New item"
                        !row.written.equals(row.name, ignoreCase = true) -> "Matched “${row.written}”"
                        else -> "Already in your catalog"
                    }
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = if (row.matchedId == null) HomeColors.Urgent else HomeColors.InkMuted,
                    )
                }
            }
            if (row.include) {
                // Store pills, wrapped three per row. Tap to toggle; an item can be in several.
                stores.chunked(3).forEach { rowStores ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowStores.forEach { store ->
                            Choice(
                                store.name,
                                selected = store.id in row.storeIds,
                                onClick = { onToggleStore(store.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowStores.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

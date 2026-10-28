package dev.krishnagurram.hometodo.ui.stores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.domain.StoreRules
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.ColorDot
import dev.krishnagurram.hometodo.ui.components.parseHex
import dev.krishnagurram.hometodo.ui.theme.HomeColors

@Composable
fun ManageStoresRoute(db: AppDatabase, onBack: () -> Unit) {
    val vm: ManageStoresViewModel = viewModel { ManageStoresViewModel(db) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val deleteRequest by vm.deleteRequest.collectAsStateWithLifecycle()

    Scaffold(containerColor = HomeColors.Ground) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { BackBar(onBack) }
            item {
                Text(
                    "Stores",
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.Ink,
                )
            }
            item {
                CardSurface {
                    stores.forEachIndexed { index, store ->
                        if (index > 0) HorizontalDivider(color = HomeColors.Hairline)
                        StoreRow(
                            store = store,
                            canMoveUp = index > 0,
                            canMoveDown = index < stores.lastIndex,
                            // Never let you delete your last store: nothing could be added anywhere.
                            canDelete = stores.size > 1,
                            onEdit = { vm.startEdit(store) },
                            onMove = { delta -> vm.move(store, delta) },
                            onDelete = { vm.requestDelete(store) },
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = vm::startAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeColors.Accent),
                ) { Text("Add store") }
            }
            item {
                Text(
                    "Tap a store to rename it or change its color.",
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    fontSize = 13.sp,
                    color = HomeColors.InkMuted,
                )
            }
        }
    }

    dialog?.let { d ->
        StoreEditDialog(
            dialog = d,
            nameError = vm::nameError,
            onSave = vm::save,
            onCancel = vm::closeDialog,
        )
    }

    deleteRequest?.let { request ->
        DeleteStoreDialog(request, onConfirm = vm::confirmDelete, onCancel = vm::cancelDelete)
    }
}

@Composable
private fun StoreRow(
    store: Store,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Edit ${store.name}", onClick = onEdit)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorDot(store.colorHex, size = 12.dp)
            Text(store.name, fontSize = 17.sp, color = HomeColors.Ink)
        }
        // Hidden arrows keep their space (Box) so rows line up.
        if (canMoveUp) {
            IconButton(onClick = { onMove(-1) }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${store.name} up", tint = HomeColors.InkMuted)
            }
        } else {
            Box(Modifier.size(48.dp))
        }
        if (canMoveDown) {
            IconButton(onClick = { onMove(1) }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${store.name} down", tint = HomeColors.InkMuted)
            }
        } else {
            Box(Modifier.size(48.dp))
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${store.name}", tint = HomeColors.InkMuted)
            }
        }
    }
}

@Composable
private fun StoreEditDialog(
    dialog: StoreDialog,
    nameError: (String) -> String?,
    onSave: (name: String, colorHex: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(dialog) { mutableStateOf(dialog.existing?.name ?: "") }
    var color by remember(dialog) { mutableStateOf(dialog.startColor) }
    // Only show "Enter a store name" after you've tried to save, not the moment the dialog opens.
    var triedSave by remember(dialog) { mutableStateOf(false) }
    val error = nameError(name)
    val showError = error != null && (triedSave || name.isNotBlank())

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (dialog.existing == null) "New store" else "Edit store") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Sam's Club") },
                    singleLine = true,
                    isError = showError,
                    supportingText = { if (showError) Text(error ?: "") },
                )
                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.InkMuted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoreRules.palette.chunked(4).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { hex ->
                                ColorSwatch(hex, selected = hex.equals(color, ignoreCase = true)) { color = hex }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                triedSave = true
                if (error == null) onSave(name, color)
            }) { Text("Save", color = HomeColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HomeColors.InkMuted) }
        },
    )
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    // 44dp tap target, with a ring around the chosen color.
    Box(
        Modifier
            .size(44.dp)
            .border(2.dp, if (selected) HomeColors.Ink else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = "Choose color", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(parseHex(hex), CircleShape)
        )
    }
}

@Composable
private fun DeleteStoreDialog(request: DeleteRequest, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val name = request.store.name
    val body = if (request.orphanNames.isEmpty()) {
        "Items tagged to $name stay in your catalog, so you can add them to another store later."
    } else {
        val list = request.orphanNames.joinToString(", ")
        "These items are only sold at $name and will come off your list: $list.\n\n" +
            "They stay in your catalog, so you can tag them to another store later."
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete $name?") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = HomeColors.Urgent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HomeColors.InkMuted) }
        },
    )
}

package dev.krishnagurram.hometodo.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.GotItem
import dev.krishnagurram.hometodo.data.Item
import dev.krishnagurram.hometodo.data.ListItem
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.CheckCircleButton
import dev.krishnagurram.hometodo.ui.components.CheckedCircle
import dev.krishnagurram.hometodo.ui.components.ColorDot
import dev.krishnagurram.hometodo.ui.components.SectionLabel
import dev.krishnagurram.hometodo.ui.theme.HomeColors

@Composable
fun StoreListRoute(db: AppDatabase, storeId: String, onBack: () -> Unit) {
    val vm: StoreListViewModel = viewModel { StoreListViewModel(db, storeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val justBought by vm.justBought.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()

    // The store was deleted (from Manage stores) while this list was open: leave.
    val current = state
    if (current != null && current.store == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    StoreListScreen(
        state = current,
        justBought = justBought,
        onBack = onBack,
        onAdd = vm::add,
        onCheckOff = vm::checkOff,
        onUndo = vm::undo,
        onUndoExpired = vm::undoExpired,
        onPutBack = vm::putBack,
        onEdit = vm::startEdit,
    )

    editing?.let { edit ->
        EditItemDialog(
            edit = edit,
            onSave = vm::saveEdit,
            onRemove = vm::removeFromList,
            onCancel = vm::cancelEdit,
        )
    }
}

@Composable
fun StoreListScreen(
    state: StoreListState?,
    justBought: JustBought?,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onCheckOff: (Item) -> Unit,
    onUndo: (JustBought) -> Unit,
    onUndoExpired: () -> Unit,
    onPutBack: (GotItem) -> Unit,
    onEdit: (Item) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(justBought) {
        val bought = justBought ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Got ${bought.itemName}", "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo(bought) else onUndoExpired()
    }

    Scaffold(
        containerColor = HomeColors.Ground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        val store = state?.store
        if (state != null && store != null) {
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
                    Row(
                        Modifier.padding(start = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ColorDot(store.colorHex, size = 12.dp)
                        Text(store.name, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
                        Text("${state.items.size} to get", fontSize = 15.sp, color = HomeColors.InkMuted)
                    }
                }
                item { AddField(storeName = store.name, onAdd = onAdd) }

                item {
                    if (state.items.isEmpty()) {
                        CardSurface {
                            Text(
                                "Nothing to get here. Add something above.",
                                modifier = Modifier.padding(20.dp),
                                fontSize = 15.sp,
                                color = HomeColors.InkMuted,
                            )
                        }
                    } else {
                        CardSurface {
                            state.items.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = HomeColors.Hairline)
                                NeededRow(row, onCheckOff, onEdit)
                            }
                        }
                    }
                }

                if (state.gotToday.isNotEmpty()) {
                    item { SectionLabel("GOT IT TODAY · ${state.gotToday.size}") }
                    // items(list) is LazyColumn's version of map(): one row per element.
                    items(state.gotToday, key = { it.purchaseId }) { got -> GotRow(got, onPutBack) }
                }
            }
        }
    }
}

@Composable
private fun AddField(storeName: String, onAdd: (String) -> Unit) {
    // rememberSaveable survives unfolding the phone; plain remember would lose your half-typed text.
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(text)
        text = "" // keyboard stays open, so you can add the next item right away
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add to $storeName") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        IconButton(onClick = submit) {
            Icon(Icons.Filled.Add, contentDescription = "Add item", tint = HomeColors.Accent)
        }
    }
}

@Composable
private fun NeededRow(row: ListItem, onCheckOff: (Item) -> Unit, onEdit: (Item) -> Unit) {
    val item = row.item
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircleButton(label = "Check off ${item.name}", onClick = { onCheckOff(item) })
        // Tapping the name (not the circle) opens the edit dialog.
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Edit ${item.name}") { onEdit(item) }
                .padding(vertical = 12.dp)
        ) {
            Text(item.name, fontSize = 17.sp, color = HomeColors.Ink)
            item.quantity?.let { Text(it, fontSize = 13.sp, color = HomeColors.InkMuted) }
        }
        row.otherStores?.let { others ->
            Text(
                "+ $others",
                modifier = Modifier
                    .background(HomeColors.Ground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.InkMuted,
            )
        }
    }
}

@Composable
private fun GotRow(got: GotItem, onPutBack: (GotItem) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Put ${got.item.name} back on the list") { onPutBack(got) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckedCircle()
        Text(
            got.item.name,
            fontSize = 16.sp,
            color = HomeColors.InkMuted,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun EditItemDialog(
    edit: EditItemState,
    onSave: (quantity: String, storeIds: Set<String>) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    // Dialog-local draft state. remember(edit) resets it if a different item is opened.
    var quantity by remember(edit) { mutableStateOf(edit.item.quantity ?: "") }
    var selected by remember(edit) { mutableStateOf(edit.taggedIds) }
    var showStoreError by remember(edit) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(edit.item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    placeholder = { Text("2, 20 lb, a big bunch") },
                    singleLine = true,
                )
                Text("Buy at", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.InkMuted)
                edit.allStores.forEach { store ->
                    val checked = store.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // toggleable makes the WHOLE row tappable, not just the tiny checkbox.
                            .toggleable(value = checked, role = Role.Checkbox) { isOn ->
                                selected = if (isOn) selected + store.id else selected - store.id
                                showStoreError = false
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        ColorDot(store.colorHex)
                        Text(store.name, fontSize = 16.sp)
                    }
                }
                if (showStoreError) {
                    Text("Pick at least one store", fontSize = 13.sp, color = HomeColors.Urgent)
                }
                TextButton(onClick = onRemove) {
                    Text("Remove from list without buying", color = HomeColors.InkMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected.isEmpty()) showStoreError = true else onSave(quantity, selected)
            }) { Text("Save", color = HomeColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HomeColors.InkMuted) }
        },
    )
}

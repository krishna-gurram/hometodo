package dev.krishnagurram.hometodo.ui.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import dev.krishnagurram.hometodo.data.CatalogItem
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
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val justRemoved by vm.justRemoved.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The store was deleted (from Manage stores) while this list was open: leave.
    val current = state
    if (current != null && current.store == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    StoreListScreen(
        state = current,
        query = vm.query, // Compose state: reading it here subscribes this screen to changes
        suggestions = suggestions,
        justRemoved = justRemoved,
        actions = StoreListActions(
            back = onBack,
            share = {
                // ACTION_SEND opens Android's share sheet; you pick WhatsApp (or anything).
                vm.shareText()?.let { text ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share list"))
                }
            },
            queryChange = vm::onQueryChange,
            add = vm::add,
            addSuggestion = vm::addSuggestion,
            toggle = vm::toggle,
            clearChecked = vm::clearChecked,
            remove = vm::remove,
            undoRemove = vm::undoRemove,
            undoExpired = vm::undoExpired,
            edit = vm::startEdit,
        ),
    )

    editing?.let { edit ->
        EditItemDialog(edit = edit, onSave = vm::saveEdit, onCancel = vm::cancelEdit)
    }
}

/** Every event this screen can send up, bundled so the parameter list stays readable. */
class StoreListActions(
    val back: () -> Unit,
    val share: () -> Unit,
    val queryChange: (String) -> Unit,
    val add: () -> Unit,
    val addSuggestion: (itemId: String) -> Unit,
    val toggle: (Item) -> Unit,
    val clearChecked: () -> Unit,
    val remove: (Item) -> Unit,
    val undoRemove: (Item) -> Unit,
    val undoExpired: () -> Unit,
    val edit: (Item) -> Unit,
)

@Composable
fun StoreListScreen(
    state: StoreListState?,
    query: String,
    suggestions: List<CatalogItem>,
    justRemoved: Item?,
    actions: StoreListActions,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(justRemoved) {
        val removed = justRemoved ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Removed ${removed.name}", "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) actions.undoRemove(removed) else actions.undoExpired()
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
                item {
                    BackBar(actions.back) {
                        IconButton(onClick = actions.share) {
                            Icon(Icons.Filled.Share, contentDescription = "Share list", tint = HomeColors.Ink)
                        }
                    }
                }
                item {
                    Row(
                        Modifier.padding(start = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ColorDot(store.colorHex, size = 12.dp)
                        Text(store.name, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
                        Text("${state.toGet.size} to get", fontSize = 15.sp, color = HomeColors.InkMuted)
                    }
                }
                item { AddField(query, store.name, actions.queryChange, actions.add) }

                // Quiet suggestions: only while typing, only things you've added before.
                val visibleSuggestions = suggestions.filterNot { onThisList(it, store.name) }
                if (query.isNotBlank() && visibleSuggestions.isNotEmpty()) {
                    item { Suggestions(visibleSuggestions, actions.addSuggestion) }
                }

                item {
                    if (state.toGet.isEmpty() && state.checked.isEmpty()) {
                        CardSurface {
                            Text(
                                "Your list is empty. Type above and press Enter to add.",
                                modifier = Modifier.padding(20.dp),
                                fontSize = 15.sp,
                                color = HomeColors.InkMuted,
                            )
                        }
                    } else if (state.toGet.isNotEmpty()) {
                        ListCard(state.toGet, actions)
                    }
                }

                if (state.checked.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.Bottom) {
                            SectionLabel("CHECKED · ${state.checked.size}", Modifier.weight(1f))
                            TextButton(onClick = actions.clearChecked) {
                                Text("Clear checked", color = HomeColors.Accent)
                            }
                        }
                    }
                    item { ListCard(state.checked, actions) }
                }

                item {
                    Text(
                        "Tap to tick · tap a name to edit · swipe left to delete",
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        fontSize = 13.sp,
                        color = HomeColors.InkMuted,
                    )
                }
            }
        }
    }
}

/** Already on this list and not ticked: no point suggesting it. */
private fun onThisList(suggestion: CatalogItem, storeName: String): Boolean =
    suggestion.item.needed &&
        suggestion.item.checkedAt == null &&
        (suggestion.storeNames?.split(", ")?.contains(storeName) ?: false)

@Composable
private fun AddField(query: String, storeName: String, onQueryChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add to $storeName") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            // Enter adds and keeps the keyboard open, so you can type the next line.
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add item", tint = HomeColors.Accent)
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<CatalogItem>, onPick: (String) -> Unit) {
    CardSurface {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = HomeColors.Hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Add ${suggestion.item.name}") {
                        onPick(suggestion.item.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = HomeColors.InkMuted)
                Text(suggestion.item.name, fontSize = 16.sp, color = HomeColors.Ink)
            }
        }
    }
}

@Composable
private fun ListCard(rows: List<ListItem>, actions: StoreListActions) {
    CardSurface {
        rows.forEachIndexed { index, row ->
            // key() ties each row's swipe state to its item, so deleting one row
            // doesn't hand its half-swiped state to the row that moves up.
            key(row.item.id) {
                if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = HomeColors.Hairline)
                SwipeToDelete(onDelete = { actions.remove(row.item) }) {
                    ItemRow(row, actions)
                }
            }
        }
    }
}

/** Swipe a row left to reveal "Delete"; let go past the threshold and it's removed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false, // only right-to-left, like most list apps
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HomeColors.Danger)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        // The row needs its own solid background, or the red would show through it.
        Box(Modifier.background(HomeColors.Card)) { content() }
    }
}

@Composable
private fun ItemRow(row: ListItem, actions: StoreListActions) {
    val item = row.item
    val checked = item.checkedAt != null
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) {
            IconButton(onClick = { actions.toggle(item) }) {
                CheckedCircle()
            }
        } else {
            CheckCircleButton(label = "Tick ${item.name}", onClick = { actions.toggle(item) })
        }
        // Tapping the name (not the circle) opens the edit dialog.
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Edit ${item.name}") { actions.edit(item) }
                .padding(vertical = 12.dp)
        ) {
            Text(
                item.name,
                fontSize = 17.sp,
                color = if (checked) HomeColors.InkMuted else HomeColors.Ink,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            item.quantity?.let { Text(it, fontSize = 13.sp, color = HomeColors.InkMuted) }
        }
        if (!checked) {
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
}

@Composable
private fun EditItemDialog(
    edit: EditItemState,
    onSave: (quantity: String, aliases: String, storeIds: Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    // Dialog-local draft state. remember(edit) resets it if a different item is opened.
    var quantity by remember(edit) { mutableStateOf(edit.item.quantity ?: "") }
    var aliases by remember(edit) { mutableStateOf(edit.item.aliases.replace(",", ", ")) }
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
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Also called") },
                    placeholder = { Text("dhania, cilantro") },
                    supportingText = { Text("Helps match lists shared from WhatsApp") },
                    singleLine = true,
                )
                Text("On these lists", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.InkMuted)
                edit.allStores.forEach { store ->
                    val isOn = store.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // toggleable makes the WHOLE row tappable, not just the tiny checkbox.
                            .toggleable(value = isOn, role = Role.Checkbox) { turnOn ->
                                selected = if (turnOn) selected + store.id else selected - store.id
                                showStoreError = false
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(checked = isOn, onCheckedChange = null)
                        ColorDot(store.colorHex)
                        Text(store.name, fontSize = 16.sp)
                    }
                }
                if (showStoreError) {
                    Text("Pick at least one list. To remove it, swipe it away instead.", fontSize = 13.sp, color = HomeColors.Urgent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected.isEmpty()) showStoreError = true else onSave(quantity, aliases, selected)
            }) { Text("Save", color = HomeColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HomeColors.InkMuted) }
        },
    )
}

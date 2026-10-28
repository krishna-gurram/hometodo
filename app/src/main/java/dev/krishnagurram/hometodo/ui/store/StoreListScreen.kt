package dev.krishnagurram.hometodo.ui.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.krishnagurram.hometodo.data.GotItem
import dev.krishnagurram.hometodo.data.Item
import dev.krishnagurram.hometodo.data.ListItem
import dev.krishnagurram.hometodo.domain.DueSuggestion
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.CheckCircleButton
import dev.krishnagurram.hometodo.ui.components.CheckedCircle
import dev.krishnagurram.hometodo.ui.components.ColorDot
import dev.krishnagurram.hometodo.ui.components.SectionLabel
import dev.krishnagurram.hometodo.ui.theme.HomeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StoreListRoute(db: AppDatabase, storeId: String, onBack: () -> Unit) {
    val vm: StoreListViewModel = viewModel { StoreListViewModel(db, storeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query = vm.query // Compose state: reading it here subscribes this composable to changes
    val results by vm.results.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val justBought by vm.justBought.collectAsStateWithLifecycle()
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
        query = query,
        results = results,
        suggestions = suggestions,
        justBought = justBought,
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
            setQuery = vm::onQueryChange,
            submitQuery = vm::submitQuery,
            addExisting = vm::addExisting,
            checkOff = vm::checkOff,
            undo = vm::undo,
            undoExpired = vm::undoExpired,
            putBack = vm::putBack,
            edit = vm::startEdit,
        ),
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

/** Every event this screen can send up, bundled so the parameter list stays readable. */
class StoreListActions(
    val back: () -> Unit,
    val share: () -> Unit,
    val setQuery: (String) -> Unit,
    val submitQuery: () -> Unit,
    val addExisting: (itemId: String) -> Unit,
    val checkOff: (Item) -> Unit,
    val undo: (JustBought) -> Unit,
    val undoExpired: () -> Unit,
    val putBack: (GotItem) -> Unit,
    val edit: (Item) -> Unit,
)

@Composable
fun StoreListScreen(
    state: StoreListState?,
    query: String,
    results: List<CatalogItem>,
    suggestions: List<DueSuggestion>,
    justBought: JustBought?,
    actions: StoreListActions,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(justBought) {
        val bought = justBought ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Got ${bought.itemName}", "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) actions.undo(bought) else actions.undoExpired()
    }

    Scaffold(
        containerColor = HomeColors.Ground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        val store = state?.store
        if (state != null && store != null) {
            val searching = query.isNotBlank()
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
                        Text("${state.items.size} to get", fontSize = 15.sp, color = HomeColors.InkMuted)
                    }
                }
                item {
                    SearchField(
                        query = query,
                        placeholder = "Add or find an item",
                        onQueryChange = actions.setQuery,
                        onSubmit = actions.submitQuery,
                    )
                }

                if (searching) {
                    // While typing, the box becomes search: your catalog, best match first.
                    item {
                        SearchResults(
                            query = query.trim(),
                            storeName = store.name,
                            results = results,
                            onAddExisting = actions.addExisting,
                            onAddNew = actions.submitQuery,
                        )
                    }
                } else {
                    if (suggestions.isNotEmpty()) {
                        item { SectionLabel("PROBABLY DUE") }
                        item { DueChips(suggestions, actions.addExisting) }
                    }
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
                                    NeededRow(row, actions.checkOff, actions.edit)
                                }
                            }
                        }
                    }
                    if (state.gotToday.isNotEmpty()) {
                        item { SectionLabel("GOT IT TODAY · ${state.gotToday.size}") }
                        // items(list) is LazyColumn's version of map(): one row per element.
                        items(state.gotToday, key = { it.purchaseId }) { got -> GotRow(got, actions.putBack) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            // Done adds it and keeps the keyboard open, so you can type the next one.
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        IconButton(onClick = onSubmit) {
            Icon(Icons.Filled.Add, contentDescription = "Add item", tint = HomeColors.Accent)
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    storeName: String,
    results: List<CatalogItem>,
    onAddExisting: (String) -> Unit,
    onAddNew: () -> Unit,
) {
    val exactMatch = results.any { it.item.name.equals(query, ignoreCase = true) }
    CardSurface {
        results.forEachIndexed { index, result ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = HomeColors.Hairline)
            val onThisList = result.item.needed &&
                (result.storeNames?.split(", ")?.contains(storeName) ?: false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(result.item.name, fontSize = 17.sp, color = HomeColors.Ink)
                    Text(describe(result), fontSize = 13.sp, color = HomeColors.InkMuted)
                }
                if (onThisList) {
                    Text(
                        "On list",
                        modifier = Modifier.padding(end = 12.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.Accent,
                    )
                } else {
                    IconButton(onClick = { onAddExisting(result.item.id) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add ${result.item.name}", tint = HomeColors.Ink)
                    }
                }
            }
        }
        if (!exactMatch) {
            if (results.isNotEmpty()) HorizontalDivider(color = HomeColors.Hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onAddNew)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = HomeColors.Accent)
                Text("Add “$query” as a new item", fontSize = 16.sp, color = HomeColors.Accent)
            }
        }
    }
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

private fun describe(result: CatalogItem): String {
    val parts = mutableListOf<String>()
    result.storeNames?.let { parts += it }
    if (result.timesBought > 0) {
        parts += "bought ${result.timesBought}×"
        result.lastBought?.let { parts += "last " + shortDate.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    }
    return parts.joinToString(" · ").ifEmpty { "Not bought yet" }
}

@Composable
private fun DueChips(suggestions: List<DueSuggestion>, onAdd: (String) -> Unit) {
    // LazyRow = horizontally scrolling list.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions, key = { it.itemId }) { suggestion ->
            Text(
                "+ ${suggestion.name}",
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .border(1.dp, HomeColors.Ring, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClickLabel = "Add ${suggestion.name}, usually every ${suggestion.everyDays} days") {
                        onAdd(suggestion.itemId)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 15.sp,
                color = HomeColors.Ink,
            )
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
    onSave: (quantity: String, aliases: String, storeIds: Set<String>) -> Unit,
    onRemove: () -> Unit,
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
                Text(boughtSummary(edit.purchaseTimes), fontSize = 13.sp, color = HomeColors.InkMuted)
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
                if (selected.isEmpty()) showStoreError = true else onSave(quantity, aliases, selected)
            }) { Text("Save", color = HomeColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HomeColors.InkMuted) }
        },
    )
}

private fun boughtSummary(times: List<Long>): String {
    if (times.isEmpty()) return "Never bought yet"
    val last = shortDate.format(Instant.ofEpochMilli(times.first()).atZone(ZoneId.systemDefault()))
    val count = if (times.size == 1) "Bought once" else "Bought ${times.size} times"
    return "$count · last $last"
}

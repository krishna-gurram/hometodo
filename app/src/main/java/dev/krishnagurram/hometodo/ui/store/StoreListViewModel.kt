package dev.krishnagurram.hometodo.ui.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.CatalogItem
import dev.krishnagurram.hometodo.data.Item
import dev.krishnagurram.hometodo.data.ListItem
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.data.now
import dev.krishnagurram.hometodo.domain.ShareFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoreListState(
    /** Null if the store was deleted while you were looking at it. */
    val store: Store?,
    /** Still to get, in the order you added them. */
    val toGet: List<ListItem>,
    /** Ticked, shown struck through at the bottom until "Clear checked". */
    val checked: List<ListItem>,
)

/** What the edit dialog needs: the item, every store, and which ones it's tagged to. */
data class EditItemState(
    val item: Item,
    val allStores: List<Store>,
    val taggedIds: Set<String>,
)

/**
 * One store's list, behaving like a list in your notes app:
 *   type + Enter     -> new line at the bottom
 *   tick             -> struck through, sinks to the bottom
 *   tick again       -> back to normal
 *   Clear checked    -> all struck items leave the list
 *   swipe left       -> gone (with Undo)
 *
 * Navigation gives each open list its own ViewModel, each with its own storeId.
 */
class StoreListViewModel(
    private val db: AppDatabase,
    private val storeId: String,
) : ViewModel() {

    private val items = db.itemDao()

    val state: StateFlow<StoreListState?> =
        combine(db.storeDao().observe(storeId), items.observeList(storeId)) { store, list ->
            StoreListState(
                store = store,
                toGet = list.filter { it.item.checkedAt == null },
                checked = list.filter { it.item.checkedAt != null },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- the add box, with quiet suggestions ----

    /**
     * The text in the add box. Compose state (not a StateFlow) on purpose: a text field
     * must see each keystroke immediately, and Flow collection can lag a frame behind,
     * which drops letters when you type fast. Google's guidance says the same.
     */
    var query by mutableStateOf("")
        private set

    /**
     * Things you've added before that match what you're typing. snapshotFlow turns the
     * Compose state into a Flow; flatMapLatest drops the previous search on each keystroke,
     * like cancelling a stale fetch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<CatalogItem>> =
        snapshotFlow { query }
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyList()) else items.search(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Not called setQuery: Kotlin already generates a hidden Java method named setQuery
    // for the `query` property above, and two methods with one signature won't compile.
    fun onQueryChange(text: String) {
        query = text
    }

    /** Enter or the + button: adds what you typed (reusing an item with that exact name). */
    fun add() {
        val name = query.trim()
        if (name.isEmpty()) return
        query = ""
        viewModelScope.launch { items.quickAdd(name, storeId) }
    }

    /** Tapping a suggestion. */
    fun addSuggestion(itemId: String) {
        query = ""
        viewModelScope.launch { items.addExistingToStore(itemId, storeId) }
    }

    // ---- tick, untick, clear ----

    fun toggle(item: Item) {
        viewModelScope.launch {
            if (item.checkedAt == null) items.check(item, storeId) else items.uncheck(item)
        }
    }

    fun clearChecked() {
        viewModelScope.launch { items.clearChecked(storeId, now()) }
    }

    // ---- swipe to delete, with undo ----

    private val _justRemoved = MutableStateFlow<Item?>(null)
    val justRemoved: StateFlow<Item?> = _justRemoved.asStateFlow()

    fun remove(item: Item) {
        viewModelScope.launch {
            items.removeFromList(item.id, now())
            _justRemoved.value = item
        }
    }

    /** Puts back the exact row as it was before the swipe: same place, same tick state. */
    fun undoRemove(item: Item) {
        _justRemoved.value = null
        viewModelScope.launch { items.upsert(item) }
    }

    fun undoExpired() {
        _justRemoved.value = null
    }

    // ---- edit dialog (tap an item's name) ----

    private val _editing = MutableStateFlow<EditItemState?>(null)
    val editing: StateFlow<EditItemState?> = _editing.asStateFlow()

    fun startEdit(item: Item) {
        viewModelScope.launch {
            _editing.value = EditItemState(
                item = item,
                allStores = db.storeDao().all(),
                taggedIds = items.storeIdsFor(item.id).toSet(),
            )
        }
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun saveEdit(quantity: String, aliases: String, storeIds: Set<String>) {
        val edit = _editing.value ?: return
        if (storeIds.isEmpty()) return // the dialog shows the error; never save an item with no store
        _editing.value = null
        // "Dhania, cilantro " -> "dhania,cilantro": the matcher compares lowercase, comma-separated.
        val cleanAliases = aliases.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        viewModelScope.launch {
            items.saveEdits(edit.item.id, quantity.trim().ifEmpty { null }, cleanAliases, storeIds.toList())
        }
    }

    // ---- share out ----

    /** Only what's still to get. Struck items are already in the cart. */
    fun shareText(): String? {
        val current = state.value ?: return null
        val store = current.store ?: return null
        return ShareFormatter.format(store.name, current.toGet.map { it.item.name to it.item.quantity })
    }
}

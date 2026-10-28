package dev.krishnagurram.hometodo.ui.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.CatalogItem
import dev.krishnagurram.hometodo.data.GotItem
import dev.krishnagurram.hometodo.data.Item
import dev.krishnagurram.hometodo.data.ListItem
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.data.now
import dev.krishnagurram.hometodo.domain.BuyAgain
import dev.krishnagurram.hometodo.domain.DueSuggestion
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
import java.time.LocalDate
import java.time.ZoneId

data class StoreListState(
    /** Null if the store was deleted while you were looking at it. */
    val store: Store?,
    val items: List<ListItem>,
    val gotToday: List<GotItem>,
)

data class JustBought(val itemName: String, val itemId: String, val purchaseId: String)

/** What the edit dialog needs: the item, every store, its tags, and when it was bought. */
data class EditItemState(
    val item: Item,
    val allStores: List<Store>,
    val taggedIds: Set<String>,
    val purchaseTimes: List<Long>,
)

/**
 * One ViewModel per open store list. Navigation gives each screen its own copy,
 * so opening Indian and then Costco creates two, each with its own storeId.
 */
class StoreListViewModel(
    private val db: AppDatabase,
    private val storeId: String,
) : ViewModel() {

    private val items = db.itemDao()

    private val startOfToday: Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Items bought within this window rank higher in search. Older ones sink but never vanish. */
    private val activeSince: Long = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000

    val state: StateFlow<StoreListState?> =
        combine(
            db.storeDao().observe(storeId),
            items.observeList(storeId),
            items.observeGotToday(storeId, startOfToday),
        ) { store, list, got -> StoreListState(store, list, got) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- search-first add box ----

    /**
     * The text in the add box. Compose state (not a StateFlow) on purpose: a text field
     * must see each keystroke immediately, and Flow collection can lag a frame behind,
     * which drops letters when you type fast. Google's guidance says the same.
     */
    var query by mutableStateOf("")
        private set

    /**
     * snapshotFlow turns that Compose state into a Flow. flatMapLatest: every time the
     * query changes, throw away the previous search and start a new live query.
     * Like cancelling a stale fetch when the user keeps typing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<CatalogItem>> =
        snapshotFlow { query }
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyList()) else items.search(q, activeSince) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Probably due" chips, recomputed whenever this store's purchase history changes. */
    val suggestions: StateFlow<List<DueSuggestion>> =
        items.observePurchaseTimes(storeId)
            .map { BuyAgain.probablyDue(it, System.currentTimeMillis()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Not called setQuery: Kotlin already generates a hidden Java method named setQuery
    // for the `query` property above, and two methods with one signature won't compile.
    fun onQueryChange(text: String) {
        query = text
    }

    /** Keyboard Done or "Add as new": reuses an item with that exact name if one exists. */
    fun submitQuery() {
        val name = query.trim()
        if (name.isEmpty()) return
        query = ""
        viewModelScope.launch { items.quickAdd(name, storeId) }
    }

    fun addExisting(itemId: String) {
        query = ""
        viewModelScope.launch { items.addExistingToStore(itemId, storeId) }
    }

    // ---- check off and undo ----

    private val _justBought = MutableStateFlow<JustBought?>(null)
    val justBought: StateFlow<JustBought?> = _justBought.asStateFlow()

    fun checkOff(item: Item) {
        viewModelScope.launch {
            val purchaseId = items.checkOff(item, storeId)
            _justBought.value = JustBought(item.name, item.id, purchaseId)
        }
    }

    fun undo(bought: JustBought) {
        _justBought.value = null
        viewModelScope.launch { items.undoCheckOff(bought.itemId, bought.purchaseId) }
    }

    fun undoExpired() {
        _justBought.value = null
    }

    /** Tapping something in "Got it": a mis-tap, so put it back and tear up the receipt. */
    fun putBack(got: GotItem) {
        viewModelScope.launch { items.undoCheckOff(got.item.id, got.purchaseId) }
    }

    // ---- edit dialog ----

    private val _editing = MutableStateFlow<EditItemState?>(null)
    val editing: StateFlow<EditItemState?> = _editing.asStateFlow()

    fun startEdit(item: Item) {
        viewModelScope.launch {
            _editing.value = EditItemState(
                item = item,
                allStores = db.storeDao().all(),
                taggedIds = items.storeIdsFor(item.id).toSet(),
                purchaseTimes = items.purchaseTimes(item.id),
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

    /** Off the list without buying it. No purchase row, so it won't count toward "probably due". */
    fun removeFromList() {
        val edit = _editing.value ?: return
        _editing.value = null
        viewModelScope.launch { items.setNeeded(edit.item.id, false, now()) }
    }

    // ---- share out ----

    fun shareText(): String? {
        val current = state.value ?: return null
        val store = current.store ?: return null
        return ShareFormatter.format(store.name, current.items.map { it.item.name to it.item.quantity })
    }
}

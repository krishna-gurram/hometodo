package dev.krishnagurram.hometodo.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.GotItem
import dev.krishnagurram.hometodo.data.Item
import dev.krishnagurram.hometodo.data.ListItem
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.data.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/** What the edit dialog needs: the item, every store, and which ones it's tagged to now. */
data class EditItemState(val item: Item, val allStores: List<Store>, val taggedIds: Set<String>)

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

    val state: StateFlow<StoreListState?> =
        combine(
            db.storeDao().observe(storeId),
            items.observeList(storeId),
            items.observeGotToday(storeId, startOfToday),
        ) { store, list, got -> StoreListState(store, list, got) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _justBought = MutableStateFlow<JustBought?>(null)
    val justBought: StateFlow<JustBought?> = _justBought.asStateFlow()

    private val _editing = MutableStateFlow<EditItemState?>(null)
    val editing: StateFlow<EditItemState?> = _editing.asStateFlow()

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { items.quickAdd(trimmed, storeId) }
    }

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

    fun startEdit(item: Item) {
        viewModelScope.launch {
            val stores = db.storeDao().all()
            val tagged = items.storeIdsFor(item.id).toSet()
            _editing.value = EditItemState(item, stores, tagged)
        }
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun saveEdit(quantity: String, storeIds: Set<String>) {
        val edit = _editing.value ?: return
        if (storeIds.isEmpty()) return // the dialog shows the error; never save an item with no store
        _editing.value = null
        viewModelScope.launch {
            items.saveEdits(edit.item.id, quantity.trim().ifEmpty { null }, storeIds.toList())
        }
    }

    /** Off the list without buying it. No purchase row, so it won't count toward "probably due". */
    fun removeFromList() {
        val edit = _editing.value ?: return
        _editing.value = null
        viewModelScope.launch { items.setNeeded(edit.item.id, false, now()) }
    }
}

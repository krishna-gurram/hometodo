package dev.krishnagurram.hometodo.ui.importlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.data.ImportWrite
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.domain.ItemMatcher
import dev.krishnagurram.hometodo.domain.ShareParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the shared message, after matching against your catalog. */
data class ImportRow(
    val key: Int,
    /** What she wrote: "dhania". */
    val written: String,
    val quantity: String?,
    /** The catalog item it matched (null = brand-new item). */
    val matchedId: String?,
    /** What it'll be called on your list: "Coriander" (or "dhania" if new). */
    val name: String,
    val storeIds: Set<String>,
    val include: Boolean = true,
)

data class ImportState(
    val originalText: String,
    val rows: List<ImportRow>,
    val stores: List<Store>,
)

class ImportViewModel(private val app: HomeTodoApp) : ViewModel() {

    private val _state = MutableStateFlow<ImportState?>(null)
    val state: StateFlow<ImportState?> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        // Take the shared text and clear the inbox, so it isn't imported twice.
        val text = app.incomingShare.value.orEmpty()
        app.incomingShare.value = null
        viewModelScope.launch { _state.value = build(text) }
    }

    private suspend fun build(text: String): ImportState {
        val items = app.db.itemDao()
        val stores = app.db.storeDao().all()
        val catalog = items.catalog()

        val rows = ShareParser.parse(text).mapIndexed { index, line ->
            val match = ItemMatcher.match(line.name, catalog)
            ImportRow(
                key = index,
                written = line.name,
                quantity = line.quantity,
                matchedId = match?.id,
                name = match?.name ?: line.name.replaceFirstChar { it.uppercase() },
                storeIds = match?.let { items.storeIdsFor(it.id).toSet() } ?: emptySet(),
            )
        }

        // New items default to the store most of the matched items belong to. A list that's
        // mostly Indian groceries probably wants the unknown ones at Indian too.
        val likelyStore = rows.flatMap { it.storeIds }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: stores.firstOrNull()?.id
        val withDefaults = rows.map { row ->
            if (row.storeIds.isEmpty() && likelyStore != null) row.copy(storeIds = setOf(likelyStore)) else row
        }
        return ImportState(text, withDefaults, stores)
    }

    private fun updateRow(key: Int, change: (ImportRow) -> ImportRow) {
        _state.update { s -> s?.copy(rows = s.rows.map { if (it.key == key) change(it) else it }) }
        _error.value = null
    }

    fun toggleInclude(key: Int) = updateRow(key) { it.copy(include = !it.include) }

    fun toggleStore(key: Int, storeId: String) = updateRow(key) { row ->
        row.copy(storeIds = if (storeId in row.storeIds) row.storeIds - storeId else row.storeIds + storeId)
    }

    fun apply() {
        val s = _state.value ?: return
        val chosen = s.rows.filter { it.include }
        if (chosen.isEmpty()) {
            _error.value = "Nothing selected to add"
            return
        }
        val missingStore = chosen.firstOrNull { it.storeIds.isEmpty() }
        if (missingStore != null) {
            _error.value = "Pick a store for ${missingStore.name}"
            return
        }
        viewModelScope.launch {
            app.db.itemDao().applyImport(
                chosen.map { ImportWrite(it.matchedId, it.name, it.quantity, it.storeIds.toList()) },
            )
            _finished.value = true
        }
    }
}

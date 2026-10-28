package dev.krishnagurram.hometodo.ui.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Store
import dev.krishnagurram.hometodo.data.now
import dev.krishnagurram.hometodo.domain.StoreRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The add/rename dialog. `existing == null` means "adding a new store". */
data class StoreDialog(val existing: Store?, val startColor: String)

/** The delete confirmation, with the items that would come off your list. */
data class DeleteRequest(val store: Store, val orphanNames: List<String>)

class ManageStoresViewModel(private val db: AppDatabase) : ViewModel() {

    private val storeDao = db.storeDao()

    val stores: StateFlow<List<Store>> =
        storeDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dialog = MutableStateFlow<StoreDialog?>(null)
    val dialog: StateFlow<StoreDialog?> = _dialog.asStateFlow()

    private val _deleteRequest = MutableStateFlow<DeleteRequest?>(null)
    val deleteRequest: StateFlow<DeleteRequest?> = _deleteRequest.asStateFlow()

    fun startAdd() {
        _dialog.value = StoreDialog(existing = null, startColor = StoreRules.nextColor(stores.value.map { it.colorHex }))
    }

    fun startEdit(store: Store) {
        _dialog.value = StoreDialog(existing = store, startColor = store.colorHex)
    }

    fun closeDialog() {
        _dialog.value = null
    }

    /** The dialog calls this on every keystroke to show or clear the error under the field. */
    fun nameError(name: String): String? =
        StoreRules.nameError(name, stores.value.map { it.name }, _dialog.value?.existing?.name)

    fun save(name: String, colorHex: String) {
        val dialog = _dialog.value ?: return
        if (nameError(name) != null) return
        val trimmed = name.trim()
        _dialog.value = null
        viewModelScope.launch {
            val existing = dialog.existing
            if (existing == null) {
                val nextOrder = (stores.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                storeDao.upsert(Store(name = trimmed, colorHex = colorHex, sortOrder = nextOrder))
            } else {
                storeDao.upsert(existing.copy(name = trimmed, colorHex = colorHex, updatedAt = now()))
            }
        }
    }

    /** delta = -1 moves up, +1 moves down. */
    fun move(store: Store, delta: Int) {
        val list = stores.value.toMutableList()
        val from = list.indexOfFirst { it.id == store.id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { storeDao.saveOrder(list.map { it.id }) }
    }

    fun requestDelete(store: Store) {
        viewModelScope.launch {
            val orphans = storeDao.neededItemsOnlyAt(store.id).map { it.name }
            _deleteRequest.value = DeleteRequest(store, orphans)
        }
    }

    fun cancelDelete() {
        _deleteRequest.value = null
    }

    fun confirmDelete() {
        val request = _deleteRequest.value ?: return
        _deleteRequest.value = null
        viewModelScope.launch { storeDao.deleteAndReset(request.store.id) }
    }
}

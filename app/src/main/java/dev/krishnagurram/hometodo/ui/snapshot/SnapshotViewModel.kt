package dev.krishnagurram.hometodo.ui.snapshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.SampleData
import dev.krishnagurram.hometodo.data.StoreWithCount
import dev.krishnagurram.hometodo.domain.ChoreRow
import dev.krishnagurram.hometodo.domain.ChoreSchedule
import dev.krishnagurram.hometodo.domain.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the snapshot screen draws. Immutable: a new copy arrives on every change. */
data class SnapshotState(
    val dateLabel: String,
    val today: List<ChoreRow>,
    val comingUp: List<ChoreRow>,
    val stores: List<StoreWithCount>,
) {
    val hasNoChores: Boolean get() = today.isEmpty() && comingUp.isEmpty()
}

/** What the Undo snackbar needs to put a chore back. */
data class JustDone(val previous: Chore, val completionId: String)

/**
 * A ViewModel holds screen state and survives configuration changes. On your Fold,
 * unfolding the phone destroys and recreates the Activity (and every composable).
 * The ViewModel lives through that, so nothing reloads and the Undo stays available.
 *
 * React mental model: this is a custom hook whose state lives outside the component
 * tree. The screen reads `state` and calls functions. It never touches the database.
 */
class SnapshotViewModel(
    private val db: AppDatabase,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val headerFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    // combine = "whenever EITHER live query emits, rebuild the state from the latest of both".
    // stateIn turns it into a StateFlow: always has a current value (null until the first load).
    val state: StateFlow<SnapshotState?> =
        combine(db.choreDao().observeOpen(), db.storeDao().observeWithCounts()) { chores, stores ->
            buildState(chores, stores, today())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _justDone = MutableStateFlow<JustDone?>(null)
    val justDone: StateFlow<JustDone?> = _justDone.asStateFlow()

    fun complete(chore: Chore) {
        viewModelScope.launch {
            val completionId = db.choreDao().complete(chore, today())
            _justDone.value = JustDone(chore, completionId)
        }
    }

    fun undo(done: JustDone) {
        _justDone.value = null
        viewModelScope.launch {
            db.choreDao().undoComplete(done.previous, done.completionId)
        }
    }

    fun undoExpired() {
        _justDone.value = null
    }

    fun loadSampleData() {
        viewModelScope.launch { SampleData.load(db, today()) }
    }

    private fun buildState(chores: List<Chore>, stores: List<StoreWithCount>, date: LocalDate): SnapshotState {
        val rows = chores.map { ChoreSchedule.classify(it, date) }
        return SnapshotState(
            dateLabel = headerFormat.format(date).uppercase(Locale.getDefault()),
            today = rows.filter { it.section == Section.TODAY },
            comingUp = rows.filter { it.section == Section.COMING_UP },
            stores = stores,
        )
    }
}

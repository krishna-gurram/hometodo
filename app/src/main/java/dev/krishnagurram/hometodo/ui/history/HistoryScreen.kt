package dev.krishnagurram.hometodo.ui.history

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.domain.HistoryBuilder
import dev.krishnagurram.hometodo.domain.HistoryDay
import dev.krishnagurram.hometodo.domain.HistoryEntry
import dev.krishnagurram.hometodo.domain.HistoryFilter
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.CheckedCircle
import dev.krishnagurram.hometodo.ui.components.Choice
import dev.krishnagurram.hometodo.ui.components.ColorDot
import dev.krishnagurram.hometodo.ui.components.SectionLabel
import dev.krishnagurram.hometodo.ui.theme.HomeColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Small enough that the ViewModel lives in the same file as its screen. */
class HistoryViewModel(db: AppDatabase) : ViewModel() {

    val filter = MutableStateFlow(HistoryFilter.ALL)

    val days: StateFlow<List<HistoryDay>> =
        combine(
            db.choreDao().observeCompletionHistory(),
            db.itemDao().observePurchaseHistory(),
            filter,
        ) { chores, purchases, f ->
            HistoryBuilder.build(chores, purchases, f, ZoneId.systemDefault(), LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun HistoryRoute(db: AppDatabase, onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel { HistoryViewModel(db) }
    val days by vm.days.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

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
                    "History",
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.Ink,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HistoryFilter.ALL to "All", HistoryFilter.CHORES to "Chores", HistoryFilter.GROCERIES to "Groceries")
                        .forEach { (value, label) ->
                            Choice(label, selected = filter == value, onClick = { vm.filter.value = value }, modifier = Modifier.weight(1f))
                        }
                }
            }
            if (days.isEmpty()) {
                item {
                    Text(
                        "Things you finish and buy show up here.",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 15.sp,
                        color = HomeColors.InkMuted,
                    )
                }
            }
            // Keys must be unique; labels like "Mon, Oct 27" can repeat across years, so use the position.
            days.forEachIndexed { dayIndex, day ->
                item(key = "label-$dayIndex") { SectionLabel(day.label.uppercase()) }
                item(key = "card-$dayIndex") {
                    CardSurface {
                        day.entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = HomeColors.Hairline)
                            // `when` over a sealed type: the compiler makes sure both kinds are handled.
                            when (entry) {
                                is HistoryEntry.ChoreEntry -> CompletedChoreRow(entry)
                                is HistoryEntry.TripEntry -> TripRow(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedChoreRow(entry: HistoryEntry.ChoreEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckedCircle()
        Text(entry.title, modifier = Modifier.weight(1f), fontSize = 16.sp, color = HomeColors.Ink)
        Text(entry.timeLabel, fontSize = 13.sp, color = HomeColors.InkMuted)
    }
}

@Composable
private fun TripRow(entry: HistoryEntry.TripEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            ColorDot(entry.colorHex ?: "#9AA0A6", size = 12.dp)
        }
        Column(Modifier.weight(1f)) {
            val count = entry.itemNames.size
            Text(
                "${entry.storeName} trip · $count ${if (count == 1) "item" else "items"}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.Ink,
            )
            val shown = entry.itemNames.take(4).joinToString(", ")
            val more = if (count > 4) ", +${count - 4}" else ""
            Text(shown + more, fontSize = 13.sp, color = HomeColors.InkMuted)
        }
    }
}

package dev.krishnagurram.hometodo.ui.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.StoreWithCount
import dev.krishnagurram.hometodo.domain.ChoreRow
import dev.krishnagurram.hometodo.ui.theme.HomeColors

// Two layers, a pattern you'll repeat for every screen:
//   SnapshotRoute  - knows about the ViewModel. Reads state, passes callbacks down.
//   SnapshotScreen - knows NOTHING about ViewModels or databases. Takes plain data and
//                    lambdas, like a presentational React component taking props.

@Composable
fun SnapshotRoute(db: AppDatabase) {
    val vm: SnapshotViewModel = viewModel { SnapshotViewModel(db) }
    val state by vm.state.collectAsStateWithLifecycle()
    val justDone by vm.justDone.collectAsStateWithLifecycle()

    SnapshotScreen(
        state = state,
        justDone = justDone,
        onComplete = vm::complete,
        onUndo = vm::undo,
        onUndoExpired = vm::undoExpired,
        onLoadSample = vm::loadSampleData,
    )
}

@Composable
fun SnapshotScreen(
    state: SnapshotState?,
    justDone: JustDone?,
    onComplete: (Chore) -> Unit,
    onUndo: (JustDone) -> Unit,
    onUndoExpired: () -> Unit,
    onLoadSample: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }

    // A side effect, like React's useEffect(() => {...}, [justDone]).
    // Runs whenever justDone changes. If it changes again mid-snackbar, this block is
    // cancelled and restarted with the new value.
    LaunchedEffect(justDone) {
        val done = justDone ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Done: ${done.previous.title}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo(done) else onUndoExpired()
    }

    Scaffold(
        containerColor = HomeColors.Ground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        // null = the very first load (a few milliseconds). Draw nothing until data arrives.
        if (state != null) {
            // LazyColumn only composes rows that are on screen, like a virtualized list in React.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Header(state.dateLabel) }

                item { SectionLabel("TODAY") }
                item {
                    when {
                        state.hasNoChores -> EmptyCard(onLoadSample)
                        state.today.isEmpty() -> QuietCard("Nothing due today.")
                        else -> ChoreCard(state.today, onComplete)
                    }
                }

                if (state.comingUp.isNotEmpty()) {
                    item { SectionLabel("COMING UP") }
                    item { ChoreCard(state.comingUp, onComplete) }
                }

                item { SectionLabel("LISTS") }
                item { StoreTiles(state.stores) }
            }
        }
    }
}

@Composable
private fun Header(dateLabel: String) {
    Column(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Text(dateLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HomeColors.InkMuted)
        Text("Today", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = HomeColors.InkMuted,
    )
}

/** The white rounded card that groups rows. Every card on this screen uses it. */
@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HomeColors.Card, RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
private fun ChoreCard(rows: List<ChoreRow>, onComplete: (Chore) -> Unit) {
    CardSurface {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = HomeColors.Hairline)
            ChoreRowItem(row, onComplete)
        }
    }
}

@Composable
private fun ChoreRowItem(row: ChoreRow, onComplete: (Chore) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // IconButton gives a 48dp touch target around the 26dp circle, plus button semantics.
        IconButton(
            onClick = { onComplete(row.chore) },
            modifier = Modifier.semantics { contentDescription = "Mark ${row.chore.title} done" },
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .border(2.dp, HomeColors.Ring, CircleShape)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(row.chore.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink)
            Text(
                row.whenLabel,
                fontSize = 13.sp,
                fontWeight = if (row.urgent) FontWeight.Medium else FontWeight.Normal,
                color = if (row.urgent) HomeColors.Urgent else HomeColors.InkMuted,
            )
        }
    }
}

@Composable
private fun QuietCard(message: String) {
    CardSurface {
        Text(message, modifier = Modifier.padding(20.dp), fontSize = 15.sp, color = HomeColors.InkMuted)
    }
}

@Composable
private fun EmptyCard(onLoadSample: () -> Unit) {
    CardSurface {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No chores yet", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink)
            Text(
                "Adding chores comes in a later milestone. Load sample data to see this screen working.",
                fontSize = 14.sp,
                color = HomeColors.InkMuted,
            )
            Button(
                onClick = onLoadSample,
                colors = ButtonDefaults.buttonColors(containerColor = HomeColors.Accent),
            ) { Text("Load sample data") }
        }
    }
}

@Composable
private fun StoreTiles(stores: List<StoreWithCount>) {
    // chunked(3) = rows of three tiles, so a 4th or 5th store you add later wraps neatly.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stores.chunked(3).forEach { rowStores ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStores.forEach { store -> StoreTile(store, Modifier.weight(1f)) }
                // Keep tile widths equal on a short last row.
                repeat(3 - rowStores.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StoreTile(store: StoreWithCount, modifier: Modifier) {
    Column(
        modifier
            .background(HomeColors.Card, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(parseHex(store.colorHex), CircleShape)
        )
        Text("${store.neededCount}", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
        Text(store.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink)
        Spacer(Modifier.height(2.dp))
    }
}

private fun parseHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

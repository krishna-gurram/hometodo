package dev.krishnagurram.hometodo.ui.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.StoreWithCount
import dev.krishnagurram.hometodo.domain.ChoreRow
import dev.krishnagurram.hometodo.ui.components.CardSurface
import dev.krishnagurram.hometodo.ui.components.CheckCircleButton
import dev.krishnagurram.hometodo.ui.components.ColorDot
import dev.krishnagurram.hometodo.ui.components.SectionLabel
import dev.krishnagurram.hometodo.ui.theme.HomeColors

// Two layers, a pattern every screen repeats:
//   SnapshotRoute  - knows about the ViewModel. Reads state, passes callbacks down.
//   SnapshotScreen - knows NOTHING about ViewModels or databases. Takes plain data and
//                    lambdas, like a presentational React component taking props.

/** Where the snapshot can send you. Grouped so the Route doesn't take seven lambdas. */
class SnapshotNav(
    val openStore: (storeId: String) -> Unit,
    val manageStores: () -> Unit,
    val openChore: (choreId: String) -> Unit,
    val newChore: () -> Unit,
    val history: () -> Unit,
)

@Composable
fun SnapshotRoute(db: AppDatabase, nav: SnapshotNav) {
    val vm: SnapshotViewModel = viewModel { SnapshotViewModel(db) }
    val state by vm.state.collectAsStateWithLifecycle()
    val justDone by vm.justDone.collectAsStateWithLifecycle()

    SnapshotScreen(
        state = state,
        justDone = justDone,
        onComplete = vm::complete,
        onUndo = vm::undo,
        onUndoExpired = vm::undoExpired,
        nav = nav,
    )
}

@Composable
fun SnapshotScreen(
    state: SnapshotState?,
    justDone: JustDone?,
    onComplete: (Chore) -> Unit,
    onUndo: (JustDone) -> Unit,
    onUndoExpired: () -> Unit,
    nav: SnapshotNav,
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = nav.newChore,
                containerColor = HomeColors.Ink,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Chore") },
            )
        },
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
                    // Extra room so the last tiles aren't hidden under the floating button.
                    bottom = insets.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Header(state.dateLabel, onHistory = nav.history) }

                item { SectionLabel("TODAY") }
                item {
                    when {
                        state.hasNoChores -> QuietCard("No chores yet. Tap Chore to add one.")
                        state.today.isEmpty() -> QuietCard("Nothing due today.")
                        else -> ChoreCard(state.today, state.photoCounts, onComplete, nav.openChore)
                    }
                }

                if (state.comingUp.isNotEmpty()) {
                    item { SectionLabel("COMING UP") }
                    item { ChoreCard(state.comingUp, state.photoCounts, onComplete, nav.openChore) }
                }

                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionLabel("LISTS", Modifier.weight(1f))
                        TextButton(onClick = nav.manageStores) { Text("Manage", color = HomeColors.Accent) }
                    }
                }
                item { StoreTiles(state.stores, nav.openStore) }
            }
        }
    }
}

@Composable
private fun Header(dateLabel: String, onHistory: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(dateLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HomeColors.InkMuted)
            Text("Today", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Filled.DateRange, contentDescription = "History", tint = HomeColors.Ink)
        }
    }
}

@Composable
private fun ChoreCard(
    rows: List<ChoreRow>,
    photoCounts: Map<String, Int>,
    onComplete: (Chore) -> Unit,
    onOpen: (String) -> Unit,
) {
    CardSurface {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = HomeColors.Hairline)
            ChoreRowItem(row, photoCounts[row.chore.id] ?: 0, onComplete, onOpen)
        }
    }
}

@Composable
private fun ChoreRowItem(row: ChoreRow, photoCount: Int, onComplete: (Chore) -> Unit, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircleButton(label = "Mark ${row.chore.title} done", onClick = { onComplete(row.chore) })
        // The circle completes; the rest of the row opens the chore.
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Open ${row.chore.title}") { onOpen(row.chore.id) }
                .padding(vertical = 12.dp)
        ) {
            Text(row.chore.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink)
            val photos = when (photoCount) {
                0 -> ""
                1 -> " · 1 photo"
                else -> " · $photoCount photos"
            }
            Text(
                row.whenLabel + photos,
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
private fun StoreTiles(stores: List<StoreWithCount>, onOpenStore: (String) -> Unit) {
    // chunked(3) = rows of three tiles, so a 4th or 5th store wraps onto a new row.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stores.chunked(3).forEach { rowStores ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStores.forEach { store ->
                    StoreTile(store, onClick = { onOpenStore(store.id) }, modifier = Modifier.weight(1f))
                }
                // Keep tile widths equal on a short last row.
                repeat(3 - rowStores.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StoreTile(store: StoreWithCount, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .background(HomeColors.Card, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${store.name} list", onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorDot(store.colorHex)
        Text("${store.neededCount}", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.Ink)
        Text(store.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeColors.Ink, maxLines = 1)
    }
}

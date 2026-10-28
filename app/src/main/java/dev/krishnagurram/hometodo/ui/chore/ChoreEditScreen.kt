package dev.krishnagurram.hometodo.ui.chore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.data.ChoreWhen
import dev.krishnagurram.hometodo.data.Repeat
import dev.krishnagurram.hometodo.ui.components.BackBar
import dev.krishnagurram.hometodo.ui.components.Choice
import dev.krishnagurram.hometodo.ui.components.SectionLabel
import dev.krishnagurram.hometodo.ui.theme.HomeColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ChoreEditRoute(app: HomeTodoApp, choreId: String?, onClose: () -> Unit) {
    // key = choreId so "new" and each existing chore get separate ViewModels.
    val vm: ChoreEditViewModel = viewModel(key = choreId ?: "new") { ChoreEditViewModel(app, choreId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) { if (finished) onClose() }

    // Activity-result launchers: Android's way to "call another app and get an answer back",
    // like awaiting a popup. The lambda runs when the gallery or camera returns.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris -> if (uris.isNotEmpty()) vm.addPhotos(uris) }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> vm.onCameraResult(success) }

    val d = draft ?: return // still loading (a few milliseconds)

    ChoreEditScreen(
        draft = d,
        isNew = vm.isNew,
        error = error,
        onEdit = vm::edit,
        onPickPhotos = {
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = { takePhoto.launch(vm.prepareCamera()) },
        onRemovePhoto = vm::removePhoto,
        onSave = vm::save,
        onMarkDone = vm::markDone,
        onDelete = vm::delete,
        onClose = onClose,
    )
}

@Composable
fun ChoreEditScreen(
    draft: ChoreDraft,
    isNew: Boolean,
    error: String?,
    onEdit: ((ChoreDraft) -> ChoreDraft) -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (PhotoDraft) -> Unit,
    onSave: () -> Unit,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoDraft?>(null) }

    Scaffold(containerColor = HomeColors.Ground) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                BackBar(onClose) {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete chore", tint = HomeColors.InkMuted)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { text -> onEdit { it.copy(title = text) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isNew) "New chore" else "Chore") },
                    placeholder = { Text("Cancel trial subscription") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            item { SectionLabel("WHEN") }
            item {
                ChoiceRow(
                    options = listOf(ChoreWhen.WEEKNIGHT to "Weeknight", ChoreWhen.WEEKEND to "Weekend", ChoreWhen.DATE to "By a date"),
                    selected = draft.whenType,
                    onSelect = { choice -> onEdit { it.copy(whenType = choice) } },
                )
            }
            item { DueDatePicker(draft.due, onPick = { date -> onEdit { it.copy(due = date) } }) }

            item { SectionLabel("REPEAT") }
            item {
                ChoiceRow(
                    options = listOf(
                        Repeat.NONE to "Never",
                        Repeat.WEEKLY to "Weekly",
                        Repeat.BIWEEKLY to "2 weeks",
                        Repeat.MONTHLY to "Monthly",
                    ),
                    selected = draft.repeat,
                    onSelect = { choice -> onEdit { it.copy(repeat = choice) } },
                )
            }

            item { SectionLabel("NOTE") }
            item {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { text -> onEdit { it.copy(note = text) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("UPS drop-off, no box needed") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            item { SectionLabel("PHOTOS · ${draft.photos.size}") }
            item { PhotoGrid(draft.photos, onView = { viewing = it }, onRemove = onRemovePhoto) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("Camera") }
                    OutlinedButton(onClick = onPickPhotos, modifier = Modifier.weight(1f)) { Text("Gallery") }
                }
            }

            if (error != null) {
                item { Text(error, color = HomeColors.Urgent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
            }
            item {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeColors.Accent),
                ) { Text("Save", fontSize = 16.sp) }
            }
            if (!isNew) {
                item {
                    OutlinedButton(onClick = onMarkDone, modifier = Modifier.fillMaxWidth()) { Text("Mark done") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this chore?") },
            text = { Text("Its photos and its history go with it. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = HomeColors.Urgent) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    viewing?.let { photo ->
        // Full-screen-ish viewer: tap anywhere to close.
        Dialog(onDismissRequest = { viewing = null }) {
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Close photo") { viewing = null },
            )
        }
    }
}

/** A row of Choice pills for an enum. <T> makes it work for ChoreWhen and Repeat alike. */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            Choice(label, selected = value == selected, onClick = { onSelect(value) }, modifier = Modifier.weight(1f))
        }
    }
}

private val dueFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class) // the Material 3 date picker is still marked experimental
@Composable
private fun DueDatePicker(due: LocalDate?, onPick: (LocalDate?) -> Unit) {
    var showCalendar by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val quick = listOf(
            "Today" to today,
            "Tomorrow" to today.plusDays(1),
            "1 week" to today.plusWeeks(1),
            "2 weeks" to today.plusWeeks(2),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { (label, date) ->
                Choice(label, selected = due == date, onClick = { onPick(date) }, modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (due == null) "No date" else {
                    val days = ChronoUnit.DAYS.between(today, due)
                    "By ${dueFormat.format(due)}" + if (days > 0) " · $days days" else ""
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                fontSize = 15.sp,
                color = HomeColors.Ink,
            )
            TextButton(onClick = { showCalendar = true }) { Text("Pick date", color = HomeColors.Accent) }
            if (due != null) {
                TextButton(onClick = { onPick(null) }) { Text("Clear", color = HomeColors.InkMuted) }
            }
        }
    }

    if (showCalendar) {
        // The picker speaks UTC midnight milliseconds. Converting with the phone's time zone
        // instead would shift the date by a day for anyone west of London (that's you).
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showCalendar = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<PhotoDraft>, onView: (PhotoDraft) -> Unit, onRemove: (PhotoDraft) -> Unit) {
    if (photos.isEmpty()) {
        Text(
            "QR codes, labels, screenshots: anything you'll need when doing this.",
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 13.sp,
            color = HomeColors.InkMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { photo ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(3f / 4f)
                    ) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = "Chore photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Image, onClickLabel = "View photo") { onView(photo) },
                        )
                        IconButton(
                            onClick = { onRemove(photo) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(Color(0x99000000), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

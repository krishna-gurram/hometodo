package dev.krishnagurram.hometodo.ui.chore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.ChorePhoto
import dev.krishnagurram.hometodo.data.ChoreWhen
import dev.krishnagurram.hometodo.data.Repeat
import dev.krishnagurram.hometodo.data.newId
import dev.krishnagurram.hometodo.data.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** A photo on the screen. isNew = picked during this edit, not saved to the database yet. */
data class PhotoDraft(val id: String, val filePath: String, val isNew: Boolean)

/**
 * The form, as one immutable value. Every edit makes a new copy (`draft.copy(title = ...)`),
 * like setState with an object spread in React.
 */
data class ChoreDraft(
    val id: String,
    val title: String = "",
    val note: String = "",
    val whenType: ChoreWhen = ChoreWhen.WEEKNIGHT,
    val due: LocalDate? = null,
    val repeat: Repeat = Repeat.NONE,
    val photos: List<PhotoDraft> = emptyList(),
)

/** Shared by "new chore" (choreId = null) and "edit chore". */
class ChoreEditViewModel(
    private val app: HomeTodoApp,
    choreId: String?,
) : ViewModel() {

    private val chores = app.db.choreDao()

    /** The chore as it was loaded, or null when creating. Needed for Mark done and Delete. */
    private var original: Chore? = null

    private val _draft = MutableStateFlow<ChoreDraft?>(null)
    val draft: StateFlow<ChoreDraft?> = _draft.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Flips to true when the screen should close (saved, done, or deleted). */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val isNew: Boolean = choreId == null

    /** Existing photos you removed in this edit. Deleted for real only when you Save. */
    private val removedPhotos = mutableListOf<PhotoDraft>()

    /** The file the camera is currently writing into, until it reports success or failure. */
    private var pendingCamera: File? = null

    /** Set after a successful Save, so onCleared knows new photos now belong to the chore. */
    private var saved = false

    init {
        if (choreId == null) {
            _draft.value = ChoreDraft(id = newId())
        } else {
            viewModelScope.launch {
                val chore = chores.byId(choreId)
                if (chore == null) {
                    _finished.value = true // deleted elsewhere; nothing to edit
                    return@launch
                }
                original = chore
                _draft.value = ChoreDraft(
                    id = chore.id,
                    title = chore.title,
                    note = chore.note,
                    whenType = chore.whenType,
                    due = chore.dueEpochDay?.let(LocalDate::ofEpochDay),
                    repeat = chore.repeat,
                    photos = chores.photos(chore.id).map { PhotoDraft(it.id, it.filePath, isNew = false) },
                )
            }
        }
    }

    /** One function for every field: the screen passes "how to change the draft". */
    fun edit(change: (ChoreDraft) -> ChoreDraft) {
        _draft.update { current -> current?.let(change) }
        _error.value = null
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val added = uris.map { uri ->
                val file = app.photos.importFrom(uri)
                PhotoDraft(newId(), file.absolutePath, isNew = true)
            }
            edit { it.copy(photos = it.photos + added) }
        }
    }

    /** Returns where the camera should save; the screen hands this Uri to the camera app. */
    fun prepareCamera(): Uri {
        val (file, uri) = app.photos.newCameraTarget()
        pendingCamera = file
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val file = pendingCamera ?: return
        pendingCamera = null
        if (success && file.length() > 0) {
            edit { it.copy(photos = it.photos + PhotoDraft(newId(), file.absolutePath, isNew = true)) }
        } else {
            file.delete() // you backed out of the camera
        }
    }

    fun removePhoto(photo: PhotoDraft) {
        edit { it.copy(photos = it.photos - photo) }
        if (photo.isNew) app.photos.delete(photo.filePath) else removedPhotos += photo
    }

    fun save() {
        val d = _draft.value ?: return
        val problem = validate(d)
        if (problem != null) {
            _error.value = problem
            return
        }
        val chore = Chore(
            id = d.id,
            title = d.title.trim(),
            note = d.note.trim(),
            whenType = d.whenType,
            dueEpochDay = d.due?.toEpochDay(),
            repeat = d.repeat,
            completedAt = original?.completedAt,
            updatedAt = now(),
        )
        val newPhotos = d.photos.filter { it.isNew }.map { ChorePhoto(id = it.id, choreId = d.id, filePath = it.filePath) }
        viewModelScope.launch {
            chores.save(chore, newPhotos, removedPhotos.map { it.id })
            removedPhotos.forEach { app.photos.delete(it.filePath) }
            removedPhotos.clear()
            saved = true
            _finished.value = true
        }
    }

    fun markDone() {
        val chore = original ?: return
        viewModelScope.launch {
            chores.complete(chore, LocalDate.now())
            _finished.value = true
        }
    }

    fun delete() {
        val chore = original ?: return
        viewModelScope.launch {
            val photoFiles = chores.photos(chore.id).map { it.filePath }
            chores.delete(chore.id) // photo rows and completions go with it (CASCADE)
            photoFiles.forEach { app.photos.delete(it) }
            _finished.value = true
        }
    }

    /**
     * Called when this screen leaves for good (Back, Cancel, or after Save). If you
     * never saved, photos picked during this edit are orphans on disk: clean them up.
     */
    override fun onCleared() {
        if (!saved) {
            _draft.value?.photos?.filter { it.isNew }?.forEach { app.photos.delete(it.filePath) }
        }
    }

    companion object {
        /** Pure check, kept separate so it's easy to read (and to test later). */
        fun validate(d: ChoreDraft): String? = when {
            d.title.isBlank() -> "Give the chore a name"
            d.whenType == ChoreWhen.DATE && d.due == null -> "Pick a date"
            d.repeat != Repeat.NONE && d.due == null -> "A repeating chore needs a first date"
            else -> null
        }
    }
}

package dev.krishnagurram.hometodo.photos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.krishnagurram.hometodo.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Chore photos live in the app's PRIVATE storage (filesDir/photos), not the gallery.
 * We copy every picked image in. If we only kept a pointer to your gallery photo,
 * deleting the screenshot from Photos would silently break the chore.
 *
 * Bonus: private storage is deleted with the app, and no storage permission is needed.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Copies a gallery/screenshot pick into private storage. IO thread: files are slow. */
    suspend fun importFrom(uri: Uri): File = withContext(Dispatchers.IO) {
        val file = File(dir, "${newId()}.jpg")
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Can't read $uri" }
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        file
    }

    /**
     * For the camera: an empty file plus a content:// address the camera app may write to.
     * FileProvider is what lets another app (the camera) write into our private folder,
     * for this one file only.
     */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(dir, "${newId()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        return file to uri
    }

    fun delete(path: String) {
        File(path).delete()
    }
}

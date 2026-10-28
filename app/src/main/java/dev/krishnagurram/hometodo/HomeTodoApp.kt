package dev.krishnagurram.hometodo

import android.app.Application
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.photos.PhotoStore
import dev.krishnagurram.hometodo.reminders.Reminders
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lives as long as the app process. The one place shared things are created.
 *
 * This is manual dependency injection: no Hilt or Dagger. Screens reach these through
 * `(application as HomeTodoApp)`. With a handful of singletons, that's plenty.
 */
class HomeTodoApp : Application() {

    // `by lazy` = build it on first use, then reuse the same instance (a thread-safe singleton).
    val db: AppDatabase by lazy { AppDatabase.build(this) }

    val photos: PhotoStore by lazy { PhotoStore(this) }

    /**
     * Text shared into the app from WhatsApp, waiting to be reviewed. MainActivity puts
     * it here; the navigation watches it and opens the import screen.
     */
    val incomingShare = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.scheduleNext(this)
    }
}

package dev.krishnagurram.hometodo

import android.app.Application
import dev.krishnagurram.hometodo.data.AppDatabase

/**
 * Lives as long as the app process. The one place we create the database.
 *
 * This is manual dependency injection: no Hilt or Dagger yet. Screens reach the
 * database through `(application as HomeTodoApp).db`. Once there are several
 * screens, we'll decide together whether that's painful enough to justify Hilt.
 */
class HomeTodoApp : Application() {

    // `by lazy` = build it on first use, then reuse the same instance (a thread-safe singleton).
    val db: AppDatabase by lazy { AppDatabase.build(this) }
}

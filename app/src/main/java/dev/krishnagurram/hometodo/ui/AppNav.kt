package dev.krishnagurram.hometodo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.ui.chore.ChoreEditRoute
import dev.krishnagurram.hometodo.ui.history.HistoryRoute
import dev.krishnagurram.hometodo.ui.importlist.ImportRoute
import dev.krishnagurram.hometodo.ui.snapshot.SnapshotNav
import dev.krishnagurram.hometodo.ui.snapshot.SnapshotRoute
import dev.krishnagurram.hometodo.ui.store.StoreListRoute
import dev.krishnagurram.hometodo.ui.stores.ManageStoresRoute

/**
 * Every screen in the app, by route. Same idea as React Router:
 *   "snapshot"          -> <Route path="/" />
 *   "store/{storeId}"   -> <Route path="/store/:storeId" />
 *   "stores"            -> <Route path="/stores" />
 *   "chore/{choreId}"   -> <Route path="/chore/:choreId" />   ("new" = create)
 *   "import"            -> <Route path="/import" />
 *   "history"           -> <Route path="/history" />
 *
 * navigate("store/abc") pushes a screen; popBackStack() is the back button.
 * Android's own back gesture works automatically.
 */
@Composable
fun AppNav(app: HomeTodoApp) {
    val nav = rememberNavController()
    val db = app.db

    // Something shared in from WhatsApp? Open the import screen on top of whatever is showing.
    val incoming by app.incomingShare.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming != null) nav.navigate("import")
    }

    NavHost(navController = nav, startDestination = "snapshot") {
        composable("snapshot") {
            SnapshotRoute(
                db = db,
                nav = SnapshotNav(
                    openStore = { storeId -> nav.navigate("store/$storeId") },
                    manageStores = { nav.navigate("stores") },
                    openChore = { choreId -> nav.navigate("chore/$choreId") },
                    newChore = { nav.navigate("chore/new") },
                    history = { nav.navigate("history") },
                ),
            )
        }
        composable("store/{storeId}") { entry ->
            // Like useParams() in React Router.
            val storeId = requireNotNull(entry.arguments?.getString("storeId"))
            StoreListRoute(db = db, storeId = storeId, onBack = { nav.popBackStack() })
        }
        composable("stores") {
            ManageStoresRoute(db = db, onBack = { nav.popBackStack() })
        }
        composable("chore/{choreId}") { entry ->
            val raw = requireNotNull(entry.arguments?.getString("choreId"))
            ChoreEditRoute(app = app, choreId = raw.takeIf { it != "new" }, onClose = { nav.popBackStack() })
        }
        composable("import") {
            ImportRoute(app = app, onClose = { nav.popBackStack() })
        }
        composable("history") {
            HistoryRoute(db = db, onBack = { nav.popBackStack() })
        }
    }
}

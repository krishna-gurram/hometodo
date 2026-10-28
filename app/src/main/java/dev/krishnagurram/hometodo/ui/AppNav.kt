package dev.krishnagurram.hometodo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.krishnagurram.hometodo.data.AppDatabase
import dev.krishnagurram.hometodo.ui.snapshot.SnapshotRoute
import dev.krishnagurram.hometodo.ui.store.StoreListRoute
import dev.krishnagurram.hometodo.ui.stores.ManageStoresRoute

/**
 * Every screen in the app, by route. Same idea as React Router:
 *   "snapshot"          -> <Route path="/" />
 *   "store/{storeId}"   -> <Route path="/store/:storeId" />
 *   "stores"            -> <Route path="/stores" />
 *
 * navigate("store/abc") pushes a screen; popBackStack() is the back button.
 * Android's own back gesture works automatically.
 */
@Composable
fun AppNav(db: AppDatabase) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "snapshot") {
        composable("snapshot") {
            SnapshotRoute(
                db = db,
                onOpenStore = { storeId -> nav.navigate("store/$storeId") },
                onManageStores = { nav.navigate("stores") },
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
    }
}

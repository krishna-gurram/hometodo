# Lesson 1: Skeleton and data model

## Run it

1. Open the `hometodo` folder in Android Studio (File > Open). Let it sync Gradle. The first sync downloads a lot and takes a few minutes.
2. On the Fold: Settings > About phone > Software information, then tap Build number 7 times. Back in Settings > Developer options, turn on Wireless debugging.
3. In Android Studio, pair the phone (Device Manager > Pair using Wi-Fi) and press Run.

You should see three stores with "0 to get." Tap the button: the Indian count goes up, and nothing in the code told the screen to refresh.

## The map from what you know

| Android / Kotlin | What you already know |
|---|---|
| `libs.versions.toml` | `package.json` dependency versions |
| Gradle + `build.gradle.kts` | Maven `pom.xml`, written in Kotlin |
| `@Entity`, `@Dao` (Room) | JPA entities, Spring Data repositories |
| KSP | Annotation processing, like Lombok/MapStruct |
| `@Composable` function | React function component |
| `Flow` + `collectAsStateWithLifecycle` | an RxJS observable feeding `useState` |
| `suspend fun` | an `async` function; `scope.launch {}` is calling it without awaiting |
| `Application` class | the app's root singleton, like a Spring context with one bean |
| `AndroidManifest.xml` | deployment descriptor: entry points and permissions |

## The data model in one picture

```
stores ──< item_stores >── items ──< purchases
chores ──< chore_photos
chores ──< chore_completions
```

Read `data/Entities.kt` top to bottom. Every design decision has a comment.

## Break it on purpose

Each of these teaches you something about where errors show up. Undo each one afterwards.

1. In `Daos.kt`, change `s.colorHex` to `s.color` in the store query and build. Notice it fails at **compile time**, with the SQL error. Compare that with how a typo in a JPA `@Query` string usually surfaces.
2. Add `<uses-permission android:name="android.permission.INTERNET" />` to the manifest, then delete it again. Nothing visible changes. Why is leaving it out still worth it? (Hint: think about a library you add in month six.)
3. Add a new field to `Store`, say `val emoji: String = ""`, and run the app on the phone that already has the database. It crashes. Read the error. That's Room telling you a schema change needs a migration. Uninstall the app to get past it for now; we'll do proper migrations once real data exists.

## Questions to think about before lesson 2

1. `Item` has `needed: Boolean`, and there's also a `purchases` table. Why not just store `lastBoughtAt` on the item and skip the table? What would "probably due" lose?
2. `checkOff` is a `@Transaction`. Describe a concrete moment at Costco where not having it would leave your data wrong.
3. Quantity is free text, not a number. What does that make easy, and what does it make impossible later?
4. In `StoreDao.deleteAndReset`, swap the two lines so `deleteById` runs first. The code still compiles. What goes wrong? (Hint: what does `CASCADE` remove before `resetItemsOnlyAt` gets to look?)

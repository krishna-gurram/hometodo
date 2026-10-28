# Lesson 2: The snapshot screen

## Try it first

Install the new APK. You'll see an empty Today card. Tap **Load sample data**, then:

1. Tap the circle next to "Return headphones." It disappears and an Undo bar shows at the bottom. Tap Undo. It comes back.
2. Tap the circle next to "Trash bins out." It doesn't disappear. It moves to next week. Why? Look at its `repeat`.
3. Unfold your Fold while the Undo bar is showing. The bar survives. That's the ViewModel's job (more below).

## How data flows (one direction only)

```
Room tables
   │  live queries (Flow)
   ▼
SnapshotViewModel  ── combine(chores, stores) ──►  SnapshotState
   ▲                                                   │
   │ vm.complete(chore)                                │ collectAsStateWithLifecycle
   │                                                   ▼
   └──────────────── tap ◄────────────────────  SnapshotScreen (draws state)
```

The screen never writes to the database and never refreshes itself. It sends an event up (`onComplete`), the ViewModel writes to Room, Room's live query emits, a new `SnapshotState` flows down, and Compose redraws. Same idea as Redux or React's "state down, events up."

## The files, in reading order

1. `domain/ChoreSchedule.kt`: pure logic. Decides Today vs Coming up, the label, and the next date for repeats. No Android code.
2. `test/.../ChoreScheduleTest.kt`: JUnit tests for that logic. CI runs them on every push. Read the test names first; they're sentences.
3. `ui/snapshot/SnapshotViewModel.kt`: combines two live queries into one `SnapshotState`. Handles complete and undo.
4. `ui/snapshot/SnapshotScreen.kt`: `SnapshotRoute` connects to the ViewModel; `SnapshotScreen` only draws.
5. `data/Daos.kt`: look at `ChoreDao.complete` and `undoComplete`.

## New Kotlin and Compose words

| Word | Meaning | You know it as |
|---|---|---|
| `ViewModel` | Holds screen state; survives rotation and unfolding | A custom hook whose state lives outside the component |
| `StateFlow` | A Flow that always has a current value | `useState` you can subscribe to |
| `combine(a, b)` | Re-run when either source emits | `useMemo` with two dependencies |
| `LaunchedEffect(key)` | Side effect that re-runs when `key` changes | `useEffect(fn, [key])` |
| `LazyColumn` | Scrolling list that only builds visible rows | A virtualized list |
| `Modifier` | Size, padding, background, clicks, chained in order | Inline styles plus event handlers |
| `vm::complete` | A reference to a function | `this.complete.bind(this)` in JS, `vm::complete` in Java too |

## Why "today" is a parameter

`ChoreSchedule.classify(chore, today)` takes the date instead of calling `LocalDate.now()`. That's what makes the tests possible: a test can say "pretend it's Saturday." If the function read the clock itself, a test would pass on weekdays and fail on weekends.

## Questions before lesson 3

1. Leave the app open on the snapshot overnight. At 12:01 am, does "Tonight" become correct for the new day? Trace `today()` in the ViewModel and find out when it's actually called.
2. `undoComplete` puts back `previous`, the chore exactly as it was before you tapped. What if you'd edited that chore's title in the 4 seconds before tapping Undo? (Not possible today, but it will be in milestone 5.)
3. Read the last test. A rent-style chore due on the 31st drifts to the 28th forever after February. Is that acceptable for your chores, or would you fix it? What would you store differently to fix it?

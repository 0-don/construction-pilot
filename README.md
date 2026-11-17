# Construction Pilot

Shows what to click next, and when, while you train Construction with the hold 1 method: mahogany tables or gnome benches in your house, with a servant fetching planks.

You still do every click. The plugin only draws outlines and labels, and can reorder the right click menu so the right option is on left click.

## What it shows

- **The space to click now.** An empty space to build on (green) or furniture to remove (orange). Both halves of a garden bench light up together.
- **When to remove.** After a build, a countdown runs until the furniture appears, so the remove lands on the first possible tick.
- **What comes next.** A faint outline marks the step after the current one, so your mouse can get there early.
- **The butler.** When to send him, when to take planks from him, how many he holds and how long until he is back (on your character's head). If he wandered off, the bell-pull lights up.
- **Setup check.** An orange box if you are missing a hammer or saw, and optional grey tips (Construction QOL key, demon butler, money bag, fetch amount in whole builds).

The send point is calculated, not fixed: it uses your free inventory slots, how many planks the servant brings, his measured trip time and your measured building speed, so he arrives as you run low. It works with any servant and any fetch amount.

## Smart left click

When on, left click does the step that is due: Talk-to on the butler even when he stands on a bench, Build or Remove on the bench even when the butler is in the way. It only changes the order of the normal menu options. It never adds or removes options and never clicks for you.

## Settings

Every setting has a description shown on hover. Leave **Send butler at** on 0 to let the plugin work out the moment. **Tick log** (Advanced) writes every click and state change with its game tick to the client log, for timing analysis or bug reports.

## How it works (for developers)

The code is split so the decision logic can be tested without a game client:

| File | Role |
| --- | --- |
| `ConstructionPilotPlugin` | Routes game events to the trackers in a fixed order, builds a `Snapshot` each tick and asks `Policy` for a `Decision` |
| `House` | Build spaces, furniture and bell-pull, kept from spawn and despawn events (no scene scans) |
| `Butler` | The servant: trips, hand-overs, what he holds, wages, his dialogue |
| `BuildTimer` | Build and remove clicks with their tick timing, and your building speed |
| `Snapshot` | Plain data for one tick |
| `Policy` | Pure function from `Snapshot` to `Decision`; covered by `PolicyTest` |
| `SceneOverlay`, `StatsPanel`, `SetupPanel` | Only draw the current `Decision` and tracker state |
| `SmartLeftClick` | Reorders the menu entries for the current step |
| `SetupCheck` | Missing tools and optional speed tips |

Timings measured in game with the tick log:

- Remove click: the furniture is gone one tick later.
- Build click: only opens the build menu. Planks are taken about one tick after the menu closes, and the furniture appears one tick after that.
- The servant hands over only when you are free (not in a menu). Continuing his return dialogue makes him walk off; clicking the next space keeps him next to you.

To run a development client: `./gradlew run`. Tests: `./gradlew test`.

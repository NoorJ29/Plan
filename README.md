# Borealis — day planner for Android

Plan today and tomorrow, tick things off, and see what slipped.

## What's inside

- **Plan** — Today / Tomorrow / any day. A "day horizon" plots timed tasks on a line through the day; the aurora behind the app brightens as you finish.
- **Couldn't finish** — unfinished one-off tasks from earlier days wait on Today with *Do today*, *Tomorrow* or *Let go*. Optional auto-carry in Settings.
- **Tasks** — notes, time, priority (low / medium / high), steps (subtasks; ticking the last one completes the task), repeat (daily, weekdays, weekly on chosen days, monthly).
- **Quick typing** — "Gym tomorrow 6pm !!" fills in day, time and priority.
- **Alerts** — Reminder (one notification) or Alarm (rings like a clock alarm, full-screen over the lock screen, Snooze / Done / Stop). Survives reboots.
- **Recap** — last 7 days: done vs couldn't finish, per-day breakdown, anything still waiting.
- **Widgets** — Today checklist (tick tasks, flip to tomorrow, add), Progress orb, Next up (sized for the lock screen). Five themes and adjustable glass in the Widgets tab.
- **Lock screen** — optional pinned "today" card; on Galaxy phones the Next up widget can go on the lock screen via Good Lock → LockStar.

## Build

Open in Android Studio (Ladybug or newer) and Run, or:

```
./gradlew assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/`.
Unit tests for the planner rules: `./gradlew testDebugUnitTest`.

Fonts: Unbounded, Onest, JetBrains Mono — SIL Open Font License 1.1 (see `app/src/main/assets/licenses`).

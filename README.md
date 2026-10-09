# Bus Tyre Tracker

Single-user Android app that tracks tyre condition and star rating for each bus.
All data is stored on the phone (Room database). There is no login, no internet
permission, and no backup.

## How to build (Android Studio)

1. Open the `BusTyreTracker` folder in Android Studio (Hedgehog or newer).
2. Let Gradle sync. Install SDK Platform 34 if asked.
3. Run ▶ on a phone (Android 8.0 / API 26 or newer).

A Gradle wrapper (Gradle 8.9) is included, so `./gradlew assembleDebug` also works from a terminal.

## What the app does

**Start:** opens straight to the vehicle list (large font, numeric order: F9 before F10).

**Search bar (every screen):** type a vehicle number. Matching vehicles appear below
the bar. When the typed number matches a vehicle exactly, its tyre rows open.
Closing search returns to the screen you were on.

**Vehicle screen:** seven tyres in this order: OF, NF, ORO, ORI, NRO, NRI, SPARE.
- Tap a tyre → pick Condition, Rating, or (SPARE only) Spare present?
- Pick an option and it saves at once. There is no Save button.
- Condition: New or Resole.
- Rating: 1 ⭐ Best (green), 2 ⭐ Good (yellow), 3 ⭐ Worst (red).
- SPARE asks "Is spare present?" first. "No" hides condition and rating.
- A new vehicle asks every tyre in order. Back and navigation are locked until
  every tyre is complete.

**Menu:** 3 ⭐ Vehicles, 2 ⭐ Vehicles, 1 ⭐ Vehicles, Add Vehicle, Live, Export PDF.
- Add Vehicle (typed) accepts letters first, then digits, 5 characters at most (for example F3334).
- Star lists show each vehicle with only the tyres rated that star count.
  Tapping opens only those tyres.

**Export PDF:** one row per vehicle, all seven tyres across the page. Each cell is
N (New) or R (Resole), filled with its star colour. Share with any app.

**History:** every change is logged in the background (field, old value, new value,
time). It can't be deleted. It can be viewed (read-only) through the Assistant.

## Project layout

```
app/src/main/java/com/bustyre/tracker/
  MainActivity.kt        Navigation, menu, search bar, Add Vehicle, export
  BusTyreApp.kt          Application: creates database and repository
  data/                  Room entities, DAO, database, repository, vehicle sort
  ui/DetailScreen.kt     Vehicle tyre rows, first-visit popups, edit popups
  ui/ListScreens.kt      Vehicle list and star lists
  ui/Common.kt           Colours and labels
  export/PdfExporter.kt  PDF writer
```

## Data safety

- No INTERNET permission: nothing leaves the phone.
- `allowBackup="false"`: data is not copied to Google backup.
- PDF exports are written to the app's private folder and shared only when you tap Share.
- Database work runs off the main thread; Room is set to refuse main-thread queries.

## Assistant

A large **🎤 Assistant / ಸಹಾಯಕ** button is at the top of the home screen. Switch between English and ಕನ್ನಡ at the top.

- **Options:** Edit vehicle, Delete vehicle, Know more, 1 / 2 / 3 star vehicles, Count ratings, Change history, Export PDF.
- **Type or speak:** Use the box and Send, or tap Speak. Say a number from 1 to 9 or the option name.
- **Edit vehicle:** enter the number, and the app opens its tyre page.
- **Delete vehicle:** enter the number, then answer Yes or No.
- **Change history:** tell it the vehicle number, then the tyre (OF, NF, ORO, ORI, NRO, NRI, or SPARE), then complete history or timeline. A timeline can be one day, two days, one week, one month, or one year. Complete history has no time limit.
- **Option buttons:** they hide while an option is in use. Use **Menu** to show them again.

Speech input and Kannada voice depend on the phone's speech service. Some phones need internet for speech, and some need Kannada voice data installed. The app itself never connects to the internet.

## Live (hands-free voice)

A **🎙 Live / ಲೈವ್** button sits below the Assistant button on the home screen (and in the ⋮ menu). Live does everything the Assistant does, and it is the only place to **add a vehicle**.

**Always listening.** The microphone stays open: each listening turn restarts itself the moment it ends, and it listens again as soon as the assistant stops speaking. The phone's start/stop beeps are muted while it listens. The screen stays on while Live is open. When you leave the app (Home or another app) Live stops listening and un-mutes the phone; it listens again when you come back. The **🎤 Mic on** button pauses and resumes the mic; **⏹ Stop talking** cuts a long answer short. A type box with Send works too.

**Start.** It says "Namaste" in an Indian English voice and asks: change the language to Kannada? Say yes or no. Then it asks "How may I help you now?" (ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ ಈಗ?). Say "Kannada" or "English" at any time to switch.

**Say whole sentences.** For example:
- "3 star vehicles" / "how many 3 star": each vehicle with its 3-star tyre positions (first ten spoken, all shown).
- "How many ratings": counts of 1, 2, 3 star and not rated.
- "Tell me about F3334": every tyre of that vehicle.
- "History of F3334 last week": it asks which tyre, then lists the changes. Say a period (one day, two days, one week, one month, one year) or "complete".
- "Edit F3334": opens its tyre page. "Delete F3334": asks yes or no first.
- "Export PDF". "Help". "Repeat". "Cancel".
- If the vehicle number is missing it asks "Which vehicle number?".

**Add vehicle.** Say "add vehicle" (or "add vehicle F3334"). It asks the number: letters first, then digits, 5 characters at most. Then say the tyre codes one after another without stopping: N1, R2, R1 ... (N = new, R = resole, number = rating 1 to 3), in order OF, NF, ORO, ORI, NRO, NRI, spare. Say "no spare" (or X) last if there is no spare. "Undo" removes the last code, "clear" starts again. The entries are shown on screen, not read aloud: tap **✔ All correct** (or say "all correct") to save, or **Re-enter**. It then thanks you. The **✕** (and Back) opens "Stop adding this vehicle?": Stay carries on; Stop drops the entries. Nothing is saved until All correct.

**Limits.** Speech comes from the phone's own speech service (it may need internet or downloaded voice data, and Kannada needs Kannada data). The mic is closed while the assistant is talking, so it does not hear itself; use Stop talking to cut in. If you still hear beeps, allow the app under Settings > Do Not Disturb access.

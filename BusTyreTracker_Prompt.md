# BusTyreTracker: prompt for future changes

Use this file to brief Claude before any change to the app.

## Fixed rules (do not change without asking)

- Single user, no login, no OTP, no group, no PIN, no sharing, no Firebase.
- Data stays on the phone (Room). No INTERNET permission. Backup off.
- Vehicle numbers: normalised to upper case, spaces removed. Sorted by letters, then by the whole number (F9 < F10).
- Search bar on every screen. Exact 5-character match opens the vehicle automatically.
- Tyre positions in order: OF, NF, ORO, ORI, NRO, NRI, SPARE.
- Condition: NEW or RESOLE. Rating: 1 (green, best), 2 (yellow, good), 3 (red, worst).
- SPARE asks "Is spare present?" first. If absent, no condition or rating is asked.
- Every tyre must be complete before the vehicle can be left. Back and navigation are locked until then.
- Edits save immediately. No Save buttons.
- History is logged in the background and cannot be deleted. It is read-only and shown only through the Assistant (for example "changes this day").
- Star lists show only the tyres with that rating. Tapping opens only those tyres.
- PDF: one row per vehicle, all seven tyres. Cells show N or R, filled with the star colour.
- Vehicle numbers (typed or Live): letters first, then digits, 5 characters at most.
- Live stops listening and un-mutes the phone when the app goes to the background, and resumes on return.
- Menu: 3/2/1 ⭐ Vehicles, Add Vehicle (typed), Live, Export PDF, Assistant.
- Assistant (assistant/AssistantScreen.kt): English or Kannada. Options: Edit, Delete (asks Yes/No first), Know more, 1/2/3 star lists, Count ratings, Change history (per tyre: complete, or timeline 1 day, 2 days, 1 week, 1 month, 1 year), Export PDF. Replies are spoken aloud and kept short. Voice input uses the phone's speech service, which depends on the phone and may need internet on some phones.
- Live (assistant/LiveScreen.kt): separate hands-free voice assistant, English or Kannada, Indian English voice. Flow: Namaste -> change to Kannada yes/no -> "How may I help you now?". The mic stays open (each listening turn restarts itself, beeps muted, screen kept on, mic on/pause button) and it listens again right after speaking. It understands whole sentences and does every Assistant option (1/2/3 star lists with tyre positions, counts, know more, history, edit, delete with yes/no, export PDF) and it is the ONLY place to add a vehicle by voice: vehicle number (letters then digits, max 5 characters) -> tyre codes said continuously (N1/R2 ..., 7 entries, or 6 plus "no spare") -> entries shown on screen (not read aloud) with "All correct" and "Re-enter" -> save only on "All correct", then a thank-you. A cross/Back opens Stay/Stop; Stop drops everything unsaved. Add vehicle is removed from the old Assistant.

## Stack

Kotlin, Jetpack Compose (Material 3), Room 2.6.1 with KSP, Navigation Compose 2.8.2, minSdk 26, targetSdk 34.

## How to brief a change

1. Name the screen or rule that changes.
2. Say what the new behaviour is.
3. Say which fixed rule above (if any) it affects.
4. Ask for the full changed files and a new zip.

# AI Travel Assistant

Open this directory in Android Studio to build the Kotlin/XML app. It uses Gradle 8.13, JDK 17 or 21, Android SDK 36 and minSdk 26. The Compose prototype is at the workspace root.

The native project has nine modules and uses Material Views, ViewBinding, Navigation XML, RecyclerView, Room, DataStore and Hilt. The [shared design kit](https://chatgpt.com/s/m_6afd6a3c860c8191a5aff97f1b626cad) supplies the launcher mark, Home artwork and state illustrations. Home shows the planning action first, followed by any resumable work, saved trips and tools. The planner collects destination and dates, travelers and budget, then preferences and review. English is the default. Vietnamese is available in Settings.

## Build and test

```sh
./gradlew :app:assembleDebug :core:domain:test
```

The debug variant uses the application ID `com.example.travelassistant.local`. Home, the planner, Settings and offline tools can be opened without a backend. For the existing development generation flow, run a compatible backend on port 8787 and map the device port if testing on a USB device:

```sh
adb -s YOUR_DEVICE_SERIAL reverse tcp:8787 tcp:8787
```

Live generation needs a compatible backend plus provider credentials configured on the server. The Android project contains no provider credentials or production endpoint. Weather and exchange rates need separate data services. World time uses the device clock and time-zone database.

Drafts are autosaved locally. A candidate generated through the backend can be explicitly saved and reopened offline. Saving changes creates a new version. Previous saved versions stay available for read-only viewing.

The ordinary UI tests exercise navigation, validation and local recovery. The live-provider and simulated API tests have separate opt-in instrumentation flags. Use isolated test packages for data-clearing fixtures.

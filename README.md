# WoW-DS

[![Latest release](https://img.shields.io/github/v/release/tomsot2/WoW-DS?label=release)](https://github.com/tomsot2/WoW-DS/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/tomsot2/WoW-DS/build.yml?branch=main&label=build)](https://github.com/tomsot2/WoW-DS/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

**World of Warcraft on dual-screen Android handhelds, built around the game's own gamepad controls.**

Play **World of Warcraft** on Snapdragon Android handhelds as a normal Android app. On the dual-screen **AYN Thor**, the bottom screen becomes a touch companion for the controller: a trackpad, a full keyboard, party targeting and one-tap window shortcuts. Pick **Forever**, **Retail** or **Classic**, press Play, and the game runs.

This uses Blizzard's own **Windows ARM64** WoW clients, so the game itself runs natively on the device's CPU. Wine translates the Windows calls, and DXVK plus a patched Turnip Vulkan driver render the game on the Adreno GPU. Nothing is emulated as x86.

> **Standing on the shoulders of others.** WoW-DS is a fork of **[AYN Thor WoW Launcher](https://github.com/wyattabuntjer/AYN-Thor-WoW-Launcher)** by **[wyattabuntjer](https://github.com/wyattabuntjer)**, which is a fork of **[WoW Forever for Android](https://github.com/jaredgei/wow-forever-android)** by **[jaredgei](https://github.com/jaredgei)**, which is built on **[GameNative](https://github.com/utkarshdalal/GameNative)**. Almost everything that makes WoW run comes from them. See [Credits](#credits-and-how-it-works), and please star and support the original projects.

### Highlights

- **Gamepad first:** WoW's native gamepad mode drives the game, with nothing in the way. Every controller button, including R3, goes straight to WoW.
- **Three clients in one app:** Forever (beta), Retail and Classic, each with its own game folder, in-app updates and Play button.
- **Second-screen companion pad:** a trackpad, a full QWERTY keyboard with a chat bar and quick phrases, one-tap target markers, your own command buttons, party targeting (Me, P1-P4) and window shortcuts (Map, Character, Spellbook, Bags, Group Finder and more), with a full settings page. It dims itself when idle and shifts slightly to protect the OLED. See [The Thor button pad](#the-thor-button-pad).
- **World of Warcraft look:** a stone-and-gold launcher and in-game quick menu, with the chosen client named on the Play button and boot screen.
- **Updates itself:** new versions published here are offered and installed from inside the app.
- **Its own app:** package `app.wowds`, so it installs next to the AYN Thor WoW Launcher, WoW Forever for Android, GameNative and Winlator.

> Unofficial community project. Not affiliated with or endorsed by Blizzard Entertainment. You need your own Battle.net account with access to the client you want to play (the Forever beta needs beta access). This repo and its releases contain **no** Blizzard game files.

---

## Supported devices

| Device | Status |
| :--- | :--- |
| AYN Thor (Snapdragon 8 Gen 2 / Adreno 740) | Main target of this fork: tested with controller support and the second-screen pad |
| Retroid Pocket 6 (Snapdragon 8 Gen 2 / Adreno 740) | Tested by the original community setup |
| AYN Odin 2 / Mini (Snapdragon 8 Gen 2) | Expected to work |
| AYN Odin Portal (Snapdragon 8 Elite / Adreno 830) | Supported via bundled Turnip v32 driver |
| RedMagic 11 Pro (Snapdragon 8 Elite / Adreno 840) | Tested in-world by arusiasotto |
| Samsung Galaxy S23/S24/S25 / Z Fold series | Supported (with automatic Samsung UBWC optimization) |

Requirements:

- Snapdragon 8 series (Adreno 7xx or Adreno 8xx). The app bundles both the Adreno 740 driver and the Turnip v32 driver for Snapdragon 8 Elite / Adreno 8xx, automatically detecting the GPU and applying the optimal driver and environment flags.
- Android 10 or newer, 64-bit.
- About **80 GB** free for the game data (internal storage or SD card), plus about 4 GB of internal storage for the app and its Windows environment.
- A Mac or PC with the client you want installed through Battle.net, to copy the game data from (one-time).

---

## Installation

### 1. Install the app

Download `WoW-DS-vX.Y.Z.apk` from the [latest release](https://github.com/tomsot2/WoW-DS/releases/latest) and install it on your device (allow "install unknown apps" for your browser or file manager if Android asks). Each release also lists the APK's SHA-256 checksum. The app's package name is `app.wowds`, so it can sit alongside the AYN Thor WoW Launcher, WoW Forever for Android, GameNative and Winlator.

After that, WoW-DS keeps itself up to date: see [Updating the app](#updating-the-app).

**Test builds:** every push to this repo also builds a **WoW-DS Dev** APK (package `app.wowds.dev`), available from the latest green run in the **Actions** tab. It installs as a separate app next to the release, and doesn't update itself.

### 2. Copy your WoW game data to the device (one-time setup)

Each client keeps its own folder. Forever uses `/storage/emulated/0/WoW Forever/` by default; Retail and Classic use `/storage/emulated/0/World of Warcraft/` (inside it: `_retail_` and `_classic_era_`). You only need the clients you plan to play. The steps below show Forever; Retail and Classic work the same way with their own install. Copy two things from your Battle.net install (on a Mac that's `/Applications/World of Warcraft/`) into a folder on the device. The default is `/storage/emulated/0/WoW Forever/`. Any other folder works too, including one on an SD card. You only need to do this **once**; all subsequent game updates are handled directly in-app over Wi-Fi.

```
WoW Forever/
├── .build.info                   <- from the install root
└── Data/                         <- the whole Data folder (~67 GB)
```

- `Data/` is the same on every platform, so a Mac, Windows, or ARM install all work.
- `.build.info` is a hidden file. Copy it too. Without it the game fails with *"CAS system was unable to initialize"*.
- You don't need `WowB-ARM64.exe`. Mac and x86 installs don't ship it, so the app downloads the matching Windows ARM64 client from Blizzard's CDN when you press Play, and checks its hashes.
- If you used a different folder, or moved it, the launcher shows **Locate Game Files**. Pick the folder that contains `.build.info` and `Data/`. **Change Location** switches it later.

Transfer the files over to a `WoW/` folder on your device. You can do this by:

- **SD Card:** Insert the microSD card into your computer and copy the files over (make sure your file manager shows hidden files so `.build.info` is included).
- **USB File Transfer:** Connect your device to your computer via USB (in File Transfer / MTP mode) and copy the files directly to internal storage (default: `/storage/emulated/0/WoW/`).
- **ADB Script:** If you have ADB installed and this repo checked out, you can use the sync script to copy initial files easily over USB:

```bash
WOW_SRC="/Applications/World of Warcraft" tools/sync_wow_to_device.sh
```

### 3. Play

Open the app. Pick **Forever**, **Retail** or **Classic** at the top of the launcher, set up your Battle.net login once, and press **PLAY**. The Play button and boot screen name the client that is starting. If a client's game files aren't found, the launcher shows **Locate Game Files**. The first launch of each client downloads its ARM64 build and sets up the Windows environment, which takes a few minutes and needs an internet connection.

- **Setup screen:** to access folder settings, forget credentials, check environment status, or view updates, hold **Start + Select + L2 + R2** (or tap the back button) during the loading splash to cancel boot and return to the setup screen.
- The app creates a basic `WTF/Config.wtf` on first run and always sets `gxApi "D3D11"`, the renderer that works with DXVK.

### Controls and signing in

- **Controller:** built-in handheld controllers work in-game. WoW's own gamepad mode handles the mapping, including R3. For a mouse cursor, use the trackpad on the bottom screen.
- **In-game menu:** press Back (the button or the back swipe gesture) to open the sidebar. It has **Keyboard**, on-screen controls, performance overlay and **Exit**.
- **Keyboard:** the sidebar's **Keyboard** opens the Android keyboard. On dual-screen devices like the Thor it appears on the bottom screen. Symbols like `@` work, and so does pasting.
- **Battle.net Auto-Login:** configure credentials directly on the launcher setup screen (**Configure Login** / **Update Login**). Credentials are saved encrypted on-device via Android Keystore. On boot, the launcher generates `_classic_beta_/login.txt`, which the WoW client automatically reads on startup to sign in natively without macro simulation or synthetic clicks. Use **Forget Saved Login** on the launcher setup screen to clear credentials and remove the login file.
  - Authenticator codes still have to be entered by hand.

---

## The Thor button pad

On the AYN Thor the bottom screen shows a touch pad while you play. It's built to sit next to WoW's own gamepad controls: the controller handles movement, combat and the action bars, and the pad covers what's awkward on a controller. The header has four buttons: **trackpad**, **chat**, **settings (gear)** and **keyboard**.

- **Window shortcuts:** Map, Character, Spellbook, Talents, Skills, Quest Log, Social, System, Bags, Group Finder, Achievements and Guild, one tap each. When more than eight are shown, they split into two columns.
- **Target markers:** Skull, Cross, Square, Moon, Triangle, Diamond, Circle and Star. One tap marks your target, instead of cycling through markers on the controller.
- **Command buttons:** eight buttons you can set yourself. They start as Ready check, Roll, Follow, Focus, Hearthstone, Stop cast, Thank and Wave.
- **Party row:** **Me**, **P1**-**P4**, WoW's default F1-F5 party targeting.
- **Chat:** the chat button opens WoW's chat box with the keyboard, and closes the keyboard when you send. Above the keyboard is a chat bar: **Say, Party, Inst, Raid, Guild, Whisper, Reply** start a message in that channel, and the quick phrases (ty, np, brb, omw, ready?, gg, all editable) send straight to the channel picked with **To**.
- **Keyboard:** a full US QWERTY layout with standard key sizes and stagger, Shift (applies to the next key), Caps Lock, Esc and arrow keys.
- **Trackpad:** a touch mouse with Shift, Ctrl and Alt always available. Drag two fingers up or down to scroll (quest log, bags, chat).
- **Easy on the screen:** the pad dims when you're not using it, and moves a few pixels every few minutes so nothing burns into the OLED.

### Marker and command buttons

Each marker, command and quick phrase can be changed in the pad's settings, under **Buttons and phrases**, using the pad's own keyboard. A button does one of two things:

- **Chat command:** types a command such as `/roll` or `/use Hearthstone` into the chat box and sends it. The markers use `/tm` (for example `/tm 8` for Skull). If your client doesn't have `/tm`, bind the markers under **Key Bindings** in game and switch the marker buttons to those keys.
- **Key:** presses one key, with Shift, Ctrl or Alt if you like (for example Ctrl+F1). Bind that key to one of your own macros in WoW, and the button runs it. This needs no typing, so it's the most direct option.

Either way, one tap is exactly one command or one key press. Nothing repeats, loops or runs on a timer.

### Settings

The settings page (the gear) lets you change:

- which modifier keys, window buttons and pad sections (windows, markers, commands, party) are shown; the rest resize to fill the space
- the marker buttons, command buttons and quick phrases
- swap left/right sides, label size, haptics and double-tap lock time
- dimming when idle (off, or after 15 s to 2 min) and how dark it goes, and burn-in protection. When dimmed, the first tap only wakes the pad
- trackpad speed, acceleration and tap-to-click
- controller stick deadzone
- remapping of the window and party buttons, three saved profiles, backup to the clipboard, and reset
- which client launches next

---

## Updating the game

The launcher has a native in-app game updater connected directly to Blizzard's public edge CDNs, for each client. **You no longer need a computer or USB cables to keep your game updated.**

Whenever Blizzard patches the game:
1. **Live Version Check:** The launcher automatically checks the live version against Blizzard's public patch service on startup.
2. **Update Gate:** If an update is detected, auto-launch cancels and displays the update card with the remote build version, plus a secondary *Launch Anyway (Outdated)* override.
3. **One-Tap Update:** Tapping **UPDATE TO {version}** downloads the new ARM64 client binaries (`WowB-ARM64.exe` and companion DLLs), saves remote configs to `Data/config/`, syncs new CASC index archives to `Data/indices/`, and atomically updates `.build.info` on disk over Wi-Fi.
4. **Instant Boot:** Once the update completes, the launcher automatically transitions into booting World of Warcraft at 60 FPS.
5. **In-Game Streaming:** The game engine's internal streaming client seamlessly streams any newly introduced assets from Blizzard's edge CDNs during gameplay.

*(Optional fallback: If you ever want to re-seed or mirror your full PC installation over USB, `tools/sync_wow_to_device.sh` is still available.)*

## Updating the app

WoW-DS checks this repo's [releases](https://github.com/tomsot2/WoW-DS/releases) each time the launcher opens. When a newer version is out, it shows the release notes and an **Update** button: tap it, and the app downloads the APK and hands it to Android's installer. The first time, Android asks you to allow WoW-DS to install apps.

Before installing, the app checks that the download is WoW-DS and signed with the same key as your install, so a bad download can't replace it. Your settings, login and game files are kept.

---

## Troubleshooting

| Symptom | Fix |
| :--- | :--- |
| *CAS system was unable to initialize: no active install info entries* | `.build.info` or `_classic_beta_/.flavor.info` is missing on the device. |
| *No realms available* / no servers listed | The device client is out of date. Tap **Update** on the launcher setup screen, or restart the app with Wi-Fi enabled. |
| Retail or Classic won't start | Support for these is newer and less tested than Forever. Check `_retail_/Errors/` or `_classic_era_/Errors/` and open an issue with what you find. |
| Keyboard doesn't appear | Force-stop Gboard (Settings → Apps → Gboard → Force stop) and open **Keyboard** again. It can get stuck on the second screen. |
| Returns to the launcher after "Launching Game…" | Check the files under `_classic_beta_/Errors/` on the device. |
| Handheld frontend (e.g. Cocoon) shows the wrong icon | The frontend cached an old icon. Set it with the frontend's "Edit App Artwork", or reinstall the app. |

---

## Building from source

Requirements: JDK 17 and the Android SDK (with build-tools and platform-tools).

```bash
tools/fetch_components.sh           # Wine/Proton, DXVK and Turnip archives (~130 MB, not in git)
./gradlew assembleModernDebug       # WoW-DS Dev (app.wowds.dev): app/build/outputs/apk/modern/debug/
./gradlew assembleModernRelease     # WoW-DS (app.wowds): app/build/outputs/apk/modern/release/
```

Release builds are signed with `app/keystores/keystore.properties` when it exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Otherwise they fall back to the debug key. Builds signed with different keys can't update each other, so switching between them means uninstalling first.

### Publishing a release

Releases are built and published by GitHub Actions ([`release.yml`](.github/workflows/release.yml)), signed with the project's release key from the repository secrets.

1. Bump `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts).
2. Add a `## [X.Y.Z]` section to [`CHANGELOG.md`](CHANGELOG.md). It becomes the release notes, which the app also shows in its update dialog.
3. Commit, then tag and push: `git tag vX.Y.Z && git push origin main vX.Y.Z`.

The workflow refuses to publish if the tag doesn't match `versionName` or the signing secrets are missing. Installed apps see the new release the next time the launcher opens.

`fetch_components.sh` downloads the three runtime archives from jaredgei's `components-v1` release and checks them against `tools/components.sha256`. If you already have them, pass a folder instead: `tools/fetch_components.sh /path/to/components`.

---

## Credits and how it works

This project packages other people's work into a single-purpose app. None of it would exist without:

- **[wyattabuntjer/AYN-Thor-WoW-Launcher](https://github.com/wyattabuntjer/AYN-Thor-WoW-Launcher)** by **wyattabuntjer**. WoW-DS is a direct fork of it. The Retail and Classic support, the stone-and-gold launcher and quick menu, and the AYN Thor second-screen pad with its trackpad, settings, remapping and profiles were built there. Thank you.
- **[jaredgei/wow-forever-android](https://github.com/jaredgei/wow-forever-android)** by **jaredgei**, which the AYN Thor WoW Launcher forks. The WoW launcher screen, the pre-configured container, native ARM64 launch, the Battle.net client downloader, the in-app game updater, the bundled runtime components and most of the Thor fixes described below were done there. Thank you.

- **[GameNative](https://github.com/utkarshdalal/GameNative)** by Utkarsh Dalal and contributors (GPL-3.0). The Android app, the Wine container management and the X server are all GameNative, based on v1.2.1. GameNative in turn builds on **[Pluvia](https://github.com/oxters168/Pluvia)**, **[Winlator](https://github.com/brunodev85/winlator)**, **[Winlator Cmod](https://github.com/coffincolors/winlator)** and the **[Bionic Vulkan wrapper](https://github.com/leegao/bionic-vulkan-wrapper)**.
- **The WoW Forever RP6 community bundle**, which first got the beta running on a Retroid Pocket 6 in GameNative and supplied the three custom runtime components:
  - **Proton 11 ARM64EC** built from [The412Banner/proton-wine](https://github.com/The412Banner/proton-wine/tree/e5fa703ed7f7329e20d7ede481ab185cf8b1a8b2) with an ARM64 copied-syscall fix and an NLS fallback allocation patch.
  - **DXVK 2.4.1 (aarch64)** built from [doitsujin/dxvk](https://github.com/doitsujin/dxvk/tree/0cf05780abd7250c2cd713b7749cf32180157cf5).
    - **Mesa Turnip (Adreno 740)** built from Mesa [`fe067b17d9`](https://github.com/mirror/mesa/tree/fe067b17d9) with a patch limiting barycentric waits to the current block, which fixes a shader scheduler assertion on Adreno 740.
  - **Mesa Turnip (Adreno 8xx / Snapdragon 8 Elite)** built by **[arusiasotto](https://github.com/arusiasotto/wow-forever-a840)** from **[whitebelyash/mesa-unified](https://github.com/whitebelyash/mesa-unified)** (`turnip/gen8` v32) with the barycentric scheduler fix and ICD exports.
- **[Wine](https://www.winehq.org/)**, **[Proton](https://github.com/ValveSoftware/Proton)**, **[DXVK](https://github.com/doitsujin/dxvk)**, **[Mesa](https://mesa3d.org/)**, **[FEX-Emu](https://github.com/FEX-Emu/FEX)**, **[box64](https://github.com/ptitSeb/box64)** and **[PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/)**.
- **Blizzard Entertainment** for World of Warcraft and its Windows ARM64 client. The app icon and splash art come from Blizzard's official [WoW Forever page](https://worldofwarcraft.blizzard.com/en-us/forever). Blizzard owns them and the World of Warcraft marks.

Full third-party license details are in [`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES).

### What WoW-DS changes from the AYN Thor WoW Launcher

WoW-DS is tuned for playing with WoW's native gamepad controls rather than keyboard-and-mouse style. The full list is in [`CHANGELOG.md`](CHANGELOG.md).

- **Native gamepad controls:** the right-stick cursor mode (toggled with R3) is removed, so R3, the right stick and A/B always go to WoW's own gamepad mode. The bottom-screen trackpad is the mouse.
- **Party targeting row:** F1-F12 are replaced by **Me, P1, P2, P3, P4** (WoW's default F1-F5 party targeting), which is awkward to do on a controller.
- **QWERTY keyboard:** a standard US layout with real key sizes and row stagger, separate Shift and Caps Lock, Esc and arrow keys, and no separate symbols page.
- **Idle dimming:** the pad dims after a set time without a touch, and the first tap only wakes it.
- **Haptics:** the keyboard follows the pad's haptics setting.
- **Own identity and releases:** package `app.wowds`, a separate `app.wowds.dev` for test builds, signed releases published by GitHub Actions, and an updater that follows this repo and checks each download before installing it.

### What the AYN Thor WoW Launcher adds on top of WoW Forever for Android

- Forever, Retail and Classic selectable from the launcher, each with its own game folder and in-app updates.
- A stone-and-gold launcher theme and quick menu, and the selected client named on the Play button and boot screen.
- Second-screen button pad with a settings page: grouped buttons, window shortcuts, modifiers, remapping, profiles, and trackpad tuning.
- Its own package name (`app.aynthorwow`) and an updater that follows its releases.

### What WoW Forever for Android changes from GameNative

GameNative is a general game library with Steam, GOG, Epic, Amazon, EA and Rockstar stores, mod management, VR support and per-game container settings. This fork turns it into a launcher for one pre-configured container:

- **Standalone identity:** its own package, WoW name, icons, banners and splash screen, installable next to GameNative.
- **Direct launch & instant boot:** the app opens to a WoW splash screen (`ui/screen/wow/WoWForeverScreen.kt`) and automatically boots straight into the game once configured. Holding **Start + Select + L2 + R2** or pressing Back on the loading screen cancels boot to return to the setup screen.
- **Pre-configured container:** bionic, Proton 11 ARM64EC, Turnip through the Vulkan wrapper, DXVK 2.4.1 aarch64, WINEESYNC off, all 8 cores, 1920x1080, and `G:` mapped to `/storage/emulated/0/WoW Forever`.
- **Native ARM64 launch:** GameNative wraps every Windows program in `winhandler.exe`, an x86-64 helper that needs x86 emulation. ARM64 executables now launch directly, so the game never goes through FEX, and the working directory is set from the mapped drive.
- **Game file setup:** the launcher writes `_classic_beta_/.flavor.info` and a basic `WTF/Config.wtf` if they're missing, forces `gxApi "D3D11"` (so a copied Mac config that says Metal can't break it), and refuses to start without `.build.info`.
- **Package-name fixes:** several paths were hard-coded to `app.gamenative`: the bionic library path rewrite (`WINEMU_HOST_PKG` / `HOST_PKG`), the gamepad shared-memory files, the DXVK state cache and the default drives. The controller path was the reason controllers didn't work.
- **Small robustness fixes:** non-numeric container IDs no longer crash the ID parser, and the bionic redirect library is copied in if it's missing.
- **Keyboard fixes:** GameNative's on-screen keyboard dropped shifted symbols (`@` came through as `2`), because it sent key codes without the character or the Shift key. The fix is in `Keyboard.java` and `IMEInputReceiver.kt`. The keyboard now always goes through the IME receiver, so it appears on the Thor's bottom screen.
- **Battle.net auto-login:** native `login.txt` client authentication managed via Keystore-encrypted credentials (`ui/screen/wow/BattleNetSignIn.kt`). Eliminates coordinate-based typing macros in favor of the game client's built-in startup authentication, with configuration available directly from the launcher setup screen.
- **Native In-App Game Updater (v2.0):** Live version check against Blizzard patch services, direct-from-CDN ARM64 binary and manifest downloads, CASC index synchronization, and atomic `.build.info` updates on-device over Wi-Fi without any PC dependency.
- **Lean runtime & startup:** eliminated continuous background accelerometer polling during gameplay in favor of native OS window management, removed cold-boot bitmap allocations, guarded background performance metric collection loops, and switched DNS to native platform resolution.
- **Removed:** every store backend (Steam and JavaSteam, GOG, Epic, Amazon, EA, Rockstar), library, login, settings, custom game scanner, and downloads screens, ExoPlayer and browser dependencies, `DownloadService`, `ContainerMigrator`, Nexus mod management, the Meta Quest/XR build, PostHog analytics, Play Integrity, the self-updater, the Room database, the notification prompt, extra bundled Box64/FEX/DXVK versions and the legacy (Android 9) build. That's roughly 135k lines of code and about half the APK size.
- **Tooling:** `tools/download_wow_arm64.py` fetches the ARM64 client from Blizzard's CDN, `tools/sync_wow_to_device.sh` handles updates, and `tools/fetch_components.sh` downloads the runtime archives.

## License

GPL-3.0, the same as GameNative. See [`LICENSE`](LICENSE). The bundled runtime components carry their own licenses (LGPL, MIT and others). See `THIRD_PARTY_NOTICES` and the notices inside each component archive.

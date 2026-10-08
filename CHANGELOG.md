# Changelog

All notable changes to WoW-DS are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

Each version's section becomes its GitHub release notes, which the app also shows in its update dialog.

## [1.1.0]

The bottom-screen pad now covers what's awkward with WoW's gamepad controls: chat, raid markers and the odd command.

### Added
- **Chat button** in the pad's header. It opens WoW's chat box with the keyboard, and the keyboard closes again when you send.
- **Chat bar** above the keyboard. **Say, Party, Inst, Raid, Guild, Whisper, Reply** start a message in that channel. **Quick phrases** (ty, np, brb, omw, ready?, gg) send in one tap to the channel picked with **To**.
- **Target marker buttons:** Skull, Cross, Square, Moon, Triangle, Diamond, Circle and Star mark your target in one tap (`/tm`).
- **Command buttons:** eight buttons that start as Ready check, Roll, Follow, Focus, Hearthstone, Stop cast, Thank and Wave.
- **Edit any marker, command or phrase** in the pad's settings, under **Buttons and phrases**, with the pad's own keyboard. A button can type a chat command, or press one key (with Shift, Ctrl or Alt) that you've bound to an in-game macro. Profiles and backups include them.
- **Burn-in protection:** the pad moves a few pixels every few minutes, so nothing sits on the same OLED pixels for hours. It can be turned off in settings.

- **Wide letters keyboard layout**, now the default. It keeps the usual stagger but moves Tab, Caps, Shift, Enter, Backspace and the symbol keys into rows of their own, so the letters are about 40% wider on the Thor's narrow bottom screen. The previous layout is still available as **Standard** under **Keyboard layout** in settings.

### Changed
- The markers and command buttons replace the 1-9, 0, - and = hotbar grid, since the controller already reaches the action bars. The "Hotbar pages" setting is gone, and "Number emphasis" is now "Command emphasis".
- The keyboard now fills the pad (below the chat bar), so its keys are as large as the screen allows.
- The party buttons (Me, P1-P4) are stacked in a column beside the markers and commands, like the party frames, instead of a row of tall, thin buttons.

## [1.0.0]

The first release of WoW-DS, a fork of [AYN Thor WoW Launcher](https://github.com/wyattabuntjer/AYN-Thor-WoW-Launcher) 2.3.2 by wyattabuntjer, tuned for World of Warcraft's native gamepad controls.

WoW-DS is a new app (`app.wowds`). It installs next to the AYN Thor WoW Launcher rather than replacing it, so set up your Battle.net login and game folder once in WoW-DS. Your game files can stay where they are.

### Added
- **Party targeting row** on the bottom-screen pad: **Me, P1, P2, P3, P4** (WoW's default F1-F5 targeting), in place of F1-F12.
- **Idle dimming** for the bottom screen: it dims after 15 s, 30 s, 1 min or 2 min without a touch (30 s by default, or off), with an adjustable dim amount. When dimmed, the first tap only wakes the pad and doesn't press anything.
- **In-app updates from this repo**, with a check that each downloaded update is WoW-DS and signed with the same key before it's installed.
- **WoW-DS Dev** test builds (`app.wowds.dev`) for every push, which install next to the release.

### Changed
- **Keyboard:** now a standard US QWERTY layout with real key sizes and row stagger, including `` ` - = [ ] \ ; ' ``. Shift and Caps Lock are separate keys, and Esc and the arrow keys sit on the bottom row. The separate symbols page is gone; symbols come from Shift.
- **Keyboard haptics** follow the pad's Haptics setting.
- The app is called **WoW-DS** in every language.
- Release builds are no longer minified, so they run exactly the code the test builds run.

### Removed
- **Right-stick cursor mode.** R3 no longer switches the right stick to a mouse cursor, and A/B are no longer clicks. All controller input goes to WoW's native gamepad mode; use the bottom-screen trackpad as the mouse. The cursor speed, ramp, A/B and mode-message settings are gone (the stick deadzone setting stays).
- The **"Write gamepad cursor lines to Config.wtf"** helper. If you used it, you can delete the `GamePadCursorAutoEnable`, `GamePadCursorLeftClick` and `GamePadCursorRightClick` lines from your client's `WTF/Config.wtf` to get WoW's default cursor behaviour back.
- F6-F12 and the F1-F6/F1-F12 setting.

[1.0.0]: https://github.com/tomsot2/WoW-DS/releases/tag/v1.0.0
[1.1.0]: https://github.com/tomsot2/WoW-DS/releases/tag/v1.1.0

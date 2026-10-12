# Changelog

All notable changes to YouMusic are listed here.

## [1.1] - 2026-10-12

### Fixed
- **Music stopped as soon as you left the app.** Android was telling the page it
  was hidden, and the page then paused. The player now always reports itself as
  visible, so music keeps going when you press home, switch apps or lock the
  screen.

### Added
- Real media-player notification: song title, channel, artwork, seek bar and
  ⏮ ⏯ ⏭ ✕ buttons. Also shows on the lock screen.
- Headphone, Bluetooth and lock-screen controls.
- Unplugging headphones pauses.
- If YouTube pauses by itself while the app is in the background (for example
  "Continue watching?"), YouMusic resumes it.

### Changed
- Back on the first page sends the app to the background instead of closing it.
  Use ✕ in the notification, or swipe the app away, to stop.

## [1.0.5] - 2026-10-12

### Fixed
- Updates now install over older versions. All builds are signed with the same key,
  and the version number goes up with every build. **If you installed v1.0.4 or
  earlier, uninstall it once before installing this version.**

### Changed
- README rewritten for people using the app. Build and code details moved to
  `DEVELOPMENT.md`.

## [1.0] - 2026-10-12

### Added
- Home screen with a large paste box, **Paste from clipboard**, **Play** and
  **Browse YouTube** buttons.
- Player screen showing the YouTube mobile site in a WebView.
- Experimental background playback: WebView is never paused, the page is told it is
  always visible, and a media-playback foreground service keeps the app alive.
- Playback notification with a **Stop** button.
- **Share → YouMusic** support from the YouTube app and browsers.
- Link parsing for youtube.com, youtu.be, music.youtube.com, Shorts, live, embed,
  playlists and bare video IDs.
- Fullscreen video support.
- GitHub Actions workflow that builds the APK on every push.
- Every push to `main` publishes a GitHub Release with `YouMusic.apk` attached.

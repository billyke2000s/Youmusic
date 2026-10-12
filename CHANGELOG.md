# Changelog

All notable changes to YouMusic are listed here.

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

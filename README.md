<div align="center">

# 🎵 YouMusic

**Play YouTube with your screen off.**
Paste a link, press play, lock your phone. The music keeps going.

[![Download APK](https://img.shields.io/badge/Download-YouMusic.apk-E53935?style=for-the-badge&logo=android&logoColor=white)](https://github.com/billyke2000s/Youmusic/releases/latest/download/YouMusic.apk)

![Latest release](https://img.shields.io/github/v/release/billyke2000s/Youmusic?label=version&color=E53935)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Size](https://img.shields.io/badge/size-~20%20KB-555)
![License MIT](https://img.shields.io/badge/license-MIT-555)

</div>

---

## Why YouMusic?

On YouTube, the music stops the moment you leave the app or lock your screen.
YouMusic is a tiny Android app built to fix that, so YouTube works more like
Spotify:

- 🔒 **Keeps playing with the screen locked**
- 📱 **Keeps playing when you switch to another app**
- 🎛️ **Proper music controls.** Song title, channel and artwork in your
  notifications and on your lock screen, with ⏮ ⏯ ⏭ buttons and a seek bar.
- 🎧 **Headphone and Bluetooth buttons work.** Unplugging headphones pauses.
- 📋 **One big paste box.** Copy a link, paste it, done.
- 🌐 **Full YouTube inside.** Tap *Browse YouTube* to search and scroll like normal.
- 📤 **Share straight to it.** In the YouTube app tap **Share → YouMusic**.
- 🪶 **About 20 KB.** No tracking, no accounts, no extra junk.

> [!NOTE]
> Background play is **experimental**. It works through a few tricks rather than
> anything official from YouTube, so it may not work on every phone. See
> [FAQ](#faq) if the music stops.

---

## Get it

1. **[Download YouMusic.apk](https://github.com/billyke2000s/Youmusic/releases/latest/download/YouMusic.apk)** on your Android phone.
2. Open the downloaded file.
3. If your phone asks, allow your browser to **install unknown apps**.
4. Open YouMusic and allow notifications when asked.

Already have it? Download the latest version and install it over the top. You
won't lose anything.

All versions are on the [Releases page](https://github.com/billyke2000s/Youmusic/releases).

---

## How to use it

### ▶️ Play a link

1. Copy a video link in YouTube (**Share → Copy link**).
2. Open YouMusic and tap **Paste from clipboard**.
3. Tap **Play**.
4. Lock your phone, press home, or go to another app. The music carries on.
5. Control it from the notification, the lock screen or your headphones.

### 🔍 Browse

Tap **Browse YouTube** to get the normal YouTube home page. Search, pick
something, and it plays the same way.

### 📤 Share from YouTube

Watching something in the YouTube app? Tap **Share**, then pick **YouMusic**.
It opens and starts playing.

### ⏹️ Stop

- **Pause:** tap ⏸ in the notification or on the lock screen.
- **Close completely:** tap ✕ in the notification, or swipe YouMusic away in your
  recent apps.

Pressing back on the first page doesn't stop the music. It tucks the app away
in the background, like Spotify does.

---

## What links work?

Pretty much any YouTube link:

- Normal videos: `youtube.com/watch?v=...`
- Short links: `youtu.be/...`
- YouTube Music: `music.youtube.com/watch?v=...`
- Shorts and live streams
- Playlists
- Just the video code on its own, like `dQw4w9WgXcQ`
- A whole shared message with a link somewhere inside it

---

## FAQ

<details>
<summary><b>The music stops when I lock my screen</b></summary>

Some phones are aggressive about closing apps in the background. Try this:

1. Go to **Settings → Apps → YouMusic → Battery**.
2. Set it to **Unrestricted** (sometimes called "Don't optimise").
3. Make sure YouMusic's notifications are turned **on**. The notification is what
   tells Android to keep the app running.

If it still stops, please [report it](https://github.com/billyke2000s/Youmusic/issues/new?template=bug_report.md)
with your phone model and Android version.
</details>

<details>
<summary><b>Why does the music stop when I swipe YouMusic away?</b></summary>

Swiping an app out of your recent apps tells Android to close it, and YouMusic's
player lives inside the app. Use the home button instead, and the music keeps
going. To stop it, tap ✕ on the notification.
</details>

<details>
<summary><b>Is it safe? Why does Android warn me?</b></summary>

Android shows a warning for any app that doesn't come from the Play Store. That's
normal for apps downloaded from GitHub. All of YouMusic's code is public in this
repo, so anyone can check what it does. It has no tracking or ads of its own, and
the only site it talks to is YouTube.
</details>

<details>
<summary><b>Does it block ads?</b></summary>

No. YouTube's normal ads still play.
</details>

<details>
<summary><b>Can I sign in to my YouTube account?</b></summary>

You can try. Tap **Browse YouTube** and sign in the same way you would on the
YouTube website. Google sometimes blocks sign-in inside apps like this one and
shows a "browser may not be secure" message. If that happens, YouMusic still works
fine without an account.
</details>

<details>
<summary><b>Can I download songs for offline?</b></summary>

No. YouMusic streams from YouTube. It doesn't download anything.
</details>

<details>
<summary><b>Is there an iPhone version?</b></summary>

No, it's Android only.
</details>

<details>
<summary><b>What permissions does it need?</b></summary>

| Permission | What for |
|---|---|
| Internet | Loading YouTube |
| Notifications | The "playing in the background" notification |
| Run in background | Keeping the music going with the screen off |
| Keep phone awake | Stopping the phone from sleeping mid-song |

Nothing else. No location, contacts, camera, microphone or storage.
</details>

---

## Found a problem? Got an idea?

[Open an issue](https://github.com/billyke2000s/Youmusic/issues/new/choose) and
say what happened. Your phone model and Android version help a lot.

Want to help build it? See [DEVELOPMENT.md](DEVELOPMENT.md).

---

<sub>
YouMusic is an independent hobby project and is not affiliated with or endorsed by
YouTube or Google. YouTube is a trademark of Google LLC. Background playback is
normally a YouTube Premium feature, and using workarounds like this may go against
<a href="https://www.youtube.com/t/terms">YouTube's Terms of Service</a>. Use at your
own risk. Released under the <a href="LICENSE">MIT License</a>.
</sub>

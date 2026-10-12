package com.youmusic.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Foreground service that:
 *  - keeps the app alive in the background (media-playback foreground service),
 *  - owns the MediaSession, which gives lock-screen, headphone and Bluetooth
 *    controls and the media player in the notification shade,
 *  - shows a media-style notification with title, channel, artwork and
 *    previous / play-pause / next / close buttons.
 *
 * The page reports what's playing through PlayerActivity's bridge, which calls
 * updateTrack()/updateState(). Button presses go back to the page with
 * PlayerActivity.command("YM.next()") etc.
 *
 * Uses only the built-in android.media.session APIs (no AndroidX).
 */
public class PlaybackService extends Service {

    static final String BROADCAST_CLOSE = "com.youmusic.app.CLOSE";

    private static final String ACTION_PREV = "com.youmusic.app.PREV";
    private static final String ACTION_TOGGLE = "com.youmusic.app.TOGGLE";
    private static final String ACTION_NEXT = "com.youmusic.app.NEXT";
    private static final String ACTION_CLOSE = "com.youmusic.app.ACTION_CLOSE";
    private static final String CUSTOM_CLOSE = "close";

    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 1;
    private static final long WAKE_LOCK_MS = 3L * 60 * 60 * 1000;
    // Android has been seen silently dropping a service's foreground status after
    // a long time in the background (reported by NouTube on newer Android). Re-assert
    // it regularly while playing so the process isn't treated as idle and killed.
    private static final long REASSERT_MS = 60_000;
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    // --- What's playing. Static so the page can report before the service is up. ---
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static PlaybackService instance;
    private static String videoId;
    private static String title = "YouMusic";
    private static String artist = "";
    private static long durationMs;
    private static boolean playing;
    private static long positionMs;
    private static long positionAt; // SystemClock.elapsedRealtime() when positionMs was read
    private static Bitmap artwork;

    private MediaSession session;
    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;
    private final Runnable reassert = new Runnable() {
        @Override
        public void run() {
            if (playing) showNotification();
            MAIN.postDelayed(this, REASSERT_MS);
        }
    };

    // ---------------------------------------------------------------------------------
    // Called from the page (any thread)

    static void updateTrack(String id, String newTitle, String newArtist, int seconds) {
        MAIN.post(() -> {
            if (id == null || !VIDEO_ID.matcher(id).matches()) return;
            videoId = id;
            title = (newTitle == null || newTitle.isEmpty()) ? "YouMusic" : newTitle;
            artist = newArtist == null ? "" : newArtist;
            durationMs = Math.max(0, seconds) * 1000L;
            artwork = null;
            if (instance != null) {
                instance.publishMetadata();
                instance.showNotification();
                instance.loadArtwork(id);
            }
        });
    }

    static void updateState(boolean isPlaying, double positionSeconds) {
        MAIN.post(() -> {
            playing = isPlaying;
            positionMs = (long) (Math.max(0, positionSeconds) * 1000);
            positionAt = SystemClock.elapsedRealtime();
            if (instance != null) {
                instance.publishState();
                instance.showNotification();
                instance.updateWakeLock();
            }
        });
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }

        session = new MediaSession(this, "YouMusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { PlayerActivity.command("YM.play()"); }
            @Override public void onPause() { PlayerActivity.command("YM.pause()"); }
            @Override public void onSkipToNext() { PlayerActivity.command("YM.next()"); }
            @Override public void onSkipToPrevious() { PlayerActivity.command("YM.prev()"); }
            @Override public void onSeekTo(long pos) { PlayerActivity.command("YM.seekTo(" + (pos / 1000.0) + ")"); }
            @Override public void onStop() { close(); }
            @Override public void onCustomAction(String action, Bundle extras) {
                if (CUSTOM_CLOSE.equals(action)) close();
            }
        });
        session.setSessionActivity(openPlayerIntent());
        publishMetadata();
        publishState();
        session.setActive(true);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouMusic:playback");
        wakeLock.setReferenceCounted(false);

        MAIN.postDelayed(reassert, REASSERT_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must go foreground straight away after startForegroundService().
        showNotification();

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PREV.equals(action)) PlayerActivity.command("YM.prev()");
        else if (ACTION_NEXT.equals(action)) PlayerActivity.command("YM.next()");
        else if (ACTION_TOGGLE.equals(action)) PlayerActivity.command(playing ? "YM.pause()" : "YM.play()");
        else if (ACTION_CLOSE.equals(action)) close();

        if (videoId != null && artwork == null) loadArtwork(videoId);
        return START_NOT_STICKY; // without the player screen there's nothing to resume
    }

    /** User swiped YouMusic away from recent apps: stop, the player screen is gone. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        close();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacks(reassert);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (instance == this) instance = null;
        // Forget the last song so a fresh start doesn't show stale info.
        videoId = null; title = "YouMusic"; artist = ""; durationMs = 0;
        playing = false; positionMs = 0; artwork = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void close() {
        PlayerActivity.command("window.YM&&YM.pause()");
        sendBroadcast(new Intent(BROADCAST_CLOSE).setPackage(getPackageName()));
        stopSelf();
    }

    // ---------------------------------------------------------------------------------
    // MediaSession

    private void publishMetadata() {
        MediaMetadata.Builder m = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist);
        if (durationMs > 0) m.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs); // seek bar
        if (artwork != null) {
            m.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork);
            m.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork);
        }
        session.setMetadata(m.build());
    }

    private void publishState() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        positionMs, playing ? 1f : 0f, positionAt == 0 ? SystemClock.elapsedRealtime() : positionAt);
        // Android 13+ builds the shade's buttons from this state; Close needs a custom action.
        if (Build.VERSION.SDK_INT >= 33) {
            b.addCustomAction(new PlaybackState.CustomAction.Builder(
                    CUSTOM_CLOSE, "Close", android.R.drawable.ic_menu_close_clear_cancel).build());
        }
        session.setPlaybackState(b.build());
    }

    // ---------------------------------------------------------------------------------
    // Notification

    private void showNotification() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            // Android 12+ can refuse to promote from the background; keep the
            // notification updated and try again on the next tick.
            nm.notify(NOTIF_ID, n);
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);

        // Android 12 and below draw these buttons; 13+ draws from the MediaSession.
        b.addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV, 1));
        b.addAction(playing
                ? action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE, 2)
                : action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE, 2));
        b.addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 3));
        b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Close", ACTION_CLOSE, 4));

        return b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(artwork)
                .setContentIntent(openPlayerIntent())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private Notification.Action action(int icon, String label, String intentAction, int requestCode) {
        PendingIntent pi = PendingIntent.getService(this, requestCode,
                new Intent(this, PlaybackService.class).setAction(intentAction),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    private PendingIntent openPlayerIntent() {
        Intent i = new Intent(this, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------------------------------------------------------------------------------
    // Wake lock: only held while actually playing.

    private void updateWakeLock() {
        if (playing) {
            wakeLock.acquire(WAKE_LOCK_MS);
        } else if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // ---------------------------------------------------------------------------------
    // Artwork: YouTube's standard thumbnail for the video, scaled down so it fits
    // through Android's notification size limits.

    private void loadArtwork(String id) {
        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                }
                if (bmp != null) bmp = cropSquare(bmp, 400);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            final Bitmap result = bmp;
            MAIN.post(() -> {
                if (result == null || !id.equals(videoId) || instance == null) return;
                artwork = result;
                instance.publishMetadata();
                instance.showNotification();
            });
        }, "YouMusic-artwork").start();
    }

    /** hqdefault is 4:3 with black bars; the middle square looks like album art. */
    private static Bitmap cropSquare(Bitmap src, int size) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.min(w, (int) (h * 0.75f)); // drop the letterbox bars
        int x = (w - side) / 2, y = (h - side) / 2;
        Bitmap square = Bitmap.createBitmap(src, x, y, side, side);
        return Bitmap.createScaledBitmap(square, size, size, true);
    }
}

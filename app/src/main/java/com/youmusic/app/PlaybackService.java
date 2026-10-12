package com.youmusic.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Foreground service whose only job is to keep the app's process alive (and the CPU awake)
 * while YouTube plays in PlayerActivity's WebView with the app minimised or the screen off.
 */
public class PlaybackService extends Service {

    static final String ACTION_STOP = "com.youmusic.app.ACTION_STOP";
    static final String BROADCAST_STOPPED = "com.youmusic.app.STOPPED";

    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 1;
    private static final long MAX_WAKE_MS = 6L * 60 * 60 * 1000; // safety cap: 6 hours

    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            sendBroadcast(new Intent(BROADCAST_STOPPED).setPackage(getPackageName()));
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouMusic:playback");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(MAX_WAKE_MS);
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, PlayerActivity.class), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP), piFlags);

        return b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("YouMusic")
                .setContentText("Playing in the background — tap to open")
                .setContentIntent(open)
                .addAction(0, "Stop", stop)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

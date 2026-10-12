package com.youmusic.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

/**
 * Shows the YouTube mobile site and keeps it playing in the background.
 *
 * Background play comes from three things working together:
 *  - AlwaysVisibleWebView: Chromium never learns the app left the screen.
 *  - We never call webView.onPause()/pauseTimers().
 *  - PlaybackService: a media-playback foreground service with a MediaSession,
 *    which keeps the process alive and gives the media notification.
 *
 * Pressing back on the first page sends the app to the background (like Spotify)
 * instead of closing it. Playback stops with the notification's Close button or
 * by swiping the app away from recent apps.
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";

    /** The live player, so PlaybackService can send it play/pause/next commands. */
    private static WeakReference<PlayerActivity> current = new WeakReference<>(null);

    private final Handler main = new Handler(Looper.getMainLooper());
    private AlwaysVisibleWebView web;
    private FrameLayout root;
    private AlwaysVisibleWebView.FullscreenContainer fullscreenLayer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private BroadcastReceiver closeReceiver;
    private BroadcastReceiver noisyReceiver;
    private String injectedJs;
    private boolean inBackground = false;

    /** Runs a YM.* command in the page, if a player is open. Safe from any thread. */
    static void command(String js) {
        PlayerActivity a = current.get();
        if (a != null) a.main.post(() -> a.runJs(js));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        getWindow().setStatusBarColor(Color.BLACK);
        injectedJs = readAsset("youmusic.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new AlwaysVisibleWebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        // Without this, Android drops the page's renderer to low priority when it
        // isn't on screen and may kill it mid-song.
        if (Build.VERSION.SDK_INT >= 26) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        web.addJavascriptInterface(new Bridge(), "YouMusicApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        root.addView(web, matchParent());

        fullscreenLayer = new AlwaysVisibleWebView.FullscreenContainer(this);
        fullscreenLayer.setVisibility(View.GONE);
        root.addView(fullscreenLayer, matchParent());
        setContentView(root);

        registerReceivers();
        startPlaybackService();
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        load(intent);
    }

    private void load(Intent intent) {
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        if (url != null) web.loadUrl(url);
        else if (web.getUrl() == null) web.loadUrl(YouTubeLinks.HOME);
    }

    // --- Foreground / background -------------------------------------------------
    // Deliberately no web.onPause()/pauseTimers(): that's what would stop the music.

    @Override
    protected void onStart() {
        super.onStart();
        setBackground(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        setBackground(true);
    }

    /** Tells the page whether the app is really on screen (for the auto-resume guard). */
    private void setBackground(boolean background) {
        inBackground = background;
        if (web != null) runJs("window.YMBackground=" + background + ";");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            moveTaskToBack(true); // keep playing, like a music app
        }
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current = new WeakReference<>(null);
        main.removeCallbacksAndMessages(null);
        unregisterQuietly(closeReceiver);
        unregisterQuietly(noisyReceiver);
        stopService(new Intent(this, PlaybackService.class));
        if (web != null) {
            web.loadUrl("about:blank");
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // --- Helpers -------------------------------------------------------------------

    private void runJs(String js) {
        if (web != null) web.evaluateJavascript(js, null);
    }

    private void injectScript() {
        if (injectedJs != null) runJs(injectedJs);
        setBackground(inBackground); // a new page starts without the flag
    }

    private void startPlaybackService() {
        Intent i = new Intent(this, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void registerReceivers() {
        // "Close" in the notification
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                runJs("window.YM&&YM.pause();");
                finishAndRemoveTask();
            }
        };
        register(closeReceiver, new IntentFilter(PlaybackService.BROADCAST_CLOSE), false);

        // Headphones unplugged / Bluetooth disconnected: pause, like every music app.
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                runJs("window.YM&&YM.pause();");
            }
        };
        register(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), true);
    }

    private void register(BroadcastReceiver r, IntentFilter f, boolean fromSystem) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, fromSystem ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    private void unregisterQuietly(BroadcastReceiver r) {
        if (r == null) return;
        try { unregisterReceiver(r); } catch (Exception ignored) {}
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        fullscreenLayer.removeView(fullscreenView);
        fullscreenLayer.setVisibility(View.GONE);
        fullscreenView = null;
        web.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    // --- Page -> app bridge (called on a background thread) -----------------------

    private class Bridge {
        @JavascriptInterface
        public void onTrack(String videoId, String title, String author, int seconds) {
            PlaybackService.updateTrack(videoId, title, author, seconds);
        }

        @JavascriptInterface
        public void onState(boolean playing, double positionSeconds) {
            PlaybackService.updateState(playing, positionSeconds);
        }

        /** False during a phone call, so the guard never fights a real interruption. */
        @JavascriptInterface
        public boolean canAutoResume() {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am == null || am.getMode() == AudioManager.MODE_NORMAL;
        }
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            // Normal pages (YouTube, Google sign-in) stay in the app.
            if ("http".equals(scheme) || "https".equals(scheme)) return false;
            // Block intent:// and vnd.youtube:// so YouTube can't push you into its own app.
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            injectScript();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectScript();
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
            fullscreenView = view;
            fullscreenCallback = callback;
            web.setVisibility(View.GONE);
            fullscreenLayer.addView(view, matchParent());
            fullscreenLayer.setVisibility(View.VISIBLE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }
}

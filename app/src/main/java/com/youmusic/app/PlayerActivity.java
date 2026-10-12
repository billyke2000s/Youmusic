package com.youmusic.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Hosts the YouTube mobile website in a WebView and keeps it running in the background.
 *
 * How background play is attempted (none of this is officially supported by YouTube):
 *  1. We never call webView.onPause() / pauseTimers(), so Android doesn't pause the page.
 *  2. Injected JS makes the page think it is always visible, so YouTube's own
 *     "tab hidden -> pause" logic doesn't fire.
 *  3. A foreground service (PlaybackService) keeps the process alive with a notification.
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";

    private static final String KEEP_VISIBLE_JS =
            "(function(){try{" +
            "if(window.__ymPatched)return;window.__ymPatched=true;" +
            "Object.defineProperty(document,'hidden',{get:function(){return false},configurable:true});" +
            "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'},configurable:true});" +
            "Object.defineProperty(document,'webkitHidden',{get:function(){return false},configurable:true});" +
            "Object.defineProperty(document,'webkitVisibilityState',{get:function(){return 'visible'},configurable:true});" +
            "var stop=function(e){e.stopImmediatePropagation();};" +
            "['visibilitychange','webkitvisibilitychange','pagehide','freeze','blur'].forEach(function(t){" +
            "window.addEventListener(t,stop,true);document.addEventListener(t,stop,true);});" +
            "}catch(e){}})();";

    private WebView web;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private BroadcastReceiver stopReceiver;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); // lets you sign in

        // Keep the page's renderer at high priority even when the app is not on screen.
        if (Build.VERSION.SDK_INT >= 26) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        registerStopReceiver();
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

    @Override
    protected void onPause() {
        super.onPause();
        // Deliberately NOT calling web.onPause() or web.pauseTimers(): that's what keeps audio going.
        web.evaluateJavascript(KEEP_VISIBLE_JS, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed(); // closes the player and stops playback
        }
    }

    @Override
    protected void onDestroy() {
        if (stopReceiver != null) {
            try { unregisterReceiver(stopReceiver); } catch (Exception ignored) {}
        }
        stopService(new Intent(this, PlaybackService.class));
        if (web != null) {
            web.loadUrl("about:blank");
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void startPlaybackService() {
        Intent i = new Intent(this, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    /** "Stop" button in the notification -> close the player. */
    private void registerStopReceiver() {
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                finish();
            }
        };
        IntentFilter f = new IntentFilter(PlaybackService.BROADCAST_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, f);
        }
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        web.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            // Stay inside the app for normal web pages (YouTube + Google sign-in).
            if ("http".equals(scheme) || "https".equals(scheme)) return false;
            // Block intent:// and vnd.youtube:// so YouTube can't bounce you into its own app.
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.evaluateJavascript(KEEP_VISIBLE_JS, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(KEEP_VISIBLE_JS, null);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
            fullscreenView = view;
            fullscreenCallback = callback;
            web.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

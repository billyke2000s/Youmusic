package com.youmusic.app;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * A WebView that always tells Chromium its window is visible.
 *
 * This is the key to background playback. When the app leaves the screen, Android
 * reports the window as hidden, and Chromium then marks the whole page hidden and
 * pauses YouTube. Reporting VISIBLE here keeps the page running. The real
 * foreground/background state is passed to the page separately (see
 * PlayerActivity.setBackground) so the auto-resume logic can still tell the
 * difference between "user paused" and "YouTube paused because we left".
 *
 * Technique learned from NouTube (github.com/nonbili/NouTube); this is an
 * independent implementation.
 */
public class AlwaysVisibleWebView extends WebView {

    public AlwaysVisibleWebView(Context context) {
        super(context);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }

    /**
     * Fullscreen video is drawn in a separate view handed out by the WebView, which
     * the override above doesn't cover. Hosting that view in this container keeps it
     * "visible" in the same way, so leaving the app from fullscreen doesn't pause.
     */
    public static class FullscreenContainer extends FrameLayout {
        public FullscreenContainer(Context context) {
            super(context);
        }

        @Override
        public void dispatchWindowVisibilityChanged(int visibility) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE);
        }
    }
}

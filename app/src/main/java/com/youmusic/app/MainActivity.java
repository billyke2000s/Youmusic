package com.youmusic.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Home screen: one massive paste box, a Play button and a Browse YouTube button. */
public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#0F0F0F");
    private static final int CARD = Color.parseColor("#212121");
    private static final int RED = Color.parseColor("#E53935");

    private EditText linkBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        // Opened via "Share -> YouMusic" from the YouTube app or browser: play straight away.
        if (handleShare(getIntent())) return;

        buildUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    private boolean handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        String url = YouTubeLinks.toPlayableUrl(text);
        if (url == null) {
            Toast.makeText(this, "That share wasn't a YouTube link", Toast.LENGTH_LONG).show();
            return false;
        }
        openPlayer(url);
        finish();
        return true;
    }

    private void buildUi() {
        int pad = dp(20);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, dp(40), pad, pad);
        col.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("YouMusic");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Paste a YouTube link. It keeps playing when you leave the app or lock the screen.");
        sub.setTextColor(Color.parseColor("#AAAAAA"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setPadding(0, dp(6), 0, dp(20));
        col.addView(sub);

        // The massive paste box
        linkBox = new EditText(this);
        linkBox.setHint("Paste YouTube link here");
        linkBox.setHintTextColor(Color.parseColor("#777777"));
        linkBox.setTextColor(Color.WHITE);
        linkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        linkBox.setGravity(Gravity.CENTER);
        linkBox.setMinHeight(dp(260));
        linkBox.setPadding(dp(18), dp(18), dp(18), dp(18));
        linkBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        linkBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        linkBox.setBackground(rounded(CARD, dp(18), Color.parseColor("#3A3A3A")));
        linkBox.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { play(); return true; }
            return false;
        });
        linkBox.setOnLongClickListener(v -> { pasteFromClipboard(); return false; });
        col.addView(linkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button paste = button("PASTE FROM CLIPBOARD", CARD);
        paste.setOnClickListener(v -> pasteFromClipboard());
        col.addView(paste, buttonParams(dp(14)));

        Button play = button("▶  PLAY", RED);
        play.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        play.setOnClickListener(v -> play());
        col.addView(play, buttonParams(dp(10)));

        Button browse = button("BROWSE YOUTUBE", CARD);
        browse.setOnClickListener(v -> openPlayer(YouTubeLinks.HOME));
        col.addView(browse, buttonParams(dp(10)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(col);
        setContentView(scroll);
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null) linkBox.setText(text.toString().trim());
    }

    private void play() {
        String url = YouTubeLinks.toPlayableUrl(linkBox.getText().toString());
        if (url == null) {
            Toast.makeText(this, "That doesn't look like a YouTube link", Toast.LENGTH_LONG).show();
            return;
        }
        openPlayer(url);
    }

    private void openPlayer(String url) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, url);
        startActivity(i);
    }

    // ---- small UI helpers ----

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(rounded(color, dp(30), 0));
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = topMargin;
        return lp;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

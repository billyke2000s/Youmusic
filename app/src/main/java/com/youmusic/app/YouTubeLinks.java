package com.youmusic.app;

import android.net.Uri;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns whatever the user pasted into a YouTube URL the web player can open. */
final class YouTubeLinks {

    static final String HOME = "https://m.youtube.com/";

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+");
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern LIST_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private YouTubeLinks() {}

    /** @return a playable https URL, or null if the input isn't a YouTube link/ID. */
    static String toPlayableUrl(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        // Share text is often "Check this out https://youtu.be/xyz" -> pull the URL out.
        Matcher m = URL_IN_TEXT.matcher(s);
        if (m.find()) s = m.group();

        // Bare 11-character video ID
        if (VIDEO_ID.matcher(s).matches()) return watch(s, null);

        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;

        Uri u;
        try {
            u = Uri.parse(s);
        } catch (Exception e) {
            return null;
        }
        String host = u.getHost();
        if (host == null) return null;
        host = host.toLowerCase(Locale.ROOT);

        List<String> seg = u.getPathSegments();
        String list = safeList(u.getQueryParameter("list"));

        // youtu.be/<id>
        if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
            if (!seg.isEmpty() && VIDEO_ID.matcher(seg.get(0)).matches()) {
                return watch(seg.get(0), list);
            }
            return null;
        }

        boolean isYouTube = host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com");
        if (!isYouTube) return null;

        // youtube.com/watch?v=<id>  and  music.youtube.com/watch?v=<id>
        String v = u.getQueryParameter("v");
        if (v != null && VIDEO_ID.matcher(v).matches()) return watch(v, list);

        // /shorts/<id>, /live/<id>, /embed/<id>, /v/<id>
        if (seg.size() >= 2) {
            String first = seg.get(0);
            if ((first.equals("shorts") || first.equals("live") || first.equals("embed") || first.equals("v"))
                    && VIDEO_ID.matcher(seg.get(1)).matches()) {
                return watch(seg.get(1), list);
            }
        }

        // Playlist link with no specific video
        if (list != null) return "https://m.youtube.com/playlist?list=" + list;

        // Any other YouTube page (channel, search, etc.) -> open it on the mobile site.
        String path = u.getEncodedPath() == null ? "/" : u.getEncodedPath();
        String query = u.getEncodedQuery();
        return "https://m.youtube.com" + path + (query != null ? "?" + query : "");
    }

    private static String watch(String id, String list) {
        String url = "https://m.youtube.com/watch?v=" + id;
        if (list != null) url += "&list=" + list;
        return url;
    }

    private static String safeList(String list) {
        return (list != null && LIST_ID.matcher(list).matches()) ? list : null;
    }
}

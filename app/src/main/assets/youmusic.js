// Injected into every YouTube page by PlayerActivity.
//
// It does three jobs:
//   1. Controls: window.YM.play/pause/toggle/next/prev/seekTo, called from the
//      media notification, lock screen and headphone buttons.
//   2. Reporting: tells the app (window.YouMusicApp) the current title, channel,
//      length, play state and position, so the notification can show them.
//   3. Background guard: if YouTube pauses by itself while the app is in the
//      background (not because the user or the app asked), resume it.
(function () {
  if (window.__ymInstalled) return;
  window.__ymInstalled = true;

  var app = window.YouMusicApp; // Java bridge (PlayerActivity.Bridge)
  if (!app) return;

  // --- Make the page believe it is always visible --------------------------
  // The WebView already reports itself visible to Chromium; this covers any
  // script that checks the Page Visibility API directly.
  try {
    var def = function (obj, prop, value) {
      Object.defineProperty(obj, prop, { get: function () { return value; }, configurable: true });
    };
    def(document, 'hidden', false);
    def(document, 'webkitHidden', false);
    def(document, 'visibilityState', 'visible');
    def(document, 'webkitVisibilityState', 'visible');
    var swallow = function (e) { e.stopImmediatePropagation(); };
    ['visibilitychange', 'webkitvisibilitychange'].forEach(function (t) {
      window.addEventListener(t, swallow, true);
      document.addEventListener(t, swallow, true);
    });
  } catch (e) {}

  // --- Helpers ---------------------------------------------------------------
  var PLAYING = 1, PAUSED = 2;

  function player() { return document.getElementById('movie_player'); }

  function video() {
    var p = player();
    return (p && p.querySelector('video')) || document.querySelector('video');
  }

  function isPlaying() {
    var v = video();
    if (v) return !v.paused;
    var p = player();
    return !!(p && p.getPlayerState && p.getPlayerState() === PLAYING);
  }

  function currentId() {
    var p = player();
    try {
      var d = p && p.getPlayerResponse && p.getPlayerResponse();
      if (d && d.videoDetails && d.videoDetails.videoId) return d.videoDetails.videoId;
    } catch (e) {}
    var m = location.search.match(/[?&]v=([A-Za-z0-9_-]{11})/);
    return m ? m[1] : null;
  }

  // The mobile player's middle controls: [previous, play/pause, next].
  function clickPrevNext(which) {
    var buttons = document.querySelectorAll('.player-middle-controls-prev-next-button');
    if (!buttons.length) return false;
    var b = which === 'next' ? buttons[buttons.length - 1] : buttons[0];
    if (b && !b.disabled) { b.click(); return true; }
    return false;
  }

  // --- 1. Controls -------------------------------------------------------------
  // Pauses the user or app asked for. The background guard never undoes these.
  var userPaused = false;

  window.YM = {
    play: function () {
      userPaused = false;
      var p = player();
      if (p && p.playVideo) p.playVideo(); else { var v = video(); if (v) v.play(); }
    },
    pause: function () {
      userPaused = true;
      var p = player();
      if (p && p.pauseVideo) p.pauseVideo(); else { var v = video(); if (v) v.pause(); }
    },
    toggle: function () { if (isPlaying()) YM.pause(); else YM.play(); },
    next: function () {
      userPaused = false;
      var before = currentId();
      var p = player();
      if (p && p.nextVideo) p.nextVideo();
      // Outside a playlist nextVideo() may do nothing; fall back to the
      // player's own Next button (the autoplay "up next" video).
      setTimeout(function () { if (currentId() === before) clickPrevNext('next'); }, 1500);
    },
    prev: function () {
      userPaused = false;
      var v = video();
      // Like music apps: past 3 seconds, "previous" restarts the song.
      if (v && v.currentTime > 3) { v.currentTime = 0; return; }
      var p = player();
      if (p && p.previousVideo) p.previousVideo();
      else if (!clickPrevNext('prev')) history.back();
    },
    seekTo: function (seconds) { var v = video(); if (v) v.currentTime = seconds; }
  };

  // --- 2. Reporting ------------------------------------------------------------
  var lastId = null, lastPlaying = null;

  function reportTrack() {
    var id = currentId();
    if (!id || id === lastId) return;
    var title = '', author = '', seconds = 0;
    try {
      var d = player().getPlayerResponse().videoDetails;
      title = d.title || ''; author = d.author || ''; seconds = Number(d.lengthSeconds) || 0;
    } catch (e) {}
    if (!title && navigator.mediaSession && navigator.mediaSession.metadata) {
      title = navigator.mediaSession.metadata.title || '';
      author = navigator.mediaSession.metadata.artist || '';
    }
    if (!title) return; // page not ready yet, try again next tick
    if (!seconds) { var v = video(); if (v && isFinite(v.duration)) seconds = Math.round(v.duration); }
    lastId = id;
    app.onTrack(id, title, author, seconds);
  }

  function reportState(force) {
    var playing = isPlaying();
    if (!force && playing === lastPlaying) return;
    lastPlaying = playing;
    var v = video();
    app.onState(playing, v ? v.currentTime : 0);
  }

  // Pauses made with YouTube's own on-screen controls while the app is open
  // count as user pauses too.
  document.addEventListener('pause', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') {
      if (!window.YMBackground) userPaused = true;
      reportState(false);
    }
  }, true);
  document.addEventListener('play', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') {
      userPaused = false;
      wasPlaying = true;
      reportState(false);
    }
  }, true);
  document.addEventListener('seeked', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') reportState(true);
  }, true);

  // --- 3. Background guard -----------------------------------------------------
  var wasPlaying = false, resumeAttempts = 0;

  function dismissStillWatchingPrompt() {
    var selectors = ['ytm-confirm-dialog-renderer', 'ytmusic-you-there-renderer', 'yt-confirm-dialog-renderer'];
    for (var i = 0; i < selectors.length; i++) {
      var dialog = document.querySelector(selectors[i]);
      if (!dialog) continue;
      var buttons = dialog.querySelectorAll('button');
      // Only click when there is exactly one button, so we can't pick wrong.
      if (buttons.length === 1) { buttons[0].click(); return; }
    }
  }

  function guard() {
    var p = player();
    var state = p && p.getPlayerState ? p.getPlayerState() : -1;
    if (state === PLAYING || isPlaying()) { wasPlaying = true; resumeAttempts = 0; return; }
    if (!window.YMBackground) { wasPlaying = false; resumeAttempts = 0; return; }
    if (state !== PAUSED || !wasPlaying || userPaused) return;
    if (resumeAttempts >= 3) return;          // give up rather than fight forever
    if (!app.canAutoResume()) return;         // phone call etc.: stay paused
    resumeAttempts++;
    dismissStillWatchingPrompt();
    if (p.playVideo) p.playVideo();
  }

  // --- Ticks -------------------------------------------------------------------
  setInterval(function () { reportTrack(); reportState(false); }, 1000);
  setInterval(function () { if (isPlaying()) reportState(true); }, 5000); // keep seek bar in sync
  setInterval(guard, 5000);
})();

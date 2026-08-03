package io.github.david419kr.niconicoyt.extension.niconicocomments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.List;

/** Self-contained bridge between YouTube bytecode hooks, chat networking, and the overlay. */
public final class NiconicoCommentsController {
    private static final String LOG_TAG = "NiconicoComments";
    private static final String PREFERENCES_NAME = "niconico_comments";
    private static final String ENABLED_KEY = "overlay_enabled";
    private static final long SEEK_THRESHOLD_MS = 12_000;
    private static final Object SOURCE_LOCK = new Object();

    private static WeakReference<ViewGroup> overlayRootRef = new WeakReference<>(null);
    private static WeakReference<NiconicoCommentsOverlayView> overlayViewRef =
            new WeakReference<>(null);

    private static volatile String currentVideoId = "";
    private static volatile boolean playerVisible;
    private static volatile boolean videoPlaying;
    private static volatile boolean commentsEnabled = true;
    private static volatile boolean preferenceLoaded;
    private static volatile long sampledVideoTimeMs = -1;
    private static volatile long sampleElapsedRealtimeMs;

    private static YoutubeChatSource activeSource;
    private static Thread activeSourceThread;
    private static String completedSourceVideoId = "";

    private static final YoutubeChatSource.Listener CHAT_LISTENER =
            new YoutubeChatSource.Listener() {
                @Override
                public long getVideoTimeMs() {
                    return getVideoTime();
                }

                @Override
                public void onComments(String videoId, List<NiconicoComment> comments) {
                    if (!commentsEnabled || !videoId.equals(currentVideoId)) {
                        return;
                    }
                    NiconicoCommentsOverlayView view = overlayViewRef.get();
                    if (view != null) {
                        view.post(() -> {
                            if (commentsEnabled && videoId.equals(currentVideoId)) {
                                view.addComments(comments);
                            }
                        });
                    }
                }

                @Override
                public void onReplayReset(String videoId) {
                    if (!videoId.equals(currentVideoId)) {
                        return;
                    }
                    NiconicoCommentsOverlayView view = overlayViewRef.get();
                    if (view != null) {
                        view.post(view::clearComments);
                    }
                }
            };

    private NiconicoCommentsController() {
    }

    /** Injection point called when YouTube creates its player overlay. */
    public static void initialize(ViewGroup overlayRoot) {
        if (overlayRoot == null) {
            return;
        }
        loadPreference(overlayRoot.getContext());
        overlayRoot.post(() -> {
            try {
                attachToOverlay(overlayRoot);
                maybeStartChatSource();
            } catch (Exception exception) {
                Log.e(LOG_TAG, "Overlay initialization failed", exception);
            }
        });
    }

    /** Injection point called when the foreground video changes. */
    public static void onVideoIdChanged(String videoId) {
        String nextVideoId = videoId == null ? "" : videoId;
        if (nextVideoId.equals(currentVideoId)) {
            return;
        }

        currentVideoId = nextVideoId;
        sampledVideoTimeMs = -1;
        sampleElapsedRealtimeMs = SystemClock.elapsedRealtime();
        synchronized (SOURCE_LOCK) {
            completedSourceVideoId = "";
            stopChatSourceLocked();
        }

        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.post(() -> {
                view.setVideoId(nextVideoId);
                applyViewState(view);
                maybeStartChatSource();
            });
        }
    }

    /** Injection point receiving YouTube's current player presentation state. */
    public static void setPlayerType(Enum<?> playerType) {
        String name = playerType == null ? "" : playerType.name();
        playerVisible = "WATCH_WHILE_MAXIMIZED".equals(name)
                || "WATCH_WHILE_FULLSCREEN".equals(name)
                || "WATCH_WHILE_SLIDING_MAXIMIZED_FULLSCREEN".equals(name);
        updateViewState();
    }

    /** Injection point receiving YouTube's play/pause state. */
    public static void setVideoState(Enum<?> videoState) {
        boolean nextPlaying = videoState != null && "PLAYING".equals(videoState.name());
        if (videoPlaying && !nextPlaying) {
            sampledVideoTimeMs = getVideoTime();
        }
        sampleElapsedRealtimeMs = SystemClock.elapsedRealtime();
        videoPlaying = nextPlaying;
        updateViewState();
        maybeStartChatSource();
    }

    /** Injection point receiving YouTube's periodic video-time sample. */
    public static void setVideoTime(long videoTimeMs) {
        long predictedVideoTimeMs = getVideoTime();
        boolean seeked = predictedVideoTimeMs >= 0
                && Math.abs(videoTimeMs - predictedVideoTimeMs) > SEEK_THRESHOLD_MS;

        sampledVideoTimeMs = videoTimeMs;
        sampleElapsedRealtimeMs = SystemClock.elapsedRealtime();
        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            if (seeked) {
                view.post(view::clearComments);
            } else {
                view.postInvalidateOnAnimation();
            }
        }
    }

    static long getVideoTime() {
        long sampledTime = sampledVideoTimeMs;
        if (sampledTime < 0 || !videoPlaying) {
            return sampledTime;
        }
        return sampledTime + Math.max(0, SystemClock.elapsedRealtime() - sampleElapsedRealtimeMs);
    }

    static boolean isCommentsEnabled() {
        return commentsEnabled;
    }

    static boolean toggleCommentsEnabled(Context context) {
        loadPreference(context);
        boolean enabled = !commentsEnabled;
        commentsEnabled = enabled;
        context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ENABLED_KEY, enabled)
                .apply();

        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.post(() -> {
                if (!enabled) {
                    view.clearComments();
                }
                applyViewState(view);
            });
        }

        synchronized (SOURCE_LOCK) {
            if (enabled) {
                completedSourceVideoId = "";
            } else {
                stopChatSourceLocked();
            }
        }
        if (enabled) {
            maybeStartChatSource();
        }
        return enabled;
    }

    static void loadPreference(Context context) {
        if (preferenceLoaded || context == null) {
            return;
        }
        synchronized (SOURCE_LOCK) {
            if (preferenceLoaded) {
                return;
            }
            SharedPreferences preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            commentsEnabled = preferences.getBoolean(ENABLED_KEY, true);
            preferenceLoaded = true;
        }
    }

    private static void attachToOverlay(ViewGroup overlayRoot) {
        NiconicoCommentsOverlayView currentView = overlayViewRef.get();
        ViewGroup currentRoot = overlayRootRef.get();
        if (currentRoot == overlayRoot && currentView != null && currentView.getParent() == overlayRoot) {
            currentView.setVideoId(currentVideoId);
            applyViewState(currentView);
            return;
        }

        if (currentView != null && currentView.getParent() instanceof ViewGroup) {
            ((ViewGroup) currentView.getParent()).removeView(currentView);
        }

        NiconicoCommentsOverlayView newView = new NiconicoCommentsOverlayView(overlayRoot.getContext());
        newView.setVideoId(currentVideoId);
        newView.setClickable(false);
        newView.setFocusable(false);
        newView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlayRoot.addView(
                newView,
                0,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        overlayRootRef = new WeakReference<>(overlayRoot);
        overlayViewRef = new WeakReference<>(newView);
        applyViewState(newView);
    }

    private static void maybeStartChatSource() {
        if (!commentsEnabled || currentVideoId.isEmpty() || overlayRootRef.get() == null) {
            return;
        }

        synchronized (SOURCE_LOCK) {
            if (currentVideoId.equals(completedSourceVideoId)) {
                return;
            }
            if (activeSourceThread != null && activeSourceThread.isAlive()) {
                return;
            }

            String sourceVideoId = currentVideoId;
            YoutubeChatSource source = new YoutubeChatSource(sourceVideoId, CHAT_LISTENER);
            Thread thread = new Thread(() -> {
                try {
                    source.run();
                } finally {
                    synchronized (SOURCE_LOCK) {
                        if (activeSource == source) {
                            activeSource = null;
                            activeSourceThread = null;
                            completedSourceVideoId = sourceVideoId;
                        }
                    }
                }
            }, "NiconicoYoutubeChat");
            thread.setDaemon(true);
            activeSource = source;
            activeSourceThread = thread;
            thread.start();
        }
    }

    private static void stopChatSourceLocked() {
        YoutubeChatSource source = activeSource;
        Thread thread = activeSourceThread;
        activeSource = null;
        activeSourceThread = null;
        if (source != null) {
            source.stop();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void updateViewState() {
        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.post(() -> applyViewState(view));
        }
    }

    private static void applyViewState(NiconicoCommentsOverlayView view) {
        view.setOverlayActive(commentsEnabled && playerVisible && !currentVideoId.isEmpty());
        view.setVideoPlaying(videoPlaying);
    }
}

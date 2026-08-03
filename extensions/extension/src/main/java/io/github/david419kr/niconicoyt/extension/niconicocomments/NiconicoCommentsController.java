package io.github.david419kr.niconicoyt.extension.niconicocomments;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Self-contained bridge between YouTube bytecode hooks and the overlay view. */
public final class NiconicoCommentsController {
    private static final String LOG_TAG = "NiconicoComments";

    private static WeakReference<ViewGroup> overlayRootRef = new WeakReference<>(null);
    private static WeakReference<NiconicoCommentsOverlayView> overlayViewRef =
            new WeakReference<>(null);

    private static volatile String currentVideoId = "";
    private static volatile boolean playerVisible;
    private static volatile boolean videoPlaying;
    private static volatile long sampledVideoTimeMs = -1;
    private static volatile long sampleElapsedRealtimeMs;

    private NiconicoCommentsController() {
    }

    /** Injection point called when YouTube creates its player overlay. */
    public static void initialize(ViewGroup overlayRoot) {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.post(() -> {
            try {
                attachToOverlay(overlayRoot);
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
        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.post(() -> {
                view.setVideoId(nextVideoId);
                applyViewState(view);
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
    }

    /** Injection point receiving YouTube's periodic video-time sample. */
    public static void setVideoTime(long videoTimeMs) {
        sampledVideoTimeMs = videoTimeMs;
        sampleElapsedRealtimeMs = SystemClock.elapsedRealtime();
        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.postInvalidateOnAnimation();
        }
    }

    static long getVideoTime() {
        long sampledTime = sampledVideoTimeMs;
        if (sampledTime < 0 || !videoPlaying) {
            return sampledTime;
        }
        return sampledTime + Math.max(0, SystemClock.elapsedRealtime() - sampleElapsedRealtimeMs);
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

    private static void updateViewState() {
        NiconicoCommentsOverlayView view = overlayViewRef.get();
        if (view != null) {
            view.post(() -> applyViewState(view));
        }
    }

    private static void applyViewState(NiconicoCommentsOverlayView view) {
        view.setOverlayActive(playerVisible && !currentVideoId.isEmpty());
        view.setVideoPlaying(videoPlaying);
    }
}

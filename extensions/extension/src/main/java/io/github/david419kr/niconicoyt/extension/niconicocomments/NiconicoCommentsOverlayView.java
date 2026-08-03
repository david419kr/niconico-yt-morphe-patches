package io.github.david419kr.niconicoyt.extension.niconicocomments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Draws live and replay chat comments on the YouTube playback timeline. */
final class NiconicoCommentsOverlayView extends View {
    private static final long COMMENT_DURATION_MS = 7_000;
    private static final long LIVE_BATCH_MAX_SPREAD_MS = 7_000;
    private static final long LIVE_BATCH_DEFAULT_STEP_MS = 350;
    private static final long IDLE_FRAME_DELAY_MS = 250;
    private static final int MAX_BUFFERED_COMMENTS = 500;
    private static final int MEMBERSHIP_COLOR = Color.rgb(43, 166, 64);

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final List<DisplayComment> comments = new ArrayList<>();
    private final float laneHeight;
    private final float firstBaseline;
    private final float backgroundPadding;
    private final float backgroundRadius;

    private String videoId = "";
    private boolean overlayActive;
    private boolean videoPlaying;
    private int nextLane;

    NiconicoCommentsOverlayView(Context context) {
        super(context);

        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = density * context.getResources().getConfiguration().fontScale;
        float textSize = 20 * scaledDensity;

        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setTextSize(textSize);
        fillPaint.setTypeface(Typeface.DEFAULT_BOLD);

        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2 * density);
        strokePaint.setTextSize(textSize);
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);

        fillPaint.getFontMetrics(fontMetrics);
        laneHeight = fontMetrics.descent - fontMetrics.ascent + (8 * density);
        firstBaseline = -fontMetrics.ascent + (12 * density);
        backgroundPadding = 4 * density;
        backgroundRadius = 3 * density;

        setWillNotDraw(false);
        setVisibility(GONE);
    }

    void setVideoId(String nextVideoId) {
        if (nextVideoId.equals(videoId)) {
            return;
        }
        videoId = nextVideoId;
        clearComments();
    }

    void setOverlayActive(boolean active) {
        if (overlayActive == active) {
            return;
        }
        overlayActive = active;
        setVisibility(active ? VISIBLE : GONE);
        requestNextFrame();
    }

    void setVideoPlaying(boolean playing) {
        if (videoPlaying == playing) {
            return;
        }
        videoPlaying = playing;
        requestNextFrame();
    }

    void addComments(List<NiconicoComment> incoming) {
        if (incoming.isEmpty()) {
            return;
        }
        long videoTimeMs = NiconicoCommentsController.getVideoTime();
        if (videoTimeMs < 0) {
            return;
        }

        long spreadStepMs = Math.min(
                LIVE_BATCH_DEFAULT_STEP_MS,
                LIVE_BATCH_MAX_SPREAD_MS / Math.max(1, incoming.size())
        );
        for (int index = 0; index < incoming.size(); index++) {
            NiconicoComment comment = incoming.get(index);
            long startTimeMs = comment.videoOffsetMs >= 0
                    ? comment.videoOffsetMs
                    : videoTimeMs + (index * spreadStepMs);
            comments.add(new DisplayComment(comment, startTimeMs, nextLane++));
        }
        while (comments.size() > MAX_BUFFERED_COMMENTS) {
            comments.remove(0);
        }
        removeExpiredComments(videoTimeMs);
        requestNextFrame();
    }

    void clearComments() {
        comments.clear();
        nextLane = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!overlayActive || videoId.isEmpty()) {
            return;
        }

        long videoTimeMs = NiconicoCommentsController.getVideoTime();
        if (videoTimeMs < 0) {
            if (videoPlaying) {
                postInvalidateDelayed(IDLE_FRAME_DELAY_MS);
            }
            return;
        }

        int laneCount = Math.max(1, (int) ((getHeight() - firstBaseline) / laneHeight) + 1);
        boolean hasActiveOrUpcomingComment = false;
        Iterator<DisplayComment> iterator = comments.iterator();
        while (iterator.hasNext()) {
            DisplayComment displayComment = iterator.next();
            long elapsedMs = videoTimeMs - displayComment.startTimeMs;
            if (elapsedMs < 0) {
                hasActiveOrUpcomingComment = true;
                continue;
            }
            if (elapsedMs > COMMENT_DURATION_MS) {
                iterator.remove();
                continue;
            }

            hasActiveOrUpcomingComment = true;
            NiconicoComment comment = displayComment.comment;
            float textWidth = fillPaint.measureText(comment.text);
            float progress = (float) elapsedMs / COMMENT_DURATION_MS;
            float x = getWidth() - (progress * (getWidth() + textWidth));
            float y = firstBaseline + ((displayComment.lane % laneCount) * laneHeight);

            if (comment.backgroundColor != Color.TRANSPARENT) {
                backgroundPaint.setColor(comment.backgroundColor);
                canvas.drawRoundRect(
                        x - backgroundPadding,
                        y + fontMetrics.ascent - backgroundPadding,
                        x + textWidth + backgroundPadding,
                        y + fontMetrics.descent + backgroundPadding,
                        backgroundRadius,
                        backgroundRadius,
                        backgroundPaint
                );
            }

            fillPaint.setColor(comment.membership ? MEMBERSHIP_COLOR : Color.WHITE);
            canvas.drawText(comment.text, x, y, strokePaint);
            canvas.drawText(comment.text, x, y, fillPaint);
        }

        if (videoPlaying && hasActiveOrUpcomingComment) {
            postInvalidateOnAnimation();
        }
    }

    private void removeExpiredComments(long videoTimeMs) {
        Iterator<DisplayComment> iterator = comments.iterator();
        while (iterator.hasNext()) {
            if (videoTimeMs - iterator.next().startTimeMs > COMMENT_DURATION_MS) {
                iterator.remove();
            }
        }
    }

    private void requestNextFrame() {
        if (overlayActive) {
            postInvalidateOnAnimation();
        }
    }

    private static final class DisplayComment {
        final NiconicoComment comment;
        final long startTimeMs;
        final int lane;

        DisplayComment(NiconicoComment comment, long startTimeMs, int lane) {
            this.comment = comment;
            this.startTimeMs = startTimeMs;
            this.lane = lane;
        }
    }
}

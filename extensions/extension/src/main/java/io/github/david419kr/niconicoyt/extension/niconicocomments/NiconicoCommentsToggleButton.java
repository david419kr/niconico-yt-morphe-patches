package io.github.david419kr.niconicoyt.extension.niconicocomments;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Adds a persistent on/off control beside Morphe's lower-right player buttons. */
public final class NiconicoCommentsToggleButton {
    private static final String LOG_TAG = "NiconicoComments";
    private static final String MORPHE_BUTTON_CLASS =
            "app.morphe.extension.youtube.videoplayer.PlayerOverlayButton";
    private static final int ENABLED_COLOR = 0xffffffff;
    private static final int DISABLED_COLOR = 0x66ffffff;

    private static WeakReference<View> sourceButtonRef = new WeakReference<>(null);
    private static WeakReference<TextView> buttonRef = new WeakReference<>(null);

    private NiconicoCommentsToggleButton() {
    }

    /** Injection point called with YouTube's fullscreen button as the layout/style anchor. */
    public static void initializeButton(View sourceButton) {
        if (sourceButton == null) {
            return;
        }
        try {
            TextView existing = buttonRef.get();
            if (sourceButtonRef.get() == sourceButton
                    && existing != null
                    && existing.isAttachedToWindow()) {
                updateState(existing);
                return;
            }

            NiconicoCommentsController.loadPreference(sourceButton.getContext());
            View.OnClickListener clickListener = view -> {
                NiconicoCommentsController.toggleCommentsEnabled(view.getContext());
                updateState((TextView) view);
            };

            TextView button = addUsingMorpheButtonHost(sourceButton, clickListener);
            if (button == null) {
                button = addStandaloneButton(sourceButton, clickListener);
            }
            if (button == null) {
                return;
            }

            configureButton(button);
            sourceButtonRef = new WeakReference<>(sourceButton);
            buttonRef = new WeakReference<>(button);
        } catch (Exception exception) {
            Log.e(LOG_TAG, "Toggle button initialization failed", exception);
        }
    }

    private static TextView addUsingMorpheButtonHost(
            View sourceButton,
            View.OnClickListener clickListener
    ) {
        try {
            Class<?> buttonClass = Class.forName(MORPHE_BUTTON_CLASS);
            Method addButton = buttonClass.getMethod(
                    "addButtonWithTextOverlay",
                    View.class,
                    View.OnClickListener.class,
                    View.OnLongClickListener.class
            );
            Object result = addButton.invoke(null, sourceButton, clickListener, null);
            return result instanceof TextView ? (TextView) result : null;
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception exception) {
            Log.w(LOG_TAG, "Morphe player-button host unavailable; using standalone layout", exception);
            return null;
        }
    }

    private static TextView addStandaloneButton(
            View sourceButton,
            View.OnClickListener clickListener
    ) {
        if (!(sourceButton.getParent() instanceof ViewGroup)) {
            return null;
        }
        ViewGroup parent = (ViewGroup) sourceButton.getParent();
        TextView button = new TextView(sourceButton.getContext());
        button.setId(View.generateViewId());
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(clickListener);
        parent.addView(button);

        final Drawable.ConstantState[] lastBackground = new Drawable.ConstantState[1];
        button.getViewTreeObserver().addOnPreDrawListener(() -> {
            ViewGroup.LayoutParams sourceParams = sourceButton.getLayoutParams();
            ViewGroup.LayoutParams buttonParams = button.getLayoutParams();
            if (sourceParams != null
                    && (buttonParams == null
                    || buttonParams.width != sourceParams.width
                    || buttonParams.height != sourceParams.height)) {
                if (sourceParams instanceof ViewGroup.MarginLayoutParams) {
                    button.setLayoutParams(new ViewGroup.MarginLayoutParams(sourceParams));
                } else {
                    button.setLayoutParams(
                            new ViewGroup.LayoutParams(sourceParams.width, sourceParams.height)
                    );
                }
            }

            button.setPadding(
                    sourceButton.getPaddingLeft(),
                    sourceButton.getPaddingTop(),
                    sourceButton.getPaddingRight(),
                    sourceButton.getPaddingBottom()
            );
            button.setX(sourceButton.getX() - sourceButton.getWidth());
            button.setY(sourceButton.getY());
            button.setAlpha(sourceButton.getAlpha());
            button.setVisibility(sourceButton.getVisibility());

            Drawable background = sourceButton.getBackground();
            Drawable.ConstantState state = background == null ? null : background.getConstantState();
            if (state != lastBackground[0]) {
                button.setBackground(state == null ? background : state.newDrawable().mutate());
                lastBackground[0] = state;
            }
            reserveChapterTitleSpace(sourceButton, sourceButton.getWidth());
            return true;
        });
        return button;
    }

    @SuppressWarnings("DiscouragedApi")
    private static void reserveChapterTitleSpace(View sourceButton, int width) {
        if (width <= 0) {
            return;
        }
        int id = sourceButton.getResources().getIdentifier(
                "time_bar_chapter_title_container",
                "id",
                sourceButton.getContext().getPackageName()
        );
        if (id == 0) {
            return;
        }

        View root = sourceButton;
        while (root.getParent() instanceof ViewGroup) {
            root = (View) root.getParent();
        }
        View chapterTitle = root.findViewById(id);
        if (chapterTitle != null
                && chapterTitle.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) chapterTitle.getLayoutParams();
            if (params.getMarginEnd() < width) {
                params.setMarginEnd(width);
                chapterTitle.setLayoutParams(params);
            }
        }
    }

    private static void configureButton(TextView button) {
        button.setText("弾");
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        updateState(button);
    }

    private static void updateState(TextView button) {
        boolean enabled = NiconicoCommentsController.isCommentsEnabled();
        button.setTextColor(enabled ? ENABLED_COLOR : DISABLED_COLOR);
        String state = enabled ? "on" : "off";
        button.setContentDescription("Niconico comments " + state);
        button.setTooltipText("Niconico comments: " + state);
    }
}

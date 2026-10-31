package com.termux.app;

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R1-only desktop window composition. Keeps TermuxActivity, TermuxService,
 * TerminalSession and the terminal/IME hierarchy unchanged.
 *
 * We never infer the caption button positions from screen size or DeX branding.
 * If Android does not provide safe bounding rectangles, the header is not drawn.
 */
public final class TacticDesktopShell {
    private final TermuxActivity activity;
    private final FrameLayout frame;
    private final FrameLayout caption;
    private final TermuxActivityRootView terminalRoot;
    private final View mobileToolbar;
    private final LinearLayout strip;
    private final TextView brand;
    private final TextView sessionsButton;
    private final TextView newButton;
    private final TextView moreButton;
    private final TextView floatingMenu;
    private WindowInsets lastInsets;
    private boolean edgeToEdge;
    private boolean captionControlsVisible;

    public TacticDesktopShell(TermuxActivity activity) {
        this.activity = activity;
        frame = activity.findViewById(R.id.tactic_shell_frame);
        caption = activity.findViewById(R.id.tactic_caption_overlay);
        terminalRoot = activity.findViewById(R.id.activity_termux_root_view);
        mobileToolbar = activity.findViewById(R.id.newtermux_toolbar_container);

        strip = new LinearLayout(activity);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(4), 0, dp(4), 0);
        strip.setFocusable(false);
        brand = label("TACTIC", "Tactic Terminal", false);
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sessionsButton = label("Sessions", "Switch terminal sessions", true);
        newButton = label("+", "New terminal session", true);
        moreButton = label("⋯", "Terminal actions", true);
        strip.addView(brand);
        strip.addView(sessionsButton);
        strip.addView(newButton);
        strip.addView(moreButton);
        caption.addView(strip);

        sessionsButton.setOnClickListener(this::showSessions);
        newButton.setOnClickListener(v -> {
            if (activity.mTermuxTerminalSessionActivityClient != null) {
                activity.mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                returnFocusToTerminal();
            }
        });
        moreButton.setOnClickListener(this::showActions);

        floatingMenu = label("TACTIC  ⋯", "Tactic session and terminal actions", true);
        FrameLayout.LayoutParams floatParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(38), Gravity.END | Gravity.BOTTOM);
        floatParams.setMargins(dp(8), dp(8), dp(14), dp(14));
        frame.addView(floatingMenu, floatParams);
        floatingMenu.setVisibility(View.GONE);
        floatingMenu.setOnClickListener(this::showActions);
        floatingMenu.setOnLongClickListener(v -> { showSessions(v); return true; });

        // Apply only on the outer FrameLayout: do not replace the Activity's
        // existing insets and IME listeners on content/TerminalRootView.
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            lastInsets = insets;
            applyInsets(insets);
            return insets;
        });
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol && lastInsets != null) applyInsets(lastInsets);
        });
        frame.post(frame::requestApplyInsets);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private TextView label(String title, String description, boolean clickable) {
        TextView tv = new TextView(activity);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTextColor(activity.getResources().getColor(R.color.tactic_header_foreground, activity.getTheme()));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), 0, dp(10), 0);
        tv.setContentDescription(description);
        tv.setFocusable(clickable);
        tv.setClickable(clickable);
        if (clickable) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(activity.getResources().getColor(R.color.tactic_header_button, activity.getTheme()));
            bg.setCornerRadius(dp(10));
            tv.setBackground(bg);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        lp.setMarginEnd(dp(5));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void setEdgeToEdge(boolean enabled) {
        if (Build.VERSION.SDK_INT < 30 || edgeToEdge == enabled) return;
        edgeToEdge = enabled;
        activity.getWindow().setDecorFitsSystemWindows(!enabled);
        // Caption mode is explicitly opt-in and independently reversible.
        if (Build.VERSION.SDK_INT >= 35) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int flag = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND;
                controller.setSystemBarsAppearance(enabled ? flag : 0, flag);
            }
        }
    }

    private void applyInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT < 30 || insets == null) return;
        int width = frame.getWidth();
        if (width <= 0) return;

        int captionHeight = insets.getInsets(WindowInsets.Type.captionBar()).top;
        boolean hasCaption = Build.VERSION.SDK_INT >= 35
            && insets.isVisible(WindowInsets.Type.captionBar())
            && captionHeight > 0;
        // Some OEM freeform caption surfaces expose no safe bounding rects.
        // A windowed wide app must not acquire a duplicate permanent header.
        boolean windowedFallback = activity.isInMultiWindowMode() && width >= dp(450);
        boolean desktop = hasCaption || windowedFallback;
        setEdgeToEdge(hasCaption);
        mobileToolbar.setVisibility(desktop ? View.GONE : View.VISIBLE);
        terminalRoot.setFitsSystemWindows(!hasCaption);
        int top = hasCaption ? Math.max(captionHeight, insets.getInsets(WindowInsets.Type.statusBars()).top) : 0;
        int bottom = hasCaption ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : 0;
        if (terminalRoot.getPaddingTop() != top || terminalRoot.getPaddingBottom() != bottom)
            terminalRoot.setPadding(0, top, 0, bottom);

        boolean safe = false;
        if (hasCaption) {
            List<Rect> occupiedRects = insets.getBoundingRects(WindowInsets.Type.captionBar());
            List<int[]> occupied = new ArrayList<>();
            for (Rect rect : occupiedRects)
                occupied.add(new int[]{rect.left, rect.top, rect.right, rect.bottom});
            TacticHeaderGeometry.Span slot = TacticHeaderGeometry.safeSpan(
                width, captionHeight, occupied, dp(100), dp(7));
            if (slot != null && captionHeight >= dp(25)) {
                FrameLayout.LayoutParams overlayParams = (FrameLayout.LayoutParams) caption.getLayoutParams();
                if (overlayParams.height != captionHeight) {
                    overlayParams.height = captionHeight;
                    caption.setLayoutParams(overlayParams);
                }
                FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
                    slot.width(), captionHeight, Gravity.TOP | Gravity.START);
                stripParams.leftMargin = slot.left;
                strip.setLayoutParams(stripParams);
                boolean roomy = slot.width() >= dp(265);
                brand.setVisibility(roomy ? View.VISIBLE : View.GONE);
                sessionsButton.setVisibility(slot.width() >= dp(180) ? View.VISIBLE : View.GONE);
                int height = Math.min(dp(32), captionHeight - dp(4));
                for (View button : new View[]{brand, sessionsButton, newButton, moreButton}) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
                    if (lp.height != height) { lp.height = height; button.setLayoutParams(lp); }
                }
                safe = true;
            }
        }
        captionControlsVisible = safe;
        caption.setVisibility(safe ? View.VISIBLE : View.GONE);
        floatingMenu.setVisibility(desktop && !safe ? View.VISIBLE : View.GONE);
        if (safe) {
            caption.post(this::updateExclusionRects);
        } else {
            caption.setSystemGestureExclusionRects(Collections.emptyList());
        }
        refreshSessions();
    }

    private void updateExclusionRects() {
        if (!captionControlsVisible) return;
        List<Rect> interactive = new ArrayList<>();
        for (View control : new View[]{sessionsButton, newButton, moreButton}) {
            if (control.getVisibility() != View.VISIBLE || control.getWidth() <= 0) continue;
            int x = strip.getLeft() + control.getLeft();
            int y = strip.getTop() + control.getTop();
            interactive.add(new Rect(x, y, x + control.getWidth(), y + control.getHeight()));
        }
        // Never exclude the caption gap, logo, or whole header: preserve window dragging.
        caption.setSystemGestureExclusionRects(interactive);
    }

    public void refreshSessions() {
        if (activity.mTermuxService == null) {
            sessionsButton.setText("Sessions");
            return;
        }
        int count = activity.mTermuxService.getTermuxSessionsSize();
        sessionsButton.setText(count + (count == 1 ? " session ▾" : " sessions ▾"));
    }

    private void showSessions(View anchor) {
        if (activity.mTermuxService == null) return;
        List<TermuxSession> entries = new ArrayList<>(activity.mTermuxService.getTermuxSessions());
        if (entries.isEmpty()) return;
        PopupMenu popup = new PopupMenu(activity, anchor);
        TerminalSession active = activity.getCurrentSession();
        for (int i = 0; i < entries.size(); i++) {
            TerminalSession session = entries.get(i).getTerminalSession();
            String title = session.mSessionName == null || session.mSessionName.isEmpty()
                ? "Session " + (i + 1) : session.mSessionName;
            popup.getMenu().add(0, 100 + i, i, (session == active ? "●  " : "   ") + title);
        }
        popup.setOnMenuItemClickListener(item -> {
            int index = item.getItemId() - 100;
            if (index >= 0 && index < entries.size()
                && activity.mTermuxTerminalSessionActivityClient != null) {
                activity.mTermuxTerminalSessionActivityClient
                    .setCurrentSession(entries.get(index).getTerminalSession());
                returnFocusToTerminal();
            }
            return true;
        });
        popup.show();
    }

    private void showActions(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        android.view.Menu menu = popup.getMenu();
        menu.add(0, 1, 0, "Sessions");
        menu.add(0, 2, 1, "New session");
        menu.add(0, 3, 2, "Rename / close current session");
        menu.add(0, 4, 3, "Settings");
        menu.add(0, 5, 4, "Packages");
        menu.add(0, 6, 5, "Clear terminal");
        menu.add(0, 7, 6, "Autocorrect");
        menu.add(0, 8, 7, "Root toggle");
        menu.add(0, 9, 8, "Speech input");
        menu.add(0, 10, 9, "Command shortcuts");
        menu.add(0, 11, 10, "Extra keys");
        menu.add(0, 12, 11, "Terminal context menu");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: anchor.post(() -> showSessions(anchor)); break;
                case 2: newButton.performClick(); break;
                case 3: activity.showTacticSessionActions(anchor); break;
                case 4: activity.startActivity(new Intent(activity, SettingsActivity.class)); break;
                case 5: activity.showTacticPackages(anchor); break;
                case 6: clickLegacy(R.id.btn_clear_terminal); break;
                case 7: clickLegacy(R.id.btn_autocorrect_toggle); break;
                case 8: clickLegacy(R.id.btn_root_toggle); break;
                case 9: clickLegacy(R.id.btn_stt); break;
                case 10: activity.getDrawer().openDrawer(Gravity.START); break;
                case 11: activity.toggleTerminalToolbar(); break;
                case 12: activity.getTerminalView().showContextMenu(); break;
                default: return false;
            }
            return true;
        });
        popup.show();
    }

    private void clickLegacy(int id) {
        View legacy = activity.findViewById(id);
        if (legacy != null && legacy.hasOnClickListeners()) legacy.performClick();
        returnFocusToTerminal();
    }

    private void returnFocusToTerminal() {
        if (activity.mTerminalView != null) activity.mTerminalView.requestFocus();
    }
}

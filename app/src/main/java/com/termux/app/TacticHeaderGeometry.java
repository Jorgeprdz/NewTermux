package com.termux.app;

import java.util.ArrayList;
import java.util.List;

/** Pure-Java placement policy. Rectangles are [left, top, right, bottom] in window pixels.
 * No fixed Samsung/Android title-bar height or close-button offset is assumed. */
public final class TacticHeaderGeometry {
    public static final class Span {
        public final int left;
        public final int right;
        public Span(int left, int right) { this.left = left; this.right = right; }
        public int width() { return right - left; }
    }

    private TacticHeaderGeometry() { }

    /**
     * Largest contiguous portion of the caption row not covered by system bounding rects.
     * An empty/unknown occupied list is UNSAFE: do not place controls blindly.
     */
    public static Span safeSpan(int width, int height, List<int[]> occupied, int minWidth, int sideGap) {
        if (width <= 0 || height <= 0 || occupied == null || occupied.isEmpty()) return null;
        List<Span> gaps = new ArrayList<>();
        if (width - sideGap * 2 < minWidth) return null;
        gaps.add(new Span(sideGap, width - sideGap));
        for (int[] rect : occupied) {
            if (rect == null || rect.length < 4) continue;
            if (rect[3] <= 0 || rect[1] >= height || rect[2] <= 0 || rect[0] >= width) continue;
            int left = Math.max(0, rect[0]);
            int right = Math.min(width, rect[2]);
            if (right <= left) continue;
            List<Span> remaining = new ArrayList<>();
            for (Span gap : gaps) {
                if (left >= gap.right || right <= gap.left) {
                    remaining.add(gap);
                    continue;
                }
                if (left > gap.left) remaining.add(new Span(gap.left, left));
                if (right < gap.right) remaining.add(new Span(right, gap.right));
            }
            gaps = remaining;
        }
        Span best = null;
        for (Span gap : gaps) {
            if (gap.width() >= minWidth && (best == null || gap.width() > best.width()))
                best = gap;
        }
        return best;
    }
}

package com.termux.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TacticHeaderGeometryTest {
    @Test public void deXExternalDisplayWorksWithoutCaptionInsets() {
        assertTrue(TacticHeaderGeometry.shouldUseDesktop(false, false, true,
            true, 1000, 450, 840));
    }

    @Test public void wideLandscapeCanEnterDesktopButPortraitPhoneCannot() {
        assertTrue(TacticHeaderGeometry.shouldUseDesktop(false, false, false,
            true, 950, 450, 840));
        assertFalse(TacticHeaderGeometry.shouldUseDesktop(false, false, false,
            false, 950, 450, 840));
        assertFalse(TacticHeaderGeometry.shouldUseDesktop(false, false, false,
            true, 430, 450, 840));
    }

    @Test public void compactFreeformNeedsMinimumWidth() {
        assertTrue(TacticHeaderGeometry.shouldUseDesktop(false, true, false,
            false, 520, 450, 840));
        assertFalse(TacticHeaderGeometry.shouldUseDesktop(false, true, false,
            false, 320, 450, 840));
    }

    @Test public void avoidsNativeButtonsAtBothEnds() {
        TacticHeaderGeometry.Span span = TacticHeaderGeometry.safeSpan(1200, 42,
            Arrays.asList(new int[]{0, 0, 90, 42}, new int[]{1040, 0, 1200, 42}), 120, 8);
        assertNotNull(span);
        assertEquals(90, span.left);
        assertEquals(1040, span.right);
    }

    @Test public void neverAssumesNoNativeButtonsWhenRectsMissing() {
        assertNull(TacticHeaderGeometry.safeSpan(1200, 42, Collections.emptyList(), 100, 8));
    }

    @Test public void stopsBeforeMiddleControls() {
        TacticHeaderGeometry.Span span = TacticHeaderGeometry.safeSpan(680, 40,
            Arrays.asList(new int[]{0, 0, 70, 40}, new int[]{310, 0, 400, 40},
                new int[]{600, 0, 680, 40}), 100, 8);
        assertNotNull(span);
        assertEquals(70, span.left);
        assertEquals(310, span.right);
    }

    @Test public void ignoresRectsOutsideCaptionHeight() {
        TacticHeaderGeometry.Span span = TacticHeaderGeometry.safeSpan(400, 40,
            Arrays.asList(new int[]{0, 40, 400, 120}, new int[]{350, 0, 400, 40}), 100, 8);
        assertNotNull(span);
        assertEquals(8, span.left);
        assertEquals(350, span.right);
    }

    @Test public void fallsBackWhenOnlyNarrowGapsRemain() {
        assertNull(TacticHeaderGeometry.safeSpan(280, 32,
            Arrays.asList(new int[]{0, 0, 110, 32}, new int[]{175, 0, 280, 32}), 100, 8));
    }
}

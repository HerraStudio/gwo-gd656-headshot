package dev.herrastudio.gwokillicon;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadshotContextTest {
    private static final UUID FIRST = UUID.fromString("160e1aa3-32f8-493a-832b-65cb4b16c9ec");
    private static final UUID SECOND = UUID.fromString("171be4d7-b693-4b58-8e12-6818e00a7f74");

    @Test
    void matchesOnlyVictimOfActiveHeadshot() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        assertTrue(context.wasHeadshot(FIRST));
        assertFalse(context.wasHeadshot(SECOND));
        assertFalse(context.wasHeadshot(null));
    }

    @Test
    void rejectsResultsOutsideAnImpact() {
        HeadshotContext context = new HeadshotContext();
        context.capture(FIRST, true);
        assertFalse(context.wasHeadshot(FIRST));
        context.begin();
        assertFalse(context.wasHeadshot(FIRST));
    }

    @Test
    void finishedNonlethalHeadshotDoesNotMarkLaterBodyHit() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.end();
        context.begin();
        assertFalse(context.wasHeadshot(FIRST));
        context.capture(FIRST, false);
        assertFalse(context.wasHeadshot(FIRST));
    }

    @Test
    void penetrationCannotCarryHeadshotToTheNextTarget() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.end();
        context.begin();
        context.capture(SECOND, false);
        assertFalse(context.wasHeadshot(FIRST));
        assertFalse(context.wasHeadshot(SECOND));
    }

    @Test
    void penetrationCanMarkTheSecondTargetIndependently() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, false);
        context.end();
        context.begin();
        context.capture(SECOND, true);
        assertFalse(context.wasHeadshot(FIRST));
        assertTrue(context.wasHeadshot(SECOND));
    }

    @Test
    void endingScopeClearsResultImmediatelyAndCannotBeRecaptured() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.end();
        assertFalse(context.wasHeadshot(FIRST));
        context.capture(SECOND, true);
        assertFalse(context.wasHeadshot(SECOND));
    }

    @Test
    void captureReplacesHeadWithBodyWithinTheSameImpact() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.capture(FIRST, false);
        assertFalse(context.wasHeadshot(FIRST));
    }

    @Test
    void captureReplacesBodyWithHeadWithinTheSameImpact() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, false);
        context.capture(FIRST, true);
        assertTrue(context.wasHeadshot(FIRST));
    }

    @Test
    void newImpactClearsAnUnfinishedPreviousScope() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.begin();
        assertFalse(context.wasHeadshot(FIRST));
        assertFalse(context.wasHeadshot(SECOND));
    }

    @Test
    void invalidCaptureClearsAnyEarlierResult() {
        HeadshotContext context = new HeadshotContext();
        context.begin();
        context.capture(FIRST, true);
        context.capture(null, true);
        assertFalse(context.wasHeadshot(FIRST));
    }
}

package de.v0idpacks.ui;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static de.v0idpacks.ui.UiMotion.Effect.*;

class UiMotionTest {
    @Test void skippedFramesStillDeliverFinalStateAndThenStop() {
        AtomicLong clock=new AtomicLong();UiMotion motion=new UiMotion(clock::get);
        assertFalse(motion.needsFrame());motion.start(OPEN);
        assertEquals(0,motion.progress(OPEN,0));clock.set(110_000_000);
        assertEquals(.875,motion.progress(OPEN,clock.get()),.001);
        motion.finishFrame(clock.get());assertTrue(motion.needsFrame());
        clock.set(2_000_000_000L);assertTrue(motion.needsFrame());
        assertEquals(1,motion.progress(OPEN,clock.get()));motion.finishFrame(clock.get());assertFalse(motion.needsFrame());
    }
    @Test void rapidChangesRestartOnlyTheirEffectAndDisablingFinishesEverything() {
        AtomicLong clock=new AtomicLong();UiMotion motion=new UiMotion(clock::get);
        motion.start(OPEN);motion.start(CONTENT);clock.set(50_000_000);motion.start(CONTENT);
        assertEquals(0,motion.progress(CONTENT,clock.get()));assertTrue(motion.progress(OPEN,clock.get())>0);
        motion.setEnabled(false);assertFalse(motion.needsFrame());assertEquals(1,motion.progress(CONTENT,clock.get()));
        motion.start(HOVER);assertFalse(motion.needsFrame());
        motion.setEnabled(true);motion.start(HOVER);assertTrue(motion.needsFrame());
    }
}

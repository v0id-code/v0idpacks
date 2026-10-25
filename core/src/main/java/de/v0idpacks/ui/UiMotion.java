package de.v0idpacks.ui;

import java.util.EnumMap;
import java.util.function.LongSupplier;

/** Bounded transitions. A final frame must be drawn before an effect is retired. */
final class UiMotion {
    enum Effect {
        OPEN(220), CONTENT(140), PREVIEW(200), MENU(150), HOVER(100), PRESS(120);
        final long nanos;
        Effect(long millis){nanos=millis*1_000_000;}
    }
    private final LongSupplier clock;
    private final EnumMap<Effect,Long> starts=new EnumMap<>(Effect.class);
    private boolean enabled=true;
    UiMotion(LongSupplier clock){this.clock=clock;}
    void setEnabled(boolean enabled){this.enabled=enabled;if(!enabled)starts.clear();}
    void start(Effect effect){if(enabled)starts.put(effect,clock.getAsLong());}
    boolean needsFrame(){return !starts.isEmpty();}
    float progress(Effect effect,long now) {
        Long start=starts.get(effect);if(start==null)return 1;
        double t=Math.clamp((double)(now-start)/effect.nanos,0,1);
        return (float)(1-Math.pow(1-t,3));
    }
    void finishFrame(long now){starts.entrySet().removeIf(e->now-e.getValue()>=e.getKey().nanos);}
}

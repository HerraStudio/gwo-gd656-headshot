package dev.herrastudio.gwokillicon;

import java.util.UUID;

/** GWO's original result is valid only while the corresponding impact applies its damage. */
public final class HeadshotContext {
    private boolean active;
    private Hit hit;

    public void begin() {
        active = true;
        hit = null;
    }

    public void capture(UUID victim, boolean headshot) {
        if (active) {
            hit = victim == null ? null : new Hit(victim, headshot);
        }
    }

    public boolean wasHeadshot(UUID victim) {
        return active && victim != null && hit != null && hit.headshot()
                && hit.victim().equals(victim);
    }

    public void end() {
        active = false;
        hit = null;
    }

    private record Hit(UUID victim, boolean headshot) { }
}

package dev.herrastudio.gwokillicon.bridge;

import java.util.UUID;

/** Read-only access to the active GWO impact; does not alter damage or hit classification. */
public interface BulletHeadshotAccess {
    boolean wasHeadshot(UUID victim);
}

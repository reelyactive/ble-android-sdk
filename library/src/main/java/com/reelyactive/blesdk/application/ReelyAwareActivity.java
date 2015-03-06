package com.reelyactive.blesdk.application;

/**
 * Implement this interface if you want your current activity to be notified when Reelceiver are detected.
 * // TODO Extend the possibilities, the API is too limited.
 */
public interface ReelyAwareActivity {
    public void onEnterRegion();

    public void onLeaveRegion();
}

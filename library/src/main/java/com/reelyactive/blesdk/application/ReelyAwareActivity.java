package com.reelyactive.blesdk.application;

import com.reelyactive.blesdk.support.ble.ScanResult;

/**
 * Implement this interface if you want your current activity to be notified when Reelceiver are detected.
 */
public interface ReelyAwareActivity {
    public void onScanStarted();

    public void onScanStopped();

    public void onEnterRegion(ScanResult beacon);

    public void onLeaveRegion(ScanResult beacon);
}

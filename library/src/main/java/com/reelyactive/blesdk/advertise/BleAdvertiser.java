package com.reelyactive.blesdk.advertise;

import com.reelyactive.blesdk.support.ble.ScanResult;

/**
 * Created by saiimons on 15-03-09.
 */
public abstract class BleAdvertiser {
    public abstract void startAdvertising(String uuid, ScanResult closestBeacon, String fallbackUrl);

    public abstract void stopAdvertising();

    public void startAdvertising(String uuid) {
        startAdvertising(uuid, null);
    }

    public void startAdvertising(String uuid, ScanResult closestBeacon) {
        startAdvertising(uuid, closestBeacon, "http://www.hyperlocalcontext.com/event");
    }
}

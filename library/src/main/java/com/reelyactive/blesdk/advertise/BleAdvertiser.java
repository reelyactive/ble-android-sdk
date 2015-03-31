package com.reelyactive.blesdk.advertise;

import com.reelyactive.blesdk.support.ble.ScanResult;

/**
 * Created by saiimons on 15-03-09.
 */
public interface BleAdvertiser {
    public void startAdvertising(String uuid, ScanResult closestBeacon);
    public void stopAdvertising();
}

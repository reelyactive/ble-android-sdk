package com.reelyactive.blesdk.support.ble.util;

import android.bluetooth.BluetoothAdapter;

/**
 * Created by saiimons on 15-03-02.
 */
public class BluetoothInterface {
    public static String getMacAddress() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getAddress();
    }
}

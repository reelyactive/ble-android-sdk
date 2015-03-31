package com.reelyactive.blesdk.advertise;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;

/**
 * Created by saiimons on 15-03-09.
 */
public class BleAdvertiserProvider {
    private static BleAdvertiser advertiserInstance;

    public static synchronized BleAdvertiser getAdvertiser(Context context) {
        if (advertiserInstance == null) {
            advertiserInstance = createInstance(context);
        }
        return advertiserInstance;
    }

    @TargetApi(21)
    private static BleAdvertiser createInstance(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (manager.getAdapter().isMultipleAdvertisementSupported()) {
                return new LBleAdvertiser(context, manager);
            }
        }
        return new DummyBleAdvertiser(context);
    }
}

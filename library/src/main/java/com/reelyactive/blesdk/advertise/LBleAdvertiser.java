package com.reelyactive.blesdk.advertise;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import com.reelyactive.blesdk.support.ble.ScanResult;

import hugo.weaving.DebugLog;

/**
 * Created by saiimons on 15-03-09.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LBleAdvertiser implements BleAdvertiser {
    private final BluetoothLeAdvertiser advertiser;
    private final Context context;
    private final AdvertiseCallback callback = new AdvertiseCallback() {
        @Override
        @DebugLog
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        @DebugLog
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
        }
    };

    public LBleAdvertiser(Context context, BluetoothManager manager) {
        this.advertiser = manager.getAdapter().getBluetoothLeAdvertiser();
        this.context = context;
    }

    @Override
    @DebugLog
    public void startAdvertising(String uuid, ScanResult closestBeaconId) {
        stopAdvertising();
        advertiser.startAdvertising(
                new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).build(),
                new AdvertiseData.Builder().addServiceUuid(ParcelUuid.fromString(uuid)).build(),
                callback
        );
    }

    @Override
    @DebugLog
    public void stopAdvertising() {
        try {
            advertiser.stopAdvertising(callback);
        } catch (Exception e) {

        }
    }
}

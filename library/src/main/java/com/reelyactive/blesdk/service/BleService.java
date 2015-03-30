package com.reelyactive.blesdk.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcelable;

import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompat;
import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompatProvider;
import com.reelyactive.blesdk.support.ble.ScanFilter;
import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hugo.weaving.DebugLog;

public class BleService extends Service {
    public static final String KEY_FILTER = "filter";
    public static final String KEY_EVENT_DATA = "event_data";
    /**
     * Keeps track of all current registered clients.
     */
    ArrayList<BleServiceCallback> mClients = new ArrayList<BleServiceCallback>();
    private BluetoothLeScannerCompat scanner;
    final ScanCallback callback = new ScanCallback();
    final ScanSettings lowPowerScan = new ScanSettings.Builder() //
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST) //
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED) //
            .setScanResultType(ScanSettings.SCAN_RESULT_TYPE_FULL) //
            .build();
    final ScanSettings higPowerScan = new ScanSettings.Builder() //
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST) //
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
            .setScanResultType(ScanSettings.SCAN_RESULT_TYPE_FULL) //
            .build();
    private ScanSettings currentSettings;
    private ScanSettings nextSettings;
    private ScanFilter currentFilter;
    private ScanFilter nextFilter;


    private final IBinder binder = new LocalBinder();

    @Override
    @DebugLog
    public void onCreate() {
        scanner = BluetoothLeScannerCompatProvider.getBluetoothLeScannerCompat(getApplicationContext());
    }

    @Override
    @DebugLog
    public void onDestroy() {
        // TODO make it possible to kill the scanner.
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public BleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BleService.this;
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public static enum Event {
        SCAN_STARTED,
        SCAN_STOPPED,
        IN_REGION,
        OUT_REGION,
        UNKNOWN;
        private static Event[] allValues = values();

        public static Event fromOrdinal(int n) {
            if (n >= 0 || n < UNKNOWN.ordinal()) {
                return allValues[n];
            }
            return UNKNOWN;
        }
    }

    public static enum ScanType {
        LOW_POWER,
        ACTIVE;
        private static ScanType[] allValues = values();

        public static ScanType fromOrdinal(int n) throws IllegalArgumentException {
            if (n < 0 || n >= allValues.length) {
                return LOW_POWER;
            }
            return allValues[n];
        }
    }

    @DebugLog
    public void registerClient(BleServiceCallback client) {
        mClients.add(client);
    }

    @DebugLog
    public void unregisterClient(BleServiceCallback client) {
        mClients.remove(client);
    }

    @DebugLog
    public void startScan() {
        nextSettings = nextSettings == null ? lowPowerScan : nextSettings;
        nextFilter = nextFilter == null ? (currentFilter == null ? new ScanFilter.Builder().build() : currentFilter) : nextFilter;
        if (currentSettings != nextSettings || nextFilter != currentFilter) {
            stopScan();
            notifyEvent(Event.SCAN_STARTED);
            scanner.startScan(Arrays.asList(nextFilter), nextSettings, callback); // TODO make it possible to scan using more filters
        }
        currentSettings = nextSettings;
        currentFilter = nextFilter;
    }

    @DebugLog
    public void stopScan() {
        scanner.stopScan(callback);
        notifyEvent(Event.SCAN_STOPPED);
    }

    @DebugLog
    public void setScanType(ScanType scanType) {
        nextSettings = ScanType.ACTIVE == scanType ? higPowerScan : lowPowerScan;
    }

    @DebugLog
    public void setScanFilter(ScanFilter scanFilter) {
        nextFilter = scanFilter;
    }

    @DebugLog
    public List<ScanResult> getMatchingRecentResults(List<ScanFilter> filters) {
        return scanner.getMatchingRecords(filters);
    }

    private void notifyEvent(Event event, Parcelable... data) {
        ScanResult result = null;
        if (data != null && data.length == 1) {
            result = (ScanResult) data[0];
        }
        for (int i = mClients.size() - 1; i >= 0; i--) {
            mClients.get(i).onBleEvent(event, result);
        }
    }

    class ScanCallback extends com.reelyactive.blesdk.support.ble.ScanCallback {
        @Override
        @DebugLog
        public void onScanResult(int callbackType, ScanResult result) {
            notifyEvent(callbackType != ScanSettings.CALLBACK_TYPE_MATCH_LOST ? Event.IN_REGION : Event.OUT_REGION, result);
        }
    }
}
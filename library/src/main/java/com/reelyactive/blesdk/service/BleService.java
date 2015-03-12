package com.reelyactive.blesdk.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;

import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompat;
import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompatProvider;
import com.reelyactive.blesdk.support.ble.ScanFilter;
import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanSettings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import hugo.weaving.DebugLog;

public class BleService extends Service {
    public static final String KEY_FILTER = "filter";
    public static final String KEY_EVENT_DATA = "event_data";
    /**
     * Keeps track of all current registered clients.
     */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
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


    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

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
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    public static enum Command {
        START_SCAN,
        STOP_SCAN,
        SET_SCAN_FILTER,
        SET_SCAN_TYPE,
        REGISTER_CLIENT,
        UNREGISTER_CLIENT,
        UNKNOWN;
        private static Command[] allValues = values();

        public static Command fromOrdinal(int n) {
            if (n >= 0 && n < UNKNOWN.ordinal()) {
                return allValues[n];
            }
            return UNKNOWN;
        }
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

    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends Handler {
        WeakReference<BleService> service;

        public IncomingHandler(BleService service) {
            this.service = new WeakReference<BleService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            BleService bleService = service.get();
            if (bleService == null) {
                return;
            }
            Command command = Command.fromOrdinal(msg.what);
            if (!bleService.handleCommands(command, convertParam(command, msg))) {
                super.handleMessage(msg);
            }
        }

        private Object convertParam(Command command, Message msg) {
            switch (command) {
                case SET_SCAN_TYPE:
                    return msg.arg1;
                case REGISTER_CLIENT:
                case UNREGISTER_CLIENT:
                    return msg.replyTo;
                case SET_SCAN_FILTER:
                    return msg.getData().getParcelable(KEY_FILTER);
            }
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Command command = Command.valueOf(intent.getAction());
        handleCommands(command, intent.getData());
        return START_STICKY;
    }

    @DebugLog
    private boolean handleCommands(Command command, Object param) {
        boolean handled = true;
        switch (command) {
            case UNREGISTER_CLIENT:
                mClients.remove((Messenger) param);
                break;
            case REGISTER_CLIENT:
                mClients.add((Messenger) param);
                break;
            case START_SCAN:
                startScan();
                break;
            case STOP_SCAN:
                stopScan();
                break;
            case SET_SCAN_TYPE:
                ScanType type = ScanType.fromOrdinal((int) param);
                nextSettings = ScanType.ACTIVE == type ? higPowerScan : lowPowerScan;
                break;
            case SET_SCAN_FILTER:
                nextFilter = (ScanFilter) param;
                break;
            default:
                handled = false;
        }
        return handled;
    }

    @DebugLog
    private void startScan() {
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
    private void stopScan() {
        scanner.stopScan(callback);
        notifyEvent(Event.SCAN_STOPPED);
    }

    private void notifyEvent(Event event, Parcelable... data) {
        Message msg = Message.obtain(null, event.ordinal());
        if (data != null && data.length == 1) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_EVENT_DATA, data[0]);
            msg.setData(bundle);
        }
        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
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
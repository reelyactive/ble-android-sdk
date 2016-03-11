package com.reelyactive.blesdk.support.ble;/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.reelyactive.blesdk.support.ble.util.Clock;
import com.reelyactive.blesdk.support.ble.util.Logger;
import com.reelyactive.blesdk.support.ble.util.SystemClock;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Implements Bluetooth LE scan related API on top of
 * {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR2} and later.
 * <p/>
 * This class delivers a callback on found, updated, and lost for devices matching a
 * {@link ScanFilter} filter during scan cycles.
 * <p/>
 * A scan cycle comprises a period when the Bluetooth Adapter is active and a period when the
 * Bluetooth adapter is idle. Having an idle period is energy efficient for long lived scans.
 * <p/>
 * This class can be accessed on multiple threads:
 * <ul>
 * <li> main thread (user) can call any of the BluetoothLeScanner APIs
 * <li> IntentService worker thread can call {@link #blockingScanCycle}
 * <li> AIDL binder thread can call {@link #leScanCallback.onLeScan}
 * </ul>
 *
 * @see <a href="http://go/ble-glossary">BLE Glossary</a>
 */
class JbBluetoothLeScannerCompat extends BluetoothLeScannerCompat {

    // Map of BD_ADDR->com.reelyactive.blesdk.support.ble.ScanResult for replay to new registrations.
    // Entries are evicted after SCAN_LOST_CYCLES cycles.
  /* @VisibleForTesting */ final HashMap<String, ScanResult> recentScanResults;
    /* @VisibleForTesting */ final HashMap<ScanCallback, ScanClient> serialClients;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothCrashResolver crashResolver;
    private Handler mainHandler;
    /**
     * The Bluetooth LE callback which will be registered with the OS,
     * to be fired on device discovery.
     */
    private final BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                /**
                 * Callback method called from the OS on each BLE device sighting.
                 * This method is invoked on the AIDL handler thread, so all methods
                 * called here must be synchronized.
                 *
                 * @param device The device discovered
                 * @param rssi The signal strength in dBm it was received at
                 * @param scanRecordBytes The raw byte payload buffer
                 */
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecordBytes) {
                    long currentTimeInNanos = TimeUnit.MILLISECONDS.toNanos(getClock().currentTimeMillis());
                    ScanResult result = new ScanResult(device, ScanRecord.parseFromBytes(scanRecordBytes), rssi,
                            currentTimeInNanos);
                    onScanResult(device.getAddress(), result);
                    if (crashResolver != null)
                        crashResolver.notifyScannedDevice(device, this);
                }
            };

    /**
     * Package constructor, called from {@link BluetoothLeScannerCompatProvider}.
     */
    JbBluetoothLeScannerCompat(
            Context context, BluetoothManager manager, AlarmManager alarmManager) {
        this(manager, alarmManager, new SystemClock(),
                PendingIntent.getBroadcast(context, 0 /* requestCode */,
                        new Intent(context, ScanWakefulBroadcastReceiver.class).putExtra(ScanWakefulService.EXTRA_USE_LOLLIPOP_API, false), 0 /* flags */));
        this.crashResolver = new BluetoothCrashResolver(context);
        this.crashResolver.start();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Testing constructor for the scanner.
     *
     * @VisibleForTesting
     */
    JbBluetoothLeScannerCompat(BluetoothManager manager, AlarmManager alarmManager,
                               Clock clock, PendingIntent alarmIntent) {
        super(clock, alarmManager, alarmIntent);
        Logger.logDebug("BLE 'JB' hardware access layer activated");
        this.bluetoothAdapter = manager.getAdapter();
        this.serialClients = new HashMap<ScanCallback, ScanClient>();
        this.recentScanResults = new HashMap<String, ScanResult>();
    }

    /**
     * The entry point blockingScanCycle executes a BLE Scan cycle and is called from the
     * com.reelyactive.blesdk.support.ble.ScanWakefulService. When this method ends, the service will signal the ScanWakefulBroadcast
     * receiver to release its wakelock and the phone will enter a sleep phase for the remainder of
     * the BLE scan cycle.
     * <p/>
     * This is called on the IntentService handler thread and hence is synchronized.
     * <p/>
     * Suppresses the experimental 'wait not in loop' warning because we don't mind exiting early.
     * Suppresses deprecation because this is the compatibility support.
     */
    @SuppressWarnings({"WaitNotInLoop", "deprecation"})
    synchronized void blockingScanCycle() {
        Logger.logDebug("Starting BLE Active Scan Cycle.");
        int activeMillis = getScanActiveMillis();
        if (activeMillis > 0) {
            try {
                bluetoothAdapter.startLeScan(leScanCallback);
            } catch (IllegalStateException e) {
                Logger.logError("Failed to start the scan", e);
            }

            // Sleep for the duration of the scan. No wakeups are expected, but catch is required.
            try {
                wait(activeMillis);
            } catch (InterruptedException e) {
                Logger.logError("Exception in ScanCycle Sleep", e);
            } finally {
                try {
                    bluetoothAdapter.stopLeScan(leScanCallback);
                } catch (NullPointerException e) {
                    // An NPE is thrown if Bluetooth has been reset since this blocking scan began.
                    Logger.logDebug("NPE thrown in BlockingScanCycle");
                } catch (IllegalStateException e) {
                    Logger.logError("Failed to stop the scan", e);
                }
                // Active BLE scan ends
                // Execute cycle complete to 1) detect lost devices
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        onScanCycleComplete();
                        callbackCycleCompleted();
                    }
                });
            }
            updateRepeatingAlarm();
        }
        Logger.logDebug("Stopping BLE Active Scan Cycle.");
    }

    private void callbackLostLeScanClients(String address, ScanResult result) {
        for (ScanClient client : serialClients.values()) {
            int wantAny = client.settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
            int wantLost = client.settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_MATCH_LOST;

            if (client.addressesSeen.remove(address) && (wantAny | wantLost) != 0) {

                // Catch any exceptions and log them but continue processing other scan results.
                try {
                    client.callback.onScanResult(ScanSettings.CALLBACK_TYPE_MATCH_LOST, result);
                } catch (Exception e) {
                    Logger.logError("Failure while sending 'lost' scan result to listener", e);
                }
            }
        }
    }

    private void callbackCycleCompleted() {
        for (ScanClient client : serialClients.values()) {
            client.callback.onScanCycleCompleted();
        }
    }

    /**
     * Process a single scan result, sending it directly
     * to any active listeners who want to know.
     *
     * @VisibleForTesting
     */
    void onScanResult(final String address, final ScanResult result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callbackLeScanClients(address, result);
            }
        });
    }

    /**
     * Distribute each scan record to registered clients. When a "found" event occurs record the
     * address in the client filter so we can later send the "lost" event to that same client.
     * <p/>
     * This method will be called by the AIDL handler thread from onLeScan.
     */
    private synchronized void callbackLeScanClients(String address, ScanResult result) {
        recentScanResults.put(address, result);
        for (ScanClient client : serialClients.values()) {
            if (matchesAnyFilter(client.filtersList, result)) {
                boolean seenItBefore = client.addressesSeen.contains(address);
                int clientFlags = client.settings.getCallbackType();
                int firstMatchBit = clientFlags & ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
                int allMatchesBit = clientFlags & ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

                // Catch any exceptions and log them but continue processing other listeners.
                if ((firstMatchBit | allMatchesBit) != 0) {
                    try {
                        if (!seenItBefore) {
                            client.callback.onScanResult(ScanSettings.CALLBACK_TYPE_FIRST_MATCH, result);
                        } else if (allMatchesBit != 0) {
                            client.callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
                        }
                    } catch (Exception e) {
                        Logger.logError("Failure while handling scan result", e);
                    }
                }
                if (!seenItBefore) {
                    client.addressesSeen.add(address);
                }
            }
        }
    }

    @Override
    public synchronized boolean startScan(List<ScanFilter> filterList, ScanSettings settings,
                                          ScanCallback callback) {
        return startSerialScan(settings, filterList, callback);
    }

    private boolean startSerialScan(ScanSettings settings, List<ScanFilter> filterList,
                                    ScanCallback callback) {
        ScanClient client = new ScanClient(settings, filterList, callback);
        serialClients.put(callback, client);

        int clientFlags = client.settings.getCallbackType();
        int firstMatchBit = clientFlags & ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
        int allMatchesBit = clientFlags & ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

        // Process new registrations by immediately invoking the "found" callback
        // with all previously sighted devices.
        if ((firstMatchBit | allMatchesBit) != 0) {
            for (Entry<String, ScanResult> entry : recentScanResults.entrySet()) {
                String address = entry.getKey();
                ScanResult savedResult = entry.getValue();
                if (matchesAnyFilter(filterList, savedResult)) {

                    // Catch any exceptions and log them but continue processing other scan results.
                    try {
                        client.callback.onScanResult(ScanSettings.CALLBACK_TYPE_FIRST_MATCH, savedResult);
                    } catch (Exception e) {
                        Logger.logError("Failure while handling scan result for new listener", e);
                    }
                    client.addressesSeen.add(address);
                }
            }
        }

        updateRepeatingAlarm();
        return true;
    }

    /**
     * Stop scanning.
     *
     * @see JbBluetoothLeScannerCompat#startScan
     */
    @Override
    public synchronized void stopScan(ScanCallback callback) {
        serialClients.remove(callback);
        updateRepeatingAlarm();
    }

    /**
     * Test for lost tags by periodically checking the found devices
     * for any that haven't been seen recently.
     *
     * @VisibleForTesting
     */
    protected void onScanCycleComplete() {
        Iterator<Entry<String, ScanResult>> iter = recentScanResults.entrySet().iterator();
        long lostTimestampMillis = getLostTimestampMillis();

        // Clear out any expired notifications from the "old sightings" record.
        while (iter.hasNext()) {
            Entry<String, ScanResult> entry = iter.next();
            String address = entry.getKey();
            ScanResult savedResult = entry.getValue();
            if (TimeUnit.NANOSECONDS.toMillis(savedResult.getTimestampNanos()) < lostTimestampMillis) {
                iter.remove();
                callbackLostLeScanClients(address, savedResult);
            }
        }
    }

    private int getScanModePriority(int mode) {
        switch (mode) {
            case ScanSettings.SCAN_MODE_LOW_LATENCY:
                return 2;
            case ScanSettings.SCAN_MODE_BALANCED:
                return 1;
            case ScanSettings.SCAN_MODE_LOW_POWER:
                return 0;
            default:
                Logger.logError("Unknown scan mode " + mode);
                return 0;
        }
    }

    protected int getMaxPriorityScanMode() {
        int maxPriority = -1;

        for (ScanClient scanClient : serialClients.values()) {
            ScanSettings settings = scanClient.settings;
            if (maxPriority == -1
                    || getScanModePriority(settings.getScanMode()) > getScanModePriority(maxPriority)) {
                maxPriority = settings.getScanMode();
            }
        }
        return maxPriority;
    }

    @Override
    protected boolean hasClients() {
        return !serialClients.isEmpty();
    }

    @Override
    protected void onNewScanCycle() {
        blockingScanCycle();
    }

    @Override
    protected Collection<ScanResult> getRecentScanResults() {
        return recentScanResults.values();
    }

    /**
     * Wraps user requests and stores the list of filters and callbacks. Also saves a set of
     * addresses for which any of the filters have matched in order to do lost processing.
     */
    private static class ScanClient {
        final List<ScanFilter> filtersList;
        final Set<String> addressesSeen;
        final ScanCallback callback;
        final ScanSettings settings;

        ScanClient(ScanSettings settings, List<ScanFilter> filters, ScanCallback callback) {
            this.settings = settings;
            this.filtersList = filters;
            this.addressesSeen = new HashSet<String>();
            this.callback = callback;
        }
    }
}

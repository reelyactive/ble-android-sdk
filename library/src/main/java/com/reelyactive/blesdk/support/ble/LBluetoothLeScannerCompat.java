package com.reelyactive.blesdk.support.ble;
/*
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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.reelyactive.blesdk.support.ble.util.Clock;
import com.reelyactive.blesdk.support.ble.util.Logger;
import com.reelyactive.blesdk.support.ble.util.SystemClock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implements Bluetooth LE scan related API on top of {@link android.os.Build.VERSION_CODES#LOLLIPOP}
 * and later.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LBluetoothLeScannerCompat extends BluetoothLeScannerCompat {

    final ConcurrentHashMap<String, ScanResult> recentScanResults;
    // Alarm Scan variables
    private final Map<ScanCallback, ScanClient> callbacksMap = new HashMap<ScanCallback, ScanClient>();
    private final android.bluetooth.le.BluetoothLeScanner osScanner;

    /**
     * Package-protected constructor, used by {@link BluetoothLeScannerCompatProvider}.
     * <p/>
     * Cannot be called from emulated devices that don't implement a BluetoothAdapter.
     */
    LBluetoothLeScannerCompat(Context context, BluetoothManager manager, AlarmManager alarmManager) {
        this(
                manager,
                alarmManager,
                new SystemClock(),
                PendingIntent.getBroadcast(
                        context,
                        0 /* requestCode */,
                        new Intent(context, ScanWakefulBroadcastReceiver.class).putExtra(ScanWakefulService.EXTRA_USE_LOLLIPOP_API, true),
                        0 /* flags */
                )
        );
    }

    LBluetoothLeScannerCompat(BluetoothManager manager, AlarmManager alarmManager,
                              Clock clock, PendingIntent alarmIntent) {
        super(clock, alarmManager, alarmIntent);
        Logger.logDebug("BLE 'L' hardware access layer activated");
        this.osScanner = manager.getAdapter().getBluetoothLeScanner();
        this.recentScanResults = new ConcurrentHashMap<>();
    }

    private static android.bluetooth.le.ScanSettings toOs(ScanSettings settings) {
        return new android.bluetooth.le.ScanSettings.Builder()
                .setReportDelay(settings.getReportDelayMillis())
                .setScanMode(settings.getScanMode())
                .build();
    }

    private static List<android.bluetooth.le.ScanFilter> toOs(List<ScanFilter> filters) {
        List<android.bluetooth.le.ScanFilter> osFilters =
                new ArrayList<android.bluetooth.le.ScanFilter>(filters.size());
        for (ScanFilter filter : filters) {
            osFilters.add(toOs(filter));
        }
        return osFilters;
    }

    private static android.bluetooth.le.ScanFilter toOs(ScanFilter filter) {
        android.bluetooth.le.ScanFilter.Builder builder = new android.bluetooth.le.ScanFilter.Builder();
        if (!TextUtils.isEmpty(filter.getDeviceAddress())) {
            builder.setDeviceAddress(filter.getDeviceAddress());
        }
        if (!TextUtils.isEmpty(filter.getDeviceName())) {
            builder.setDeviceName(filter.getDeviceName());
        }
        if (filter.getManufacturerId() != -1 && filter.getManufacturerData() != null) {
            if (filter.getManufacturerDataMask() != null) {
                builder.setManufacturerData(filter.getManufacturerId(), filter.getManufacturerData(),
                        filter.getManufacturerDataMask());
            } else {
                builder.setManufacturerData(filter.getManufacturerId(), filter.getManufacturerData());
            }
        }
        if (filter.getServiceDataUuid() != null && filter.getServiceData() != null) {
            if (filter.getServiceDataMask() != null) {
                builder.setServiceData(
                        filter.getServiceDataUuid(), filter.getServiceData(), filter.getServiceDataMask());
            } else {
                builder.setServiceData(filter.getServiceDataUuid(), filter.getServiceData());
            }
        }
        if (filter.getServiceUuid() != null) {
            if (filter.getServiceUuidMask() != null) {
                builder.setServiceUuid(filter.getServiceUuid(), filter.getServiceUuidMask());
            } else {
                builder.setServiceUuid(filter.getServiceUuid());
            }
        }
        return builder.build();
    }

    private static List<ScanResult> fromOs(List<android.bluetooth.le.ScanResult> osResults) {
        List<ScanResult> results = new ArrayList<ScanResult>(osResults.size());
        for (android.bluetooth.le.ScanResult result : osResults) {
            results.add(fromOs(result));
        }
        return results;
    }

    private static ScanResult fromOs(android.bluetooth.le.ScanResult osResult) {
        return new ScanResult(
                osResult.getDevice(),
                fromOs(osResult.getScanRecord()),
                osResult.getRssi(),
                // Convert the osResult timestamp from 'nanos since boot' to 'nanos since epoch'.
                osResult.getTimestampNanos() + getActualBootTimeNanos());
    }

    private static long getActualBootTimeNanos() {
        long currentTimeNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        long elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos();
        return currentTimeNanos - elapsedRealtimeNanos;
    }

    private static ScanRecord fromOs(android.bluetooth.le.ScanRecord osRecord) {
        return ScanRecord.parseFromBytes(osRecord.getBytes());
    }

    /////////////////////////////////////////////////////////////////////////////
    // Conversion methods

    @Override
    public boolean startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        if (callbacksMap.containsKey(callback)) {
            Logger.logInfo("StartScan(): BLE 'L' hardware scan already in progress...");
            stopScan(callback);
        }

        android.bluetooth.le.ScanSettings osSettings = toOs(settings);
        ScanClient osCallback = toOs(callback, settings);
        List<android.bluetooth.le.ScanFilter> osFilters = toOs(filters);

        callbacksMap.put(callback, osCallback);
        try {
            Logger.logInfo("Starting BLE 'L' hardware scan ");
            for (ScanFilter filter : filters) {
                Logger.logInfo("\tFilter " + filter);
            }
            osScanner.startScan(osFilters, osSettings, osCallback);
            updateRepeatingAlarm();
            return true;
        } catch (Exception e) {
            Logger.logError("Exception caught calling 'L' BluetoothLeScanner.startScan()", e);
            return false;
        }
    }

    @Override
    public void stopScan(ScanCallback callback) {
        android.bluetooth.le.ScanCallback osCallback = callbacksMap.get(callback);

        if (osCallback != null) {
            try {
                Logger.logInfo("Stopping BLE 'L' hardware scan");
                osScanner.stopScan(osCallback);
            } catch (Exception e) {
                Logger.logError("Exception caught calling 'L' BluetoothLeScanner.stopScan()", e);
            }
            callbacksMap.remove(callback);
            updateRepeatingAlarm();
        }
    }

    private int getScanModePriority(int mode) {
        switch (mode) {
            case ScanSettings.SCAN_MODE_LOW_LATENCY:
            case ScanSettings.SCAN_MODE_BALANCED:
            case ScanSettings.SCAN_MODE_LOW_POWER:
                return mode;
            default:
                Logger.logError("Unknown scan mode " + mode);
                return 0;
        }
    }

    protected int getMaxPriorityScanMode() {
        int maxPriority = -1;

        for (ScanClient scanClient : callbacksMap.values()) {
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
        return !callbacksMap.isEmpty();
    }

    private ScanClient toOs(final ScanCallback callback, ScanSettings settings) {
        return new ScanClient(callback, settings);
    }

    @Override
    protected void onNewScanCycle() {
        int activeMillis = getScanActiveMillis();
        if (activeMillis > 0) {
            synchronized (this) {
                try {
                    wait(activeMillis);
                } catch (InterruptedException e) {
                    Logger.logError("Exception in ScanCycle Sleep", e);
                }
            }
        }
        long lostTimestampMillis = getLostTimestampMillis();
        Iterator<Map.Entry<String, ScanResult>> iter = recentScanResults.entrySet().iterator();
        // Clear out any expired notifications from the "old sightings" record.
        while (iter.hasNext()) {
            Map.Entry<String, ScanResult> entry = iter.next();
            final String address = entry.getKey();
            final ScanResult savedResult = entry.getValue();
            if (TimeUnit.NANOSECONDS.toMillis(savedResult.getTimestampNanos()) < lostTimestampMillis) {
                iter.remove();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callbackLostLeScanClients(address, savedResult);
                    }
                });
            }
        }
        callbackCycleCompleted();
        updateRepeatingAlarm();
    }

    private void callbackCycleCompleted() {
        for (ScanClient client : callbacksMap.values()) {
            client.callback.onScanCycleCompleted();
        }
    }

    @Override
    protected Collection<ScanResult> getRecentScanResults() {
        return recentScanResults.values();
    }

    private void callbackLostLeScanClients(String address, ScanResult result) {
        for (ScanClient client : callbacksMap.values()) {
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

    private class ScanClient extends android.bluetooth.le.ScanCallback {
        final Set<String> addressesSeen;
        final ScanCallback callback;
        final ScanSettings settings;

        ScanClient(ScanCallback callback, ScanSettings settings) {
            this.settings = settings;
            this.addressesSeen = new HashSet<String>();
            this.callback = callback;
        }

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult osResult) {
            String address = osResult.getDevice().getAddress();
            boolean seenItBefore = addressesSeen.contains(address);
            int clientFlags = settings.getCallbackType();

            int firstMatchBit = clientFlags & ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
            int allMatchesBit = clientFlags & ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

            ScanResult result = fromOs(osResult);
            recentScanResults.put(address, result);

            // Catch any exceptions and log them but continue processing other listeners.
            if ((firstMatchBit | allMatchesBit) != 0) {
                try {
                    if (!seenItBefore) {
                        callback.onScanResult(ScanSettings.CALLBACK_TYPE_FIRST_MATCH, result);
                    } else if (allMatchesBit != 0) {
                        callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
                    }
                } catch (Exception e) {
                    Logger.logError("Failure while handling scan result", e);
                }
            }
            addressesSeen.add(address);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Logger.logInfo("LBluetoothLeScannerCompat::onScanFailed(" + errorCode + ")");
            callback.onScanFailed(errorCode);
        }
    }
}

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

// THIS IS MODIFIED COPY OF THE "L" PLATFORM CLASS. BE CAREFUL ABOUT EDITS.
// THIS CODE SHOULD FOLLOW ANDROID STYLE.
//
// Changes:
//   Change to abstract class
//   Remove implementations
//   Define setCustomScanTiming for ULR
//   Slight updates to javadoc

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;

import com.reelyactive.blesdk.support.ble.util.Clock;
import com.reelyactive.blesdk.support.ble.util.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents the public entry into the Bluetooth LE compatibility scanner that efficiently captures
 * advertising packets broadcast from Bluetooth LE devices.
 * <p/>
 * API declarations in this class are the same as the new LE scanning API that is being introduced
 * in Android "L" platform. Declarations contained here will eventually be replaced by the platform
 * versions. Refer to the <a href="http://go/android-ble">"L" release API design</a> for further
 * information.
 * <p/>
 * The API implemented here is for compatibility when used on
 * {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR2} and later.
 *
 * @see <a href="https://www.bluetooth.org/en-us/specification/adopted-specifications"> Bluetooth
 * Adopted Specifications</a>
 * @see <a href="https://www.bluetooth.org/DocMan/handlers/DownloadDoc.ashx?doc_id=282152"> ​Core
 * Specification Supplement (CSS) v4</a>
 */
public abstract class BluetoothLeScannerCompat {

    // Number of cycles before a sighted device is considered lost.
  /* @VisibleForTesting */ static final int SCAN_LOST_CYCLES = 4;

    // Constants for Scan Cycle
    // Low Power: 2.5 minute period with 1.5 seconds active (1% duty cycle)
  /* @VisibleForTesting */ static final int LOW_POWER_IDLE_MILLIS = 148500;
    /* @VisibleForTesting */ static final int LOW_POWER_ACTIVE_MILLIS = 1500;

    // Balanced: 15 second period with 1.5 second active (10% duty cycle)
  /* @VisibleForTesting */ static final int BALANCED_IDLE_MILLIS = 13500;
    /* @VisibleForTesting */ static final int BALANCED_ACTIVE_MILLIS = 1500;

    // Low Latency: 1.67 second period with 1.5 seconds active (90% duty cycle)
  /* @VisibleForTesting */ static final int LOW_LATENCY_IDLE_MILLIS = 167;
    /* @VisibleForTesting */ static final int LOW_LATENCY_ACTIVE_MILLIS = 1500;
    // Alarm Scan variables
    private final Clock clock;
    private final AlarmManager alarmManager;
    private final PendingIntent alarmIntent;
    // Milliseconds to wait before considering a device lost. If set to a negative number
    // SCAN_LOST_CYCLES is used to determine when to inform clients about lost events.
    protected long scanLostOverrideMillis = -1;
    // Default Scan Constants = Balanced
    private int scanIdleMillis = BALANCED_IDLE_MILLIS;
    private int scanActiveMillis = BALANCED_ACTIVE_MILLIS;
    // Override values for scan window
    private int overrideScanActiveMillis = -1;
    private int overrideScanIdleMillis;

    protected BluetoothLeScannerCompat(Clock clock, AlarmManager alarmManager, PendingIntent alarmIntent) {
        this.clock = clock;
        this.alarmManager = alarmManager;
        this.alarmIntent = alarmIntent;
    }

    protected static boolean matchesAnyFilter(List<ScanFilter> filters, ScanResult result) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (ScanFilter filter : filters) {
            if (filter.matches(result)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Start Bluetooth LE scan with default parameters and no filters.
     * The scan results will be delivered through {@code callback}.
     * <p/>
     * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param callback Callback used to deliver scan results.
     * @throws IllegalArgumentException If {@code callback} is null.
     */
    public void startScan(final ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }
        this.startScan(new ArrayList<ScanFilter>(), new ScanSettings.Builder().build(), callback);
    }

    /**
     * Starts a scan for Bluetooth LE devices, looking for device advertisements that match the
     * given filters.  Attempts to start scans more than once with the same {@link ScanCallback} instance
     * will fail and return {@code false}.
     * <p/>
     * Due to the resource limitations in BLE chipset, an app cannot add more than N(real number
     * TBD) filters.
     * <p/>
     * Once the controller storage reaches its limitation on scan filters, the framework will try to
     * merge the existing scan filters and set the merged filters to chipset. The framework will
     * need to keep a copy of the original scan filters to make sure each app gets only its desired
     * results.
     * <p/>
     * The callback will be invoked when LE scan finds a matching BLE advertising packet. A BLE
     * advertising record is considered matching the filters if it matches any of the
     * BluetoothLeScanFilter in the list.
     * <p/>
     * Results of the scan are reported using the {@link ScanCallback#onScanResult(int, ScanResult)} callback.
     * <p/>
     * Requires BLUETOOTH_ADMIN permission.
     * <p/>
     *
     * @return true if the scan starts successfully, false otherwise.
     */
    public abstract boolean startScan(List<ScanFilter> filters, ScanSettings settings,
                                      ScanCallback callback);

    /**
     * Stops an ongoing Bluetooth LE device scan.
     * <p/>
     * Requires BLUETOOTH_ADMIN permission.
     * <p/>
     *
     * @param callback
     */
    public abstract void stopScan(ScanCallback callback);

    /**
     * Request matching records in the scanner's list.
     *
     * @param filters Filters which will apply
     * @return The list of matching {@link ScanResult}
     */
    public List<ScanResult> getMatchingRecords(List<ScanFilter> filters) {
        ArrayList<ScanResult> results = new ArrayList<ScanResult>();
        for (Object res : getRecentScanResults().toArray()) {
            if (matchesAnyFilter(filters, (ScanResult) res)) {
                results.add((ScanResult) res);
            }
        }
        return results;
    }

    /**
     * Sets the Bluetooth LE scan cycle overriding values set on individual scans from
     * {@link ScanSettings}.
     * <p/>
     * This is an extension of the "L" Platform API.
     * <p/>
     *
     * @param scanMillis               duration in milliseconds for the scan to be active, or -1 to remove, 0 to
     *                                 pause.  Ignored by hardware and truncated batch scanners.
     * @param idleMillis               duration in milliseconds for the scan to be idle.  Ignored by hardware
     *                                 and truncated batch scanners.
     * @param serialScanDurationMillis duration in milliseconds of the on-demand serial scan
     *                                 launched opportunistically by the truncated batch mode scanner.  Ignored by
     *                                 non-truncated scanners.
     */
    public void setCustomScanTiming(
            int scanMillis, int idleMillis, long serialScanDurationMillis) {
        overrideScanActiveMillis = scanMillis;
        overrideScanIdleMillis = idleMillis;
        // reset scanner so it picks up new scan window values
        updateRepeatingAlarm();
    }

    /**
     * Sets the delay after which a device will be marked as lost if it hasn't been sighted
     * within the given time. Set to a negative value to allow default behaviour.
     */
    public void setScanLostOverride(long lostOverrideMillis) {
        scanLostOverrideMillis = lostOverrideMillis;
    }

    /**
     * Compute the timestamp in the past which is the earliest that a sighting can have been
     * seen; sightings last seen before this timestamp will be deemed to be too old.
     * Then the Sandmen come.
     *
     * @VisibleForTesting
     */
    long getLostTimestampMillis() {
        if (scanLostOverrideMillis >= 0) {
            return getClock().currentTimeMillis() - scanLostOverrideMillis;
        }
        return getClock().currentTimeMillis() - (SCAN_LOST_CYCLES * getScanCycleMillis());
    }

    /**
     * Returns the length of a single scan cycle, comprising both active and idle time.
     *
     * @VisibleForTesting
     */
    long getScanCycleMillis() {
        return getScanActiveMillis() + getScanIdleMillis();
    }

    /**
     * Get the current active ble scan time that has been set
     *
     * @VisibleForTesting
     */
    int getScanActiveMillis() {
        return (overrideScanActiveMillis != -1) ? overrideScanActiveMillis : scanActiveMillis;
    }

    /**
     * Get the current idle ble scan time that has been set
     *
     * @VisibleForTesting
     */
    int getScanIdleMillis() {
        return (overrideScanActiveMillis != -1) ? overrideScanIdleMillis : scanIdleMillis;
    }

    /**
     * Update the repeating alarm wake-up based on the period defined for the scanner If there are
     * no clients, or a batch scan running, it will cancel the alarm.
     */
    protected void updateRepeatingAlarm() {
        // Apply Scan Mode (Cycle Parameters)
        setScanMode(getMaxPriorityScanMode());
        if (!hasClients()) {
            // No listeners.  Remove the repeating alarm, if there is one.
            getAlarmManager().cancel(getAlarmIntent());
            Logger.logInfo("Scan : No clients left, canceling alarm.");
        } else {
            long alarmIntervalMillis = getScanIdleMillis();
            // Specifies an alarm at the scanPeriod, starting immediately.
            if (Build.VERSION.SDK_INT > 22) {
                getAlarmManager().setExact(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + alarmIntervalMillis,
                        getAlarmIntent()
                );
            } else if (Build.VERSION.SDK_INT > 18) {
                getAlarmManager().setExact(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + alarmIntervalMillis,
                        getAlarmIntent()
                );
            } else {
                getAlarmManager().set(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + alarmIntervalMillis,
                        getAlarmIntent()
                );
            }
            Logger.logInfo("Scan alarm setup complete @ " + System.currentTimeMillis() + " (" + (alarmIntervalMillis) + ")");
        }
    }

    /**
     * Sets parameters for the various scan modes
     *
     * @param scanMode the ScanMode in BluetoothLeScanner Settings
     */
    protected void setScanMode(int scanMode) {
        switch (scanMode) {
            case ScanSettings.SCAN_MODE_LOW_LATENCY:
                scanIdleMillis = LOW_LATENCY_IDLE_MILLIS;
                scanActiveMillis = LOW_LATENCY_ACTIVE_MILLIS;
                break;
            case ScanSettings.SCAN_MODE_LOW_POWER:
                scanIdleMillis = LOW_POWER_IDLE_MILLIS;
                scanActiveMillis = LOW_POWER_ACTIVE_MILLIS;
                break;

            // Fall through and be balanced when there's nothing saying not to.
            default:
            case ScanSettings.SCAN_MODE_BALANCED:
                scanIdleMillis = BALANCED_IDLE_MILLIS;
                scanActiveMillis = BALANCED_ACTIVE_MILLIS;
                break;
        }
    }

    protected Clock getClock() {
        return clock;
    }

    protected AlarmManager getAlarmManager() {
        return alarmManager;
    }

    protected PendingIntent getAlarmIntent() {
        return alarmIntent;
    }

    protected abstract int getMaxPriorityScanMode();

    protected abstract boolean hasClients();

    protected abstract void onNewScanCycle();

    protected abstract Collection<ScanResult> getRecentScanResults();

}

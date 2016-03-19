package com.reelyactive.blesdk.support.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.reelyactive.blesdk.support.ble.util.Logger;
import com.reelyactive.blesdk.support.ble.util.SystemClock;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by saiimons on 16-03-17.
 */
public class NoBluetoothLeScannerCompat extends BluetoothLeScannerCompat {

    private final Map<ScanCallback, ScanSettings> callbacksMap = new ConcurrentHashMap<>();

    public NoBluetoothLeScannerCompat(Context context, AlarmManager alarmManager) {
        super(
                new SystemClock(),
                alarmManager,
                PendingIntent.getBroadcast(
                        context,
                        0,
                        new Intent(context, ScanWakefulBroadcastReceiver.class).putExtra(ScanWakefulService.EXTRA_USE_LOLLIPOP_API, false), 0)
        );
    }

    @Override
    public boolean startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        if (callback != null) {
            try {
                callbacksMap.put(callback, settings);
                updateRepeatingAlarm();
                return true;
            } catch (Exception e) {
                Logger.logError(e.getMessage(), e);
            }
        }
        return false;
    }

    @Override
    public void stopScan(ScanCallback callback) {
        callbacksMap.remove(callback);
        updateRepeatingAlarm();
    }

    @Override
    protected int getMaxPriorityScanMode() {
        int maxPriority = -1;

        for (ScanSettings settings : callbacksMap.values()) {
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

    @Override
    protected void onNewScanCycle() {
        for (ScanCallback callback : callbacksMap.keySet()) {
            callback.onScanCycleCompleted();
        }
    }

    @Override
    protected Collection<ScanResult> getRecentScanResults() {
        return Collections.emptyList();
    }
}

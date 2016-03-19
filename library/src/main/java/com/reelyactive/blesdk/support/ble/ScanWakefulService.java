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

import android.app.IntentService;
import android.content.Intent;

import com.reelyactive.blesdk.support.ble.util.Logger;

/**
 * The com.reelyactive.blesdk.support.ble.ScanWakefulService Class is called with a WakeLock held, and executes a single Bluetooth LE
 * scan cycle. It then calls the {@link ScanWakefulBroadcastReceiver#completeWakefulIntent} with the
 * same intent to release the associated WakeLock.
 */
public class ScanWakefulService extends IntentService {
    public static final String EXTRA_USE_LOLLIPOP_API = "use_lollipop";

    public ScanWakefulService() {
        super("com.reelyactive.blesdk.support.ble.ScanWakefulService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        // This method runs in a worker thread.
        // At this point com.reelyactive.blesdk.support.ble.ScanWakefulBroadcastReceiver is still holding a WakeLock.
        // We can do whatever we need to do in the code below.
        // After the call to completeWakefulIntent the WakeLock is released.
        Logger.logDebug("Running scancycle");
        try {
            BluetoothLeScannerCompat bleScanner =
                    BluetoothLeScannerCompatProvider.getBluetoothLeScannerCompat(this, intent.getBooleanExtra(EXTRA_USE_LOLLIPOP_API, true));
            if (bleScanner != null) {
                bleScanner.onNewScanCycle();
            }
        } finally {
            ScanWakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }
}

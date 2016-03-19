package com.reelyactive.blescanner;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.reelyactive.blesdk.application.ReelyAwareApplicationCallback;
import com.reelyactive.blesdk.service.BleService;

/**
 * Created by saiimons on 15-04-17.
 */
public class ReelyAwareApplication extends Application {

    private static final String TAG = ReelyAwareApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ReelyAwareApplicationCallback(this) {
            @Override
            public boolean onBleEvent(BleService.Event event, Object data) {
                Log.d(TAG, "BLE Event " + event + " // " + data);
                return super.onBleEvent(event, data);
            }

            @Override
            protected boolean shouldStartScan() {
                return isBound();
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }
}

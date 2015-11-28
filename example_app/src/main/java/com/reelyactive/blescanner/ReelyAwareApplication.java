package com.reelyactive.blescanner;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.reelyactive.blesdk.application.ReelyAwareApplicationCallback;

/**
 * Created by saiimons on 15-04-17.
 */
public class ReelyAwareApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ReelyAwareApplicationCallback(this) {
            @Override
            protected boolean shouldStartScan() {
                return true;
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

package com.reelyactive.blescanner;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;


public class BleSdkScanActivity extends ActionBarActivity {
    private static final String TAG = BleSdkScanActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new BleScanFragment())
                    .commit();
        }
    }
}

package com.reelyactive.blescanner;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import com.reelyactive.blesdk.application.ReelyAwareActivity;
import com.reelyactive.blesdk.support.ble.ScanResult;

/**
 * Created by saiimons on 15-04-17.
 */
public class ReelyAwareScanActivity extends Activity implements ReelyAwareActivity {
    private BleScanResultAdapter adapter;
    private ListView list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_ble_scan);
        adapter = new BleScanResultAdapter(this);
        findViewById(R.id.start_ble_scan_btn).setVisibility(View.GONE);
        list = (ListView) findViewById(R.id.ble_scan_results);
        list.setAdapter(adapter);
    }

    @Override
    public void onScanStarted() {

    }

    @Override
    public void onScanStopped() {

    }

    @Override
    public void onEnterRegion(ScanResult beacon) {
        adapter.addItem(beacon);
    }

    @Override
    public void onLeaveRegion(ScanResult beacon) {
        adapter.removeItem(beacon);
    }
}

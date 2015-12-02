package com.reelyactive.blescanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ListView;

import com.reelyactive.blesdk.application.ReelyAwareActivity;
import com.reelyactive.blesdk.support.ble.ScanResult;

/**
 * Created by saiimons on 15-04-17.
 */
public class ReelyAwareScanActivity extends AppCompatActivity implements ReelyAwareActivity {
    private static final int REQUEST_CODE_LOCATION = 42;
    private BleScanResultAdapter adapter;
    private ListView list;
    private boolean permissionChecked = false;

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
    protected void onResume() {
        super.onResume();
        if (!permissionChecked && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOCATION:
                permissionChecked = true;
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Snackbar
                            .make((View) list.getParent(), R.string.location_permission, Snackbar.LENGTH_INDEFINITE)
                            .setAction(
                                    R.string.ok,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            ActivityCompat.requestPermissions(ReelyAwareScanActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
                                        }
                                    }
                            ).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
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

package com.reelyactive.blescanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompat;
import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompatProvider;
import com.reelyactive.blesdk.support.ble.ScanCallback;
import com.reelyactive.blesdk.support.ble.ScanFilter;
import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanSettings;

import java.util.Arrays;

public class BleScanFragment extends Fragment {
    private static final int REQUEST_CODE_LOCATION = 42;
    private BluetoothLeScannerCompat scanner;
    private boolean isScanning;
    private ScanCallback scanCallback;
    private Button scanButton;
    private BleScanResultAdapter adapter;
    private ListView list;
    private boolean permissionChecked = false;

    public BleScanFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        scanner = BluetoothLeScannerCompatProvider.getBluetoothLeScannerCompat(getContext());
        isScanning = false;
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
                    adapter.addItem(result);
                } else {
                    adapter.removeItem(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("SCAN", "Failed " + errorCode);
            }
        };
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (scanner != null && isScanning) {
            scanner.stopScan(scanCallback);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_ble_scan, container, false);
        adapter = new BleScanResultAdapter(getContext());
        list = (ListView) rootView.findViewById(R.id.ble_scan_results);
        list.setAdapter(adapter);
        scanButton = (Button) rootView.findViewById(R.id.start_ble_scan_btn);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scanner != null) {
                    if (isScanning) {
                        scanner.stopScan(scanCallback);
                        scanButton.setText(R.string.start_ble_scan);
                        isScanning = false;
                    } else {
                        adapter.clear();
                        scanner.startScan(
                                Arrays.asList(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("7265656c-7941-6374-6976-652055554944")).build()),
                                new ScanSettings.Builder() //
                                        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST) //
                                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                                        .setScanResultType(ScanSettings.SCAN_RESULT_TYPE_FULL) //
                                        .build(),
                                scanCallback
                        );
                        scanButton.setText(R.string.stop_ble_scan);
                        isScanning = true;
                    }
                }
            }
        });
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!permissionChecked && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOCATION:
                permissionChecked = true;
                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Snackbar
                            .make((View) list.getParent(), R.string.location_permission, Snackbar.LENGTH_INDEFINITE)
                            .setAction(
                                    R.string.ok,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
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
}
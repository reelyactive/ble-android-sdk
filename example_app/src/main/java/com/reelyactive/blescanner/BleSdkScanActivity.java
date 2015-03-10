package com.reelyactive.blescanner;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompat;
import com.reelyactive.blesdk.support.ble.BluetoothLeScannerCompatProvider;
import com.reelyactive.blesdk.support.ble.ScanCallback;
import com.reelyactive.blesdk.support.ble.ScanFilter;
import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanSettings;

import java.util.Arrays;
import java.util.HashMap;


public class BleSdkScanActivity extends ActionBarActivity {
    private static final String TAG = BleSdkScanActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        private Context mContext;
        private BluetoothLeScannerCompat scanner;
        private boolean isScanning;
        private ScanCallback scanCallback;
        private Button scanButton;
        private BleScanResultAdapter adapter;
        private ListView list;

        public PlaceholderFragment() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mContext = activity.getApplicationContext();
            scanner = BluetoothLeScannerCompatProvider.getBluetoothLeScannerCompat(mContext, false);
            isScanning = false;
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
                        Log.d("FOUND", result.toString());
                        adapter.addItem(result);
                    } else {
                        Log.d("LOST", result.toString());
                        adapter.removeItem(result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.d("SCAN", "Failed " + errorCode);
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
            adapter = new BleScanResultAdapter(mContext);
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
                                            //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
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
    }

    public static class BleScanResultAdapter extends BaseAdapter {

        private final Context mContext;
        private HashMap<String, ScanResult> items;

        public BleScanResultAdapter(Context context) {
            items = new HashMap<String, ScanResult>();
            mContext = context;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public ScanResult getItem(int position) {
            return items.get(items.keySet().toArray()[position]);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.ble_scan_result, parent, false);
            }
            TextView tv = (TextView) convertView.findViewById(R.id.device_name);
            tv.setText(getItem(position).getDevice().getName());
            tv = (TextView) convertView.findViewById(R.id.device_address);
            tv.setText(getItem(position).getDevice().getAddress());
            return convertView;
        }

        public void addItem(ScanResult result) {
            if (!items.containsKey(result.getDevice().getAddress())) {
                items.put(result.getDevice().getAddress(), result);
                notifyDataSetChanged();
            }
        }

        public void removeItem(ScanResult result) {
            if (items.containsKey(result.getDevice().getAddress())) {
                items.remove(result.getDevice().getAddress());
                notifyDataSetChanged();
            }
        }

        public void clear() {
            items.clear();
            notifyDataSetChanged();
        }

    }
}

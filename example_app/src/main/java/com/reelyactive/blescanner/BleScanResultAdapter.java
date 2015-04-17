package com.reelyactive.blescanner;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.reelyactive.blesdk.support.ble.ScanResult;

import java.util.HashMap;

public class BleScanResultAdapter extends BaseAdapter {

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
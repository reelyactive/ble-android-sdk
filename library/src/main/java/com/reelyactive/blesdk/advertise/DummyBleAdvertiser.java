package com.reelyactive.blesdk.advertise;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanResultParser;
import com.reelyactive.blesdk.support.ble.util.BluetoothInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by saiimons on 15-03-09.
 */
public class DummyBleAdvertiser extends BleAdvertiser {
    private final Context context;
    private final Worker worker;
    private final AdvertisingRunnable runnable;
    private String uuid;
    private List<ScanResult> closestBeacon;
    private boolean advertising = false;
    private String url;

    public DummyBleAdvertiser(Context context) {
        this.context = context;
        worker = new Worker("advertiser");
        worker.start();
        worker.waitUntilReady();
        runnable = new AdvertisingRunnable();
    }

    private JSONObject getJsonBase() {
        try {
            return new JSONObject(
                    "        {" +
                            "    \"event\": \"appearance\"," +
                            "    \"tiraid\":{" +
                            "        \"identifier\":{" +
                            "            \"type\":\"ADVA-48\"," +
                            "            \"value\":\"<MAC_ADDR>\"," +
                            "            \"advHeader\": {" +
                            "              \"txAdd\": \"public\"" +
                            "            }," +
                            "            \"advData\":{" +
                            "                \"complete128BitUUIDs\":\"<UUID>\"" +
                            "            }" +
                            "        }," +
                            "        \"timestamp\":\"<DATE>\"," +
                            "        \"radioDecodings\":[" +
                            "        ]" +
                            "    }" +
                            "}"
            );
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject getRadioDecodingBase() {
        try {
            return new JSONObject(
                    "            {" +
                            "                \"rssi\":0," +
                            "                \"identifier\":{" +
                            "                    \"type\": \"EUI-64\"," +
                            "                    \"value\": \"<ID_DU_BEACON>\"" +
                            "                }" +
                            "            }"
            );
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void startAdvertising(String uuid, List<ScanResult> closestBeacon, String fallbackUrl) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Do not try to run from outside the main thread !");
        }
        this.uuid = uuid;
        synchronized (this) {
            this.closestBeacon = closestBeacon;
        }
        this.url = fallbackUrl;
        if (!advertising) {
            advertising = true;
            worker.getHandler().post(runnable);
        }
    }

    @Override
    public void stopAdvertising() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Do not try to run from outside the main thread !");
        }
        advertising = false;
    }

    @Override
    public void updateBeacons(List<ScanResult> closestBeacon) {
        synchronized (this) {
            this.closestBeacon = closestBeacon;
        }
    }

    private void advertise() {
        List<ScanResult> results;
        synchronized (this) {
            if (closestBeacon != null) {
                results = new ArrayList<>(closestBeacon);
            } else {
                return;
            }
        }
        if (url == null || results.size() == 0) {
            return;
        }
        URL url;
        OutputStream os = null;
        BufferedReader is = null;
        try {
            url = new URL(this.url);
            URLConnection conn = url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            JSONObject jsonObject = getJsonBase();
            jsonObject.getJSONObject("tiraid").getJSONObject("identifier").put("value", BluetoothInterface.getMacAddress().replace(":", "").toLowerCase());
            jsonObject.getJSONObject("tiraid").getJSONObject("identifier").getJSONObject("advData").put("complete128BitUUIDs", uuid);
            JSONArray decodings = jsonObject.getJSONObject("tiraid").getJSONArray("radioDecodings");
            for (ScanResult result : results) {
                JSONObject decoding = getRadioDecodingBase();
                decoding.getJSONObject("identifier").put("value", ScanResultParser.getSystemId(result));
                decoding.getJSONObject("identifier").put("rssi", 127 + result.getRssi());
                decodings.put(decoding);
            }
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
            df.setTimeZone(TimeZone.getTimeZone("Z"));
            jsonObject.getJSONObject("tiraid").put("timestamp", df.format(new Date(System.currentTimeMillis())));
            String content = jsonObject.toString(4);
            conn.setRequestProperty("Content-Length", String.valueOf(content.getBytes().length));
            os = conn.getOutputStream();
            os.write(content.getBytes());
            is = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while (null != is.readLine()) {
            }
            is.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Worker extends HandlerThread {
        public Handler mHandler;

        public Worker(String name) {
            super(name);
        }

        public Handler getHandler() {
            return mHandler;
        }

        public synchronized void waitUntilReady() {
            mHandler = new Handler(getLooper());
        }
    }

    private class AdvertisingRunnable implements Runnable {

        @Override
        public void run() {
            if (advertising) {
                advertise();
                worker.getHandler().postDelayed(runnable, 5 * 1000);
            }
        }
    }
}

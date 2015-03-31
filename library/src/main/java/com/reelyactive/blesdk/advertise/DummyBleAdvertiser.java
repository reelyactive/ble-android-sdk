package com.reelyactive.blesdk.advertise;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.reelyactive.blesdk.support.ble.ScanResult;
import com.reelyactive.blesdk.support.ble.ScanResultParser;
import com.reelyactive.blesdk.support.ble.util.BluetoothInterface;

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
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by saiimons on 15-03-09.
 */
public class DummyBleAdvertiser implements BleAdvertiser {
    private final Context context;
    private String uuid;
    private ScanResult closestBeacon;

    private final Worker worker;

    private boolean advertising = false;

    private final AdvertisingRunnable runnable;

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
                            "            {" +
                            "                \"rssi\":0," +
                            "                \"identifier\":{" +
                            "                    \"type\": \"EUI-64\"," +
                            "                    \"value\": \"<ID_DU_BEACON>\"" +
                            "                }" +
                            "            }" +
                            "        ]" +
                            "    }" +
                            "}"
            );
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void startAdvertising(String uuid, ScanResult closestBeacon) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Do not try to run from outside the main thread !");
        }
        this.uuid = uuid;
        this.closestBeacon = closestBeacon;
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

    private void advertise() {
        URL url;
        OutputStream os = null;
        BufferedReader is = null;
        try {
            url = new URL("http://www.hyperlocalcontext.com/event/");
            URLConnection conn = url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            JSONObject jsonObject = getJsonBase();
            jsonObject.getJSONObject("tiraid").getJSONObject("identifier").put("value", BluetoothInterface.getMacAddress().replace(":", "").toLowerCase());
            jsonObject.getJSONObject("tiraid").getJSONObject("identifier").getJSONObject("advData").put("complete128BitUUIDs", uuid);
            jsonObject.getJSONObject("tiraid").getJSONArray("radioDecodings").getJSONObject(0).getJSONObject("identifier").put("value", ScanResultParser.getSystemId(closestBeacon));
            jsonObject.getJSONObject("tiraid").getJSONArray("radioDecodings").getJSONObject(0).getJSONObject("identifier").put("rssi", 127 + closestBeacon.getRssi());
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

    private class AdvertisingRunnable implements Runnable {

        @Override
        public void run() {
            if (advertising) {
                advertise();
                worker.getHandler().postDelayed(runnable, 5 * 1000);
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
}

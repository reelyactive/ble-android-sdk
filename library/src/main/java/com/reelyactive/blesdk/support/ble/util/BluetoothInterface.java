package com.reelyactive.blesdk.support.ble.util;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Random;

/**
 * Created by saiimons on 15-03-02.
 */
public class BluetoothInterface {
    private static final String PREF_NAME = "reelyactive";
    private static final String MAC_PREF = "mac_address";

    public static MacAddress getMacAddress(Context context) {
        MacAddress address = new MacAddress();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (!preferences.contains(MAC_PREF)) {
                preferences.edit().putString(MAC_PREF, randomMACAddress()).apply();
            }
            address.type = "random";
            address.address = preferences.getString(MAC_PREF, "00:00:00:00:00:00");
        } else {
            address.type = "public";
            address.address = adapter.getAddress();
        }
        return address;
    }

    private static String randomMACAddress() {
        Random rand = new Random(System.currentTimeMillis());
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);

        macAddr[0] = (byte) (macAddr[0] & (byte) 254);  //zeroing last 2 bytes to make it unicast and locally adminstrated

        StringBuilder sb = new StringBuilder(18);
        for (byte b : macAddr) {

            if (sb.length() > 0)
                sb.append(":");

            sb.append(String.format("%02x", b));
        }


        return sb.toString();
    }

    public static class MacAddress {
        public String address;
        public String type;
    }
}

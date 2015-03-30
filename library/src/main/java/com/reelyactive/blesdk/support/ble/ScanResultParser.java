package com.reelyactive.blesdk.support.ble;

/**
 * Created by saiimons on 15-03-30.
 */
public class ScanResultParser {
    public static String getSystemId(ScanResult result) {
        byte[] data = getSystemIdBytes(result);
        if (data == null || data.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = data.length - 1; i >= 0; i--) {
            sb.append(String.format("%02x", data[i]));
        }
        return sb.toString();
    }

    public static byte[] getSystemIdBytes(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return null;
        }
        return record.getServiceData(BluetoothUuid.parseUuidFrom(new byte[]{0x23, 0x2a}));
    }
}

package com.platypii.baseline.lasers.rangefinder;

import com.platypii.baseline.lasers.LaserMeasurement;
import com.platypii.baseline.util.Exceptions;
import com.platypii.baseline.views.laser.LaserActivity;

import android.bluetooth.le.ScanRecord;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.WriteType;
import java.util.Arrays;
import java.util.UUID;
import org.greenrobot.eventbus.EventBus;

import static com.platypii.baseline.bluetooth.BluetoothUtil.byteArrayToHex;
import static com.platypii.baseline.bluetooth.BluetoothUtil.bytesToShort;

/**
 * This class contains ids, commands, and decoders for Uineye / Hawkeye laser rangefinders.
 */
class UineyeProtocol extends BleProtocol {
    private static final String TAG = "UineyeProtocol";

    // Manufacturer ID
    private static final int manufacturerId1 = 21881;
    private static final byte[] manufacturerData1 = {-120, -96, -44, 54, 57, 101, 118, 103}; // 88-a0-d4-36-39-65-76-67

    private static final int manufacturerId2 = 19784;
    private static final byte[] manufacturerData2 = {0, 21, -123, 20, -100, 9}; // 00-15-85-14-9c-09

    private static final int manufacturerId3 = 42841;
    private static final byte[] manufacturerData3 = {-120, -96, -54, -42, 67, 72, -111, 32};

    // Rangefinder service
    private static final UUID rangefinderService = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    // Rangefinder characteristic
    private static final UUID rangefinderCharacteristic = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // Say hello to laser
    private static final byte[] appHello = {-82, -89, 4, 0, 6, 10, -68, -73};   // ae-a7-04-00-06-0a-bc-b7
    // Tell laser to shutdown. Uineye app sends this if laser doesn't say hello back in 5s.
    // private static final byte[] appGoodbye = {-82, -89, 4, 0, 7, 11, -68, -73}; // ae-a7-04-00-07-0b-bc-b7
    // Send this in response to heartbeat
    private static final byte[] appHeartbeatAck = {-82, -89, 4, 0, -120, -116, -68, -73}; // ae-a7-04-00-88-8c-bc-b7

    // Rangefinder responses
    private static final byte[] laserHello = {4, 0, -122, -118}; // ae-a7-04-00-86-8a-bc-b7
    private static final byte[] heartbeat = {4, 0, 8, 12}; // ae-a7-04-00-08-0c-bc-b7
    private static final byte[] norange = {4, 0, 5, 9}; // ae-a7-04-00-05-09-bc-b7

    // Protocol state
    @NonNull
    private final RfSentenceIterator sentenceIterator = new RfSentenceIterator();

    @Override
    public void onServicesDiscovered(@NonNull BluetoothPeripheral peripheral) {
        try {
            // Request rangefinder service
            Log.i(TAG, "app -> rf: subscribe");
            peripheral.setNotify(rangefinderService, rangefinderCharacteristic, true);
            sendHello(peripheral);
            readRangefinder(peripheral);
        } catch (Throwable e) {
            Log.e(TAG, "rangefinder handshake exception", e);
        }
    }

    @Override
    public void processBytes(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        sentenceIterator.addBytes(value);
        while (sentenceIterator.hasNext()) {
            processSentence(peripheral, sentenceIterator.next());
        }
    }

    private void processSentence(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        if (Arrays.equals(value, laserHello)) {
            Log.i(TAG, "rf -> app: hello");
        } else if (Arrays.equals(value, heartbeat)) {
            Log.d(TAG, "rf -> app: heartbeat");
            sendHeartbeatAck(peripheral);
        } else if (Arrays.equals(value, norange)) {
            Log.i(TAG, "rf -> app: norange");
        } else if (value[0] == 23 && value[1] == 0) {
            processMeasurement(value);
        } else {
            Log.w(TAG, "rf -> app: unknown " + byteArrayToHex(value));
        }
    }

    private void readRangefinder(@NonNull BluetoothPeripheral peripheral) {
        Log.i(TAG, "app -> rf: read");
        peripheral.readCharacteristic(rangefinderService, rangefinderCharacteristic);
    }

    private void sendHello(@NonNull BluetoothPeripheral peripheral) {
        Log.d(TAG, "app -> rf: hello");
        peripheral.writeCharacteristic(rangefinderService, rangefinderCharacteristic, appHello, WriteType.WITH_RESPONSE);
    }

    private void sendHeartbeatAck(@NonNull BluetoothPeripheral peripheral) {
        Log.d(TAG, "app -> rf: heartbeat ack");
        peripheral.writeCharacteristic(rangefinderService, rangefinderCharacteristic, appHeartbeatAck, WriteType.WITH_RESPONSE);
    }

    private void processMeasurement(@NonNull byte[] value) {
        Log.d(TAG, "rf -> app: measure " + byteArrayToHex(value));

        final double units; // unit multiplier
        if (value[21] == 1) {
            units = 1; // meters
        } else if (value[21] == 2) {
            units = 0.9144; // yards
        } else if (value[21] == 3) {
            units = 0.3048; // feet
        } else {
            Exceptions.report(new IllegalStateException("Unexpected units value from uineye " + value[21]));
            units = 0;
        }
        final double pitch = bytesToShort(value[3], value[4]) * 0.1 * units; // degrees
//        final double total = Util.bytesToShort(value[5], value[6]) * 0.1 * units; // meters
        double vert = bytesToShort(value[7], value[8]) * 0.1 * units; // meters
        double horiz = bytesToShort(value[9], value[10]) * 0.1 * units; // meters
//        double bearing = (value[22] & 0xff) * 360.0 / 256.0; // degrees
        if (pitch < 0) {
            vert = -vert;
        }

        final LaserMeasurement meas = new LaserMeasurement(horiz, vert);
        Log.i(TAG, "rf -> app: measure " + meas);
        EventBus.getDefault().post(meas);
    }

    /**
     * Return true iff a bluetooth scan result looks like a rangefinder
     */
    @Override
    public boolean canParse(@NonNull BluetoothPeripheral peripheral, @Nullable ScanRecord record) {
        final String deviceName = peripheral.getName();
        if (record != null && Arrays.equals(record.getManufacturerSpecificData(manufacturerId1), manufacturerData1)) {
            return true; // Manufacturer match (kenny's laser)
        } else if (record != null && Arrays.equals(record.getManufacturerSpecificData(manufacturerId2), manufacturerData2)) {
            return true; // Manufacturer match (hartman's laser)
        } else if (record != null && Arrays.equals(record.getManufacturerSpecificData(manufacturerId3), manufacturerData3)) {
            return true; // Manufacturer match 2022 version
        } else if (deviceName.endsWith("BT05") || deviceName.startsWith("Rangefinder") || deviceName.startsWith("Uineye")) {
            // Device name match
            if (record != null) {
                // Send manufacturer data to firebase
                final Bundle bundle = new Bundle();
                bundle.putString("rf_device_name", deviceName);
                final SparseArray<byte[]> mfg = record.getManufacturerSpecificData();
                for (int i = 0; i < mfg.size(); i++) {
                    final String key = "mfg_" + mfg.keyAt(i);
                    final String hex = byteArrayToHex(mfg.valueAt(i));
                    bundle.putString(key, hex);
                }
                LaserActivity.firebaseAnalytics.logEvent("manufacturer_data", bundle);
            }
            return true;
        } else {
            return false;
        }
    }

}

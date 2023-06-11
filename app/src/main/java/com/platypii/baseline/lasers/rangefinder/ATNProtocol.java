package com.platypii.baseline.lasers.rangefinder;

import com.platypii.baseline.bluetooth.BleProtocol;
import com.platypii.baseline.lasers.LaserMeasurement;

import android.bluetooth.le.ScanRecord;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.welie.blessed.BluetoothPeripheral;
import java.util.UUID;
import org.greenrobot.eventbus.EventBus;

import static com.platypii.baseline.bluetooth.BluetoothUtil.byteArrayToHex;
import static com.platypii.baseline.bluetooth.BluetoothUtil.bytesToShort;

/**
 * This class contains ids, commands, and decoders for ATN laser rangefinders.
 */
class ATNProtocol extends BleProtocol {
    private static final String TAG = "ATNProtocol";

    // Rangefinder service
    private static final UUID rangefinderService = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    // Rangefinder characteristic
    private static final UUID rangefinderCharacteristic = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // Rangefinder responses
    // 10-01-20-00-0b-01-bb-18 // measurement
    // 10-01-a0-ff-58-01-55-b2 // measurement fail
    // 10-01-10-02-0c-00-79-68 // fog mode
    // 10-01-91-ff-58-01-bb-5c // fog mode fail yards

    @Override
    public void onServicesDiscovered(@NonNull BluetoothPeripheral peripheral) {
        // Request rangefinder service
        Log.i(TAG, "app -> rf: subscribe");
        peripheral.setNotify(rangefinderService, rangefinderCharacteristic, true);
    }

    @Override
    public void processBytes(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        final String hex = byteArrayToHex(value);
        if (value[0] == 16 && value[1] == 1) {
            // Check for bits we haven't seen before
            if ((value[2] & 0x4e) != 0) {
                Log.w(TAG, "Unexpected ATN command: " + hex);
            }
            final boolean success = (value[2] & 0x80) == 0;
//            final boolean normalMode = (value[2] & 0x20) != 0;
//            final boolean fogMode = (value[2] & 0x10) != 0;
//            final boolean metric = (value[2] & 0x01) == 0;

            if (success) {
                processMeasurement(value);
            } else {
                Log.i(TAG, "rf -> app: norange " + hex);
            }
        } else {
            Log.w(TAG, "rf -> app: unknown " + hex);
        }
    }

    private void processMeasurement(@NonNull byte[] value) {
        Log.d(TAG, "rf -> app: measure " + byteArrayToHex(value));

        if (value[0] != 16 || value[1] != 1) {
            throw new IllegalArgumentException("Invalid measurement prefix " + byteArrayToHex(value));
        }

        final boolean metric = (value[2] & 0x01) == 0;
        final double units = metric ? 1 : 0.9144; // yards or meters

        final double total = bytesToShort(value[3], value[4]) * 0.5 * units; // meters
        final double pitch = bytesToShort(value[5], value[6]) * 0.1; // degrees

        final double horiz = total * Math.cos(Math.toRadians(pitch)); // meters
        final double vert = total * Math.sin(Math.toRadians(pitch)); // meters

        if (horiz < 0) {
            throw new IllegalArgumentException("Invalid horizontal distance " + total + " " + pitch + " " + horiz + " " + vert);
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
        return "ATN-LD99".equals(peripheral.getName());
    }

}

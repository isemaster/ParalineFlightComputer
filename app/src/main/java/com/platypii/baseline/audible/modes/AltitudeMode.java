package com.platypii.baseline.audible.modes;

import com.platypii.baseline.Services;
import com.platypii.baseline.audible.AudibleMode;
import com.platypii.baseline.audible.AudibleSample;
import com.platypii.baseline.util.Convert;

import androidx.annotation.NonNull;

/**
 * Altitude mode announces current altitude
 */
public class AltitudeMode extends AudibleMode {

    public AltitudeMode() {
        super("altitude", "Altitude", "altitude", 0, 10000, 0);
    }

    @Override
    public @NonNull
    AudibleSample currentSample(int precision) {
        final double altitude = Services.alti.altitudeAGL();
        return new AudibleSample(altitude, Convert.altitude(altitude));
    }

    @Override
    public float units() {
        return Convert.metric ? 1f : (float) Convert.FT;
    }

    @NonNull
    @Override
    public String renderDisplay(double output, int precision) {
        return Convert.altitude(output);
    }
} 
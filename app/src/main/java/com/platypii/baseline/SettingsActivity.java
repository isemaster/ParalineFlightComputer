package com.platypii.baseline;

import android.os.Bundle;

/**
 * Settings activity for things like metric / imperial
 */
public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

}

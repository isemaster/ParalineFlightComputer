package com.platypii.baseline.audible;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import com.platypii.baseline.R;
import com.platypii.baseline.util.Util;

/**
 * Preference dialog that converts to/from internal units in the preference,
 * to local units in the dialog.
 */
public class AudibleMinMaxPreference extends DialogPreference {

    private EditText mEditText;

    public AudibleMinMaxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // We will persist values on our own, after converting units
        // setPersistent(false);
        setDialogLayoutResource(R.layout.audible_minmax_dialog);
    }

    public float getValue() {
        return Util.parseFloat(getPersistedString(null));
    }

    public void setValue(float value) {
        persistString(Float.toString(value));
    }

    private AudibleMode getMode() {
        final String audibleMode = getSharedPreferences().getString("audible_mode", null);
        return AudibleModes.get(audibleMode);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mEditText = (EditText) view.findViewById(R.id.minmax_input);

        final float value = getValue();
        if(Util.isReal(value)) {
            final float valueLocalUnits = value / getMode().units();
            mEditText.setText(Float.toString(valueLocalUnits));
        } else {
            mEditText.setText("");
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if(positiveResult) {
            // Convert mEditText into value
            final String valueString = mEditText.getText().toString();
            final float valueLocalUnits = Util.parseFloat(valueString);
            final float value = valueLocalUnits * getMode().units();
            if(Util.isReal(value)) {
                persistString(Float.toString(value));
                callChangeListener(value);
            }
        }
    }

    /**
     * Secret Android API, used by EditTextPreference to show soft keyboard by default
     */
    protected boolean needInputMethod() {
        return true;
    }

}

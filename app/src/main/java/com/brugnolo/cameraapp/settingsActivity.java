package com.brugnolo.cameraapp;

import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class settingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState != null) {
            switch (savedInstanceState.getInt(getString(R.string.FORMAT_TAG))) {
                case R.integer.RADIO_RAW:
                    ((RadioButton) findViewById(R.id.RawRadio)).toggle();
                    break;
                case R.integer.RADIO_JPEG:
                    ((RadioButton) findViewById(R.id.JPEGRadio)).toggle();
                    break;
            }
            switch (savedInstanceState.getInt(getString(R.string.EFFECT_TAG))) {
                case R.integer.RADIO_SEPIA:
                    ((RadioButton) findViewById(R.id.sepiaEffect)).toggle();
                    break;
                case R.integer.RADIO_BLACKBOARD:
                    ((RadioButton) findViewById(R.id.blackboardEffect)).toggle();
                    break;
            }
        } else {
            ((RadioButton) findViewById(R.id.RawRadio)).toggle();
            ((RadioButton) findViewById(R.id.sepiaEffect)).toggle();
        }
    }


    public void startCamera(View view) {
        int format = 0;
        int effect = 0;
        RadioGroup formatGroup = (RadioGroup) findViewById(R.id.formatGroup);
        switch (formatGroup.getCheckedRadioButtonId()) {
            case R.id.RawRadio:
                format = ImageFormat.RAW_SENSOR;
                break;
            case R.id.JPEGRadio:
                format = ImageFormat.JPEG;
                break;
        }
        if (format == ImageFormat.RAW_SENSOR) {
            effect = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
        } else {
            RadioGroup effectGroup = (RadioGroup) findViewById(R.id.effectsGroup);
            switch (effectGroup.getCheckedRadioButtonId()) {
                case R.id.sepiaEffect:
                    effect = CaptureRequest.CONTROL_EFFECT_MODE_SEPIA;
                    break;
                case R.id.blackboardEffect:
                    effect = CaptureRequest.CONTROL_EFFECT_MODE_MONO;
                    break;
            }
        }
        Intent i = new Intent(getApplicationContext(), MainActivity.class);
        i.putExtra(getString(R.string.EFFECT_TAG), effect);
        i.putExtra(getString(R.string.FORMAT_TAG), format);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        int format = 0;
        int effect = 0;
        RadioGroup formatGroup = (RadioGroup) findViewById(R.id.formatGroup);
        switch (formatGroup.getCheckedRadioButtonId()) {
            case R.id.RawRadio:
                format = R.integer.RADIO_RAW;
                break;
            case R.id.JPEGRadio:
                format = R.integer.RADIO_JPEG;
                break;
        }
        RadioGroup effectGroup = (RadioGroup) findViewById(R.id.effectsGroup);
        switch (effectGroup.getCheckedRadioButtonId()) {
            case R.id.sepiaEffect:
                effect = R.integer.RADIO_SEPIA;
                break;
            case R.id.blackboardEffect:
                effect = R.integer.RADIO_BLACKBOARD;
                break;
        }
        savedInstanceState.putInt(getString(R.string.FORMAT_TAG), format);
        savedInstanceState.putInt(getString(R.string.EFFECT_TAG), effect);
        super.onSaveInstanceState(savedInstanceState);
    }
}

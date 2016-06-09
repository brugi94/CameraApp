package com.brugnolo.cameraapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
                    setRaw();
                    break;
                case R.integer.RADIO_JPEG:
                    ((RadioButton) findViewById(R.id.JPEGRadio)).toggle();
                    switch (savedInstanceState.getInt(getString(R.string.EFFECT_TAG))) {
                        case R.integer.RADIO_SEPIA:
                            ((RadioButton) findViewById(R.id.sepiaEffect)).toggle();
                            break;
                        case R.integer.RADIO_BLACKBOARD:
                            ((RadioButton) findViewById(R.id.blackboardEffect)).toggle();
                            break;
                        default:
                            ((RadioButton) findViewById(R.id.normalEffect)).toggle();
                    }
                    break;
            }
        } else {
            setRaw();
        }

    }

    private void setRaw() {
        ((RadioButton) findViewById(R.id.normalEffect)).toggle();
        ((RadioButton) findViewById(R.id.RawRadio)).toggle();
        setEnabled(R.id.effectsGroup, false);
    }

    public void clickRaw(View view){
        ((RadioButton) findViewById(R.id.normalEffect)).toggle();
        setEnabled(R.id.effectsGroup, false);
    }
    public void clickJPEG(View view){
        setEnabled(R.id.effectsGroup, true);
    }

    private void setEnabled(int group, boolean value) {
        RadioGroup effectGroup = (RadioGroup) findViewById(group);
        if (((RadioButton) effectGroup.getChildAt(0)).isEnabled()!=value) {
            for (int i = 0; i < effectGroup.getChildCount(); i++) {
                ((RadioButton) effectGroup.getChildAt(i)).setEnabled(value);
            }
        }
    }

    public void startCamera(View view) {

        requestCameraPermission();
        requestWritePermission();
        requestReadPermission();

        if(((ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)&&((ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE)) == PackageManager.PERMISSION_GRANTED)&&((ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) == PackageManager.PERMISSION_GRANTED))) {
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
                    case R.id.normalEffect:
                        effect = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                }
            }
            Intent i = new Intent(getApplicationContext(), MainActivity.class);
            i.putExtra(getString(R.string.EFFECT_TAG), effect);
            i.putExtra(getString(R.string.FORMAT_TAG), format);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
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

    private int MY_PERMISSIONS_REQUEST_CAMERA = 1;                        //IDs for the requests
    private int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    private int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 3;

    private void requestCameraPermission() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.CAMERA)) != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);

        }

    }

    private void requestWritePermission() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

        }

    }

    public void requestReadPermission() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE)) != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);

        }
    }
}

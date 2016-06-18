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
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class settingsActivity extends AppCompatActivity {

    private int MY_PERMISSIONS_REQUEST_CAMERA = 1;                        //IDs for the requests
    private int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    private int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 3;
    private Button mSwitchToPreviewButton;
    private Button mRAWButton;
    private Button mJPEGButton;

    @Override
    /*
    retrieves the previous state if there was any, otherwise default settings are applied
     */
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

        mSwitchToPreviewButton = (Button)findViewById(R.id.switchToPreviewButton);
        mSwitchToPreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera(v);
            }
        });

        mRAWButton = (Button)findViewById(R.id.RawRadio);
        mRAWButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRaw(v);
            }
        });

        mJPEGButton = (Button)findViewById(R.id.JPEGRadio);
        mJPEGButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickJPEG(v);
            }
        });
    }

    /*
    applies default settings
     */
    private void setRaw() {
        ((RadioButton) findViewById(R.id.normalEffect)).toggle();
        ((RadioButton) findViewById(R.id.RawRadio)).toggle();
        setEnabled(R.id.effectsGroup, false);
    }

    /*
    listener for the raw radiobutton click: greys out the effects
     */
    public void clickRaw(View view){
        ((RadioButton) findViewById(R.id.normalEffect)).toggle();
        setEnabled(R.id.effectsGroup, false);
    }

    /*
    listener for the jpeg radiobutton click: turns normal the effects if they were greyed out
     */
    public void clickJPEG(View view){
        setEnabled(R.id.effectsGroup, true);
    }

    /*
    sets a radiogroup so that group.isEnabled() == value
     */
    private void setEnabled(int group, boolean value) {
        RadioGroup effectGroup = (RadioGroup) findViewById(group);
        if ((effectGroup.getChildAt(0)).isEnabled() != value) {
            for (int i = 0; i < effectGroup.getChildCount(); i++) {
                (effectGroup.getChildAt(i)).setEnabled(value);
            }
        }
    }

    /*
    listener for the button where the user asks to take pictures which asks for the permission:
    if they're not granted just a toast is displayed that asks to give permission
    otherwise we retrieve the selected settings and then start the photo-taking activity with an intent containing those settings
     */
    public void startCamera(View view) {

        requestPermission(Manifest.permission.CAMERA, MY_PERMISSIONS_REQUEST_CAMERA);
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        //checks if we have permissions
        if ((checkPermission(Manifest.permission.CAMERA)
                && (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                && checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE))) {
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
        } else {
            Toast.makeText(getApplicationContext(), "please grant permissions", Toast.LENGTH_LONG).show();
        }
    }

    /*
    saves the current state
     */
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

    /**
     * @param permission the requested permission
     */
    private void requestPermission(String permission, int id) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (ContextCompat.checkSelfPermission(getApplicationContext(),
                permission)) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    id);
        }
    }

    /**
     * @param permission the permission to check for
     * @return true if granted, false otherwise
     */
    private boolean checkPermission(String permission) {
        return ((ContextCompat.checkSelfPermission(getApplicationContext(),
                permission)) == PackageManager.PERMISSION_GRANTED);
    }
}

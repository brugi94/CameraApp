package com.brugnolo.cameraapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession captureSession;
    private CaptureCallback sessionCallback = new CaptureCallback(){
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }
    };
    private TextureView preview;
    private TextureView.SurfaceTextureListener previewListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    setUpCamera(width, height);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };
    private Size previewSize;
    private String cameraID;
    private CameraDevice device;
    private CameraDevice.StateCallback callback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    device = camera;
                    createCameraPreviewSession();
                    Toast.makeText(getApplicationContext(), "succesfully opened", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    release(camera);
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    release(camera);
                }
            };


    @Override
    public void onStop(){
        super.onStop();
        Log.e("Luca_Debug", "onStop");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.e("Luca_Debug", "onDestroy");
    }



    @Override
    public void onPause(){
        super.onPause();
        Log.e("Luca_Debug", "onPause");
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.e("Luca_Debug", "onResume");
        if (preview.isAvailable()) {

        } else {
            preview.setSurfaceTextureListener(previewListener);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        preview = (TextureView) findViewById(R.id.previewView);
        Log.e("Luca_Debug", "onCreate");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setUpCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camera : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                previewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                cameraID = camera;

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * search the best size for the preview (closest to wanted is better)
     *
     * @param sizes  the sizes supported by the camera for the preview
     * @param width  actual TextureView width
     * @param height actual TextureView height
     * @return the optimal size
     */

    private Size getOptimalSize(Size[] sizes, int width, int height) {
        List<Size> sizesCollector = new ArrayList<>();
        //out of all the supported sizes we choose the ones that are bigger than the preview is and put them inside sizesColletor
        for (Size option : sizes) {
            if (width > height) {
                if ((option.getWidth() > width) && (option.getHeight() > height)) {
                    sizesCollector.add(option);
                }
            } else {
                if ((option.getWidth() > height) && (option.getHeight() > width)) {
                    sizesCollector.add(option);
                }
            }
        }
        //if we have sizes inside the collector we take the smallest one (less scaling needed)
        if (!sizesCollector.isEmpty()) {
            return Collections.min(sizesCollector, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((lhs.getHeight() * lhs.getWidth()) - (rhs.getHeight() * rhs.getWidth()));
                }
            });
        }

        Size optimalSize=sizes[0];
        double currentGap = Math.abs(width/height-sizes[0].getWidth()/sizes[0].getHeight());
        double minimumGap = currentGap;

        for(int i=1; i<sizes.length; i++){

            currentGap= Math.abs(width/height-sizes[i].getWidth()/sizes[i].getHeight());

            if (minimumGap>currentGap) {
                minimumGap = currentGap;
                optimalSize = sizes[i];
            }
        }

        //if not we take the biggest size available
        return optimalSize;
    }

    /**
     * opens the previously selected camera
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //TODO : request permissions at runtime
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "not succesfully opened", Toast.LENGTH_LONG).show();
                return;
            }
            manager.openCamera(cameraID, callback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * release the resources allocated for the camera
     *
     * @param camera the camera to be released
     */
    private static void release(CameraDevice camera) {
        camera.close();
        camera = null;
    }
    private void createCameraPreviewSession(){
        SurfaceTexture surfaceSettings = preview.getSurfaceTexture();
        surfaceSettings.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceSettings);
        try {
            builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            device.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if(device==null){
                                return;
                                //TODO : make it better
                            }
                            previewCaptureRequest = builder.build();
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(previewCaptureRequest, sessionCallback, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "problem during configuration", Toast.LENGTH_LONG).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}

package com.brugnolo.cameraapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private boolean saving = false;
    private ImageSaver saver;
    private File galleryFolder;
    private int photoCount;
    private Size photoSize;
    private static int screenRotation;
    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession captureSession;
    private static TextureView preview;
    private static Size previewSize;
    private String cameraID;
    private CameraDevice device;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader reader;
    private int format;
    private int effect;

    /*
    compares sizes, the one with the largest area is the bigger
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    /*
    onCreate simply takes care of retrieving the parameters sent by the previous activity and instantiating the things we need
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        Intent i = getIntent();
        format = i.getIntExtra(getString(R.string.FORMAT_TAG), ImageFormat.JPEG);
        effect = i.getIntExtra(getString(R.string.EFFECT_TAG), -1);
        preview = (TextureView) findViewById(R.id.previewView);
        Button mSettingButton = (Button) findViewById(R.id.btn_settings);
        assert mSettingButton != null;
        mSettingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //starts the settings activity and kills itself right after
                Intent mIntent = new Intent(getApplicationContext(), settingsActivity.class);
                startActivity(mIntent);
                finish();
            }
        });


        Button mTakePhotoButton = (Button) findViewById(R.id.pictureButton);
        assert mTakePhotoButton != null;
        mTakePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto(v);
            }
        });


    }

    /*
    calls the method needed to start the preview
     */
    @Override
    public void onResume() {
        super.onResume();
        retrieveState();
        openBackgroundThread();
        initializeListener();
    }

    /*
    gets the saved camera id, if there's one saved (useful so we don't need to search for a camera again) and also the photo count(used the the photo name)
     */
    private void retrieveState() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        cameraID = preferences.getString(getString(R.string.CAMERA_PREFERENCES), null);
        photoCount = preferences.getInt(getString(R.string.PHOTO_COUNT), 0);
        Log.i(getString(R.string.LOG_TAG), "state retrieved" + photoCount);

    }

    /*
    opens the background thread which is used to save the images and to execute some callbacks
    */
    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("Camera2 background thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /*
    sets a listener to the texture view which contains the method callbacks that will be used when the view is ready
     */
    private void initializeListener() {
        screenRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        TextureView.SurfaceTextureListener previewListener =
                new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        //retrieves camera parameters
                        getCamera();
                        //prepares output for image
                        setupReader();
                        //modifies textureview (rotate ecc.)
                        configureTransform(width, height);
                        //opens selected camera
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
        preview.setSurfaceTextureListener(previewListener);
    }

    /*
    this methods retrieves a camera manager, which is used to get all the cameras and search for the one we prefer
    in this example we choose a camera on the back of the phone, then we get the supported preview sizes
    and photo sizes for the requested format
    also initializes the saver with the basic parameters (a builder pattern is used for the imageSaver class)
    */
    private void getCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraID == null || photoSize == null) {
            try {
                //search all the cameras for the back one
                for (String camera : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera);
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    //starts preparing the saver runnable
                    saver = new ImageSaver(getApplicationContext(), characteristics);//saver isn't already instantiated: if it was, photosize wouldnt be null, nor cameraID would be
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    //retrieves the output size for current format
                    photoSize = Collections.max(Arrays.asList(map.getOutputSizes(format)), new CompareSizesByArea());
                    //finds best size for the preview
                    previewSize = getSize(map);
                    cameraID = camera;
                    return;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param map the map from which to take the sizes
     * @return the smallest of the big enough supported sizes (if there are big enough), otherwise the largest of the smaller ones
     */
    private Size getSize(StreamConfigurationMap map) {
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        for (Size option : choices) {
            if (option.getWidth() >= preview.getWidth() && option.getHeight() >= preview.getHeight() && ((float) preview.getWidth() / (float) preview.getHeight()) < 1.4F) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }
        if (!bigEnough.isEmpty()) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        }
    }

    /*
    sets up the output for the final image with the sizes, format and number of photo we want to take
    also sets the callbacks for when the image is ready
    we prepare the file name, in case we need to the image folder is created, the saver gets completely built and then we start saving
     */
    private void setupReader() {
        reader = ImageReader.newInstance(photoSize.getWidth(),
                photoSize.getHeight(),
                format,
                1);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.i(getString(R.string.LOG_TAG), "image available");
                Image imageToSave = reader.acquireNextImage();
                String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
                String imageFileName = "IMAGE_" + timeStamp + "_" + (photoCount++);
                createImageGallery();
                File image = new File(galleryFolder, imageFileName);
                saver.setFileToSave(image);
                saver.setImageToSave(imageToSave);
                saving = false;
            }
        }, backgroundHandler);
    }

    /*
    creates the folder where the images are saved
     */
    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        galleryFolder = new File(storageDirectory, getString(R.string.CAMERA2_APP_FOLDER));
        if (!galleryFolder.exists()) {
            galleryFolder.mkdirs();
        }
    }

    /*
    transforms the preview to match te current screen orientation: if we're in landscape mode, we rotate the preview and scale it to be fine
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (preview == null) {
            return;
        }
        if (screenRotation == Surface.ROTATION_90 || screenRotation == Surface.ROTATION_270) {
            Matrix matrix = new Matrix();
            RectF srcRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF dstRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = srcRect.centerX();
            float centerY = srcRect.centerY();
            //rects set so they have the same center
            dstRect.offset(centerX - dstRect.centerX(), centerY - dstRect.centerY());
            //scale srcRect to be equal to dstRect
            matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewWidth / previewSize.getWidth(),
                    (float) viewHeight / previewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            //rotate accordingly to current screen rotation
            if (screenRotation == Surface.ROTATION_90) {
                matrix.postRotate(-90, centerX, centerY);
            } else {
                matrix.postRotate(90, centerX, centerY);
            }
            preview.setTransform(matrix);
        }
    }

    /*
     * opens the previously selected camera and sets the callbacks
    */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(getString(R.string.LOG_TAG), "error while opening camera");
                return;
            }
            CameraDevice.StateCallback cameraCallback =
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            device = camera;
                            createCameraPreviewSession();
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
            manager.openCamera(cameraID, cameraCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    prepares the capture session which needs: the output targets, the callbacks and where to execute it
    we also setup our texture view to accept images of a maximum size (actual size depends by the phone)
    */
    private void createCameraPreviewSession() {
        SurfaceTexture surfaceSettings = preview.getSurfaceTexture();
        surfaceSettings.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceSettings);
        try {
            //create preview request
            builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            if (effect != -1) {
                builder.set(CaptureRequest.CONTROL_EFFECT_MODE, effect);
            }
            previewCaptureRequest = builder.build();
            device.createCaptureSession(Arrays.asList(previewSurface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (device == null) {
                                Log.e(getString(R.string.LOG_TAG), "camera device is null");
                                return;
                            }
                            captureSession = session;
                            startPreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(getString(R.string.LOG_TAG), "error while creating session");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    starts the preview
     */
    private void startPreview() {
        try {
            captureSession.setRepeatingRequest(previewCaptureRequest,
                    null,
                    backgroundHandler);
            Log.i(getString(R.string.LOG_TAG), "preview started");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    call methods to free resources and save the state
     */
    @Override
    public void onPause() {
        saveState();
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }

    /*
    saves the photo count and cameraID
     */
    private void saveState() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getString(R.string.CAMERA_PREFERENCES), cameraID);
        editor.putInt(getString(R.string.PHOTO_COUNT), photoCount);
        Log.i(getString(R.string.LOG_TAG), "state saved");
        editor.apply();
    }

    /*
    releases acquired resources
     */
    private void closeCamera() {
        if (captureSession != null) {
            stopPreview();
            captureSession.close();
            captureSession = null;
        }
        if (device != null) {
            device.close();
            device = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
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

    /*
    closes background thread
     */
    private void closeBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
    this method is called when the button is clicked: if we're not saving a photo we lock the focus, otherwise we tell the user to wait a while
    */
    public void takePhoto(View view) {
        if (!saving) {
            saving = true;
            lockFocus();
        } else {
            Toast.makeText(getApplicationContext(), "saving, wait a while", Toast.LENGTH_SHORT).show();
        }
    }

    /*
    we ask the camera to lock the focus (an unlock isnt needed since we restart the preview when we're done taking each photo
    when the focus is locked we start taking an image
     */
    private void lockFocus() {
        try {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureSession.capture(builder.build(), new CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    captureImage();
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.e(getString(R.string.LOG_TAG), "capture failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    we prepare the request for the capture: output is reader, flash is on when required, we set the rotation for the image, the effect
    when the request is ready we stop the preview and ask for capture
    after the capture is done we restart the preview and further build our imageSaver

    */
    private void captureImage() {
        try {
            //create request for still capture
            CaptureRequest.Builder captureStillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //set output
            captureStillBuilder.addTarget(reader.getSurface());
            //set flash
            captureStillBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int JPEGRotation;
            //set rotation
            switch (screenRotation) {
                case Surface.ROTATION_0:
                    JPEGRotation = 90;
                    break;
                //normal landscape
                case Surface.ROTATION_270:
                    JPEGRotation = 180;
                    break;
                default:
                    JPEGRotation = 0;
            }
            captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION, JPEGRotation);
            //set effect mode
            if (effect != -1) {
                captureStillBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, effect);
            }
            stopPreview();
            captureSession.capture(captureStillBuilder.build(), new CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Log.i(getString(R.string.LOG_TAG), "capture completed");
                    saver.setCaptureResult(result);
                    if (!backgroundHandler.post(saver)) {
                        Log.e(getString(R.string.LOG_TAG), "failed to post runnable");
                    }
                    startPreview();
                }
            }, backgroundHandler);
            Log.i(getString(R.string.LOG_TAG), "picture taken");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    stops the preview
     */
    private void stopPreview() {
        try {
            captureSession.stopRepeating();
            Log.i(getString(R.string.LOG_TAG), "preview stopped");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}

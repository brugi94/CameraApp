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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    /*
    this listener will post a Runnable class instance with the acquired image as parameter, let's move the the ImageSaver class
     */
    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image imageToSave = reader.acquireNextImage();
            String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String imageFileName = "IMAGE_" + timeStamp + "_" + (photoCount++);
            try {
                FileOutputStream fos = openFileOutput(imageFileName, Context.MODE_PRIVATE);
                ByteBuffer byteBuffer = imageToSave.getPlanes()[0].getBuffer();
                byte[] imageBytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(imageBytes);
                fos.write(imageBytes, 0, imageBytes.length);
                Log.i(getString(R.string.LOG_TAG), "sccesfully saved in private directory");
                Intent i = new Intent(getApplicationContext(), saverService.class);
                i.putExtra(getString(R.string.FILE_TAG), imageFileName);
                i.putExtra(getString(R.string.SCREEN_ROTATION_TAG), screenRotation);
                startService(i);
                imageToSave.close();
            } catch (FileNotFoundException e) {
                Log.e(getString(R.string.LOG_TAG), "error while saving in private directory");
            } catch (IOException e) {
                Log.e(getString(R.string.LOG_TAG), "error while saving in private directory");
            }
        }
    };
    private int photoCount;
    private Size photoSize;
    private static int screenRotation;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int state = 0;
    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession captureSession;
    /*
    when the capture is completed, we call the process method so that we can capture the image if we got the focus
    and then unlock the focus and start a new preview(setRepeatingRequest call), let's move to captureImage
     */
    private CaptureCallback sessionCallback = new CaptureCallback() {
        private void process(CaptureResult result) {

            switch (state) {

                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    Integer autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);
                    switch (autoFocusState) {
                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                            captureImage();
                            break;
                    }
                    break;
            }
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            process((CaptureResult) result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Toast.makeText(getApplicationContext(), "Capture failed", Toast.LENGTH_SHORT).show();
        }
    };
    private static TextureView preview;
    private static Size previewSize = new Size(1280, 960);
    private String cameraID;
    private CameraDevice device;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private File imageFile;
    private ImageReader reader;

    private File galleryFolder;
    private final String GALLERY_NAME = "Camera2_app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        preview = (TextureView) findViewById(R.id.previewView);
    }

    /*
    we start from here, so let's break down what's happening in the onResume method
    1) we call retrieveState() which gets the saved camera id (useful so we don't need to search for a camera again)
    2) we open the background thread which is used to compute multiple things (will say which when we get to them)
    3) we check if the preview is available: this will return true only if it's not the first time we call the onResume method
       so the first time we attach a listener to the preview that will call some other methods when the preview is available
       the next times we call getCamera and openCamera, will explain why on top of those methods
    let's move to the next method: getCamera
     */
    @Override
    public void onResume() {
        super.onResume();
        retrieveState();
        openBackgroundThread();
        screenRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        TextureView.SurfaceTextureListener previewListener =
                new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        getCamera();
                        setupReader();
                        configureTransform(width, height);
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

    @Override
    public void onPause() {
        saveState();
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }

    private void retrieveState() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        cameraID = preferences.getString(getString(R.string.CAMERA_PREFERENCES), null);
        photoCount = preferences.getInt(getString(R.string.PHOTO_COUNT), 0);
        Log.i(getString(R.string.LOG_TAG), "state retrieved" + photoCount);

    }

    private void saveState() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getString(R.string.CAMERA_PREFERENCES), cameraID);
        editor.putInt(getString(R.string.PHOTO_COUNT), photoCount);
        Log.i(getString(R.string.LOG_TAG), "state saved");
        editor.apply();
    }

    /*
    this methods retrieves a camera manager, which is used to get all the cameras and search for the one we prefer
    in this example we choose a camera on the back of the phone, then we get the supported preview sizes
    and photo sizes(jpeg format)
    the setUpReader method simply prepares a request:"i want to take 1 JPEG image with the sizes passed as arguments"
    then attachs a listener to it which will call a method when the image is ready. this is called in the background thread
    next part is openCamera()
     */
    private void getCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraID == null || photoSize == null) {
            try {
                for (String camera : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera);
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    photoSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new Comparator<Size>() {
                                @Override
                                public int compare(Size lhs, Size rhs) {
                                    return Long.signum((lhs.getHeight() * lhs.getWidth()) - (rhs.getHeight() * rhs.getWidth()));
                                }
                            });
                    cameraID = camera;
                    return;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupReader() {
        reader = ImageReader.newInstance(photoSize.getWidth(),
                photoSize.getHeight(),
                ImageFormat.JPEG,
                1);
        reader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
    }

    /**
     * opens the previously selected camera
     */
    /*
    here we simply open the camera with the requested cameraID.
    the call is asynchronous so the method onOpened will be called as soon as the camera is open (this is done on the background thread too)
    after we succesfully opened the camera, we save the device istance associated to it and try to create a preview session, we'll continue from there
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //TODO : request permissions at runtime
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "not succesfully opened", Toast.LENGTH_LONG).show();
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
    on this method we need to create a capture session, which needs 3 parameters:
    1) the surfaces that will be used: we use a surface for the rpeview and another one for saving the file
    2) a callback for when the session is ready
    3) the thread where to invoke the callback (we always use the background thread so that the UI thread doesn't get flooded)
    important notes: once we start a session, we'll be using it for the rest of the application life: we shouldnt create another one
    with this method the preview is created and we got the setup for saving the image, so we move to the part where we click the button
    the takePhoto method
     */
    private void createCameraPreviewSession() {
        SurfaceTexture surfaceSettings = preview.getSurfaceTexture();
        surfaceSettings.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceSettings);
        try {
            builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            device.createCaptureSession(Arrays.asList(previewSurface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (device == null) {
                                return;
                                //TODO : make it better
                            }
                            previewCaptureRequest = builder.build();
                            captureSession = session;
                            startPreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "problem during configuration", Toast.LENGTH_LONG).show();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("Camera2 background thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

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
    in this method we say that our app state is "we got the focus", then we prepare a capture saying
    that we want to lock the focus and to call the onCaptureCompleted() method from sessionCallback as soon as the focus is granted
    let's move the sessionCallback declaration
     */
    private void lockFocus() {
        try {
            state = STATE_WAIT_LOCK;
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureSession.capture(builder.build(), sessionCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            state = STATE_PREVIEW;
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            captureSession.capture(builder.build(), null, null);
            Log.i(getString(R.string.LOG_TAG), "focus unlocked");
            startPreview();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        try {
            captureSession.setRepeatingRequest(previewCaptureRequest, null, null);
            Log.i(getString(R.string.LOG_TAG), "preview started");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void stopPreview() {
        try {
            captureSession.stopRepeating();
            Log.i(getString(R.string.LOG_TAG), "preview stopped");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    this method is called when the button is clicked: here we create the folder where we'll save the images and then create a file where we'll save the image we take
    after this we call the lockFocus, so let's move there
     */
    public void takePhoto(View view) {
        lockFocus();
    }

    /*
    here we create a builder saying "i want to take a photo" and send it to "reader.getSurface()"
    after the capture is complete we unlock the focus and the onImageAvailable method is called, so let's move there
     */
    private void captureImage() {
        try {
            CaptureRequest.Builder captureStillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.addTarget(reader.getSurface());
            captureStillBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            unlockFocus();
                        }
                    };
            stopPreview();
            captureSession.capture(captureStillBuilder.build(), new CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    startPreview();
                }
            }, backgroundHandler);
            Log.i(getString(R.string.LOG_TAG), "picture taken");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (preview == null) {
            return;
        }
        Matrix matrix = new Matrix();
        RectF dstRect = null;
        RectF srcRect = null;
        if (screenRotation == Surface.ROTATION_0) {
            dstRect = new RectF(0, 0, viewHeight, viewWidth);
            srcRect = new RectF(0, 0, previewSize.getWidth(), previewSize.getHeight());
            matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);
        } else if (screenRotation == Surface.ROTATION_90 || screenRotation == Surface.ROTATION_270) {
            srcRect = new RectF(0, 0, viewWidth, viewHeight);
            dstRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = srcRect.centerX();
            float centerY = srcRect.centerY();
            dstRect.offset(centerX - dstRect.centerX(), centerY - dstRect.centerY());
            matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewWidth / previewSize.getWidth(),
                    (float) viewHeight / previewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (screenRotation - 2), centerX, centerY);
        }
        preview.setTransform(matrix);
    }
}

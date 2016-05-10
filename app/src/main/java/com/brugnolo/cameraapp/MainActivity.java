package com.brugnolo.cameraapp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
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

    private Size photoSize;
    private final int SCREEN_WIDTH = 1920;
    private final int SCREEN_HEIGHT = 1080;
    private static int screenRotation;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int state = 0;
    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession captureSession;
    private CaptureCallback sessionCallback = new CaptureCallback() {
        private void process(CaptureResult result) {

            switch (state) {

                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    Integer autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);
                    switch (autoFocusState) {
                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                            //TODO: my phone is always in this case, idk why yet, will need to test if i can take photos like this
                            captureImage();
                            unlockFocus();
                            try {
                                captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            break;

                        case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                            Toast.makeText(getApplicationContext(), "got focus lock", Toast.LENGTH_SHORT).show();
                            unlockFocus();
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
    private CameraDevice.StateCallback cameraCallback =
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
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private static File imageFile;
    private ImageReader reader;
    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Toast.makeText(getApplicationContext(), "height" + reader.getHeight() + " width " + reader.getWidth(), Toast.LENGTH_SHORT);
            backgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
        }
    };
    private File galleryFolder;
    private final String GALLERY_NAME = "Camera2_app";
    private String imageLocation = "";

    private static class ImageSaver implements Runnable {
        private Image imageField;

        public ImageSaver(Image image) {
            imageField = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = imageField.getPlanes()[0].getBuffer();
            byte[] imageBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(imageBytes);
            Bitmap imageAsBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            final Matrix rotationMatrix = new Matrix();
            switch (screenRotation) {
                case Surface.ROTATION_0:
                    rotationMatrix.setRotate(90);
                    break;
                //reverse landscape
                case Surface.ROTATION_90:
                    rotationMatrix.setRotate(0);
                    break;
                //normal landscape
                case Surface.ROTATION_270:
                    rotationMatrix.setRotate(180);
                    break;
            }
            Bitmap rotatedBitmap = Bitmap.createBitmap(imageAsBitmap, 0, 0, imageAsBitmap.getWidth(), imageAsBitmap.getHeight(), rotationMatrix, false);
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(imageFile);
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                imageField.close();
                if (outputStream != null) {
                    try {
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            byteBuffer = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        preview = (TextureView) findViewById(R.id.previewView);
    }

    @Override
    public void onResume() {
        super.onResume();
        retrieveState();
        openBackgroundThread();
        if (preview.isAvailable()) {

            setUpCamera();
            openCamera();
        } else {
            TextureView.SurfaceTextureListener previewListener =
                    new TextureView.SurfaceTextureListener() {
                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                            setUpCamera();
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
        cameraID = preferences.getString("cameraID", null);
    }

    private void saveState() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("cameraID", cameraID);
    }

    private void setUpCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraID != null && photoSize != null) {
            setupReader();
        } else {
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
                    setupReader();
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
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //TODO : request permissions at runtime
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "not succesfully opened", Toast.LENGTH_LONG).show();
                return;
            }
            manager.openCamera(cameraID, cameraCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
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
                            try {
                                captureSession.setRepeatingRequest(previewCaptureRequest, null, backgroundHandler);
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
            captureSession.capture(builder.build(), sessionCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePhoto(View view) {
        try {
            createImageGallery();
            imageFile = createImageFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        lockFocus();
    }

    private File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";

        File image = new File(galleryFolder, imageFileName + ".jpg");
        imageLocation = image.getAbsolutePath();
        return image;
    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        galleryFolder = new File(storageDirectory, GALLERY_NAME);
        if (!galleryFolder.exists()) {
            galleryFolder.mkdirs();
        }

    }

    private void captureImage() {
        try {
            CaptureRequest.Builder captureStillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.addTarget(reader.getSurface());
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            Toast.makeText(getApplicationContext(),
                                    "Image Captured!",
                                    Toast.LENGTH_SHORT).show();
                            unlockFocus();
                        }
                    };
            captureSession.capture(captureStillBuilder.build(),
                    captureCallback,
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (preview == null) {
            return;
        }
        screenRotation = this.getWindowManager().getDefaultDisplay().getRotation();
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

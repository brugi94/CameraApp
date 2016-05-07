package com.brugnolo.cameraapp;

import android.Manifest;
import android.content.Context;
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
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //    private boolean firstTime = true;
    private OrientationEventListener orientationListener;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

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
                            setUpCamera(previewSize.getWidth(), previewSize.getHeight());
                            openCamera();
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
    private TextureView.SurfaceTextureListener previewListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    configureTransform(width,height);
                    setUpCamera(width, height);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    configureTransform(width,height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };
    private static Size previewSize;
    private String cameraID;
    private CameraDevice device;
    private CameraDevice.StateCallback callback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    device = camera;
                    createCameraPreviewSession();
                    //Toast.makeText(getApplicationContext(), "succesfully opened", Toast.LENGTH_LONG).show();
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
            switch (screenRotation){
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
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        preview = (TextureView) findViewById(R.id.previewView);
    }

    @Override
    public void onResume() {
        super.onResume();
        openBackgroundThread();
        //if we're on landscape we rotate the textureview
        /*if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            final Matrix rotationMatrix = new Matrix();
            rotationMatrix.setRotate(90);
            preview.setTransform(rotationMatrix);
        }*/
        if (preview.isAvailable()) {

            setUpCamera(previewSize.getWidth(), previewSize.getHeight());
            openCamera();
        } else {
            preview.setSurfaceTextureListener(previewListener);
        }


    }

    @Override
    public void onPause() {
        closeCamera();
        closeBackgroundThread();
        super.onPause();
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
        /*if(!firstTime){
            return;
        }*/
//        firstTime = false;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camera : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size largestPossiblePhotoSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum((lhs.getHeight() * lhs.getWidth()) - (rhs.getHeight() * rhs.getWidth()));
                            }
                        });
                reader = ImageReader.newInstance(largestPossiblePhotoSize.getWidth(),
                        largestPossiblePhotoSize.getHeight(),
                        ImageFormat.JPEG,
                        1);
                if (reader != null) {
                    reader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
                }
                screenRotation = getWindowManager().getDefaultDisplay().getRotation();
                previewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largestPossiblePhotoSize);
                cameraID = camera;

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * search the best size for the preview (closest to wanted is better)
     * we started by using our own algorithm, but the preview was stretched or in low quality,
     * so we switched to the algorithm google uses. Result is it's still a bit stretched,
     * but not at low quality
     * I think there's some limitation hardware side, might work better with other phones
     *
     * @param sizes  the sizes supported by the camera for the preview
     * @param width  actual TextureView width
     * @param height actual TextureView height
     * @return the optimal size
     */

    private Size getOptimalSize(Size[] sizes, int width, int height, Size largest) {
        //<Google's example algorithm>
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = largest.getWidth();
        int h = largest.getHeight();
        for (Size option : sizes) {
            if (option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= width &&
                        option.getHeight() >= height) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        final Comparator<Size> comparator = new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum(lhs.getHeight() * lhs.getWidth() - rhs.getWidth() * rhs.getHeight());
            }
        };
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, comparator);
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, comparator);
        } else {
            Log.e("preview tag", "Couldn't find any suitable preview size");
            return sizes[0];
        }
    }
    // </Google's example algorithm>

    //<our algorithm>

        //if not we take the biggest size available
        /*if(screenRotation== Surface.ROTATION_90 || screenRotation==Surface.ROTATION_270){
            return sizes[0];
        }
        float ratio =(float)width/(float)height;
        return minRatioDifference(sizes,
                (screenRotation== Surface.ROTATION_90 || screenRotation==Surface.ROTATION_270) ? ratio : 1/ratio);


    }

    private Size minRatioDifference(Size[] sizes, float ratio){
        float minDifference = getRatioDifference(sizes[0], ratio);
        int i=0;
        for(int index =0;index<sizes.length;index++){
            float ratioDifference = getRatioDifference(sizes[index], ratio);
            if(ratioDifference < minDifference){
                minDifference = ratioDifference;
                i = index;
            }
        }
        return sizes[i];
    }
    private float getRatioDifference(Size size, float ratio){
        return Math.abs((float)size.getWidth()/(float)size.getHeight() - ratio);
    }
    </our algorithm>
    */
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
            manager.openCamera(cameraID, callback, backgroundHandler);
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
                                captureSession.setRepeatingRequest(previewCaptureRequest, sessionCallback, backgroundHandler);
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
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    ORIENTATIONS.get(rotation));
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
            screenRotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureSession.capture(captureStillBuilder.build(),
                    captureCallback,
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void configureTransform(int viewWidth, int viewHeight){
        if (null == preview || null == previewSize) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);

            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        preview.setTransform(matrix);
    }
}

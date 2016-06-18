package com.brugnolo.cameraapp;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by ricca on 09/06/2016.
 */
public class ImageSaver implements Runnable {

    /**
     * The image to save.
     */
    private Image imageToSave;
    /**
     * The file we save the image into.
     */
    private File fileToSave;

    /**
     * The CaptureResult for this image capture.
     */
    private CaptureResult captureResult;

    /**
     * The CameraCharacteristics for this cameraCharacteristics device.
     */
    private CameraCharacteristics cameraCharacteristics;

    /**
     * The Context to use when updating MediaStore with the saved images.
     */
    private Context appContext;

    public ImageSaver(Context context) {
        appContext = context;
    }

    public ImageSaver(Context context, CameraCharacteristics characteristics) {
        appContext = context;
        cameraCharacteristics = characteristics;
    }

    public void setImageToSave(Image image) {
        imageToSave = image;
    }

    public void setFileToSave(File file) {
        fileToSave = file;
    }

    public void setCaptureResult(CaptureResult result) {
        captureResult = result;
    }

    @Override
    public void run() {
        boolean success = false;
        int format = imageToSave.getFormat();
        FileOutputStream fos = null;

        switch (format) {
            case ImageFormat.JPEG: {
                addExtension("jpg");
                try {
                    fos = new FileOutputStream(fileToSave);
                } catch (FileNotFoundException e) {
                    Log.e(appContext.getString(R.string.LOG_TAG), "file not found");
                }
                ByteBuffer byteBuffer = imageToSave.getPlanes()[0].getBuffer();
                byte[] imageBytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(imageBytes);
                try {
                    fos.write(imageBytes, 0, imageBytes.length);
                    fos.close();
                    success = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageToSave.close();
                break;
            }
            case ImageFormat.RAW_SENSOR: {

                if(captureResult==null){Log.e("Luca","CaptureResult=null");}
                if(cameraCharacteristics==null){Log.e("Luca","cameraCharacteristics=null");}
                DngCreator dngCreator = new DngCreator(cameraCharacteristics, captureResult);
                addExtension("dng");
                try {
                    fos = new FileOutputStream(fileToSave);
                    dngCreator.writeImage(fos, imageToSave);
                    success = true;
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    imageToSave.close();
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            default: {
                Log.e(appContext.getString(R.string.LOG_TAG), "Cannot save image, unexpected image format:" + format);
                break;
            }
        }
        // If saving the file succeeded, update MediaStore.
        if (success) {
            Log.i(appContext.getString(R.string.LOG_TAG), "image saved");
            MediaScannerConnection.scanFile(appContext, new String[]{fileToSave.getPath()},
                /*mimeTypes*/null, new MediaScannerConnection.MediaScannerConnectionClient() {
                        @Override
                        public void onMediaScannerConnected() {
                            // Do nothing
                        }

                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i(appContext.getString(R.string.LOG_TAG), "scanned picture");
                        }
                    });
        }
    }

    private void addExtension(String extension) {
        String path = fileToSave.getAbsolutePath();
        path += "." + extension;
        fileToSave = new File(path);
    }
}
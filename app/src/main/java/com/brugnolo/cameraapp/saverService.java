package com.brugnolo.cameraapp;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by ricca on 24/05/2016.
 */
public class saverService extends IntentService {
    private File galleryFolder;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public saverService(String name) {
        super(name);
    }

    public saverService() {
        super("saver service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(getString(R.string.LOG_TAG), "service started");
        createImageGallery();
        String fileName = intent.getStringExtra(getString(R.string.FILE_TAG));
        int screenRotation = intent.getIntExtra(getString(R.string.SCREEN_ROTATION_TAG), -1);
        FileInputStream inputImage = null;
        try {
            inputImage = openFileInput(fileName);
            Log.i(getString(R.string.LOG_TAG), "input file opened");
        } catch (FileNotFoundException e) {
            Log.e(getString(R.string.LOG_TAG), "error while opening file");
            deletePrivateFile(fileName);
            return;
        }
        byte[] buffer = null;
        try {
            buffer = new byte[inputImage.available()];
            inputImage.read(buffer);
            Log.i(getString(R.string.LOG_TAG), "buffer initialized");
        } catch (IOException e) {
            Log.e(getString(R.string.LOG_TAG), "error while creating or filling buffer");
            deletePrivateFile(fileName);
            return;
        }
        Bitmap imageAsBitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
        buffer = null;
        final Matrix rotationMatrix = new Matrix();
        switch (screenRotation) {
            case Surface.ROTATION_0:
                rotationMatrix.setRotate(90);
                break;
            //normal landscape
            case Surface.ROTATION_270:
                rotationMatrix.setRotate(180);
                break;
            case -1:
                Log.e(getString(R.string.LOG_TAG), "screen rotation value not sent");
        }
        Bitmap rotatedBitmap = Bitmap.createBitmap(imageAsBitmap, 0, 0, imageAsBitmap.getWidth(), imageAsBitmap.getHeight(), rotationMatrix, false);
        Log.i(getString(R.string.LOG_TAG), "image rotated");
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(createImageFile(fileName));
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Log.i(getString(R.string.LOG_TAG), "picture saved");
        } catch (FileNotFoundException e) {
            Log.e(getString(R.string.LOG_TAG), "problem opening output file");
        } catch (IOException e) {
            Log.e(getString(R.string.LOG_TAG), "problem during save in pub directory");
        } finally {
            deletePrivateFile(fileName);
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.i(getString(R.string.LOG_TAG), "service dead");
        }
    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        galleryFolder = new File(storageDirectory, getString(R.string.CAMERA2_BASIC_APP_FOLDER));
        if (!galleryFolder.exists()) {
            galleryFolder.mkdirs();
        }
    }

    private File createImageFile(String name) throws IOException {
        File image = new File(galleryFolder, name + ".jpg");
        return image;
    }

    private void deletePrivateFile(String fileName) {
        File internalFile = new File(getFilesDir(), fileName);
        internalFile.delete();
    }
}

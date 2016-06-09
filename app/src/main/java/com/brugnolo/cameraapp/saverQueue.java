package com.brugnolo.cameraapp;

import android.media.Image;

import java.io.File;

/**
 * Created by ricca on 09/06/2016.
 */
public class saverQueue {
    private ImageSaver[] queue = null;
    private int size;
    private int imageIndex;
    private int fileIndex;

    public saverQueue() {
        queue = new ImageSaver[5];
        size = 0;
        imageIndex = 0;
        fileIndex = 0;
    }

    public synchronized void add(ImageSaver saver) {
        if (size == queue.length) {
            resize();
        }
        queue[size++] = saver;
    }

    public synchronized void addImage(Image image) {
        queue[imageIndex++].setImageToSave(image);
    }

    public synchronized void addFile(File file) {
        queue[fileIndex++].setFileToSave(file);
    }

    private void resize() {
        ImageSaver[] newQueue = new ImageSaver[queue.length * 2];
        System.arraycopy(queue, 0, newQueue, 0, queue.length);
        queue = newQueue;
    }
}

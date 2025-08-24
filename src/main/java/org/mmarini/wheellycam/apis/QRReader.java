/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheellycam.apis;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.objdetect.QRCodeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Controls the webcam
 * -Djava.library.path=C:\opencv\build\java\x64
 */
public abstract class QRReader {

    public static final int SIZE_96X96 = 0;
    public static final int SIZE_160X120 = 1;
    public static final int SIZE_176x144 = 2;
    public static final int SIZE_240X176 = 3;
    public static final int SIZE_240X240 = 4;
    public static final int SIZE_320X240 = 5;
    public static final int SIZE_400X296 = 5;
    public static final int SIZE_480x320 = 7;
    public static final int SIZE_640X480 = 8;
    public static final int SIZE_800X600 = 9;
    public static final int SIZE_1024X768 = 10;
    public static final int SIZE_1280X720 = 11;
    public static final int SIZE_1280X1024 = 12;
    public static final int SIZE_1600X1200 = 13;
    static final Logger logger = LoggerFactory.getLogger(QRReader.class);

    static {
        System.loadLibrary("opencv_java4100");
    }

    /**
     * Returns the captured qr code if any
     */
    static public CameraEvent captureQrCode(BufferedImage img) {
        byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Mat image = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC(3));
        image.put(0, 0, pixels);
        long timestamp = System.currentTimeMillis();
        Mat points = new Mat();
        String data = new QRCodeDetector().detectAndDecode(image, points);
        return CameraEvent.create(timestamp, data, image.width(), image.height(), points, img);
    }
}

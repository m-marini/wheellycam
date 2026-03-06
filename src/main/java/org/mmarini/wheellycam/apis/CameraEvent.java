/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.Locale;

import static java.lang.String.format;

/**
 * Stores the Camera Event properties
 *
 * @param timestamp the event timestamp
 * @param qrcode    the qr code (? if unrecognized)
 * @param width     the camera image width
 * @param height    the camera image height
 * @param points    the qr code vertices
 * @param image     the image
 */
public record CameraEvent(long timestamp, String qrcode, int width, int height, Mat points, String line,
                          BufferedImage image) {
    public static CameraEvent create(long timestamp, String qrcode, int width, int height, Mat points, BufferedImage image) {
        String line = qrCode2String(timestamp, qrcode, width, height, points);
        return new CameraEvent(timestamp, qrcode, width, height, points, line, image);
    }

    /**
     * Returns the string of qrcode result
     */
    private static String qrCode2String(long timestamp, String qrcode, int width, int height, Mat points) {
        return qrcode.isEmpty()
                ? format(Locale.ENGLISH, "%d,?,%d,%d,0,0,0,0,0,0,0,0",
                timestamp,
                width, height)
                : format(Locale.ENGLISH, "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                timestamp,
                qrcode,
                width, height,
                points.get(0, 0)[0],
                points.get(0, 0)[1],
                points.get(0, 1)[0],
                points.get(0, 1)[1],
                points.get(0, 2)[0],
                points.get(0, 2)[1],
                points.get(0, 3)[0],
                points.get(0, 3)[1]);
    }
}

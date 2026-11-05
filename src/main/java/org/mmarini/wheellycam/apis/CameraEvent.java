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
 * Stores the properties of a camera event snapshot.
 *
 * @param timestamp the epoch timestamp when the event was captured
 * @param markerId  the identified marker ID, or negative if unrecognised
 * @param width     the width of the processed camera image
 * @param height    the height of the processed camera image
 * @param points    the matrix containing the vertices of the detected marker
 * @param line      the text data representing the serialised event details
 * @param image     the buffered image instance of the capture
 */
public record CameraEvent(long timestamp, int markerId, int width, int height, Mat points, String line,
                          BufferedImage image) {

    /**
     * Creates and initialises a new camera event instance.
     * <p>
     * This factory method automatically serialises the marker properties
     * into a single formatted line block.
     * </p>
     *
     * @param timestamp the event timestamp
     * @param markerId  the ID of the target marker
     * @param width     the image width
     * @param height    the image height
     * @param points    the coordinate points of the marker
     * @param image     the captured image data
     * @return the initialised camera event object
     */
    public static CameraEvent create(long timestamp, int markerId, int width, int height, Mat points, BufferedImage image) {
        String line = eventToString(timestamp, markerId, width, height, points);
        return new CameraEvent(timestamp, markerId, width, height, points, line, image);
    }

    /**
     * Converts the ArUco marker result attributes into a serialised string.
     *
     * @param timestamp the event timestamp
     * @param markerId  the identifier of the marker
     * @param width     the frame width
     * @param height    the frame height
     * @param points    the spatial vertex points matrix
     * @return a structured string format representing the marker data
     */
    private static String eventToString(long timestamp, int markerId, int width, int height, Mat points) {
        return markerId < 0
                ? format(Locale.ENGLISH, "%d,-1,%d,%d,0,0,0,0,0,0,0,0",
                timestamp,
                width, height)
                : format(Locale.ENGLISH, "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                timestamp,
                markerId,
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

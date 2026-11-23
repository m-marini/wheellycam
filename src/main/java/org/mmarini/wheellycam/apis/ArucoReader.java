/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;

/**
 * Coordinates webcam control and video stream analysis.
 * <p>
 * This class provides specialised mechanisms to process incoming frames
 * and handles native library initialisation.
 * </p>
 * <p>
 * Configuration note: requires <code>-Djava.library.path=C:\opencv\build\java\x64</code>
 * </p>
 */
public abstract class ArucoReader {

    static final Logger logger = LoggerFactory.getLogger(ArucoReader.class);
    private static final Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50);

    /**
     * Calculates the total surface area of a four-cornered marker represented by a Mat.
     * <p>
     * The method splits the quadrilateral into two adjacent triangles sharing a common diagonal
     * (vertices 0-2), calculates the area of each using Heron's formula, and sums them up.
     * </p>
     *
     * @param mat the OpenCV matrix containing the coordinates of the four corners
     * @return the total calculated area of the marker
     */
    private static double area(Mat mat) {
        return heron(mat.get(0, 0), mat.get(0, 1), mat.get(0, 2))
                + heron(mat.get(0, 0), mat.get(0, 2), mat.get(0, 3));
    }
    /**
     * Analyses a buffered image frame to detect ArUco fiducial markers and
     * constructs a corresponding Protocol Buffers {@link MarkerEvent}.
     * <p>
     * The raw image pixels are converted into an OpenCV 3-channel {@link Mat}
     * and scanned using an {@link ArucoDetector}. If multiple markers are
     * detected within the frame, only the marker occupying the largest pixel
     * area is selected, and its four rounded corner coordinates are recorded
     * in clockwise/sequential order. If no valid markers are found, an event
     * with {@code markerId} set to {@code -1} and an empty coordinate list is
     * returned.
     * </p>
     *
     * @param img the source {@link BufferedImage} frame (backed by a 3-byte
     *            {@link DataBufferByte}) captured from the camera
     * @return a {@link Tuple2} pairing the populated {@link MarkerEvent} message
     *         (with capture timestamp, image dimensions, detected marker ID,
     *         and flattened corner coordinates) with the original {@code img}
     */
    static public Tuple2<MarkerEvent, BufferedImage> captureMarkerProto(BufferedImage img) {
        byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Mat image = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC(3));
        image.put(0, 0, pixels);
        long timestamp = System.currentTimeMillis();
        DetectorParameters params = new DetectorParameters();
        ArucoDetector detector = new ArucoDetector(dictionary, params);

        List<Mat> edges = new ArrayList<>(); // four corners markers
        Mat ids = new Mat();                  // found marker ids
        List<Mat> invalid = new ArrayList<>(); // invalid markers

        detector.detectMarkers(image, edges, ids, invalid);
        image.release();
        if (!ids.empty()) {
            int n = ids.rows();
            int markerIdx = 0;
            if (n > 1) {
                // Find the bigest marker
                double bigest = -1;
                for (int i = 0; i < edges.size(); i++) {
                    double area = area(edges.get(i));
                    if (area > bigest) {
                        bigest = area;
                        markerIdx = i;
                    }
                }
            }
            int markerId = (int) ids.get(markerIdx, 0)[0];
            logger.atDebug().log("markerId: {}", markerId);
            logger.atDebug().log("edges: {}", edges);
            ids.release();
            Mat points = edges.get(markerIdx);
            List<Integer> p = IntStream.of(
                            (int) round(points.get(0, 0)[0]),
                            (int) round(points.get(0, 0)[1]),
                            (int) round(points.get(0, 1)[0]),
                            (int) round(points.get(0, 1)[1]),
                            (int) round(points.get(0, 2)[0]),
                            (int) round(points.get(0, 2)[1]),
                            (int) round(points.get(0, 3)[0]),
                            (int) round(points.get(0, 3)[1]))
                    .boxed()
                    .toList();
            ids.release();

            return Tuple2.of(MarkerEvent.newBuilder()
                            .setTimestamp(timestamp)
                            .setMarkerId(markerId)
                            .setWidth(img.getWidth())
                            .setHeight(img.getHeight())
                            .addAllCoords(p)
                            .build(),
                    img);
        }

        return Tuple2.of(MarkerEvent.newBuilder()
                        .setTimestamp(timestamp)
                        .setMarkerId(-1)
                        .setWidth(img.getWidth())
                        .setHeight(img.getHeight())
                        .build(),
                img);
    }

    /**
     * Calculates the area of a triangle defined by three 2D vertices using Heron's formula.
     * <p>
     * This method computes the Euclidean distance between each pair of coordinate points
     * to determine the three side lengths, then delegates the area calculation.
     * </p>
     *
     * @param p0 the coordinates of the first vertex as an array of minimum [x, y]
     * @param p1 the coordinates of the second vertex as an array of minimum [x, y]
     * @param p2 the coordinates of the third vertex as an array of minimum [x, y]
     * @return the calculated area of the triangle
     */
    private static double heron(double[] p0, double[] p1, double[] p2) {
        double dx01 = p1[0] - p0[0];
        double dy01 = p1[1] - p0[1];
        double dx02 = p2[0] - p0[0];
        double dy02 = p2[1] - p0[1];
        double dx12 = p2[0] - p1[0];
        double dy12 = p2[1] - p1[1];
        double a = sqrt(dx01 * dx01 + dy01 * dy01);
        double b = sqrt(dx02 * dx02 + dy02 * dy02);
        double c = sqrt(dx12 * dx12 + dy12 * dy12);
        return heron(a, b, c);
    }

    /**
     * Calculates the area of a triangle given its three side lengths using Heron's formula.
     * <p>
     * The method first computes the semi-perimeter of the triangle and then applies the formula
     * under the square root. If the provided sides do not form a valid triangle,
     * this method returns {@code 0.0}.
     * </p>
     *
     * @param a the length of the first side
     * @param b the length of the second side
     * @param c the length of the third side
     * @return the calculated area of the triangle, or {@code 0.0} if the sides are invalid
     */
    private static double heron(double a, double b, double c) {
        double p = (a + b + c) / 2;
        return sqrt(p * (p - a) * (p - b) * (p - c));
    }
}

/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheellycam.apps;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DetectAruco {
    private static final Logger logger = LoggerFactory.getLogger(DetectAruco.class);

    static {
        // Carica la libreria nativa di OpenCV (assicurati che il percorso sia corretto)
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        String imgPath = "aruco-image.jpg";
        Mat image = Imgcodecs.imread(imgPath);

        if (image.empty()) {
            logger.atError().log("Errore: Impossibile caricare l'immagine.");
            return;
        }

        Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50);

        DetectorParameters params = new DetectorParameters();

        ArucoDetector detector = new ArucoDetector(dictionary, params);

        List<Mat> detectedCorners = new ArrayList<>();
        Mat markerIds = new Mat();

        detector.detectMarkers(image, detectedCorners, markerIds);

        logger.atInfo().log("{} detected markers", detectedCorners.size());
        if (!markerIds.empty()) {
            logger.atInfo().log("ID: {}", markerIds.dump());
            logger.atInfo().log("  {}", markerIds.get(0, 0));
        }
    }
}

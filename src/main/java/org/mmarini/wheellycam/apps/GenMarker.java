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
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class GenMarker {
    private static final Logger logger = LoggerFactory.getLogger(GenMarker.class);

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        Dictionary dizionario = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50);

        // 2. Definisci i parametri del marcatore da generare
        new File("markers").mkdirs();
        for (int markerId = 0; markerId < 50; markerId++) {          // L'identificativo numerico del marcatore (da 0 a 49)
            int dimensionePixel = 800;  // Risoluzione dell'immagine quadrata (es. 800x800 pixel)
            int bitBordo = 1;           // Spessore del bordo nero (valore predefinito = 1)

            // 3. Crea una matrice vuota (Mat) in cui disegnare il marcatore
            Mat immagineMarker = new Mat();

            // 4. Genera il marcatore ArUco canonico
            Objdetect.generateImageMarker(dizionario, markerId, dimensionePixel, immagineMarker, bitBordo);

            // 5. Salva l'immagine su disco (pronta per essere stampata)
            String percorsoSalvataggio = "markers/marker_aruco_id" + markerId + ".png";
            boolean successo = Imgcodecs.imwrite(percorsoSalvataggio, immagineMarker);

            if (successo) {
                logger.atInfo().log("Marcatore ArUco generato con successo e salvato in: " + percorsoSalvataggio);
            } else {
                logger.atError().log("Errore: Impossibile salvare l'immagine del marcatore {} .", markerId);
            }
        }
    }
}

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

import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.objdetect.QRCodeDetector;

import javax.imageio.ImageIO;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Controls the webcam
 * -Djava.library.path=C:\opencv\build\java\x64
 */
public class CameraController {

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

    static {
        System.loadLibrary("opencv_java4100");
    }

    /**
     * Returns the CameraController
     *
     * @param baseUrl      the base url of remote camera
     * @param ledIntensity the LED intensity (0...255)
     * @param frameSize    the frame size
     */
    public static CameraController create(
            String baseUrl,
            int ledIntensity,
            int frameSize) {
        Client client = ClientBuilder.newClient()
                .register(RxFlowableInvokerProvider.class);
        WebTarget statusService = client.target(baseUrl + "/status");
        String captureUrl = baseUrl + "/capture";
        WebTarget ctrlService = client.target(baseUrl + "/control");
        return new CameraController(statusService, ctrlService, captureUrl, ledIntensity, frameSize);
    }

    private final WebTarget statusService;
    private final WebTarget controlService;
    private final String captureUrl;
    private final int ledIntensity;
    private final int frameSize;

    /**
     * Creates the webcam controller
     *
     * @param statusService  the status service
     * @param controlService the control service
     * @param captureUrl     the capture url
     * @param ledIntensity   the LED intensity (0...255)
     * @param frameSize      the frame size
     */
    protected CameraController(WebTarget statusService, WebTarget controlService, String captureUrl, int ledIntensity, int frameSize) {
        this.statusService = statusService;
        this.controlService = controlService;
        this.captureUrl = captureUrl;
        this.ledIntensity = ledIntensity;
        this.frameSize = frameSize;
    }

    /**
     * Returns the capture image
     *
     * @throws IOException in case of error
     */
    Mat capture() throws IOException {
        BufferedImage img = ImageIO.read(URI.create(captureUrl + "?_cb=" + System.currentTimeMillis()).toURL());
        byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC(3));
        mat.put(0, 0, pixels);
        return mat;
    }

    /**
     * Returns the captured qr code if any
     *
     * @throws IOException in case of error
     */
    public CameraEvent captureQrCode() throws IOException {
        Mat image = capture();
        long timestamp = System.currentTimeMillis();
        Mat points = new Mat();
        String data = new QRCodeDetector().detectAndDecode(image, points);
        return new CameraEvent(timestamp, data, image.width(), image.height(), points);
    }

    /**
     * Sets the control parameter
     *
     * @param param  the parameter name
     * @param values the values
     */
    boolean control(String param, Object... values) {
        Response resp = controlService.queryParam("var", param)
                .queryParam("val", values)
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();
        return resp.getStatus() == HttpURLConnection.HTTP_OK;
    }

    /**
     * Returns the status of webcam configuration
     */
    JsonNode status() {
        return statusService.request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<>() {
                });
    }

    /**
     * Returns the action to synchronize the status
     */
    public boolean sync() {
        JsonNode json = status();
        if (json.path("led_intensity").asInt() != ledIntensity) {
            if (!control("led_intensity", ledIntensity)) {
                return false;
            }
        }
        if (json.path("framesize").asInt() != frameSize) {
            return control("framesize", frameSize);
        }
        return true;
    }

    /**
     * Stores the Camera Event properties
     *
     * @param timestamp the event timestamp
     * @param qrcode    the qr code (? if unrecognized)
     * @param width     the camera image width
     * @param height    the camera image height
     * @param points    the qr code vertices
     */
    public record CameraEvent(long timestamp, String qrcode, int width, int height, Mat points) {
    }

}

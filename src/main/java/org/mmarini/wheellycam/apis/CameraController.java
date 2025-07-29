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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.objdetect.QRCodeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Logger logger = LoggerFactory.getLogger(CameraController.class);
    public static final String SYNCHRONIZING_CAMERA_STATE = "Synchronizing";
    public static final String CAPTURING_IMAGE_STATE = "Capturing";
    public static final String WAITING_FOR_CAPTURE_INTERVAL_STATE = "WaitingForCapture";
    public static final String WAITING_FOR_CAMERA_SYNCHRONISATION_STATE = "WaitingForSync";

    static {
        System.loadLibrary("opencv_java4100");
    }

    /**
     * Returns the CameraController
     *
     * @param baseUrl         the base url of remote camera
     * @param ledIntensity    the LED intensity (0...255)
     * @param frameSize       the frame size
     * @param captureInterval the capture interval (ms)
     * @param synchInterval   the synchronisation interval (ms)
     * @param retryInterval   te retry interval (ms)
     */
    public static CameraController create(
            String baseUrl,
            int ledIntensity,
            int frameSize, long captureInterval, long synchInterval, long retryInterval) {
        Client client = ClientBuilder.newClient()
                .register(RxFlowableInvokerProvider.class);
        WebTarget statusService = client.target(baseUrl + "/status");
        String captureUrl = baseUrl + "/capture";
        WebTarget ctrlService = client.target(baseUrl + "/control");
        return new CameraController(statusService, ctrlService, captureUrl, ledIntensity, frameSize, captureInterval, synchInterval, retryInterval);
    }

    private final WebTarget statusService;
    private final WebTarget controlService;
    private final String captureUrl;
    private final int ledIntensity;
    private final int frameSize;
    private final PublishProcessor<CameraEvent> events;
    private final BehaviorProcessor<String> states;
    private final long captureInterval;
    private final long synchInterval;
    private final long retryInterval;
    private final AtomicReference<Status> status;
    private final CompletableSubject closed;

    /**
     * Creates the webcam controller
     *
     * @param statusService   the status service
     * @param controlService  the control service
     * @param captureUrl      the capture url
     * @param ledIntensity    the LED intensity (0...255)
     * @param frameSize       the frame size
     * @param captureInterval the capture interval (ms)
     * @param synchInterval   the synchronisation interval (ms)
     * @param retryInterval   the retry interval (ms)
     */
    protected CameraController(WebTarget statusService, WebTarget controlService, String captureUrl,
                               int ledIntensity, int frameSize,
                               long captureInterval, long synchInterval, long retryInterval) {
        this.statusService = statusService;
        this.controlService = controlService;
        this.captureUrl = captureUrl;
        this.ledIntensity = ledIntensity;
        this.frameSize = frameSize;
        this.captureInterval = captureInterval;
        this.synchInterval = synchInterval;
        this.retryInterval = retryInterval;
        this.events = PublishProcessor.create();
        this.states = BehaviorProcessor.create();
        this.closed = CompletableSubject.create();
        this.status = new AtomicReference<>(new Status(false, false, false, 0));
    }

    /**
     * Returns the captured image pixels
     */
    Mat capture(BufferedImage img) {
        byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC(3));
        mat.put(0, 0, pixels);
        return mat;
    }

    /**
     * Returns the captured image
     */
    Single<BufferedImage> captureImage() {
        return Single.fromSupplier(() -> {
                    logger.atDebug().log("Capturing image ...");
            URL url = URI.create(captureUrl + "?_cb=" + System.currentTimeMillis()).toURL();
            URLConnection c = url.openConnection();
            c.setConnectTimeout(1000);
            c.connect();
            InputStream in = c.getInputStream();
            BufferedImage img = ImageIO.read(in);
            logger.atDebug().log("Captured image.");
                    return img;
                }
        );
    }

    /**
     * Returns the captured qr code if any
     */
    CameraEvent captureQrCode(BufferedImage img) {
        Mat image = capture(img);
        long timestamp = System.currentTimeMillis();
        Mat points = new Mat();
        String data = new QRCodeDetector().detectAndDecode(image, points);
        return CameraEvent.create(timestamp, data, image.width(), image.height(), points, img);
    }

    /**
     * Captures the image
     */
    private void capturing() {
        Status s = status.get();
        if (!s.closed()) {
            if (System.currentTimeMillis() >= status.get().lastSynch + synchInterval) {
                sync();
            } else if (!s.pause()) {
                states.onNext(CAPTURING_IMAGE_STATE);
                captureImage()
                        .subscribeOn(Schedulers.io())
                        .map(this::captureQrCode)
                        .subscribe(this::onEvent,
                                this::onEventError);
            } else {
                states.onNext(WAITING_FOR_CAPTURE_INTERVAL_STATE);
                Completable.timer(captureInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                        .subscribe(this::capturing);
            }
        } else {
            events.onComplete();
            closed.onComplete();
        }
    }

    /**
     * Closes the flows
     */
    private void closeFlows() {
        states.onComplete();
        events.onComplete();
        closed.onComplete();
    }

    /**
     * Closes the controller
     */
    public void close() {
        status.updateAndGet(s -> s.closed(true));
    }

    /**
     * Handles the camera event
     *
     * @param event the camer event
     */
    private void onEvent(CameraEvent event) {
        events.onNext(event);
        if (!status.get().closed()) {
            logger.atDebug().log("Waiting scan interval ...");
            states.onNext(WAITING_FOR_CAPTURE_INTERVAL_STATE);
            Completable.timer(captureInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                    .subscribe(this::capturing);
        } else {
            events.onComplete();
            closed.onComplete();
        }
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
     * Handles the camera error
     *
     * @param error the error
     */
    private void onEventError(Throwable error) {
        logger.atError().setCause(error).log("Error scanning image");
        if (!status.get().closed()) {
            sync();
        } else {
            closeFlows();
        }
    }

    /**
     * Handles the synchronisation on camera
     *
     * @param success true if success
     */
    private void onSync(Boolean success) {
        if (!status.get().closed()) {
            if (success) {
                logger.atDebug().log("Camera synchronised");
                status.updateAndGet(s -> s.lastSynch(System.currentTimeMillis()));
                capturing();
            } else {
                logger.atDebug().log("Camera not synchronised");
                logger.atDebug().log("Waiting retry ...");
                states.onNext(WAITING_FOR_CAMERA_SYNCHRONISATION_STATE);
                Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                        .subscribe(this::sync);
            }
        } else {
            closeFlows();
        }
    }

    /**
     * Handles the synchronisation error
     *
     * @param error the error
     */
    private void onSyncError(Throwable error) {
        logger.atError().setCause(error).log("Error synchronising");
        if (!status.get().closed()) {
            logger.atDebug().log("Waiting retry ...");
            states.onNext(WAITING_FOR_CAMERA_SYNCHRONISATION_STATE);
            Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                    .subscribe(this::sync);
        } else {
            closeFlows();
        }
    }

    /**
     * Returns the state flow
     */
    public Flowable<String> readStates() {
        return states;
    }

    /**
     * Pause the image captures
     *
     * @param pause true id pause
     */
    public void pause(boolean pause) {
        status.updateAndGet(s -> s.pause(pause));
    }

    /**
     * Returns the camera events flow
     */
    public PublishProcessor<CameraEvent> readCamera() {
        return events;
    }

    /**
     * Returns the closed event
     */
    public Completable readClosed() {
        return closed;
    }

    /**
     * Returns the action to synchronise the status
     */
    public Single<Boolean> sendSync() {
        return status().map(json -> {
            if (json.path("led_intensity").asInt() != ledIntensity) {
                if (!control("led_intensity", ledIntensity)) {
                    return false;
                }
            }
            if (json.path("framesize").asInt() != frameSize) {
                return control("framesize", frameSize);
            }
            return true;
        });
    }

    /**
     * Starts the controller
     */
    public void start() {
        Status s0 = status.getAndUpdate(s -> s.started(true));
        if (!s0.started()) {
            sync();
        }
    }

    /**
     * Returns the status of webcam configuration
     */
    Single<JsonNode> status() {
        return Single.fromSupplier(() -> {
                    logger.atDebug().log("Requesting camera status ...");
                    JsonNode status = statusService.request()
                            .accept(MediaType.APPLICATION_JSON_TYPE)
                            .get(new GenericType<>() {
                            });
                    logger.atDebug().log("Received camera status.");
                    return status;
                }
        );
    }

    /**
     * Starts camera synchronisation
     */
    void sync() {
        if (!status.get().closed()) {
            states.onNext(SYNCHRONIZING_CAMERA_STATE);
            sendSync()
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            this::onSync,
                            this::onSyncError
                    );
        } else {
            closeFlows();
        }
    }

    /**
     * The controller status
     *
     * @param started   true if started
     * @param pause     true if pause
     * @param closed    true if closed
     * @param lastSynch last synch instant (ms)
     */
    record Status(boolean started, boolean pause, boolean closed, long lastSynch) {

        public Status closed(boolean closed) {
            return this.closed != closed
                    ? new Status(started, pause, closed, lastSynch)
                    : this;
        }

        public Status lastSynch(long lastSynch) {
            return this.lastSynch != lastSynch
                    ? new Status(started, pause, closed, lastSynch)
                    : this;
        }

        public Status pause(boolean pause) {
            return this.pause != pause
                    ? new Status(started, pause, closed, lastSynch)
                    : this;
        }

        public Status started(boolean started) {
            return this.started != started
                    ? new Status(started, pause, closed, lastSynch)
                    : this;
        }
    }

}

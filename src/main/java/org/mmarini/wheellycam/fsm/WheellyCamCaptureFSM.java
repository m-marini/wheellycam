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

package org.mmarini.wheellycam.fsm;

import com.google.protobuf.InvalidProtocolBufferException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.mqtt.Device;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.mmarini.wheelly.protobuf.webcam.CameraHiMessage;
import org.mmarini.wheellycam.apis.ArucoReader;
import org.mmarini.wheellycam.apis.CameraImageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Concrete implementation of the finite state machine coordinating camera capture workflows.
 * <p>
 * This manager initialises operational lifecycles, driving asynchronous processing tasks
 * and thread-safe transitions by wrapping reactive pipelines and atomic synchronisation primitives.
 * </p>
 */
public class WheellyCamCaptureFSM implements CaptureFSMContext, CaptureFSM {

    private static final Logger logger = LoggerFactory.getLogger(WheellyCamCaptureFSM.class);

    /**
     * Factory helper to create and initialises a state machine coordinator instance.
     *
     * @param config the configuration block containing intervals and proxy settings
     * @return the initialised finite state machine reference
     */
    public static WheellyCamCaptureFSM create(CaptureFSMConfig config) {
        return new WheellyCamCaptureFSM(config);
    }

    private final CaptureFSMConfig config;
    private final AtomicReference<CaptureInternalState> state;
    private final DisconnectedState disconnectedState;
    private final IdleState idleState;
    private final CaptureFSMState capturingState;
    private final ClosedState closedState;
    private final PublishProcessor<Tuple2<MarkerEvent, BufferedImage>> cameraEvents;
    private final BehaviorProcessor<CaptureFSMState> changeStateEvent;
    private final CompletableSubject closedEvent;

    /**
     * Initialises the coordinator structure with defaults and starts in a disconnected status.
     *
     * @param config the configuration properties used to guide the engine behaviour
     */
    public WheellyCamCaptureFSM(CaptureFSMConfig config) {
        this.config = requireNonNull(config);
        this.disconnectedState = new DisconnectedState();
        this.idleState = new IdleState();
        this.capturingState = new CapturingState();
        this.closedState = new ClosedState();
        this.state = new AtomicReference<>(new CaptureInternalState(null, disconnectedState, null, null, null));
        this.cameraEvents = PublishProcessor.create();
        this.changeStateEvent = BehaviorProcessor.createDefault(disconnectedState);
        this.closedEvent = CompletableSubject.create();
        logger.atDebug().log("Created WheellyCamCaptureFSM");
    }

    @Override
    public void captureImage() {
        CameraImageFetcher fetcher = state.get().fetcher();
        Disposable captureTask = fetcher.captureImage()
                .map(ArucoReader::captureMarkerProto)
                .flatMapCompletable(this::sendEvent)
                .subscribe(() -> {
                            logger.atInfo().log("Capture image complete.");
                            state.get().currentState().onImageProcessed(this);
                        },
                        this::processError);
        state.updateAndGet(s -> s.captureTask(captureTask));
    }

    @Override
    public void close() {
        state.get().currentState().onClose(this);
    }

    @Override
    public void closed() {
        changeStateEvent.onComplete();
        cameraEvents.onComplete();
        closedEvent.onComplete();
    }

    @Override
    public void connectCameraDevice() {
        disconnectCameraDevice();
        logger.atDebug().log("Connect device task.");
        Disposable cameraDeviceTask = createDeviceTask();
        state.updateAndGet(s -> s.cameraDeviceReceiver(cameraDeviceTask));
    }

    /**
     * Builds and initialises the asynchronous remote data stream transmission listener task.
     *
     * @return the disposable reference tracking the network message pipeline context
     */
    private Disposable createDeviceTask() {
        logger.atDebug().log("Creating device task.");
        return config.cameraDevice().readData("hi", this::parseHiMessage)
                .timeout(config.watchDogTimeout(), TimeUnit.MILLISECONDS)
                .subscribe(this::onDeviceMessageReceived,
                        this::onDeviceError);
    }

    /**
     * Resolves the active abstract state processor variant currently handling signals.
     *
     * @return the active abstract state implementation
     */
    private CaptureFSMState currentState() {
        return state.get().currentState();
    }

    @Override
    public void disconnectCameraDevice() {
        Disposable old = state.getAndUpdate(s -> s.cameraDeviceReceiver(null))
                .cameraDeviceReceiver();
        if (old != null) {
            logger.atDebug().log("Disconnect camera device.");
            old.dispose();
        }
    }

    /**
     * Dispatches communication exceptions or safety watchdog timeouts to the active state.
     *
     * @param err the error or exception context thrown by the receiver pipeline
     */
    private void onDeviceError(Throwable err) {
        if (err instanceof TimeoutException) {
            this.currentState().onWatchDogBark(this);
        } else {
            this.currentState().onError(this, err);
        }
    }

    /**
     * Forwards a freshly publicised endpoint string message payload down into the active state.
     *
     * @param url the advertised endpoint location received from the device
     */
    private void onDeviceMessageReceived(String url) {
        currentState().onDeviceMessageReceived(this, url);
    }

    private String parseHiMessage(MqttMessage mqttMessage) {
        byte[] payload = mqttMessage.getPayload();
        try {
            return CameraHiMessage.parseFrom(payload)
                    .getUrl();
        } catch (InvalidProtocolBufferException e) {
            logger.atError().setCause(e).log("Error parsing protobuf message");
            return null;
        }
    }

    /**
     * Forwards background processing worker exceptions down to the current state context handler.
     *
     * @param throwable the processing pipeline exception details
     */
    private void processError(Throwable throwable) {
        currentState().onError(this, throwable);
    }

    @Override
    public Flowable<Tuple2<MarkerEvent, BufferedImage>> readCameraEvents() {
        return cameraEvents.onBackpressureLatest();
    }

    @Override
    public Flowable<CaptureFSMState> readChangeStates() {
        return changeStateEvent;
    }

    @Override
    public Completable readClosed() {
        return closedEvent;
    }

    private CompletableSource sendEvent(Tuple2<MarkerEvent, BufferedImage> tuple) {
        cameraEvents.onNext(tuple);
        byte[] messageBfr = tuple.v1().toByteArray();
        return config.arucoDevice().publishData("am", messageBfr);
    }

    @Override
    public void start() {
        Device arucoDevice = config.arucoDevice();
        arucoDevice.subscribe();
        arucoDevice.publishData("hi", "arucoRecognition");
        logger.atInfo().log("Device {} subscribed", arucoDevice.subCommandTopic());
        changeStateEvent.onNext(state.get().currentState());
        currentState().init(this);
    }

    @Override
    public void startCaptureTimer() {
        stopCaptureTimer();
        logger.atDebug().log("Start capture timer");
        Disposable disposable = Completable.timer(config.captureInterval(), TimeUnit.MILLISECONDS)
                .subscribe(() ->
                        state.get().currentState().onCaptureTimerExpired(this));

        state.updateAndGet(s -> s.captureTimer(disposable));
    }

    @Override
    public void stopCaptureTask() {
        Disposable disposable = state.getAndUpdate(s -> s.captureTask(null)).captureTask();
        if (disposable != null) {
            logger.atDebug().log("Stop capture task.");
            disposable.dispose();
        }
    }

    @Override
    public void stopCaptureTimer() {
        Disposable old = state.getAndUpdate(s -> s.captureTimer(null))
                .captureTimer();
        if (old != null) {
            logger.atDebug().log("Stop capture timer.");
            old.dispose();
        }
    }

    @Override
    public void transitTo(CaptureStateId stateId) {
        requireNonNull(stateId);
        CaptureFSMState cs = state.updateAndGet(s -> s.currentState(
                switch (stateId) {
                    case DISCONNECTED -> disconnectedState;
                    case IDLE -> idleState;
                    case CAPTURING -> capturingState;
                    case CLOSED -> closedState;
                })).currentState();
        changeStateEvent.onNext(cs);
        cs.init(this);
    }

    @Override
    public void updateFetcher(String url) {
        try {
            CameraImageFetcher fetcher = CameraImageFetcher.create(url, config.imageProperties());
            state.updateAndGet(s -> s.fetcher(fetcher));
        } catch (IOException e) {
            logger.atError().setCause(e).log("Failed to create image fetcher");
            currentState().onError(this, e);
        }
    }
}
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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;

import java.awt.image.BufferedImage;

/**
 * Defines the finite state machine responsible for managing the camera capture workflow.
 * <p>
 * This interface coordinates the asynchronous behaviour of the engine, ensuring
 * that state transitions and frame processing events are properly synchronised
 * across reactive pipelines.
 * </p>
 */
public interface CaptureFSM {

    /**
     * Closes the state machine and finalises all allocated components safely.
     */
    void close();

    /**
     * Provides a reactive stream of camera events processed by the system.
     *
     * @return a flowable stream emitting the parsed camera events
     */
    Flowable<Tuple2<MarkerEvent, BufferedImage>> readCameraEvents();

    /**
     * Provides a reactive stream tracking the internal lifecycle and state changes.
     *
     * @return a flowable stream emitting the state transition updates
     */
    Flowable<CaptureFSMState> readChangeStates();

    /**
     * Returns a completable context that signals when the instance has completely closed.
     *
     * @return a completable interface tracking the termination sequence
     */
    Completable readClosed();

    /**
     * Starts the finite state machine and initialises the underlying messaging connections.
     *
     * @throws MqttException if the network client fails to establish a communication channel
     */
    void start() throws MqttException;
}
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

/**
 * Defines the operational execution context for the finite state machine.
 * <p>
 * This interface exposes the actions and internal control hooks required
 * to handle device connection changes, asynchronous scheduling, and state transitions
 * within an optimised reactive framework.
 * </p>
 */
public interface CaptureFSMContext {

    /**
     * Triggers an asynchronous image capture workflow task.
     */
    void captureImage();

    /**
     * Signals that the execution pipeline has been entirely closed and shutdown is final.
     */
    void closed();

    /**
     * Initialises a communication connection block to the target proxy camera device.
     */
    void connectCameraDevice();

    /**
     * Cuts the communication link and disconnects from the proxy camera device.
     */
    void disconnectCameraDevice();

    /**
     * Starts the recurrence timer task responsible for triggering frames capture at regular intervals.
     */
    void startCaptureTimer();

    /**
     * Disrupts and stops any currently active background asynchronous image acquisition tasks.
     */
    void stopCaptureTask();

    /**
     * Suspends the capture scheduling timer
     */
    void stopCaptureTimer();

    /**
     * Executes an internal machine transition step moving the engine to the target status.
     *
     * @param stateId the target identifier indicating the new operational status destination
     */
    void transitTo(CaptureStateId stateId);

    /**
     * Updates or initialises the active camera image fetcher instance based on a new endpoint URL.
     * <p>
     * This method handles the dynamic reconfiguration of the image acquisition pipeline,
     * isolating the processing states from the low-level details of the connection setup and
     * matching the newly advertised endpoint with the pre-existing, customised configuration profiles.
     * </p>
     *
     * @param url the newly advertised target endpoint resource locator string received from the device
     */
    void updateFetcher(String url);

    /**
     * Lists the unique identifiers tracking the formal lifecycle states of the capture engine.
     */
    enum CaptureStateId {
        /**
         * Indicates that the communication links to the hardware target are offline.
         */
        DISCONNECTED,
        /**
         * Indicates that the engine is active and waiting for the next scheduled capture trigger.
         */
        IDLE,
        /**
         * Indicates that an asynchronous image acquisition transaction is currently processing.
         */
        CAPTURING,
        /**
         * Indicates that the environment has been finalised and closed.
         */
        CLOSED
    }
}

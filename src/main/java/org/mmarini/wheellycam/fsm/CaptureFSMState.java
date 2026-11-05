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
 * Defines the contract for the lifecycle states within the capture finite state machine.
 * <p>
 * Each implementing class encapsulates the specific behaviour and event handling
 * required when the system resides in that particular state, processing incoming
 * signals and orchestrating synchronised transitions via the execution context.
 * </p>
 */
public interface CaptureFSMState {

    /**
     * Initialises the state actions and triggers any immediate background tasks.
     *
     * @param context the operational execution context of the state machine
     */
    void init(CaptureFSMContext context);

    /**
     * Handles the signal event emitted when the automated capture timer interval expires.
     *
     * @param context the operational execution context of the state machine
     */
    void onCaptureTimerExpired(CaptureFSMContext context);

    /**
     * Invoked when a request to finalise and close the state machine is issued.
     *
     * @param context the operational execution context of the state machine
     */
    void onClose(CaptureFSMContext context);

    /**
     * Processes communication messages received from the monitored hardware device.
     *
     * @param context the operational execution context of the state machine
     * @param url     the freshly advertised target endpoint resource locator string
     */
    void onDeviceMessageReceived(CaptureFSMContext context, String url);

    /**
     * Handles internal pipeline errors or network anomalies thrown during asynchronous operations.
     *
     * @param context   the operational execution context of the state machine
     * @param throwable the exception or error instance detailing the failure cause
     */
    void onError(CaptureFSMContext context, Throwable throwable);

    /**
     * Triggered immediately after a captured camera frame has been processed and analysed.
     *
     * @param context the operational execution context of the state machine
     */
    void onImageProcessed(CaptureFSMContext context);

    /**
     * Handles the safety watchdog notification event when the device heartbeat activity drops.
     *
     * @param context the operational execution context of the state machine
     */
    void onWatchDogBark(CaptureFSMContext context);
}
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mmarini.wheellycam.fsm.CaptureFSMContext.CaptureStateId.CLOSED;
import static org.mmarini.wheellycam.fsm.CaptureFSMContext.CaptureStateId.IDLE;

/**
 * Handles the state machine behaviour when the communication link with the camera is offline.
 * <p>
 * This class coordinates the initialisation of reconnection procedures and monitors
 * incoming device configuration advertisements to drive transition back to the idle loop.
 * </p>
 */
public class DisconnectedState extends CaptureFSMStateWithDefault {
    private static final Logger logger = LoggerFactory.getLogger(DisconnectedState.class);

    /**
     * {@inheritDoc}
     * <p>
     * Initialises the disconnected phase and signals the execution context to
     * establish a connection block to the target proxy camera hardware.
     * </p>
     */
    @Override
    public void init(CaptureFSMContext context) {
        logger.atDebug().log("Init");
        context.connectCameraDevice();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Responds to the external termination request by safely cutting the communication
     * link to the proxy camera hardware and driving a state transition sequence
     * into the final closed context status.
     * </p>
     */
    @Override
    public void onClose(CaptureFSMContext context) {
        logger.atDebug().log("Close");
        context.disconnectCameraDevice();
        context.transitTo(CLOSED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Intercepts the newly advertised endpoint configuration location string, initialises
     * the underlying image fetcher component based on customised parameters, and transits to the idle state.
     * </p>
     */
    @Override
    public void onDeviceMessageReceived(CaptureFSMContext context, String url) {
        logger.atDebug().log("Message receive");
        context.updateFetcher(url);
        context.transitTo(IDLE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Responds to the safety watchdog timeout alert indicating that inactivity expired,
     * re-triggering the native initialisation routine.
     * </p>
     */
    @Override
    public void onWatchDogBark(CaptureFSMContext context) {
        logger.atDebug().log("Watchdog timer expired");
        init(context);
    }

}


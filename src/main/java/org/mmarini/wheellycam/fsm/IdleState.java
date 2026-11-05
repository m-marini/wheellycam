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

import static org.mmarini.wheellycam.fsm.CaptureFSMContext.CaptureStateId.*;

/**
 * Handles the state machine behaviour when the engine is active and awaiting the next scheduling interval.
 * <p>
 * This class coordinates the recurring frame-rate capture clock triggers, updates underlying configurations
 * based on incoming network messages, and manages defensive fallback sequences upon safety timeout alerts.
 * </p>
 */
public class IdleState extends CaptureFSMStateWithDefault {
    private static final Logger logger = LoggerFactory.getLogger(IdleState.class);

    /**
     * {@inheritDoc}
     * <p>
     * Initialises the idle loop sequence and signals the execution context to
     * start the automated scheduling interval capture timer.
     * </p>
     */
    @Override
    public void init(CaptureFSMContext context) {
        logger.atDebug().log("init");
        context.startCaptureTimer();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Responds to the automated capture clock interval expiration, driving the
     * engine status forward into the active image acquisition transaction phase.
     * </p>
     */
    @Override
    public void onCaptureTimerExpired(CaptureFSMContext context) {
        logger.atDebug().log("onCaptureTimerExpired");
        context.transitTo(CAPTURING);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Suspends automated frame scheduling, safely cuts communication links to the
     * proxy camera hardware, and drives a state transition sequence into the final closed context status.
     * </p>
     */
    @Override
    public void onClose(CaptureFSMContext context) {
        logger.atDebug().log("onClose");
        context.stopCaptureTimer();
        context.disconnectCameraDevice();
        context.transitTo(CLOSED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Intercepts freshly advertised location strings to initialises or update the
     * underlying camera image fetcher utility component based on customised layout parameters.
     * </p>
     */
    @Override
    public void onDeviceMessageReceived(CaptureFSMContext context, String url) {
        logger.atDebug().log("onDeviceMessageReceived");
        context.updateFetcher(url);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Responds to safety watchdog activity drops by pausing automated scheduling triggers
     * and forcing the engine flow to fall back into the disconnected processing phase.
     * </p>
     */
    @Override
    public void onWatchDogBark(CaptureFSMContext context) {
        logger.atError().log("Watchdog bark");
        context.stopCaptureTimer();
        context.transitTo(DISCONNECTED);
    }
}

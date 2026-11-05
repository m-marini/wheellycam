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

/**
 * Provides an abstract or fallback base implementation of a state with default logging hooks.
 * <p>
 * This component defines standard defensive behaviour by flagging unhandled, out-of-sequence
 * signals as error occurrences, making it easier to spot invalid engine coordination logic.
 * </p>
 */
public class CaptureFSMStateWithDefault implements CaptureFSMState {
    private static final Logger logger = LoggerFactory.getLogger(CaptureFSMStateWithDefault.class);

    /**
     * {@inheritDoc}
     * <p>
     * Logs an unexpected initialisation event within the current active lifecycle context.
     * </p>
     */
    @Override
    public void init(CaptureFSMContext context) {
        logger.atError().log("Unexpected init at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs an unhandled capture timer expiration signal.
     * </p>
     */
    @Override
    public void onCaptureTimerExpired(CaptureFSMContext context) {
        logger.atError().log("Unexpected onCaptureTimerExpired at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs an unexpected close request issued during an invalid processing phase.
     * </p>
     */
    @Override
    public void onClose(CaptureFSMContext context) {
        logger.atError().log("Unexpected onClose at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs an unhandled message dispatch transaction from the target hardware proxy device.
     * </p>
     */
    @Override
    public void onDeviceMessageReceived(CaptureFSMContext context, String url) {
        logger.atError().log("Unexpected onDeviceMessageReceived at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Intercepts and logs an unexpected pipeline exception or error context details.
     * </p>
     */
    @Override
    public void onError(CaptureFSMContext context, Throwable throwable) {
        logger.atError().setCause(throwable).log("Unexpected onError at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs an out-of-sequence frame finalisation notification.
     * </p>
     */
    @Override
    public void onImageProcessed(CaptureFSMContext context) {
        logger.atError().log("Unexpected onImageProcessed at {}", this.getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs a safety watchdog alert indication that activity dropped unexpectly.
     * </p>
     */
    @Override
    public void onWatchDogBark(CaptureFSMContext context) {
        logger.atError().log("Unexpected onWatchDogTimerExpired at {}", this.getClass().getSimpleName());
    }
}

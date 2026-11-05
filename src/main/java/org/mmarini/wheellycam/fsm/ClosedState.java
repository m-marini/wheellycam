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
 * Handles the final terminal state of the finite state machine lifecycle.
 * <p>
 * This class ensures that all allocated connections are completely released
 * and signals the execution context that the engine has been permanently finalised.
 * </p>
 */
public class ClosedState extends CaptureFSMStateWithDefault {
    private static final Logger logger = LoggerFactory.getLogger(ClosedState.class);

    /**
     * {@inheritDoc}
     * <p>
     * Initialises the termination routine by disconnecting the camera proxy link
     * and dispatching the definitive closure notification event.
     * </p>
     */
    @Override
    public void init(CaptureFSMContext context) {
        logger.atDebug().log("init");
        context.disconnectCameraDevice();
        context.closed();
    }
}

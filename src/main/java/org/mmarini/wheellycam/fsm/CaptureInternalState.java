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

import io.reactivex.rxjava3.disposables.Disposable;
import org.mmarini.wheellycam.apis.CameraImageFetcher;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Immutable container tracking the internal reference states and active reactive subscriptions.
 * <p>
 * This record holds references to the current abstract state, the image fetching utility,
 * and active background task resources to assist with fluid, thread-safe transitions.
 * </p>
 *
 * @param fetcher              the auxiliary resource component targeting camera endpoints
 * @param currentState         the active lifecycle state logic variant
 * @param captureTimer         the active recurring scheduling interval timer task context
 * @param cameraDeviceReceiver the active hardware device messaging flow connection interface
 * @param captureTask          the running asynchronous image processing task token
 */
record CaptureInternalState(CameraImageFetcher fetcher,
                            CaptureFSMState currentState,
                            Disposable captureTimer,
                            Disposable cameraDeviceReceiver,
                            Disposable captureTask) {

    /**
     * Enforces strict validation ensuring that the current logical state remains non-null.
     */
    public CaptureInternalState {
        requireNonNull(currentState);
    }

    /**
     * Updates the remote device receiver subscription reference while preserving immutability.
     *
     * @param cameraDeviceReceiver the newly established notification channel context
     * @return a state clone with the updated context or this instance if reference is identical
     */
    public CaptureInternalState cameraDeviceReceiver(Disposable cameraDeviceReceiver) {
        return Objects.equals(this.cameraDeviceReceiver, cameraDeviceReceiver)
                ? this
                : new CaptureInternalState(fetcher, currentState, captureTimer, cameraDeviceReceiver, captureTask);
    }

    /**
     * Updates the asynchronous processing framework task token while preserving immutability.
     *
     * @param captureTask the freshly initialised image processing transaction context
     * @return a state clone with the updated context or this instance if reference is identical
     */
    public CaptureInternalState captureTask(Disposable captureTask) {
        return Objects.equals(this.captureTask, captureTask)
                ? this
                : new CaptureInternalState(fetcher, currentState, captureTimer, cameraDeviceReceiver, captureTask);
    }

    /**
     * Updates the active intervals scheduling clock instance while preserving immutability.
     *
     * @param captureTimer the newly assigned recurrence timer context token
     * @return a state clone with the updated context or this instance if reference is identical
     */
    public CaptureInternalState captureTimer(Disposable captureTimer) {
        return Objects.equals(this.captureTimer, captureTimer)
                ? this
                : new CaptureInternalState(fetcher, currentState, captureTimer, cameraDeviceReceiver, captureTask);
    }

    /**
     * Switches the active state processing module execution context while preserving immutability.
     *
     * @param currentState the targeted abstract state engine block destination
     * @return a state clone with the updated context or this instance if reference is identical
     */
    public CaptureInternalState currentState(CaptureFSMState currentState) {
        return Objects.equals(this.currentState, currentState)
                ? this
                : new CaptureInternalState(fetcher, currentState, captureTimer, cameraDeviceReceiver, captureTask);
    }

    /**
     * Updates the underlying camera connection fetcher component while preserving immutability.
     *
     * @param fetcher the newly initialised resource loader instance
     * @return a state clone with the updated context or this instance if reference is identical
     */
    public CaptureInternalState fetcher(CameraImageFetcher fetcher) {
        return Objects.equals(this.fetcher, fetcher)
                ? this
                : new CaptureInternalState(fetcher, currentState, captureTimer, cameraDeviceReceiver, captureTask);
    }
}

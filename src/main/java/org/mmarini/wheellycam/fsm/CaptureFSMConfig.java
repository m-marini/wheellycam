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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.mqtt.Device;
import org.mmarini.wheelly.mqtt.RemoteDevice;
import org.mmarini.wheellycam.apis.CameraImageFetcher;
import org.mmarini.yaml.Locator;

import static java.util.Objects.requireNonNull;

/**
 * Stores the configuration profiles required to initialises the finite state machine.
 * <p>
 * This record wraps the intervals and customised properties needed to safely monitor
 * device behaviour and schedule frames acquisition.
 * </p>
 *
 * @param captureInterval the frequency rate of camera frame captures in milliseconds
 * @param watchDogTimeout the maximum inactive period before triggering a safety timeout
 * @param imageProperties the customised structural parameters used for camera capture settings
 * @param arucoDevice     the local MQTT device interface managing ArUco events reporting
 * @param cameraDevice    the remote hardware device target supplying the video data streams
 */
public record CaptureFSMConfig(long captureInterval,
                               long watchDogTimeout,
                               CameraImageFetcher.ImageProperties imageProperties,
                               Device arucoDevice, RemoteDevice cameraDevice) {

    public static final String CAPTURE_INTERVAL_ID = "captureInterval";
    public static final String WATCH_DOG_INTERVAL_ID = "watchDogInterval";
    public static final long DEFAULT_CAPTURE_INTERVAL = 1000;
    public static final long DEFAULT_WATCHDOG_INTERVAL = 30000;

    /**
     * Contextual factory translating configuration hierarchies into verified state machine settings.
     *
     * @param root         the root JSON configuration node element structure
     * @param locator      the tracking tool utility targeting keys inside the tree
     * @param arucoDevice  the initialised local device reference
     * @param cameraDevice the initialised remote proxy target reference
     * @return the fully populated configuration block instance
     */
    public static CaptureFSMConfig fromJson(JsonNode root, Locator locator, Device arucoDevice, RemoteDevice cameraDevice) {
        long captureInterval = locator.path(CAPTURE_INTERVAL_ID).getNode(root).asLong(DEFAULT_CAPTURE_INTERVAL);
        long watchDogInterval = locator.path(WATCH_DOG_INTERVAL_ID).getNode(root).asLong(DEFAULT_WATCHDOG_INTERVAL);
        CameraImageFetcher.ImageProperties imageProperties = CameraImageFetcher.ImageProperties.fromJson(root, locator);
        return new CaptureFSMConfig(captureInterval, watchDogInterval, imageProperties, arucoDevice, cameraDevice);
    }

    /**
     * Validates that all crucial object references are non-null before initialisation.
     */
    public CaptureFSMConfig {
        requireNonNull(imageProperties);
        requireNonNull(arucoDevice);
        requireNonNull(cameraDevice);
    }
}

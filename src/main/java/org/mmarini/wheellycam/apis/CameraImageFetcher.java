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

package org.mmarini.wheellycam.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Commits to fetching camera frames asynchronously using a reactive pipeline.
 * <p>
 * This component handles downloading binary image streams from the
 * dynamically discovered camera endpoint, preparing data transmission
 * required for the ArUco marker recognition workflow. It ensures network tasks
 * are offloaded from the main MQTT signalling thread to an optimised I/O thread pool.
 * </p>
 *
 * @param imageUrl the targeted HTTP endpoint URL used for fetching the camera captures
 */
public record CameraImageFetcher(HttpUrl imageUrl) {
    public static final int DEFAULT_HTTP_CONNECTION_TIMEOUT = 10;
    public static final int DEFAULT_HTTP_READ_TIMEOUT = 10;
    public static final String FRAME_SIZE_ID = "frameSize";
    public static final String VFLIP_ID = "vflip";
    public static final String HMIRROR_ID = "hmirror";
    public static final String SATURATION_ID = "saturation";
    public static final String CONTRAST_ID = "contrast";
    public static final String FLASH_INTENSITY_ID = "flashIntensity";
    public static final String BRIGHTNESS_ID = "brightness";
    public static final int MAX_IDLE_CONNECTIONS = 5;
    public static final int KEEP_ALIVE_DURATION = 300;
    public static final int DEFAULT_FRAME_SIZE = 10;
    private static final Logger logger = LoggerFactory.getLogger(CameraImageFetcher.class);

    /**
     * Configures and initialises the shared HTTP client with specified timeout intervals.
     *
     * @param connectTimeout the maximum time allowed for network initialisation and connection
     * @param readTimeout    the maximum time allowed for receiving frame segments
     * @return the configured client instance
     */
    public static OkHttpClient configure(long connectTimeout, long readTimeout) {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.SECONDS))
                .build();
        return httpClient;
    }

    /**
     * Creates and initialises a new camera image fetcher instance based on target settings.
     *
     * @param serverEndpoint the remote host application base address
     * @param props          the customised snapshot parameters
     * @return the initialised fetcher component
     * @throws IOException if the endpoint format cannot be parsed properly
     */
    public static CameraImageFetcher create(String serverEndpoint, ImageProperties props) throws IOException {
        return new CameraImageFetcher(imageUrl(serverEndpoint, props));
    }

    /**
     * Serialises parameter constraints into a fully qualified resource location query.
     *
     * @param serverEndpoint the host base URL address
     * @param props          the target properties configuration block
     * @return the structured URL address containing specified parameters
     */
    private static HttpUrl imageUrl(String serverEndpoint, ImageProperties props) {
        HttpUrl baseUrl = HttpUrl.parse(serverEndpoint + "/capture");
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.newBuilder().addQueryParameter("f", String.valueOf(props.frameSize()))
                .addQueryParameter("v", String.valueOf(props.vflip()))
                .addQueryParameter("h", String.valueOf(props.hmirror()))
                .addQueryParameter("b", String.valueOf(props.brightness()))
                .addQueryParameter("c", String.valueOf(props.contrast()))
                .addQueryParameter("s", String.valueOf(props.saturation()))
                .addQueryParameter("l", String.valueOf(props.flashIntensity()))
                .build();
    }

    /**
     * Validates that the endpoint URL parameter is non-null.
     */
    public CameraImageFetcher {
        requireNonNull(imageUrl);
    }

    /**
     * Requests the raw binary payload stream over an optimised reactive pipeline background worker.
     *
     * @return a single containing the primitive byte array stream data
     */
    private Single<byte[]> capture() {
        return Single.create(this::syncCapture).subscribeOn(Schedulers.io());
    }

    /**
     * Performs a pipeline transmission step transforming raw data into an image structure.
     *
     * @return a single containing the fully constructed buffered image reference
     */
    public Single<BufferedImage> captureImage() {
        return capture()
                .observeOn(Schedulers.computation())
                .map(bfr ->
                        ImageIO.read(new ByteArrayInputStream(bfr)));
    }

    /**
     * Executes the synchronous network transaction context mapping responses to the engine.
     *
     * @param emitter the subscription context interface handling downstream delivery status
     */
    private void syncCapture(SingleEmitter<byte[]> emitter) {
        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();
        logger.atDebug().log("Getting {}", imageUrl);
        httpClient.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                if (!emitter.isDisposed()) {
                    emitter.onError(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP code " + response.code());
                    }
                    if (!emitter.isDisposed()) {
                        logger.atDebug().log("Image received {}", imageUrl);
                        emitter.onSuccess(body.bytes());
                    }
                } catch (IOException e) {
                    logger.atError().setCause(e).log("Error receiving image");
                    if (!emitter.isDisposed()) {
                        emitter.onError(e);
                    }
                }
            }
        });
    }

    /**
     * Represents the specific visual configurations utilised during the capture workflow.
     *
     * @param frameSize      the relative dimensions profile indicator
     * @param vflip          specifies if vertical image flipping behaviour should be enforced
     * @param hmirror        specifies if horizontal mirroring should be active
     * @param saturation     the adjusted colour intensity level
     * @param contrast       the system rendering contrast parameter
     * @param brightness     the general lighting adjustment parameter
     * @param flashIntensity the brightness of the built-in flash hardware auxiliary unit
     */
    public record ImageProperties(int frameSize, int vflip, int hmirror, int saturation, int contrast,
                                  int brightness,
                                  int flashIntensity) {

        /**
         * Contextual factory translating configuration structures into valid initialised parameters.
         *
         * @param root    the node configuration element hierarchy block
         * @param locator the utility parsing tool targeting key elements
         * @return the mapped model reference containing verified properties
         */
        public static ImageProperties fromJson(JsonNode root, Locator locator) {
            int frameSize = locator.path(FRAME_SIZE_ID).getNode(root).asInt(DEFAULT_FRAME_SIZE);
            int vflip = locator.path(VFLIP_ID).getNode(root).asInt();
            int hmirror = locator.path(HMIRROR_ID).getNode(root).asInt();
            int saturation = locator.path(SATURATION_ID).getNode(root).asInt();
            int contrast = locator.path(CONTRAST_ID).getNode(root).asInt();
            int brightness = locator.path(BRIGHTNESS_ID).getNode(root).asInt();
            int flashIntensity = locator.path(FLASH_INTENSITY_ID).getNode(root).asInt();
            return new ImageProperties(frameSize, vflip, hmirror, saturation, contrast, brightness,
                    flashIntensity);
        }

        /**
         * Validates parameter ranges adhering to strict system hardware behaviour limits.
         *
         * @throws IllegalArgumentException if any metric falls outside established bounds
         */
        public ImageProperties {
            if (!(frameSize >= 5 && frameSize <= 15)) {
                throw new IllegalArgumentException("frame size must be between 5 and 15");
            }
            if (!(vflip >= 0 && vflip <= 1)) {
                throw new IllegalArgumentException("vflip must be between 0 and 1");
            }
            if (!(hmirror >= 0 && hmirror <= 1)) {
                throw new IllegalArgumentException("hmirror must be between 0 and 1");
            }
            if (!(brightness >= -2 && brightness <= 2)) {
                throw new IllegalArgumentException("brightness must be between -2 and 2");
            }
            if (!(contrast >= -2 && contrast <= 2)) {
                throw new IllegalArgumentException("contrast must be between -2 and 2");
            }
            if (!(saturation >= -2 && saturation <= 2)) {
                throw new IllegalArgumentException("saturation must be between -2 and 2");
            }
            if (!(flashIntensity >= 0 && flashIntensity <= 255)) {
                throw new IllegalArgumentException("flash intensity must be between 0 and 255");
            }
        }
    }

    private static OkHttpClient httpClient = configure(DEFAULT_HTTP_CONNECTION_TIMEOUT, DEFAULT_HTTP_READ_TIMEOUT);
}
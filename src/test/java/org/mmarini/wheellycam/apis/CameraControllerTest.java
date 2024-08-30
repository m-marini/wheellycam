/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.Mat;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;

class CameraControllerTest {
    private CameraController cameraController;

    @Test
    void captureQrcodeTest() throws IOException {
        // Given ...

        // When ...
        CameraController.CameraEvent qrcode = cameraController.captureQrCode();

        // Then ...
        assertNotNull(qrcode);
        assertEquals("A", qrcode.qrcode());
        assertThat(qrcode.points().dump(), matchesPattern("\\[.*]"));
    }

    @Test
    void captureTest() throws IOException {
        // Given ...

        // When ...
        Mat image = cameraController.capture();

        // Then ...
        assertNotNull(image);
        assertEquals(320, image.width());
        assertEquals(240, image.height());
        assertEquals(3, image.channels());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 127, 255})
    void controlTest127(int value) {
        // Given ...

        // When ...
        boolean ctrl = cameraController.control("led_intensity", value);
        assertTrue(ctrl);
        JsonNode status = cameraController.status();

        // Then ...
        assertNotNull(status);
        assertEquals(value, status.path("led_intensity").asInt());
    }

    @Test
    void createTest() {
        // Given ...

        // When ...

        // Then ...
        assertNotNull(cameraController);
    }

    @BeforeEach
    void setUp() {
        cameraController = CameraController.create(
                "http://192.168.1.89",
                255, CameraController.SIZE_320X240);
    }

    @Test
    void syncTest() {
        // Given ...

        // When ...
        boolean ctrl = cameraController.sync();
        assertTrue(ctrl);
    }

}
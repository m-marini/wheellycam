/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheellycam.apps;

import io.reactivex.Single;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Handles the client comunication
 */
public class CameraClient {
    private static final Logger logger = LoggerFactory.getLogger(CameraClient.class);

    /**
     * Returns the camera client
     *
     * @param socket the client socket
     * @throws IOException in case of error
     */
    public static CameraClient create(AsynchronousSocketChannel socket) throws IOException {
        return new CameraClient(socket);
    }

    private final AsynchronousSocketChannel socket;
    private Disposable eventSubscription;
    private final CompletableSubject closed;

    /**
     * Creates the camera client
     *
     * @param socket the client socket
     */
    protected CameraClient(AsynchronousSocketChannel socket) {
        this.socket = requireNonNull(socket);
        this.closed = CompletableSubject.create();
    }

    /**
     * Closes the camera client
     */
    public void close() {
        Disposable sub = this.eventSubscription;
        this.eventSubscription = null;
        sub.dispose();
        try {
            socket.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error closing client socket");
        }
        closed.onComplete();
    }

    /**
     * Handles the send error
     *
     * @param error the error
     */
    private void onSendError(Throwable error) {
        logger.atError().setCause(error).log("Error sending data to client");
        close();
    }

    /**
     * Returns the closed client
     */
    public Completable readClose() {
        return closed;
    }

    /**
     * Sends lines
     *
     * @param lines the lines flow
     */
    public void sendLines(Flowable<String> lines) {
        this.eventSubscription = lines.subscribeOn(Schedulers.io())
                .subscribe(this::sendText,
                        err -> {
                            logger.atError().setCause(err).log("Error reading data to send");
                            close();
                        });
    }

    /**
     * Send a line to the client
     *
     * @param text the line
     */
    private void sendText(String text) {
        if (!socket.isOpen()) {
            close();
        } else {
            byte[] bytes = (text + "\r\n").getBytes(StandardCharsets.UTF_8);
            ByteBuffer bfr = ByteBuffer.allocate(bytes.length);
            bfr.put(bytes).flip();
            Single.fromFuture(socket.write(bfr))
                    .subscribe(i -> {
                            },
                            this::onSendError);
        }
    }
}

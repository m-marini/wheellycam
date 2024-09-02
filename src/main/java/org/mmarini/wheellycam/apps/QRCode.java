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

package org.mmarini.wheellycam.apps;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.wheellycam.apis.CameraController;
import org.mmarini.yaml.Locator;
import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * QrCode image recognizer
 */
public class QRCode {
    private static final String QRCODE_SCHEMA_YML = "https://mmarini.org/wheelly/qrcode-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(QRCode.class);

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * Returns the list of clean clients
     */
    static private List<Client> cleanClients(List<Client> clients) {
        // Test and filter for closed socket
        List<Client> closed = clients.stream()
                .filter(client -> {
                    try {
                        client.socket.getInputStream().available();
                        client.socket.getOutputStream().flush();
                        return !client.socket.isConnected() || client.socket.isClosed();
                    } catch (IOException e) {
                        return true;
                    }
                })
                .toList();
        if (closed.isEmpty()) {
            return clients;
        }
        clients.forEach(client ->
                logger.atInfo().log("Closed {}:{}",
                        client.socket.getInetAddress().getCanonicalHostName(),
                        client.socket.getPort())
        );
        return clients.stream()
                .filter(client -> !closed.contains(client))
                .toList();
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(QRCode.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("QRCode.title"))
                .description("Run the test.");
        parser.addArgument("-c", "--config")
                .setDefault("qrcode.yml")
                .help("specify the configuration file");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        return parser;
    }

    /**
     * Entry point
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new QRCode(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error starting application");
            System.exit(1);
        }
    }

    /**
     * Returns the string of qrcode result
     *
     * @param qrCode the qrcode result
     */
    private static String qrCode2String(CameraController.CameraEvent qrCode) {
        return qrCode.qrcode().isEmpty()
                ? format(Locale.ENGLISH, "qr %d ? %d %d 0 0 0 0 0 0 0 0",
                qrCode.timestamp(),
                qrCode.width(), qrCode.height())
                : format(Locale.ENGLISH, "qr %d %s %d %d %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f",
                qrCode.timestamp(),
                qrCode.qrcode(),
                qrCode.width(), qrCode.height(),
                qrCode.points().get(0, 0)[0],
                qrCode.points().get(0, 0)[1],
                qrCode.points().get(0, 1)[0],
                qrCode.points().get(0, 1)[1],
                qrCode.points().get(0, 2)[0],
                qrCode.points().get(0, 2)[1],
                qrCode.points().get(0, 3)[0],
                qrCode.points().get(0, 3)[1]);
    }

    private final Namespace args;
    private final AtomicReference<List<Client>> clients;

    /**
     * @param args the argument
     */
    public QRCode(Namespace args) {
        this.args = args;
        this.clients = new AtomicReference<>(List.of());
    }

    /**
     * Runs the application
     */
    private void run() throws IOException {
        logger.atInfo().log("Running {}", QRCode.class.getName());
        JsonNode config = fromFile(args.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, QRCODE_SCHEMA_YML);
        String url = Locator.locate("cameraUrl").getNode(config).asText();
        int ledIntensity = Locator.locate("ledIntensity").getNode(config).asInt(255);
        long captureInterval = Locator.locate("captureInterval").getNode(config).asLong(800);
        long syncInterval = Locator.locate("syncInterval").getNode(config).asLong(30000);
        int frameSize = Locator.locate("frameSize").getNode(config).asInt(CameraController.SIZE_320X240);
        int serverPort = Locator.locate("port").getNode(config).asInt(8100);
        CameraController cameraController = CameraController.create(url, ledIntensity, frameSize);
        long syncTimeout = 0;
        boolean synchro = false;
        Schedulers.io().scheduleDirect(() -> runServer(serverPort));
        for (; ; ) {
            long time = System.currentTimeMillis();
            if (time >= syncTimeout || !synchro) {
                logger.atInfo().log("Synchronizing camera ...");
                if (cameraController.sync()) {
                    syncTimeout = time + syncInterval;
                    synchro = true;
                }
            }
            if (synchro) {
                try {
                    logger.atDebug().log("Capturing QRCode ...");
                    CameraController.CameraEvent qrCode = cameraController.captureQrCode();
                    String line = qrCode2String(qrCode);
                    send(line);
                    logger.atInfo().log("{}", line);
                    Thread.sleep(captureInterval);
                } catch (IOException e) {
                    logger.atError().setCause(e).log("Error capturing qrcode");
                    synchro = false;
                } catch (InterruptedException e) {
                    logger.atError().setCause(e).log("Error capturing qrcode");
                }
            }
        }
    }

    /**
     * Runs socket server
     *
     * @param serverPort the server port
     */
    private void runServer(int serverPort) {
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            for (; ; ) {
                // Waits for a client access
                Socket socket = serverSocket.accept();
                logger.atInfo().log("New client {}:{}",
                        socket.getInetAddress().getCanonicalHostName(),
                        socket.getPort());
                Client cli = new Client(socket,
                        new PrintWriter(socket.getOutputStream(), true)
                );
                // Add list
                this.clients.updateAndGet(list -> {
                    List<Client> clients = new ArrayList<>(list);
                    clients.add(cli);
                    return clients;
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a line to all clients
     *
     * @param line the line
     */
    private void send(String line) {
        // Clean up the client list
        List<Client> clients = this.clients.updateAndGet(QRCode::cleanClients);
        for (Client client : clients) {
            Completable.complete()
                    .observeOn(Schedulers.io())
                    .doOnComplete(() ->
                            client.out.println(line))
                    .subscribe();
        }
    }

    record Client(Socket socket, PrintWriter out) {
    }
}

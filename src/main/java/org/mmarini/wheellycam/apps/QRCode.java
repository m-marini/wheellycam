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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheellycam.apis.CameraController;
import org.mmarini.wheellycam.apis.CameraEvent;
import org.mmarini.wheellycam.swing.Utils;
import org.mmarini.yaml.Locator;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * QrCode image recognizer
 */
public class QRCode {
    public static final Color QR_FRAME_COLOR = Color.WHITE;
    public static final Font QR_FONT = Font.decode(Font.DIALOG).deriveFont(20f);
    public static final BasicStroke QR_STROKE = new BasicStroke(3);
    public static final Color OFF_COLOR = Color.RED;
    public static final Color ON_COLOR = Color.GREEN;
    public static final Color PAUSE_COLOR = Color.YELLOW;
    public static final int DEFAULT_LED_INTENSITY = 255;
    public static final int DEFAULT_CAPTURE_INTERVAL = 800;
    public static final int DEFAULT_RETRY_INTERVAL = 2400;
    public static final int DEFAULT_SYNC_INTERVAL = 30000;
    private static final String QRCODE_SCHEMA_YML = "https://mmarini.org/wheelly/qrcode-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(QRCode.class);

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(QRCode.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("QRCode.title"))
                .description("Run the QR Code server.");
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

    private final Namespace args;
    private final JFrame frame;
    private final JTextField statusText;
    private final JLabel imageView;
    private final JToggleButton pauseButton;
    private final AtomicReference<Status> status;
    private CameraController cameraController;
    private AsynchronousServerSocketChannel serverSocket;

    /**
     * Create the camera server
     * @param args the arguments
     */
    public QRCode(Namespace args) {
        this.args = args;
        this.statusText = new JTextField();
        this.frame = new JFrame();
        this.imageView = new JLabel();
        this.pauseButton = SwingUtils.createToggleButton("QRCode.pauseButton");
        this.status = new AtomicReference<>(new Status(false, null));

        statusText.setEditable(false);
        statusText.setColumns(80);
        statusText.setHorizontalAlignment(JTextField.CENTER);
        statusText.setBackground(Color.BLACK);

        imageView.setPreferredSize(new Dimension(300, 300));

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setTitle(Messages.getString("QRCode.title"));

        createContent();

        createFlow();
    }

    /**
     * Creates content
     */
    private void createContent() {
        new GridLayoutHelper<>(frame.getContentPane()).modify("insets,2,2")
                .modify("at,0,0 nofill weight,0,0 center").add(pauseButton)
                .modify("at,0,1 nofill weight,1,1 center").add(imageView)
                .modify("at,0,2 hfill weight,1,0").add(statusText);
    }

    /**
     * Creates control flows
     */
    private void createFlow() {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (cameraController != null) {
                    cameraController.close();
                }
                stopServer();
                logger.atInfo().log("Closing ...");
            }
        });
        pauseButton.addActionListener(this::onPause);
    }

    /**
     * Display message info
     *
     * @param color the colour info
     * @param fmt   the format
     * @param args  the arguments
     */
    private void info(Color color, String fmt, Object... args) {
        String text = format(fmt, args);
        statusText.setForeground(color);
        statusText.setText(text);
        logger.atInfo().log(text);
    }

    /**
     * Handle the acceptation of client socket
     * @param socket the socket
     */
    private void onAccept(AsynchronousSocketChannel socket) {
        Status s1 = status.updateAndGet(s -> s.accepting(null));
        if (!s1.exit()) {
            try {
                CameraClient cli = CameraClient.create(socket);
                Flowable<String> textFlow = cameraController.readCamera()
                        .map(CameraEvent::line);
                cli.sendLines(textFlow);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error creating client");
                try {
                    socket.close();
                } catch (IOException e1) {
                    logger.atError().setCause(e1).log("Error closing client socket");
                }
            }
            startServer();
        }
    }

    /**
     * Handles the acceptance error
     *
     * @param error the error
     */
    private void onAcceptError(Throwable error) {
        Status s1 = status.updateAndGet(s -> s.accepting(null));
        logger.atError().setCause(error).log("Error accepting client");
        if (!s1.exit()) {
            startServer();
        }
    }

    /**
     * Handles the camera error
     *
     * @param error the error
     */
    private void onCameraError(Throwable error) {
        logger.atError().setCause(error).log("Error reading camera event");
    }

    /**
     * Handles the camera event
     *
     * @param event the camera event
     */
    private void onCameraEvent(CameraEvent event) {
        BufferedImage img = event.image();
        if (!event.qrcode().isEmpty()) {
            // Draws qr code bound
            Mat pts = event.points();
            Graphics2D gr = img.createGraphics();
            Polygon poly = new Polygon();
            gr.setStroke(QR_STROKE);
            gr.setFont(QR_FONT);
            for (int i = 0; i < 4; i++) {
                double[] p = pts.get(0, i);
                int x = (int) round(p[0]);
                int y = (int) round(p[1]);
                poly.addPoint(x, y);
                gr.drawString(event.qrcode(), x, y);
            }
            gr.setColor(QR_FRAME_COLOR);
            gr.draw(poly);
        }
        imageView.setIcon(new ImageIcon(img));
        info(ON_COLOR, "%s", event.line());
    }

    /**
     * Handles the pause button
     *
     * @param actionEvent the event
     */
    private void onPause(ActionEvent actionEvent) {
        cameraController.pause(pauseButton.isSelected());
    }

    /**
     * Runs the application
     */
    private void run() throws IOException {
        info(OFF_COLOR, "Running %s", QRCode.class.getName());
        JsonNode config = fromFile(args.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, QRCODE_SCHEMA_YML);
        String url = Locator.locate("cameraUrl").getNode(config).asText();
        int ledIntensity = Locator.locate("ledIntensity").getNode(config).asInt(DEFAULT_LED_INTENSITY);
        long captureInterval = Locator.locate("captureInterval").getNode(config).asLong(DEFAULT_CAPTURE_INTERVAL);
        long retryInterval = Locator.locate("retryInterval").getNode(config).asLong(DEFAULT_RETRY_INTERVAL);
        long syncInterval = Locator.locate("syncInterval").getNode(config).asLong(DEFAULT_SYNC_INTERVAL);
        int frameSize = Locator.locate("frameSize").getNode(config).asInt(CameraController.SIZE_320X240);
        int serverPort = Locator.locate("port").getNode(config).asInt(8100);
        // Creates the server socket
        this.serverSocket = AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(serverPort));

        // Creates the camera controller
        this.cameraController = CameraController.create(url, ledIntensity, frameSize, retryInterval, syncInterval, captureInterval);
        cameraController.readCamera()
                .subscribeOn(Schedulers.io())
                .subscribe(this::onCameraEvent,
                        this::onCameraError);


        frame.setVisible(true);
        Utils.center(frame);

        cameraController.start();
        startServer();
        cameraController.readClosed().blockingAwait();
        logger.atInfo().log("Controller closed");
    }

    /**
     * Starts server
     */
    private void startServer() {
        Disposable accepting = Single.fromFuture(serverSocket.accept())
                .subscribeOn(Schedulers.io())
                .subscribe(this::onAccept,
                        this::onAcceptError);
        status.updateAndGet(s -> s.accepting(accepting));
    }

    /**
     * Stops the server
     */
    private void stopServer() {
        Status s1 = status.getAndUpdate(s -> s.exit(true).accepting(null));
        if (s1.accepting() != null) {
            s1.accepting().dispose();
        }
    }

    /**
     * The server status
     * @param exit true if exit request
     * @param accepting the disposable accepting
     */
    public record Status(boolean exit, Disposable accepting) {
        public Status accepting(Disposable accepting) {
            return !Objects.equals(this.accepting, accepting)
                    ? new Status(exit, accepting)
                    : this;
        }

        public Status exit(boolean exit) {
            return this.exit != exit
                    ? new Status(exit, accepting)
                    : this;
        }
    }
}

/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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
import hu.akarnokd.rxjava3.swing.SwingSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.mmarini.Tuple2;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.mqtt.Device;
import org.mmarini.wheelly.mqtt.RemoteDevice;
import org.mmarini.wheelly.mqtt.RxMqttClient;
import org.mmarini.wheelly.protobuf.aruco.MarkerEvent;
import org.mmarini.wheellycam.apis.CameraImageFetcher;
import org.mmarini.wheellycam.fsm.CaptureFSM;
import org.mmarini.wheellycam.fsm.CaptureFSMConfig;
import org.mmarini.wheellycam.fsm.WheellyCamCaptureFSM;
import org.mmarini.wheellycam.swing.Utils;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static org.mmarini.wheellycam.apis.CameraImageFetcher.DEFAULT_HTTP_CONNECTION_TIMEOUT;
import static org.mmarini.wheellycam.apis.CameraImageFetcher.DEFAULT_HTTP_READ_TIMEOUT;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * ArUco image recogniser server application.
 */
public class ArucoMarker {
    public static final Color MARKER_FRAME_COLOR = Color.WHITE;
    public static final Font MARKER_FONT = Font.decode(Font.DIALOG).deriveFont(20f);
    public static final BasicStroke MARKER_STROKE = new BasicStroke(3);
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";
    public static final long DEFAULT_RETRY_INTERVAL = 3000L;
    public static final String DEFAULT_DEVICE_NAME = "wheellyma";
    public static final String DEFAULT_DEVICE_VERSION = "v1";
    public static final String DEFAULT_CAMERA_NAME = "wheellycam";
    public static final String DEFAULT_CAMERA_VERSION = "v0";
    private static final String QRCODE_SCHEMA_YML = "https://mmarini.org/wheelly/aruco-markers-schema-0.1";
    private static final Color ERROR_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Logger logger = LoggerFactory.getLogger(ArucoMarker.class);

    /*
     * Loads the specific OpenCV native binary version required for image processing.
     */
    static {
        try {
            System.loadLibrary("opencv_java4100");
        } catch (Exception e) {
            logger.error("Failed to load opencv_java4100", e);
            throw e;
        }
    }

    /**
     * Creates and returns the argument parser configured for the application.
     *
     * @return the initialised argument parser instance
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(ArucoMarker.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("QRCode.title"))
                .description("Run the QR Code server.");
        parser.addArgument("-c", "--config")
                .setDefault("arucomarker.yml")
                .help("specify the configuration file");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        return parser;
    }

    /**
     * Generates a unique device identifier based on the computer name and main class.
     *
     * @return a formatted string representing the unique device ID
     */
    private static String generateDeviceId() {
        String domainName = System.getenv("COMPUTERNAME") + ArucoMarker.class.getCanonicalName();
        UUID uuid = UUID.nameUUIDFromBytes(domainName.getBytes());
        long lsb = uuid.getLeastSignificantBits();
        return format("%012x", lsb & 0xffffffffffffL);
    }

    /**
     * Main entry point of the server application.
     *
     * @param args the command line arguments passed to the programme
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new ArucoMarker(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error starting application");
            System.exit(1);
        }
    }

    /**
     * Maps a camera event to a graphical icon presentation tuple.
     * <p>
     * If a valid marker identifier is recognised, this method renders visual indicators
     * by drawing boundary polygons and overlaying the specific marker ID onto the image buffer
     * before returning the final icon container.
     * </p>
     *
     * @param tuple the camera snapshot event detailing marker data and image frames
     * @return a tuple containing the generated image icon instance and the associated marker ID
     */
    private static Tuple2<ImageIcon, Integer> mapToIcon(Tuple2<MarkerEvent, BufferedImage> tuple) {
        BufferedImage img = tuple.v2();
        MarkerEvent event = tuple.v1();
        if (event.getMarkerId() >= 0) {
            // Draws qr code bound
            List<Integer> pts = event.getCoordsList();
            Graphics2D gr = img.createGraphics();
            Polygon poly = new Polygon();
            gr.setStroke(MARKER_STROKE);
            gr.setFont(MARKER_FONT);
            for (int i = 0; i < 4; i++) {
                int x = pts.get(i * 2);
                int y = pts.get(i * 2 + 1);
                poly.addPoint(x, y);
                gr.drawString(String.valueOf(event.getMarkerId()), x, y);
            }
            gr.setColor(MARKER_FRAME_COLOR);
            gr.draw(poly);
        }
        return Tuple2.of(new ImageIcon(img), event.getMarkerId());
    }

    private final Namespace args;
    private final JFrame frame;
    private final JTextField statusText;
    private final JTextField deviceField;
    private final JLabel imageView;
    private final AtomicBoolean exit;
    private RxMqttClient mqttClient;
    private long retryInterval;
    private CaptureFSM fsm;

    /**
     * Initialises a new camera server instance with parsed command line arguments.
     *
     * @param args the parsed command line arguments configuration
     */
    public ArucoMarker(Namespace args) {
        this.args = args;
        this.statusText = new JTextField();
        this.deviceField = new JTextField();
        this.frame = new JFrame();
        this.imageView = new JLabel();
        this.exit = new AtomicBoolean(false);

        statusText.setEditable(false);
        statusText.setColumns(80);
        statusText.setHorizontalAlignment(JTextField.CENTER);
        statusText.setBackground(Color.BLACK);

        deviceField.setEditable(false);
        deviceField.setColumns(20);
        deviceField.setHorizontalAlignment(JTextField.LEFT);

        imageView.setPreferredSize(new Dimension(300, 300));

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setTitle(Messages.getString("QRCode.title"));

        createContent();

        createFlow();
    }

    /**
     * Closes the MQTT client and finalises network resources safely.
     */
    private void closeMqttClient() {
        logger.atInfo().log("Closing mqtt client ...");
        try {
            mqttClient.close();
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error closing mqtt client");
        }
        logger.atInfo().log("Mqtt client closed");
    }

    /**
     * Creates and layouts the graphical user interface elements within the frame.
     */
    private void createContent() {
        /*
            | Device | device field |
            |        image          |
            |        status         |
         */
        new GridLayoutHelper<>(frame.getContentPane()).modify("insets,2,2")
                .modify("at,0,0").add("Device")
                .modify("at,1,0 hw,1 fill e").add(deviceField)
                .modify("at,0,1 hspan,2 fill weight,1,1 center").add(new JScrollPane(imageView))
                .modify("at,0,2 hspan,2 hfill noweight center").add(statusText);
    }

    /**
     * Establishes window event listeners and reactive control flows.
     */
    private void createFlow() {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing(e);
            }
        });
    }

    /**
     * Displays a formatted status message within the user interface using a specified text colour.
     *
     * @param color the specific text colour profile info
     * @param fmt   the string format pattern to render
     * @param args  the arguments to match the specified format pattern
     */
    private void info(Color color, String fmt, Object... args) {
        String text = format(fmt, args);
        statusText.setForeground(color);
        statusText.setText(text);
        logger.atInfo().log(text);
    }

    /**
     * Dispatches an asynchronous initialisation request to connect the MQTT client.
     * <p>
     * If the shutdown flag is inactive, this method updates the status text components
     * on the main window thread and attempts to establish a communication channel
     * with the messaging broker.
     * </p>
     */
    private void mqttConnect() {
        if (!exit.get()) {
            logger.atInfo().log("Starting mqtt client ...");
            SwingUtilities.invokeLater(() ->
                    info(WARNING_COLOR, "Starting mqtt client ...")
            );
            try {
                mqttClient.connect().subscribe(this::onMqttConnected,
                        this::onMqttConnectionError);
            } catch (MqttException e) {
                onMqttConnectionError(e);
            }
        }
    }

    /**
     * Intercepts system anomalies and notifies the user interface of an operational error.
     * <p>
     * Logs the fault context parameters using the internal engine framework
     * and asynchronously updates the status bar colour configuration.
     * </p>
     *
     * @param msg the descriptive text message detailing the intercepted failure
     * @param e   the underlying exception or error instance causing the disruption
     */
    private void notifyError(String msg, Throwable e) {
        logger.atError().setCause(e).log(msg);
        SwingUtilities.invokeLater(() ->
                info(ERROR_COLOR, msg));
    }

    /**
     * Updates the user interface view with the processed image icon frame.
     * <p>
     * This method renders the updated icon and logs specific target summary details
     * based on whether a valid marker identifier was detected.
     * </p>
     *
     * @param t a tuple wrapping the target image icon and the corresponding marker identifier
     */
    private void onImageIcon(Tuple2<ImageIcon, Integer> t) {
        imageView.setIcon(t.v1());
        if (t.v2() >= 0) {
            info(SUCCESS_COLOR, "makerId [%d]", t.v2());
        } else {
            info(SUCCESS_COLOR, "No marker");
        }
    }

    /**
     * Responds to an MQTT broker connection event.
     * <p>
     * Upon a successful network subscription, this method updates the text components
     * on the main window thread and initialises the core finite state machine behaviour.
     * </p>
     *
     * @param connected true if the network communications client is active and online
     */
    private void onMqttConnected(boolean connected) {
        logger.atInfo().log("Mqtt client connected");
        SwingUtilities.invokeLater(() ->
                info(SUCCESS_COLOR, "Mqtt client connected"));
        try {
            fsm.start();
        } catch (MqttException e) {
            notifyError("Error starting finite state machine", e);
        }
    }

    /**
     * Handles exceptions and communication failures thrown by the underlying MQTT network client.
     * <p>
     * Logs the fault context parameters and schedules an automated retry attempt
     * after a specific interval using a background scheduler.
     * </p>
     *
     * @param error the exception detailing the network channel communication drop cause
     */
    private void onMqttConnectionError(Throwable error) {
        logger.atError().setCause(error).log("Error connecting mqtt client");
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe(this::mqttConnect);
    }

    /**
     * Executes final window resource deallocation tasks when the application closes down.
     */
    private void onShutdown() {
        logger.atInfo().log("Shut down completed");
        frame.dispose();
    }

    /**
     * Intercepts window closing notification events to coordinate a safe application shutdown sequence.
     * <p>
     * This handler triggers the reactive clean-up pipeline, ensuring that the background
     * finite state machine is completely finalised and the underlying MQTT messaging client connection
     * is disconnected before releasing window components.
     * </p>
     *
     * @param e the window event trigger context instance
     */
    private void onWindowClosing(WindowEvent e) {
        logger.atInfo().log("Closing ...");
        exit.set(true);
        closeMqttClient();
        fsm.close();
        fsm.readClosed()
                .subscribeOn(Schedulers.computation())
                .doOnComplete(() ->
                        logger.atInfo().log("FSM closed")
                )
                .andThen(mqttClient.closed())
                .doOnComplete(() ->
                        logger.atInfo().log("mqtt closed")
                )
                .observeOn(SwingSchedulers.edt())
                .subscribe(this::onShutdown);
    }

    /**
     * Runs the main server application infrastructure context pipeline.
     * <p>
     * It initialises the configuration file values, validates the schema structure, configures
     * local and remote messaging clients, maps window layouts, and establishes the core reactive tracking channels.
     * </p>
     *
     * @throws IOException   if file transactions or resource parsing fails
     * @throws MqttException if the communications framework fails to initialise properly
     */
    private void run() throws IOException, MqttException {
        JsonNode config = fromFile(args.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, QRCODE_SCHEMA_YML);

        int httpConnectTimeout = Locator.locate("httpConnectTimeout").getNode(config).asInt(DEFAULT_HTTP_CONNECTION_TIMEOUT);
        int httpReadTimeout = Locator.locate("httpReadTimeout").getNode(config).asInt(DEFAULT_HTTP_READ_TIMEOUT);
        String serverUrl = Locator.locate("brokerUrl").getNode(config).asText(DEFAULT_BROKER_URL);
        String userName = Locator.locate("mqttUser").getNode(config).asText();
        String password = Locator.locate("mqttPassword").getNode(config).asText();
        String deviceId = Locator.locate("deviceId").getNode(config).asText(generateDeviceId());
        String deviceName = Locator.locate("deviceName").getNode(config).asText(DEFAULT_DEVICE_NAME);
        String deviceVersion = Locator.locate("deviceVersion").getNode(config).asText(DEFAULT_DEVICE_VERSION);
        this.retryInterval = Locator.locate("retryInterval").getNode(config).asLong(DEFAULT_RETRY_INTERVAL);
        String cameraId = Locator.locate("cameraId").getNode(config).asText();
        String cameraName = Locator.locate("cameraName").getNode(config).asText(DEFAULT_CAMERA_NAME);
        String cameraVersion = Locator.locate("cameraVersion").getNode(config).asText(DEFAULT_CAMERA_VERSION);

        // Set http timing
        CameraImageFetcher.configure(httpConnectTimeout, httpReadTimeout);

        // Creates qrcode mqtt client
        this.mqttClient = RxMqttClient.create(serverUrl, null, userName, password);

        // Create remote camera device
        RemoteDevice cameraDevice = new RemoteDevice(cameraName + "/" + cameraId + "/" + cameraVersion, mqttClient);

        // Creates aruco device
        Device arucoDevice = new Device(deviceName, deviceId, deviceVersion, mqttClient);
        deviceField.setText(deviceName + "/" + deviceId + "/" + deviceVersion);

        // Creates finite state machine
        CaptureFSMConfig fsmConfig = CaptureFSMConfig.fromJson(config, Locator.root(), arucoDevice, cameraDevice);
        this.fsm = WheellyCamCaptureFSM.create(fsmConfig);

        frame.setVisible(true);

        fsm.readCameraEvents()
                .map(ArucoMarker::mapToIcon)
                .observeOn(SwingSchedulers.edt())
                .subscribe(this::onImageIcon);
        fsm.readChangeStates()
                .map(state -> state.getClass().getSimpleName())
                .distinct()
                .observeOn(SwingSchedulers.edt())
                .subscribe(txt -> {
                    Color color = switch (txt) {
                        case "IdleState" -> SUCCESS_COLOR;
                        case "CapturingState" -> WARNING_COLOR;
                        default -> ERROR_COLOR;
                    };
                    info(color, "%s", txt);
                });

        Utils.center(frame);
        mqttConnect();
    }
}

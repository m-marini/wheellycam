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
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.mqtt.Device;
import org.mmarini.wheelly.mqtt.RemoteDevice;
import org.mmarini.wheelly.mqtt.RxMqttClient;
import org.mmarini.wheelly.mqtt.StringCommand;
import org.mmarini.wheellycam.apis.CameraEvent;
import org.mmarini.wheellycam.apis.QRReader;
import org.mmarini.wheellycam.swing.Utils;
import org.mmarini.yaml.Locator;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";
    public static final long DEFAULT_RETRY_INTERVAL = 3000L;
    public static final String DEFAULT_DEVICE_NAME = "wheellyqr";
    public static final String DEFAULT_DEVICE_VERSION = "v0";
    public static final String DEFAULT_CAMERA_NAME = "wheellycam";
    public static final String DEFAULT_CAMERA_VERSION = "v0";
    private static final Logger logger = LoggerFactory.getLogger(QRCode.class);
    private static final String QRCODE_SCHEMA_YML = "https://mmarini.org/wheelly/qrcode-schema-0.3";
    private static final Color ERROR_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    public static final int COMMAND_TIMEOUT = 3000;

    /*
     * Load the native library of opencv
     */
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
     * Returns the device id by computer name and main class
     */
    private static String generateDeviceId() {
        String domainName = System.getenv("COMPUTERNAME") + QRCode.class.getCanonicalName();
        UUID uuid = UUID.nameUUIDFromBytes(domainName.getBytes());
        long lsb = uuid.getLeastSignificantBits();
        return format("%012x", lsb & 0xffffffffffffL);
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
    private final JTextField deviceField;
    private final JLabel imageView;
    private final JButton captureButton;
    private final AtomicReference<Status> status;
    private RxMqttClient mqttClient;
    private long retryInterval;
    private Device qrDevice;
    private RemoteDevice cameraDevice;

    /**
     * Create the camera server
     *
     * @param args the arguments
     */
    public QRCode(Namespace args) {
        this.args = args;
        this.statusText = new JTextField();
        this.deviceField = new JTextField();
        this.frame = new JFrame();
        this.imageView = new JLabel();
        this.captureButton = SwingUtils.createButton("QRCode.captureButton");
        this.status = new AtomicReference<>(new Status(false));

        statusText.setEditable(false);
        statusText.setColumns(80);
        statusText.setHorizontalAlignment(JTextField.CENTER);
        statusText.setBackground(Color.BLACK);

        deviceField.setEditable(false);
        deviceField.setColumns(20);
        deviceField.setHorizontalAlignment(JTextField.LEFT);

        imageView.setPreferredSize(new Dimension(300, 300));

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setTitle(Messages.getString("QRCode.title"));

        createContent();

        createFlow();
    }

    /**
     * Close mqtt client
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
     * Creates content
     */
    private void createContent() {
        new GridLayoutHelper<>(frame.getContentPane()).modify("insets,2,2")
                .modify("at,0,0").add("Device")
                .modify("at,1,0 hw,1 fill e").add(deviceField)
                .modify("at,2,0 noweight nofill center").add(captureButton)
                .modify("at,0,1 hspan,3 fill weight,1,1 center").add(new JScrollPane(imageView))
                .modify("at,0,2 hspan,3 hfill noweight center").add(statusText);
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
     * Connects mqtt client
     */
    private void mqttConnect() {
        if (!status.get().exit) {
            logger.atInfo().log("Starting mqtt client ...");
            try {
                mqttClient.connect().subscribe(this::onMqttConnected,
                        this::onMqttConnectionError);
            } catch (MqttException e) {
                onMqttConnectionError(e);
            }
        }
    }

    /**
     * Creates control flows
     */
    private void createFlow() {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                logger.atInfo().log("Closing ...");
                status.updateAndGet(status1 -> status1.exit(true));
                closeMqttClient();
            }
        });
        captureButton.addActionListener(this::onCaptureButton);
    }

    /**
     * Returns the image from mqtt message
     *
     * @param message the message
     */
    private BufferedImage message2Image(MqttMessage message) {
        InputStream in = new ByteArrayInputStream(message.getPayload());
        try {
            return ImageIO.read(in);
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error getting camera");
            info(ERROR_COLOR, "Error getting camera");
            return null;
        }
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
        qrDevice.publishData("qr", event.line());
        info(SUCCESS_COLOR, "Image captured QRCODE=" + event.qrcode());
    }

    private void onCaptureButton(ActionEvent actionEvent) {
        cameraDevice.execute(StringCommand.create("ca", ""), COMMAND_TIMEOUT)
                .subscribe(x -> {
                        },
                        this::onCaptureError);
    }

    private void onCaptureError(Throwable error) {
        logger.atError().setCause(error).log("Error capturing image");
        info(ERROR_COLOR, "Error capturing image");
    }

    private void onImage(BufferedImage image) {
        CameraEvent event = QRReader.captureQrCode(image);
        onCameraEvent(event);
    }

    /**
     * Handles on mqtt connection event
     *
     * @param connected true if connected
     */
    private void onMqttConnected(boolean connected) {
        logger.atInfo().log("Mqtt client connected");
        logger.atInfo().log("Subscribing mqtt topic ...");
        try {
            qrDevice.subscribe();
            qrDevice.publishData("hi", "");
            logger.atInfo().log("Device {} subscribed", qrDevice.subCommandTopic());
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error subscribing device");
            info(ERROR_COLOR, "Error subscribing topic");
        }
    }

    /**
     * Handles the mqtt client connection error
     *
     * @param error the error
     */
    private void onMqttConnectionError(Throwable error) {
        logger.atError().setCause(error).log("Error connecting mqtt client");
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe(this::mqttConnect);
    }

    /**
     * Runs the application
     */
    private void run() throws IOException, MqttException {
        JsonNode config = fromFile(args.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, QRCODE_SCHEMA_YML);

        // Creates mqtt client
        String serverUrl = Locator.locate("brokerUrl").getNode(config).asText(DEFAULT_BROKER_URL);
        String userName = Locator.locate("mqttUser").getNode(config).asText();
        String password = Locator.locate("mqttPassword").getNode(config).asText();
        String deviceId = Locator.locate("deviceId").getNode(config).asText(generateDeviceId());
        String deviceName = Locator.locate("deviceName").getNode(config).asText(DEFAULT_DEVICE_NAME);
        String deviceVersion = Locator.locate("deviceVersion").getNode(config).asText(DEFAULT_DEVICE_VERSION);
        this.retryInterval = Locator.locate("retryInterval").getNode(config).asLong(DEFAULT_RETRY_INTERVAL);
        this.mqttClient = RxMqttClient.create(serverUrl, null, userName, password);
        this.qrDevice = new Device(deviceName, deviceId, deviceVersion, mqttClient);

        String cameraId = Locator.locate("cameraId").getNode(config).asText();
        String cameraName = Locator.locate("deviceName").getNode(config).asText(DEFAULT_CAMERA_NAME);
        String cameraVersion = Locator.locate("deviceVersion").getNode(config).asText(DEFAULT_CAMERA_VERSION);
        this.cameraDevice = new RemoteDevice(cameraName, cameraId, cameraVersion, mqttClient);

        deviceField.setText(deviceName + "/" + deviceId + "/" + deviceVersion);
        cameraDevice.readData("img", this::message2Image)
                .subscribe(this::onImage);

        frame.setVisible(true);
        Utils.center(frame);

        mqttConnect();
    }

    /**
     * The server status
     *
     * @param exit
     */
    public record Status(boolean exit) {

        public Status exit(boolean exit) {
            return this.exit != exit
                    ? new Status(true)
                    : this;
        }

    }
}

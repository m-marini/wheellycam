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

package org.mmarini.wheelly.mqtt;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.Function2Throws;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Manages the messaging for a device
 */
public class Device {
    private static final Logger logger = LoggerFactory.getLogger(Device.class);
    private final String name;
    private final String id;
    private final String version;
    private final RxMqttClient client;
    private final Map<String, Function2Throws<String, String, String, Exception>> handlers;

    /**
     * Creates the device
     *
     * @param name    the device name
     * @param id      the device uuid
     * @param version the device version
     * @param client  the mqtt client
     */
    public Device(String name, String id, String version, RxMqttClient client) {
        this.name = requireNonNull(name);
        this.id = requireNonNull(id);
        this.version = requireNonNull(version);
        this.client = requireNonNull(client);
        this.handlers = new HashMap<>();
    }

    /**
     * Adds a command handler to the device
     *
     * @param cmd     the command (suffix of the command topic
     * @param handler the function returning the response of the command by topic and argument
     */
    public Device addCommand(String cmd, Function2Throws<String, String, String, Exception> handler) {
        handlers.put(format("cmd/%s/%s/%s/%s", name, id, version, cmd), handler);
        return this;
    }

    /**
     * Returns the executable command
     *
     * @param topic the command topic
     * @param arg   the command argument
     */
    void execute(String topic, String arg) {
        Function2Throws<String, String, String, Exception> handler = handlers.get(topic);
        if (handler == null) {
            logger.atError().log("Command {} not recognized", topic);
            return;
        }
        String response;
        try {
            response = handler.apply(topic, arg);
        } catch (Exception e) {
            try {
                publishError(topic, e.getMessage()).blockingAwait();
            } catch (Throwable err) {
                logger.atError().setCause(err).log("Error publishing error");
            }
            return;
        }
        try {
            publishResponse(topic, response).blockingAwait();
        } catch (Throwable err) {
            logger.atError().setCause(err).log("Error publishing response");
        }
    }

    /**
     * Returns the executable command
     *
     * @param command the command tuple with topics and payload
     */
    void execute(Tuple2<String, String> command) {
        execute(command._1, command._2);
    }

    /**
     * Publishes the data to the sensor topic
     *
     * @param dataType the data type used as suffix of the topic
     * @param data     the data message
     */
    public Completable publishData(String dataType, String data) {
        return client.publish(format("sens/%s/%s/%s/%s", name, id, version, dataType),
                new MqttMessage(data.getBytes()));
    }

    /**
     * Publishes the error response to the command error topic
     *
     * @param topic the command topic
     * @param msg   the error message
     */
    private Completable publishError(String topic, String msg) {
        return client.publish(topic + "/err",
                new MqttMessage(msg.getBytes()));
    }

    /**
     * Publishes the response to the command response topic
     *
     * @param topic the command topic
     * @param msg   the response message
     */
    private Completable publishResponse(String topic, String msg) {
        return client.publish(topic + "/res", new MqttMessage(msg.getBytes()));
    }

    /**
     * Returns the subscription command topic
     */
    public String subCommandTopic() {
        return format("cmd/%s/%s/%s/+", name, id, version);
    }

    /**
     * Subscribes for command execution
     *
     * @throws MqttException in case of error
     */
    public Device subscribe() throws MqttException {
        client.subscribe(subCommandTopic(), 1)
                .map(t -> t.setV2(new String(t._2.getPayload())))
                .observeOn(Schedulers.computation())
                .subscribe(this::execute);
        return this;
    }
}

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
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Function2Throws;
import org.mmarini.NotImplementedException;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceTest {

    private static final Logger logger = LoggerFactory.getLogger(DeviceTest.class);
    Device device;
    RxMqttClient client;

    @BeforeEach
    void setUp() {
        assertDoesNotThrow(() -> {
            client = RxMqttClient.create("tcp://localhost:1883", null, "wheellyj", "wheellyj");
            device = new Device("test", "1", "v0", client);
            client.connect().blockingGet();
            client.subscribe(device.subCommandTopic(), 1);
        });
    }

    @Test
    void testData() {
        TestSubscriber<Tuple2<String, MqttMessage>> sub = new TestSubscriber<>();
        assertDoesNotThrow(() ->
                client.subscribe("sens/test/1/v0/+", 0)
                        .subscribe(sub));
        device.publishData("ts", "data");
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();
        assertDoesNotThrow(() -> client.close());
        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertValueCount(1);
        assertEquals("sens/test/1/v0/ts", sub.values().getFirst()._1);
        assertEquals("data", new String(sub.values().getFirst()._2.getPayload()));
    }

    @Test
    void testExecute() throws MqttException {
        assertEquals("cmd/test/1/v0/+", device.subCommandTopic());
        TestSubscriber<Tuple2<String, MqttMessage>> sub = new TestSubscriber<>();
        client.subscribe("#", 1)
                .subscribe(sub);

        Function2Throws<String, String, String, Exception> handler = (topic, arg) -> {
            logger.atInfo().log("Command execution {}", arg);
            return arg.toUpperCase();
        };
        device.addCommand("ts", handler);
        device.subscribe();
        client.publish("cmd/test/1/v0/ts", new MqttMessage("arg".getBytes()));

        Completable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.computation())
                .blockingAwait();

        client.close();
        client.closed().blockingAwait();

        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertValueCount(2);
        assertEquals("cmd/test/1/v0/ts", sub.values().getFirst()._1);
        assertEquals("arg", new String(sub.values().getFirst()._2.getPayload()));
        assertEquals("cmd/test/1/v0/ts/res", sub.values().get(1)._1);
        assertEquals("ARG", new String(sub.values().get(1)._2.getPayload()));
    }

    @Test
    void testExecuteErr() throws MqttException {
        assertEquals("cmd/test/1/v0/+", device.subCommandTopic());
        TestSubscriber<Tuple2<String, MqttMessage>> sub = new TestSubscriber<>();
        client.subscribe("#", 1)
                .subscribe(sub);

        Function2Throws<String, String, String, Exception> handler = (topic, arg) -> {
            logger.atInfo().log("Command execution {}", arg);
            throw new NotImplementedException();
        };
        device.addCommand("ts", handler);
        device.subscribe();
        client.publish("cmd/test/1/v0/ts", new MqttMessage("arg".getBytes()));

        Completable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.computation())
                .blockingAwait();

        client.close();
        client.closed().blockingAwait();

        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertValueCount(2);
        assertEquals("cmd/test/1/v0/ts", sub.values().getFirst()._1);
        assertEquals("arg", new String(sub.values().getFirst()._2.getPayload()));
        assertEquals("cmd/test/1/v0/ts/err", sub.values().get(1)._1);
        assertEquals("Not implemented", new String(sub.values().get(1)._2.getPayload()));
    }
}
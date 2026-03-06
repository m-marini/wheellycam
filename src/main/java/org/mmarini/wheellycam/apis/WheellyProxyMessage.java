/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.schedulers.Timed;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;

/**
 * Contains the proxy sensor information
 *
 * @param simulationTime     the simulation markerTime (ms)
 * @param sensorDirectionDeg the sensor direction at ping (DEG)
 * @param echoDelay          the echo delay (um)
 * @param xPulses            the x robot location pulses at echo ping
 * @param yPulses            the y robot location pulses at echo ping
 * @param robotYawDeg        the robot direction at ping (DEG)
 */
public record WheellyProxyMessage(long simulationTime,
                                  int sensorDirectionDeg, long echoDelay,
                                  double xPulses, double yPulses, int robotYawDeg
) {
    public static final int NUM_PARAMS = 7;
    // [sampleTime] [sensorDirectionDeg] [distanceTime (us)] [xLocation] [yLocation] [yaw]
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(-?\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+)$");

    public static WheellyProxyMessage create(Timed<String> line, long timeOffset) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NUM_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\" (#params=%d)", line.value(), params.length));
        }
        int echoDirection = parseInt(params[2]);
        int echoDelay = parseInt(params[3]);
        double x = parseDouble(params[4]);
        double y = parseDouble(params[5]);
        int robotYaw = parseInt(params[6]);

        long simTime = time - timeOffset;
        return new WheellyProxyMessage(simTime, echoDirection, echoDelay, x, y, robotYaw);
    }

    /**
     * Returns the proxy message from argument string
     * The string status is formatted as:
     * <pre>
     *     [sampleTime]
     *     [sensorDirectionDeg]
     *     [distanceTime (us)]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     * </pre>
     *
     * @param simTime the simulation time
     * @param arg     the status string
     */
    public static WheellyProxyMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\"", arg));
        }
        int echoDirection = parseInt(m.group(1));
        int echoDelay = parseInt(m.group(2));
        double x = parseDouble(m.group(3));
        double y = parseDouble(m.group(4));
        int robotYaw = parseInt(m.group(5));
        return new WheellyProxyMessage(simTime, echoDirection, echoDelay, x, y, robotYaw);
    }

    /**
     * Creates the proxy message
     *
     * @param simulationTime     the simulation markerTime (ms)
     * @param sensorDirectionDeg the sensor direction at ping (DEG)
     * @param echoDelay          the echo delay (um)
     * @param xPulses            the x robot location pulses at echo ping
     * @param yPulses            the y robot location pulses at echo ping
     * @param robotYawDeg        the robot direction at ping (DEG)
     */
    public WheellyProxyMessage(long simulationTime, int sensorDirectionDeg, long echoDelay, double xPulses, double yPulses, int robotYawDeg) {
        this.simulationTime = simulationTime;
        this.sensorDirectionDeg = sensorDirectionDeg;
        this.echoDelay = echoDelay;
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.robotYawDeg = robotYawDeg;
    }

    /**
     * Returns the proxy message with the echo delay set
     *
     * @param echoDelay echo delay (us)
     */
    public WheellyProxyMessage setEchoDelay(long echoDelay) {
        return echoDelay != this.echoDelay
                ? new WheellyProxyMessage(simulationTime, sensorDirectionDeg, echoDelay, xPulses, yPulses, robotYawDeg)
                : this;
    }

    /**
     * Returns the proxy message with the sensor direction set
     *
     * @param sensorDirectionDeg the sensor direction (DEG)
     */
    public WheellyProxyMessage setSensorDirection(int sensorDirectionDeg) {
        return sensorDirectionDeg != this.sensorDirectionDeg
                ? new WheellyProxyMessage(simulationTime, sensorDirectionDeg, echoDelay, xPulses, yPulses, robotYawDeg)
                : this;
    }

    /**
     * Returns the proxy message with simulation markerTime
     *
     * @param simulationTime the simulation markerTime
     */
    public WheellyProxyMessage setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyProxyMessage(simulationTime, sensorDirectionDeg, echoDelay, xPulses, yPulses, robotYawDeg)
                : this;
    }
}
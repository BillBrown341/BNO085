/***************************** BEGIN LICENSE BLOCK ***************************
 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020-2025 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package com.sample.impl.sensor.BNO085;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;
import org.vast.swe.SWEBuilders;
import org.vast.swe.helper.GeoPosHelper;


/**
 * Output specification and provider for {@link Bno085Sensor}.
 */
public class BNO085Output extends AbstractSensorOutput<Bno085Sensor> {
    static final String SENSOR_OUTPUT_NAME = "SensorOutput";
    static final String SENSOR_OUTPUT_LABEL = "Sensor Output";
    static final String SENSOR_OUTPUT_DESCRIPTION = "Sensor output data";

    // myNote:
    // Added Variables because there were in other templates
    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private int setCount = 0;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();
    private long lastSetTimeMillis = System.currentTimeMillis();


    private DataRecord dataStruct;
    private DataEncoding dataEncoding;

    /**
     * Creates a new output for the sensor driver.
     *
     * @param parentSensor Sensor driver providing this output.
     */
    BNO085Output(Bno085Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
    }

    /**
     * Initializes the data structure for the output, defining the fields, their ordering, and data types.
     */
    void doInit() {
        // Get an instance of SWE Factory suitable to build components
        SWEHelper sweFactory = new SWEHelper();

        // Create the data record description
        SWEBuilders.DataRecordBuilder recordBuilder = sweFactory.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("sampleTime", sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sample Time")
                        .description("Time of data collection"));

        // Conditionally add Acceleration fields
        if (parentSensor.getConfiguration().outputs.isAccelerometer) {
            recordBuilder
                    .addField("accelX", sweFactory.createQuantity()
                            .uom("m/s^2").label("Acceleration X").description("X-axis acceleration"))
                    .addField("accelY", sweFactory.createQuantity()
                            .uom("m/s^2").label("Acceleration Y").description("Y-axis acceleration"))
                    .addField("accelZ", sweFactory.createQuantity()
                            .uom("m/s^2").label("Acceleration Z").description("Z-axis acceleration"));
        }
        // Conditionally add Gravity fields
        if (parentSensor.getConfiguration().outputs.isGravity) {
            recordBuilder
                    .addField("gravityX", sweFactory.createQuantity()
                            .uom("m/s^2").label("Gravity X").description("X-axis gravity"))
                    .addField("gravityY", sweFactory.createQuantity()
                            .uom("m/s^2").label("Gravity Y").description("Y-axis gravity"))
                    .addField("gravityZ", sweFactory.createQuantity()
                            .uom("m/s^2").label("Gravity Z").description("Z-axis gravity"));
        }
        // Conditionally add Gyroscope fields
        if (parentSensor.getConfiguration().outputs.isGyroCal) {
            recordBuilder
                    .addField("gyroX", sweFactory.createQuantity()
                            .uom("rad/s").label("Gyro X").description("X-axis gyro"))
                    .addField("gyroY", sweFactory.createQuantity()
                            .uom("rad/s").label("Gyro Y").description("Y-axis gyro"))
                    .addField("gyroZ", sweFactory.createQuantity()
                            .uom("rad/s").label("Gyro Z").description("Z-axis gyro"));
        }
        // Conditionally add Magnetic Field fields
        if (parentSensor.getConfiguration().outputs.isMagFieldCal) {
            recordBuilder
                    .addField("magFieldX", sweFactory.createQuantity()
                            .uom("µT").label("Magnetic Field X").description("X-axis magnetic field"))
                    .addField("magFieldY", sweFactory.createQuantity()
                            .uom("µT").label("Magnetic Field Y").description("Y-axis magnetic field"))
                    .addField("magFieldZ", sweFactory.createQuantity()
                            .uom("µT").label("Magnetic Field Z").description("Z-axis magnetic field"));
        }
        // Conditionally add Magnetic Field fields
        if (parentSensor.getConfiguration().outputs.isRotation) {
            recordBuilder
                    .addField("rotI", sweFactory.createQuantity()
                            .label("Rotation Quaternion I").description("rotation quaterion i"))
                    .addField("rotJ", sweFactory.createQuantity()
                            .label("Rotation Quaternion J").description("rotation quaterion j"))
                    .addField("rotK", sweFactory.createQuantity()
                            .label("Rotation Quaternion K").description("rotation quaterion k"))
                    .addField("rotR", sweFactory.createQuantity()
                            .label("Rotation Quaternion Real").description("rotation quaterion real"))
                    .addField("rotA", sweFactory.createQuantity()
                            .uom("rad").label("Roation Quaternion Accuracy").description("rotation accuracy"));
        }



        dataStruct = recordBuilder.build();

        dataEncoding = sweFactory.newTextEncoding(",", "\n");
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        long accumulator = 0;
        synchronized (histogramLock) {
            for (int idx = 0; idx < MAX_NUM_TIMING_SAMPLES; ++idx) {
                accumulator += timingHistogram[idx];
            }
        }
        return accumulator / (double) MAX_NUM_TIMING_SAMPLES;
    }

    public void SetData(float[] combinedData ) {
               DataBlock dataBlock;
               System.out.println(parentSensor.BoldOn + "Ouput Set Data - DataRecord Length: " + parentSensor.BoldOff + combinedData.length);

        try {
            if (latestRecord == null) {
                dataBlock = dataStruct.createDataBlock();
            } else {
                dataBlock = latestRecord.renew();
            }
            synchronized (histogramLock) {
                int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;
                // Get a sampling time for latest set based on previous set sampling time
                timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;
                // Set latest sampling time to now
                lastSetTimeMillis = timingHistogram[setIndex];
            }
            ++setCount;

            dataBlock.setDoubleValue(0, System.currentTimeMillis() / 1000d);
            for (int i=0; i < combinedData.length; i++){
                dataBlock.setDoubleValue(i+1,combinedData[i]);
            };
            latestRecord = dataBlock;
            latestRecordTime = System.currentTimeMillis();

            eventHandler.publish(new DataEvent(latestRecordTime, BNO085Output.this, dataBlock));

        } catch (Exception e) {
            System.err.println("Error reading from BNO085: " + e.getMessage());
            e.printStackTrace();
        }
    }

}


Drive/hub vehicle sensor collector and push client
==================================================

This code is a part of the [Drive/hub](http://drivehub.net) public interface for vehicle's OBD-II (or any other) sensor data logging and pushing onto remote server.

## Usage

    public class AppSensorCollector implements SensorCollectorAdapter
    {
        public void activate()
        {
            // on J2ME:
            recordStore = new RMSRecordStore(SENSOR_STORAGE);
            // or on J2SE:
            recordStore = new MemoryRecordStore();

            SensorCollector sc = new SensorCollector(recordStore);
            // add parameter KPH with update interval 1sec, allow average calculation between intervals
            sc.addParameter("KPH", 1000, true);

            // create network push thread
            sensorPushHandler = new SensorPushHandler(recordStore, "drivehub.net/events/push", "secret_token", null);
            sensorPushHandler.setMinimumPushSize(10);
            sensorPushHandler.activate();

            ts = System.currentTimeMillis();
            sc.setTripStamp(ts);
            sensorPushHandler.setActiveTrip(ts);
        }

        public void deactivate()
        {
            sc.deactivate();
            sensorPushHandler.deactivate();
        }

        // within some sensor data notification:
        sc.recordValue("KPH", timeStamp, value);

    }

Network data format
==============================

Data is pushed in a form of HTTP POST request, 200 OK response means event was added.
4XX or 5XX response means something bad happened. Response body may contain some details.

## API to add events

    token=accessToken
Access Token - a secret phrase allowing to access vehicle and push events:

    date=1234456
Date - this trip's date, in seconds from epoch

    tags=sensor,mytag
Tags - comma separate list of tags. Drivehub uses 'sensor' tag to recognize special 'trip' events

    push=<base64 data stream>
Push data - sensor data in special encoding. See next section

## Push data format

Drivehub uses OpenDMTP custom header 0x7D to encode raw OBD-II sensor flow

    PKT_CLIENT_CUSTOM_FORMAT_SENSOR     = PKT_CLIENT_HEADER|0x7D,    // Sensor dumping format

Data stream format follows (all is big endian):

- Trip stamp [8 bytes]

- Parameter name [utf8 as in DataOutputStream.writeUTF]

- Sensor Events:
  - either
  - absolute timestamp event - 0xFE [1 byte]
  - absolute timestamp in milliseconds [8 bytes]
  - or
  - current_interval change event - 0xFF  [1 byte]
  - new current_interval value in milliseconds. From 1 to 65535 (~1min) [2 bytes]
  - or
  - New sensor timestamp value - values 0x00-0xFD. (real timestamp is calculated by ts*current_interval) [1 byte]
  - New sensor value as a float [4 bytes]

## Sample stream
    
    drivehub.net/events/push?token=secret_token&date=123423&tags=sensor,mytag&push=$E07D=BASE64STREAM$E07D=BASE64STREAM$E07D=BASE64STREAM

Size Estimations
================

    a very detailed sensor frequency - 1Hz
      gives 3600 events per hour,
      gives ~3600*5 = 18Kb per hour,
      gives 18*1.3 = 24Kb of HTTP data per hour
    minimum 3 detailes sensor (injector, rpm, kph)
      gives 24Kb*3 = 75Kb per hour
    2 hours of driving per day, 30 days
      gives 75*30*2 = 4.5Mb per month
    average 10cents per Mb of GPRS traffic
      gives $0.45 per month

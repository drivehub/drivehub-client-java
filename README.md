
Drive/hub vehicle sensor collector and push client
==================================================

This code is a part of the [Drive/hub](http://drivehub.net) public interface for vehicle's OBD-II (or any other) sensor data logging and pushing onto remote server.

## Usage


Network data format
==============================

## CT01=  = (old raw) split with '=  ='
        //   0:X - utf8 as in DataOutputStream.writeUTF - parameter name
        // Payload:
        //   0:8 - Timestamp epoch in milliseconds
        //   8:8 - Sensor value
        //   * [repeat multiple times]

## Compact format 7F
    PKT_CLIENT_CUSTOM_FORMAT_SENSOR     = PKT_CLIENT_HEADER|0x7F,    // Sensor dumping format

    0:X - utf8 as in DataOutputStream.writeUTF - parameter name
    
    X+

    <<< [repeat multiple times]

    0:1 - if == 0x00 then absolute timestamp event
    1:8 - absolute timestamp in milliseconds.

    0:1 - if == 0xFF then [current_interval] change event
    1:2 - new [current_interval] in milliseconds. From 1 to 65535 (~1min)

    0:1 - Timestamp increment from previous measurement in millisecond * [current_interval]
    1:4 - Sensor value as a raw Float (4bytes) in network order
    
    <<< [repeat multiple times]

## Compact format 7E
    PKT_CLIENT_CUSTOM_FORMAT_SENSOR     = PKT_CLIENT_HEADER|0x7E,    // Sensor dumping format

    0:8 - trip stamp

    0:X - utf8 as in DataOutputStream.writeUTF - parameter name
    
    X+

    <<< [repeat multiple times]

    0:1 - if == 0xFE then absolute timestamp event
    1:8 - absolute timestamp in milliseconds.

    0:1 - if == 0xFF then [current_interval] change event
    1:2 - new [current_interval] in milliseconds. From 1 to 65535 (~1min)

    0:1 - Timestamp increment from previous measurement in millisecond * [current_interval]
    1:4 - Sensor value as a raw Float (4bytes) in network order
    
    <<< [repeat multiple times]


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

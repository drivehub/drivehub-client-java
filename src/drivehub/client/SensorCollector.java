package drivehub.client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Class responsible for sensor data collection, storage in a compact binary format and
 * passing byte[] records up to the application for external storage.
 */
public class SensorCollector {

	private int maxBulkSize = 2*1024;

	Hashtable paramStorage = new Hashtable();
	private SensorRecordStore adapter;

	/**
	 * Stamp for the current trip. Used to distinguish records from different trips
	 */
	long tripStamp = 0;

	/**
	 * @param adapter is an application level code which stores to records into some persistent storage
	 */
	public SensorCollector(SensorRecordStore adapter)
	{
		this.adapter = adapter;
	}
	
	/**
	 * Registers a new parameter to be logged in
	 */
	public void addParameter(String name, int updateInterval, boolean average)
	{
		SensorData sd = new SensorData();
		sd.updateInterval = updateInterval;
		sd.optionAverage = average;
		
		paramStorage.put(name, sd);
	}

	/**
	 * Removes the parameter from the set of logged sensors
	 */
	public void removeParameter(String name) {
		paramStorage.remove(name);
	}

	public void deactivate() {
		for(Enumeration e = paramStorage.keys(); e.hasMoreElements();)
		{
			String name = (String)e.nextElement();
			SensorData sd = (SensorData)paramStorage.get(name);
			if (sd.list_bos != null){
			    adapter.storeRecord(sd.list_bos.getBuffer(), sd.list_bos.size());
			}
		}
		paramStorage.clear();
	}

	/**
	 * Invoked by the application each time the parameter's value is changed.
	 * Accumulates value change using the updateInterval for this sensor.
	 */
	public void recordValue(String name, long timeStamp, float value) throws IOException {
		SensorData sd = (SensorData)paramStorage.get(name);
		
		if (sd.list_bos != null && sd.list_bos.size() >= maxBulkSize){
			adapter.storeRecord(sd.list_bos.getBuffer(), sd.list_bos.size());
			sd.list_bos = null;
		}

		if (sd.list_bos == null){
			sd.list_bos = new DirectBOS(maxBulkSize);
			sd.list_dos = new DataOutputStream(sd.list_bos);
			// ID of the current trip
			sd.list_dos.writeLong(tripStamp);
			// Sensor parameter name
			sd.list_dos.writeUTF(name);
			// Initiate timestamp
			sd.list_dos.writeByte(0xFE);
			sd.list_dos.writeLong(timeStamp);
			// Initiate interval value
			sd.currentInterval = 1 + sd.updateInterval/10;
			if (sd.currentInterval > Short.MAX_VALUE)
				sd.currentInterval = Short.MAX_VALUE;
			sd.list_dos.writeByte(0xFF);
			sd.list_dos.writeShort(sd.currentInterval);
			// init data
			sd.last_ts = timeStamp;
			sd.currentAveraged = 0;
			sd.averagedNo = 0;
		}

		long ts_diff = timeStamp-sd.last_ts;
		
		// accumulate values
		if (sd.optionAverage){
			sd.currentAveraged = (sd.currentAveraged*sd.averagedNo + value)/(sd.averagedNo+1);
			sd.averagedNo++;
		}else{
			sd.currentAveraged = value;
		}

		if (ts_diff >= sd.updateInterval || ts_diff == 0)
		{
			int ts_diff_adopted = (int)(ts_diff / sd.currentInterval);
			/*
			 * May happen if sensor reading is delayed because of some issues
			 * and becomes outside of current coefficient 
			 */
			if (ts_diff_adopted >= 0xFE){
				sd.list_dos.writeByte(0xFE);
				sd.list_dos.writeLong(sd.last_ts);
				ts_diff_adopted = 0;
			}
			// Write sensor value
			sd.list_dos.writeByte(ts_diff_adopted);
			sd.list_dos.writeFloat(sd.currentAveraged);
			sd.last_ts = timeStamp;
			sd.currentAveraged = 0;
			sd.averagedNo = 0;
		}
	}

	/**
	 * Changes current trip stamp which is used to group records into trips
	 */
	public void setTripStamp(long ts) {
		this.tripStamp = ts;
	}

	public long getTripStamp() {
		return tripStamp;
	}
	
}

/**
 * Direct access to BOF buffer for speed
 * @author irusskih
 *
 */
class DirectBOS extends ByteArrayOutputStream
{
	public DirectBOS(int size) {
		super(size);
	}
	public DirectBOS() {
		super();
	}
	
	public byte[] getBuffer()
	{
		return buf;
	}
}

class SensorData
{
	long last_ts = -1;
	int updateInterval;
	int currentInterval;
	boolean optionAverage;
	float currentAveraged;
	int averagedNo;
	
	DirectBOS list_bos;
	DataOutputStream list_dos;	
}

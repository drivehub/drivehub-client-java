package drivehub.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Vector;

import drivehub.util.Base64;

public class SensorPush {

	/**
	 * Forcibly join different trips within this threshold interval
	 */
	private static final long EVENTS_GROUP_THRESHOLD = 2*60*1000;

	/**
	 * Each record is 2Kb max, giving 300Kb of data max
	 */
	private static final int MAX_RECORDS_IN_PUSH = 150;

	Vector collectedRecordIDs = new Vector();
	Vector collectedRecordBytes = new Vector();
	long collected_trip_stamp = -1;
	long min_ts = -1;
	long max_ts = -1;
	
	int pushedSize = 0;

	public SensorPush()
	{
	}
	
	public void analyseRecord(int id, byte[] record) throws IOException
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(record);
		DataInputStream dis = new DataInputStream(bis);
		long trip_stamp = dis.readLong();
		dis.readUTF();
		dis.readByte(); // should be 0xFE
		long ts = dis.readLong();
		
		if (collectedRecordIDs.size() > MAX_RECORDS_IN_PUSH){
			return;
		}

		if (min_ts == -1) {
			min_ts = ts;
		}
		if (max_ts == -1) {
			max_ts = ts;
		}
		if (collected_trip_stamp == -1) {
			collected_trip_stamp = trip_stamp;
		}
		if (collected_trip_stamp != trip_stamp){
			// event is outside of a threshold?
			if ((min_ts - ts > EVENTS_GROUP_THRESHOLD) ||
			    (ts - max_ts > EVENTS_GROUP_THRESHOLD))
			{
				// drop this event
				return;
			}
		}
		if (min_ts > ts){
		  min_ts = ts;
		}
		if (max_ts < ts){
		  max_ts = ts;
		}

		// event is ok, push it
		collectedRecordIDs.addElement(new Integer(id));
		collectedRecordBytes.addElement(record);
	}
	
	/**
	 * Returns IDs of the records which where chosen for the push
	 * @return
	 */
	public Vector getRecordIDs()
	{
		return collectedRecordIDs;
	}
	
	public long getTripStamp()
	{
		return collected_trip_stamp;
	}
	
	public void pushData(OutputStream os) throws UnsupportedEncodingException, IOException
	{
		pushedSize = 0;
		os.write("date=".getBytes("UTF-8"));
		os.write(String.valueOf((int)(min_ts/1000)).getBytes("UTF-8"));
		os.write('&');
		os.write("tags=sensor".getBytes("UTF-8"));
		os.write('&');
		os.write("push=".getBytes("UTF-8"));
		pushedSize += 20;
		// data
		for(int i = 0; i < collectedRecordBytes.size(); i++){
			byte[] record = (byte[])collectedRecordBytes.elementAt(i);
			// DMTP header 7F - initial compact sensor dump format 
			// DMTP header 7E - updates on some constants and ranges
			// DMTP header 7D - bug fix with '0' timestamp
			os.write("$E07D=".getBytes("UTF-8"));
			// base64
			String b64 = Base64.encode(record);
			pushedSize += b64.length();
			os.write(b64.getBytes());
		}
		
	}

	public int getPushedSize() {
		return pushedSize;
	}

}

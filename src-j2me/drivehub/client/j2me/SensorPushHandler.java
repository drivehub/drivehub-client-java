package drivehub.client.j2me;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

import drivehub.client.ProgressLogger;
import drivehub.client.SensorPush;

/**
 * J2ME client side logic to periodically scan RMS store, collect and push available records
 * into the network.
 * 
 * @author irusskih
 *
 */
public class SensorPushHandler implements Runnable {

	/**
	 * Period to initiate data push
	 */
	private static final long PUSH_TIMEOUT = 5*60*1000;

	private Object sync = new Object();
	private boolean active;
	private String pushURL;
	private String rms;
	private ProgressLogger logger;
	
	private int minimumPushSize = 20;
	private long active_trip_stamp;

	/**
	 * Creates the instance, but do not runs it.
	 * 
	 * @param rms RMS name to be used to scan over
	 * @param pushSite push site url (drivehub.us/events/push)
	 * @param accessToken vehicle's access token
	 * @param logger log interface to give a progress information
	 */
	public SensorPushHandler(String rms, String pushSite, String accessToken, ProgressLogger logger)
	{
		this.rms = rms;
		this.pushURL = "http://"+pushSite+"?token="+accessToken;
		if (logger == null){
			logger = new ProgressLogger() {
				public void info(String state, String details) {}
				public void info(String state) {}
				public void error(String state, Exception e) {}
			};
		}
		this.logger = logger;
	}
	
	public void activate()
	{
		if (active == false)
		{
			active = true;
			new Thread(this).start();
		}
	}

	public void deactivate()
	{
		active = false;
		triggerUpload();
	}
	
	/**
	 * Changes active trip stamp. This is used to prevent push of the records
	 * which are still within a currently collected trip
	 * 
	 * @param ts
	 */
	public void setActiveTrip(long ts)
	{
		active_trip_stamp = ts;
	}
	
	/**
	 * Minimum nuber of records to push.
	 * @param minimumPushSize
	 */
	public void setMinimumPushSize(int minimumPushSize)
	{
		this.minimumPushSize = minimumPushSize;
	}

	public void triggerUpload()
	{
		synchronized (sync) {
			sync.notifyAll();
		}
	}

	public void run()
	{
		boolean hasMoreRecords = true;
		while(true)
		{
			try {
				// Wait if no more records to push
				if (!hasMoreRecords)
				{
					synchronized (sync) {
						sync.wait(PUSH_TIMEOUT);
					}
				}
				if (!active){
					return;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			try {
				RecordStore rs = RecordStore.openRecordStore(rms, false);

				// collect all records within a timerange of a first record
				SensorPush sp = new SensorPush();
				RecordEnumeration rse = rs.enumerateRecords(null, null, false);

				while(true){
					int id;
					try{
						id = rse.nextRecordId();
					}catch(InvalidRecordIDException e){
						break;
					}
					byte[] record = rs.getRecord(id);

					sp.analyseRecord(id, record);
				}
				
				// break whole collection if looks like a current trip.
				if (active_trip_stamp == sp.getTripStamp()){
					continue;
				}
				
				Vector collectedRecordIDs = sp.getRecordIDs();
				if (collectedRecordIDs.size() == 0)
				{
					logger.info("no records");
					hasMoreRecords = false;
					continue;
				}
				if (collectedRecordIDs.size() < minimumPushSize)
				{
					logger.info("not enough records");
					hasMoreRecords = false;
					continue;
				}

                logger.info("pushing "+collectedRecordIDs.size()+" records");

				HttpConnection conn = (HttpConnection) Connector.open(this.pushURL);
				conn.setRequestMethod(HttpConnection.POST);
				conn.setRequestProperty("User-Agent", "Profile/MIDP-2.0 Confirguration/CLDC-1.0");
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				OutputStream os = conn.openDataOutputStream();
				
				sp.pushData(os);
				
				if (conn.getResponseCode() == 200){
					for(int i = 0; i < collectedRecordIDs.size(); i++){
						rs.deleteRecord(((Integer)collectedRecordIDs.elementAt(i)).intValue());
                    }
					logger.info("pushed "+sp.getPushedSize());
                    
                }else{
					InputStream is = conn.openDataInputStream();
					final byte[] reply = new byte[10000];
					is.read(reply, 0, 10000);
					is.close();
					logger.info("bad response", new String(reply));
					// got a problem - delay
					hasMoreRecords = false;
				}
				
				conn.close();
				rs.closeRecordStore();
			} catch (Exception e) {
				logger.error("pushing", e);
				e.printStackTrace();
				hasMoreRecords = false;
			}
			
		}

	}
}

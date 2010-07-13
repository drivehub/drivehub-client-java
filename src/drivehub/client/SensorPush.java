package drivehub.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import drivehub.util.Base64;

public abstract class SensorPush implements Runnable {

	/**
	 * Forcibly join different trips within this threshold interval
	 */
	private static final long EVENTS_GROUP_THRESHOLD = 2*60*1000;

	/**
	 * Each record is 2Kb max, giving 300Kb of data max
	 */
	private static final int MAX_RECORDS_IN_PUSH = 150;
    /**
     * Period to initiate data push
     */
    private static final long PUSH_TIMEOUT = 5*60*1000;
    
    protected String pushURL;
    protected ProgressLogger logger;
    protected boolean active;
	protected int minimumPushSize = 20;
	protected int pushedSize;

    private Object sync = new Object();
    private long active_trip_stamp;
    private SensorRecordStore recordStore;

	private Vector collectedRecordIDs;
	private Vector collectedRecordBytes;
	private long collected_trip_stamp;
	private long min_ts;
	private long max_ts;

    /**
     * Creates the instance, but do not runs it.
     * 
     * @param rms RMS name to be used to scan over
     * @param pushSite push site url (drivehub.us/events/push)
     * @param accessToken vehicle's access token
     * @param logger log interface to give a progress information
     */
	public SensorPush(SensorRecordStore recordStore, String pushSite, String accessToken, ProgressLogger logger)
	{
		String schema = "";
        if (pushSite.indexOf("://") == -1){
            schema = "http://";
        }
        this.pushURL = schema + pushSite + "?token=" + accessToken;
        if (logger == null){
            logger = new ProgressLogger() {
                public void info(String state, String details) {}
                public void info(String state) {}
                public void error(String state, Exception e) {}
            };
        }
        this.logger = logger;
		this.recordStore = recordStore;
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
	
	public void pushStream(OutputStream os) throws Exception
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
            	collectedRecordIDs = new Vector();
            	collectedRecordBytes = new Vector();
            	collected_trip_stamp = -1;
            	min_ts = -1;
            	max_ts = -1;
                Enumeration rse = this.recordStore.enumerateRecordIDs();

                while(rse.hasMoreElements()){
                    int id = ((Integer)rse.nextElement()).intValue();
                    byte[] record = this.recordStore.getRecord(id);
                    analyseRecord(id, record);
                }
                
                // break whole collection if looks like a current trip.
                if (active_trip_stamp == collected_trip_stamp){
                    continue;
                }
                
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

                boolean result = pushData();
                
                if (result){
                    for(int i = 0; i < collectedRecordIDs.size(); i++){
                        recordStore.deleteRecord(((Integer)collectedRecordIDs.elementAt(i)).intValue());
                    }
                    logger.info("pushed " + getPushedSize());
                }else{
                    // got a problem - delay
                    hasMoreRecords = false;
                }
            } catch (Exception e) {
                logger.error("pushing", e);
                e.printStackTrace();
                hasMoreRecords = false;
            }
            
        }

    }
    
    abstract public boolean pushData() throws Exception;
	
}

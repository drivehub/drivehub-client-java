package drivehub.client.j2se;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.Enumeration;
import java.util.Vector;

import drivehub.client.ProgressLogger;
import drivehub.client.SensorPush;
import drivehub.client.SensorRecordStore;

/**
 * J2SE client side logic to periodically scan, collect and push available records
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

    private SensorRecordStore recordStore;

    /**
     * Creates the instance, but do not runs it.
     * 
     * @param rms RMS name to be used to scan over
     * @param pushSite push site url (drivehub.us/events/push)
     * @param accessToken vehicle's access token
     * @param logger log interface to give a progress information
     */
    public SensorPushHandler(SensorRecordStore recordStore, String pushSite, String accessToken, ProgressLogger logger)
    {
        this.recordStore = recordStore;
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
                Enumeration rse = this.recordStore.enumerateRecordIDs();

                // collect all records within a timerange of a first record
                SensorPush sp = new SensorPush();

                while(rse.hasMoreElements()){
                    int id = ((Integer)rse.nextElement()).intValue();
                    byte[] record = this.recordStore.getRecord(id);
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

                HttpURLConnection conn = (HttpURLConnection)new URL(this.pushURL).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", "Profile/MIDP-2.0 Confirguration/CLDC-1.0");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream os = conn.getOutputStream();
                
                sp.pushData(os);
                
                if (conn.getResponseCode() == 200){
                    for(int i = 0; i < collectedRecordIDs.size(); i++){
                        recordStore.deleteRecord(((Integer)collectedRecordIDs.elementAt(i)).intValue());
                    }
                    logger.info("pushed "+sp.getPushedSize());
                    
                }else{
                    InputStream is = conn.getErrorStream();
                    final byte[] reply = new byte[10000];
                    is.read(reply, 0, 10000);
                    is.close();
                    logger.info("bad response", new String(reply));
                    // got a problem - delay
                    hasMoreRecords = false;
                }
                
                conn.disconnect();
            } catch (Exception e) {
                logger.error("pushing", e);
                e.printStackTrace();
                hasMoreRecords = false;
            }
            
        }

    }
}

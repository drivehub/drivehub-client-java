package drivehub.client.j2se;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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
public class SensorPushHandler extends SensorPush  {

    public SensorPushHandler(SensorRecordStore recordStore, String pushSite, String accessToken, ProgressLogger logger)
    {
    	super(recordStore, pushSite, accessToken, logger);
    }
    
    public boolean pushData() throws Exception
    {
        HttpURLConnection conn = (HttpURLConnection)new URL(this.pushURL).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Profile/MIDP-2.0 Confirguration/CLDC-1.0");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream os = conn.getOutputStream();
        
        pushStream(os);
        
        if (conn.getResponseCode() == 200){
            conn.disconnect();
        	return true;
        }else{
            InputStream is = conn.getErrorStream();
            final byte[] reply = new byte[10000];
            is.read(reply, 0, 10000);
            is.close();
            logger.info("bad response", new String(reply));
            conn.disconnect();
        	return false;
        }
    }
    
}

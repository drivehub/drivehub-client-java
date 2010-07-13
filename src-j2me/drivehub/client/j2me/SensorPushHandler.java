package drivehub.client.j2me;

mport java.io.InputStream;
import java.io.OutputStream;

import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

import drivehub.client.ProgressLogger;
import drivehub.client.SensorPush;
import drivehub.client.SensorRecordStore;

/**
 * J2ME client side logic to periodically scan RMS store, collect and push available records
 * into the network.
 * 
 * @author irusskih
 *
 */
public class SensorPushHandler extends SensorPush {

	public SensorPushHandler(SensorRecordStore recordStore, String pushSite, String accessToken, ProgressLogger logger)
	{
    	super(recordStore, pushSite, accessToken, logger);
	}
	
    public boolean pushData() throws Exception
    {
		HttpConnection conn = (HttpConnection) Connector.open(this.pushURL);
		conn.setRequestMethod(HttpConnection.POST);
		conn.setRequestProperty("User-Agent", "Profile/MIDP-2.0 Confirguration/CLDC-1.0");
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		OutputStream os = conn.openDataOutputStream();
				
		pushStream(os);
				
		if (conn.getResponseCode() == 200){
			conn.close();
            return true;
        }else{
			InputStream is = conn.openDataInputStream();
			final byte[] reply = new byte[10000];
			is.read(reply, 0, 10000);
			is.close();
			logger.info("bad response", new String(reply));
			conn.close();
			return false;
		}
	}
}

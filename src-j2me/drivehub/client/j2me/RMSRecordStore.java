package drivehub.client.j2me;

import java.util.Enumeration;

import drivehub.client.SensorRecordStore;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordEnumeration;

public class RMSRecordStore implements SensorRecordStore {

	
	private String rms;
	RecordStore rs;

	public RMSRecordStore(String rms) {
		this.rms = rms;
		try{
			rs = RecordStore.openRecordStore(rms, false);
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public void deleteRecord(int id) {
		try{
			rs.deleteRecord(id);
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}

	public Enumeration enumerateRecordIDs() {
		return new Enumeration() {
			
			RecordEnumeration rse;
			
			public boolean hasMoreElements() {
				try{
					if (rse == null) rse = rs.enumerateRecords(null, null, false);
					return rse.hasNextElement();
				}catch(Exception e){
					throw new RuntimeException(e.getMessage());
				}
			}
			public Object nextElement() {
				try{
					if (rse == null) rse = rs.enumerateRecords(null, null, false);
					return new Integer(rse.nextRecordId());
				}catch(Exception e){
					throw new RuntimeException(e.getMessage());
				}
			}
		};
	}

    public int getRecordsCount() {
        try{
            return rs.getNumRecords();
        }catch(Exception e){
			throw new RuntimeException(e.getMessage());
        }
    }

	public byte[] getRecord(int id) {
		try{
			return rs.getRecord(id);
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}

	public void storeRecord(byte[] record, int size) {
		try{
			rs.addRecord(record, 0, size);
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}
	public void closeRecordStore() {
		try{
			rs.closeRecordStore();
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}
}

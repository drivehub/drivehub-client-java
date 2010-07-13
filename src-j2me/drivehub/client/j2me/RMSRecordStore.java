package drivehub.client.j2me;

import java.util.Enumeration;

import drivehub.client.SensorRecordStore;

public class RMSRecordStore implements SensorRecordStore {

	
	private String rms;
	RecordStore rs = RecordStore.openRecordStore(rms, false);

	public RMSRecordStore(String rms){
		this.rms = rms;

/*	try{
}catch(InvalidRecordIDException e){
}
byte[] record = rs.getRecord(id);
*/
		
	}
	@Override
	public void deleteRecord(int id) {
		rs.deleteRecord(id);
	}

	@Override
	public Enumeration enumerateRecordIDs() {
		return new Enumeration() {
			RecordEnumeration rse = rs.enumerateRecords(null, null, false);
			
			@Override
			public boolean hasMoreElements() {
				return rse.hasMoreElements();
			}
			@Override
			public Object nextElement() {
				return rse.nextRecordId();
			}
		};
	}
	@Override
	public byte[] getRecord(int id) {
		return rs.getRecord(id);
	}

	@Override
	public void storeRecord(byte[] record, int size) {
		rs.addRecord(record, 0, size);
	}

}

package drivehub.client;

import java.util.Enumeration;

public interface SensorRecordStore {

	/**
	 * Notifies application that maxBulkSize was reached and record should be stored
	 * Application should handle failures on its own way.
	 * 
	 * @param record record to store
	 * @param size number of bytes to store
	 */
	void storeRecord(byte[] record, int size);

	/**
	 * Each object in the enumeration is Integer record ID.
	 * @return
	 */
	public Enumeration enumerateRecordIDs();
	
	/**
	 * Each object in the enumeration is Integer record ID.
	 * @return
	 */
	public byte[] getRecord(int id);

	void deleteRecord(int id);

}

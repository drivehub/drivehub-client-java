package drivehub.client;

public interface SensorCollectorAdapter {

	/**
	 * Notifies application that maxBulkSize was reached and record should be stored
	 * Application should handle failures on its own way.
	 * 
	 * @param record record to store
	 * @param size number of bytes to store
	 */
	void storeRecord(byte[] record, int size);

}
